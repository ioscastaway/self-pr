package com.ioscastaway.selfpr.platform

import android.content.Context
import com.ioscastaway.selfpr.BuildConfig
import com.ioscastaway.selfpr.updater.ApkExtractor
import com.ioscastaway.selfpr.updater.CiBuild
import com.ioscastaway.selfpr.updater.PendingUpdate
import com.ioscastaway.selfpr.updater.UpdateDecision
import com.ioscastaway.selfpr.updater.UpdateLog
import com.ioscastaway.selfpr.updater.UpdateRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stage 4: check what CI built on the channel, download it, hand it to `SelfInstaller`. Every
 * step is behind a tap and every answer from GitHub is shown before the install button exists.
 * The channel is a branch name, `main` by default; pointing it at a pull-request branch installs
 * that PR's build, which is how the loop is tested before the PR is merged.
 */
class Updater(
    context: Context,
    private val secrets: Secrets,
    private val installer: SelfInstaller,
) {
    sealed class Phase {
        data object Idle : Phase()
        data object Checking : Phase()
        data class Checked(val decision: UpdateDecision) : Phase()
        data class Downloading(val bytes: Long, val of: Long) : Phase()
        data class Installing(val build: CiBuild) : Phase()
        data class Failed(val message: String, val decision: UpdateDecision?) : Phase()
    }

    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = File(context.cacheDir, "update").apply { mkdirs() }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> get() = _phase

    var channel: String
        get() = prefs.getString(CHANNEL, null) ?: BuildConfig.SELF_BASE_BRANCH
        set(v) = prefs.edit().putString(CHANNEL, v.trim().ifEmpty { BuildConfig.SELF_BASE_BRANCH }).apply()

    val runningSha: String get() = BuildConfig.GIT_SHA
    val canCheck: Boolean get() = secrets.hasGitHub

    /** Every time this app replaced itself, oldest first. */
    val history: List<UpdateRecord> get() = prefs.getString(HISTORY, null)?.let { runCatching { json.decodeFromString<List<UpdateRecord>>(it) }.getOrNull() }.orEmpty()

    /** The record of the update that produced this very launch, if this launch is one. Call once at startup. */
    fun arrive(now: Long = System.currentTimeMillis()): UpdateRecord? {
        val pending = prefs.getString(PENDING, null)?.let { runCatching { json.decodeFromString<PendingUpdate>(it) }.getOrNull() } ?: return null
        prefs.edit().remove(PENDING).apply()
        val record = UpdateLog.arrival(pending, runningSha, now) ?: return null
        prefs.edit().putString(HISTORY, json.encodeToString(history + record)).apply()
        return record
    }

    private fun builds(): GitHubBuilds = GitHubBuilds(secrets.githubToken, BuildConfig.SELF_REPO, BuildConfig.CI_WORKFLOW)

    suspend fun check() = withContext(Dispatchers.IO) {
        _phase.value = Phase.Checking
        _phase.value = runCatching {
            val gh = builds()
            val build = gh.latest(channel)
            val compare = build?.takeUnless { UpdateDecision.matches(runningSha, it.headSha) }?.let { gh.compare(runningSha, it.headSha) }
            Phase.Checked(UpdateDecision.decide(runningSha, build, compare))
        }.getOrElse { Phase.Failed(it.message ?: it.toString(), null) }
    }

    suspend fun install(decision: UpdateDecision) = withContext(Dispatchers.IO) {
        val build = decision.build ?: return@withContext
        runCatching {
            val gh = builds()
            val artifact = gh.artifacts(build).firstOrNull { it.name == BuildConfig.CI_ARTIFACT && !it.expired }
                ?: throw IllegalStateException("Run #${build.runNumber} has no ${BuildConfig.CI_ARTIFACT} artifact (expired, or the build did not upload one)")
            val apk = File(cache, "run-${build.runNumber}.apk")
            _phase.value = Phase.Downloading(0, artifact.sizeBytes)
            gh.download(artifact).use { zip ->
                apk.outputStream().use { out -> ApkExtractor.extract(zip, out) { _phase.value = Phase.Downloading(it, artifact.sizeBytes) } }
            }
            _phase.value = Phase.Installing(build)
            // Written before the commit: the process does not survive a successful install.
            prefs.edit().putString(PENDING, json.encodeToString(PendingUpdate(runningSha, build.headSha, build.runNumber, System.currentTimeMillis()))).apply()
            installer.install(apk)
        }.onFailure { _phase.value = Phase.Failed(it.message ?: it.toString(), decision) }
    }

    fun reset() { _phase.value = Phase.Idle }

    private companion object { const val CHANNEL = "channel"; const val PENDING = "pending"; const val HISTORY = "history" }
}
