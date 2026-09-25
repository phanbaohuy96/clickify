package com.pbh.clickify.overlay

import com.pbh.clickify.domain.recording.RecordedTouch
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.overlay.ui.RecordingEvent

/**
 * The finger currently on the screen, and the [RecordedTouch] it becomes when it leaves.
 *
 * Shared by the two things that watch touches for different reasons — [Recorder], which keeps a
 * whole session of them and hands each one back to the application underneath (`RD-2`), and
 * [Picker], which wants exactly one and hands nothing back (`PK-2`). The bookkeeping is identical
 * and the decisions are not, so this is the part they have in common and no more.
 */
internal class TouchAssembler {
    private var down: Down? = null

    /** When the screen was last let go of, so the next touch knows how long the pause was. */
    private var lastReleasedAt: Long? = null

    private data class Down(
        val at: ScreenPoint,
        val atMilliseconds: Long,
        var latest: ScreenPoint,
    )

    /** Returns the touch that just finished, or null while one is still in progress. */
    fun accept(event: RecordingEvent): RecordedTouch? =
        when (event) {
            is RecordingEvent.Down -> {
                down = Down(at = event.at, atMilliseconds = event.atMilliseconds, latest = event.at)
                null
            }

            is RecordingEvent.Moved -> {
                down?.latest = event.at
                null
            }

            RecordingEvent.Cancelled -> {
                // The system took the gesture away. Half a touch is not a Step, and acting on one
                // the user did not complete would put something on screen they never did.
                down = null
                null
            }

            is RecordingEvent.Up -> complete(event)
        }

    private fun complete(event: RecordingEvent.Up): RecordedTouch? {
        val started = down ?: return null
        down = null
        val gap = lastReleasedAt?.let { (started.atMilliseconds - it).coerceAtLeast(0) } ?: 0L
        lastReleasedAt = event.atMilliseconds
        return RecordedTouch(
            start = started.at,
            end = event.at,
            durationMilliseconds = (event.atMilliseconds - started.atMilliseconds).coerceAtLeast(0),
            gapBeforeMilliseconds = gap,
        )
    }
}
