package com.ioscastaway.selfpr.platform

import android.util.Log
import com.ioscastaway.selfpr.BuildConfig
import com.ioscastaway.selfpr.healer.CrashReport
import com.ioscastaway.selfpr.healer.Diagnoser
import com.ioscastaway.selfpr.healer.Diagnosis
import com.ioscastaway.selfpr.healer.HealRecord
import com.ioscastaway.selfpr.healer.PatchValidator
import com.ioscastaway.selfpr.healer.PullRequestPlan
import com.ioscastaway.selfpr.healer.TraceParser

/**
 * The loop: crash → own frames → bundled files → diagnosis → validation → branch + pull request.
 * Two explicit steps, each behind a tap, so the human sees the diagnosis before the app
 * publishes anything.
 */
class Healer(
    private val store: HealStore,
    private val kb: KnowledgeBase,
    private val diagnoser: () -> Diagnoser?,
    private val publisher: () -> GitHubPublisher?,
) {
    val canDiagnose: Boolean get() = diagnoser() != null
    val canFile: Boolean get() = publisher() != null

    suspend fun diagnose(crash: CrashReport): HealRecord {
        val d = diagnoser() ?: return fail(crash, "No ANTHROPIC_API_KEY in this build.")
        return runCatching {
            val frames = TraceParser.ownFrames(crash.trace, BuildConfig.APPLICATION_ID)
            val files = kb.source.filesFor(frames).toMutableList()
            // The existing test next to the culprit, so the model extends it instead of inventing one.
            files.firstOrNull()?.let { (main, _) -> kb.source.testFor(main)?.let { t -> files += t to kb.source[t]!! } }
            if (files.isEmpty()) throw IllegalStateException("No frame of this trace is in the bundled source; nothing to patch.")
            val diagnosis = d.diagnose(crash, files, kb.notes)
            val record = HealRecord(crash.id, HealRecord.Status.DIAGNOSED, diagnosis, updatedAt = System.currentTimeMillis())
            store.put(record); record
        }.getOrElse { fail(crash, it.message ?: it.toString()) }
    }

    fun validate(d: Diagnosis): PatchValidator.Verdict = PatchValidator.validate(d, kb.source)

    suspend fun file(crash: CrashReport): HealRecord {
        val p = publisher() ?: return fail(crash, "No GITHUB_TOKEN in this build.")
        val current = store.records.value[crash.id]
        val d = current?.diagnosis ?: return fail(crash, "Diagnose first.")
        val verdict = validate(d)
        if (!verdict.ok) return fail(crash, verdict.problems.joinToString("; "), d)
        if (d.confidence < Diagnosis.MIN_CONFIDENCE_TO_FILE) return fail(crash, "Confidence ${d.confidence} is below ${Diagnosis.MIN_CONFIDENCE_TO_FILE}; not filing.", d)
        return runCatching {
            val plan = PullRequestPlan.from(crash, d, verdict.changed, System.currentTimeMillis())
            val opened = p.open(plan)
            Log.i(TAG, "opened ${opened.url}")
            val record = HealRecord(crash.id, HealRecord.Status.FILED, d, opened.url, opened.branch, updatedAt = System.currentTimeMillis())
            store.put(record); record
        }.getOrElse { fail(crash, it.message ?: it.toString(), d) }
    }

    private fun fail(crash: CrashReport, error: String, d: Diagnosis? = null): HealRecord {
        val record = HealRecord(crash.id, HealRecord.Status.FAILED, d, error = error, updatedAt = System.currentTimeMillis())
        store.put(record); return record
    }

    private companion object { const val TAG = "Healer" }
}
