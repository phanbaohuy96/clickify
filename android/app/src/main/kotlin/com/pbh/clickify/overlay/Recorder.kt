package com.pbh.clickify.overlay

import com.pbh.clickify.domain.recording.RecordedTouch
import com.pbh.clickify.domain.run.Gesture
import com.pbh.clickify.domain.run.GestureDispatcher
import com.pbh.clickify.domain.run.Stroke
import com.pbh.clickify.overlay.ui.RecordingEvent

/**
 * Turns what the recording layer saw into [RecordedTouch]es, and hands each one back to the
 * application underneath (`RD-2`, `RD-5`).
 *
 * Holds the one piece of state recording has — the finger currently down — and nothing else. What
 * the touches *mean* is `domain/recording`, which is where it can be tested.
 */
class Recorder(
    private val dispatcher: GestureDispatcher,
    /** Told when a touch is complete, so the Overlay can count it and the caller can re-emit it. */
    private val onRecorded: (RecordedTouch) -> Unit,
) {
    private val assembler = TouchAssembler()

    val touches = mutableListOf<RecordedTouch>()

    /** Returns the Gesture to re-emit, or null when nothing has finished. */
    fun accept(event: RecordingEvent): Gesture? {
        val touch = assembler.accept(event) ?: return null
        touches += touch
        onRecorded(touch)

        // RD-5: the same movement, at the same speed, handed to whatever is underneath. A stroke
        // of no duration is refused by the platform (GX-13), so the shortest one is a millisecond.
        return Gesture(
            listOf(
                Stroke(
                    from = touch.start,
                    to = touch.end,
                    durationMilliseconds = touch.durationMilliseconds.coerceAtLeast(1L),
                ),
            ),
        )
    }

    suspend fun replay(gesture: Gesture) {
        dispatcher.dispatch(gesture)
    }
}
