package com.ioscastaway.selfpr.healer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SourceIndexTest {
    private val main = "app/src/main/java/com/ioscastaway/selfpr/demo/BillSplitter.kt"
    private val test = "app/src/test/java/com/ioscastaway/selfpr/demo/BillSplitterTest.kt"
    private val index = SourceIndex(mapOf(main to "object BillSplitter", test to "class BillSplitterTest", "docs/ARCHITECTURE.md" to "# notes"))

    @Test fun `class names resolve to bundled paths`() {
        assertEquals(main, index.pathForClass("com.ioscastaway.selfpr.demo.BillSplitter"))
        assertEquals(main, index.pathForClass("com.ioscastaway.selfpr.demo.BillSplitter\$Result"))
        assertNull(index.pathForClass("com.ioscastaway.selfpr.demo.Missing"))
        assertEquals(test, index.testFor(main))
    }

    @Test fun `filesFor dedups and keeps frame order`() {
        val frames = listOf(
            OwnFrame("com.ioscastaway.selfpr.demo.BillSplitter", "split", "BillSplitter.kt", 14),
            OwnFrame("com.ioscastaway.selfpr.demo.BillSplitter\$Companion", "x", "BillSplitter.kt", 2),
            OwnFrame("com.ioscastaway.selfpr.ui.Nope", "y", "Nope.kt", 1),
        )
        assertEquals(listOf(main to "object BillSplitter"), index.filesFor(frames))
    }

    @Test fun `fromZip reads every file entry`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("a/b.kt")); z.write("hello".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("a/")); z.closeEntry()
            }
        }.toByteArray()
        val idx = SourceIndex.fromZip(ByteArrayInputStream(bytes))
        assertEquals(listOf("a/b.kt"), idx.paths)
        assertEquals("hello", idx["a/b.kt"])
    }
}
