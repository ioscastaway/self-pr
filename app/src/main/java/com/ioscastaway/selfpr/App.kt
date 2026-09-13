package com.ioscastaway.selfpr

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        graph.crashCollector.install()
        scope.launch { graph.crashCollector.importPending() }
    }

    companion object {
        lateinit var graph: Graph
            private set
    }
}
