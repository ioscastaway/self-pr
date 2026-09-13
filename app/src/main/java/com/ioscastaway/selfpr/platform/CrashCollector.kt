package com.ioscastaway.selfpr.platform

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import com.ioscastaway.selfpr.BuildConfig
import com.ioscastaway.selfpr.healer.CrashReport
import java.io.File
import java.util.UUID

/**
 * Two sources: an uncaught-exception handler that writes a file synchronously and then lets the
 * previous handler kill the process, and `ApplicationExitInfo`, which the system keeps for us and
 * also covers ANRs and native crashes the handler never sees.
 */
class CrashCollector(private val context: Context, private val store: HealStore) {
    private val dir = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val ts = System.currentTimeMillis()
                File(dir, "$ts.txt").writeText(
                    "thread=${thread.name}\n" +
                        "description=${throwable::class.java.name}: ${throwable.message}\n" +
                        "trace=\n" + throwable.stackTraceToString(),
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Call once on startup, off the main thread. */
    fun importPending() {
        importFiles()
        importExitInfo()
    }

    private fun importFiles() {
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            runCatching {
                val ts = f.nameWithoutExtension.toLongOrNull() ?: f.lastModified()
                val text = f.readText()
                val description = text.lineSequence().firstOrNull { it.startsWith("description=") }
                    ?.removePrefix("description=") ?: "uncaught exception"
                store.addCrash(CrashReport(
                    id = UUID.randomUUID().toString().replace("-", ""), timestamp = ts, source = "uncaught",
                    reason = "CRASH", description = description, trace = text.substringAfter("trace=\n", ""),
                    versionName = BuildConfig.VERSION_NAME, gitSha = BuildConfig.GIT_SHA, device = device(),
                ))
            }.onFailure { Log.w(TAG, "could not import ${f.name}", it) }
            f.delete()
        }
    }

    private fun importExitInfo() {
        val am = context.getSystemService(ActivityManager::class.java)
        val newest = store.crashes.value.filter { it.source == "exit-info" }.maxOfOrNull { it.timestamp } ?: 0L
        val known = store.crashes.value.map { it.timestamp }.toSet()
        val exits = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 20) }
            .getOrElse { Log.w(TAG, "exit reasons unavailable", it); return }
        exits.filter { it.timestamp > newest && it.reason in interesting }
            .sortedBy { it.timestamp }
            .forEach { info ->
                // A crash our handler already wrote lands here too, a few ms apart. Skip near-duplicates.
                if (known.any { kotlin.math.abs(it - info.timestamp) < 5_000 }) return@forEach
                val trace = runCatching {
                    info.traceInputStream?.bufferedReader()?.use { r -> r.readText().take(64 * 1024) }
                }.getOrNull() ?: ""
                store.addCrash(CrashReport(
                    id = UUID.randomUUID().toString().replace("-", ""), timestamp = info.timestamp, source = "exit-info",
                    reason = reasonName(info.reason), description = info.description ?: "", trace = trace,
                    versionName = BuildConfig.VERSION_NAME, gitSha = BuildConfig.GIT_SHA, device = device(),
                ))
            }
    }

    private val interesting = setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR)

    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        else -> "REASON_$reason"
    }

    companion object {
        private const val TAG = "CrashCollector"
        fun device() = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
    }
}
