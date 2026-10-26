package com.saldolivre.app.data.engine

import androidx.compose.runtime.Immutable
import com.saldolivre.app.data.model.Card
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
    val totalCommitted: Double = 0.0
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
                it.reserveCardId == null &&
                it.visibleToUids.contains(uid)
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
        val myShare = FinanceEngine.round2(
            max(0.0, expenses.sumOf { FinanceEngine.impactFor(it, uid, all) } - credits)
        )
        val othersShare = FinanceEngine.round2(max(0.0, total - myShare))

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
            val shares = getTransactionParticipantShares(t, uid, all)
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

        // Se houver créditos (estornos/pagamentos) e o titular tiver participação, abate proporcionalmente do titular
        if (credits > 0.0 && participantMap.containsKey("Você (Minha parte)")) {
            val cur = participantMap["Você (Minha parte)"]!!.amount
            val adjusted = FinanceEngine.round2(max(0.0, cur - credits))
            participantMap["Você (Minha parte)"] = participantMap["Você (Minha parte)"]!!.copy(amount = adjusted)
        }

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
                it.reserveCardId == null &&
                it.visibleToUids.contains(uid)
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
     * O titular/usuário atual é indexado pela chave especial "__me__".
     * Outros participantes são indexados por seus nomes.
     * A soma de todos os valores no mapa é GARANTIDA de ser igual a FinanceEngine.round2(tx.effectiveAmount).
     */
    fun getTransactionParticipantShares(
        tx: Transaction,
        uid: String,
        all: List<Transaction> = emptyList()
    ): Map<String, Double> {
        if (tx.type != TransactionType.EXPENSE) return emptyMap()
        val total = FinanceEngine.round2(tx.effectiveAmount)
        if (Math.abs(total) <= 0.0001) return emptyMap()

        val shares = mutableMapOf<String, Double>()

        // 1. Alocações explícitas (quando há alocações cadastradas na transação)
        val validAllocs = tx.allocations.filter { Math.abs(it.amount) > 0.0001 }
        val othersAllocs = validAllocs.filter { a ->
            val isMyUid = !a.uid.isNullOrBlank() && a.uid == uid
            val norm = a.name.trim().lowercase()
            val isMyName = (a.uid.isNullOrBlank() || a.uid == uid) && (
                norm == "você" || norm == "voce" || norm == "meu" ||
                norm == "minha parte" || norm == "você (minha parte)" || norm == "voce (minha parte)"
            )
            !isMyUid && !isMyName
        }

        if (othersAllocs.isNotEmpty()) {
            var sumOthers = 0.0
            for (a in othersAllocs) {
                val name = a.name.trim().ifEmpty { "Outro" }
                val amt = FinanceEngine.round2(a.amount)
                shares[name] = FinanceEngine.round2((shares[name] ?: 0.0) + amt)
                sumOthers += amt
            }
            val myAllocs = validAllocs.filter { !othersAllocs.contains(it) }
            val myAllocSum = myAllocs.sumOf { it.amount }
            val myAmount = if (Math.abs(myAllocSum) > 0.0001) {
                FinanceEngine.round2(myAllocSum)
            } else {
                FinanceEngine.round2(total - sumOthers)
            }
            if (Math.abs(myAmount) > 0.0001) {
                shares["__me__"] = myAmount
            }
        } else if (tx.splitType == com.saldolivre.app.data.model.SplitType.SPLIT_50) {
            // 2. Divisão 50/50 implícita
            val half = FinanceEngine.round2(total / 2.0)
            val otherHalf = FinanceEngine.round2(total - half)
            shares["__me__"] = half
            val partnerName = if (!tx.externalName.isNullOrBlank()) tx.externalName.trim() else "Parceiro(a)"
            shares[partnerName] = otherHalf
        } else if (tx.splitType == com.saldolivre.app.data.model.SplitType.PARTNER) {
            // 3. 100% Parceiro(a)
            val partnerName = if (!tx.externalName.isNullOrBlank()) tx.externalName.trim() else "Parceiro(a)"
            shares[partnerName] = total
        } else if (!tx.assignedFriendName.isNullOrBlank() ||
            !tx.assignedFriendUid.isNullOrBlank() ||
            tx.splitType == com.saldolivre.app.data.model.SplitType.FRIEND
        ) {
            // 4. Amigo atribuído
            val friendName = tx.assignedFriendName?.trim()?.ifEmpty { "Amigo" } ?: "Amigo"
            val friendAmount: Double = when {
                tx.friendSharePct != null && tx.friendSharePct > 0.0 -> {
                    FinanceEngine.round2((total * tx.friendSharePct) / 100.0)
                }
                tx.userImpact.containsKey(uid) && Math.abs(tx.userImpact[uid] ?: 0.0) > 0.0 -> {
                    FinanceEngine.round2(total - (tx.userImpact[uid] ?: 0.0))
                }
                !tx.assignedFriendUid.isNullOrBlank() && tx.userImpact.containsKey(tx.assignedFriendUid) -> {
                    FinanceEngine.round2(tx.userImpact[tx.assignedFriendUid] ?: total)
                }
                else -> {
                    FinanceEngine.round2(total / 2.0)
                }
            }

            val myAmount = FinanceEngine.round2(total - friendAmount)
            if (Math.abs(myAmount) > 0.0001) {
                shares["__me__"] = myAmount
            }
            shares[friendName] = friendAmount
        } else if (!tx.externalName.isNullOrBlank() ||
            tx.splitType == com.saldolivre.app.data.model.SplitType.EXTERNAL
        ) {
            // 5. Terceiro / Externo
            val extName = tx.externalName?.trim()?.ifEmpty { "Terceiro" } ?: "Terceiro"
            val extAmount: Double = if (tx.splitType == com.saldolivre.app.data.model.SplitType.EXTERNAL) {
                total
            } else if (tx.userImpact.containsKey(uid) && Math.abs(tx.userImpact[uid] ?: 0.0) > 0.0) {
                FinanceEngine.round2(total - (tx.userImpact[uid] ?: 0.0))
            } else {
                total
            }
            val myAmount = FinanceEngine.round2(total - extAmount)
            if (Math.abs(myAmount) > 0.0001) {
                shares["__me__"] = myAmount
            }
            shares[extName] = extAmount
        } else {
            // 6. 100% Meu (SplitType.MINE ou padrão)
            shares["__me__"] = total
        }

        // 7. Reconciliação estrita de centavos:
        // Garante com precisão matemática absoluta que a soma de todas as cotas seja EXATAMENTE igual a 'total'.
        val sumShares = FinanceEngine.round2(shares.values.sum())
        val diff = FinanceEngine.round2(total - sumShares)
        if (kotlin.math.abs(diff) > 0.0001) {
            if (shares.containsKey("__me__")) {
                val curMe = shares["__me__"] ?: 0.0
                val adjustedMe = FinanceEngine.round2(curMe + diff)
                if (Math.abs(adjustedMe) > 0.0001) {
                    shares["__me__"] = adjustedMe
                } else {
                    shares.remove("__me__")
                }
            } else {
                val largestKey = shares.maxByOrNull { Math.abs(it.value) }?.key
                if (largestKey != null) {
                    val cur = shares[largestKey] ?: 0.0
                    shares[largestKey] = FinanceEngine.round2(cur + diff)
                } else {
                    shares["__me__"] = diff
                }
            }
        }

        return shares
    }

    /**
     * Calcula o valor atribuível a um participante específico em uma transação da fatura.
     */
    fun participantAmountIn(
        tx: Transaction,
        participant: InvoiceParticipantShare,
        uid: String
    ): Double {
        if (tx.type != TransactionType.EXPENSE) return 0.0
        val shares = getTransactionParticipantShares(tx, uid)

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
        uid: String
    ): List<InvoiceCategorySlice> {
        val participantExpenses = expenses.mapNotNull { tx ->
            val share = participantAmountIn(tx, participant, uid)
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
}
