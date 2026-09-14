package com.ioscastaway.selfpr.updater

/**
 * Whether the build CI made is one the running app should become. The interesting cases are the
 * honest ones: "CI's build is exactly what I am", "CI's build contains commits I do not have", and
 * "I cannot tell, because the revision I was built from is not on GitHub" (a local build of
 * uncommitted work). Only the second is an update; the third is offered with a warning.
 */
sealed class UpdateDecision {
    abstract val build: CiBuild?

    /** No successful run of the workflow on that branch. */
    data object NoBuild : UpdateDecision() { override val build: CiBuild? = null }

    /** CI's newest build is the revision that is running. */
    data class UpToDate(override val build: CiBuild) : UpdateDecision()

    /** CI's build is ahead of the running revision by `comparison.aheadBy` commits. */
    data class Available(override val build: CiBuild, val comparison: Comparison) : UpdateDecision() {
        val pullRequests: List<Int> get() = comparison.commits.mapNotNull { it.pullRequest }.distinct()
    }

    /** The two revisions have diverged, or CI's build is older. Installable, but it is a downgrade or a sidestep. */
    data class Sideways(override val build: CiBuild, val comparison: Comparison) : UpdateDecision()

    /** GitHub could not compare (the running revision is not on GitHub). Installable, blind. */
    data class Unknown(override val build: CiBuild, val reason: String) : UpdateDecision()

    companion object {
        fun matches(runningSha: String, headSha: String): Boolean =
            runningSha.isNotBlank() && runningSha != "unknown" && (headSha.startsWith(runningSha) || runningSha.startsWith(headSha))

        /** `compare` is what GitHub answered for `runningSha...build.headSha`, or the error message if it could not. */
        fun decide(runningSha: String, build: CiBuild?, compare: Result<Comparison>?): UpdateDecision {
            build ?: return NoBuild
            if (matches(runningSha, build.headSha)) return UpToDate(build)
            val c = compare?.getOrNull() ?: return Unknown(build, compare?.exceptionOrNull()?.message ?: "no comparison")
            return when (c.status) {
                "ahead" -> Available(build, c)
                "identical" -> UpToDate(build)
                else -> Sideways(build, c)
            }
        }
    }
}
