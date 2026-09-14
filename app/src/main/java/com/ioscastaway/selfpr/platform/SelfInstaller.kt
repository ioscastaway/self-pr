package com.ioscastaway.selfpr.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The one call iOS does not have: an app handing the system a package that replaces itself.
 * `PackageInstaller` streams the APK into a session and commits it; the system then asks the
 * user once (the "Install unknown apps" grant, per app, and the install dialog, per install),
 * verifies that the new APK is signed with the same key as the running one, and kills this
 * process to swap it. Nothing here runs after a successful commit; the next launch is the new
 * build, and `Updater` notices from what was written before the commit.
 */
class SelfInstaller(private val context: Context) {
    sealed class Status {
        data object Idle : Status()
        data object Committed : Status()
        data object AwaitingUser : Status()
        data class Failed(val message: String) : Status()
        data object Installed : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> get() = _status

    /** `REQUEST_INSTALL_PACKAGES` is granted per app in Settings, not in a runtime prompt. */
    val canInstall: Boolean get() = context.packageManager.canRequestPackageInstalls()

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The installer of record for this package: adb, Play, or, after stage 4 has run once, the app itself. */
    fun installerOfRecord(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        else @Suppress("DEPRECATION") context.packageManager.getInstallerPackageName(context.packageName)
    }.getOrNull()

    fun install(apk: File) {
        _status.value = Status.Idle
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("self-pr.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_RESULT)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
        _status.value = Status.Committed
    }

    internal fun onResult(intent: Intent) {
        when (val s = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                _status.value = Status.AwaitingUser
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> _status.value = Status.Installed
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $s"
                Log.w(TAG, "install failed: $msg")
                _status.value = Status.Failed(msg)
            }
        }
    }

    companion object {
        const val ACTION_RESULT = "com.ioscastaway.selfpr.INSTALL_RESULT"
        private const val TAG = "SelfInstaller"
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SelfInstaller.ACTION_RESULT) com.ioscastaway.selfpr.App.graph.installer.onResult(intent)
    }
}
