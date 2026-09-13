package com.ioscastaway.selfpr

import android.content.Context
import com.ioscastaway.selfpr.platform.ClaudeDiagnoser
import com.ioscastaway.selfpr.platform.CrashCollector
import com.ioscastaway.selfpr.platform.GitHubPublisher
import com.ioscastaway.selfpr.platform.HealStore
import com.ioscastaway.selfpr.platform.Healer
import com.ioscastaway.selfpr.platform.KnowledgeBase
import com.ioscastaway.selfpr.platform.Secrets

/** Manual constructor injection. */
class Graph(context: Context) {
    private val app = context.applicationContext
    val store = HealStore(app)
    val knowledgeBase = KnowledgeBase(app)
    val crashCollector = CrashCollector(app, store)
    val healer = Healer(
        store = store,
        kb = knowledgeBase,
        diagnoser = Secrets.anthropic()?.let { ClaudeDiagnoser(it, BuildConfig.CLAUDE_MODEL) },
        publisher = Secrets.githubToken()?.let { GitHubPublisher(it, BuildConfig.SELF_REPO, BuildConfig.SELF_BASE_BRANCH) },
    )
}
