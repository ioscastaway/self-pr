package com.ioscastaway.selfpr.healer

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Everything the GitHub publisher needs, built without the network so it can be tested. The
 * publisher only sends these bodies in order: create ref, put each file, open the pull request.
 */
data class PullRequestPlan(
    val branch: String,
    val commitMessage: String,
    val files: List<FilePatch>,
    val title: String,
    val body: String,
) {
    fun createRefBody(baseSha: String): JsonObject = buildJsonObject {
        put("ref", "refs/heads/$branch")
        put("sha", baseSha)
    }

    /** `sha` is the current blob sha when the file exists on the branch; null for a new file. */
    fun putFileBody(patch: FilePatch, sha: String?): JsonObject = buildJsonObject {
        put("message", commitMessage)
        put("branch", branch)
        put("content", java.util.Base64.getEncoder().encodeToString(patch.content.toByteArray(Charsets.UTF_8)))
        if (sha != null) put("sha", sha)
    }

    fun pullRequestBody(base: String): JsonObject = buildJsonObject {
        put("title", title)
        put("head", branch)
        put("base", base)
        put("body", body)
    }

    companion object {
        fun from(crash: CrashReport, d: Diagnosis, changed: List<FilePatch>, at: Long): PullRequestPlan {
            val slug = TraceParser.headline(crash.trace).substringBefore(':').substringAfterLast('.')
                .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30).ifEmpty { "crash" }
            val branch = "heal/$slug-${crash.id.take(8)}"
            val title = d.prTitle.ifBlank { "fix: $slug" }.take(72)
            val body = buildString {
                append(d.prBody.ifBlank { d.explanation }).append("\n\n")
                append("## Filed by the app itself\n\n")
                append("- Build `").append(crash.versionName).append("` @ `").append(crash.gitSha).append("`")
                if (crash.device.isNotBlank()) append(" on ").append(crash.device)
                append('\n')
                append("- Crash: `").append(TraceParser.headline(crash.trace).take(200)).append("`\n")
                append("- Root cause: ").append(d.rootCause).append('\n')
                append("- Confidence: ").append("%.2f".format(d.confidence)).append('\n')
                if (d.caveats.isNotEmpty()) {
                    append("- Caveats:\n")
                    d.caveats.forEach { append("  - ").append(it).append('\n') }
                }
                append("\n<details><summary>Trace</summary>\n\n```\n").append(crash.trace.take(4000)).append("\n```\n</details>\n\n")
                append("Evolving App, stage 3. A human merges; CI decides whether the app was right.\n")
            }
            val message = title + "\n\n" + changed.joinToString("\n") { "- ${it.path}: ${it.summary}" }.take(1500) +
                "\n\nFiled by the app from build ${crash.gitSha}."
            return PullRequestPlan(branch, message, changed, title, body)
        }
    }
}
