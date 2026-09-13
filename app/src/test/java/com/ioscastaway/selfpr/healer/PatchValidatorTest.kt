package com.ioscastaway.selfpr.healer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchValidatorTest {
    private val main = "app/src/main/java/com/ioscastaway/selfpr/demo/BillSplitter.kt"
    private val index = SourceIndex(mapOf(main to "old\ncontent"))
    private fun diag(vararg patches: FilePatch) = Diagnosis("cause", 0.9, "why", patches.toList())

    @Test fun `a changed bundled file and a new test file pass`() {
        val v = PatchValidator.validate(diag(FilePatch(main, "new\ncontent"), FilePatch("app/src/test/java/X.kt", "class X")), index)
        assertTrue(v.problems.joinToString(), v.ok)
        assertEquals(2, v.changed.size)
        assertEquals(1 to 1, PatchValidator.linesChanged(v.changed[0], index))
    }

    @Test fun `unknown main files, traversal, empties and no-ops are refused`() {
        val v = PatchValidator.validate(diag(
            FilePatch("app/src/main/java/New.kt", "x"),
            FilePatch("../etc/passwd", "x"),
            FilePatch(main, ""),
            FilePatch(main, "old\ncontent"),
        ), index)
        assertFalse(v.ok)
        assertEquals(4, v.problems.size)
    }

    @Test fun `no patches is a problem`() {
        assertFalse(PatchValidator.validate(diag(), index).ok)
    }

    @Test fun `diagnosis parses model json with unknown keys`() {
        val d = Diagnosis.parse("""{"rootCause":"r","confidence":0.7,"explanation":"e","patches":[],"prTitle":"t","prBody":"b","caveats":["c"],"extra":1}""")
        assertEquals(0.7, d.confidence, 0.0)
        assertEquals(listOf("c"), d.caveats)
    }
}
