package com.ioscastaway.selfpr.healer

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestPlanTest {
    private val crash = CrashReport("abcdef1234", 1L, "uncaught", "CRASH",
        "java.lang.NumberFormatException: For input string: \"12.5\"",
        "java.lang.NumberFormatException: For input string: \"12.5\"\n\tat com.ioscastaway.selfpr.demo.BillSplitter.split(BillSplitter.kt:14)",
        "0.1.0", "0123abc", "sdk_gphone64_arm64")
    private val d = Diagnosis("toInt on decimal input", 0.85, "why", listOf(FilePatch("a.kt", "x", "parse as double")), "fix: accept decimal amounts", "body text", listOf("no ANR handling"))

    @Test fun `branch, title and body are derived from the crash and diagnosis`() {
        val plan = PullRequestPlan.from(crash, d, d.patches, 5L)
        assertEquals("heal/numberformatexception-abcdef12", plan.branch)
        assertEquals("fix: accept decimal amounts", plan.title)
        assertTrue(plan.body.startsWith("body text"))
        assertTrue(plan.body.contains("Confidence: 0.85"))
        assertTrue(plan.body.contains("no ANR handling"))
        assertTrue(plan.commitMessage.contains("a.kt: parse as double"))
    }

    @Test fun `request bodies carry the right fields`() {
        val plan = PullRequestPlan.from(crash, d, d.patches, 5L)
        assertEquals("refs/heads/${plan.branch}", plan.createRefBody("deadbeef")["ref"]!!.jsonPrimitive.content)
        val put = plan.putFileBody(d.patches[0], "blobsha")
        assertEquals("eA==", put["content"]!!.jsonPrimitive.content)
        assertEquals("blobsha", put["sha"]!!.jsonPrimitive.content)
        assertTrue(plan.putFileBody(d.patches[0], null)["sha"] == null)
        val pr = plan.pullRequestBody("main")
        assertEquals(plan.branch, pr["head"]!!.jsonPrimitive.content)
        assertEquals("main", pr["base"]!!.jsonPrimitive.content)
    }

    @Test fun `prompt carries trace, notes and files`() {
        val p = HealPrompt.user(crash, listOf("a.kt" to "fun x() = 1"), "# notes")
        assertTrue(p.contains("BillSplitter.split"))
        assertTrue(p.contains("### a.kt"))
        assertTrue(p.contains("# notes"))
    }
}
