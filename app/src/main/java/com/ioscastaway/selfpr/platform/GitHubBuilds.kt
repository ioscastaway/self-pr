package com.ioscastaway.selfpr.platform

import com.ioscastaway.selfpr.updater.CiArtifact
import com.ioscastaway.selfpr.updater.CiBuild
import com.ioscastaway.selfpr.updater.Comparison
import com.ioscastaway.selfpr.updater.GitHubJson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * The read side of the app's relationship with its repository: what CI built, what is in it, and
 * the bytes. Needs a token with actions:read on that one repository (the artifact zip cannot be
 * downloaded anonymously even on a public repository). The download follows a redirect to blob
 * storage; OkHttp drops the Authorization header when the host changes, which is what that
 * storage requires.
 */
class GitHubBuilds(private val token: String, private val repo: String, private val workflow: String) {
    private val http = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()
    private val api = "https://api.github.com/repos/$repo"

    /** Newest successful run of the workflow on `branch`. Pull-request runs report the PR's head branch, so a PR channel works too. */
    fun latest(branch: String): CiBuild? =
        GitHubJson.runs(text("$api/actions/workflows/$workflow/runs?branch=$branch&status=success&per_page=5")).firstOrNull()

    fun artifacts(build: CiBuild): List<CiArtifact> = GitHubJson.artifacts(text("$api/actions/runs/${build.runId}/artifacts"))

    /** `running...head`. Fails with GitHub's message when `running` is not a commit GitHub knows. */
    fun compare(runningSha: String, headSha: String): Result<Comparison> =
        runCatching { GitHubJson.comparison(text("$api/compare/$runningSha...$headSha")) }

    /** The artifact zip. The caller closes the stream. */
    fun download(artifact: CiArtifact): InputStream {
        val res = http.newCall(request(artifact.archiveDownloadUrl)).execute()
        if (!res.isSuccessful) { val t = res.body?.string().orEmpty(); res.close(); throw error(res.code, "GET artifact", t) }
        return res.body!!.byteStream()
    }

    private fun text(url: String): String = http.newCall(request(url)).execute().use { res ->
        val t = res.body?.string().orEmpty()
        if (!res.isSuccessful) throw error(res.code, "GET ${url.removePrefix(api)}", t)
        t
    }

    private fun request(url: String) = Request.Builder().url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "self-pr (the app itself)")
        .build()

    private fun error(code: Int, what: String, body: String): IllegalStateException {
        val hint = if (code == 403 || code == 401) " (the token needs actions:read on $repo)" else ""
        return IllegalStateException("GitHub $code on $what$hint: ${body.take(200)}")
    }
}
