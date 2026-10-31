package com.saldolivre.app.data.engine

import androidx.compose.runtime.Immutable
import com.saldolivre.app.data.model.Allocation
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Motor de faturas de cartão (puro, espelha `web/src/lib/invoices.ts`).
 *
 * Uma fatura é derivada das transações de um cartão (`cardId`) com o mesmo
 * mês de competência (mês de vencimento). Não há coleção nova no Firestore.
 */
enum class InvoiceStatus(val label: String) {
    EMPTY("Sem lançamentos"),
    OPEN("Aberta"),
    CLOSED("Fechada"),
    OVERDUE("Vencida"),
    PAID("Paga")
}

@Immutable
data class ConsolidatedInvoiceMeta(
    val card: Card,
    val invoice: CardInvoice,
    val totalInvoiceAmount: Double,
    val myShare: Double,
    val othersShare: Double,
    val itemsCount: Int,
    val closingDate: String,
    val dueDate: String,
    val invoiceStatus: InvoiceStatus,
    val isOwner: Boolean
)

@Immutable
data class InvoiceCategorySlice(
    val category: String,
    val amount: Double,
    val pct: Int
)

@Immutable
data class InvoiceParticipantShare(
    val name: String,
    val amount: Double,
    val pct: Int,
    val isMe: Boolean
)

@Immutable
sealed class PredictiveInvoiceAlert {
    abstract val headline: String
    abstract val detail: String

    @Immutable
    data class Projection(
        val projectedTotal: Double,
        val dailyAverage: Double,
        val daysPassed: Int,
        val daysTotal: Int,
        val daysRemaining: Int,
        override val headline: String,
        override val detail: String
    ) : PredictiveInvoiceAlert()

    @Immutable
    data class Anomaly(
        val category: String,
        val pctIncrease: Int,
        val currentAmount: Double,
        val previousAvgAmount: Double,
        override val headline: String,
        override val detail: String
    ) : PredictiveInvoiceAlert()

    @Immutable
    data class NewSubscription(
        val serviceName: String,
        val amount: Double,
        override val headline: String,
        override val detail: String
    ) : PredictiveInvoiceAlert()
}

@Immutable
data class CardInvoice(
    val card: Card,
    val monthKey: String,
    val closingDate: String,
    val dueDate: String,
    val status: InvoiceStatus,
    val total: Double,
    val grossExpenses: Double,
    val credits: Double,
    val paidAmount: Double,
    val openAmount: Double,
    val myShare: Double,
    val othersShare: Double,
    val limit: Double?,
    val limitUsagePct: Int?,
    val daysToDue: Int,
    val daysToClose: Int,
    val items: List<Transaction>,
    val byCategory: List<InvoiceCategorySlice>,
    val splitBreakdown: List<InvoiceParticipantShare> = emptyList(),
    val topExpense: Transaction? = null,
    val futureInstallmentsTotal: Double = 0.0,
    val futureInstallmentsCount: Int = 0,
    val bestPurchaseDay: Int = 1,
    val bestPurchaseDate: String = "",
    val isBestPurchaseTime: Boolean = false,
    val bestPurchaseHint: String = "",
    val cycleProgressPct: Int = 0,
    val availableLimit: Double? = null,
    val totalCommitted: Double = 0.0,
    val predictiveAlert: PredictiveInvoiceAlert? = null
) {
    val itemsCount: Int get() = items.size
    val unpaidExpenses: List<Transaction>
        get() = items.filter { it.type == TransactionType.EXPENSE && it.status != TransactionStatus.PAID }
    val paidRatio: Float
        get() = if (total > 0) (paidAmount / total).toFloat().coerceIn(0f, 1f) else 0f
}

object InvoiceEngine {

    private fun clampDay(monthKey: String, day: Int): String {
        val d = day.coerceIn(1, FinanceEngine.daysInMonth(monthKey))
        return "$monthKey-${String.format(Locale.US, "%02d", d)}"
    }

