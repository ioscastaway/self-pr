package com.ioscastaway.selfpr.healer

/**
 * The bundled source tree: path → content. Built from `assets/source.zip` on the device and from
 * a map in tests. Paths are repository-relative (`app/src/main/java/...`), which is what the
 * GitHub contents API wants back.
 */
class SourceIndex(private val files: Map<String, String>) {
    val paths: List<String> get() = files.keys.sorted()

    operator fun get(path: String): String? = files[path]
    fun contains(path: String) = files.containsKey(path)

    /** `com.x.demo.BillSplitter` → `app/src/main/java/com/x/demo/BillSplitter.kt`, if bundled. */
    fun pathForClass(fqcn: String): String? {
        val rel = fqcn.substringBefore('$').replace('.', '/')
        return files.keys.firstOrNull { it.endsWith("/main/java/$rel.kt") }
            ?: files.keys.firstOrNull { it.endsWith("/$rel.kt") }
    }

    /** Files to hand the model: the culprit's file first, then the other own frames, deduplicated. */
    fun filesFor(frames: List<OwnFrame>, limit: Int = 6): List<Pair<String, String>> =
        frames.mapNotNull { pathForClass(it.topLevelClass) }.distinct().take(limit).map { it to files.getValue(it) }

    /** The test file that would sit next to a main file, if any. */
    fun testFor(mainPath: String): String? {
        val test = mainPath.replace("/src/main/java/", "/src/test/java/").replace(Regex("\\.kt$"), "Test.kt")
        return if (files.containsKey(test)) test else null
    }

    companion object {
        /** Reads a zip's entries into memory. Small trees only; ours is under a megabyte. */
        fun fromZip(bytes: java.io.InputStream): SourceIndex {
            val map = LinkedHashMap<String, String>()
            java.util.zip.ZipInputStream(bytes).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (!e.isDirectory) map[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                    e = zip.nextEntry
                }
            }
            return SourceIndex(map)
        }
    }
}
