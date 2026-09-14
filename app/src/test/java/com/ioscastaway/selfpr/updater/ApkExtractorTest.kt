package com.ioscastaway.selfpr.updater

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkExtractorTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    }.toByteArray()

    @Test fun `the one apk comes out whole and progress is reported`() {
        val apk = ByteArray(200_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        var last = 0L
        val r = ApkExtractor.extract(ByteArrayInputStream(zip("notes.txt" to "hi".toByteArray(), "app-debug.apk" to apk)), out) { last = it }
        assertEquals("app-debug.apk", r.entryName)
        assertEquals(apk.size.toLong(), r.bytes)
        assertEquals(apk.size.toLong(), last)
        assertArrayEquals(apk, out.toByteArray())
    }

    @Test fun `no apk, two apks, and traversal are refused`() {
        assertThrows(IllegalStateException::class.java) { ApkExtractor.extract(ByteArrayInputStream(zip("a.txt" to byteArrayOf(1))), ByteArrayOutputStream()) }
        assertThrows(IllegalStateException::class.java) { ApkExtractor.extract(ByteArrayInputStream(zip("a.apk" to byteArrayOf(1), "b.apk" to byteArrayOf(2))), ByteArrayOutputStream()) }
        assertThrows(IllegalArgumentException::class.java) { ApkExtractor.extract(ByteArrayInputStream(zip("../x.apk" to byteArrayOf(1))), ByteArrayOutputStream()) }
    }
}
