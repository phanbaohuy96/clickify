package com.pbh.clickify.domain.run

import com.pbh.clickify.domain.recognition.TemplateFinder
import com.pbh.clickify.domain.scenario.GestureLimits
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.differencesFrom
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Walks a Scenario's Steps in order and nothing else (GX-1).
 *
 * The rule this class exists to keep is `GX-9`: **every exit path terminates every stroke.** A
 * press that is never released leaves a finger on the screen that is not there, the device stops
 * answering the real finger, and the user reboots it — the single most common complaint about
 * every app in this category. That is why the whole run sits inside one `try/finally` and why
 * `releaseEverything()` is not allowed to throw.
 *
 * No Android here on purpose: this is the part that must not be wrong, so it is the part tested on
 * the JVM with virtual time.
 */
class ScenarioRunner(
    private val dispatcher: GestureDispatcher,
    private val limits: GestureLimits = GestureLimits(),
    /** TP-24, TP-26: how a Step looks at the screen. Null when recognition is unavailable. */
    finder: TemplateFinder? = null,
) {
    private val stopRequested = AtomicBoolean(false)
    private val preconditions = StepPreconditions(finder)

    /** GX-8: honoured between strokes and during one. The stroke in flight still finishes. */
    fun requestStop() {
        stopRequested.set(true)
    }

    suspend fun run(
        scenario: Scenario,
        currentProfile: ScreenProfile?,
        onEvent: (RunEvent) -> Unit = {},
    ): FinishReason {
        stopRequested.set(false)

        val reason =
            try {
                // GX-2: before the countdown, so the user is not made to wait to be told no.
                screenProfileMismatch(scenario, currentProfile)
                    ?: countDown(scenario.countdownMilliseconds, onEvent)
                    ?: walk(scenario, onEvent)
            } finally {
                // GX-9. Runs for every reason a run can end, including a cancelled coroutine, a
                // screen that does not match, and the ones already going wrong.
                dispatcher.releaseEverything()
            }

        onEvent(RunEvent.Finished(reason))
        return reason
    }

    private fun screenProfileMismatch(
        scenario: Scenario,
        currentProfile: ScreenProfile?,
    ): FinishReason? {
        val expected = scenario.screenProfile ?: return null
        val actual = currentProfile ?: return null
        val differences = expected.differencesFrom(actual)
        return if (differences.isEmpty()) null else FinishReason.ScreenProfileMismatch(differences)
    }

    /** GX-3. Returns a reason only when the user stopped during it. */
    private suspend fun countDown(
        milliseconds: Int,
        onEvent: (RunEvent) -> Unit,
    ): FinishReason? {
        var remaining = milliseconds
        while (remaining > 0) {
            if (stopRequested.get()) return FinishReason.Stopped
            onEvent(RunEvent.CountingDown(remaining))
            val slice = minOf(remaining, COUNTDOWN_TICK_MILLISECONDS)
            delay(slice.toLong())
            remaining -= slice
        }
        return null
    }

    private suspend fun walk(
        scenario: Scenario,
        onEvent: (RunEvent) -> Unit,
    ): FinishReason {
        val iterations = scenario.runCount.totalIterations
        var iteration = 0
        var outcome: FinishReason? = null

        while (outcome == null && (iterations == null || iteration < iterations)) {
            for ((index, step) in scenario.steps.withIndex()) {
                outcome = runStep(step, index, iteration, onEvent)
                if (outcome != null) break
            }
            iteration++
            if (outcome == null && stopRequested.get()) outcome = FinishReason.Stopped
        }

        return outcome ?: FinishReason.Completed
    }

    /** Null means carry on. Anything else is why the run is over. */
    private suspend fun runStep(
        step: Step,
        index: Int,
        iteration: Int,
        onEvent: (RunEvent) -> Unit,
    ): FinishReason? {
        if (stopRequested.get()) {
            onEvent(RunEvent.Stopping)
            return FinishReason.Stopped
        }

        onEvent(RunEvent.StepStarted(stepIndex = index, iteration = iteration))
        var outcome = perform(step, index, onEvent)

        if (outcome == null && stopRequested.get()) {
            onEvent(RunEvent.Stopping)
            outcome = FinishReason.Stopped
        }
        if (outcome == null) {
            delay(step.delayMillisecondsAfter.toLong().coerceAtLeast(MINIMUM_GAP_MILLISECONDS))
        }
        return outcome
    }

    /**
     * GX-4: repeats the Action in place, and the delay is observed once, by the caller.
     *
     * TP-26 puts the Guard and the Target search **inside** this loop, once per repetition. A Step
     * that presses a button ten times is ten chances for the button to move or the advert to come
     * back, and resolving once outside would spend nine of them on a stale answer.
     *
     * A skipped Step still observes its delay. The delay belongs to the sequence's rhythm rather
     * than to the Action, and the next Step is entitled to the same gap either way.
     */
    private suspend fun perform(
        step: Step,
        index: Int,
        onEvent: (RunEvent) -> Unit,
    ): FinishReason? {
        var outcome: FinishReason? = null
        var repetition = 0
        var skipped = false

        while (outcome == null && !skipped && repetition < step.repeatCount) {
            if (repetition > 0) {
                // GX-5: a yield even when everything is set to zero, or the run is a tight loop
                // that starves the thread it would have to be stopped from.
                delay(MINIMUM_GAP_MILLISECONDS)
            }
            when (val resolution = preconditions.resolve(step, index)) {
                is Resolution.End -> outcome = resolution.reason
                is Resolution.Skip -> {
                    onEvent(RunEvent.StepSkipped(stepIndex = index))
                    skipped = true
                }

                is Resolution.Run -> outcome = performOnce(resolution.step, index)
            }
            if (stopRequested.get()) break
            repetition++
        }
        return outcome
    }

    private suspend fun performOnce(
        step: Step,
        index: Int,
    ): FinishReason? =
        when (val action = step.action) {
            is StepAction.Global ->
                if (dispatcher.performGlobalAction(action.action)) {
                    null
                } else {
                    FinishReason.GlobalActionRefused(index)
                }

            is StepAction.SetText ->
                if (dispatcher.setText(action.text)) null else FinishReason.NoFocusedField(index)

            else -> dispatchGestureFor(step, index)
        }

    private suspend fun dispatchGestureFor(
        step: Step,
        index: Int,
    ): FinishReason? {
        val gesture = step.toGesture(limits) ?: return null
        // GX-6: a gesture that never reports back is treated as cancelled.
        val outcome =
            withTimeoutOrNull(gesture.durationMilliseconds + CALLBACK_GRACE_MILLISECONDS) {
                dispatcher.dispatch(gesture)
            }
        return if (outcome == GestureOutcome.COMPLETED) null else FinishReason.GestureCancelled(index)
    }

    private companion object {
        /** GX-5, mirroring macOS SF-8. */
        const val MINIMUM_GAP_MILLISECONDS = 10L

        /** GX-6: how long past a gesture's own duration the callback is still believed. */
        const val CALLBACK_GRACE_MILLISECONDS = 1_000L

        const val COUNTDOWN_TICK_MILLISECONDS = 100
    }
}

