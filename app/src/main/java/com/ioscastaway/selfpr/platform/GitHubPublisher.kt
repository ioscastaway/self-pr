package com.ioscastaway.selfpr.platform

import com.ioscastaway.selfpr.healer.PullRequestPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Opens a pull request on the app's own repository through the REST API: read the base ref,
 * create a branch, put each patched file (one commit per file, the contents API's shape), open
 * the PR. Needs a token with contents:write and pull_requests:write on that one repository.
 */
class GitHubPublisher(
    private val token: String,
    private val repo: String,
    private val base: String,
) {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val api = "https://api.github.com/repos/$repo"

    data class Opened(val url: String, val branch: String)

    suspend fun open(plan: PullRequestPlan): Opened = withContext(Dispatchers.IO) {
        val baseSha = get("$api/git/ref/heads/$base")["object"]!!.jsonObject["sha"]!!.jsonPrimitive.content
        post("$api/git/refs", plan.createRefBody(baseSha))
        for (patch in plan.files) {
            val existing = runCatching { get("$api/contents/${patch.path}?ref=${plan.branch}")["sha"]?.jsonPrimitive?.content }.getOrNull()
            put("$api/contents/${patch.path}", plan.putFileBody(patch, existing))
        }
        val pr = post("$api/pulls", plan.pullRequestBody(base))
        Opened(pr["html_url"]!!.jsonPrimitive.content, plan.branch)
    }

    private fun get(url: String): JsonObject = call(Request.Builder().url(url).get())
    private fun post(url: String, body: JsonObject): JsonObject = call(Request.Builder().url(url).post(body.toString().toRequestBody(JSON)))
    private fun put(url: String, body: JsonObject): JsonObject = call(Request.Builder().url(url).put(body.toString().toRequestBody(JSON)))

    private fun call(b: Request.Builder): JsonObject {
        val req = b.header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "self-pr (the app itself)")
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IllegalStateException("GitHub ${res.code} on ${req.method} ${req.url.encodedPath}: ${text.take(300)}")
            return json.parseToJsonElement(text).jsonObject
        }
    }

    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}
