package com.ioscastaway.selfpr

import android.content.Context
import com.ioscastaway.selfpr.platform.ClaudeDiagnoser
import com.ioscastaway.selfpr.platform.CrashCollector
import com.ioscastaway.selfpr.platform.GitHubPublisher
import com.ioscastaway.selfpr.platform.HealStore
import com.ioscastaway.selfpr.platform.Healer
import com.ioscastaway.selfpr.platform.KnowledgeBase
import com.ioscastaway.selfpr.platform.Secrets
import com.ioscastaway.selfpr.platform.SelfInstaller
import com.ioscastaway.selfpr.platform.Updater

/** Manual constructor injection. */
class Graph(context: Context) {
    private val app = context.applicationContext
    val secrets = Secrets(app)
    val store = HealStore(app)
    val knowledgeBase = KnowledgeBase(app)
    val crashCollector = CrashCollector(app, store)
    // Clients are made per use so keys pasted on the About tab apply without a restart.
    val healer = Healer(
        store = store,
        kb = knowledgeBase,
        diagnoser = { secrets.anthropic()?.let { ClaudeDiagnoser(it, BuildConfig.CLAUDE_MODEL) } },
        publisher = { secrets.githubToken.takeIf { it.isNotBlank() }?.let { GitHubPublisher(it, BuildConfig.SELF_REPO, BuildConfig.SELF_BASE_BRANCH) } },
    )
    val installer = SelfInstaller(app)
    val updater = Updater(app, secrets, installer)
}
