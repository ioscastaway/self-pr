package com.ioscastaway.selfpr.platform

import android.content.Context
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.ioscastaway.selfpr.BuildConfig

/**
 * Both credentials come from `local.properties` at build time and are copied into app-private
 * storage at first launch. That copy is what survives stage 4: a build CI made carries no keys,
 * but app data survives an update, so the app it replaces hands them over. Keys can also be
 * pasted on the About tab, for a CI build installed onto a phone that never had a local build.
 * Right for a sideloaded experiment on a phone its owner controls; a shipping app would put both
 * calls behind a backend.
 */
class Secrets(context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    init {
        // A local build's keys win over whatever is stored; a CI build's blanks change nothing.
        if (BuildConfig.ANTHROPIC_API_KEY.isNotBlank()) prefs.edit().putString(ANTHROPIC, BuildConfig.ANTHROPIC_API_KEY).apply()
        if (BuildConfig.GITHUB_TOKEN.isNotBlank()) prefs.edit().putString(GITHUB, BuildConfig.GITHUB_TOKEN).apply()
    }

    val anthropicKey: String get() = prefs.getString(ANTHROPIC, null).orEmpty()
    val githubToken: String get() = prefs.getString(GITHUB, null).orEmpty()
    val hasAnthropic: Boolean get() = anthropicKey.isNotBlank()
    val hasGitHub: Boolean get() = githubToken.isNotBlank()

    /** Where the keys in use came from: this build, or the storage an earlier build left behind. */
    val source: String get() = if (BuildConfig.ANTHROPIC_API_KEY.isNotBlank() || BuildConfig.GITHUB_TOKEN.isNotBlank()) "this build" else "stored by an earlier build"

    fun store(anthropic: String?, github: String?) {
        prefs.edit().apply {
            anthropic?.trim()?.takeIf { it.isNotEmpty() }?.let { putString(ANTHROPIC, it) }
            github?.trim()?.takeIf { it.isNotEmpty() }?.let { putString(GITHUB, it) }
        }.apply()
    }

    fun anthropic(): AnthropicClient? = anthropicKey.takeIf { it.isNotBlank() }?.let { AnthropicOkHttpClient.builder().apiKey(it).build() }

    private companion object { const val ANTHROPIC = "anthropic"; const val GITHUB = "github" }
}
