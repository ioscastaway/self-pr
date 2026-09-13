package com.ioscastaway.selfpr.healer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A whole-file replacement. Files are small; a full file is easier to validate than a diff. */
@Serializable
data class FilePatch(val path: String, val content: String, val summary: String = "")

/** What the model returns for one crash. Mirrors [HealPrompt.schema]. */
@Serializable
data class Diagnosis(
    val rootCause: String,
    /** 0.0 to 1.0. Below [MIN_CONFIDENCE_TO_FILE] the app shows the diagnosis but will not open a PR. */
    val confidence: Double,
    val explanation: String,
    val patches: List<FilePatch> = emptyList(),
    val prTitle: String = "",
    val prBody: String = "",
    /** What the model could not do from here: needs a repro, a design decision, a human. */
    val caveats: List<String> = emptyList(),
) {
    companion object {
        const val MIN_CONFIDENCE_TO_FILE = 0.6
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        fun parse(text: String): Diagnosis = json.decodeFromString(text)
    }
}

/** The seam to the model. */
interface Diagnoser {
    suspend fun diagnose(crash: CrashReport, files: List<Pair<String, String>>, notes: String): Diagnosis
}

/**
 * Checks a diagnosis before it can become a branch. The model may only rewrite files that are
 * bundled, may add new files only under `app/src/test/`, and must change something.
 */
object PatchValidator {
    data class Verdict(val ok: Boolean, val problems: List<String>, val changed: List<FilePatch>)

    fun validate(d: Diagnosis, index: SourceIndex): Verdict {
        val problems = ArrayList<String>()
        val changed = ArrayList<FilePatch>()
        for (p in d.patches) {
            when {
                p.path.contains("..") || p.path.startsWith("/") -> problems += "Refusing path ${p.path}"
                !index.contains(p.path) && !p.path.startsWith("app/src/test/") -> problems += "Unknown file ${p.path}; new files are only allowed under app/src/test/"
                p.content.isBlank() -> problems += "${p.path} would become empty"
                index[p.path] == p.content -> problems += "${p.path} is unchanged"
                else -> changed += p
            }
        }
        if (d.patches.isEmpty()) problems += "No patch proposed"
        if (changed.isEmpty() && problems.isEmpty()) problems += "Nothing changed"
        return Verdict(problems.isEmpty(), problems, changed)
    }

    /** A rough size of the change for the UI: added + removed lines against the bundled file. */
    fun linesChanged(p: FilePatch, index: SourceIndex): Pair<Int, Int> {
        val old = index[p.path]?.lines()?.toSet() ?: emptySet()
        val new = p.content.lines().toSet()
        return (new - old).size to (old - new).size
    }
}

object HealPrompt {
    const val SYSTEM: String = """You are the self-repair routine of a small Android app. The app crashed, captured its own stack trace, and is now handing you the trace together with its own source files and architecture notes. Your job is to find the root cause and write the fix as complete replacement files.

Rules:
- Fix the cause, not the symptom. If the crash is an unhandled input, handle it where the input enters and give the user a sensible result or message; do not wrap the crash site in a blanket try/catch.
- Change as little as possible. Keep the file's style, imports, package line and public API unless the fix needs otherwise. Return every patched file in full; partial files cannot be applied.
- You may edit only files you were given, and you may add new test files under app/src/test/java/. Prefer adding a unit test that reproduces the crash next to the fix; the project uses JUnit 4 and the tests run on the JVM with no Android dependencies.
- Kotlin only. Android APIs are available in main sources; tests must not use them.
- Be honest about confidence: 0.9 means you are sure the patch compiles and fixes this trace; 0.5 means you would want a human to look. Put anything you could not settle in caveats.
- prTitle is a conventional commit line ("fix: ..."). prBody explains the crash, the cause, and the change in a few short paragraphs, written for a reviewer who has not seen the trace.

Respond with JSON only, matching the schema you were given."""

    fun user(crash: CrashReport, files: List<Pair<String, String>>, notes: String): String = buildString {
        append("## Crash\n")
        append("reason: ").append(crash.reason).append('\n')
        append("build: ").append(crash.versionName).append(" @ ").append(crash.gitSha).append('\n')
        if (crash.device.isNotBlank()) append("device: ").append(crash.device).append('\n')
        append("description: ").append(crash.description).append("\n\n")
        append("```\n").append(crash.trace.take(6000)).append("\n```\n\n")
        append("## Architecture notes\n\n").append(notes.take(8000)).append("\n\n")
        append("## Source files (repository-relative paths)\n\n")
        for ((path, content) in files) {
            append("### ").append(path).append("\n```kotlin\n").append(content).append("\n```\n\n")
        }
    }

    val schema: Map<String, Any?> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("rootCause", "confidence", "explanation", "patches", "prTitle", "prBody", "caveats"),
        "properties" to mapOf(
            "rootCause" to mapOf("type" to "string"),
            "confidence" to mapOf("type" to "number"),
            "explanation" to mapOf("type" to "string"),
            "patches" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("path", "content", "summary"),
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string"),
                        "content" to mapOf("type" to "string"),
                        "summary" to mapOf("type" to "string"),
                    ),
                ),
            ),
            "prTitle" to mapOf("type" to "string"),
            "prBody" to mapOf("type" to "string"),
            "caveats" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
        ),
    )
}
