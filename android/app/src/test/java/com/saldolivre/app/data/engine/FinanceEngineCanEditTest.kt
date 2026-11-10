package com.saldolivre.app.data.engine

import com.saldolivre.app.data.model.Allocation
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionStatus
import com.saldolivre.app.data.model.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceEngineCanEditTest {

    private val myUid = "user_me"
    private val otherUid = "user_other"

    @Test
    fun `canUserEditTransaction returns false when currentUid is null or blank`() {
        val tx = Transaction(id = "tx_1", createdByUid = myUid)
        assertFalse(FinanceEngine.canUserEditTransaction(tx, null))
        assertFalse(FinanceEngine.canUserEditTransaction(tx, ""))
        assertFalse(FinanceEngine.canUserEditTransaction(tx, "   "))
    }

    @Test
    fun `canUserEditTransaction returns false for synthetic invoice transactions`() {
        val invoiceTx = Transaction(
            id = "invoice_card123_2026-03",
            createdByUid = myUid,
            paidByUid = myUid
        )
        assertFalse(FinanceEngine.canUserEditTransaction(invoiceTx, myUid))
    }

    @Test
    fun `canUserEditTransaction returns true when user is creator or payer`() {
        val tx1 = Transaction(id = "tx_1", createdByUid = myUid, paidByUid = myUid)
        assertTrue(FinanceEngine.canUserEditTransaction(tx1, myUid))

        val tx2 = Transaction(id = "tx_2", createdByUid = "", paidByUid = myUid)
        assertTrue(FinanceEngine.canUserEditTransaction(tx2, myUid))

        val tx3 = Transaction(id = "tx_3_payer", createdByUid = otherUid, paidByUid = myUid)
        assertTrue(FinanceEngine.canUserEditTransaction(tx3, myUid))
    }

    @Test
    fun `canUserEditTransaction returns false when transaction was created by another user`() {
        val tx = Transaction(id = "tx_3", createdByUid = otherUid, paidByUid = otherUid)
        assertFalse(FinanceEngine.canUserEditTransaction(tx, myUid))
    }

    @Test
    fun `canUserEditTransaction returns false when user is assigned friend in split`() {
        val tx = Transaction(
            id = "tx_4",
            createdByUid = otherUid,
            paidByUid = otherUid,
            assignedFriendUid = myUid
        )
        assertFalse(FinanceEngine.canUserEditTransaction(tx, myUid))
    }

    @Test
    fun `canUserEditTransaction returns false when user is in allocations but did not create`() {
        val tx = Transaction(
            id = "tx_5",
            createdByUid = otherUid,
            paidByUid = otherUid,
            allocations = listOf(
                Allocation(type = "PLATFORM_USER", uid = myUid, name = "Kayo", amount = 50.0)
            )
        )
        assertFalse(FinanceEngine.canUserEditTransaction(tx, myUid))
    }
}
