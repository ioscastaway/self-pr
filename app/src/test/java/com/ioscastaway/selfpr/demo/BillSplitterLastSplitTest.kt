package com.ioscastaway.selfpr.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillSplitterLastSplitTest {

    @Test
    fun `lastSplit on empty history returns null instead of throwing`() {
        assertNull(BillSplitter.lastSplit(emptyList()))
    }

    @Test
    fun `lastSplit returns the most recent result`() {
        val first = BillSplitter.split("100", "2", 10)
        val second = BillSplitter.split("60", "3", 0)
        assertEquals(second, BillSplitter.lastSplit(listOf(first, second)))
    }
}
