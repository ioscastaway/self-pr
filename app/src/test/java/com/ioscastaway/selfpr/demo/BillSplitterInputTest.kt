package com.ioscastaway.selfpr.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BillSplitterInputTest {

    @Test
    fun `decimal amount is accepted and rounded to whole units`() {
        // The crash: "12.5" used to reach Integer.parseInt and throw NumberFormatException.
        val r = BillSplitter.split("12.5", "2", 10)
        assertEquals(13, r.amount)
        assertEquals(2, r.people)
        assertEquals(1, r.tipTotal)
        assertEquals(7, r.perPerson)
    }

    @Test
    fun `decimal amount rounds down below half`() {
        assertEquals(12, BillSplitter.split("12.4", "2", 0).amount)
    }

    @Test
    fun `whole numbers still work`() {
        val r = BillSplitter.split(" 100 ", " 4 ", 10)
        assertEquals(100, r.amount)
        assertEquals(10, r.tipTotal)
        assertEquals(27, r.perPerson)
    }

    @Test
    fun `non numeric amount is reported not thrown as NumberFormatException`() {
        assertInvalid { BillSplitter.split("abc", "2", 10) }
    }

    @Test
    fun `empty fields are reported`() {
        assertInvalid { BillSplitter.split("", "2", 10) }
        assertInvalid { BillSplitter.split("10", "", 10) }
    }

    @Test
    fun `zero or negative people is reported instead of dividing by zero`() {
        assertInvalid { BillSplitter.split("10", "0", 10) }
        assertInvalid { BillSplitter.split("10", "-3", 10) }
    }

    @Test
    fun `negative amount is reported`() {
        assertInvalid { BillSplitter.split("-10", "2", 10) }
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("expected BillSplitter.InvalidInput")
        } catch (e: BillSplitter.InvalidInput) {
            assertEquals(true, !e.message.isNullOrBlank())
        }
    }
}
