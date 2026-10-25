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

        // Rateio detalhado por participante
        val participantMap = mutableMapOf<String, InvoiceParticipantShare>()
        if (myShare > 0) {
            participantMap["__me__"] = InvoiceParticipantShare(
                name = "Você (Minha parte)",
                amount = myShare,
                pct = if (total > 0) ((myShare / total) * 100).roundToInt() else 0,
                isMe = true
            )
        }

        for (t in expenses) {
            if (t.allocations.isNotEmpty()) {
                for (a in t.allocations) {
                    if (a.uid == uid) continue
                    val name = a.name.trim().ifEmpty { "Outro" }
                    val current = participantMap[name]?.amount ?: 0.0
                    val updatedAmt = FinanceEngine.round2(current + a.amount)
                    participantMap[name] = InvoiceParticipantShare(
                        name = name,
                        amount = updatedAmt,
                        pct = if (total > 0) ((updatedAmt / total) * 100).roundToInt() else 0,
                        isMe = false
                    )
                }
            } else if (!t.assignedFriendName.isNullOrBlank()) {
                val name = t.assignedFriendName.trim()
                val friendShare = FinanceEngine.round2(max(0.0, t.effectiveAmount - (t.userImpact[uid] ?: 0.0)))
                if (friendShare > 0) {
                    val current = participantMap[name]?.amount ?: 0.0
                    val updatedAmt = FinanceEngine.round2(current + friendShare)
                    participantMap[name] = InvoiceParticipantShare(
                        name = name,
                        amount = updatedAmt,
                        pct = if (total > 0) ((updatedAmt / total) * 100).roundToInt() else 0,
                        isMe = false
                    )
                }
            } else if (!t.externalName.isNullOrBlank()) {
                val name = t.externalName.trim()
                val share = FinanceEngine.round2(max(0.0, t.effectiveAmount - (t.userImpact[uid] ?: 0.0)))
                if (share > 0) {
                    val current = participantMap[name]?.amount ?: 0.0
                    val updatedAmt = FinanceEngine.round2(current + share)
                    participantMap[name] = InvoiceParticipantShare(
                        name = name,
                        amount = updatedAmt,
                        pct = if (total > 0) ((updatedAmt / total) * 100).roundToInt() else 0,
                        isMe = false
                    )
                }
            }
        }

        val recordedOthers = participantMap.values.filter { !it.isMe }.sumOf { it.amount }
        if (othersShare > recordedOthers + 0.05) {
            val diff = FinanceEngine.round2(othersShare - recordedOthers)
            val current = participantMap["Parceiro(a)"]?.amount ?: 0.0
            val updatedAmt = FinanceEngine.round2(current + diff)
            participantMap["Parceiro(a)"] = InvoiceParticipantShare(
                name = "Parceiro(a)",
                amount = updatedAmt,
                pct = if (total > 0) ((updatedAmt / total) * 100).roundToInt() else 0,
                isMe = false
            )
        }

        val splitBreakdown = participantMap.values
            .filter { it.amount > 0 }
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
}
