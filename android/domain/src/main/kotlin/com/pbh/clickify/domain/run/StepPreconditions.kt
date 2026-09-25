package com.pbh.clickify.domain.run

import com.pbh.clickify.domain.recognition.SearchResult
import com.pbh.clickify.domain.recognition.TemplateFinder
import com.pbh.clickify.domain.recognition.satisfies
import com.pbh.clickify.domain.scenario.OnTimeout
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.Step

/**
 * Everything that has to be true, or found, before a Step may run (`TP-24`, `TP-26`, `TP-20`).
 *
 * Its own class rather than two more methods on the runner, because it is the one part of a run
 * that looks at the screen. The runner's job is order, repetition and stopping; this one's is
 * conditions, and they fail for different reasons.
 *
 * [finder] is null when recognition is not available — no accessibility service is connected, so
 * no frame can be taken. Every search then reports *not found*, which is the `onTimeout` the user
 * chose. That is deliberate: a Step told to wait for a button must not run as though the button
 * were there.
 */
internal class StepPreconditions(
    private val finder: TemplateFinder?,
) {
    /** TP-26: the Guard first, then the Target search, and both before the Action. */
    suspend fun resolve(
        step: Step,
        index: Int,
    ): Resolution = guardFailure(step, index) ?: located(step, index)

    private suspend fun guardFailure(
        step: Step,
        index: Int,
    ): Resolution? {
        val guard = step.guard ?: return null
        val met = finder?.await(guard.search, guard.expects)?.satisfies(guard.expects) == true
        return if (met) null else guard.search.onTimeout.resolutionFor(FinishReason.GuardUnmet(index))
    }

    private suspend fun located(
        step: Step,
        index: Int,
    ): Resolution {
        val search = step.effectiveSearch ?: return Resolution.Run(step)
        val found =
            finder?.await(search, Presence.PRESENT) as? SearchResult.Found
                ?: return search.onTimeout.resolutionFor(FinishReason.TemplateNotFound(index))
        // TP-20: the match moves the whole Step, so the gesture keeps the shape it was drawn with.
        return Resolution.Run(step.movedTo(found.at))
    }
}

/** What the runner should do with this Step, once the screen has been consulted. */
internal sealed interface Resolution {
    /** Go ahead — with this Step, which may have been moved onto a match. */
    data class Run(
        val step: Step,
    ) : Resolution

    /** TP-21: this Step does not happen, and the next one does. */
    data object Skip : Resolution

    /** TP-21: the run is over, and the user is told which Step and why. */
    data class End(
        val reason: FinishReason,
    ) : Resolution
}

private fun OnTimeout.resolutionFor(reason: FinishReason): Resolution =
    when (this) {
        OnTimeout.STOP_SCENARIO -> Resolution.End(reason)
        OnTimeout.SKIP_STEP -> Resolution.Skip
    }
