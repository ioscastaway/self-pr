package com.ioscastaway.selfpr.healer

/** One frame of a Java/Kotlin stack trace that belongs to this app. */
data class OwnFrame(val className: String, val method: String, val file: String, val line: Int) {
    /** Outermost class, e.g. `com.x.BillSplitter` for `com.x.BillSplitter$Companion`. */
    val topLevelClass: String get() = className.substringBefore('$')
}

/**
 * Pulls the frames that belong to this app out of a trace, in order. Works on the format both
 * `Throwable.stackTraceToString()` and `ApplicationExitInfo.getTraceInputStream()` produce:
 * `at com.pkg.Cls.method(File.kt:42)`. Lambdas and inlined frames keep the enclosing class name.
 */
object TraceParser {
    private val frame = Regex("""at\s+([\w.$]+)\.([\w<>$]+)\(([\w.]+):(\d+)\)""")

    fun ownFrames(trace: String, packagePrefix: String): List<OwnFrame> =
        frame.findAll(trace).mapNotNull { m ->
            val (cls, method, file, line) = m.destructured
            if (!cls.startsWith(packagePrefix)) null
            else OwnFrame(cls, method, file, line.toInt())
        }.toList()

    /** The exception line(s) before the first `at`, e.g. `java.lang.NumberFormatException: For input string: "12.5"`. */
    fun headline(trace: String): String =
        trace.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("at ") } ?: ""

    /** The frame closest to the throw site that is ours: the first own frame in the innermost exception. */
    fun culprit(trace: String, packagePrefix: String): OwnFrame? {
        // "Caused by" sections come later in the text but are closer to the root cause.
        val sections = trace.split(Regex("(?m)^\\s*Caused by: "))
        return sections.asReversed().firstNotNullOfOrNull { ownFrames(it, packagePrefix).firstOrNull() }
    }
}
