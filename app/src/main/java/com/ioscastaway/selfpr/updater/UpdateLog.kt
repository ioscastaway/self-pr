package com.ioscastaway.selfpr.updater

import kotlinx.serialization.Serializable

/**
 * What the app remembers across the one thing it cannot survive: its own reinstall. Written before
 * the install session is committed, read at the next launch, promoted to a record when the launch
 * is the build it asked for.
 */
@Serializable
data class PendingUpdate(val fromSha: String, val toSha: String, val runNumber: Int, val committedAt: Long)

@Serializable
data class UpdateRecord(val fromSha: String, val toSha: String, val runNumber: Int, val committedAt: Long, val arrivedAt: Long)

object UpdateLog {
    /** The launch that follows a successful self-install is the arrival; any other launch means the install did not happen. */
    fun arrival(pending: PendingUpdate?, runningSha: String, now: Long): UpdateRecord? {
        pending ?: return null
        if (!UpdateDecision.matches(runningSha, pending.toSha)) return null
        return UpdateRecord(pending.fromSha, pending.toSha, pending.runNumber, pending.committedAt, now)
    }
}
