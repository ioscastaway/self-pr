package com.ioscastaway.selfpr.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDecisionTest {
    private val build = CiBuild(1, 9, "abcdef0123456789", "main", "push", "2026-09-14T00:00:00Z", "https://x")
    private fun cmp(status: String, ahead: Int = 0, behind: Int = 0, vararg subjects: String) =
        Comparison(status, ahead, behind, subjects.mapIndexed { i, s -> CommitSummary("c$i", s) })

    @Test fun `short and full shas match each other, unknown never does`() {
        assertTrue(UpdateDecision.matches("abcdef0", "abcdef0123456789"))
        assertTrue(UpdateDecision.matches("abcdef0123456789", "abcdef0"))
        assertFalse(UpdateDecision.matches("unknown", "unknown123"))
        assertFalse(UpdateDecision.matches("", "abc"))
        assertFalse(UpdateDecision.matches("abcdef1", "abcdef0123456789"))
    }

    @Test fun `no run means no build`() {
        assertEquals(UpdateDecision.NoBuild, UpdateDecision.decide("abcdef0", null, null))
    }

    @Test fun `the running revision is up to date without asking compare`() {
        assertEquals(UpdateDecision.UpToDate(build), UpdateDecision.decide("abcdef0", build, null))
    }

    @Test fun `ahead is an update and names the pull requests it contains`() {
        val d = UpdateDecision.decide("1111111", build, Result.success(cmp("ahead", 2, 0, "fix: x (#3)", "fix: y (#3)", "docs: z")))
        assertTrue(d is UpdateDecision.Available)
        assertEquals(listOf(3), (d as UpdateDecision.Available).pullRequests)
    }

    @Test fun `behind and diverged are sideways, identical is up to date`() {
        assertTrue(UpdateDecision.decide("1111111", build, Result.success(cmp("behind", 0, 1))) is UpdateDecision.Sideways)
        assertTrue(UpdateDecision.decide("1111111", build, Result.success(cmp("diverged", 1, 1))) is UpdateDecision.Sideways)
        assertEquals(UpdateDecision.UpToDate(build), UpdateDecision.decide("1111111", build, Result.success(cmp("identical"))))
    }

    @Test fun `a failed compare is unknown and keeps GitHub's reason`() {
        val d = UpdateDecision.decide("1111111", build, Result.failure(IllegalStateException("GitHub 404 on GET /compare")))
        assertEquals(UpdateDecision.Unknown(build, "GitHub 404 on GET /compare"), d)
    }
}
