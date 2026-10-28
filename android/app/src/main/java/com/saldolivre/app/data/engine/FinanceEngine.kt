package com.saldolivre.app.data.engine

import com.saldolivre.app.data.model.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max
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

    data class NubankDateHeader(
        val title: String,
        val subtitle: String? = null,
        val isToday: Boolean = false,
        val isYesterday: Boolean = false
    )

    /**
     * Formata uma data para o cabeçalho de agrupamento estilo Nubank (ex: "HOJE", "ONTEM", "25 OUT", "14 SET").
     */
    fun formatNubankDateHeader(dateStr: String?, today: LocalDate = LocalDate.now()): NubankDateHeader {
        if (dateStr.isNullOrBlank()) return NubankDateHeader(title = "Sem data")
        val iso = parseInputDateToIso(dateStr)
        return try {
            val date = LocalDate.parse(iso)
            val isToday = date.isEqual(today)
            val isYesterday = date.isEqual(today.minusDays(1))

            val monthShort = when (date.monthValue) {
                1 -> "JAN"
                2 -> "FEV"
                3 -> "MAR"
                4 -> "ABR"
                5 -> "MAI"
                6 -> "JUN"
                7 -> "JUL"
                8 -> "AGO"
                9 -> "SET"
                10 -> "OUT"
                11 -> "NOV"
                12 -> "DEZ"
                else -> ""
            }
            val dayOfWeek = when (date.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "segunda-feira"
                java.time.DayOfWeek.TUESDAY -> "terça-feira"
                java.time.DayOfWeek.WEDNESDAY -> "quarta-feira"
                java.time.DayOfWeek.THURSDAY -> "quinta-feira"
                java.time.DayOfWeek.FRIDAY -> "sexta-feira"
                java.time.DayOfWeek.SATURDAY -> "sábado"
                java.time.DayOfWeek.SUNDAY -> "domingo"
                else -> ""
            }
            val dayFormatted = String.format(Locale.US, "%02d", date.dayOfMonth)
            val yearSuffix = if (date.year != today.year) " ${date.year}" else ""

            when {
                isToday -> NubankDateHeader(
                    title = "HOJE",
                    subtitle = "$dayFormatted $monthShort",
                    isToday = true
                )
                isYesterday -> NubankDateHeader(
                    title = "ONTEM",
                    subtitle = "$dayFormatted $monthShort",
                    isYesterday = true
                )
                else -> NubankDateHeader(
                    title = "$dayFormatted $monthShort$yearSuffix",
                    subtitle = dayOfWeek
                )
            }
        } catch (_: Exception) {
            NubankDateHeader(title = dateStr)
        }
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
        return stripInstallmentSuffix(baseDesc)
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
                    val half = round2(total / 2.0)
                    val otherHalf = round2(total - half)
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
                            type = "EXTERNAL",
                            uid = null,
                            name = partnerName?.ifBlank { "Parceiro(a)" } ?: "Parceiro(a)",
                            amount = otherHalf,
                            reimbursed = false
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
                            type = "EXTERNAL",
                            uid = null,
                            name = partnerName?.ifBlank { "Parceiro(a)" } ?: "Parceiro(a)",
                            amount = round2(total),
                            reimbursed = false
                        )
                    )
                }
            }
            SplitType.FRIEND -> {
                val fName = assignedFriendName?.takeIf { it.isNotBlank() } ?: "Amigo"
                val pct = (friendSharePct ?: 50.0).coerceIn(1.0, 100.0)
                val friendAmount = round2((total * pct) / 100.0)
                val myAmount = round2(total - friendAmount)

                if (Math.abs(myAmount) > 0.001) {
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
                        type = if (!assignedFriendUid.isNullOrBlank()) "PLATFORM_USER" else "EXTERNAL",
                        uid = assignedFriendUid,
                        name = fName,
                        amount = friendAmount,
                        reimbursed = false
                    )
                )
                if (!assignedFriendUid.isNullOrBlank()) {
                    visible.add(assignedFriendUid)
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
                        val signedAmount = if (total < 0.0) -Math.abs(a.amount) else Math.abs(a.amount)
                        allocations.add(
                            Allocation(
                                type = a.type,
                                uid = a.uid,
                                name = a.name.ifBlank { "Participante" },
                                amount = round2(signedAmount),
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

    fun rescaleSplit(tx: Transaction, newTotal: Double, viewerUid: String? = null): Transaction {
        val roundedTotal = round2(newTotal)
        val allocs = tx.allocations
        val allocSum = allocs.sumOf { it.amount }
        val hasMultipleValidAllocs = allocs.size > 1 && Math.abs(allocSum) > 0.001

        val newAllocs = mutableListOf<Allocation>()

        if (hasMultipleValidAllocs) {
            val ratio = roundedTotal / allocSum
            var acc = 0.0
            allocs.forEachIndexed { idx, a ->
                if (idx == allocs.lastIndex) {
                    newAllocs.add(a.copy(amount = round2(roundedTotal - acc)))
                } else {
                    val amt = round2(a.amount * ratio)
                    acc += amt
                    newAllocs.add(a.copy(amount = amt))
                }
            }
        } else if (!tx.assignedFriendUid.isNullOrBlank() || tx.splitType == SplitType.FRIEND) {
            val pct = (tx.friendSharePct ?: 50.0).coerceIn(1.0, 100.0)
            val friendAmount = round2((roundedTotal * pct) / 100.0)
            val myAmount = round2(roundedTotal - friendAmount)
            val fName = tx.assignedFriendName?.takeIf { it.isNotBlank() } ?: "Amigo"
            val myUid = viewerUid ?: tx.createdByUid.ifBlank { tx.paidByUid }

            if (Math.abs(myAmount) > 0.0001) {
                newAllocs.add(
                    Allocation(
                        type = "PLATFORM_USER",
                        uid = myUid,
                        name = "Você",
                        amount = myAmount,
                        reimbursed = true
                    )
                )
            }
            newAllocs.add(
                Allocation(
                    type = if (!tx.assignedFriendUid.isNullOrBlank()) "PLATFORM_USER" else "EXTERNAL",
                    uid = tx.assignedFriendUid,
                    name = fName,
                    amount = friendAmount,
                    reimbursed = false
                )
            )
        } else if (tx.splitType == SplitType.SPLIT_50) {
            val half = round2(roundedTotal / 2.0)
            val otherHalf = round2(roundedTotal - half)
            val myUid = viewerUid ?: tx.createdByUid.ifBlank { tx.paidByUid }
            val pName = tx.externalName?.takeIf { it.isNotBlank() } ?: "Parceiro(a)"

            newAllocs.add(
                Allocation(
                    type = "PLATFORM_USER",
                    uid = myUid,
                    name = "Você",
                    amount = half,
                    reimbursed = true
                )
            )
            newAllocs.add(
                Allocation(
                    type = "EXTERNAL",
                    uid = null,
                    name = pName,
                    amount = otherHalf,
                    reimbursed = false
                )
            )
        } else if (tx.splitType == SplitType.PARTNER) {
            newAllocs.add(
                Allocation(
                    type = "EXTERNAL",
                    uid = null,
                    name = tx.externalName?.takeIf { it.isNotBlank() } ?: "Parceiro(a)",
                    amount = roundedTotal,
                    reimbursed = false
                )
            )
        } else if (tx.splitType == SplitType.EXTERNAL || !tx.externalName.isNullOrBlank()) {
            newAllocs.add(
                Allocation(
                    type = "EXTERNAL",
                    uid = null,
                    name = tx.externalName?.takeIf { it.isNotBlank() } ?: "Terceiro",
                    amount = roundedTotal,
                    reimbursed = false
                )
            )
        } else if (allocs.isNotEmpty()) {
            val ratio = if (Math.abs(allocSum) > 0.001) roundedTotal / allocSum else 1.0
            var acc = 0.0
            allocs.forEachIndexed { idx, a ->
                if (idx == allocs.lastIndex) {
                    newAllocs.add(a.copy(amount = round2(roundedTotal - acc)))
                } else {
                    val amt = round2(a.amount * ratio)
                    acc += amt
                    newAllocs.add(a.copy(amount = amt))
                }
            }
        } else {
            val myUid = viewerUid ?: tx.createdByUid.ifBlank { tx.paidByUid }
            newAllocs.add(
                Allocation(
                    type = "PLATFORM_USER",
                    uid = myUid,
                    name = "Você",
                    amount = roundedTotal,
                    reimbursed = true
                )
            )
        }

        val visible = tx.visibleToUids.toMutableSet()
        newAllocs.forEach { a ->
            if (!a.uid.isNullOrBlank()) visible.add(a.uid)
        }

        return tx.copy(
            totalAmount = roundedTotal,
            allocations = newAllocs,
            userImpact = deriveImpact(newAllocs, visible.toList()),
            visibleToUids = visible.toList()
        )
    }

    /**
     * Cria uma transação de estorno vinculada a uma compra existente no cartão de crédito.
     * Replica fielmente a categoria, subcategoria, cartão, participantes e proporções de rateio,
     * gerando cotas negativas que abatem a fatura do cartão e o Saldo Livre.
     */
    fun createRefundTransaction(
        origTx: Transaction,
        refundAmount: Double,
        currentUser: UserProfile,
        todayIso: String = todayKey()
    ): Transaction {
        val absAmount = round2(Math.abs(refundAmount))
        val signedTotal = -absAmount

        val rescaled = rescaleSplit(origTx, signedTotal, currentUser.uid)
        val cleanDesc = stripInstallmentSuffix(origTx.description.removePrefix("Estorno:").trim())

        val friendAlloc = if (!origTx.assignedFriendUid.isNullOrBlank()) {
            rescaled.allocations.find { it.uid == origTx.assignedFriendUid }
        } else null
        val friendSharePct = if (friendAlloc != null && Math.abs(signedTotal) > 0.0) {
            round2((Math.abs(friendAlloc.amount) / Math.abs(signedTotal)) * 100.0)
        } else origTx.friendSharePct

        return Transaction(
            id = "tx_" + UUID.randomUUID().toString().take(8),
            createdByUid = currentUser.uid,
            paidByUid = origTx.paidByUid.ifBlank { currentUser.uid },
            cardId = origTx.cardId,
            description = "Estorno: $cleanDesc",
            category = origTx.category,
            subcategory = origTx.subcategory ?: "Crédito na Fatura",
            type = TransactionType.EXPENSE,
            paymentMethod = origTx.paymentMethod,
            status = TransactionStatus.PENDING,
            estimatedAmount = null,
            actualAmount = signedTotal,
            totalAmount = signedTotal,
            dueDate = todayIso.ifBlank { origTx.dueDate },
            competenceMonth = origTx.competenceMonth,
            visibleToUids = rescaled.visibleToUids.ifEmpty { listOf(currentUser.uid) },
            userImpact = rescaled.userImpact,
            allocations = rescaled.allocations,
            splitType = origTx.splitType ?: if (!origTx.assignedFriendUid.isNullOrBlank()) SplitType.FRIEND else SplitType.MINE,
            assignedFriendUid = origTx.assignedFriendUid,
            assignedFriendName = origTx.assignedFriendName,
            acceptanceStatus = if (!origTx.assignedFriendUid.isNullOrBlank()) "pending_approval" else null,
            friendSharePct = friendSharePct,
            externalName = origTx.externalName,
            refundedTransactionId = origTx.id,
            createdAt = System.currentTimeMillis()
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
        if (friendAlloc.isNotEmpty()) {
            return round2(friendAlloc.sumOf { it.amount })
        }

        // Fallback 1: se userImpact tem o assignedFriendUid com valor diferente de zero
        val impactVal = tx.userImpact[tx.assignedFriendUid]
        if (impactVal != null && Math.abs(impactVal) > 0.0001) {
            return round2(impactVal)
        }

        // Fallback 2: se tem friendSharePct
        val pct = (tx.friendSharePct ?: 50.0).coerceIn(1.0, 100.0)
        val total = effectiveAmount(tx)
        return round2((total * (pct / 100.0)))
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
                    return round2(effectiveAmount(tx) - friendShare)
                }
                return 0.0
            }

            // Amigo ainda não aceitou (pendente ou recusado)
            if (uid == tx.assignedFriendUid) return 0.0
            // Quem lançou tem apenas a sua própria parte (a cota do amigo é valor a receber em receivables, não despesa própria)
            val ownImpact = tx.userImpact[uid] ?: if (uid == tx.createdByUid || uid == tx.paidByUid) {
                round2(effectiveAmount(tx) - friendShare)
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

    /**
     * Verifica se a transação representa um gasto do usuário (impacta o Saldo Livre ou tem sua participação).
     * Inclui gastos 100% próprios e lançamentos rateados (50/50, amigos, divisões personalizadas).
     */
    fun isMyExpense(tx: Transaction, uid: String, all: List<Transaction> = emptyList()): Boolean {
        if (tx.type == TransactionType.INCOME) return false

        val myImp = kotlin.math.abs(impactFor(tx, uid, all))
        if (myImp > 0.001) return true

        if (tx.allocations.any {
            (it.uid == uid || it.name.equals("você", true) || it.name.equals("meu", true) || it.name.equals("100% meu", true)) && it.amount > 0
        }) {
            return true
        }

        if (tx.splitType == SplitType.SPLIT_50) {
            if (tx.visibleToUids.contains(uid) || tx.createdByUid == uid || tx.paidByUid == uid) {
                return true
            }
        }

        if (tx.splitType == SplitType.MINE) {
            if (tx.visibleToUids.contains(uid) || tx.createdByUid == uid || tx.paidByUid == uid) {
                return true
            }
        }

        if (tx.splitType == SplitType.FRIEND || !tx.assignedFriendUid.isNullOrBlank()) {
            if (uid == tx.assignedFriendUid && tx.acceptanceStatus == "accepted") return true
            if ((tx.createdByUid == uid || tx.paidByUid == uid) && friendShareOf(tx) < kotlin.math.abs(effectiveAmount(tx))) {
                return true
            }
        }

        if (tx.createdByUid == uid || tx.paidByUid == uid) {
            if (tx.splitType == SplitType.PARTNER) return false
            if (tx.splitType == SplitType.EXTERNAL) return false
            return true
        }

        return false
    }

    /**
     * Verifica se a transação é 100% exclusiva do usuário (sem divisão com parceiro, amigos ou terceiros).
     */
    fun isPureMine(tx: Transaction, uid: String, all: List<Transaction> = emptyList()): Boolean {
        if (!isMyExpense(tx, uid, all)) return false

        if (tx.splitType == SplitType.SPLIT_50) return false
        if (tx.splitType == SplitType.PARTNER) return false
        if (!tx.assignedFriendUid.isNullOrBlank() || !tx.assignedFriendName.isNullOrBlank()) return false
        if (!tx.externalName.isNullOrBlank() || tx.splitType == SplitType.EXTERNAL) return false

        if (tx.allocations.size > 1) {
            val hasOthers = tx.allocations.any {
                it.uid != uid && !it.name.equals("você", true) && !it.name.equals("meu", true) && !it.name.equals("100% meu", true) && it.amount > 0
            }
            if (hasOthers) return false
        }

        val myImp = kotlin.math.abs(impactFor(tx, uid, all))
        val eff = kotlin.math.abs(effectiveAmount(tx))
        if (eff > 0 && kotlin.math.abs(myImp - eff) > 0.05) return false

        return true
    }

    /**
     * Verifica se a transação é um gasto do usuário que foi rateado/compartilhado com parceiro, amigos ou terceiros.
     */
    fun isSharedWithMe(tx: Transaction, uid: String, all: List<Transaction> = emptyList()): Boolean {
        return isMyExpense(tx, uid, all) && !isPureMine(tx, uid, all)
    }

    /**
     * Determina se o usuário logado tem permissão para editar ou excluir a transação.
     * Um lançamento feito por outra pessoa NÃO pode ser editado nem excluído por quem
     * recebeu o rateio (amigo atribuído ou participante em rateio).
     */
    fun canUserEditTransaction(tx: Transaction, currentUid: String?): Boolean {
        if (currentUid.isNullOrBlank()) return false

        val isCreator = tx.createdByUid == currentUid || (tx.createdByUid.isNullOrBlank() && tx.paidByUid == currentUid)
        if (isCreator) return true

        if (tx.assignedFriendUid == currentUid) return false

        if (tx.allocations.any { it.uid == currentUid }) return false

        if (!tx.createdByUid.isNullOrBlank() && tx.createdByUid != currentUid) return false

        return true
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

        val nonCardTxs = monthTxs.filter { it.cardId.isNullOrBlank() || !cardMap.containsKey(it.cardId) }

        // 1. Não-cartões (Pix, Boleto, Salário, etc.)
        for (tx in nonCardTxs) {
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
        }

        // 2. Faturas de Cartão (comprometimento consolidado exato: myShare abatido de estornos/créditos)
        val processedCardIds = mutableSetOf<String>()
        val monthCardIds = (monthTxs.mapNotNull { it.cardId } + cards.map { it.id }).distinct()
        for (cardId in monthCardIds) {
            val card = cardMap[cardId] ?: continue
            if (processedCardIds.contains(cardId)) continue
            processedCardIds.add(cardId)

            val invoice = InvoiceEngine.buildInvoice(card, monthKey, all, uid)
            val myShare = invoice.myShare
            if (myShare > 0.0) {
                val paidPart = if (invoice.status == InvoiceStatus.PAID) {
                    myShare
                } else {
                    round2(kotlin.math.min(myShare, invoice.paidRatio * myShare))
                }
                val openPart = round2(max(0.0, myShare - paidPart))

                paid += paidPart
                if (invoice.status == InvoiceStatus.OPEN) {
                    committedEstimated += openPart
                } else {
                    committedPending += openPart
                }
            }
        }

        // 3. Categorias, Investimentos e A Receber / A Pagar (processa todas as despesas do mês)
        for (tx in monthTxs) {
            val impact = impactFor(tx, uid, all)

            if (tx.type == TransactionType.EXPENSE && impact > 0.0) {
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

    data class AutoLaunchBillsResult(
        val created: List<Transaction>,
        val skipped: Int,
        val launchedBillIds: List<String>
    )

    fun computeLaunchDate(dueDay: Int, monthKey: String, trigger: LaunchTrigger = LaunchTrigger.DAY_BEFORE): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val dim = daysInMonth(monthKey)
        val clampedDueDay = dueDay.coerceIn(1, dim)

        if (trigger == LaunchTrigger.FIRST_DAY_OF_MONTH) {
            return "$monthKey-01"
        }

        var date = java.time.LocalDate.of(year, month, clampedDueDay)
        if (trigger == LaunchTrigger.DAY_BEFORE) {
            date = date.minusDays(1)
        } else if (trigger == LaunchTrigger.DAY_AFTER) {
            date = date.plusDays(1)
        }

        return date.toString()
    }

    data class PendingRecurringCandidate(
        val id: String,
        val bill: RecurringBill? = null,
        val isSalary: Boolean = false,
        val description: String,
        val category: String,
        val subcategory: String? = null,
        val amount: Double,
        val type: TransactionType,
        val paymentMethod: PaymentMethod,
        val cardId: String? = null,
        val dueDay: Int,
        val dueDate: String,
        val launchDate: String,
        val matchedManualTx: Transaction? = null
    )

    fun normalizeText(str: String): String {
        val nfd = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
        val pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(nfd).replaceAll("").lowercase().trim()
    }

    fun findMatchingManualSalary(
        allTxs: List<Transaction>,
        monthKey: String,
        expectedAmount: Double = 0.0
    ): Transaction? {
        val salaryWords = listOf("salario", "remuneracao", "holerite", "pro labore", "pro-labore", "renda fixa", "salario base")
        val monthIncomes = allTxs.filter { it.competenceMonth == monthKey && it.type == TransactionType.INCOME }

        val explicit = monthIncomes.firstOrNull { it.recurringBillId?.startsWith("salary_") == true }
        if (explicit != null) return explicit

        val descMatch = monthIncomes.firstOrNull { tx ->
            val norm = normalizeText(tx.description)
            salaryWords.any { norm.contains(it) }
        }
        if (descMatch != null) return descMatch

        if (expectedAmount > 0.0) {
            val amountMatch = monthIncomes.firstOrNull { Math.abs(it.totalAmount - expectedAmount) < 0.01 }
            if (amountMatch != null) return amountMatch
        }

        return null
    }

    fun findMatchingManualBill(
        allTxs: List<Transaction>,
        monthKey: String,
        bill: RecurringBill
    ): Transaction? {
        val normBill = normalizeText(bill.description)
        val monthTxs = allTxs.filter { it.competenceMonth == monthKey && it.type == bill.type }

        val explicit = monthTxs.firstOrNull { it.recurringBillId == bill.id }
        if (explicit != null) return explicit

        return monthTxs.firstOrNull { tx ->
            val normTx = normalizeText(tx.description)
            normTx.contains(normBill) || normBill.contains(normTx)
        }
    }

    fun getPendingRecurringLaunches(
        bills: List<RecurringBill>,
        all: List<Transaction>,
        monthKey: String,
        todayStr: String,
        me: UserProfile,
        resolvedIds: List<String> = emptyList()
    ): List<PendingRecurringCandidate> {
        val resolvedSet = resolvedIds.toSet()
        val candidates = mutableListOf<PendingRecurringCandidate>()

        if (me.monthlyIncome > 0) {
            val salaryId = "salary_${me.uid}"
            val alreadyResolved = resolvedSet.contains(salaryId)
            val hasExplicitTx = all.any { it.recurringBillId == salaryId && it.competenceMonth == monthKey }

            if (!alreadyResolved && !hasExplicitTx) {
                val salaryLaunchDate = "$monthKey-01"
                if (todayStr >= salaryLaunchDate) {
                    val matched = findMatchingManualSalary(all, monthKey, me.monthlyIncome)
                    if (matched == null || matched.recurringBillId != salaryId) {
                        candidates.add(
                            PendingRecurringCandidate(
                                id = salaryId,
                                isSalary = true,
                                description = "Salário Base",
                                category = "Receita",
                                subcategory = null,
                                amount = me.monthlyIncome,
                                type = TransactionType.INCOME,
                                paymentMethod = PaymentMethod.PIX,
                                cardId = null,
                                dueDay = 5,
                                dueDate = dateInMonth(monthKey, 5),
                                launchDate = salaryLaunchDate,
                                matchedManualTx = matched
                            )
                        )
                    }
                }
            }
        }

        val activeBills = bills.filter { it.active && it.ownerUid == me.uid }
        for (bill in activeBills) {
            if (!bill.startMonth.isNullOrBlank() && monthKey < bill.startMonth) continue
            if (!bill.endMonth.isNullOrBlank() && monthKey > bill.endMonth) continue
            if (resolvedSet.contains(bill.id)) continue

            val hasExplicitTx = all.any { it.recurringBillId == bill.id && it.competenceMonth == monthKey }
            if (hasExplicitTx) continue

            val trigger = bill.launchTrigger
            val launchDate = computeLaunchDate(bill.dueDay, monthKey, trigger)

            if (todayStr >= launchDate) {
                val estimate = estimateRecurring(bill, all, monthKey)
                val matched = findMatchingManualBill(all, monthKey, bill)
                if (matched == null || matched.recurringBillId != bill.id) {
                    val isIncome = bill.type == TransactionType.INCOME
                    val pm = bill.paymentMethod ?: if (isIncome) PaymentMethod.PIX else if (!bill.cardId.isNullOrBlank()) PaymentMethod.CREDIT_CARD else PaymentMethod.BILL

                    candidates.add(
                        PendingRecurringCandidate(
                            id = bill.id,
                            bill = bill,
                            isSalary = false,
                            description = bill.description,
                            category = if (isIncome) "Receita" else bill.category,
                            subcategory = bill.subcategory,
                            amount = estimate.amount,
                            type = bill.type,
                            paymentMethod = pm,
                            cardId = bill.cardId,
                            dueDay = bill.dueDay,
                            dueDate = dateInMonth(monthKey, bill.dueDay),
                            launchDate = launchDate,
                            matchedManualTx = matched
                        )
                    )
                }
            }
        }

        return candidates
    }

    /**
     * Lança automaticamente contas recorrentes e salários cujo gatilho de vencimento
     * já tenha sido atingido na data atual.
     */
    fun autoLaunchDueBills(
        bills: List<RecurringBill>,
        all: List<Transaction>,
        monthKey: String,
        todayStr: String,
        me: UserProfile,
        partner: UserProfile? = null
    ): AutoLaunchBillsResult {
        val created = mutableListOf<Transaction>()
        val launchedBillIds = mutableListOf<String>()
        var skipped = 0

        // 1. Receita mensal (salário) a partir do perfil
        if (me.monthlyIncome > 0) {
            val salaryId = "salary_${me.uid}"
            val exists = all.any { it.recurringBillId == salaryId && it.competenceMonth == monthKey }
            if (!exists) {
                val salaryLaunchDate = "$monthKey-01"
                if (todayStr >= salaryLaunchDate) {
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
                            description = "Salário Base",
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
                    launchedBillIds.add(salaryId)
                } else {
                    skipped++
                }
            } else {
                skipped++
            }
        }

        // 2. Contas recorrentes ativas do usuário
        val activeBills = bills.filter { it.active && it.ownerUid == me.uid }
        for (bill in activeBills) {
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

            val trigger = bill.launchTrigger
            val launchDate = computeLaunchDate(bill.dueDay, monthKey, trigger)

            if (todayStr >= launchDate) {
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
                val pm = bill.paymentMethod ?: if (isIncome) PaymentMethod.PIX else if (!bill.cardId.isNullOrBlank()) PaymentMethod.CREDIT_CARD else PaymentMethod.BILL

                created.add(
                    Transaction(
                        id = "tx_gen_${UUID.randomUUID().toString().take(8)}",
                        createdByUid = me.uid,
                        paidByUid = me.uid,
                        cardId = bill.cardId,
                        description = bill.description,
                        category = if (isIncome) "Receita" else bill.category,
                        subcategory = bill.subcategory,
                        type = bill.type,
                        paymentMethod = pm,
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
                launchedBillIds.add(bill.id)
            } else {
                skipped++
            }
        }

        return AutoLaunchBillsResult(created = created, skipped = skipped, launchedBillIds = launchedBillIds)
    }

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
                        description = "Salário Base",
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
            val pm = bill.paymentMethod ?: if (isIncome) PaymentMethod.PIX else if (!bill.cardId.isNullOrBlank()) PaymentMethod.CREDIT_CARD else PaymentMethod.BILL

            created.add(
                Transaction(
                    id = "tx_gen_${UUID.randomUUID().toString().take(8)}",
                    createdByUid = me.uid,
                    paidByUid = me.uid,
                    cardId = bill.cardId,
                    description = bill.description,
                    category = if (isIncome) "Receita" else bill.category,
                    subcategory = bill.subcategory,
                    type = bill.type,
                    paymentMethod = pm,
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
     * espelhando projectFutureMonths de finance.ts na web (padrão 12 meses).
     */
    fun projectFutureMonths(
        all: List<Transaction>,
        bills: List<RecurringBill>,
        profile: UserProfile?,
        uid: String,
        startMonthKey: String,
        count: Int = 12,
        cards: List<Card> = emptyList()
    ): List<FutureMonthProjection> {
        val projections = mutableListOf<FutureMonthProjection>()
        val cardsMap = cards.associateBy { it.id }

        for (i in 0 until count) {
            val monthKey = shiftMonth(startMonthKey, i)
            val label = formatMonthLabel(monthKey)
            val dim = daysInMonth(monthKey)

            val monthTxs = all.filter {
                it.competenceMonth == monthKey && it.visibleToUids.contains(uid) && it.reserveCardId == null
            }

            var projectedIncome = 0.0
            var projectedExpense = 0.0
            var invoiceExpense = 0.0
            var fixedBillsExpense = 0.0
            var variableAccountExpense = 0.0
            var incomeCount = 0
            var expenseCount = 0

            data class CatAcc(
                var amount: Double = 0.0,
                var invoiceAmount: Double = 0.0,
                var accountAmount: Double = 0.0,
                var fixedBillAmount: Double = 0.0,
                var count: Int = 0
            )
            val catMap = mutableMapOf<String, CatAcc>()
            val items = mutableListOf<ProjectedMonthItem>()

            fun addCat(category: String, impact: Double, bucket: String) {
                if (impact <= 0.0) return
                val cur = catMap.getOrPut(category) { CatAcc() }
                cur.amount = round2(cur.amount + impact)
                when (bucket) {
                    "invoice" -> cur.invoiceAmount = round2(cur.invoiceAmount + impact)
                    "fixed_bill" -> cur.fixedBillAmount = round2(cur.fixedBillAmount + impact)
                    else -> cur.accountAmount = round2(cur.accountAmount + impact)
                }
                cur.count += 1
            }

            // 1. Transações reais agendadas para aquele mês (parcelas de compras com cartão, fatura, Pix/débito/boleto)
            for (t in monthTxs) {
                val impact = impactFor(t, uid, all)
                val isCard = t.paymentMethod == PaymentMethod.CREDIT_CARD || !t.cardId.isNullOrBlank()
                val cardObj = t.cardId?.let { cardsMap[it] }

                if (t.type == TransactionType.INCOME) {
                    if (isCard) {
                        projectedExpense = max(0.0, projectedExpense - impact)
                        invoiceExpense = max(0.0, invoiceExpense - impact)
                    } else if (impact > 0.0) {
                        projectedIncome += impact
                        incomeCount++
                        items.add(
                            ProjectedMonthItem(
                                id = t.id,
                                description = t.description,
                                amount = effectiveAmount(t),
                                myImpact = impact,
                                category = t.category,
                                subcategory = t.subcategory,
                                dueDate = t.dueDate,
                                type = TransactionType.INCOME,
                                paymentMethod = t.paymentMethod,
                                cardId = t.cardId,
                                cardName = cardObj?.cardName,
                                origin = "income",
                                status = t.status.value,
                                isRecurringProjection = false
                            )
                        )
                    }
                } else if (impact != 0.0) {
                    projectedExpense += impact
                    expenseCount++

                    val isFixedTx = !t.recurringBillId.isNullOrBlank() || t.status == TransactionStatus.ESTIMATED
                    if (isCard) {
                        invoiceExpense += impact
                        addCat(t.category, impact, "invoice")
                    } else if (isFixedTx) {
                        fixedBillsExpense += impact
                        addCat(t.category, impact, "fixed_bill")
                    } else {
                        variableAccountExpense += impact
                        addCat(t.category, impact, "account")
                    }

                    val instInfo = if ((t.installmentsTotal ?: 0) > 1) {
                        "${t.installmentNumber ?: 1}/${t.installmentsTotal}"
                    } else null

                    items.add(
                        ProjectedMonthItem(
                            id = t.id,
                            description = t.description,
                            amount = effectiveAmount(t),
                            myImpact = impact,
                            category = t.category,
                            subcategory = t.subcategory,
                            dueDate = t.dueDate,
                            type = TransactionType.EXPENSE,
                            paymentMethod = if (isCard) PaymentMethod.CREDIT_CARD else t.paymentMethod,
                            cardId = t.cardId,
                            cardName = cardObj?.cardName,
                            origin = if (isCard) "invoice" else if (isFixedTx) "fixed_bill" else "debit_pix",
                            status = t.status.value,
                            isRecurringProjection = false,
                            installmentInfo = instInfo
                        )
                    )
                }
            }

            // 2. Projeta o salário e as contas fixas ativas que ainda NÃO foram lançadas como transação neste mês
            val hasSalary = monthTxs.any { it.recurringBillId == "salary_$uid" } ||
                findMatchingManualSalary(monthTxs, monthKey, profile?.monthlyIncome ?: 0.0) != null
            if (!hasSalary && profile != null && profile.monthlyIncome > 0) {
                projectedIncome += profile.monthlyIncome
                incomeCount++
                items.add(
                    ProjectedMonthItem(
                        id = "proj_salary_${uid}_$monthKey",
                        description = "Salário Base",
                        amount = profile.monthlyIncome,
                        myImpact = profile.monthlyIncome,
                        category = "Salário & Remuneração",
                        subcategory = "Salário Principal",
                        dueDate = dateInMonth(monthKey, 5),
                        type = TransactionType.INCOME,
                        paymentMethod = PaymentMethod.PIX,
                        cardId = null,
                        cardName = null,
                        origin = "income",
                        status = "projected_recurring",
                        isRecurringProjection = true
                    )
                )
            }

            var ungeneratedCount = 0
            for (bill in bills.filter { it.active && it.ownerUid == uid }) {
                if (monthTxs.any { it.recurringBillId == bill.id }) continue
                if (findMatchingManualBill(monthTxs, monthKey, bill) != null) continue

                if (!bill.startMonth.isNullOrBlank() && monthKey < bill.startMonth) continue
                if (!bill.endMonth.isNullOrBlank() && monthKey > bill.endMonth) continue

                ungeneratedCount++
                val estimate = estimateRecurring(bill, all, monthKey)
                val amount = estimate.amount
                val myFraction = if (bill.splitType == SplitType.SPLIT_50) round2(amount / 2.0) else amount
                val effMethod = bill.paymentMethod ?: if (bill.type == TransactionType.INCOME) {
                    PaymentMethod.PIX
                } else if (!bill.cardId.isNullOrBlank()) {
                    PaymentMethod.CREDIT_CARD
                } else {
                    PaymentMethod.BILL
                }
                val isCardBill = effMethod == PaymentMethod.CREDIT_CARD || !bill.cardId.isNullOrBlank()
                val cardObj = bill.cardId?.let { cardsMap[it] }
                val dueStr = if (cardObj != null) dateInMonth(monthKey, cardObj.dueDay) else dateInMonth(monthKey, bill.dueDay)

                if (bill.type == TransactionType.INCOME) {
                    projectedIncome += myFraction
                    incomeCount++
                    items.add(
                        ProjectedMonthItem(
                            id = "proj_bill_${bill.id}_$monthKey",
                            description = bill.description,
                            amount = amount,
                            myImpact = myFraction,
                            category = bill.category.ifBlank { "Receita" },
                            subcategory = bill.subcategory,
                            dueDate = dueStr,
                            type = TransactionType.INCOME,
                            paymentMethod = effMethod,
                            cardId = bill.cardId,
                            cardName = cardObj?.cardName,
                            origin = "income",
                            status = "projected_recurring",
                            isRecurringProjection = true
                        )
                    )
                } else {
                    projectedExpense += myFraction
                    expenseCount++
                    if (isCardBill) {
                        invoiceExpense += myFraction
                        addCat(bill.category, myFraction, "invoice")
                    } else {
                        fixedBillsExpense += myFraction
                        addCat(bill.category, myFraction, "fixed_bill")
                    }
                    items.add(
                        ProjectedMonthItem(
                            id = "proj_bill_${bill.id}_$monthKey",
                            description = bill.description,
                            amount = amount,
                            myImpact = myFraction,
                            category = bill.category,
                            subcategory = bill.subcategory,
                            dueDate = dueStr,
                            type = TransactionType.EXPENSE,
                            paymentMethod = if (isCardBill) PaymentMethod.CREDIT_CARD else effMethod,
                            cardId = bill.cardId,
                            cardName = cardObj?.cardName,
                            origin = if (isCardBill) "invoice" else "fixed_bill",
                            status = "projected_recurring",
                            isRecurringProjection = true
                        )
                    )
                }
            }

            val isGenerated = ungeneratedCount == 0 && (profile == null || profile.monthlyIncome <= 0 || hasSalary)

            projectedIncome = round2(projectedIncome)
            projectedExpense = round2(projectedExpense)
            invoiceExpense = round2(invoiceExpense)
            fixedBillsExpense = round2(fixedBillsExpense)
            variableAccountExpense = round2(variableAccountExpense)
            val projectedFreeBalance = round2(projectedIncome - projectedExpense)
            val projectedDailyLimit = if (dim > 0) round2(projectedFreeBalance / dim.toDouble()) else null

            val byCategory = catMap.entries
                .map { (cat, acc) ->
                    ProjectedCategoryItem(
                        category = cat,
                        amount = acc.amount,
                        invoiceAmount = acc.invoiceAmount,
                        accountAmount = acc.accountAmount,
                        fixedBillAmount = acc.fixedBillAmount,
                        count = acc.count
                    )
                }
                .sortedByDescending { it.amount }

            val sortedItems = items.sortedBy { it.dueDate }

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
                    expenseCount = expenseCount,
                    invoiceExpense = invoiceExpense,
                    fixedBillsExpense = fixedBillsExpense,
                    variableAccountExpense = variableAccountExpense,
                    byCategory = byCategory,
                    items = sortedItems
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
            it.acceptanceStatus == "pending_approval" && (
                it.assignedFriendUid == currentUid ||
                (it.createdByUid != currentUid && it.allocations.any { a -> a.uid == currentUid && a.type == "PLATFORM_USER" })
            )
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


