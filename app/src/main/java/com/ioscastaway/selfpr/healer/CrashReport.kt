package com.ioscastaway.selfpr.healer

import kotlinx.serialization.Serializable

/** One crash or ANR of this app, as the healer sees it. */
@Serializable
data class CrashReport(
    val id: String,
    val timestamp: Long,
    /** "uncaught" (our handler) or "exit-info" (ApplicationExitInfo after the fact). */
    val source: String,
    /** "CRASH", "ANR", "CRASH_NATIVE". */
    val reason: String,
    val description: String,
    val trace: String,
    val versionName: String,
    val gitSha: String,
    val device: String = "",
)

/** What happened to a crash after the healer looked at it. */
@Serializable
data class HealRecord(
    val crashId: String,
    val status: Status,
    val diagnosis: Diagnosis? = null,
    val prUrl: String? = null,
    val branch: String? = null,
    val error: String? = null,
    val updatedAt: Long,
) {
    @Serializable
    enum class Status { NEW, DIAGNOSED, FILED, FAILED }
}
