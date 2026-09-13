package com.ioscastaway.selfpr.healer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TraceParserTest {
    private val trace = """
        java.lang.NumberFormatException: For input string: "12.5"
            at java.lang.Integer.parseInt(Integer.java:652)
            at com.ioscastaway.selfpr.demo.BillSplitter.split(BillSplitter.kt:14)
            at com.ioscastaway.selfpr.ui.SplitScreenKt${'$'}SplitScreen${'$'}1${'$'}1.invoke(SplitScreen.kt:40)
            at androidx.compose.foundation.ClickableKt.onClick(Clickable.kt:100)
    """.trimIndent()

    @Test fun `own frames keep order and drop framework frames`() {
        val frames = TraceParser.ownFrames(trace, "com.ioscastaway.selfpr")
        assertEquals(listOf("com.ioscastaway.selfpr.demo.BillSplitter", "com.ioscastaway.selfpr.ui.SplitScreenKt\$SplitScreen\$1\$1"), frames.map { it.className })
        assertEquals(OwnFrame("com.ioscastaway.selfpr.demo.BillSplitter", "split", "BillSplitter.kt", 14), frames[0])
        assertEquals("com.ioscastaway.selfpr.ui.SplitScreenKt", frames[1].topLevelClass)
    }

    @Test fun `headline is the exception line`() {
        assertEquals("java.lang.NumberFormatException: For input string: \"12.5\"", TraceParser.headline(trace))
    }

    @Test fun `culprit prefers the innermost cause`() {
        val nested = "java.lang.RuntimeException: wrapper\n\tat com.ioscastaway.selfpr.ui.A.b(A.kt:1)\n" +
            "Caused by: java.lang.IllegalStateException: real\n\tat com.ioscastaway.selfpr.demo.C.d(C.kt:2)\n"
        assertEquals("com.ioscastaway.selfpr.demo.C", TraceParser.culprit(nested, "com.ioscastaway.selfpr")?.className)
        assertNull(TraceParser.culprit("java.lang.Error\n\tat android.os.Looper.loop(Looper.java:1)", "com.ioscastaway.selfpr"))
    }
}
