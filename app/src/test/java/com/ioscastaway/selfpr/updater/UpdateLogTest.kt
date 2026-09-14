package com.ioscastaway.selfpr.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateLogTest {
    private val pending = PendingUpdate("1111111", "abcdef0123456789", 9, 1000)

    @Test fun `the launch that runs the requested revision is the arrival`() {
        assertEquals(UpdateRecord("1111111", "abcdef0123456789", 9, 1000, 2000), UpdateLog.arrival(pending, "abcdef0", 2000))
    }

    @Test fun `any other launch is not`() {
        assertNull(UpdateLog.arrival(pending, "1111111", 2000))
        assertNull(UpdateLog.arrival(null, "abcdef0", 2000))
    }
}
