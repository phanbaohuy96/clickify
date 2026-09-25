package com.pbh.clickify.overlay.ui

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.scenario.ScenarioLimits
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenRegion
import com.pbh.clickify.overlay.CropPurpose
import com.pbh.clickify.overlay.OverlayBounds
import kotlin.math.roundToInt

/**
 * TP-7: the frozen screen, with a rectangle dragged on it.
 *
 * The user is dragging on a **picture of the screen**, not on the screen. That is the point: the
 * frame was taken with nothing of Clickify's on the display, so whatever is cropped out of it is
 * the application's own pixels and nothing else. macOS had to hide a translucent overlay and hope
 * (`RG-5`); here there is nothing to hide, because what is being shown was captured before this
 * window existed.
 *
 * Coordinates are raw and screen-absolute, taken from `rawX`/`rawY` exactly as the recording layer
 * takes them, because the rectangle has to mean the same thing a **Gesture** would.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CropLayer(
    frame: ImageBitmap,
    purpose: CropPurpose,
    onConfirm: (ScreenRegion) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    bounds: OverlayBounds = OverlayBounds(width = 0, height = 0),
) {
    var start by remember { mutableStateOf<ScreenPoint?>(null) }
    var region by remember { mutableStateOf<ScreenRegion?>(null) }
    val chosen = region
    val bigEnough = chosen != null && chosen.isBigEnoughForATemplate()

    Box(modifier.fillMaxSize()) {
        Image(
            bitmap = frame,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().pointerInteropFilter { event ->
                val at = ScreenPoint(event.rawX.roundToInt(), event.rawY.roundToInt())
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        start = at
                        region = null
                    }

                    MotionEvent.ACTION_MOVE -> start?.let { region = ScreenRegion.between(it, at) }
                    MotionEvent.ACTION_UP -> start?.let { region = ScreenRegion.between(it, at) }
                    else -> Unit
                }
                true
            },
        )
        CropFraming(chosen)
        CropBar(
            purpose = purpose,
            region = chosen,
            bigEnough = bigEnough,
            bounds = bounds,
            onConfirm = { chosen?.let(onConfirm) },
            onCancel = onCancel,
        )
    }
}

/** TP-8: a rectangle smaller than this has nothing left in it to correlate against. */
fun ScreenRegion.isBigEnoughForATemplate(): Boolean =
    width >= ScenarioLimits.MINIMUM_TEMPLATE_SIDE_PIXELS && height >= ScenarioLimits.MINIMUM_TEMPLATE_SIDE_PIXELS

/**
 * Everything outside the rectangle, dimmed, and a border around what is inside.
 *
 * Four rectangles rather than one with a hole, because a hole needs a blend mode and a layer, and
 * this window is already the size of the display.
 */
@Composable
private fun CropFraming(region: ScreenRegion?) {
    Canvas(Modifier.fillMaxSize()) {
        val scrim = Color.Black.copy(alpha = SCRIM_ALPHA)
        if (region == null) {
            drawRect(color = scrim, size = size)
            return@Canvas
        }
        val left = region.left.toFloat()
        val top = region.top.toFloat()
        val right = region.right.toFloat()
        val bottom = region.bottom.toFloat()

        drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(scrim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, bottom - top))
        drawRect(scrim, topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = BORDER_PIXELS),
        )
    }
}

/**
 * The instructions and the two answers.
 *
 * It moves out of the way rather than staying put: a button at the bottom of the screen is one of
 * the likeliest things anybody wants to crop, and a bar sitting on top of it would make that
 * impossible. When the rectangle reaches the top the bar goes to the bottom, and otherwise it
 * stays at the top where it does not cover the thumb.
 */
@Composable
private fun BoxScope.CropBar(
    purpose: CropPurpose,
    region: ScreenRegion?,
    bigEnough: Boolean,
    bounds: OverlayBounds,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val nearTheTop = region != null && region.top < BAR_AVOIDANCE_PIXELS
    val density = LocalDensity.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier =
            Modifier
                .align(if (nearTheTop) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(
                    start = BAR_MARGIN,
                    end = BAR_MARGIN,
                    top = BAR_MARGIN + with(density) { bounds.topInset.toDp() },
                    bottom = BAR_MARGIN + with(density) { bounds.bottomInset.toDp() },
                ).clip(MaterialTheme.shapes.large),
    ) {
        Column(Modifier.padding(BAR_PADDING)) {
            Text(
                text =
                    stringResource(
                        if (purpose == CropPurpose.TARGET) R.string.crop_target_title else R.string.crop_guard_title,
                    ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(if (bigEnough) R.string.crop_hint_ready else R.string.crop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = BAR_PADDING),
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.crop_cancel)) }
                Button(onClick = onConfirm, enabled = bigEnough) { Text(stringResource(R.string.crop_use)) }
            }
        }
    }
}

private const val SCRIM_ALPHA = 0.55f
private const val BORDER_PIXELS = 4f

/** How near the top a rectangle has to come before the bar gets out of its way, in pixels. */
private const val BAR_AVOIDANCE_PIXELS = 520

private val BAR_MARGIN = 24.dp
private val BAR_PADDING = 12.dp