    private fun diffDays(from: String, to: String): Int = try {
        ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)).toInt()
    } catch (_: Exception) {
        0
    }

    /** Fechamento/vencimento da fatura que vence em [monthKey]. */
    fun invoiceDates(card: Card, monthKey: String): Pair<String, String> {
        val dueDate = clampDay(monthKey, card.dueDay)
        val closingMonth = if (card.dueDay < card.closingDay) FinanceEngine.shiftMonth(monthKey, -1) else monthKey
        val closingDate = clampDay(closingMonth, card.closingDay)
        return closingDate to dueDate
    }

    fun buildInvoice(
        card: Card,
        monthKey: String,
        all: List<Transaction>,
        uid: String,
        today: String = FinanceEngine.todayKey()
    ): CardInvoice {
        val (closingDate, dueDate) = invoiceDates(card, monthKey)

        val items = all.filter {
            it.cardId == card.id &&
                it.competenceMonth == monthKey &&
                it.reserveCardId == null
        }.sortedWith(compareByDescending<Transaction> { it.dueDate }.thenByDescending { it.createdAt })

        val expenses = items.filter { it.type == TransactionType.EXPENSE }
        val creditItems = items.filter { it.type == TransactionType.INCOME }

        val gross = FinanceEngine.round2(expenses.sumOf { it.effectiveAmount })
        val credits = FinanceEngine.round2(creditItems.sumOf { it.effectiveAmount })
        val total = FinanceEngine.round2(max(0.0, gross - credits))
        val paid = FinanceEngine.round2(
            min(total, expenses.filter { it.status == TransactionStatus.PAID }.sumOf { it.effectiveAmount })
        )
        val open = FinanceEngine.round2(max(0.0, total - paid))

        val isOwner = card.ownerUid.isBlank() || card.ownerUid == uid
        val rawOwnerName = card.ownerName.ifBlank { "Titular" }

        val byCategory = expenses.groupBy { it.category }
            .map { (cat, txs) ->
                val amount = txs.sumOf { it.effectiveAmount }
                InvoiceCategorySlice(
                    category = cat,
                    amount = FinanceEngine.round2(amount),
                    pct = if (gross > 0) ((amount / gross) * 100).roundToInt() else 0
                )
            }
            .sortedByDescending { it.amount }

        val status = when {
            expenses.isEmpty() -> InvoiceStatus.EMPTY
            expenses.all { it.status == TransactionStatus.PAID } -> InvoiceStatus.PAID
            today < closingDate -> InvoiceStatus.OPEN
            today <= dueDate -> InvoiceStatus.CLOSED
            else -> InvoiceStatus.OVERDUE
        }

        val limit = card.limit?.takeIf { it > 0.0 }

        // Rateio detalhado por participante baseado nas cotas reais de cada lançamento
        val participantMap = mutableMapOf<String, InvoiceParticipantShare>()

        for (t in expenses) {
            val shares = getTransactionParticipantShares(t, uid, card, all)
            for ((key, amt) in shares) {
                if (Math.abs(amt) <= 0.0001) continue
                val isMe = (key == "__me__")
                val name = if (isMe) "Você (Minha parte)" else key
                val current = participantMap[name]?.amount ?: 0.0
                val updatedAmt = FinanceEngine.round2(current + amt)
                participantMap[name] = InvoiceParticipantShare(
                    name = name,
                    amount = updatedAmt,
                    pct = 0,
                    isMe = isMe
                )
            }
        }

        // Se houver créditos (estornos/pagamentos), abate proporcionalmente do titular do cartão
        if (credits > 0.0) {
            val targetKey = if (isOwner) "Você (Minha parte)" else rawOwnerName
            if (participantMap.containsKey(targetKey)) {
                val cur = participantMap[targetKey]!!.amount
                val adjusted = FinanceEngine.round2(max(0.0, cur - credits))
                participantMap[targetKey] = participantMap[targetKey]!!.copy(amount = adjusted)
            } else if (participantMap.containsKey("Você (Minha parte)")) {
                val cur = participantMap["Você (Minha parte)"]!!.amount
                val adjusted = FinanceEngine.round2(max(0.0, cur - credits))
                participantMap["Você (Minha parte)"] = participantMap["Você (Minha parte)"]!!.copy(amount = adjusted)
            }
        }

        val myShare = FinanceEngine.round2(participantMap["Você (Minha parte)"]?.amount ?: 0.0)
        val othersShare = FinanceEngine.round2(max(0.0, total - myShare))

        val splitBreakdown = participantMap.values
            .filter { it.amount > 0 }
            .map { p ->
                p.copy(pct = if (total > 0) ((p.amount / total) * 100).roundToInt() else 0)
            }
            .sortedWith(compareByDescending<InvoiceParticipantShare> { it.isMe }.thenByDescending { it.amount })

        val topExpense = expenses.maxByOrNull { it.effectiveAmount }

        val futureItems = all.filter {
            it.cardId == card.id &&
                it.competenceMonth > monthKey &&
                it.reserveCardId == null
        }
        val futureExpenses = futureItems.filter { it.type == TransactionType.EXPENSE && it.status != TransactionStatus.PAID }
        val futureCredits = futureItems.filter { it.type == TransactionType.INCOME }.sumOf { it.effectiveAmount }
        val futureInstallmentsTotal = FinanceEngine.round2(max(0.0, futureExpenses.sumOf { it.effectiveAmount } - futureCredits))
        val futureInstallmentsCount = futureExpenses.size

        val bestPurchaseDay = card.closingDay
        val bestPurchaseDate = closingDate
        val isBestPurchaseTime = today >= closingDate && diffDays(closingDate, today) <= 5
        val bestPurchaseHint = if (today < closingDate) {
            "A partir do dia $bestPurchaseDay, compras caem só na próxima fatura (até 40 dias para pagar)."
        } else {
            "Melhor época de compra ativa! Suas compras agora já caem na fatura do próximo ciclo."
        }

        val closingMonth = if (card.dueDay < card.closingDay) FinanceEngine.shiftMonth(monthKey, -1) else monthKey
        val prevClosingMonth = FinanceEngine.shiftMonth(closingMonth, -1)
        val prevClosingDate = clampDay(prevClosingMonth, card.closingDay)
        val cycleTotalDays = max(1, diffDays(prevClosingDate, closingDate))
        val cyclePassedDays = max(0, diffDays(prevClosingDate, today))
        val cycleProgressPct = ((cyclePassedDays.toDouble() / cycleTotalDays) * 100).roundToInt().coerceIn(0, 100)

        val currentCommitted = if (status == InvoiceStatus.PAID) 0.0 else open
        val totalCommitted = FinanceEngine.round2(currentCommitted + futureInstallmentsTotal)
        val limitUsagePct = limit?.let { ((totalCommitted / it) * 100).roundToInt().coerceIn(0, 100) }
        val availableLimit = limit?.let { max(0.0, FinanceEngine.round2(it - totalCommitted)) }

        return CardInvoice(
            card = card,
            monthKey = monthKey,
            closingDate = closingDate,
            dueDate = dueDate,
            status = status,
            total = total,
            grossExpenses = gross,
            credits = credits,
            paidAmount = paid,
            openAmount = open,
            myShare = myShare,
            othersShare = othersShare,
            limit = limit,
            limitUsagePct = limitUsagePct,
            availableLimit = availableLimit,
            totalCommitted = totalCommitted,
            daysToDue = diffDays(today, dueDate),
            daysToClose = diffDays(today, closingDate),
            items = items,
            byCategory = byCategory,
            splitBreakdown = splitBreakdown,
            topExpense = topExpense,
            futureInstallmentsTotal = futureInstallmentsTotal,
            futureInstallmentsCount = futureInstallmentsCount,
            bestPurchaseDay = bestPurchaseDay,
            bestPurchaseDate = bestPurchaseDate,
            isBestPurchaseTime = isBestPurchaseTime,
            bestPurchaseHint = bestPurchaseHint,
            cycleProgressPct = cycleProgressPct
        )
    }

    /** Histórico (mais antiga → mais recente), incluindo [forward] meses futuros já comprometidos. */
    fun buildHistory(
        card: Card,
        monthKey: String,
        all: List<Transaction>,
        uid: String,
        months: Int = 6,
        forward: Int = 2
    ): List<CardInvoice> =
        ((months - 1) downTo -forward).map { offset ->
            buildInvoice(card, FinanceEngine.shiftMonth(monthKey, -offset), all, uid)
        }

    fun countdownLabel(inv: CardInvoice): String = when (inv.status) {
        InvoiceStatus.PAID -> "Fatura quitada"
        InvoiceStatus.EMPTY -> "Nenhuma compra neste ciclo"
        InvoiceStatus.OPEN ->
            if (inv.daysToClose == 0) "Fecha hoje"
            else if (inv.daysToClose < 0) "Fechando ciclo"
            else "Fecha em ${inv.daysToClose} dia${if (inv.daysToClose == 1) "" else "s"}"
        else -> when {
            inv.daysToDue == 0 -> "Vence hoje"
            inv.daysToDue > 0 -> "Vence em ${inv.daysToDue} dia${if (inv.daysToDue == 1) "" else "s"}"
            else -> {
                val late = abs(inv.daysToDue)
                "Venceu há $late dia${if (late == 1) "" else "s"}"
            }
        }
    }

    /**
     * Retorna o mapa de participantes e seus respectivos valores rateados para uma transação de despesa.
     * O usuário atual/visualizador é indexado pela chave especial "__me__" (que se tornará "Você (Minha parte)").
     * O titular do cartão (se o visualizador for um convidado) é indexado pelo nome do titular.
     * Outros participantes são indexados por seus respectivos nomes.
     * A soma de todos os valores no mapa é GARANTIDA de ser igual a FinanceEngine.round2(tx.effectiveAmount).
     */
    fun getTransactionParticipantShares(
        tx: Transaction,
        uid: String,
        card: Card? = null,
        all: List<Transaction> = emptyList()
    ): Map<String, Double> {
        if (tx.type != TransactionType.EXPENSE) return emptyMap()
        val total = FinanceEngine.round2(tx.effectiveAmount)
        if (Math.abs(total) <= 0.0001) return emptyMap()

        val shares = mutableMapOf<String, Double>()

        val ownerUid = card?.ownerUid?.takeIf { it.isNotBlank() } ?: tx.paidByUid.ifBlank { tx.createdByUid }
        val rawOwnerName = card?.ownerName?.takeIf { it.isNotBlank() } ?: "Titular"
        val isViewerOwner = (uid == ownerUid)
        val ownerDisplayName = if (isViewerOwner) "__me__" else rawOwnerName

        // 1. Alocações explícitas (quando há alocações cadastradas na transação)
        val validAllocs = tx.allocations.filter { Math.abs(it.amount) > 0.0001 }
        if (validAllocs.isNotEmpty()) {
            for (a in validAllocs) {
                val amt = FinanceEngine.round2(a.amount)
                val isMyUid = !a.uid.isNullOrBlank() && a.uid == uid
                val isOwnerUid = !a.uid.isNullOrBlank() && a.uid == ownerUid
                val norm = a.name.trim().lowercase()
                val isMeName = (norm == "você" || norm == "voce" || norm == "meu" || norm == "minha parte" || norm == "você (minha parte)" || norm == "voce (minha parte)")

                val key = when {
                    isMyUid -> "__me__"
                    isOwnerUid -> ownerDisplayName
                    isMeName -> {
                        // "Você" na alocação original representa quem fez a compra (o titular do cartão)
                        ownerDisplayName
                    }
                    else -> a.name.trim().ifEmpty { "Outro" }
                }
                shares[key] = FinanceEngine.round2((shares[key] ?: 0.0) + amt)
            }
        } else if (tx.splitType == com.saldolivre.app.data.model.SplitType.SPLIT_50) {
            // 2. Divisão 50/50 implícita
            val half = FinanceEngine.round2(total / 2.0)
            val otherHalf = FinanceEngine.round2(total - half)
            val partnerLabel = if (!tx.externalName.isNullOrBlank()) tx.externalName.trim() else "Parceiro(a)"

            shares[ownerDisplayName] = FinanceEngine.round2((shares[ownerDisplayName] ?: 0.0) + half)
            val partnerKey = if (tx.assignedFriendUid == uid) "__me__" else partnerLabel
            shares[partnerKey] = FinanceEngine.round2((shares[partnerKey] ?: 0.0) + otherHalf)
        } else if (tx.splitType == com.saldolivre.app.data.model.SplitType.PARTNER) {
            // 3. 100% Parceiro(a)
            val partnerLabel = if (!tx.externalName.isNullOrBlank()) tx.externalName.trim() else "Parceiro(a)"
            val partnerKey = if (tx.assignedFriendUid == uid) "__me__" else partnerLabel
            shares[partnerKey] = total
        } else if (!tx.assignedFriendName.isNullOrBlank() ||
            !tx.assignedFriendUid.isNullOrBlank() ||
            tx.splitType == com.saldolivre.app.data.model.SplitType.FRIEND
        ) {
            // 4. Amigo atribuído
            val friendUid = tx.assignedFriendUid
            val friendName = tx.assignedFriendName?.trim()?.ifEmpty { "Amigo" } ?: "Amigo"
            val pct = (tx.friendSharePct ?: 50.0).coerceIn(1.0, 100.0)
            val friendAmount = FinanceEngine.round2((total * pct) / 100.0)
            val buyerAmount = FinanceEngine.round2(total - friendAmount)

            val isFriendViewer = (!friendUid.isNullOrBlank() && friendUid == uid)
            val friendKey = if (isFriendViewer) "__me__" else friendName
            val buyerKey = ownerDisplayName

            if (Math.abs(friendAmount) > 0.0001) {
                shares[friendKey] = FinanceEngine.round2((shares[friendKey] ?: 0.0) + friendAmount)
            }
            if (Math.abs(buyerAmount) > 0.0001) {
                shares[buyerKey] = FinanceEngine.round2((shares[buyerKey] ?: 0.0) + buyerAmount)
            }
        } else if (!tx.externalName.isNullOrBlank() ||
            tx.splitType == com.saldolivre.app.data.model.SplitType.EXTERNAL
        ) {
            // 5. Terceiro / Externo
            val extName = tx.externalName?.trim()?.ifEmpty { "Terceiro" } ?: "Terceiro"
            shares[extName] = total
        } else {
            // 6. 100% Meu (despesa própria do titular do cartão)
            shares[ownerDisplayName] = total
        }

        // 7. Reconciliação estrita de centavos:
        // Garante com precisão matemática absoluta que a soma de todas as cotas seja EXATAMENTE igual a 'total'.
        val sumShares = FinanceEngine.round2(shares.values.sum())
        val diff = FinanceEngine.round2(total - sumShares)
        if (kotlin.math.abs(diff) > 0.0001) {
            val primaryKey = ownerDisplayName
            val cur = shares[primaryKey] ?: 0.0
            shares[primaryKey] = FinanceEngine.round2(cur + diff)
        }

        return shares
    }

    /**
     * Calcula o valor atribuível a um participante específico em uma transação da fatura.
     */
    fun participantAmountIn(
        tx: Transaction,
        participant: InvoiceParticipantShare,
        uid: String,
        card: Card? = null
    ): Double {
        if (tx.type != TransactionType.EXPENSE) return 0.0
        val shares = getTransactionParticipantShares(tx, uid, card)

        if (participant.isMe) {
            return shares["__me__"] ?: 0.0
        }

        val partName = participant.name.trim().lowercase()
        return shares.entries.find { (k, _) ->
            val kNorm = k.trim().lowercase()
            kNorm == partName ||
                (partName == "parceiro(a)" && (kNorm == "parceiro" || kNorm == "parceira" || kNorm == "parceiro(a)"))
        }?.value ?: 0.0
    }

    /**
     * Recalcula a distribuição de categorias exclusivamente para os gastos do participante.
     */
    fun calculateCategoryBreakdownForParticipant(
        expenses: List<Transaction>,
        participant: InvoiceParticipantShare,
        uid: String,
        card: Card? = null
    ): List<InvoiceCategorySlice> {
        val participantExpenses = expenses.mapNotNull { tx ->
            val share = participantAmountIn(tx, participant, uid, card)
            if (share > 0.0) tx to share else null
        }

        val totalParticipantExpense = participantExpenses.sumOf { it.second }

        return participantExpenses
            .groupBy { it.first.category }
            .map { (cat, list) ->
                val amount = list.sumOf { it.second }
                InvoiceCategorySlice(
                    category = cat,
                    amount = FinanceEngine.round2(amount),
                    pct = if (totalParticipantExpense > 0) ((amount / totalParticipantExpense) * 100).roundToInt() else 0
                )
            }
            .sortedByDescending { it.amount }
    }

    /**
     * Cria um pseudo-lançamento consolidado de fatura para exibição no extrato geral no dia do vencimento.
     * O valor corresponde estritamente à cota do usuário (myShare).
     */
    fun createConsolidatedInvoiceTx(
        invoice: CardInvoice,
        uid: String,
        monthKey: String
    ): Transaction {
        val card = invoice.card
        val isOwner = card.ownerUid == uid
        val myShare = invoice.myShare
        val status = if (invoice.status == InvoiceStatus.PAID) {
            TransactionStatus.PAID
        } else if (invoice.status == InvoiceStatus.OPEN) {
            TransactionStatus.ESTIMATED
        } else {
            TransactionStatus.PENDING
        }

        val desc = "Fatura ${card.cardName}"

        val meta = ConsolidatedInvoiceMeta(
            card = card,
            invoice = invoice,
            totalInvoiceAmount = invoice.total,
            myShare = invoice.myShare,
            othersShare = invoice.othersShare,
            itemsCount = invoice.itemsCount,
            closingDate = invoice.closingDate,
            dueDate = invoice.dueDate,
            invoiceStatus = invoice.status,
            isOwner = isOwner
        )

        val allocationsList = mutableListOf(
            Allocation(
                type = "PLATFORM_USER",
                uid = uid,
                name = if (isOwner) "Você (Minha parte)" else (invoice.splitBreakdown.find { it.isMe }?.name ?: "Minha parte"),
                amount = myShare,
                reimbursed = false
            )
        )
        if (isOwner && invoice.othersShare > 0.0) {
            allocationsList.add(
                Allocation(
                    type = "EXTERNAL",
                    name = "Terceiros / Amigos",
                    amount = invoice.othersShare,
                    reimbursed = false
                )
            )
        }

        return Transaction(
            id = "invoice_${card.id}_$monthKey",
            createdByUid = card.ownerUid,
            paidByUid = card.ownerUid,
            cardId = card.id,
            description = desc,
            category = "Outros",
            subcategory = "Vencimento dia ${card.dueDay}",
            type = TransactionType.EXPENSE,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            status = status,
            estimatedAmount = if (status == TransactionStatus.PAID) null else myShare,
            actualAmount = if (status == TransactionStatus.PAID) myShare else null,
            totalAmount = myShare,
            dueDate = invoice.dueDate,
            competenceMonth = monthKey,
            visibleToUids = listOf(uid),
            userImpact = mapOf(uid to myShare),
            allocations = allocationsList,
            invoiceMeta = meta
        )
    }

    /**
     * Consolida transações de cartão de crédito no extrato mensal.
     * Substitui os lançamentos individuais de cartão por um único lançamento no dia do vencimento.
     */
    fun consolidateMonthTransactions(
        monthTransactions: List<Transaction>,
        allTransactions: List<Transaction>,
        cards: List<Card>,
        uid: String,
        monthKey: String,
        enabled: Boolean = true
    ): List<Transaction> {
        if (!enabled || uid.isBlank()) return monthTransactions

        val nonCardTxs = monthTransactions.filter {
            it.paymentMethod != PaymentMethod.CREDIT_CARD || it.cardId.isNullOrBlank() || it.reserveCardId != null
        }

        val relevantCardMap = LinkedHashMap<String, Card>()
        for (c in cards) {
            if (c.ownerUid == uid || c.sharedWithUids.contains(uid)) {
                relevantCardMap[c.id] = c
            }
        }

        for (t in monthTransactions) {
            if (t.paymentMethod == PaymentMethod.CREDIT_CARD && !t.cardId.isNullOrBlank() && !relevantCardMap.containsKey(t.cardId)) {
                cards.find { it.id == t.cardId }?.let { relevantCardMap[it.id] = it }
            }
        }

        val invoiceTxs = mutableListOf<Transaction>()
        for (card in relevantCardMap.values) {
            val inv = buildInvoice(card, monthKey, allTransactions, uid)
            if (inv.itemsCount == 0) continue
            if (card.ownerUid != uid && inv.myShare <= 0.0) continue

            invoiceTxs.add(createConsolidatedInvoiceTx(inv, uid, monthKey))
        }

        return (nonCardTxs + invoiceTxs).sortedByDescending { it.dueDate }
    }

    /**
     * Motor de Alertas Preditivos:
     * Calcula dinamicamente um dos 3 insights preditivos para a fatura:
     * 1. Alerta de Anomalia (aumento significativo em categorias de gastos vs histórico)
     * 2. Nova Assinatura detectada no ciclo atual (serviço recorrente inédito no cartão)
     * 3. Projeção estimada de fechamento (baseada na velocidade de gasto diário e dias restantes)
     */
    fun computePredictiveAlert(
        invoice: CardInvoice,
        all: List<Transaction>,
        history: List<CardInvoice>,
        today: String = FinanceEngine.todayKey()
    ): PredictiveInvoiceAlert {
        val card = invoice.card
        val monthKey = invoice.monthKey

        // 1. Detectar anomalia de gastos em relação aos ciclos anteriores
        val pastInvoices = history.filter { it.monthKey < monthKey && it.grossExpenses > 0 }
        if (pastInvoices.isNotEmpty()) {
            val anomalies = invoice.byCategory.mapNotNull { catSlice ->
                if (catSlice.amount < 40.0) return@mapNotNull null
                val pastAmounts = pastInvoices.map { past ->
                    past.byCategory.find { it.category.equals(catSlice.category, ignoreCase = true) }?.amount ?: 0.0
                }
                val pastAvg = if (pastAmounts.isNotEmpty()) pastAmounts.average() else 0.0
                if (pastAvg >= 25.0 && catSlice.amount > pastAvg) {
                    val pct = (((catSlice.amount - pastAvg) / pastAvg) * 100).roundToInt()
                    if (pct >= 25) {
                        Triple(catSlice.category, pct, pastAvg)
                    } else null
                } else null
            }.sortedByDescending { it.second }

            val topAnomaly = anomalies.firstOrNull()
            if (topAnomaly != null) {
                val (cat, pct, pastAvg) = topAnomaly
                val currentAmount = invoice.byCategory.find { it.category.equals(cat, ignoreCase = true) }?.amount ?: 0.0
                return PredictiveInvoiceAlert.Anomaly(
                    category = cat,
                    pctIncrease = pct,
                    currentAmount = currentAmount,
                    previousAvgAmount = FinanceEngine.round2(pastAvg),
                    headline = "Seus gastos em $cat subiram +$pct%",
                    detail = "Neste ciclo foram ${FinanceEngine.formatCurrency(currentAmount)} vs média de ${FinanceEngine.formatCurrency(pastAvg)} dos ciclos anteriores."
                )
            }
        }

        // 2. Detectar novas assinaturas / serviços recorrentes detectados pela primeira vez
        val knownServices = listOf(
            "netflix", "spotify", "amazon", "prime", "disney", "hbo", "max",
            "youtube", "icloud", "apple", "chatgpt", "openai", "deezer",
            "crunchyroll", "globo", "globoplay", "gympass", "totalpass",
            "smart fit", "alura", "uber one", "meli+", "star+"
        )
        val expenses = invoice.items.filter { it.type == TransactionType.EXPENSE }
        val newSub = expenses.firstOrNull { tx ->
            val descLower = tx.description.lowercase()
            val isCandidate = knownServices.any { descLower.contains(it) } ||
                tx.isRecurring ||
                tx.category.contains("Assinatura", ignoreCase = true) ||
                tx.category.contains("Streaming", ignoreCase = true)

            if (!isCandidate) return@firstOrNull false

            // Verifica se essa descrição já apareceu em faturas passadas deste cartão
            val existedInPast = all.any { pastTx ->
                pastTx.cardId == card.id &&
                pastTx.competenceMonth < monthKey &&
                pastTx.type == TransactionType.EXPENSE &&
                (pastTx.description.lowercase().contains(descLower) || descLower.contains(pastTx.description.lowercase()))
            }
            !existedInPast
        }
        if (newSub != null) {
            return PredictiveInvoiceAlert.NewSubscription(
                serviceName = newSub.description,
                amount = newSub.effectiveAmount,
                headline = "Nova assinatura detectada: ${newSub.description}",
                detail = "Detectamos uma nova cobrança recorrente de ${FinanceEngine.formatCurrency(newSub.effectiveAmount)} neste ciclo."
            )
        }

        // 3. Projeção estimada de fechamento (baseada no gasto médio diário)
        val closingMonth = if (card.dueDay < card.closingDay) FinanceEngine.shiftMonth(monthKey, -1) else monthKey
        val prevClosingMonth = FinanceEngine.shiftMonth(closingMonth, -1)
        val prevClosingDate = clampDay(prevClosingMonth, card.closingDay)
        val cycleTotalDays = max(1, diffDays(prevClosingDate, invoice.closingDate))
        val cyclePassedDays = max(1, diffDays(prevClosingDate, today)).coerceAtMost(cycleTotalDays)
        val daysRemaining = max(0, cycleTotalDays - cyclePassedDays)

        val dailyAvg = if (invoice.total > 0) FinanceEngine.round2(invoice.total / cyclePassedDays) else 0.0
        val projected = if (daysRemaining > 0 && dailyAvg > 0) {
            FinanceEngine.round2(invoice.total + (dailyAvg * daysRemaining))
        } else {
            invoice.total
        }

        val (headline, detail) = if (invoice.total <= 0) {
            Pair(
                "Fatura sem lançamentos",
                "Nenhum gasto registrado neste ciclo até o momento."
            )
        } else if (daysRemaining > 0) {
            Pair(
                "Fechamento estimado: ${FinanceEngine.formatCurrency(projected)}",
                "Média de ${FinanceEngine.formatCurrency(dailyAvg)}/dia (${cyclePassedDays} de ${cycleTotalDays} dias corridos · restam ${daysRemaining} dias)."
            )
        } else {
            Pair(
                "Fatura fechada: ${FinanceEngine.formatCurrency(invoice.total)}",
                "Média diária final de ${FinanceEngine.formatCurrency(dailyAvg)}/dia durante o ciclo de ${cycleTotalDays} dias."
            )
        }

        return PredictiveInvoiceAlert.Projection(
            projectedTotal = projected,
            dailyAverage = dailyAvg,
            daysPassed = cyclePassedDays,
            daysTotal = cycleTotalDays,
            daysRemaining = daysRemaining,
            headline = headline,
            detail = detail
        )
    }
}
