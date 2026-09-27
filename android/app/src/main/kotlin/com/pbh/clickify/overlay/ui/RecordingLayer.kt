package com.pbh.clickify.overlay.ui

import android.view.MotionEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import com.pbh.clickify.domain.scenario.ScreenPoint
import kotlin.math.roundToInt

/** RD-6: wide enough to be unmistakable at the edge of vision, thin enough to see past. */
private val RECORDING_BORDER = 4.dp

/**
 * The layer that swallows a touch so it can be recorded (`RD-1`, `RD-6`).
 *
 * Raw screen coordinates, because that is what a **Gesture** is dispatched in: a touch recorded
 * anywhere else would be replayed somewhere else.
 *
 * The border is not decoration. While this is on screen the application underneath is being driven
 * by **synthetic** touches rather than by a finger (`RD-5`), and an application that treats those
 * differently will behave differently while being recorded. The user is entitled to know which
 * mode they are in without having to remember.
 */
@Composable
fun RecordingLayer(
    onTouch: (RecordingEvent) -> Unit,
    modifier: Modifier = Modifier,
    passThrough: Boolean = true,
) {
    // RD-9: red is "what you do is being done", the accent is "what you do is only being noted".
    // Silent recording never touches the application underneath, so it never earns the red.
    CaptureLayer(
        border = if (passThrough) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        onTouch = onTouch,
        modifier = modifier,
    )
}

/**
 * PK-1: the same layer, armed for one gesture rather than for a session.
 *
 * The accent rather than red, and that is the whole message. Red says *this screen is being
 * recorded and what you do is being kept*; the accent says *this screen is waiting for you to
 * point at something*. They are different promises and the user has to be able to tell which one
 * is in force, because in one of them the application underneath is reacting to their finger and
 * in the other it is deliberately not (`PK-2`).
 */
@Composable
fun PickLayer(
    onTouch: (RecordingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    CaptureLayer(border = MaterialTheme.colorScheme.primary, onTouch = onTouch, modifier = modifier)
}

/**
 * The layer that swallows a touch so it can be used (`RD-1`, `RD-6`, `PK-1`).
 *
 * Raw screen coordinates, because that is what a **Gesture** is dispatched in: a touch recorded
 * anywhere else would be replayed somewhere else.
 *
 * The border is not decoration. While this is on screen the user's finger is not reaching the
 * application underneath in the ordinary way, and they are entitled to know which mode they are in
 * without having to remember.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CaptureLayer(
    border: Color,
    onTouch: (RecordingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .border(RECORDING_BORDER, border)
            .pointerInteropFilter { event ->
                val at = ScreenPoint(event.rawX.roundToInt(), event.rawY.roundToInt())
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> onTouch(RecordingEvent.Down(at, event.eventTime))
                    MotionEvent.ACTION_MOVE -> onTouch(RecordingEvent.Moved(at))
                    MotionEvent.ACTION_UP -> onTouch(RecordingEvent.Up(at, event.eventTime))
                    MotionEvent.ACTION_CANCEL -> onTouch(RecordingEvent.Cancelled)
                    else -> Unit
                }
                true
            },
    )
}

/**
 * What the recording layer saw, with Android's `MotionEvent` left behind at the boundary.
 *
 * A second finger is not here on purpose (`RD-7`): this records taps and swipes, and a multi-touch
 * **Step** is something the user builds in the panel rather than performs.
 */
sealed interface RecordingEvent {
    data class Down(
        val at: ScreenPoint,
        val atMilliseconds: Long,
    ) : RecordingEvent

    data class Moved(
        val at: ScreenPoint,
    ) : RecordingEvent

    data class Up(
        val at: ScreenPoint,
        val atMilliseconds: Long,
    ) : RecordingEvent

    /** The system took the gesture away. Nothing is recorded, and nothing is re-emitted. */
    data object Cancelled : RecordingEvent
}