/**
 * The strokes this Step dispatches (GX-13 to GX-15), or null when it dispatches none.
 *
 * A zero-length hold becomes one millisecond: the platform rejects a stroke of no duration, and a
 * rejected gesture is a Step that silently did nothing.
 */
internal fun Step.toGesture(limits: GestureLimits = GestureLimits()): Gesture? {
    fun clamp(milliseconds: Long) = milliseconds.coerceIn(SHORTEST_STROKE_MILLISECONDS, limits.maxGestureDurationMilliseconds)

    return when (val current = action) {
        is StepAction.Tap ->
            Gesture(listOf(Stroke(target.point, target.point, clamp(current.holdMilliseconds))))

        is StepAction.Swipe ->
            Gesture(
                listOf(
                    Stroke(target.point, current.destination, clamp(current.durationMilliseconds)),
                ),
            )

        is StepAction.MultiTouch ->
            Gesture(
                current.paths
                    .take(limits.maxStrokeCount)
                    .map { Stroke(it.start, it.end, clamp(it.durationMilliseconds)) },
            )

        is StepAction.Global, is StepAction.SetText -> null
    }
}

/** GX-13. */
private const val SHORTEST_STROKE_MILLISECONDS = 1L

/** Kept so a caller can build the one-millisecond tap `GX-11` frees the touch with. */
fun freeTheTouchGesture(at: ScreenPoint): Gesture = Gesture(listOf(Stroke(at, at, SHORTEST_STROKE_MILLISECONDS)))
