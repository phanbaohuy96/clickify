package com.pbh.clickify.domain.scenario

/** How many times a Scenario runs (SM-2). A Step always uses a plain number. */
sealed interface RunCount {
    data class Times(
        val count: Int,
    ) : RunCount

    data object UntilStopped : RunCount

    /** Null means there is no last iteration to count towards. */
    val totalIterations: Int?
        get() =
            when (this) {
                is Times -> count
                UntilStopped -> null
            }
}
