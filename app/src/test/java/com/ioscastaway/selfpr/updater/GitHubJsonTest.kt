package com.ioscastaway.selfpr.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubJsonTest {
    @Test fun `runs keeps successful runs in order and reads the fields the updater needs`() {
        val text = """{"total_count":2,"workflow_runs":[
            {"id":11,"run_number":7,"head_sha":"abc1234def","head_branch":"main","event":"push","conclusion":"success","created_at":"2026-09-14T01:02:03Z","html_url":"https://x/11"},
            {"id":10,"run_number":6,"head_sha":"0000000000","head_branch":"main","event":"push","conclusion":"failure","created_at":"2026-09-13T01:02:03Z","html_url":"https://x/10"}
        ]}"""
        val runs = GitHubJson.runs(text)
        assertEquals(1, runs.size)
        assertEquals(CiBuild(11, 7, "abc1234def", "main", "push", "2026-09-14T01:02:03Z", "https://x/11"), runs[0])
        assertEquals("abc1234", runs[0].shortSha)
    }

    @Test fun `artifacts reads size, expiry and the download url`() {
        val text = """{"artifacts":[{"id":5,"name":"app-debug","size_in_bytes":12345,"expired":false,"archive_download_url":"https://api/zip"}]}"""
        assertEquals(listOf(CiArtifact(5, "app-debug", 12345, false, "https://api/zip")), GitHubJson.artifacts(text))
    }

    @Test fun `comparison keeps the first line of each commit and finds pull request numbers`() {
        val text = """{"status":"ahead","ahead_by":2,"behind_by":0,"commits":[
            {"sha":"aaaa","commit":{"message":"fix: accept decimals (#1)\n\nbody"}},
            {"sha":"bbbb","commit":{"message":"docs: notes"}}
        ]}"""
        val c = GitHubJson.comparison(text)
        assertEquals("ahead", c.status)
        assertEquals(listOf(CommitSummary("aaaa", "fix: accept decimals (#1)"), CommitSummary("bbbb", "docs: notes")), c.commits)
        assertEquals(1, c.commits[0].pullRequest)
        assertNull(c.commits[1].pullRequest)
        assertTrue(c.commits.mapNotNull { it.pullRequest } == listOf(1))
    }
}
