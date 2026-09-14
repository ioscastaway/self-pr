package com.ioscastaway.selfpr

import android.app.Application
import com.ioscastaway.selfpr.updater.UpdateRecord
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
        // Stage 4: was this launch the result of the app replacing itself? Decided once, before anything else runs.
        arrival = graph.updater.arrive()
        scope.launch { graph.crashCollector.importPending() }
    }

    companion object {
        lateinit var graph: Graph
            private set
        var arrival: UpdateRecord? = null
            private set
    }
}
