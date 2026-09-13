package com.ioscastaway.selfpr.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ioscastaway.selfpr.BuildConfig
import com.ioscastaway.selfpr.healer.CrashReport
import com.ioscastaway.selfpr.healer.Diagnosis
import com.ioscastaway.selfpr.healer.HealRecord
import com.ioscastaway.selfpr.healer.TraceParser
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfPrApp(vm: HealViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(listOf("Bill splitter", "Heal", "About")[tab]) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Calculate, null) }, label = { Text("Split") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Build, null) }, label = { Text("Heal") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Info, null) }, label = { Text("About") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = Modifier.padding(padding).fillMaxSize()
        when (tab) {
            0 -> SplitScreen(vm, m)
            1 -> HealScreen(vm, m)
            else -> AboutScreen(vm, m)
        }
    }
}

@Composable
private fun SplitScreen(vm: HealViewModel, modifier: Modifier) {
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("The host feature. Written like a first draft, on purpose: it works for the inputs the author tried.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(vm.amount, { vm.amount = it }, label = { Text("Bill amount") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.people, { vm.people = it }, label = { Text("People") }, modifier = Modifier.fillMaxWidth())
        Text("Tip: ${vm.tip}%")
        Slider(vm.tip.toFloat(), { vm.tip = it.toInt() }, valueRange = 0f..30f, steps = 5)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::split) { Text("Split") }
            OutlinedButton(onClick = vm::showLast) { Text("Show last") }
        }
        if (vm.lastOutput.isNotBlank()) Text(vm.lastOutput, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HealScreen(vm: HealViewModel, modifier: Modifier) {
    val crashes by vm.store.crashes.collectAsStateWithLifecycle()
    val records by vm.store.records.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!vm.healer.canDiagnose) Text("No ANTHROPIC_API_KEY in this build: crashes are collected but cannot be diagnosed.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (!vm.healer.canFile) Text("No GITHUB_TOKEN in this build: diagnoses cannot become pull requests.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (crashes.isEmpty()) Text("No crashes yet. Go to Split and type something the first draft did not expect: a decimal, an empty field, zero people, or Show last before any split.")
        crashes.forEach { c ->
            val r = records[c.id]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${c.reason} · ${timeOf(c.timestamp)} · ${c.source}", style = MaterialTheme.typography.labelMedium)
                    Text(TraceParser.headline(c.trace).ifBlank { c.description }, style = MaterialTheme.typography.titleSmall)
                    TraceParser.culprit(c.trace, BuildConfig.APPLICATION_ID)?.let { Text("at ${it.className.substringAfterLast('.')}.${it.method} (${it.file}:${it.line})", style = MaterialTheme.typography.bodySmall) }
                    Text("build ${c.versionName} @ ${c.gitSha}", style = MaterialTheme.typography.bodySmall)
                    when (r?.status) {
                        null, HealRecord.Status.NEW -> {}
                        HealRecord.Status.FAILED -> Text("Failed: ${r.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        else -> {}
                    }
                    r?.diagnosis?.let { DiagnosisView(vm, it) }
                    r?.prUrl?.let { url ->
                        Text("Pull request: $url", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }) { Text("Open in browser") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.busy == c.id) CircularProgressIndicator(Modifier.padding(8.dp))
                        else {
                            Button(onClick = { vm.diagnose(c) }, enabled = vm.healer.canDiagnose) { Text(if (r?.diagnosis == null) "Diagnose" else "Diagnose again") }
                            val d = r?.diagnosis
                            if (d != null && r.prUrl == null) {
                                val ok = vm.verdict(d).ok && d.confidence >= Diagnosis.MIN_CONFIDENCE_TO_FILE
                                Button(onClick = { vm.file(c) }, enabled = vm.healer.canFile && ok) { Text("Open pull request") }
                            }
                        }
                    }
                }
            }
        }
        if (crashes.isNotEmpty()) TextButton(onClick = vm::clearHistory) { Text("Clear crash history") }
    }
}

@Composable
private fun DiagnosisView(vm: HealViewModel, d: Diagnosis) {
    var showFiles by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Root cause (confidence ${"%.2f".format(d.confidence)})", style = MaterialTheme.typography.titleSmall)
        Text(d.rootCause)
        Text(d.explanation, style = MaterialTheme.typography.bodySmall)
        if (d.prTitle.isNotBlank()) Text("PR: ${d.prTitle}", style = MaterialTheme.typography.bodyMedium)
        val verdict = vm.verdict(d)
        d.patches.forEach { p ->
            val (added, removed) = vm.linesChanged(p)
            Text("${p.path.substringAfterLast('/')}  +$added −$removed  ${p.summary}", style = MaterialTheme.typography.bodySmall)
        }
        verdict.problems.forEach { Text("Refused: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        d.caveats.forEach { Text("Caveat: $it", style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { showFiles = !showFiles }) { Text(if (showFiles) "Hide patched files" else "Show patched files") }
        if (showFiles) d.patches.forEach { p ->
            Text(p.path, style = MaterialTheme.typography.labelMedium)
            Text(p.content, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

@Composable
private fun AboutScreen(vm: HealViewModel, modifier: Modifier) {
    var showNotes by remember { mutableStateOf(false) }
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Build ${BuildConfig.VERSION_NAME} @ ${BuildConfig.GIT_SHA}", style = MaterialTheme.typography.titleMedium)
        Text("Repository: ${BuildConfig.SELF_REPO}, base ${BuildConfig.SELF_BASE_BRANCH}")
        Text("Bundled knowledge base: ${vm.kb.source.paths.size} files", style = MaterialTheme.typography.titleSmall)
        vm.kb.source.paths.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showNotes = !showNotes }) { Text(if (showNotes) "Hide ARCHITECTURE.md" else "Show ARCHITECTURE.md") }
        if (showNotes) Text(vm.kb.notes, style = MaterialTheme.typography.bodySmall)
    }
}

private fun timeOf(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))
