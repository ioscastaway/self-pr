package com.ioscastaway.selfpr.updater

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** One successful run of the CI workflow: the build the app could become. */
@Serializable
data class CiBuild(
    val runId: Long,
    val runNumber: Int,
    val headSha: String,
    val headBranch: String,
    /** `push` (a merge landed on the channel) or `pull_request` (a PR build, the app's own included). */
    val event: String,
    val createdAt: String,
    val htmlUrl: String,
) {
    val shortSha: String get() = headSha.take(7)
}

/** An artifact of a run. The APK is inside a zip; that is the artifacts API's shape. */
@Serializable
data class CiArtifact(val id: Long, val name: String, val sizeBytes: Long, val expired: Boolean, val archiveDownloadUrl: String)

/** One commit between the running build and a CI build, as `compare` returns them. */
@Serializable
data class CommitSummary(val sha: String, val subject: String) {
    /** `(#12)` at the end of a squash-merge subject: the pull request this commit was. */
    val pullRequest: Int? get() = PR_SUFFIX.find(subject)?.groupValues?.get(1)?.toInt()

    private companion object { val PR_SUFFIX = Regex("""\(#(\d+)\)\s*$""") }
}

/** What `GET /compare/{running}...{build}` says about the two revisions. */
@Serializable
data class Comparison(val status: String, val aheadBy: Int, val behindBy: Int, val commits: List<CommitSummary>)

/** Parsers for the three GitHub responses stage 4 reads. Pure functions, tested on the JVM. */
object GitHubJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun runs(text: String): List<CiBuild> = json.parseToJsonElement(text).jsonObject["workflow_runs"]!!.jsonArray.map { it.jsonObject }
        .filter { it["conclusion"]?.jsonPrimitive?.content == "success" }
        .map {
            CiBuild(
                runId = it["id"]!!.jsonPrimitive.long,
                runNumber = it["run_number"]!!.jsonPrimitive.int,
                headSha = it["head_sha"]!!.jsonPrimitive.content,
                headBranch = it["head_branch"]!!.jsonPrimitive.content,
                event = it["event"]!!.jsonPrimitive.content,
                createdAt = it["created_at"]!!.jsonPrimitive.content,
                htmlUrl = it["html_url"]!!.jsonPrimitive.content,
            )
        }

    fun artifacts(text: String): List<CiArtifact> = json.parseToJsonElement(text).jsonObject["artifacts"]!!.jsonArray.map { it.jsonObject }
        .map {
            CiArtifact(
                id = it["id"]!!.jsonPrimitive.long,
                name = it["name"]!!.jsonPrimitive.content,
                sizeBytes = it["size_in_bytes"]!!.jsonPrimitive.long,
                expired = it["expired"]?.jsonPrimitive?.boolean ?: false,
                archiveDownloadUrl = it["archive_download_url"]!!.jsonPrimitive.content,
            )
        }

    fun comparison(text: String): Comparison {
        val o: JsonObject = json.parseToJsonElement(text).jsonObject
        return Comparison(
            status = o["status"]!!.jsonPrimitive.content,
            aheadBy = o["ahead_by"]!!.jsonPrimitive.int,
            behindBy = o["behind_by"]!!.jsonPrimitive.int,
            commits = o["commits"]!!.jsonArray.map { c ->
                val co = c.jsonObject
                CommitSummary(co["sha"]!!.jsonPrimitive.content, co["commit"]!!.jsonObject["message"]!!.jsonPrimitive.content.lineSequence().first())
            },
        )
    }
}
