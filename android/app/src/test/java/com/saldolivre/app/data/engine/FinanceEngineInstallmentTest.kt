package com.saldolivre.app.data.engine

import com.saldolivre.app.data.model.Allocation
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.SplitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceEngineInstallmentTest {

    @Test
    fun stripInstallmentSuffix_removesVariousFormats() {
        assertEquals("Notebook Dell", FinanceEngine.stripInstallmentSuffix("Notebook Dell (3/10)"))
        assertEquals("iPhone 15 Pro", FinanceEngine.stripInstallmentSuffix("iPhone 15 Pro ( 1 / 12 )"))
        assertEquals("Curso de Inglês", FinanceEngine.stripInstallmentSuffix("Curso de Inglês (02 de 06)"))
        assertEquals("Aluguel", FinanceEngine.stripInstallmentSuffix("Aluguel"))

        assertEquals("Notebook Dell", FinanceEngine.formatInstallmentDesc("Notebook Dell (3/10)", 3, 10))
        assertEquals("iPhone 15 Pro", FinanceEngine.formatInstallmentDesc("iPhone 15 Pro ( 1 / 12 )", 1, 12))
        assertEquals("Curso de Inglês", FinanceEngine.formatInstallmentDesc("Curso de Inglês (02 de 06)", 2, 6))
        assertEquals("Aluguel", FinanceEngine.formatInstallmentDesc("Aluguel", 1, 1))
    }

    @Test
    fun buildSplit_preservesFriendReimbursementFromPrevious() {
        val myUid = "u_me"
        val myName = "Eu"
        val friendUid = "u_friend"
        val friendName = "Amigo"

        val prevAllocations = listOf(
            Allocation(type = "PLATFORM_USER", uid = myUid, name = myName, amount = 50.0, reimbursed = true),
            Allocation(type = "PLATFORM_USER", uid = friendUid, name = friendName, amount = 50.0, reimbursed = true)
        )

        val result = FinanceEngine.buildSplit(
            splitType = SplitType.FRIEND,
            total = 100.0,
            myUid = myUid,
            myName = myName,
            partnerUid = null,
            partnerName = null,
            payerUid = myUid,
            assignedFriendUid = friendUid,
            assignedFriendName = friendName,
            friendSharePct = 50.0,
            previous = prevAllocations
        )

        val friendAlloc = result.allocations.find { it.uid == friendUid }
        assertTrue("Reembolso prévio do amigo deve ser preservado como true", friendAlloc?.reimbursed == true)
    }

    @Test
    fun buildSplit_maintainsFalseWhenFriendNotReimbursed() {
        val myUid = "u_me"
        val myName = "Eu"
        val friendUid = "u_friend"
        val friendName = "Amigo"

        val prevAllocations = listOf(
            Allocation(type = "PLATFORM_USER", uid = myUid, name = myName, amount = 50.0, reimbursed = true),
            Allocation(type = "PLATFORM_USER", uid = friendUid, name = friendName, amount = 50.0, reimbursed = false)
        )

        val result = FinanceEngine.buildSplit(
            splitType = SplitType.FRIEND,
            total = 100.0,
            myUid = myUid,
            myName = myName,
            partnerUid = null,
            partnerName = null,
            payerUid = myUid,
            assignedFriendUid = friendUid,
            assignedFriendName = friendName,
            friendSharePct = 50.0,
            previous = prevAllocations
        )

        val friendAlloc = result.allocations.find { it.uid == friendUid }
        assertFalse("Reembolso prévio não pago deve continuar false", friendAlloc?.reimbursed == true)
    }

    @Test
    fun calculateCardInvoiceDates_recalculatesCycleWhenChangingCard() {
        val cardNubank = Card(
            id = "c_nubank",
            cardName = "Nubank",
            ownerUid = "u_me",
            closingDay = 25,
            dueDay = 5
        )

        val cardInter = Card(
            id = "c_inter",
            cardName = "Inter",
            ownerUid = "u_me",
            closingDay = 30,
            dueDay = 10
        )

        // Compra feita em 2026-01-28
        // No Nubank (fecha 25, vence 5): 28 >= 25 -> fecha em fev, vence em março (2026-03)
        val datesNubank = FinanceEngine.calculateCardInvoiceDates("2026-01-28", cardNubank)
        assertEquals("2026-03", datesNubank.second)
        assertEquals("2026-03-05", datesNubank.first)

        // No Inter (fecha 30, vence 10): 28 < 30 -> fecha em jan, vence em fev (2026-02)
        val datesInter = FinanceEngine.calculateCardInvoiceDates("2026-01-28", cardInter)
        assertEquals("2026-02", datesInter.second)
        assertEquals("2026-02-10", datesInter.first)
    }

    // --- Testes da Issue #77 ---

    @Test
    fun calculateInstallmentCompetenceMonths_avoidsMonthRolloverCollapse() {
        val cardNubank = Card(
            id = "c_nubank",
            cardName = "Nubank",
            ownerUid = "u_me",
            closingDay = 30,
            dueDay = 10
        )

        // Compra feita em 2026-01-31 em 3x:
        // Parcela 1: 31/01 >= 30 -> fecha em fev, vence em março (2026-03)
        // Parcela 2: avança estritamente para 2026-04 (sem colapsar em 2026-03 por fevereiro ter 28 dias)
        // Parcela 3: 2026-05
        val months = FinanceEngine.calculateInstallmentCompetenceMonths("2026-01-31", cardNubank, 3, true)
        assertEquals(listOf("2026-03", "2026-04", "2026-05"), months)
    }

    @Test
    fun calculateInstallmentCompetenceMonths_advancesDeterministicForNonCreditCard() {
        // Compra sem cartão (ex: boleto ou pix) em 2026-01-31:
        // Parcela 1: 2026-01
        // Parcela 2: 2026-02
        // Parcela 3: 2026-03
        val months = FinanceEngine.calculateInstallmentCompetenceMonths("2026-01-31", null, 3, false)
        assertEquals(listOf("2026-01", "2026-02", "2026-03"), months)
    }

    @Test
    fun fractionCustomAllocations_splitsAllocationsWithoutMultiplying() {
        val origAllocs = listOf(
            Allocation(type = "PLATFORM_USER", uid = "u_me", name = "Eu", amount = 150.0, reimbursed = true),
            Allocation(type = "PLATFORM_USER", uid = "u_friend", name = "Amigo", amount = 150.0, reimbursed = false)
        )

        val p1 = FinanceEngine.fractionCustomAllocations(origAllocs, 1, 3, false)
        val p2 = FinanceEngine.fractionCustomAllocations(origAllocs, 2, 3, false)
        val p3 = FinanceEngine.fractionCustomAllocations(origAllocs, 3, 3, false)

        assertEquals(50.0, p1?.find { it.uid == "u_friend" }?.amount ?: 0.0, 0.001)
        assertEquals(50.0, p2?.find { it.uid == "u_friend" }?.amount ?: 0.0, 0.001)
        assertEquals(50.0, p3?.find { it.uid == "u_friend" }?.amount ?: 0.0, 0.001)
    }

    @Test
    fun fractionCustomAllocations_absorbsRemainderCentsOnLastInstallment() {
        val origAllocs = listOf(
            Allocation(type = "PLATFORM_USER", uid = "u_friend", name = "Amigo", amount = 100.0, reimbursed = false)
        )

        val p1 = FinanceEngine.fractionCustomAllocations(origAllocs, 1, 3, false)
        val p2 = FinanceEngine.fractionCustomAllocations(origAllocs, 2, 3, false)
        val p3 = FinanceEngine.fractionCustomAllocations(origAllocs, 3, 3, false)

        val a1 = p1?.get(0)?.amount ?: 0.0
        val a2 = p2?.get(0)?.amount ?: 0.0
        val a3 = p3?.get(0)?.amount ?: 0.0

        assertEquals(33.33, a1, 0.001)
        assertEquals(33.33, a2, 0.001)
        assertEquals(33.34, a3, 0.001)
        assertEquals(100.0, FinanceEngine.round2(a1 + a2 + a3), 0.001)
    }

    @Test
    fun calculateInstallmentAmount_absorbsRemainderCentsOnOddDivisions() {
        val p1 = FinanceEngine.calculateInstallmentAmount(100.0, 1, 3, false)
        val p2 = FinanceEngine.calculateInstallmentAmount(100.0, 2, 3, false)
        val p3 = FinanceEngine.calculateInstallmentAmount(100.0, 3, 3, false)

        assertEquals(33.33, p1, 0.001)
        assertEquals(33.33, p2, 0.001)
        assertEquals(33.34, p3, 0.001)
        assertEquals(100.0, FinanceEngine.round2(p1 + p2 + p3), 0.001)

        val parts = (1..6).map { FinanceEngine.calculateInstallmentAmount(100.0, it, 6, false) }
        assertEquals(listOf(16.67, 16.67, 16.67, 16.67, 16.67), parts.take(5))
        assertEquals(16.65, parts.last(), 0.001)
        assertEquals(100.0, FinanceEngine.round2(parts.sum()), 0.001)
    }

    @Test
    fun calculateInstallmentAmount_handlesNegativeRefundInstallments() {
        val p1 = FinanceEngine.calculateInstallmentAmount(-100.0, 1, 3, false)
        val p2 = FinanceEngine.calculateInstallmentAmount(-100.0, 2, 3, false)
        val p3 = FinanceEngine.calculateInstallmentAmount(-100.0, 3, 3, false)

        assertEquals(-33.33, p1, 0.001)
        assertEquals(-33.33, p2, 0.001)
        assertEquals(-33.34, p3, 0.001)
        assertEquals(-100.0, FinanceEngine.round2(p1 + p2 + p3), 0.001)
    }
}
