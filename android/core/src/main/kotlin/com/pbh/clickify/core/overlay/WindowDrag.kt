package com.pbh.clickify.core.overlay

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Drags the **window** this Composable is in, rather than anything inside it (`OV-14`).
 *
 * Raw screen coordinates, and not as a preference. A window moved under the finger takes its own
 * coordinate space with it: by the next event the finger is back where it started **relative to
 * the view**, so the ordinary `detectDragGestures` delta reads zero and the window stops one frame
 * into the drag. `MotionEvent.getRawX` is measured against the screen, which does not move.
 *
 * Whole pixels only — a window position is an integer — and the fraction is kept rather than
 * discarded, or a slow drag reports zero for ever and the control never leaves the corner.
 *
 * [onTap] is here rather than in a second `pointerInput` because this modifier consumes the whole
 * gesture: a Marker handle has to be both draggable and tappable (`OV-7`), and only the code that
 * already sees every event can tell those two apart.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.windowDragHandle(
    onDragBy: (x: Int, y: Int) -> Unit,
    onDragFinished: () -> Unit,
    onTap: (() -> Unit)? = null,
): Modifier {
    val slop = LocalViewConfiguration.current.touchSlop
    var anchorX by remember { mutableFloatStateOf(0f) }
    var anchorY by remember { mutableFloatStateOf(0f) }
    var travelled by remember { mutableFloatStateOf(0f) }

    return pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anchorX = event.rawX
                anchorY = event.rawY
                travelled = 0f
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val x = (event.rawX - anchorX).roundToInt()
                val y = (event.rawY - anchorY).roundToInt()
                if (x != 0 || y != 0) {
                    anchorX += x
                    anchorY += y
                    travelled += abs(x) + abs(y)
                    onDragBy(x, y)
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                if (onTap != null && travelled <= slop) onTap() else onDragFinished()
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                onDragFinished()
                true
            }

            else -> false
        }
    }
}
