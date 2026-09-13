package com.ioscastaway.selfpr.platform

import android.content.Context
import com.ioscastaway.selfpr.healer.CrashReport
import com.ioscastaway.selfpr.healer.HealRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Crashes and what the healer did about them. Two JSON files; this is an experiment, not a product. */
class HealStore(context: Context) {
    private val dir = File(context.filesDir, "heal").apply { mkdirs() }
    private val crashFile = File(dir, "crashes.json")
    private val recordFile = File(dir, "records.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private val _crashes = MutableStateFlow(load<List<CrashReport>>(crashFile) ?: emptyList())
    private val _records = MutableStateFlow(load<Map<String, HealRecord>>(recordFile) ?: emptyMap())
    val crashes: StateFlow<List<CrashReport>> get() = _crashes
    val records: StateFlow<Map<String, HealRecord>> get() = _records

    @Synchronized
    fun addCrash(c: CrashReport) {
        _crashes.value = (_crashes.value + c).sortedByDescending { it.timestamp }.take(50)
        crashFile.writeText(json.encodeToString(_crashes.value))
    }

    @Synchronized
    fun put(r: HealRecord) {
        _records.value = _records.value + (r.crashId to r)
        recordFile.writeText(json.encodeToString(_records.value))
    }

    @Synchronized
    fun clear() {
        _crashes.value = emptyList(); _records.value = emptyMap()
        crashFile.delete(); recordFile.delete()
    }

    private inline fun <reified T> load(f: File): T? =
        runCatching { if (f.exists()) json.decodeFromString<T>(f.readText()) else null }.getOrNull()
}
