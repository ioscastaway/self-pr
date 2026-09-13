package com.ioscastaway.selfpr.platform

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.ioscastaway.selfpr.BuildConfig

/**
 * Both credentials come from `local.properties` at build time. Right for a sideloaded experiment
 * on a phone its owner controls; a shipping app would put both calls behind a backend.
 */
object Secrets {
    val hasAnthropic: Boolean get() = BuildConfig.ANTHROPIC_API_KEY.isNotBlank()
    val hasGitHub: Boolean get() = BuildConfig.GITHUB_TOKEN.isNotBlank()

    fun anthropic(): AnthropicClient? =
        BuildConfig.ANTHROPIC_API_KEY.takeIf { it.isNotBlank() }?.let { AnthropicOkHttpClient.builder().apiKey(it).build() }

    fun githubToken(): String? = BuildConfig.GITHUB_TOKEN.takeIf { it.isNotBlank() }
}
