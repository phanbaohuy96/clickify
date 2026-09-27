package com.pbh.clickify.domain.run

import com.pbh.clickify.domain.scenario.GlobalActionKind
import kotlinx.coroutines.delay

/**
 * Records what the runner asked for, so a test can assert on the strokes rather than on the code
 * that produced them.
 */
class FakeGestureDispatcher(
    private val outcome: GestureOutcome = GestureOutcome.COMPLETED,
    private val globalActionSucceeds: Boolean = true,
    private val setTextSucceeds: Boolean = true,
    /** GX-6: set far beyond the gesture's duration to model a callback that never arrives. */
    private val extraLatencyMilliseconds: Long = 0L,
) : GestureDispatcher {
    val gestures = mutableListOf<Gesture>()
    val globalActions = mutableListOf<GlobalActionKind>()
    val texts = mutableListOf<String>()
    var releaseCount = 0
        private set

    override suspend fun dispatch(gesture: Gesture): GestureOutcome {
        gestures += gesture
        delay(gesture.durationMilliseconds + extraLatencyMilliseconds)
        return outcome
    }

    override suspend fun performGlobalAction(action: GlobalActionKind): Boolean {
        globalActions += action
        return globalActionSucceeds
    }

    override suspend fun setText(text: String): Boolean {
        texts += text
        return setTextSucceeds
    }

    override suspend fun releaseEverything() {
        releaseCount++
    }
}
