package com.saldolivre.app.data.engine

import com.saldolivre.app.data.model.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object FinanceEngine {

    private val ptBrLocale = Locale("pt", "BR")
    private val currencyFormatThreadLocal = object : ThreadLocal<NumberFormat>() {
        override fun initialValue(): NumberFormat = NumberFormat.getCurrencyInstance(ptBrLocale)
    }
    private val monthLabelFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBrLocale)
    private val installmentSuffixRegex = Regex("""\s*\(\d+/\d+\)\s*$""")
    private val fullDateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val yearMonthRegex = Regex("""^\d{4}-\d{2}$""")

    fun formatCurrency(amount: Double): String {
        return currencyFormatThreadLocal.get()?.format(amount)
            ?: String.format(ptBrLocale, "R$ %,.2f", amount)
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

    /**
     * Formata a data atual no padrão brasileiro completo DD/MM/YYYY.
     */
    fun todayFormatted(): String = formatFullDate(todayKey())

    /**
     * Formata uma data (YYYY-MM-DD ou DD/MM/YYYY) para exibição curta no padrão brasileiro DD/MM.
     */
    fun formatShortDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val clean = dateStr.trim()
        if (clean.contains("-")) {
            val parts = clean.split("-")
            if (parts.size >= 3) {
                val day = parts[2].take(2).padStart(2, '0')
                val month = parts[1].padStart(2, '0')
                return "$day/$month"
            } else if (parts.size == 2) {
                return "${parts[1].padStart(2, '0')}/${parts[0]}"
            }
        }
        if (clean.contains("/")) {
            val parts = clean.split("/")
            if (parts.size >= 2) {
                return "${parts[0].padStart(2, '0')}/${parts[1].padStart(2, '0')}"
            }
        }
        return clean
    }

    /**
     * Formata uma data (YYYY-MM-DD ou DD/MM/YYYY) para exibição completa no padrão brasileiro DD/MM/YYYY.
     */
    fun formatFullDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val clean = dateStr.trim()
        if (clean.contains("-")) {
            val parts = clean.split("-")
            if (parts.size >= 3) {
                val year = parts[0]
                val month = parts[1].padStart(2, '0')
                val day = parts[2].take(2).padStart(2, '0')
                return "$day/$month/$year"
            } else if (parts.size == 2) {
                return "${parts[1].padStart(2, '0')}/${parts[0]}"
            }
        }
        if (clean.contains("/")) {
            val parts = clean.split("/")
            if (parts.size == 3) {
                val day = parts[0].padStart(2, '0')
                val month = parts[1].padStart(2, '0')
                val year = parts[2]
                return "$day/$month/$year"
            }
        }
        return clean
    }

    /**
     * Converte uma data informada pelo usuário (DD/MM/YYYY, DD/MM ou YYYY-MM-DD) para ISO YYYY-MM-DD.
     */
    fun parseInputDateToIso(dateInput: String?): String {
        if (dateInput.isNullOrBlank()) return todayKey()
        val clean = dateInput.trim()
        if (clean.matches(fullDateRegex)) return clean
        if (clean.contains("/")) {
            val parts = clean.split("/")
            if (parts.size == 3) {
                val day = parts[0].padStart(2, '0')
                val month = parts[1].padStart(2, '0')
                val year = if (parts[2].length == 2) "20${parts[2]}" else parts[2].padStart(4, '0')
                return "$year-$month-$day"
            } else if (parts.size == 2) {
                val day = parts[0].padStart(2, '0')
                val month = parts[1].padStart(2, '0')
                val year = LocalDate.now().year
                return String.format(Locale.US, "%04d-%s-%s", year, month, day)
            }
        }
        if (clean.contains("-")) {
            val parts = clean.split("-")
            if (parts.size == 3) {
                return if (parts[0].length == 4) {
                    "${parts[0]}-${parts[1].padStart(2, '0')}-${parts[2].padStart(2, '0')}"
                } else {
                    val year = if (parts[2].length == 2) "20${parts[2]}" else parts[2].padStart(4, '0')
                    "$year-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
                }
            }
        }
        return todayKey()
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

    /**
     * Gera YYYY-MM-DD para um dia específico do mês, limitando ao último dia válido do mês.
     */
    fun dateInMonth(monthKey: String, day: Int): String {
        val maxDays = daysInMonth(monthKey)
        val clampedDay = day.coerceIn(1, maxDays)
        return String.format(Locale.US, "%s-%02d", monthKey, clampedDay)
    }

    fun formatMonthLabel(monthKey: String): String {
        return try {
            val parts = monthKey.split("-")
            val ym = YearMonth.of(parts[0].toInt(), parts[1].toInt())
            ym.format(monthLabelFormatter).replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            monthKey
        }
    }

    fun monthLabel(monthKey: String): String = formatMonthLabel(monthKey)

    /**
     * Remove o sufixo numérico de parcelamento como " (1/10)" do final da descrição.
     */
    fun stripInstallmentSuffix(desc: String?): String {
        if (desc.isNullOrBlank()) return ""
        return desc.replace(installmentSuffixRegex, "").trim()
    }

    /**
     * Formata a descrição da parcela com a numeração correspondente (ex: "Celular (3/10)").
     */
    fun formatInstallmentDesc(baseDesc: String?, num: Int, total: Int): String {
        val clean = stripInstallmentSuffix(baseDesc)
        return if (clean.isNotBlank()) "$clean ($num/$total)" else "($num/$total)"
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

    fun shiftDateMonth(dateStr: String?, delta: Int): String {
        val key = if (!dateStr.isNullOrBlank() && dateStr.contains("-")) dateStr.trim() else todayKey()
        return try {
            val parts = key.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = if (parts.size >= 3) parts[2].take(2).toInt() else 1
            val ym = YearMonth.of(year, month).plusMonths(delta.toLong())
            val clampedDay = kotlin.math.min(day, ym.lengthOfMonth())
            String.format(Locale.US, "%04d-%02d-%02d", ym.year, ym.monthValue, clampedDay)
        } catch (_: Exception) {
            key
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
        val normalizedPurchaseDate = parseInputDateToIso(purchaseDate)
        if (!explicitDueDate.isNullOrBlank()) {
            val cleanDue = parseInputDateToIso(explicitDueDate)
            val cleanMonth = if (!explicitMonth.isNullOrBlank() && explicitMonth.matches(yearMonthRegex)) {
                explicitMonth.trim()
            } else {
                cleanDue.substring(0, 7)
            }
            return Pair(cleanDue, cleanMonth)
        }

        if (card == null || card.closingDay <= 0 || card.dueDay <= 0) {
            val m = if (normalizedPurchaseDate.length >= 7) normalizedPurchaseDate.substring(0, 7) else currentMonthKey()
            return Pair(normalizedPurchaseDate, m)
        }

        try {
            val parts = normalizedPurchaseDate.split("-")
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
        friendSharePct: Double? = null,
        customAllocations: List<Allocation>? = null
    ): SplitResult {
        val allocations = mutableListOf<Allocation>()
        val visible = mutableSetOf(myUid, payerUid)
        if (!assignedFriendUid.isNullOrBlank()) {
            visible.add(assignedFriendUid)
        }

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
                if (!customAllocations.isNullOrEmpty()) {
                    for (a in customAllocations) {
                        allocations.add(
                            Allocation(
                                type = a.type,
                                uid = a.uid,
                                name = a.name.ifBlank { "Participante" },
                                amount = round2(a.amount),
                                reimbursed = (a.uid == payerUid)
                            )
                        )
                        if (!a.uid.isNullOrBlank()) {
                            visible.add(a.uid)
                        }
                    }
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
        }

        val visibleList = visible.toList()
        return SplitResult(
            allocations = allocations,
            userImpact = deriveImpact(allocations, visibleList),
            visibleToUids = visibleList
        )
    }

    /**
     * Resolve o parceiro a partir dos cartões compartilhados ou da lista de usuários.
     */
    fun resolvePartner(meUid: String, cards: List<Card>, allUsers: List<UserProfile>): UserProfile? {
        for (c in cards) {
            if (c.ownerUid == meUid && c.sharedWithUsers.isNotEmpty()) {
                val s = c.sharedWithUsers.firstOrNull()
                if (s != null) {
                    val matched = allUsers.find { it.uid == s.uid }
                    return matched ?: UserProfile(uid = s.uid, displayName = s.name, email = s.email)
                }
            }
            if (c.ownerUid != meUid && c.sharedWithUids.contains(meUid)) {
                val matched = allUsers.find { it.uid == c.ownerUid }
                return matched ?: UserProfile(uid = c.ownerUid, displayName = c.ownerName, email = "")
            }
        }
        return allUsers.find { it.uid != meUid && it.uid.isNotBlank() }
    }

    val STATUS_ORDER: List<TransactionStatus> = listOf(
        TransactionStatus.ESTIMATED,
        TransactionStatus.PENDING,
        TransactionStatus.PAID
    )

    fun nextStatus(status: TransactionStatus): TransactionStatus {
        val idx = STATUS_ORDER.indexOf(status).coerceAtLeast(0)
        return STATUS_ORDER[(idx + 1) % STATUS_ORDER.size]
    }

    fun togglePaidStatus(status: TransactionStatus): TransactionStatus {
        return if (status == TransactionStatus.PAID) TransactionStatus.PENDING else TransactionStatus.PAID
    }

    fun rescaleSplit(tx: Transaction, newTotal: Double): Transaction {
        val allocs = tx.allocations
        val oldTotal = allocs.sumOf { it.amount }
        val roundedTotal = round2(newTotal)
        val newAllocs = when {
            allocs.isEmpty() -> emptyList()
            oldTotal <= 0.0 -> {
                val each = round2(roundedTotal / allocs.size)
                allocs.map { it.copy(amount = each) }
            }
            else -> {
                val ratio = roundedTotal / oldTotal
                var acc = 0.0
                allocs.mapIndexed { idx, a ->
                    if (idx == allocs.lastIndex) {
                        a.copy(amount = round2(roundedTotal - acc))
                    } else {
                        val amt = round2(a.amount * ratio)
                        acc += amt
                        a.copy(amount = amt)
                    }
                }
            }
        }
        return tx.copy(
            totalAmount = roundedTotal,
            allocations = newAllocs,
            userImpact = deriveImpact(newAllocs, tx.visibleToUids)
        )
    }

    fun applyStatusPatch(tx: Transaction, newStatus: TransactionStatus, customActualAmount: Double? = null): Transaction {
        val resolvedActual = when {
            customActualAmount != null && customActualAmount > 0.0 -> round2(customActualAmount)
            newStatus == TransactionStatus.PAID && tx.actualAmount == null -> round2(tx.estimatedAmount ?: tx.totalAmount)
            else -> tx.actualAmount
        }
        val base = if (customActualAmount != null && customActualAmount > 0.0) {
            rescaleSplit(tx, customActualAmount)
        } else if (resolvedActual != null && resolvedActual != tx.totalAmount) {
            rescaleSplit(tx, resolvedActual)
        } else {
            tx
        }
        return base.copy(
            status = newStatus,
            actualAmount = resolvedActual
        )
    }

    fun friendShareOf(tx: Transaction): Double {
        if (tx.assignedFriendUid.isNullOrBlank()) return 0.0
        val friendAlloc = tx.allocations.filter { it.uid == tx.assignedFriendUid }
        val allocSum = friendAlloc.sumOf { it.amount }
        if (allocSum > 0.0) {
            return round2(allocSum)
        }

        // Fallback 1: se userImpact tem o assignedFriendUid com valor > 0
        val impactVal = tx.userImpact[tx.assignedFriendUid]
        if (impactVal != null && impactVal > 0.0) {
            return round2(impactVal)
        }

        // Fallback 2: se tem friendSharePct
        if (tx.friendSharePct != null && tx.friendSharePct > 0.0) {
            val total = effectiveAmount(tx)
            return round2((total * (tx.friendSharePct / 100.0)))
        }

        // Fallback 3: total da transação
        return round2(effectiveAmount(tx))
    }

    fun detectSplit(tx: Transaction, viewerUid: String): SplitType {
        if (tx.splitType != null) return tx.splitType
        val allocs = tx.allocations
        if (allocs.size > 2) return SplitType.CUSTOM
        if (!tx.assignedFriendUid.isNullOrBlank() || !tx.assignedFriendName.isNullOrBlank()) return SplitType.FRIEND
        if (allocs.any { it.type == "EXTERNAL" }) return SplitType.EXTERNAL
        if (allocs.isEmpty()) {
            return SplitType.MINE
        }
        val users = allocs.filter { it.type == "PLATFORM_USER" }
        val mine = users.any { it.uid == viewerUid || it.name.equals("você", true) || it.name.equals("meu", true) }
        val others = users.any { it.uid != viewerUid && !it.name.equals("você", true) && !it.name.equals("meu", true) }
        if (mine && others) {
            val myAlloc = users.find { it.uid == viewerUid || it.name.equals("você", true) || it.name.equals("meu", true) }?.amount ?: 0.0
            val otherAlloc = users.find { it.uid != viewerUid && !it.name.equals("você", true) && !it.name.equals("meu", true) }?.amount ?: 0.0
            if (kotlin.math.abs(myAlloc - otherAlloc) > 0.05) return SplitType.CUSTOM
            return SplitType.SPLIT_50
        }
        if (mine) return SplitType.MINE
        return SplitType.PARTNER
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

        // Caso com amigo vinculado
        if (!tx.assignedFriendUid.isNullOrBlank()) {
            val friendShare = friendShareOf(tx)

            if (tx.acceptanceStatus == "accepted") {
                // Amigo aceitou: amigo sofre a cota dele
                if (uid == tx.assignedFriendUid) return friendShare
                // Se o próprio usuário tem um valor explícito em userImpact
                val ownImpact = tx.userImpact[uid]
                if (ownImpact != null) return ownImpact
                // Se o usuário foi quem pagou ou criou, o impacto dele é o total da compra menos o que o amigo deve
                if (uid == tx.createdByUid || uid == tx.paidByUid) {
                    return round2(max(0.0, effectiveAmount(tx) - friendShare))
                }
                return 0.0
            }

            // Amigo ainda não aceitou (pendente ou recusado)
            if (uid == tx.assignedFriendUid) return 0.0
            // Quem lançou tem apenas a sua própria parte (a cota do amigo é valor a receber em receivables, não despesa própria)
            val ownImpact = tx.userImpact[uid] ?: if (uid == tx.createdByUid || uid == tx.paidByUid) {
                round2(max(0.0, effectiveAmount(tx) - friendShare))
            } else 0.0
            return ownImpact
        }

        // Se userImpact possui o UID (inclusive se for 0.0, ex: compras para terceiros ou parceiro)
        val directImpact = tx.userImpact[uid]
        if (directImpact != null) {
            return directImpact
        }

        // Fallbacks para dados legados sem userImpact explícito:
        // Se temos allocations explícitas cadastradas
        if (tx.allocations.isNotEmpty()) {
            val userAllocs = tx.allocations.filter {
                it.type == "PLATFORM_USER" && (it.uid == uid || it.name.equals("você", true) || it.name.equals("meu", true))
            }
            if (userAllocs.isNotEmpty()) {
                return round2(userAllocs.sumOf { it.amount })
            }
            // Se nenhuma alocação pertence ao usuário (ex: EXTERNAL ou apenas outro usuário), não há consumo
            return 0.0
        }

        // Terceiro Externo: nunca consome o saldo livre do usuário (é 100% valor a receber em receivables)
        if (tx.splitType == SplitType.EXTERNAL) {
            return 0.0
        }

        // Divisão 50/50
        if (tx.splitType == SplitType.SPLIT_50) {
            if (tx.visibleToUids.contains(uid) || uid == tx.createdByUid || uid == tx.paidByUid) {
                return round2(effectiveAmount(tx) / 2.0)
            }
            return 0.0
        }

        // 100% Parceiro
        if (tx.splitType == SplitType.PARTNER) {
            if (uid == tx.createdByUid || uid == tx.paidByUid) return 0.0
            if (tx.visibleToUids.contains(uid)) return effectiveAmount(tx)
            return 0.0
        }

        // 100% Meu
        if (tx.splitType == SplitType.MINE) {
            if (uid == tx.createdByUid || uid == tx.paidByUid) return effectiveAmount(tx)
            return 0.0
        }

        // Padrão de segurança: se criado ou pago pelo usuário, impacta o valor total; senão, 0
        return if (uid == tx.createdByUid || uid == tx.paidByUid) effectiveAmount(tx) else 0.0
    }

    fun computeMonthRawBalance(
        all: List<Transaction>,
        uid: String,
        monthKey: String
    ): Double {
        val monthTxs = all.filter { it.competenceMonth == monthKey && it.visibleToUids.contains(uid) }
        var inc = 0.0
        var exp = 0.0
        for (tx in monthTxs) {
            val impact = impactFor(tx, uid, all)
            if (tx.type == TransactionType.INCOME) {
                inc += impact
            } else {
                exp += impact
            }
        }
        return round2(inc - exp)
    }

    fun computeSummary(
        all: List<Transaction>,
        uid: String,
        monthKey: String,
        cards: List<Card> = emptyList(),
        closures: List<MonthClosure> = emptyList()
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
        val rawFreeBalance = round2(income - (paid + committed))

        var carryoverAmount = 0.0
        var previousMonthDeficit = 0.0
        var cushionAmount = 0.0
        var pendingClosureSurplus: Double? = null
        var pendingClosureMonth: String? = null

        // Mês futuro NUNCA antecipa sobra nem herda provisoriamente
        if (monthState != "future") {
            val prevMonthKey = shiftMonth(monthKey, -1)
            val prevClosure = closures.firstOrNull { it.closedMonth == prevMonthKey && it.uid == uid }

            if (prevClosure != null) {
                // Já houve fechamento formal do mês imediatamente anterior
                carryoverAmount = round2(prevClosure.carryoverToNextMonth)
                cushionAmount = round2(prevClosure.cushionAmount)
            } else {
                // Cadeia contínua de saldo acumulado:
                // Itera mês a mês desde o primeiro registro no passado até o mês anterior imediato
                val pastMonths = all
                    .filter { it.visibleToUids.contains(uid) && it.competenceMonth < monthKey }
                    .map { it.competenceMonth }
                    .distinct()
                    .sorted()

                if (pastMonths.isNotEmpty()) {
                    val startMonth = pastMonths.first()
                    val endMonth = prevMonthKey
                    var runningBalance = 0.0
                    var currentIter = startMonth

                    while (currentIter <= endMonth) {
                        val closure = closures.firstOrNull { it.closedMonth == currentIter && it.uid == uid }
                        val raw = computeMonthRawBalance(all, uid, currentIter)
                        val finalBalance = runningBalance + raw

                        if (closure != null) {
                            // Mês foi fechado formalmente: reinicia a cadeia com a sobra destinada para o próximo
                            runningBalance = closure.carryoverToNextMonth
                        } else {
                            runningBalance = finalBalance
                        }

                        currentIter = shiftMonth(currentIter, 1)
                    }

                    if (runningBalance < 0.0) {
                        // Déficit acumulado total de toda a sequência passada abate o mês atual
                        previousMonthDeficit = round2(kotlin.math.abs(runningBalance))
                    } else if (runningBalance > 0.0 && monthState == "current") {
                        // Sobra acumulada total de meses anteriores aguardando destinação
                        pendingClosureSurplus = round2(runningBalance)
                        pendingClosureMonth = prevMonthKey
                    }
                }
            }
        }

        val effectiveAvailable = income + carryoverAmount - previousMonthDeficit - cushionAmount
        val freeBalance = round2(effectiveAvailable - (paid + committed))
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

        val thisMonthClosure = closures.firstOrNull { it.closedMonth == monthKey && it.uid == uid }

        return MonthSummary(
            monthKey = monthKey,
            income = round2(income),
            paid = round2(paid),
            committed = round2(committed),
            committedEstimated = round2(committedEstimated),
            committedPending = round2(committedPending),
            freeBalance = round2(freeBalance),
            rawFreeBalance = rawFreeBalance,
            cushionAmount = cushionAmount,
            carryoverAmount = carryoverAmount,
            previousMonthDeficit = previousMonthDeficit,
            pendingClosureSurplus = pendingClosureSurplus,
            pendingClosureMonth = pendingClosureMonth,
            closure = thisMonthClosure,
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

    data class EstimateResult(
        val amount: Double,
        val basis: String
    )

    /**
     * Calcula o valor estimado para uma conta recorrente com base na sua regra
     * ('fixed', 'last_month' ou 'avg_3_months'), espelhando finance.ts na web.
     */
    fun estimateRecurring(bill: RecurringBill, all: List<Transaction>, monthKey: String): EstimateResult {
        fun fromMonth(offset: Int): Transaction? {
            val targetMonth = shiftMonth(monthKey, -offset)
            return all.find { it.recurringBillId == bill.id && it.competenceMonth == targetMonth }
        }

        return when (bill.estimationRule) {
            EstimationRule.LAST_MONTH -> {
                val prev = fromMonth(1)
                val v = prev?.actualAmount ?: prev?.estimatedAmount
                if (v != null) {
                    EstimateResult(round2(v), "Valor real do mês anterior")
                } else {
                    EstimateResult(bill.defaultAmount, "Sem histórico — valor padrão")
                }
            }
            EstimationRule.AVG_3_MONTHS -> {
                val values = listOf(1, 2, 3)
                    .mapNotNull { fromMonth(it) }
                    .filter { it.actualAmount != null }
                    .map { it.actualAmount!! }
                if (values.isNotEmpty()) {
                    val avg = round2(values.sum() / values.size)
                    val suffix = if (values.size == 1) "mês" else "meses"
                    EstimateResult(avg, "Média de ${values.size} $suffix pagos")
                } else {
                    EstimateResult(bill.defaultAmount, "Sem histórico — valor padrão")
                }
            }
            EstimationRule.FIXED -> {
                EstimateResult(bill.defaultAmount, "Valor fixo")
            }
        }
    }

    data class GenerateBillsResult(
        val created: List<Transaction>,
        val skipped: Int
    )

    /**
     * Gera as contas e salários previstos para o mês especificado,
     * ignorando contas já geradas ou fora do período de vigência.
     */
    fun generateMonthBills(
        bills: List<RecurringBill>,
        all: List<Transaction>,
        monthKey: String,
        me: UserProfile,
        partner: UserProfile? = null
    ): GenerateBillsResult {
        val created = mutableListOf<Transaction>()
        var skipped = 0

        // 1. Receita mensal (salário) a partir do perfil
        if (me.monthlyIncome > 0) {
            val salaryId = "salary_${me.uid}"
            val exists = all.any { it.recurringBillId == salaryId && it.competenceMonth == monthKey }
            if (!exists) {
                val split = buildSplit(
                    splitType = SplitType.MINE,
                    total = me.monthlyIncome,
                    myUid = me.uid,
                    myName = me.displayName,
                    partnerUid = null,
                    partnerName = null,
                    payerUid = me.uid
                )
                created.add(
                    Transaction(
                        id = "tx_gen_${UUID.randomUUID().toString().take(8)}",
                        createdByUid = me.uid,
                        paidByUid = me.uid,
                        description = "Salário",
                        category = "Receita",
                        type = TransactionType.INCOME,
                        paymentMethod = PaymentMethod.PIX,
                        status = TransactionStatus.ESTIMATED,
                        estimatedAmount = me.monthlyIncome,
                        actualAmount = null,
                        totalAmount = me.monthlyIncome,
                        dueDate = dateInMonth(monthKey, 5),
                        competenceMonth = monthKey,
                        recurringBillId = salaryId,
                        allocations = split.allocations,
                        userImpact = split.userImpact,
                        visibleToUids = split.visibleToUids,
                        createdAt = System.currentTimeMillis()
                    )
                )
            } else {
                skipped++
            }
        }

        // 2. Contas recorrentes ativas do usuário
        val activeBills = bills.filter { it.active && it.ownerUid == me.uid }
        for (bill in activeBills) {
            // Feature 1: Limite de meses da recorrência/parcelas
            if (!bill.startMonth.isNullOrBlank() && monthKey < bill.startMonth) {
                skipped++
                continue
            }
            if (!bill.endMonth.isNullOrBlank() && monthKey > bill.endMonth) {
                skipped++
                continue
            }

            val exists = all.any { it.recurringBillId == bill.id && it.competenceMonth == monthKey }
            if (exists) {
                skipped++
                continue
            }

            val estimate = estimateRecurring(bill, all, monthKey)
            val amount = estimate.amount
            val split = buildSplit(
                splitType = bill.splitType ?: SplitType.MINE,
                total = amount,
                myUid = me.uid,
                myName = me.displayName,
                partnerUid = partner?.uid,
                partnerName = partner?.displayName,
                payerUid = me.uid
            )
            val isIncome = bill.type == TransactionType.INCOME

            created.add(
                Transaction(
                    id = "tx_gen_${UUID.randomUUID().toString().take(8)}",
                    createdByUid = me.uid,
                    paidByUid = me.uid,
                    description = bill.description,
                    category = if (isIncome) "Receita" else bill.category,
                    subcategory = bill.subcategory,
                    type = bill.type,
                    paymentMethod = bill.paymentMethod ?: if (isIncome) PaymentMethod.PIX else PaymentMethod.BILL,
                    status = TransactionStatus.ESTIMATED,
                    estimatedAmount = amount,
                    actualAmount = null,
                    totalAmount = amount,
                    dueDate = dateInMonth(monthKey, bill.dueDay),
                    competenceMonth = monthKey,
                    recurringBillId = bill.id,
                    allocations = split.allocations,
                    userImpact = split.userImpact,
                    visibleToUids = split.visibleToUids,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        return GenerateBillsResult(created = created, skipped = skipped)
    }

    /**
     * Projeta os próximos N meses com base nas transações existentes e moldes de contas ativas,
     * espelhando projectFutureMonths de finance.ts na web.
     */
    fun projectFutureMonths(
        all: List<Transaction>,
        bills: List<RecurringBill>,
        profile: UserProfile?,
        uid: String,
        startMonthKey: String,
        count: Int = 6
    ): List<FutureMonthProjection> {
        val projections = mutableListOf<FutureMonthProjection>()

        for (i in 0 until count) {
            val monthKey = shiftMonth(startMonthKey, i)
            val label = formatMonthLabel(monthKey)
            val dim = daysInMonth(monthKey)

            val monthTxs = all.filter { it.competenceMonth == monthKey && it.visibleToUids.contains(uid) }
            val isGenerated = monthTxs.any { !it.recurringBillId.isNullOrBlank() }

            var projectedIncome = 0.0
            var projectedExpense = 0.0
            var incomeCount = 0
            var expenseCount = 0

            // 1. Sempre inclui transações reais agendadas para aquele mês (ex: parcelas de compras com cartão)
            for (t in monthTxs) {
                val impact = impactFor(t, uid, all)
                if (t.type == TransactionType.INCOME) {
                    projectedIncome += impact
                    incomeCount++
                } else {
                    projectedExpense += impact
                    expenseCount++
                }
            }

            // 2. Se as contas do mês ainda não foram geradas, projeta o salário e os moldes ativos válidos
            if (!isGenerated) {
                val hasSalary = monthTxs.any { it.recurringBillId == "salary_$uid" }
                if (!hasSalary && profile != null && profile.monthlyIncome > 0) {
                    projectedIncome += profile.monthlyIncome
                    incomeCount++
                }

                for (bill in bills.filter { it.active && it.ownerUid == uid }) {
                    if (monthTxs.any { it.recurringBillId == bill.id }) continue

                    if (!bill.startMonth.isNullOrBlank() && monthKey < bill.startMonth) continue
                    if (!bill.endMonth.isNullOrBlank() && monthKey > bill.endMonth) continue

                    val estimate = estimateRecurring(bill, all, monthKey)
                    val amount = estimate.amount
                    val myFraction = if (bill.splitType == SplitType.SPLIT_50) round2(amount / 2.0) else amount

                    if (bill.type == TransactionType.INCOME) {
                        projectedIncome += myFraction
                        incomeCount++
                    } else {
                        projectedExpense += myFraction
                        expenseCount++
                    }
                }
            }

            projectedIncome = round2(projectedIncome)
            projectedExpense = round2(projectedExpense)
            val projectedFreeBalance = round2(projectedIncome - projectedExpense)
            val projectedDailyLimit = if (dim > 0) round2(projectedFreeBalance / dim.toDouble()) else null

            projections.add(
                FutureMonthProjection(
                    monthKey = monthKey,
                    label = label,
                    daysInMonth = dim,
                    projectedIncome = projectedIncome,
                    projectedExpense = projectedExpense,
                    projectedFreeBalance = projectedFreeBalance,
                    projectedDailyLimit = projectedDailyLimit,
                    isGenerated = isGenerated,
                    incomeCount = incomeCount,
                    expenseCount = expenseCount
                )
            )
        }

        return projections
    }

    fun groupPendingCharges(
        transactions: List<Transaction>,
        currentUid: String,
        allUsers: List<UserProfile> = emptyList()
    ): List<PendingChargeGroup> {
        if (currentUid.isBlank()) return emptyList()

        val rawPending = transactions.filter {
            it.assignedFriendUid == currentUid && it.acceptanceStatus == "pending_approval"
        }

        val groups = mutableListOf<PendingChargeGroup>()
        val seenGroups = mutableSetOf<String>()

        for (tx in rawPending) {
            val gId = tx.installmentGroupId
            if (!gId.isNullOrBlank()) {
                if (seenGroups.contains(gId)) continue
                seenGroups.add(gId)

                val items = rawPending
                    .filter { it.installmentGroupId == gId }
                    .sortedBy { it.installmentNumber ?: 0 }

                val primaryTx = items.first()
                val baseDesc = stripInstallmentSuffix(primaryTx.description)
                val sender = allUsers.find { it.uid == primaryTx.createdByUid }
                val friendPct = primaryTx.friendSharePct ?: 100.0

                val totalAmount = round2(items.sumOf { it.totalAmount })
                val friendShareAmount = round2(items.sumOf { friendShareOf(it) })

                val dates = items.map { it.dueDate }.sorted()
                val firstDueDate = dates.firstOrNull() ?: primaryTx.dueDate
                val lastDueDate = dates.lastOrNull() ?: primaryTx.dueDate

                groups.add(
                    PendingChargeGroup(
                        id = gId,
                        isInstallment = true,
                        installmentGroupId = gId,
                        installmentsCount = items.size,
                        baseDescription = baseDesc,
                        cardId = primaryTx.cardId,
                        senderUid = primaryTx.createdByUid,
                        senderName = sender?.displayName ?: "Amigo",
                        totalAmount = totalAmount,
                        friendShareAmount = friendShareAmount,
                        friendSharePct = friendPct,
                        firstDueDate = firstDueDate,
                        lastDueDate = lastDueDate,
                        perInstallmentAmount = primaryTx.totalAmount,
                        perInstallmentFriendShare = friendShareOf(primaryTx),
                        items = items,
                        primaryTx = primaryTx
                    )
                )
            } else {
                val sender = allUsers.find { it.uid == tx.createdByUid }
                val friendPct = tx.friendSharePct ?: 100.0
                val friendShare = friendShareOf(tx)

                groups.add(
                    PendingChargeGroup(
                        id = tx.id,
                        isInstallment = false,
                        installmentGroupId = null,
                        installmentsCount = 1,
                        baseDescription = tx.description,
                        cardId = tx.cardId,
                        senderUid = tx.createdByUid,
                        senderName = sender?.displayName ?: "Amigo",
                        totalAmount = tx.totalAmount,
                        friendShareAmount = friendShare,
                        friendSharePct = friendPct,
                        firstDueDate = tx.dueDate,
                        lastDueDate = tx.dueDate,
                        perInstallmentAmount = tx.totalAmount,
                        perInstallmentFriendShare = friendShare,
                        items = listOf(tx),
                        primaryTx = tx
                    )
                )
            }
        }

        return groups
    }
}


