package com.saldolivre.app.data.engine

import com.saldolivre.app.data.model.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object FinanceEngine {

    private val ptBrLocale = Locale("pt", "BR")
    private val currencyFormat = NumberFormat.getCurrencyInstance(ptBrLocale)

    fun formatCurrency(amount: Double): String {
        return currencyFormat.format(amount)
    }

    fun round2(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    fun currentMonthKey(): String {
        val now = LocalDate.now()
        return String.format(Locale.US, "%04d-%02d", now.year, now.monthValue)
    }

    fun todayKey(): String {
        val now = LocalDate.now()
        return String.format(Locale.US, "%04d-%02d-%02d", now.year, now.monthValue, now.dayOfMonth)
    }

    fun daysInMonth(monthKey: String): Int {
        return try {
            val parts = monthKey.split("-")
            val ym = YearMonth.of(parts[0].toInt(), parts[1].toInt())
            ym.lengthOfMonth()
        } catch (_: Exception) {
            30
        }
    }

    fun formatMonthLabel(monthKey: String): String {
        return try {
            val parts = monthKey.split("-")
            val ym = YearMonth.of(parts[0].toInt(), parts[1].toInt())
            val formatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBrLocale)
            ym.format(formatter).replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            monthKey
        }
    }

    fun shiftMonth(monthKey: String, delta: Int): String {
        return try {
            val parts = monthKey.split("-")
            val ym = YearMonth.of(parts[0].toInt(), parts[1].toInt()).plusMonths(delta.toLong())
            String.format(Locale.US, "%04d-%02d", ym.year, ym.monthValue)
        } catch (_: Exception) {
            monthKey
        }
    }

    fun effectiveAmount(tx: Transaction): Double {
        return tx.actualAmount ?: tx.estimatedAmount ?: tx.totalAmount
    }

    fun calculateCardInvoiceDates(
        purchaseDate: String,
        card: Card?,
        explicitDueDate: String? = null,
        explicitMonth: String? = null
    ): Pair<String, String> {
        if (!explicitDueDate.isNullOrBlank() && explicitDueDate.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
            val cleanDue = explicitDueDate.trim()
            val cleanMonth = if (!explicitMonth.isNullOrBlank() && explicitMonth.matches(Regex("""^\d{4}-\d{2}$"""))) {
                explicitMonth.trim()
            } else {
                cleanDue.substring(0, 7)
            }
            return Pair(cleanDue, cleanMonth)
        }

        if (card == null || card.closingDay <= 0 || card.dueDay <= 0) {
            val m = if (purchaseDate.length >= 7) purchaseDate.substring(0, 7) else currentMonthKey()
            return Pair(purchaseDate, m)
        }

        try {
            val parts = purchaseDate.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()

            var closingMonth = month
            var closingYear = year

            if (day >= card.closingDay) {
                closingMonth += 1
                if (closingMonth > 12) {
                    closingMonth = 1
                    closingYear += 1
                }
            }

            var dueMonth = closingMonth
            var dueYear = closingYear

            if (card.dueDay < card.closingDay) {
                dueMonth += 1
                if (dueMonth > 12) {
                    dueMonth = 1
                    dueYear += 1
                }
            }

            val competenceMonth = String.format(Locale.US, "%04d-%02d", dueYear, dueMonth)
            val dueDate = String.format(Locale.US, "%s-%02d", competenceMonth, card.dueDay)
            return Pair(dueDate, competenceMonth)
        } catch (_: Exception) {
            val today = todayKey()
            return Pair(today, today.substring(0, 7))
        }
    }

    fun deriveImpact(allocations: List<Allocation>, visibleToUids: List<String>): Map<String, Double> {
        val impact = mutableMapOf<String, Double>()
        for (u in visibleToUids) {
            impact[u] = 0.0
        }
        for (a in allocations) {
            if (a.type == "PLATFORM_USER" && !a.uid.isNullOrBlank()) {
                impact[a.uid] = round2((impact[a.uid] ?: 0.0) + a.amount)
            }
        }
        return impact
    }

    data class SplitResult(
        val allocations: List<Allocation>,
        val userImpact: Map<String, Double>,
        val visibleToUids: List<String>
    )

    fun buildSplit(
        splitType: SplitType,
        total: Double,
        myUid: String,
        myName: String,
        partnerUid: String?,
        partnerName: String?,
        payerUid: String,
        externalName: String? = null,
        assignedFriendUid: String? = null,
        assignedFriendName: String? = null,
        friendSharePct: Double? = null
    ): SplitResult {
        val allocations = mutableListOf<Allocation>()
        val visible = mutableSetOf(myUid, payerUid)

        when (splitType) {
            SplitType.MINE -> {
                allocations.add(
                    Allocation(
                        type = "PLATFORM_USER",
                        uid = myUid,
                        name = myName,
                        amount = round2(total),
                        reimbursed = (myUid == payerUid)
                    )
                )
            }
            SplitType.SPLIT_50 -> {
                if (partnerUid != null && partnerName != null) {
                    val half = round2(total / 2.0)
                    val otherHalf = round2(total - half)
                    visible.add(partnerUid)
                    allocations.add(
                        Allocation(
                            type = "PLATFORM_USER",
                            uid = myUid,
                            name = myName,
                            amount = half,
                            reimbursed = (myUid == payerUid)
                        )
                    )
                    allocations.add(
                        Allocation(
                            type = "PLATFORM_USER",
                            uid = partnerUid,
                            name = partnerName,
                            amount = otherHalf,
                            reimbursed = (partnerUid == payerUid)
                        )
                    )
                } else {
                    allocations.add(
                        Allocation(
                            type = "PLATFORM_USER",
                            uid = myUid,
                            name = myName,
                            amount = round2(total),
                            reimbursed = true
                        )
                    )
                }
            }
            SplitType.PARTNER -> {
                if (partnerUid != null && partnerName != null) {
                    visible.add(partnerUid)
                    allocations.add(
                        Allocation(
                            type = "PLATFORM_USER",
                            uid = partnerUid,
                            name = partnerName,
                            amount = round2(total),
                            reimbursed = (partnerUid == payerUid)
                        )
                    )
                } else {
                    allocations.add(
                        Allocation(
                            type = "PLATFORM_USER",
                            uid = myUid,
                            name = myName,
                            amount = round2(total),
                            reimbursed = true
                        )
                    )
                }
            }
            SplitType.FRIEND -> {
                if (!assignedFriendUid.isNullOrBlank() && !assignedFriendName.isNullOrBlank()) {
                    visible.add(assignedFriendUid)
                    val pct = (friendSharePct ?: 100.0).coerceIn(1.0, 100.0)
                    val friendAmount = round2((total * pct) / 100.0)
                    val myAmount = round2(total - friendAmount)

                    if (myAmount > 0) {
                        allocations.add(
                            Allocation(
                                type = "PLATFORM_USER",
                                uid = myUid,
                                name = myName,
                                amount = myAmount,
                                reimbursed = (myUid == payerUid)
                            )
                        )
                    }
                    allocations.add(
                        Allocation(
                            type = "PLATFORM_USER",
                            uid = assignedFriendUid,
                            name = assignedFriendName,
                            amount = friendAmount,
                            reimbursed = false
                        )
                    )
                } else {
                    allocations.add(
                        Allocation(
                            type = "PLATFORM_USER",
                            uid = myUid,
                            name = myName,
                            amount = round2(total),
                            reimbursed = true
                        )
                    )
                }
            }
            SplitType.EXTERNAL -> {
                val label = externalName?.takeIf { it.isNotBlank() } ?: "Terceiro"
                allocations.add(
                    Allocation(
                        type = "EXTERNAL",
                        name = label,
                        amount = round2(total),
                        reimbursed = false
                    )
                )
            }
            SplitType.CUSTOM -> {
                allocations.add(
                    Allocation(
                        type = "PLATFORM_USER",
                        uid = myUid,
                        name = myName,
                        amount = round2(total),
                        reimbursed = true
                    )
                )
            }
        }

        val visibleList = visible.toList()
        return SplitResult(
            allocations = allocations,
            userImpact = deriveImpact(allocations, visibleList),
            visibleToUids = visibleList
        )
    }

    fun impactFor(tx: Transaction, uid: String, all: List<Transaction>): Double {
        if (!tx.reserveCardId.isNullOrBlank()) {
            if (tx.createdByUid != uid) return 0.0
            val reserve = effectiveAmount(tx)
            val consumed = round2(
                all.filter { o ->
                    o.id != tx.id &&
                            o.reserveCardId.isNullOrBlank() &&
                            o.type == TransactionType.EXPENSE &&
                            o.cardId == tx.reserveCardId &&
                            o.competenceMonth == tx.competenceMonth
                }.sumOf { it.userImpact[tx.createdByUid] ?: 0.0 }
            )
            return round2(max(0.0, reserve - consumed))
        }

        if (!tx.assignedFriendUid.isNullOrBlank()) {
            val friendAlloc = tx.allocations.filter { it.uid == tx.assignedFriendUid }
            val friendShare = round2(friendAlloc.sumOf { it.amount }.takeIf { it > 0 } ?: tx.totalAmount)

            if (tx.acceptanceStatus == "accepted") {
                if (uid == tx.assignedFriendUid) return friendShare
                return tx.userImpact[uid] ?: 0.0
            }

            if (uid == tx.assignedFriendUid) return 0.0
            val own = tx.userImpact[uid] ?: 0.0
            return if (uid == tx.createdByUid) round2(own + friendShare) else own
        }

        return tx.userImpact[uid] ?: 0.0
    }

    fun computeSummary(
        all: List<Transaction>,
        uid: String,
        monthKey: String,
        cards: List<Card> = emptyList()
    ): MonthSummary {
        val monthTxs = all.filter { it.competenceMonth == monthKey && it.visibleToUids.contains(uid) }

        var income = 0.0
        var paid = 0.0
        var committedEstimated = 0.0
        var committedPending = 0.0

        var receivableFromPartner = 0.0
        var receivableFromExternal = 0.0
        var receivableFromFriends = 0.0
        var payableToOthers = 0.0
        var totalInvested = 0.0

        val categoryMap = mutableMapOf<String, Double>()
        val investSubcatMap = mutableMapOf<String, Double>()
        val receivablesList = mutableListOf<ReceivableItem>()

        val cardMap = cards.associateBy { it.id }

        for (tx in monthTxs) {
            val impact = impactFor(tx, uid, all)

            if (tx.type == TransactionType.INCOME) {
                income += impact
                continue
            }

            when (tx.status) {
                TransactionStatus.PAID -> paid += impact
                TransactionStatus.ESTIMATED -> committedEstimated += impact
                TransactionStatus.PENDING -> committedPending += impact
            }

            if (impact > 0.0) {
                categoryMap[tx.category] = (categoryMap[tx.category] ?: 0.0) + impact

                if (tx.category == "Investimentos") {
                    totalInvested += impact
                    val sub = tx.subcategory ?: "Outros Investimentos"
                    investSubcatMap[sub] = (investSubcatMap[sub] ?: 0.0) + impact
                }
            }

            // A Receber / A Pagar logic
            if (tx.paidByUid == uid) {
                for (a in tx.allocations) {
                    if (a.reimbursed) continue
                    if (a.type == "EXTERNAL") {
                        receivableFromExternal += a.amount
                        receivablesList.add(
                            ReceivableItem(
                                transactionId = tx.id,
                                description = tx.description,
                                dueDate = tx.dueDate,
                                name = a.name,
                                amount = a.amount,
                                type = "EXTERNAL",
                                cardName = tx.cardId?.let { cardMap[it]?.cardName },
                                reimbursed = false
                            )
                        )
                    } else if (a.uid != uid) {
                        if (tx.assignedFriendUid == a.uid) {
                            receivableFromFriends += a.amount
                        } else {
                            receivableFromPartner += a.amount
                        }
                        receivablesList.add(
                            ReceivableItem(
                                transactionId = tx.id,
                                description = tx.description,
                                dueDate = tx.dueDate,
                                name = a.name,
                                amount = a.amount,
                                type = "PLATFORM_USER",
                                cardName = tx.cardId?.let { cardMap[it]?.cardName },
                                reimbursed = false
                            )
                        )
                    }
                }
            } else {
                for (a in tx.allocations) {
                    if (a.type == "PLATFORM_USER" && a.uid == uid && !a.reimbursed) {
                        payableToOthers += a.amount
                    }
                }
            }
        }

        val curMonth = currentMonthKey()
        val monthState = when {
            monthKey == curMonth -> "current"
            monthKey < curMonth -> "past"
            else -> "future"
        }

        val dim = daysInMonth(monthKey)
        val daysRemaining = when (monthState) {
            "past" -> 0
            "future" -> dim
            else -> {
                val nowDay = LocalDate.now().dayOfMonth
                max(0, dim - nowDay + 1)
            }
        }

        val committed = committedEstimated + committedPending
        val freeBalance = income - (paid + committed)
        val dailyLimit = if (daysRemaining > 0) round2(freeBalance / daysRemaining) else null

        val byCategory = categoryMap.entries.map {
            CategoryExpense(category = it.key, amount = round2(it.value))
        }.sortedByDescending { it.amount }

        val byInvestSub = investSubcatMap.entries.map {
            InvestmentSubcat(
                subcategory = it.key,
                amount = round2(it.value),
                percentage = if (totalInvested > 0) ((it.value / totalInvested) * 100).toInt() else 0
            )
        }.sortedByDescending { it.amount }

        return MonthSummary(
            monthKey = monthKey,
            income = round2(income),
            paid = round2(paid),
            committed = round2(committed),
            committedEstimated = round2(committedEstimated),
            committedPending = round2(committedPending),
            freeBalance = round2(freeBalance),
            daysRemaining = daysRemaining,
            daysInMonth = dim,
            dailyLimit = dailyLimit,
            monthState = monthState,
            receivableTotal = round2(receivableFromPartner + receivableFromExternal + receivableFromFriends),
            receivableFromPartner = round2(receivableFromPartner),
            receivableFromExternal = round2(receivableFromExternal),
            receivableFromFriends = round2(receivableFromFriends),
            payableToOthers = round2(payableToOthers),
            totalInvested = round2(totalInvested),
            byCategory = byCategory,
            byInvestmentSubcategory = byInvestSub,
            receivables = receivablesList
        )
    }
}
