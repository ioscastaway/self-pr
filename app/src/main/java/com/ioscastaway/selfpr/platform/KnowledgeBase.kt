package com.ioscastaway.selfpr.platform

import android.content.Context
import com.ioscastaway.selfpr.healer.SourceIndex

/** The app's own source tree and architecture notes, bundled at build time. Read lazily, once. */
class KnowledgeBase(private val context: Context) {
    val source: SourceIndex by lazy {
        runCatching { context.assets.open("source.zip").use { SourceIndex.fromZip(it) } }
            .getOrDefault(SourceIndex(emptyMap()))
    }
    val notes: String by lazy {
        runCatching { context.assets.open("ARCHITECTURE.md").bufferedReader().use { it.readText() } }
            .getOrDefault("(docs/ARCHITECTURE.md was not bundled)")
    }
}
