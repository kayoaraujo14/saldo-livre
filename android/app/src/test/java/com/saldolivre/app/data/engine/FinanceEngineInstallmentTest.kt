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
}
