package com.saldolivre.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreSafeParsersTest {

    @Test
    fun `safeExtractDouble correctly converts Long to Double without ClassCastException`() {
        val longVal: Any = 50L
        val result = safeExtractDouble(longVal)
        assertEquals(50.0, result!!, 0.0001)
    }

    @Test
    fun `safeExtractDouble correctly converts Int to Double`() {
        val intVal: Any = 100
        val result = safeExtractDouble(intVal)
        assertEquals(100.0, result!!, 0.0001)
    }

    @Test
    fun `safeExtractDouble preserves Double values`() {
        val doubleVal: Any = 49.99
        val result = safeExtractDouble(doubleVal)
        assertEquals(49.99, result!!, 0.0001)
    }

    @Test
    fun `safeExtractDouble parses numeric String correctly`() {
        val strVal: Any = "150.75"
        val result = safeExtractDouble(strVal)
        assertEquals(150.75, result!!, 0.0001)
    }

    @Test
    fun `safeExtractDouble returns null for invalid formats or null`() {
        assertNull(safeExtractDouble(null))
        assertNull(safeExtractDouble("invalid_amount"))
        assertNull(safeExtractDouble(emptyList<Any>()))
    }

    @Test
    fun `safeExtractLong handles Double, Long, String and null`() {
        assertEquals(1762700000000L, safeExtractLong(1762700000000L))
        assertEquals(1762700000000L, safeExtractLong(1762700000000.0))
        assertEquals(12345L, safeExtractLong("12345"))
        assertNull(safeExtractLong(null))
        assertNull(safeExtractLong("not_a_number"))
    }

    @Test
    fun `safeExtractInt handles Long and String conversions`() {
        assertEquals(5, safeExtractInt(5L))
        assertEquals(12, safeExtractInt(12))
        assertEquals(3, safeExtractInt("3"))
        assertNull(safeExtractInt(null))
    }

    @Test
    fun `safeExtractBoolean parses Booleans, Numbers and Strings`() {
        assertTrue(safeExtractBoolean(true)!!)
        assertFalse(safeExtractBoolean(false)!!)
        assertTrue(safeExtractBoolean(1)!!)
        assertFalse(safeExtractBoolean(0)!!)
        assertTrue(safeExtractBoolean("true")!!)
        assertFalse(safeExtractBoolean("false")!!)
        assertNull(safeExtractBoolean(null))
        assertNull(safeExtractBoolean("other"))
    }
}
