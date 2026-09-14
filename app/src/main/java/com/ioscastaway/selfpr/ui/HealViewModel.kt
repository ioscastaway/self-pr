package com.ioscastaway.selfpr.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ioscastaway.selfpr.App
import com.ioscastaway.selfpr.demo.BillSplitter
import com.ioscastaway.selfpr.healer.CrashReport
import com.ioscastaway.selfpr.healer.Diagnosis
import com.ioscastaway.selfpr.healer.PatchValidator
import kotlinx.coroutines.launch

class HealViewModel(app: Application) : AndroidViewModel(app) {
    private val graph get() = App.graph
    val store get() = graph.store
    val healer get() = graph.healer
    val kb get() = graph.knowledgeBase
    val secrets get() = graph.secrets
    val updater get() = graph.updater
    val installer get() = graph.installer

    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(App.arrival?.let { "This launch is the build the app installed on itself: ${it.fromSha.take(7)} → ${it.toSha.take(7)} (CI run #${it.runNumber})." })

    // ---- the host feature ----
    var amount by mutableStateOf("")
    var people by mutableStateOf("")
    var tip by mutableStateOf(10)
    val history = mutableListOf<BillSplitter.Result>()
    var lastOutput by mutableStateOf("")

    /** Runs on the main thread on purpose: a bug here is a real crash, which is the point. */
    fun split() {
        val r = try {
            BillSplitter.split(amount, people, tip)
        } catch (e: BillSplitter.InvalidInput) {
            message = e.message
            return
        }
        history += r
        lastOutput = BillSplitter.format(r)
    }

    fun showLast() {
        val last = BillSplitter.lastSplit(history)
        lastOutput = if (last == null) "No splits yet." else "Last: " + BillSplitter.format(last)
    }

    // ---- the healer ----
    fun diagnose(crash: CrashReport) {
        busy = crash.id
        viewModelScope.launch {
            val r = healer.diagnose(crash)
            message = r.error?.let { "Diagnosis failed: $it" } ?: "Diagnosed with confidence ${"%.2f".format(r.diagnosis?.confidence ?: 0.0)}."
            busy = null
        }
    }

    fun file(crash: CrashReport) {
        busy = crash.id
        viewModelScope.launch {
            val r = healer.file(crash)
            message = r.error?.let { "Could not open the pull request: $it" } ?: "Pull request opened: ${r.prUrl}"
            busy = null
        }
    }

    // ---- stage 4 ----
    fun checkForBuild() { viewModelScope.launch { updater.check() } }
    fun installBuild(d: com.ioscastaway.selfpr.updater.UpdateDecision) { viewModelScope.launch { updater.install(d) } }
    fun storeKeys(anthropic: String, github: String) { secrets.store(anthropic, github); message = "Stored. They stay on this phone and survive the app updating itself." }

    fun verdict(d: Diagnosis): PatchValidator.Verdict = healer.validate(d)
    fun linesChanged(p: com.ioscastaway.selfpr.healer.FilePatch) = PatchValidator.linesChanged(p, kb.source)
    fun clearHistory() { store.clear(); message = "Cleared." }
}
