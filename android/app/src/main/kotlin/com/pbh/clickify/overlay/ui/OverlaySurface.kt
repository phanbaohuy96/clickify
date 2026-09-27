package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pbh.clickify.core.designsystem.OverlayGlass
import com.pbh.clickify.core.overlay.windowDragHandle

/**
 * A window the user can see the edge of, whatever is behind it (`DS-4`, `DS-6`).
 *
 * **There is no drop shadow, and its absence is the fix for a real defect.** A shadow is offset
 * downwards, so on a window with rounded corners it pools in the two *bottom* corners and shows
 * as a grey wedge outside the curve — measured at 20% grey against a white application, which is
 * plainly visible and was reported as one. Worse, the blur behind a glass window is cropped to the
 * window's **rectangle** rather than to its shape, so the same two corners were about to collect a
 * second artifact on top of the first.
 *
 * What replaces it is cheaper and covers more cases. The hairline is what gives the window an edge
 * over a dark wallpaper, where the surface is the same value as the background and the window
 * otherwise vanishes; over a light application the surface is near-black and needs no help at all,
 * which is the case the shadow was originally added for. Where two Overlay windows meet — the
 * control resting on the panel — they are told apart by [tone] instead, because on a dark scheme a
 * shadow was never visible between them anyway.
 */
@Composable
fun OverlaySurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tone: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    glass: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (glass) tone.copy(alpha = OverlayGlass.SURFACE_ALPHA) else tone,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content,
    )
}

/**
 * OV-14: what the user takes hold of to move the control.
 *
 * Its own target rather than the whole control, for two reasons. A drag that begins on a button
 * has to be told apart from a press of that button, and this way it never has to be; and a handle
 * that looks like a handle is the only thing on the control that says it can be moved at all.
 */
@Composable
fun DragGrip(
    onDragBy: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(width = 20.dp, height = height)
                .windowDragHandle(onDragBy = onDragBy, onDragFinished = onDragFinished),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 3.5.dp, height = 18.dp),
            content = {},
        )
    }
}
