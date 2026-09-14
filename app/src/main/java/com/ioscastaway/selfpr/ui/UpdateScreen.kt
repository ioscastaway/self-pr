package com.ioscastaway.selfpr.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ioscastaway.selfpr.BuildConfig
import com.ioscastaway.selfpr.platform.SelfInstaller
import com.ioscastaway.selfpr.platform.Updater
import com.ioscastaway.selfpr.updater.UpdateDecision
import java.text.DateFormat
import java.util.Date

/** Stage 4. Check what CI built, show exactly what it contains, install it over this app. */
@Composable
fun UpdateScreen(vm: HealViewModel, modifier: Modifier) {
    val updater = vm.updater
    val phase by updater.phase.collectAsStateWithLifecycle()
    val install by vm.installer.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var channel by remember { mutableStateOf(updater.channel) }

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Running ${updater.runningSha.take(7)}, installed by ${vm.installer.installerOfRecord() ?: "adb"}", style = MaterialTheme.typography.titleMedium)
        if (!BuildConfig.SIGNED_FOR_UPDATE) Text(
            "This build is signed with the default debug key. Android will refuse to replace it with a CI build, which is signed with the shared key. Build with SELF_PR_KEYSTORE set and reinstall once.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        )
        if (!updater.canCheck) Text("No GITHUB_TOKEN: cannot ask CI what it built. Paste one on the About tab.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (!vm.installer.canInstall) {
            Text("Installing over itself needs the per-app \"Install unknown apps\" grant.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { context.startActivity(vm.installer.settingsIntent()) }) { Text("Open the grant in Settings") }
        }

        OutlinedTextField(channel, { channel = it; updater.channel = it }, label = { Text("Channel (branch)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("main installs merged work. A pull-request branch installs that PR's CI build.") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::checkForBuild, enabled = updater.canCheck && phase !is Updater.Phase.Checking) { Text("Ask CI") }
            if (phase !is Updater.Phase.Idle) TextButton(onClick = updater::reset) { Text("Clear") }
        }

        when (val p = phase) {
            Updater.Phase.Idle -> {}
            Updater.Phase.Checking -> CircularProgressIndicator()
            is Updater.Phase.Checked -> DecisionCard(vm, p.decision)
            is Updater.Phase.Downloading -> {
                Text("Downloading run artifact: ${p.bytes / 1024} KB of ${p.of / 1024} KB (zip)")
                LinearProgressIndicator(progress = { if (p.of > 0) (p.bytes.toFloat() / p.of).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
            }
            is Updater.Phase.Installing -> {
                Text("Handing run #${p.build.runNumber} (${p.build.shortSha}) to PackageInstaller.")
                when (val s = install) {
                    SelfInstaller.Status.AwaitingUser -> Text("The system is asking you. If this process is still here afterwards, the answer was no.", style = MaterialTheme.typography.bodySmall)
                    is SelfInstaller.Status.Failed -> Text("PackageInstaller refused: ${s.message}", color = MaterialTheme.colorScheme.error)
                    SelfInstaller.Status.Installed -> Text("Installed. This process should not be alive to say so.")
                    else -> CircularProgressIndicator()
                }
            }
            is Updater.Phase.Failed -> Text("Failed: ${p.message}", color = MaterialTheme.colorScheme.error)
        }

        val history = updater.history
        if (history.isNotEmpty()) {
            Text("Times this app replaced itself", style = MaterialTheme.typography.titleSmall)
            history.asReversed().forEach { r ->
                Text("${r.fromSha.take(7)} → ${r.toSha.take(7)}, run #${r.runNumber}, committed ${timeOf(r.committedAt)}, arrived ${timeOf(r.arrivedAt)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DecisionCard(vm: HealViewModel, d: UpdateDecision) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val b = d.build
            if (b == null) { Text("No successful CI run on that branch."); return@Column }
            Text("CI run #${b.runNumber} · ${b.event} on ${b.headBranch} · ${b.shortSha}", style = MaterialTheme.typography.labelMedium)
            Text(b.createdAt, style = MaterialTheme.typography.bodySmall)
            when (d) {
                is UpdateDecision.UpToDate -> Text("That is this build. Nothing to install.")
                is UpdateDecision.Available -> {
                    Text("Ahead of this build by ${d.comparison.aheadBy} commit${if (d.comparison.aheadBy == 1) "" else "s"}" +
                        (d.pullRequests.takeIf { it.isNotEmpty() }?.let { ", including PR ${it.joinToString { n -> "#$n" }}" } ?: "") + ":", style = MaterialTheme.typography.titleSmall)
                    d.comparison.commits.forEach { Text("${it.sha.take(7)} ${it.subject}", style = MaterialTheme.typography.bodySmall) }
                }
                is UpdateDecision.Sideways -> Text("Not a straight update: ${d.comparison.status}, ahead ${d.comparison.aheadBy}, behind ${d.comparison.behindBy}. Installing it is a sidestep.", color = MaterialTheme.colorScheme.error)
                is UpdateDecision.Unknown -> Text("GitHub cannot compare: ${d.reason}. This build's revision is probably not pushed. Installing is blind.", color = MaterialTheme.colorScheme.error)
                UpdateDecision.NoBuild -> {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d !is UpdateDecision.UpToDate) Button(onClick = { vm.installBuild(d) }, enabled = vm.installer.canInstall) {
                    Text(if (d is UpdateDecision.Available) "Install run #${b.runNumber} over this app" else "Install anyway")
                }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(b.htmlUrl))) }) { Text("Open run") }
            }
        }
    }
}

private fun timeOf(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))
