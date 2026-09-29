package com.pbh.clickify.feature.map.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.scenario.OnTimeout
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.overlay.ui.summary
import java.util.UUID

/**
 * `MP-6`: where a box of [size] goes beside [anchor] inside [bounds]. It goes to the right of the anchor
 * and hangs below its middle; when that does not fit it flips to the left and above, and only then is
 * it clamped, so nothing is cut at the edge. [preferAbove] starts above instead, for a badge.
 */
internal fun placeBeside(
    anchor: Rect,
    gap: Float,
    size: Size,
    bounds: Size,
    preferAbove: Boolean = false,
): Offset {
    var x = anchor.right + gap
    if (x + size.width > bounds.width) x = anchor.left - gap - size.width
    var y = if (preferAbove) anchor.center.y - size.height else anchor.center.y
    if (preferAbove && y < 0f) y = anchor.center.y
    if (!preferAbove && y + size.height > bounds.height) y = anchor.center.y - size.height
    return Offset(
        x.coerceIn(0f, (bounds.width - size.width).coerceAtLeast(0f)),
        y.coerceIn(0f, (bounds.height - size.height).coerceAtLeast(0f)),
    )
}

/** Lays [content] out inside the frame beside [anchor] (`MP-6`); the frame is the space this is given. */
@Composable
internal fun FloatingInFrame(
    anchor: Rect,
    gapDp: Float,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val margin = MARGIN_DP.dp.roundToPx()
        val limit =
            MAX_WIDTH_DP.dp
                .roundToPx()
                .coerceAtMost(constraints.maxWidth - margin * 2)
                .coerceAtLeast(0)
        val placeable =
            measurables.first().measure(
                Constraints(maxWidth = limit, maxHeight = (constraints.maxHeight - margin * 2).coerceAtLeast(0)),
            )
        val bounds = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val at = placeBeside(anchor, gapDp.dp.toPx(), Size(placeable.width.toFloat(), placeable.height.toFloat()), bounds)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(at.x.toInt(), at.y.toInt()) }
    }
}

/**
 * `MP-9`: at most three short lines beside the current Step. The rest is in the card.
 *
 * It says what Playback assumes and never that a Step would succeed (`MP-7`, `MP-15`).
 */
@Composable
internal fun MapTooltip(
    number: Int,
    step: Step,
    thumbnails: Map<UUID, ImageBitmap?>,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val repeat = if (step.repeatCount > 1) " " + stringResource(R.string.map_repeat, step.repeatCount) else ""
            Text(
                "$number  ${step.summary()}$repeat",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            step.effectiveSearch?.let { search ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TemplateThumb(thumbnails[search.templateId], TOOLTIP_PICTURE_DP.dp)
                    Text(
                        stringResource(R.string.map_wait, search.waitMilliseconds) + " · " + notFound(search.onTimeout),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            step.guard?.let { guard ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(
                            if (guard.expects ==
                                Presence.PRESENT
                            ) {
                                R.string.map_out_only_present
                            } else {
                                R.string.map_out_only_absent
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    TemplateThumb(thumbnails[guard.search.templateId], TOOLTIP_PICTURE_DP.dp)
                    Text(otherwise(guard.search.onTimeout), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** `MP-7`: what a search that runs out of time does, in words. */
@Composable
internal fun notFound(onTimeout: OnTimeout): String =
    stringResource(if (onTimeout == OnTimeout.SKIP_STEP) R.string.map_out_not_found_skip else R.string.map_out_not_found_stop)

/** `MP-7`: what a Guard that does not hold does, in words. */
@Composable
internal fun otherwise(onTimeout: OnTimeout): String =
    stringResource(if (onTimeout == OnTimeout.SKIP_STEP) R.string.map_out_otherwise_skip else R.string.map_out_otherwise_stop)

private const val MARGIN_DP = 4f
private const val MAX_WIDTH_DP = 260f
private const val TOOLTIP_PICTURE_DP = 40f
