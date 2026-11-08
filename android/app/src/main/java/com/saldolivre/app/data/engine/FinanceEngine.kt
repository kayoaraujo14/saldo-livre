package com.saldolivre.app.data.engine

import androidx.compose.runtime.Immutable
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

    @Immutable
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

    @Immutable
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
        var grossInvested = 0.0

        val categoryMap = mutableMapOf<String, Double>()
        val investSubcatMap = mutableMapOf<String, Double>()
        val receivablesList = mutableListOf<ReceivableItem>()

        val cardMap = cards.associateBy { it.id }

        val nonCardTxs = monthTxs.filter { it.cardId.isNullOrBlank() || !cardMap.containsKey(it.cardId) }

        // 1. Não-cartões (Pix, Boleto, Salário, etc.)
        for (tx in nonCardTxs) {
            val impact = impactFor(tx, uid, all)

            if (tx.type == TransactionType.INCOME) {
                income += kotlin.math.abs(impact)
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

                val isAporte = tx.category == "Investimentos" || tx.category.contains("investimento", ignoreCase = true)
                if (isAporte) {
                    grossInvested += impact
                    totalInvested += impact
                    val sub = tx.subcategory ?: "Outros Investimentos"
                    investSubcatMap[sub] = (investSubcatMap[sub] ?: 0.0) + impact
                    if (tx.category != "Investimentos") {
                        categoryMap["Investimentos"] = (categoryMap["Investimentos"] ?: 0.0) + impact
                    }
                }
            } else if (tx.type == TransactionType.INCOME && impact > 0.0) {
                // Abate resgates do total investido no mês
                val isResgate = tx.category == "Investimentos" ||
                    tx.category.contains("investimento", ignoreCase = true) ||
                    (tx.category == "Rendimentos & Investimentos" && (tx.subcategory?.contains("Resgate", ignoreCase = true) == true))
                if (isResgate) {
                    // Resgates de investimento no mês
                    val sub = tx.subcategory ?: "Outros Investimentos"
                    investSubcatMap[sub] = (investSubcatMap[sub] ?: 0.0) - impact
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
        var prevClosureObj: MonthClosure? = null

        val prevMonthKey = shiftMonth(monthKey, -1)
        val prevClosure = closures.firstOrNull { 
            (it.closedMonth == prevMonthKey || it.targetMonth == monthKey) && it.uid == uid 
        }

        if (prevClosure != null) {
            prevClosureObj = prevClosure
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

        // Total acumulado investido no histórico (aportes - resgates)
        var totalInvestedAccumulated = 0.0
        for (tx in all) {
            if (tx.visibleToUids.contains(uid)) {
                val isAporte = (tx.category == "Investimentos" || tx.category.contains("investimento", ignoreCase = true)) && tx.type == TransactionType.EXPENSE
                val isResgate = ((tx.category == "Investimentos" || tx.category.contains("investimento", ignoreCase = true)) && tx.type == TransactionType.INCOME) ||
                    (tx.category == "Rendimentos & Investimentos" && (tx.subcategory?.contains("Resgate", ignoreCase = true) == true))
                val imp = impactFor(tx, uid, all)
                if (isAporte && imp > 0.0) {
                    totalInvestedAccumulated += imp
                } else if (isResgate && imp > 0.0) {
                    totalInvestedAccumulated -= imp
                }
            }
        }
        totalInvestedAccumulated = max(0.0, round2(totalInvestedAccumulated))

        val effectiveAvailable = income + carryoverAmount - previousMonthDeficit
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
            previousMonthClosure = prevClosureObj,
            daysRemaining = daysRemaining,
            daysInMonth = dim,
            dailyLimit = dailyLimit,
            monthState = monthState,
            receivableTotal = round2(receivableFromPartner + receivableFromExternal + receivableFromFriends),
            receivableFromPartner = round2(receivableFromPartner),
            receivableFromExternal = round2(receivableFromExternal),
            receivableFromFriends = round2(receivableFromFriends),
            payableToOthers = round2(payableToOthers),
            totalInvested = round2(kotlin.math.max(grossInvested, totalInvested)),
            totalInvestedAccumulated = totalInvestedAccumulated,
            byCategory = byCategory,
            byInvestmentSubcategory = byInvestSub,
            receivables = receivablesList
        )
    }

    @Immutable
    data class AccountCashFlowSummary(
        val previousMonthsSurplus: Double,
        val previousMonthLabel: String,
        val accountIncomesReceived: Double,
        val accountExpensesPaid: Double,
        val paidInvoicesAmount: Double,
        val totalOutflowSoFar: Double,
        val currentBankBalance: Double,
        val futureIncomesRemaining: Double,
        val futureCommittedRemaining: Double,
        val projectedFreeBalance: Double,
        val dailyLimit: Double?
    )

    fun computeAccountCashFlow(
        all: List<Transaction>,
        uid: String,
        monthKey: String,
        summary: MonthSummary,
        cards: List<Card> = emptyList(),
        today: String = todayKey()
    ): AccountCashFlowSummary {
        val prevMonth = shiftMonth(monthKey, -1)
        val prevMonthLabel = monthLabel(prevMonth)

        val previousMonthsSurplus = if (summary.carryoverAmount > 0.0) {
            summary.carryoverAmount
        } else {
            round2((summary.pendingClosureSurplus ?: 0.0) - summary.previousMonthDeficit)
        }

        val monthTxs = all.filter { it.competenceMonth == monthKey && it.visibleToUids.contains(uid) }

        // Apenas transações da conta bancária / extrato (exclui compras feitas em cartão de crédito)
        val accountTxs = monthTxs.filter { it.cardId.isNullOrBlank() && it.paymentMethod != PaymentMethod.CREDIT_CARD }

        val accountIncomesReceived = round2(accountTxs.filter {
            it.type == TransactionType.INCOME && (it.status == TransactionStatus.PAID && it.dueDate <= today)
        }.sumOf { kotlin.math.abs(impactFor(it, uid, all)) })

        val accountExpensesPaid = round2(accountTxs.filter {
            it.type == TransactionType.EXPENSE && (it.status == TransactionStatus.PAID && it.dueDate <= today)
        }.sumOf { kotlin.math.abs(impactFor(it, uid, all)) })

        val paidInvoicesAmount = round2(cards.sumOf { card ->
            val invoice = InvoiceEngine.buildInvoice(card, monthKey, all, uid, today)
            if (invoice.status == InvoiceStatus.PAID && invoice.dueDate <= today) {
                invoice.myShare
            } else {
                0.0
            }
        })

        val totalOutflowSoFar = round2(accountExpensesPaid + paidInvoicesAmount)
        val currentBankBalance = round2(previousMonthsSurplus + accountIncomesReceived - totalOutflowSoFar)

        val futureIncomesRemaining = round2(kotlin.math.max(0.0, summary.income - accountIncomesReceived))
        val futureCommittedRemaining = round2(kotlin.math.max(0.0, summary.committed - totalOutflowSoFar))

        return AccountCashFlowSummary(
            previousMonthsSurplus = previousMonthsSurplus,
            previousMonthLabel = prevMonthLabel,
            accountIncomesReceived = accountIncomesReceived,
            accountExpensesPaid = accountExpensesPaid,
            paidInvoicesAmount = paidInvoicesAmount,
            totalOutflowSoFar = totalOutflowSoFar,
            currentBankBalance = currentBankBalance,
            futureIncomesRemaining = futureIncomesRemaining,
            futureCommittedRemaining = futureCommittedRemaining,
            projectedFreeBalance = summary.freeBalance,
            dailyLimit = summary.dailyLimit
        )
    }

    @Immutable
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

    @Immutable
    data class GenerateBillsResult(
        val created: List<Transaction>,
        val skipped: Int
    )

    @Immutable
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

    @Immutable
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
        if (normBill.isBlank()) return null
        val monthTxs = allTxs.filter { it.competenceMonth == monthKey && it.type == bill.type }

        val explicit = monthTxs.firstOrNull { it.recurringBillId == bill.id }
        if (explicit != null) return explicit

        return monthTxs.firstOrNull { tx ->
            val normTx = normalizeText(tx.description)
            normTx.isNotBlank() && (normTx.contains(normBill) || normBill.contains(normTx))
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
        cards: List<Card> = emptyList(),
        closures: List<MonthClosure> = emptyList()
    ): List<FutureMonthProjection> {
        val projections = mutableListOf<FutureMonthProjection>()
        val cardsMap = cards.associateBy { it.id }

        for (i in 0 until count) {
            val monthKey = shiftMonth(startMonthKey, i)
            val prevMonthKey = shiftMonth(monthKey, -1)
            val label = formatMonthLabel(monthKey)
            val dim = daysInMonth(monthKey)

            var carryoverAmount = 0.0
            var previousMonthDeficit = 0.0

            val prevClosure = closures.firstOrNull {
                (it.closedMonth == prevMonthKey || it.targetMonth == monthKey) && it.uid == uid
            }

            if (prevClosure != null) {
                carryoverAmount = round2(prevClosure.carryoverToNextMonth)
            } else if (i == 0) {
                val pastMonths = all
                    .filter { it.visibleToUids.contains(uid) && it.competenceMonth < monthKey }
                    .map { it.competenceMonth }
                    .distinct()
                    .sorted()

                if (pastMonths.isNotEmpty()) {
                    var runningBalance = 0.0
                    var currentIter = pastMonths.first()
                    while (currentIter <= prevMonthKey) {
                        val closure = closures.firstOrNull {
                            (it.closedMonth == currentIter || it.targetMonth == shiftMonth(currentIter, 1)) && it.uid == uid
                        }
                        val raw = computeMonthRawBalance(all, uid, currentIter)
                        if (closure != null) {
                            runningBalance = closure.carryoverToNextMonth
                        } else {
                            runningBalance += raw
                        }
                        currentIter = shiftMonth(currentIter, 1)
                    }
                    if (runningBalance < 0.0) {
                        previousMonthDeficit = round2(kotlin.math.abs(runningBalance))
                    }
                }
            } else if ((projections.getOrNull(i - 1)?.projectedFreeBalance ?: 0.0) < 0.0) {
                // Cascata Conservadora: déficit projetado do mês anterior acumula para o próximo mês
                previousMonthDeficit = round2(kotlin.math.abs(projections[i - 1].projectedFreeBalance))
            }

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
                        projectedExpense = max(0.0, projectedExpense - kotlin.math.abs(impact))
                        invoiceExpense = max(0.0, invoiceExpense - kotlin.math.abs(impact))
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

            if (carryoverAmount > 0.0) {
                incomeCount++
                items.add(
                    ProjectedMonthItem(
                        id = "proj_carryover_$monthKey",
                        description = "Sobra Injetada do Mês Anterior",
                        amount = carryoverAmount,
                        myImpact = carryoverAmount,
                        category = "Saldo Remanescente",
                        subcategory = "Fechamento de Mês",
                        dueDate = dateInMonth(monthKey, 1),
                        type = TransactionType.INCOME,
                        paymentMethod = PaymentMethod.PIX,
                        cardId = null,
                        cardName = null,
                        origin = "income",
                        status = "paid",
                        isRecurringProjection = false
                    )
                )
            }

            val isGenerated = ungeneratedCount == 0 && (profile == null || profile.monthlyIncome <= 0 || hasSalary)

            projectedIncome = round2(projectedIncome)
            projectedExpense = round2(projectedExpense)
            invoiceExpense = round2(invoiceExpense)
            fixedBillsExpense = round2(fixedBillsExpense)
            variableAccountExpense = round2(variableAccountExpense)
            val projectedAvailable = round2(projectedIncome + carryoverAmount - previousMonthDeficit)
            val projectedFreeBalance = round2(projectedAvailable - projectedExpense)
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
                    carryoverAmount = carryoverAmount,
                    previousMonthDeficit = previousMonthDeficit,
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

    /** Categorias canônicas (padrão dos lançamentos) com despesa fixa/recorrente mensal. */
    val FIXED_MONTHLY_CATEGORIES = setOf(
        "Moradia",
        "Assinaturas & Serviços",
        "Educação & Desenvolvimento",
        "Financiamentos & Dívidas",
        "Impostos & Tributos"
    )

    /** Entradas, patrimônio e legados de crédito/terceiros nunca geram envelope de despesa. */
    val NON_ENVELOPE_CATEGORIES: Set<String> =
        setOf("Investimentos", "Receita", "Estorno", "Cartão Terceiros") +
            com.saldolivre.app.data.model.CategoryRegistry.INCOME_CATEGORIES.map { it.name }

    private val CATEGORY_CANONICAL_MAP = mapOf(
        "assinaturas" to "Assinaturas & Serviços",
        "assinaturas & serviços" to "Assinaturas & Serviços",
        "assinaturas & servicos" to "Assinaturas & Serviços",
        "assinaturas e serviços" to "Assinaturas & Serviços",
        "assinaturas e servicos" to "Assinaturas & Serviços",
        "serviços" to "Assinaturas & Serviços",
        "servicos" to "Assinaturas & Serviços",
        "streaming" to "Assinaturas & Serviços",
        "educação" to "Educação & Desenvolvimento",
        "educacao" to "Educação & Desenvolvimento",
        "educação & desenvolvimento" to "Educação & Desenvolvimento",
        "educacao & desenvolvimento" to "Educação & Desenvolvimento",
        "educação e desenvolvimento" to "Educação & Desenvolvimento",
        "educacao e desenvolvimento" to "Educação & Desenvolvimento",
        "cursos" to "Educação & Desenvolvimento",
        "lazer" to "Lazer & Entretenimento",
        "lazer & entretenimento" to "Lazer & Entretenimento",
        "lazer e entretenimento" to "Lazer & Entretenimento",
        "entretenimento" to "Lazer & Entretenimento",
        "compras" to "Compras & Estilo de Vida",
        "compras & estilo de vida" to "Compras & Estilo de Vida",
        "compras e estilo de vida" to "Compras & Estilo de Vida",
        "estilo de vida" to "Compras & Estilo de Vida",
        "vestuário" to "Compras & Estilo de Vida",
        "vestuario" to "Compras & Estilo de Vida",
        "cuidados pessoais" to "Cuidados Pessoais & Estética",
        "cuidados pessoais & estética" to "Cuidados Pessoais & Estética",
        "cuidados pessoais & estetica" to "Cuidados Pessoais & Estética",
        "cuidados pessoais e estética" to "Cuidados Pessoais & Estética",
        "cuidados pessoais e estetica" to "Cuidados Pessoais & Estética",
        "estética" to "Cuidados Pessoais & Estética",
        "estetica" to "Cuidados Pessoais & Estética",
        "família" to "Família & Dependentes",
        "familia" to "Família & Dependentes",
        "família & dependentes" to "Família & Dependentes",
        "familia & dependentes" to "Família & Dependentes",
        "família e dependentes" to "Família & Dependentes",
        "familia e dependentes" to "Família & Dependentes",
        "dependentes" to "Família & Dependentes",
        "família & pets" to "Família & Dependentes",
        "familia & pets" to "Família & Dependentes",
        "família e pets" to "Família & Dependentes",
        "familia e pets" to "Família & Dependentes",
        "trabalho" to "Trabalho / PJ / Carreira",
        "carreira" to "Trabalho / PJ / Carreira",
        "pj" to "Trabalho / PJ / Carreira",
        "trabalho / pj / carreira" to "Trabalho / PJ / Carreira",
        "financiamentos" to "Financiamentos & Dívidas",
        "financiamento" to "Financiamentos & Dívidas",
        "financiamentos & empréstimos" to "Financiamentos & Dívidas",
        "financiamentos & emprestimos" to "Financiamentos & Dívidas",
        "financiamentos e empréstimos" to "Financiamentos & Dívidas",
        "financiamentos e emprestimos" to "Financiamentos & Dívidas",
        "financiamentos & dívidas" to "Financiamentos & Dívidas",
        "financiamentos & dividas" to "Financiamentos & Dívidas",
        "financiamentos e dívidas" to "Financiamentos & Dívidas",
        "financiamentos e dividas" to "Financiamentos & Dívidas",
        "empréstimos" to "Financiamentos & Dívidas",
        "emprestimos" to "Financiamentos & Dívidas",
        "empréstimo" to "Financiamentos & Dívidas",
        "emprestimo" to "Financiamentos & Dívidas",
        "dívidas" to "Financiamentos & Dívidas",
        "dividas" to "Financiamentos & Dívidas",
        "dívida" to "Financiamentos & Dívidas",
        "divida" to "Financiamentos & Dívidas",
        "impostos" to "Impostos & Tributos",
        "impostos & tributos" to "Impostos & Tributos",
        "impostos e tributos" to "Impostos & Tributos",
        "tributos" to "Impostos & Tributos",
        "pets" to "Pets",
        "pet" to "Pets",
        "alimentação" to "Alimentação",
        "alimentacao" to "Alimentação",
        "moradia" to "Moradia",
        "transporte" to "Transporte",
        "transportes" to "Transporte",
        "saúde" to "Saúde",
        "saude" to "Saúde",
        "investimentos" to "Investimentos",
        "investimento" to "Investimentos",
        "outros" to "Outros",
        "outro" to "Outros",
        "receita" to "Outras Receitas",
        "outras receitas" to "Outras Receitas",
        "cartão terceiros" to "Outros",
        "cartao terceiros" to "Outros",
        "estorno" to "Outros"
    )

    private fun foldAccents(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    /**
     * Normaliza a categoria de um envelope para o padrão das categorias dos lançamentos.
     * Demais categorias canônicas são reconhecidas sem diferenciar maiúsculas/acentos.
     */
    fun normalizeBudgetCategory(category: String): String {
        val trimmed = category.trim()
        if (trimmed.isEmpty()) return trimmed
        CATEGORY_CANONICAL_MAP[trimmed.lowercase()]?.let { return it }
        val folded = foldAccents(trimmed)
        CATEGORY_CANONICAL_MAP[folded]?.let { return it }
        val registry = com.saldolivre.app.data.model.CategoryRegistry
        return (registry.EXPENSE_CATEGORIES + registry.INCOME_CATEGORIES)
            .firstOrNull { foldAccents(it.name) == folded }?.name ?: trimmed
    }

    /**
     * Pontuação do Termômetro de Saúde Financeira (0 a 100), com paridade com a Web:
     * 1. Poupança & Investimentos (até 30 pts)
     * 2. Controle de Custo de Vida / Consumo da Renda (até 30 pts)
     * 3. Cobertura da Reserva de Emergência (até 25 pts)
     * 4. Aderência aos Envelopes Orçamentários (até 15 pts)
     */    fun isFixedBudgetCategory(category: String): Boolean =
        category in FIXED_MONTHLY_CATEGORIES || normalizeBudgetCategory(category) in FIXED_MONTHLY_CATEGORIES

    fun resolveEmergencyReserveBalance(profile: UserProfile?, summary: MonthSummary?): Double {
        if (profile != null && profile.investmentAssetBalances.isNotEmpty()) {
            val explicit = profile.investmentAssetBalances["Reserva de Emergência"]
                ?: profile.investmentAssetBalances.entries.firstOrNull {
                    it.key.contains("Reserva", ignoreCase = true) && it.value > 0.0
                }?.value
            if (explicit != null && explicit > 0.0) return round2(explicit)
            val totalAssets = profile.investmentAssetBalances.values.filter { it > 0.0 }.sum()
            if (totalAssets > 0.0) return round2(totalAssets)
        }
        if (profile != null && profile.emergencyReserveBalance > 0.0) {
            return round2(profile.emergencyReserveBalance)
        }
        return round2(kotlin.math.max(0.0, summary?.totalInvestedAccumulated ?: 0.0))
    }

    fun calculateFinancialHealthScore(
        summary: MonthSummary?,
        emergencyReserveBalance: Double = 0.0,
        budgetReport: CategoryBudgetReport? = null
    ): FinancialHealthScore {
        if (summary == null) {
            return FinancialHealthScore(
                score = 50,
                classification = FinancialHealthClassification.WARNING,
                label = "Atenção",
                description = "Aguardando dados financeiros para calcular o score.",
                savingsScore = 0,
                spendingLimitScore = 0,
                emergencyReserveScore = 0,
                savingsRatePercentage = 0,
                spendingRatioPercentage = 0,
                emergencyCoverageMonths = 0.0,
                budgetAdherenceScore = 0,
                budgetConfiguredCount = 0,
                budgetOverspentCount = 0,
                monthlyLivingCost = 0.0,
                potentialSavingsRatePercentage = 0,
                actionableTips = listOf("Cadastre suas receitas e despesas do mês para liberar o diagnóstico completo.")
            )
        }

        val rawIncome = summary.income
        val carryover = summary.carryoverAmount
        val deficit = summary.previousMonthDeficit
        val effectiveAvailable = round2(kotlin.math.max(0.0, rawIncome + carryover - deficit))
        val income = effectiveAvailable
        val rawTotalOutflow = when {
            summary.paid > 0.0 || summary.committed > 0.0 -> {
                if (summary.paid == summary.committed && summary.committedEstimated == 0.0 && summary.committedPending == 0.0) {
                    summary.committed
                } else {
                    summary.paid + summary.committed
                }
            }
            summary.byCategory.isNotEmpty() -> summary.byCategory.sumOf { it.amount }
            else -> 0.0
        }
        val totalInvested = kotlin.math.max(0.0, summary.totalInvested)
        val positiveFreeBalance = kotlin.math.max(0.0, summary.freeBalance)

        // Desconta aportes de Investimentos do cálculo de custo de vida quando presentes em byCategory
        val investedInByCategory = summary.byCategory.firstOrNull { it.category == "Investimentos" }?.amount ?: 0.0
        val totalExpenses = kotlin.math.max(0.0, round2(rawTotalOutflow - investedInByCategory))

        // 1. Pilar de Poupança & Investimentos (0 a 30 pontos — Meta >= 20% da renda)
        var savingsScore = 0
        var savingsRatePercentage = 0
        var potentialSavingsRatePercentage = 0
        if (income > 0.0) {
            val rate = totalInvested / income
            savingsRatePercentage = kotlin.math.round(rate * 100.0).toInt()
            potentialSavingsRatePercentage = kotlin.math.min(
                100,
                kotlin.math.round(((totalInvested + positiveFreeBalance) / income) * 100.0).toInt()
            )
            savingsScore = if (totalInvested <= 0.0 || rate <= 0.0) {
                0
            } else if (rate >= 0.20) {
                30
            } else {
                val baseInvestedPts = kotlin.math.round((rate / 0.20) * 25.0).toInt()
                val freeMarginRate = positiveFreeBalance / income
                val marginBonusPts = if (freeMarginRate > 0.0) {
                    kotlin.math.min(5, kotlin.math.round((freeMarginRate / 0.20) * 5.0).toInt())
                } else 0
                kotlin.math.min(30, baseInvestedPts + marginBonusPts)
            }
        } else if (totalInvested > 0.0) {
            savingsScore = 15
            savingsRatePercentage = 100
            potentialSavingsRatePercentage = 100
        }

        // 2. Pilar de Custo de Vida / Comprometimento da Renda (0 a 30 pontos)
        var spendingLimitScore = 0
        var spendingRatioPercentage = 0
        if (income > 0.0) {
            val ratio = totalExpenses / income
            spendingRatioPercentage = kotlin.math.round(ratio * 100.0).toInt()
            spendingLimitScore = when {
                ratio <= 0.70 -> 30
                ratio <= 1.00 -> kotlin.math.max(8, kotlin.math.round(30.0 - ((ratio - 0.70) / 0.30) * 22.0).toInt())
                ratio <= 1.10 -> 4
                else -> 0
            }
        } else if (totalExpenses == 0.0) {
            spendingLimitScore = 15
            spendingRatioPercentage = 0
        } else {
            spendingLimitScore = 0
            spendingRatioPercentage = 100
        }

        // 3. Pilar de Reserva de Emergência (0 a 25 pontos — Meta >= 3 a 6 meses de custo)
        val baselineCost = when {
            totalExpenses > 0.0 -> totalExpenses
            income > 0.0 -> income * 0.8
            else -> 1000.0
        }

        val coverageMonths = if (emergencyReserveBalance > 0.0) {
            round2(emergencyReserveBalance / baselineCost)
        } else 0.0

        val emergencyReserveScore = when {
            coverageMonths >= 6.0 -> 25
            coverageMonths >= 3.0 -> kotlin.math.round(18.0 + ((coverageMonths - 3.0) / 3.0) * 7.0).toInt()
            coverageMonths >= 1.0 -> kotlin.math.round(8.0 + ((coverageMonths - 1.0) / 2.0) * 10.0).toInt()
            coverageMonths > 0.0 -> kotlin.math.min(8, kotlin.math.round(coverageMonths * 8.0).toInt())
            else -> 0
        }

        // 4. Pilar de Controle de Envelopes (0 a 15 pontos)
        var budgetAdherenceScore = 0
        var budgetConfiguredCount = 0
        var budgetOverspentCount = 0
        var envelopeScore = 0

        if (budgetReport != null && budgetReport.totalBudgeted > 0.0) {
            val configuredItems = budgetReport.items.filter { it.limit > 0.0 && !it.category.contains("investimento", ignoreCase = true) }
            budgetConfiguredCount = configuredItems.size
            val overspentItems = configuredItems.filter {
                val committed = it.committedAmount
                (committed - it.limit) >= 0.01
            }
            budgetOverspentCount = overspentItems.size

            if (budgetConfiguredCount > 0) {
                if (budgetOverspentCount == 0) {
                    envelopeScore = 15
                    budgetAdherenceScore = 100
                } else {
                    val compliantCount = budgetConfiguredCount - budgetOverspentCount
                    val complianceRatio = compliantCount.toDouble() / budgetConfiguredCount
                    val isGlobalOver = (budgetReport.totalCommitted - budgetReport.totalBudgeted) >= 0.01
                    val globalOverPenalty = if (isGlobalOver) 3 else 0
                    envelopeScore = (kotlin.math.round(complianceRatio * 15.0).toInt() - globalOverPenalty).coerceIn(0, 15)
                    budgetAdherenceScore = kotlin.math.round((envelopeScore.toDouble() / 15.0) * 100.0).toInt()
                }
            } else {
                envelopeScore = 15
                budgetAdherenceScore = 100
            }
        } else {
            // Sem envelopes: infere a disciplina pelo comprometimento geral de gastos
            if (spendingRatioPercentage <= 70) {
                envelopeScore = 15
                budgetAdherenceScore = 100
            } else if (spendingRatioPercentage >= 100) {
                envelopeScore = 0
                budgetAdherenceScore = 0
            } else {
                val ratio = (100 - spendingRatioPercentage).toDouble() / 30.0
                envelopeScore = kotlin.math.round(ratio * 15.0).toInt().coerceIn(0, 15)
                budgetAdherenceScore = kotlin.math.round((envelopeScore.toDouble() / 15.0) * 100.0).toInt()
            }
        }

        // Pontuação Total (0 a 100 pontos — soma exata e transparente dos 4 pilares)
        val totalScore = (savingsScore + spendingLimitScore + emergencyReserveScore + envelopeScore).coerceIn(0, 100)

        val (classification, label, description) = when {
            totalScore >= 80 -> Triple(
                FinancialHealthClassification.EXCELLENT,
                "Excelente",
                "Finanças blindadas com excelente taxa de poupança, controle e reserva sólida."
            )
            totalScore >= 60 -> Triple(
                FinancialHealthClassification.HEALTHY,
                "Saudável",
                "Bom ritmo financeiro com despesas controladas e reservas ativas."
            )
            totalScore >= 40 -> Triple(
                FinancialHealthClassification.WARNING,
                "Atenção",
                "Gastos próximos do limite ou reserva de emergência em formação."
            )
            else -> Triple(
                FinancialHealthClassification.CRITICAL,
                "Crítico",
                "Despesas acima da renda ou ausência de margem e reservas."
            )
        }

        val actionableTips = mutableListOf<String>()

        if (budgetConfiguredCount > 0 && budgetOverspentCount > 0 && budgetReport?.topOverspentCategory != null) {
            val topOver = budgetReport.topOverspentCategory
            val excess = round2(kotlin.math.max(0.0, topOver.committedAmount - topOver.limit))
            actionableTips.add(
                "📦 Envelope de ${topOver.category} excedeu o teto em ${formatCurrency(excess)}. Reequilibrar seus envelopes recupera +${15 - envelopeScore} pts no Score."
            )
        } else if (budgetConfiguredCount == 0 && income > 0.0) {
            actionableTips.add(
                "📦 Ative os Envelopes Orçamentários com IA para blindar suas categorias variáveis e ganhar até +15 pts no Score."
            )
        }

        if (income > 0.0 && savingsRatePercentage < 20) {
            val idealInvest = round2(income * 0.20)
            val missingInvest = round2(kotlin.math.max(0.0, idealInvest - totalInvested))
            val potentialGain = kotlin.math.max(1, 30 - savingsScore)
            if (positiveFreeBalance >= missingInvest && missingInvest > 0.0) {
                actionableTips.add(
                    "📈 Você tem Saldo Livre suficiente para aportar mais ${formatCurrency(missingInvest)} (atingindo 20% da renda) e ganhar +$potentialGain pts."
                )
            } else if (missingInvest > 0.0) {
                actionableTips.add(
                    "📈 Aportar na categoria Investimentos (meta ideal: ${formatCurrency(idealInvest)}/mês) pode elevar seu Score em até +$potentialGain pts."
                )
            }
        }

        if (income > 0.0 && spendingRatioPercentage > 70) {
            val targetExpense = round2(income * 0.70)
            val excessExpense = round2(kotlin.math.max(0.0, totalExpenses - targetExpense))
            val potentialGain = kotlin.math.max(1, 30 - spendingLimitScore)
            actionableTips.add(
                "🎯 Seu custo de vida consome $spendingRatioPercentage% da renda. Reduzir ${formatCurrency(excessExpense)} em gastos variáveis destrava +$potentialGain pts."
            )
        }

        if (coverageMonths < 6.0) {
            val nextTargetMonths = when {
                coverageMonths < 1.0 -> 1
                coverageMonths < 3.0 -> 3
                else -> 6
            }
            val neededAmount = round2(kotlin.math.max(0.0, baselineCost * nextTargetMonths - emergencyReserveBalance))
            if (neededAmount > 0.0) {
                actionableTips.add(
                    "🛡️ Sua Reserva cobre ${coverageMonths}m do seu custo de vida. Acumular mais ${formatCurrency(neededAmount)} atinge o marco de $nextTargetMonths ${if (nextTargetMonths == 1) "mês" else "meses"} de proteção."
                )
            }
        }

        if (actionableTips.isEmpty()) {
            actionableTips.add(
                "✨ Parabéns! Você atingiu excelência nos 4 pilares (Poupança, Custo de Vida, Envelopes e Reserva de Emergência)."
            )
        }

        return FinancialHealthScore(
            score = totalScore,
            classification = classification,
            label = label,
            description = description,
            savingsScore = savingsScore,
            spendingLimitScore = spendingLimitScore,
            emergencyReserveScore = emergencyReserveScore,
            envelopeScore = envelopeScore,
            savingsRatePercentage = savingsRatePercentage,
            spendingRatioPercentage = spendingRatioPercentage,
            emergencyCoverageMonths = coverageMonths,
            budgetAdherenceScore = budgetAdherenceScore,
            budgetConfiguredCount = budgetConfiguredCount,
            budgetOverspentCount = budgetOverspentCount,
            monthlyLivingCost = round2(baselineCost),
            potentialSavingsRatePercentage = potentialSavingsRatePercentage,
            actionableTips = actionableTips
        )
    }

    // ------------------------------------------------------------------
    // Envelopes Orçamentários Inteligentes & Análise de Gastos (IA-Ready)
    // ------------------------------------------------------------------

    fun computeCategoryBudgets(
        summary: MonthSummary,
        categoryBudgets: Map<String, Double> = emptyMap(),
        referenceDate: String = todayKey(),
        expectedIncome: Double = summary.income
    ): CategoryBudgetReport {
        val mKey = summary.monthKey.ifBlank { currentMonthKey() }
        val resolvedIncome = if (expectedIncome > 0.0) expectedIncome else summary.income
        return computeCategoryBudgets(
            byCategory = summary.byCategory,
            categoryBudgets = categoryBudgets,
            monthKey = mKey,
            referenceDate = referenceDate,
            expectedIncome = resolvedIncome,
            carryoverAmount = summary.carryoverAmount,
            previousMonthDeficit = summary.previousMonthDeficit
        )
    }

    fun computeCategoryBudgets(
        byCategory: List<CategoryExpense>,
        categoryBudgets: Map<String, Double> = emptyMap(),
        monthKey: String = currentMonthKey(),
        referenceDate: String = todayKey(),
        expectedIncome: Double = 0.0,
        carryoverAmount: Double = 0.0,
        previousMonthDeficit: Double = 0.0,
        recurringBills: List<RecurringBill> = emptyList()
    ): CategoryBudgetReport {
        val spentMap = mutableMapOf<String, Double>()
        for (item in byCategory) {
            if (item.category.isNotBlank()) {
                spentMap[item.category] = (spentMap[item.category] ?: 0.0) + item.amount
            }
        }
        return buildCategoryBudgetReport(
            spentMap = spentMap,
            pendingMap = emptyMap(),
            categoryBudgets = categoryBudgets,
            monthKey = monthKey,
            referenceDate = referenceDate,
            expectedIncome = expectedIncome,
            carryoverAmount = carryoverAmount,
            previousMonthDeficit = previousMonthDeficit
        )
    }

    fun computeCategoryBudgetsFromTransactions(
        transactions: List<Transaction>,
        categoryBudgets: Map<String, Double> = emptyMap(),
        monthKey: String = currentMonthKey(),
        referenceDate: String = todayKey(),
        uid: String,
        allTransactions: List<Transaction> = transactions,
        expectedIncome: Double = 0.0,
        carryoverAmount: Double = 0.0,
        previousMonthDeficit: Double = 0.0,
        recurringBills: List<RecurringBill> = emptyList()
    ): CategoryBudgetReport {
        val spentMap = mutableMapOf<String, Double>()
        val pendingMap = mutableMapOf<String, Double>()
        var monthIncomeFromTx = 0.0

        for (tx in transactions) {
            if (tx.competenceMonth == monthKey && (tx.visibleToUids.isEmpty() || tx.visibleToUids.contains(uid))) {
                val impact = impactFor(tx, uid, allTransactions)
                val isAporte = (tx.category == "Investimentos" || tx.category.contains("investimento", ignoreCase = true)) && tx.type == TransactionType.EXPENSE
                if (tx.type == TransactionType.EXPENSE && impact > 0.0 && tx.category.isNotBlank()) {
                    if (tx.status == TransactionStatus.PAID) {
                        spentMap[tx.category] = round2((spentMap[tx.category] ?: 0.0) + impact)
                        if (isAporte && tx.category != "Investimentos") {
                            spentMap["Investimentos"] = round2((spentMap["Investimentos"] ?: 0.0) + impact)
                        }
                    } else {
                        pendingMap[tx.category] = round2((pendingMap[tx.category] ?: 0.0) + impact)
                        if (isAporte && tx.category != "Investimentos") {
                            pendingMap["Investimentos"] = round2((pendingMap["Investimentos"] ?: 0.0) + impact)
                        }
                    }
                } else if (tx.type == TransactionType.INCOME && impact > 0.0) {
                    monthIncomeFromTx += impact
                }
            }
        }

        // Contas fixas e recorrentes ativas a vencer no mês que ainda não geraram lançamento
        if (recurringBills.isNotEmpty()) {
            val activeBills = recurringBills.filter { it.active && it.ownerUid == uid && it.type != TransactionType.INCOME }
            for (bill in activeBills) {
                if (!bill.startMonth.isNullOrBlank() && monthKey < bill.startMonth) continue
                if (!bill.endMonth.isNullOrBlank() && monthKey > bill.endMonth) continue

                val hasTx = allTransactions.any { it.recurringBillId == bill.id && (it.competenceMonth == monthKey || it.dueDate.startsWith(monthKey)) }
                val hasManual = findMatchingManualBill(allTransactions, monthKey, bill)
                if (!hasTx && hasManual == null) {
                    val estimated = estimateRecurring(bill, allTransactions, monthKey).amount
                    if (estimated > 0.0 && bill.category.isNotBlank()) {
                        pendingMap[bill.category] = round2((pendingMap[bill.category] ?: 0.0) + estimated)
                    }
                }
            }
        }

        val resolvedIncome = if (expectedIncome > 0.0) expectedIncome else monthIncomeFromTx
        return buildCategoryBudgetReport(
            spentMap = spentMap,
            pendingMap = pendingMap,
            categoryBudgets = categoryBudgets,
            monthKey = monthKey,
            referenceDate = referenceDate,
            expectedIncome = resolvedIncome,
            carryoverAmount = carryoverAmount,
            previousMonthDeficit = previousMonthDeficit
        )
    }

    private fun buildCategoryBudgetReport(
        spentMap: Map<String, Double>,
        pendingMap: Map<String, Double> = emptyMap(),
        categoryBudgets: Map<String, Double>,
        monthKey: String,
        referenceDate: String,
        expectedIncome: Double = 0.0,
        carryoverAmount: Double = 0.0,
        previousMonthDeficit: Double = 0.0
    ): CategoryBudgetReport {
        val totalDays = daysInMonth(monthKey)
        var currentDay = totalDays

        if (referenceDate.startsWith(monthKey)) {
            val dayParsed = referenceDate.split("-").getOrNull(2)?.toIntOrNull() ?: 1
            currentDay = dayParsed.coerceIn(1, totalDays)
        } else if (referenceDate < monthKey) {
            currentDay = 1
        }

        val daysRemaining = kotlin.math.max(0, totalDays - currentDay)

        // Reconcilia aliases de categorias (ex.: "Lazer" <-> "Lazer & Entretenimento")
        val normalizedBudgetKeyMap = mutableMapOf<String, String>()
        val consolidatedBudgets = linkedMapOf<String, Double>()
        for ((rawCat, valAmt) in categoryBudgets) {
            if (valAmt <= 0.0) continue
            val norm = normalizeBudgetCategory(rawCat)
            val displayKey = normalizedBudgetKeyMap[norm] ?: rawCat
            normalizedBudgetKeyMap[norm] = displayKey
            consolidatedBudgets[displayKey] = round2((consolidatedBudgets[displayKey] ?: 0.0) + valAmt)
        }

        val consolidatedSpentMap = linkedMapOf<String, Double>()
        for ((rawCat, valAmt) in spentMap) {
            if (valAmt <= 0.0) continue
            val norm = normalizeBudgetCategory(rawCat)
            val matchedBudgetKey = normalizedBudgetKeyMap[norm]
            val targetKey = matchedBudgetKey ?: norm
            consolidatedSpentMap[targetKey] = round2((consolidatedSpentMap[targetKey] ?: 0.0) + valAmt)
        }

        val consolidatedPendingMap = linkedMapOf<String, Double>()
        for ((rawCat, valAmt) in pendingMap) {
            if (valAmt <= 0.0) continue
            val norm = normalizeBudgetCategory(rawCat)
            val matchedBudgetKey = normalizedBudgetKeyMap[norm]
            val targetKey = matchedBudgetKey ?: norm
            consolidatedPendingMap[targetKey] = round2((consolidatedPendingMap[targetKey] ?: 0.0) + valAmt)
        }

        // Calibração automática pela Capacidade Financeira Disponível do Mês (Entrada + Sobra do Mês Anterior):
        val effectiveExpectedIncome = round2(kotlin.math.max(0.0, expectedIncome))
        val effectiveCarryover = round2(kotlin.math.max(0.0, carryoverAmount))
        val effectiveDeficit = round2(kotlin.math.max(0.0, previousMonthDeficit))
        val totalAvailableCapacity = round2(kotlin.math.max(0.0, effectiveExpectedIncome + effectiveCarryover - effectiveDeficit))

        // Exclui Investimentos do somatório de despesas orçadas
        val rawExpenseBudgeted = round2(
            consolidatedBudgets.entries
                .filter { it.value > 0.0 && !it.key.contains("investimento", ignoreCase = true) && it.key != "Investimentos" }
                .sumOf { it.value }
        )
        val rawTotalBudgeted = rawExpenseBudgeted
        val isCappedToIncome = totalAvailableCapacity > 0.0 && rawTotalBudgeted > totalAvailableCapacity
        val scaleFactor = if (isCappedToIncome && rawTotalBudgeted > 0.0) {
            totalAvailableCapacity / rawTotalBudgeted
        } else {
            1.0
        }

        val effectiveBudgets = linkedMapOf<String, Double>()
        if (isCappedToIncome && rawTotalBudgeted > 0.0) {
            var runningSum = 0.0
            var maxCat = ""
            var maxVal = -1.0
            for ((cat, rawVal) in consolidatedBudgets) {
                if (cat.contains("investimento", ignoreCase = true) || cat == "Investimentos") {
                    effectiveBudgets[cat] = round2(rawVal)
                    continue
                }
                var scaled = kotlin.math.floor((rawVal * scaleFactor) / 10.0) * 10.0
                if (scaled <= 0.0 && rawVal > 0.0) scaled = kotlin.math.max(1.0, kotlin.math.floor(rawVal * scaleFactor))
                effectiveBudgets[cat] = scaled
                runningSum = round2(runningSum + scaled)
                if (scaled > maxVal) {
                    maxVal = scaled
                    maxCat = cat
                }
            }
            val diff = kotlin.math.floor((totalAvailableCapacity - runningSum) / 10.0) * 10.0
            if (maxCat.isNotEmpty() && diff >= 10.0) {
                effectiveBudgets[maxCat] = round2((effectiveBudgets[maxCat] ?: 0.0) + diff)
            }
        } else {
            for ((cat, rawVal) in consolidatedBudgets) {
                if (rawVal > 0.0) {
                    effectiveBudgets[cat] = round2(rawVal)
                }
            }
        }

        val budgetedCategories = effectiveBudgets.filter { it.value > 0.0 }.keys
        val allCategoriesSet = linkedSetOf<String>().apply {
            addAll(budgetedCategories)
            addAll(consolidatedSpentMap.keys)
            addAll(consolidatedPendingMap.keys)
        }

        val items = mutableListOf<CategoryBudgetStatus>()
        var totalBudgeted = 0.0
        var totalSpent = 0.0
        var totalPending = 0.0
        var criticalCount = 0
        var warningCount = 0

        for (category in allCategoriesSet) {
            val rawLimit = round2(kotlin.math.max(0.0, consolidatedBudgets[category] ?: 0.0))
            val limit = round2(kotlin.math.max(0.0, effectiveBudgets[category] ?: 0.0))
            val spentAmount = round2(kotlin.math.max(0.0, consolidatedSpentMap[category] ?: 0.0))
            val pendingAmount = round2(kotlin.math.max(0.0, consolidatedPendingMap[category] ?: 0.0))
            val committedAmount = round2(spentAmount + pendingAmount)
            val spent = committedAmount

            if (limit <= 0.0 && (category in NON_ENVELOPE_CATEGORIES || normalizeBudgetCategory(category) in NON_ENVELOPE_CATEGORIES)) continue
            if (limit <= 0.0 && committedAmount <= 0.0) continue

            val remaining = round2(limit - committedAmount)
            val realRemaining = round2(kotlin.math.max(0.0, limit - committedAmount))
            val percentage = if (limit > 0.0) round2((spentAmount / limit) * 100.0) else if (spentAmount > 0.0) 100.0 else 0.0
            val pendingPercentage = if (limit > 0.0) round2((pendingAmount / limit) * 100.0) else 0.0
            val totalPercentage = if (limit > 0.0) round2((committedAmount / limit) * 100.0) else if (committedAmount > 0.0) 100.0 else 0.0
            val isFixedCategory = isFixedBudgetCategory(category)
            val isInvestmentCategory = category == "Investimentos" || category.contains("investimento", ignoreCase = true)

            val isPaidOverspent = limit > 0.0 && (spentAmount - limit >= 0.01)
            val isCommittedOverspent = limit > 0.0 && (committedAmount - limit >= 0.01)
            val isExactLimit = limit > 0.0 && kotlin.math.abs(committedAmount - limit) < 0.01

            var status = CategoryBudgetAlertStatus.OK
            var badgeLabel = "Sob Controle"

            if (isInvestmentCategory) {
                status = CategoryBudgetAlertStatus.OK
                badgeLabel = "Sob Controle"
            } else if (limit <= 0.0) {
                status = if (committedAmount > 0.0) CategoryBudgetAlertStatus.WARNING else CategoryBudgetAlertStatus.OK
                badgeLabel = "Definir Teto"
                if (status == CategoryBudgetAlertStatus.WARNING) warningCount++
            } else if (isPaidOverspent) {
                status = CategoryBudgetAlertStatus.DANGER
                badgeLabel = "Estourado"
                criticalCount++
            } else if (isCommittedOverspent) {
                status = CategoryBudgetAlertStatus.DANGER
                badgeLabel = "Vai Estourar"
                criticalCount++
            } else if (isExactLimit) {
                if (spentAmount == 0.0 && pendingAmount > 0.0) {
                    status = CategoryBudgetAlertStatus.OK
                    badgeLabel = "Previsto no Teto"
                } else {
                    status = if (isFixedCategory) CategoryBudgetAlertStatus.OK else CategoryBudgetAlertStatus.WARNING
                    badgeLabel = if (isFixedCategory) "Quitado / Dentro do Previsto" else "No Limite"
                    if (status == CategoryBudgetAlertStatus.WARNING) warningCount++
                }
            } else if (isFixedCategory) {
                status = CategoryBudgetAlertStatus.OK
                badgeLabel = "Fixo em Dia"
            } else {
                // Gastos variáveis
                if (spentAmount == 0.0 && pendingAmount > 0.0 && committedAmount <= limit + 0.01) {
                    status = CategoryBudgetAlertStatus.OK
                    badgeLabel = "Previsto no Teto"
                } else if (totalPercentage >= 90.0) {
                    status = CategoryBudgetAlertStatus.WARNING
                    badgeLabel = "Quase no Limite"
                    warningCount++
                } else if (totalPercentage >= 80.0) {
                    status = CategoryBudgetAlertStatus.WARNING
                    val isFast = currentDay > 7 && ((spent / currentDay) * totalDays) > limit
                    badgeLabel = if (isFast) "Ritmo Rápido" else "Quase no Limite"
                    warningCount++
                } else if (currentDay <= 7) {
                    if (totalPercentage > 65.0) {
                        status = CategoryBudgetAlertStatus.OK
                        badgeLabel = "Ritmo Rápido"
                    } else {
                        status = CategoryBudgetAlertStatus.OK
                        badgeLabel = "Sob Controle"
                    }
                } else {
                    val dailyPaceCalc = if (currentDay > 0) spent / currentDay else 0.0
                    val projectedCalc = dailyPaceCalc * totalDays
                    if (projectedCalc >= limit * 0.95) {
                        status = CategoryBudgetAlertStatus.OK
                        badgeLabel = "Ritmo Rápido"
                    } else {
                        status = CategoryBudgetAlertStatus.OK
                        badgeLabel = "Sob Controle"
                    }
                }
            }

            val dailyPace = if (currentDay > 0) round2(spent / currentDay) else 0.0
            val projectedSpent = if (isFixedCategory && limit > 0.0) {
                round2(kotlin.math.max(committedAmount, limit))
            } else {
                round2(dailyPace * totalDays)
            }
            val projectedPercentage = if (limit > 0.0) {
                round2((projectedSpent / limit) * 100.0)
            } else if (projectedSpent > 0.0) 100.0 else 0.0
            val recommendedDailyRemaining = if (realRemaining > 0.0 && daysRemaining > 0) {
                round2(realRemaining / daysRemaining)
            } else 0.0

            val burnRateStatus = when {
                isInvestmentCategory || isFixedCategory -> {
                    if (committedAmount - limit >= 0.01) CategoryBudgetPaceStatus.CRITICAL else CategoryBudgetPaceStatus.NORMAL
                }
                currentDay <= 7 -> {
                    when {
                        committedAmount - limit >= 0.01 -> CategoryBudgetPaceStatus.CRITICAL
                        totalPercentage >= 90.0 -> CategoryBudgetPaceStatus.CRITICAL
                        totalPercentage > 65.0 -> CategoryBudgetPaceStatus.FAST
                        else -> CategoryBudgetPaceStatus.NORMAL
                    }
                }
                else -> {
                    when {
                        committedAmount - limit >= 0.01 || projectedPercentage >= 115.0 -> CategoryBudgetPaceStatus.CRITICAL
                        projectedPercentage >= 95.0 || totalPercentage >= 90.0 -> CategoryBudgetPaceStatus.FAST
                        else -> CategoryBudgetPaceStatus.NORMAL
                    }
                }
            }

            if (limit > 0.0 && !isInvestmentCategory) {
                totalBudgeted += limit
                totalSpent += spentAmount
                totalPending += pendingAmount
            }

            items.add(
                CategoryBudgetStatus(
                    category = category,
                    limit = limit,
                    rawLimit = rawLimit,
                    spent = spent,
                    spentAmount = spentAmount,
                    pendingAmount = pendingAmount,
                    committedAmount = committedAmount,
                    remaining = remaining,
                    realRemaining = realRemaining,
                    percentage = percentage,
                    pendingPercentage = pendingPercentage,
                    totalPercentage = totalPercentage,
                    status = status,
                    badgeLabel = badgeLabel,
                    dailyPace = dailyPace,
                    projectedSpent = projectedSpent,
                    projectedPercentage = projectedPercentage,
                    recommendedDailyRemaining = recommendedDailyRemaining,
                    burnRateStatus = burnRateStatus,
                    daysRemaining = daysRemaining,
                    currentDay = currentDay,
                    totalDays = totalDays,
                    isFixedCategory = isFixedCategory
                )
            )
        }

        items.sortWith(
            compareByDescending<CategoryBudgetStatus> {
                when (it.status) {
                    CategoryBudgetAlertStatus.DANGER -> 3
                    CategoryBudgetAlertStatus.WARNING -> 2
                    CategoryBudgetAlertStatus.OK -> 1
                }
            }.thenByDescending { it.committedAmount }
        )

        totalBudgeted = round2(totalBudgeted)
        totalSpent = round2(totalSpent)
        totalPending = round2(totalPending)
        val totalCommitted = round2(totalSpent + totalPending)
        val totalRemaining = round2(totalBudgeted - totalCommitted)
        val totalRealRemaining = round2(kotlin.math.max(0.0, totalBudgeted - totalCommitted))
        val overallPercentage = if (totalBudgeted > 0.0) round2((totalSpent / totalBudgeted) * 100.0) else 0.0

        val overallStatus = when {
            totalCommitted > totalBudgeted + 0.01 || criticalCount > 0 -> CategoryBudgetAlertStatus.DANGER
            overallPercentage >= 80.0 || warningCount > 0 -> CategoryBudgetAlertStatus.WARNING
            else -> CategoryBudgetAlertStatus.OK
        }

        val topOverspentCategory = items.firstOrNull {
            !it.category.contains("investimento", ignoreCase = true) &&
            (it.status == CategoryBudgetAlertStatus.DANGER || (it.committedAmount - it.limit >= 0.01))
        }

        val unbudgetedCategories = consolidatedSpentMap.entries
            .filter { (cat, amt) ->
                amt > 0.0 &&
                    (effectiveBudgets[cat] ?: 0.0) <= 0.0 &&
                    cat !in NON_ENVELOPE_CATEGORIES &&
                    normalizeBudgetCategory(cat) !in NON_ENVELOPE_CATEGORIES
            }
            .map { (cat, amt) -> CategoryExpense(category = cat, amount = round2(amt)) }
            .sortedByDescending { it.amount }

        val incomeUsagePercentage = if (totalAvailableCapacity > 0.0) {
            round2((totalBudgeted / totalAvailableCapacity) * 100.0)
        } else 0.0
        val unallocatedIncome = if (totalAvailableCapacity > 0.0) {
            round2(kotlin.math.max(0.0, totalAvailableCapacity - totalBudgeted))
        } else 0.0

        val investmentTarget = consolidatedBudgets["Investimentos"] ?: 0.0
        val investmentSpent = consolidatedSpentMap["Investimentos"] ?: 0.0
        val investmentPercentage = if (investmentTarget > 0.0) round2((investmentSpent / investmentTarget) * 100.0) else 0.0
        // Meta 50/30/20: 20% da renda prevista, independente do teto de envelope (que pode ter sido reduzido pela calibragem)
        val investmentGoal = round2(kotlin.math.max(0.0, effectiveExpectedIncome) * 0.20)

        return CategoryBudgetReport(
            items = items,
            totalBudgeted = totalBudgeted,
            rawTotalBudgeted = rawTotalBudgeted,
            expectedIncome = effectiveExpectedIncome,
            carryoverAmount = effectiveCarryover,
            previousMonthDeficit = effectiveDeficit,
            isCappedToIncome = isCappedToIncome,
            incomeUsagePercentage = incomeUsagePercentage,
            unallocatedIncome = unallocatedIncome,
            totalSpent = totalSpent,
            totalPending = totalPending,
            totalCommitted = totalCommitted,
            totalRemaining = totalRemaining,
            totalRealRemaining = totalRealRemaining,
            overallPercentage = overallPercentage,
            status = overallStatus,
            criticalCount = criticalCount,
            warningCount = warningCount,
            topOverspentCategory = topOverspentCategory,
            unbudgetedCategories = unbudgetedCategories,
            investmentTarget = investmentTarget,
            investmentSpent = investmentSpent,
            investmentPercentage = investmentPercentage,
            investmentGoal = investmentGoal
        )
    }

    /**
     * Gera até 3 insights acionáveis e determinísticos (sem rede) sobre os envelopes.
     * Espelha `buildBudgetInsights` da Web. Ordenados por severidade (perigo > atenção > info).
     */
    fun buildBudgetInsights(report: CategoryBudgetReport): List<BudgetInsight> {
        val insights = mutableListOf<BudgetInsight>()

        if (report.items.isEmpty() || report.totalBudgeted <= 0.0) {
            insights.add(
                BudgetInsight(
                    id = "setup",
                    severity = BudgetInsightSeverity.INFO,
                    title = "Crie seus envelopes",
                    message = "Defina um teto por categoria para saber quanto ainda pode gastar. A IA monta um plano com base no seu histórico.",
                    action = BudgetInsightAction(BudgetInsightActionType.APPLY_PLAN, "Montar com IA")
                )
            )
            return insights
        }

        val rebalance = suggestBudgetRebalance(report)

        // 1) Estouros reais (>= R$ 1,00)
        report.items
            .filter { it.limit > 0.0 && (it.committedAmount - it.limit) >= 1.0 }
            .sortedByDescending { it.committedAmount - it.limit }
            .take(2)
            .forEach { item ->
                val excess = round2(item.committedAmount - item.limit)
                val transfer = rebalance.suggestions.firstOrNull { it.overspentCategory == item.category }
                val action = if (transfer != null && transfer.suggestedTransfer > 0.0) {
                    BudgetInsightAction(
                        type = BudgetInsightActionType.REBALANCE,
                        label = "Cobrir com ${transfer.compensateFrom}",
                        category = item.category,
                        amount = transfer.suggestedTransfer,
                        fromCategory = transfer.compensateFrom
                    )
                } else {
                    val raise = kotlin.math.ceil(excess / 10.0) * 10.0
                    BudgetInsightAction(
                        type = BudgetInsightActionType.ADJUST_LIMIT,
                        label = "Subir teto em ${formatCurrency(raise)}",
                        category = item.category,
                        amount = raise
                    )
                }
                insights.add(
                    BudgetInsight(
                        id = "over_${item.category}",
                        severity = BudgetInsightSeverity.DANGER,
                        title = "${item.category} passou do teto",
                        message = "Comprometido ${formatCurrency(item.committedAmount)} de ${formatCurrency(item.limit)} (${formatCurrency(excess)} acima).",
                        action = action
                    )
                )
            }

        // 2) Ritmo de gasto: variáveis perto do teto ou projetadas para estourar
        report.items
            .filter {
                it.limit > 0.0 && !it.isFixedCategory &&
                    (it.committedAmount - it.limit) < 1.0 &&
                    it.spentAmount > 0.0 &&
                    (it.totalPercentage >= 85.0 || it.projectedSpent > it.limit + 1.0)
            }
            .sortedByDescending { it.totalPercentage }
            .take(1)
            .forEach { item ->
                val daily = if (item.daysRemaining > 0) item.realRemaining / item.daysRemaining else item.realRemaining
                insights.add(
                    BudgetInsight(
                        id = "pace_${item.category}",
                        severity = BudgetInsightSeverity.ATTENTION,
                        title = "${item.category} está acelerado",
                        message = "Restam ${formatCurrency(kotlin.math.max(0.0, item.realRemaining))} até o fim do mês, cerca de ${formatCurrency(kotlin.math.max(0.0, daily))} por dia.",
                        action = BudgetInsightAction(
                            type = BudgetInsightActionType.ASK_COPILOT,
                            label = "Pedir dica à IA",
                            category = item.category,
                            prompt = "Meu envelope de ${item.category} está em ${kotlin.math.round(item.totalPercentage).toInt()}% do teto. Como posso segurar os gastos até o fim do mês?"
                        )
                    )
                )
            }

        // 3) Gastos sem envelope
        report.unbudgetedCategories.firstOrNull()?.let { unb ->
            val suggestedLimit = kotlin.math.ceil(unb.amount / 50.0) * 50.0
            insights.add(
                BudgetInsight(
                    id = "unbudgeted_${unb.category}",
                    severity = BudgetInsightSeverity.ATTENTION,
                    title = "${unb.category} está sem teto",
                    message = "Você já tem ${formatCurrency(unb.amount)} nessa categoria e nenhum envelope para acompanhar.",
                    action = BudgetInsightAction(
                        type = BudgetInsightActionType.ADJUST_LIMIT,
                        label = "Criar teto de ${formatCurrency(suggestedLimit)}",
                        category = unb.category,
                        amount = suggestedLimit
                    )
                )
            )
        }

        // 4) Meta de aporte 50/30/20
        val goalGap = report.investmentGoal - report.investmentSpent
        if (report.investmentGoal > 0.0 && goalGap > 0.0 && report.investmentSpent < report.investmentGoal * 0.5) {
            insights.add(
                BudgetInsight(
                    id = "invest_goal",
                    severity = BudgetInsightSeverity.INFO,
                    title = "Meta de aporte: faltam ${formatCurrency(goalGap)}",
                    message = "Pela regra 50/30/20, separe 20% da renda (${formatCurrency(report.investmentGoal)}) para o seu futuro.",
                    action = BudgetInsightAction(
                        type = BudgetInsightActionType.ASK_COPILOT,
                        label = "Como atingir?",
                        prompt = "Quero atingir minha meta de aporte de ${formatCurrency(report.investmentGoal)} neste mês. Já investi ${formatCurrency(report.investmentSpent)}. Qual o melhor caminho?"
                    )
                )
            )
        }

        if (insights.isEmpty()) {
            insights.add(
                BudgetInsight(
                    id = "all_good",
                    severity = BudgetInsightSeverity.INFO,
                    title = "Tudo sob controle",
                    message = "Você ainda pode gastar ${formatCurrency(kotlin.math.max(0.0, report.totalRealRemaining))} dentro dos tetos até o fim do mês."
                )
            )
        }

        return insights
            .sortedByDescending { it.severity.ordinal }
            .take(3)
    }

    fun suggestCategoryBudgets(
        allTransactions: List<Transaction>,
        uid: String,
        referenceMonth: String = currentMonthKey(),
        monthlyIncome: Double = 0.0,
        carryoverAmount: Double = 0.0,
        previousMonthDeficit: Double = 0.0
    ): SuggestedBudgetsResult {
        val previousMonths = listOf(
            shiftMonth(referenceMonth, -1),
            shiftMonth(referenceMonth, -2),
            shiftMonth(referenceMonth, -3)
        )

        // Calcula a entrada prevista real do mês de referência a partir dos lançamentos de receita
        var referenceMonthIncome = 0.0
        for (tx in allTransactions) {
            if (tx.type != TransactionType.INCOME) continue
            val m = tx.competenceMonth.ifBlank { tx.dueDate.take(7) }
            if (m != referenceMonth) continue
            val imp = impactFor(tx, uid, allTransactions)
            if (imp > 0.0) referenceMonthIncome += imp
        }
        referenceMonthIncome = round2(referenceMonthIncome)

        // Prioriza o que é esperado entrar no mês selecionado + sobra do mês anterior
        val baseIncome = if (referenceMonthIncome > 0.0) {
            referenceMonthIncome
        } else {
            round2(kotlin.math.max(0.0, monthlyIncome))
        }
        val effectiveExpectedIncome = round2(kotlin.math.max(0.0, baseIncome + carryoverAmount - previousMonthDeficit))

        val monthCategoryTotals = mutableMapOf<String, MutableMap<String, Double>>()
        for (m in previousMonths) {
            monthCategoryTotals[m] = mutableMapOf()
        }

        var matchingCount = 0
        for (tx in allTransactions) {
            if (tx.type != TransactionType.EXPENSE) continue
            val m = tx.competenceMonth.ifBlank { tx.dueDate.take(7) }
            if (m !in previousMonths) continue
            val impact = impactFor(tx, uid, allTransactions)
            if (impact <= 0.0 || tx.category.isBlank()) continue
            val canonicalCat = normalizeBudgetCategory(tx.category)
            val catMap = monthCategoryTotals.getOrPut(m) { mutableMapOf() }
            catMap[canonicalCat] = (catMap[canonicalCat] ?: 0.0) + impact
            matchingCount++
        }

        val curMonthExpenseMap = mutableMapOf<String, Double>()
        var referenceMonthInvested = 0.0
        for (tx in allTransactions) {
            val m = tx.competenceMonth.ifBlank { tx.dueDate.take(7) }
            if (m != referenceMonth) continue
            val impact = impactFor(tx, uid, allTransactions)
            if (tx.type == TransactionType.EXPENSE && impact > 0.0 && tx.category.isNotBlank()) {
                val canonicalCat = normalizeBudgetCategory(tx.category)
                if (canonicalCat == "Investimentos") {
                    referenceMonthInvested += impact
                } else {
                    curMonthExpenseMap[canonicalCat] = (curMonthExpenseMap[canonicalCat] ?: 0.0) + impact
                }
            } else if (tx.type == TransactionType.INCOME && impact > 0.0) {
                val isResgate = tx.category == "Investimentos" ||
                    (tx.category == "Rendimentos & Investimentos" && (tx.subcategory?.contains("Resgate", ignoreCase = true) == true))
                if (isResgate) {
                    referenceMonthInvested -= impact
                }
            }
        }
        referenceMonthInvested = round2(kotlin.math.max(0.0, referenceMonthInvested))

        if (matchingCount == 0) {
            monthCategoryTotals[referenceMonth] = curMonthExpenseMap
        }

        val activeMonths = monthCategoryTotals.keys.filter { (monthCategoryTotals[it]?.size ?: 0) > 0 }
        val divisor = kotlin.math.max(1, activeMonths.size)

        val sumMap = mutableMapOf<String, Double>()
        for (m in activeMonths) {
            val catMap = monthCategoryTotals[m] ?: continue
            for ((cat, amt) in catMap) {
                sumMap[cat] = (sumMap[cat] ?: 0.0) + amt
            }
        }

        // Inclui categorias com gastos no mês atual mesmo que ausentes do histórico
        for ((cat, curAmt) in curMonthExpenseMap) {
            if (!sumMap.containsKey(cat) && curAmt >= 20.0) {
                sumMap[cat] = curAmt * divisor
            }
        }

        val suggestedBudgets = linkedMapOf<String, Double>()
        val categoryAverages = linkedMapOf<String, Double>()
        var totalSuggested = 0.0

        for ((cat, sum) in sumMap) {
            if (cat in NON_ENVELOPE_CATEGORIES || normalizeBudgetCategory(cat) in NON_ENVELOPE_CATEGORIES) continue
            val avg = round2(sum / divisor)
            categoryAverages[cat] = avg
            val curCommitted = curMonthExpenseMap[cat] ?: 0.0
            if (avg >= 20.0 || curCommitted > 0.0) {
                val isFixed = isFixedBudgetCategory(cat)
                val baseTarget = if (isFixed) {
                    kotlin.math.max(avg, curCommitted)
                } else {
                    kotlin.math.max(avg * 1.15, curCommitted * 1.20)
                }
                val rawSuggested = if (isFixed) {
                    kotlin.math.max(50.0, kotlin.math.ceil(baseTarget / 10.0) * 10.0)
                } else {
                    kotlin.math.max(50.0, kotlin.math.ceil(baseTarget / 50.0) * 50.0)
                }
                // O piso inegociável é o que já foi gasto no mês selecionado
                val committedCeil = if (isFixed) {
                    kotlin.math.ceil(curCommitted / 10.0) * 10.0
                } else {
                    kotlin.math.ceil(curCommitted / 50.0) * 50.0
                }
                val suggested = kotlin.math.max(committedCeil, rawSuggested)
                suggestedBudgets[cat] = suggested
                totalSuggested += suggested
            }
        }

        if (referenceMonthInvested > 0.0 && !suggestedBudgets.containsKey("Investimentos")) {
            val roundedInvest = kotlin.math.ceil(referenceMonthInvested / 10.0) * 10.0
            suggestedBudgets["Investimentos"] = roundedInvest
            categoryAverages["Investimentos"] = roundedInvest
            totalSuggested += roundedInvest
        }

        var isCappedToIncome = false
        // Calibração estrita: o orçamento sugerido nunca pode exceder a entrada prevista do mês
        // e NUNCA pode ser menor do que o que o usuário já gastou no mês
        if (effectiveExpectedIncome > 0.0 && totalSuggested > effectiveExpectedIncome) {
            isCappedToIncome = true
            var sumFloors = 0.0
            val floorsMap = mutableMapOf<String, Double>()
            for (cat in suggestedBudgets.keys) {
                val rawFloor = curMonthExpenseMap[cat] ?: (if (cat == "Investimentos") referenceMonthInvested else 0.0)
                val floor = kotlin.math.ceil(rawFloor / 10.0) * 10.0
                floorsMap[cat] = floor
                sumFloors += floor
            }
            sumFloors = round2(sumFloors)

            if (sumFloors >= effectiveExpectedIncome) {
                var newTotal = 0.0
                for (cat in suggestedBudgets.keys.toList()) {
                    val floor = floorsMap[cat] ?: 0.0
                    suggestedBudgets[cat] = floor
                    newTotal = round2(newTotal + floor)
                }
                totalSuggested = newTotal
            } else {
                val availableFlex = round2(effectiveExpectedIncome - sumFloors)
                var sumDesiredFlex = 0.0
                val desiredFlexMap = mutableMapOf<String, Double>()
                for (cat in suggestedBudgets.keys) {
                    val floor = floorsMap[cat] ?: 0.0
                    val desired = kotlin.math.max(0.0, (suggestedBudgets[cat] ?: 0.0) - floor)
                    desiredFlexMap[cat] = desired
                    sumDesiredFlex += desired
                }
                sumDesiredFlex = round2(sumDesiredFlex)

                var newTotal = 0.0
                var largestFlexCat = ""
                var largestFlexVal = -1.0

                for (cat in suggestedBudgets.keys.toList()) {
                    val floor = floorsMap[cat] ?: 0.0
                    val desired = desiredFlexMap[cat] ?: 0.0
                    val scaledFlex = if (sumDesiredFlex > 0.0) {
                        kotlin.math.floor(((desired / sumDesiredFlex) * availableFlex) / 10.0) * 10.0
                    } else 0.0
                    val finalVal = round2(floor + scaledFlex)
                    suggestedBudgets[cat] = finalVal
                    newTotal = round2(newTotal + finalVal)

                    if (desired > largestFlexVal) {
                        largestFlexVal = desired
                        largestFlexCat = cat
                    }
                }

                val diff = kotlin.math.floor((effectiveExpectedIncome - newTotal) / 10.0) * 10.0
                if (largestFlexCat.isNotEmpty() && diff >= 10.0) {
                    suggestedBudgets[largestFlexCat] = round2((suggestedBudgets[largestFlexCat] ?: 0.0) + diff)
                    newTotal = round2(newTotal + diff)
                }
                totalSuggested = newTotal
            }
        } else {
            totalSuggested = round2(totalSuggested)
        }

        val incomeCoveragePct = if (effectiveExpectedIncome > 0.0) {
            round2((totalSuggested / effectiveExpectedIncome) * 100.0)
        } else 0.0

        return SuggestedBudgetsResult(
            suggestedBudgets = suggestedBudgets,
            totalSuggested = totalSuggested,
            incomeCoveragePct = incomeCoveragePct,
            expectedIncome = effectiveExpectedIncome,
            isCappedToIncome = isCappedToIncome,
            categoryAverages = categoryAverages
        )
    }

    fun suggestBudgetRebalance(report: CategoryBudgetReport): BudgetRebalanceResult {
        // Uma categoria só é elegível para socorro se tiver estouro real >= R$ 1,00
        val overspent = report.items.filter {
            it.limit > 0.0 && !it.category.contains("investimento", ignoreCase = true) &&
            ((it.committedAmount - it.limit) >= 1.00)
        }
        // Só retira de categorias variáveis que tenham folga real livre (realRemaining) >= R$ 10,00
        val surplus = report.items.filter {
            it.limit > 0.0 && !it.isFixedCategory && !it.category.contains("investimento", ignoreCase = true) &&
            it.realRemaining >= 10.0
        }

        val deficitTotal = round2(overspent.sumOf { kotlin.math.max(0.0, it.committedAmount - it.limit) })
        val surplusTotal = round2(surplus.sumOf { it.realRemaining })

        if (overspent.isEmpty() || surplus.isEmpty()) {
            return BudgetRebalanceResult(
                canRebalance = false,
                deficitTotal = deficitTotal,
                surplusTotal = surplusTotal,
                suggestions = emptyList()
            )
        }

        val remainingSurplusMap = surplus.associate { it.category to it.realRemaining }.toMutableMap()
        val suggestions = mutableListOf<BudgetRebalanceSuggestion>()

        for (o in overspent) {
            var needed = round2(o.committedAmount - o.limit)
            for (s in surplus) {
                if (needed <= 0.0) break
                val avail = remainingSurplusMap[s.category] ?: 0.0
                if (avail <= 0.0) continue

                val rawTransfer = kotlin.math.min(needed, avail)
                val transfer = kotlin.math.max(5.0, kotlin.math.ceil(rawTransfer / 5.0) * 5.0)
                val safeTransfer = kotlin.math.min(transfer, avail)

                if (safeTransfer >= 5.0) {
                    val curFromLim = s.limit - (s.realRemaining - avail)
                    val curToLim = o.limit + ((o.committedAmount - o.limit) - needed)
                    suggestions.add(
                        BudgetRebalanceSuggestion(
                            overspentCategory = o.category,
                            overspentAmount = round2(o.committedAmount - o.limit),
                            compensateFrom = s.category,
                            availableSurplus = round2(avail),
                            suggestedTransfer = round2(safeTransfer),
                            newFromLimit = round2(kotlin.math.max(0.0, curFromLim - safeTransfer)),
                            newToLimit = round2(curToLim + safeTransfer)
                        )
                    )
                    needed = round2(kotlin.math.max(0.0, needed - safeTransfer))
                    remainingSurplusMap[s.category] = round2(kotlin.math.max(0.0, avail - safeTransfer))
                }
            }
        }

        val totalCovered = round2(suggestions.sumOf { it.suggestedTransfer })
        val summaryText = if (suggestions.isNotEmpty()) {
            val parts = suggestions.joinToString(", ") { "${formatCurrency(it.suggestedTransfer)} de ${it.compensateFrom} → ${it.overspentCategory}" }
            "Cobertura inteligente de ${formatCurrency(totalCovered)} ($parts)."
        } else ""

        return BudgetRebalanceResult(
            canRebalance = suggestions.isNotEmpty(),
            deficitTotal = deficitTotal,
            surplusTotal = surplusTotal,
            suggestions = suggestions,
            summaryText = summaryText
        )
    }

    /**
     * Validador matemático determinístico pós-IA para envelopes orçamentários.
     * Garante que:
     * 1. O teto sugerido NUNCA seja inferior ao que já foi pago + contas a vencer (piso inegociável).
     * 2. A soma dos tetos de despesas NUNCA ultrapasse a renda líquida real disponível no mês.
     * 3. Se os gastos já comprometidos consumirem ou superarem toda a renda disponível,
     *    os tetos são travados estritamente nos pisos reais consumidos e um alerta de déficit é gerado.
     * 4. Toda folga flexível é distribuída proporcionalmente apenas em despesas variáveis em múltiplos de R$ 10.
     */
    fun sanitizeAndValidateBudgetPlan(
        plan: SmartBudgetPlan,
        availableIncome: Double,
        spentByCategory: Map<String, Double> = emptyMap(),
        pendingByCategory: Map<String, Double> = emptyMap()
    ): SmartBudgetPlan {
        val maxAvailable = round2(kotlin.math.max(0.0, availableIncome))
        val rawAllocs = plan.allocations

        if (rawAllocs.isEmpty()) {
            return SmartBudgetPlan(
                summaryInsight = plan.summaryInsight.ifBlank { "Nenhuma alocação fornecida para calibrar." },
                allocations = emptyList()
            )
        }

        data class ItemCalc(
            val category: String,
            val reason: String,
            val isInvestment: Boolean,
            val isFixed: Boolean,
            val committed: Double,
            val floor: Double,
            val rawSuggested: Double
        )

        val canonSpent = mutableMapOf<String, Double>()
        for ((k, v) in spentByCategory) {
            if (v > 0.0) {
                val c = normalizeBudgetCategory(k)
                canonSpent[c] = round2((canonSpent[c] ?: 0.0) + v)
            }
        }
        val canonPending = mutableMapOf<String, Double>()
        for ((k, v) in pendingByCategory) {
            if (v > 0.0) {
                val c = normalizeBudgetCategory(k)
                canonPending[c] = round2((canonPending[c] ?: 0.0) + v)
            }
        }

        // Deduplica e consolida alocações por categoria canônica
        data class AllocData(val rawLimit: Double, val reason: String)
        val allocMap = linkedMapOf<String, AllocData>()
        for (alloc in rawAllocs) {
            val rawCat = alloc.category.trim()
            if (rawCat.isBlank() || rawCat.contains("rendimento", ignoreCase = true) || rawCat.contains("salário", ignoreCase = true) || rawCat.contains("salario", ignoreCase = true)) continue
            val canonCat = normalizeBudgetCategory(rawCat)
            if (canonCat.isBlank()) continue

            val prev = allocMap[canonCat]
            val curLimit = alloc.suggestedLimit
            val curReason = alloc.reason.trim()

            if (prev == null) {
                allocMap[canonCat] = AllocData(rawLimit = curLimit, reason = curReason)
            } else {
                val maxLimit = kotlin.math.max(prev.rawLimit, curLimit)
                val chosenReason = if (curReason.isNotBlank() && !Regex("""(?:sem|nenhum|zerad|não|nao)""", RegexOption.IGNORE_CASE).containsMatchIn(curReason)) {
                    curReason
                } else {
                    prev.reason
                }
                allocMap[canonCat] = AllocData(rawLimit = maxLimit, reason = chosenReason)
            }
        }

        // Assegura que TODAS as categorias com gastos ou pendências comprometidas estejam presentes
        val allCommittedCategories = linkedSetOf<String>().apply {
            addAll(canonSpent.keys)
            addAll(canonPending.keys)
        }
        for (cat in allCommittedCategories) {
            if (cat.isBlank() || cat.contains("rendimento", ignoreCase = true) || cat.contains("salário", ignoreCase = true) || cat.contains("salario", ignoreCase = true)) continue
            val canonCat = normalizeBudgetCategory(cat)
            val committed = round2((canonSpent[canonCat] ?: 0.0) + (canonPending[canonCat] ?: 0.0))
            if (committed > 0.0 && !allocMap.containsKey(canonCat)) {
                val pending = canonPending[canonCat] ?: 0.0
                val minFloor = kotlin.math.ceil(committed / 10.0) * 10.0
                val reason = if (pending > 0.0) {
                    "Garante os ${formatCurrency(committed)} já comprometidos no mês (${formatCurrency(pending)} a vencer)."
                } else {
                    "Garante os ${formatCurrency(committed)} já pagos no mês com margem de segurança."
                }
                allocMap[canonCat] = AllocData(rawLimit = minFloor, reason = reason)
            }
        }

        var totalFloorExpenses = 0.0
        val items = mutableListOf<ItemCalc>()

        for ((canonCat, data) in allocMap) {
            val isInv = canonCat.contains("investimento", ignoreCase = true)
            val isFix = isFixedBudgetCategory(canonCat)
            val spent = kotlin.math.max(0.0, canonSpent[canonCat] ?: 0.0)
            val pending = kotlin.math.max(0.0, canonPending[canonCat] ?: 0.0)
            val committed = round2(spent + pending)

            val floor = if (isInv) 0.0 else kotlin.math.ceil(committed / 10.0) * 10.0
            val rawSuggested = kotlin.math.max(floor, kotlin.math.ceil((data.rawLimit.takeIf { it > 0.0 } ?: floor) / 10.0) * 10.0)

            if (!isInv) {
                totalFloorExpenses += floor
            }

            var cleanReason = data.reason
            if (committed > 0.0) {
                val isHallucinated =
                    data.rawLimit < committed ||
                    Regex("""(?:sem|nenhum|zerad|não h[aá]|nao h[aá]|não possui|nao possui)\s*(?:compromisso|gasto|despesa|conta|pendência|pendencia|saída|saida|movimentação|movimentacao)?""", RegexOption.IGNORE_CASE).containsMatchIn(cleanReason) ||
                    cleanReason.isBlank()
                if (isHallucinated) {
                    cleanReason = if (pending > 0.0) {
                        "Garante os ${formatCurrency(committed)} já comprometidos no mês (${formatCurrency(pending)} a vencer)."
                    } else {
                        "Garante os ${formatCurrency(committed)} já pagos no mês com margem de segurança."
                    }
                }
            } else if (cleanReason.isBlank()) {
                cleanReason = if (isInv) {
                    "Meta de investimento e construção de patrimônio (50/30/20)."
                } else {
                    "Sem compromissos no momento, alocação preventiva de segurança."
                }
            }

            items.add(
                ItemCalc(
                    category = canonCat,
                    reason = cleanReason,
                    isInvestment = isInv,
                    isFixed = isFix,
                    committed = committed,
                    floor = floor,
                    rawSuggested = rawSuggested
                )
            )
        }

        // 2. Se os pisos das despesas já consom ou superam a renda líquida disponível:
        if (totalFloorExpenses >= maxAvailable && maxAvailable > 0.0) {
            val calibratedAllocs = items.map { item ->
                SmartBudgetAllocation(
                    category = item.category,
                    suggestedLimit = if (item.isInvestment) 0.0 else item.floor,
                    reason = if (item.isInvestment) {
                        "Aportes pausados: gastos essenciais e compromissos consom toda a renda do mês."
                    } else if (item.committed > 0.0) {
                        "Travado no piso real de ${formatCurrency(item.committed)} já comprometido neste mês."
                    } else {
                        "Teto mínimo de segurança mantido."
                    }
                )
            }

            val deficit = round2(totalFloorExpenses - maxAvailable)
            val deficitMsg = if (deficit > 0.0) {
                "Atenção: Seus gastos e contas já comprometidos (${formatCurrency(totalFloorExpenses)}) excedem a renda disponível (${formatCurrency(maxAvailable)}) em ${formatCurrency(deficit)}. Os envelopes foram travados estritamente no piso real já consumido para evitar endividamento adicional."
            } else {
                "Atenção: Seus gastos já comprometidos consom exatamente 100% da sua renda disponível do mês (${formatCurrency(maxAvailable)}). Os tetos foram travados nos pisos reais já consumidos sem margem flexível."
            }

            return SmartBudgetPlan(
                summaryInsight = deficitMsg,
                allocations = calibratedAllocs
            )
        }

        // Se a renda disponível for 0 ou negativa:
        if (maxAvailable <= 0.0) {
            val calibratedAllocs = items.map { item ->
                SmartBudgetAllocation(
                    category = item.category,
                    suggestedLimit = item.floor,
                    reason = if (item.floor > 0.0) {
                        "Travado no gasto já realizado de ${formatCurrency(item.committed)}."
                    } else {
                        "Sem renda disponível registrada para alocação."
                    }
                )
            }
            return SmartBudgetPlan(
                summaryInsight = "Renda líquida disponível não identificada ou zerada. Envelopes travados no piso dos gastos já ocorridos.",
                allocations = calibratedAllocs
            )
        }

        // 3. Há folga disponível acima dos pisos de despesas (maxAvailable > totalFloorExpenses):
        val availableFlex = maxAvailable - totalFloorExpenses
        val expenseItems = items.filter { !it.isInvestment }
        val investmentItem = items.firstOrNull { it.isInvestment }

        var targetInvestment = 0.0
        if (investmentItem != null) {
            val rawInv = investmentItem.rawSuggested
            val ideal20pct = kotlin.math.floor((maxAvailable * 0.20) / 10.0) * 10.0
            targetInvestment = kotlin.math.min(availableFlex, if (rawInv > 0.0) rawInv else ideal20pct)
            if (availableFlex - targetInvestment < 50.0 && expenseItems.any { !it.isFixed }) {
                targetInvestment = kotlin.math.max(0.0, kotlin.math.floor((availableFlex * 0.5) / 10.0) * 10.0)
            }
        }

        val flexForExpenses = availableFlex - targetInvestment

        var sumDesiredFlex = 0.0
        for (item in expenseItems) {
            if (!item.isFixed) {
                val desired = kotlin.math.max(0.0, item.rawSuggested - item.floor)
                sumDesiredFlex += desired
            }
        }

        var allocatedExpenseFlex = 0.0
        var largestFlexItem: ItemCalc? = null
        var maxDesired = -1.0

        val calibratedAllocs = mutableListOf<SmartBudgetAllocation>()

        for (item in expenseItems) {
            val finalLimit: Double
            if (item.isFixed) {
                finalLimit = if (item.floor > 0.0) item.floor else kotlin.math.min(flexForExpenses, item.rawSuggested)
            } else {
                val desired = kotlin.math.max(0.0, item.rawSuggested - item.floor)
                val share = if (sumDesiredFlex > 0.0) {
                    kotlin.math.floor(((desired / sumDesiredFlex) * flexForExpenses) / 10.0) * 10.0
                } else if (expenseItems.isNotEmpty()) {
                    kotlin.math.floor((flexForExpenses / expenseItems.size) / 10.0) * 10.0
                } else 0.0

                finalLimit = item.floor + share
                allocatedExpenseFlex += share

                if (desired > maxDesired) {
                    maxDesired = desired
                    largestFlexItem = item
                }
            }

            calibratedAllocs.add(
                SmartBudgetAllocation(
                    category = item.category,
                    suggestedLimit = finalLimit,
                    reason = item.reason.ifBlank { if (item.isFixed) "Teto de custo fixo contratual." else "Calibrado com base no histórico e folga real." }
                )
            )
        }

        val expenseResidue = kotlin.math.floor((flexForExpenses - allocatedExpenseFlex) / 10.0) * 10.0
        if (largestFlexItem != null && expenseResidue > 0.0) {
            val idx = calibratedAllocs.indexOfFirst { it.category == largestFlexItem.category }
            if (idx >= 0) {
                val current = calibratedAllocs[idx]
                calibratedAllocs[idx] = current.copy(suggestedLimit = current.suggestedLimit + expenseResidue)
                allocatedExpenseFlex += expenseResidue
            }
        }

        if (investmentItem != null) {
            val finalInvestment = kotlin.math.max(0.0, maxAvailable - (totalFloorExpenses + allocatedExpenseFlex))
            calibratedAllocs.add(
                SmartBudgetAllocation(
                    category = investmentItem.category,
                    suggestedLimit = finalInvestment,
                    reason = if (finalInvestment > 0.0) {
                        "Meta de aporte protegida (${formatCurrency(finalInvestment)} garantidos no saldo disponível)."
                    } else {
                        "Sem margem para investimentos este mês após cobrir compromissos essenciais."
                    }
                )
            )
        }

        return SmartBudgetPlan(
            summaryInsight = plan.summaryInsight.ifBlank { "Plano de envelopes calibrado com sucesso respeitando 100% da sua renda líquida disponível." },
            allocations = calibratedAllocs
        )
    }
}


