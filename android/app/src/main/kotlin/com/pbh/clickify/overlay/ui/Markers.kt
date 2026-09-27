package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pbh.clickify.core.overlay.windowDragHandle
import com.pbh.clickify.domain.overlay.Marker
import com.pbh.clickify.domain.overlay.MarkerRole
import com.pbh.clickify.domain.scenario.ScreenPoint

/** How wide a Marker is. Large enough to hit with a thumb, small enough to see past. */
val MARKER_DIAMETER: Dp = 44.dp

/** OV-21: the ring that picks the Marker being configured out of fifteen. */
private val EDITED_BORDER = 3.dp

/** The line every Marker is drawn with, so it has an edge over light content as well as dark. */
private val MARKER_EDGE = 2.dp

/**
 * One Marker, in a window of its own (OV-7, OV-27).
 *
 * A window per handle is what lets a Marker be dragged and tapped **while the application
 * underneath stays usable**: everything outside these few dozen pixels reaches whatever is below.
 * It replaced a full-screen layer with a mode the user had to remember to leave, which is the
 * shape `landscape.md` records as the category's seventh-worst complaint.
 */
@Composable
fun MarkerHandle(
    marker: Marker,
    edited: Boolean,
    onDragBy: (x: Int, y: Int) -> Unit,
    onDragFinished: () -> Unit,
    onTapped: () -> Unit,
    modifier: Modifier = Modifier,
    searching: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.surface
    val arrival = marker.isArrival

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(MARKER_DIAMETER)
                .clip(CircleShape)
                // DS-2: one accent. Which end of a travelling contact this is is told by whether
                // the disc is filled or hollow, not by a second and third hue — a Marker has to
                // stay legible over a game's own colours, and three of ours is two too many.
                .background(if (arrival) ink.copy(alpha = 0.92f) else accent)
                .border(if (edited) EDITED_BORDER else MARKER_EDGE, if (edited) Color.White else accent, CircleShape)
                .windowDragHandle(
                    onDragBy = onDragBy,
                    onDragFinished = onDragFinished,
                    onTap = onTapped,
                ),
    ) {
        // TP-23: a Step that finds a picture still has a Marker, and the Marker is where the
        // picture was cropped — which is where the Step acts if the match comes back where it was
        // made, and only then. The broken ring is that "only then": the number is still the
        // Step's, and the position is a starting guess rather than a promise.
        if (searching) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = if (arrival) accent else ink,
                    radius = size.minDimension / 2 - SEARCH_RING_INSET_PIXELS,
                    style =
                        Stroke(
                            width = SEARCH_RING_WIDTH_PIXELS,
                            pathEffect = PathEffect.dashPathEffect(SEARCH_RING_DASHES),
                        ),
                )
            }
        }
        Text(
            text = marker.label(),
            // The body face rather than the display one. A Marker's number is read at a glance
            // over somebody else's artwork, and Space Grotesk's figures are drawn to be
            // distinctive — which is the opposite of what is wanted here.
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (arrival) accent else MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * The lines joining the two ends of every travelling Marker pair (OV-8, OV-9).
 *
 * Its own full-screen window, and one that never takes a touch. The handles are what the user
 * grabs; this only says which of them belong together.
 */
@Composable
fun MarkerLines(
    markers: List<Marker>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxSize()) {
        markers
            .filter { it.role == MarkerRole.SWIPE_START || it.role == MarkerRole.TOUCH_START }
            .forEach { marker ->
                val end = marker.connectedTo ?: return@forEach
                drawLine(
                    color = accent,
                    start = Offset(marker.point.x.toFloat(), marker.point.y.toFloat()),
                    end = Offset(end.x.toFloat(), end.y.toFloat()),
                    strokeWidth = Stroke.HairlineWidth + 4f,
                )
            }
    }
}

/** Where a handle's window goes, given that the Marker names the point its Step will touch. */
fun Marker.handleOrigin(diameterPixels: Int): ScreenPoint = ScreenPoint(x = point.x - diameterPixels / 2, y = point.y - diameterPixels / 2)

/** OV-8: the end of a swipe is an arrow, not a second copy of the number. */
private fun Marker.label(): String = if (isArrival) "\u2192" else stepNumber.toString()

/** Where a travelling contact ends up, as opposed to where it starts (OV-8, OV-9). */
private val Marker.isArrival: Boolean
    get() = role == MarkerRole.SWIPE_END || role == MarkerRole.TOUCH_END

/** TP-23: a broken ring just inside the Marker's edge, in pixels because a Canvas works in them. */
private const val SEARCH_RING_INSET_PIXELS = 7f
private const val SEARCH_RING_WIDTH_PIXELS = 3f
private val SEARCH_RING_DASHES = floatArrayOf(7f, 6f)
