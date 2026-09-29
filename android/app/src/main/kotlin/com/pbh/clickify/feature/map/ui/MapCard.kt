package com.pbh.clickify.feature.map.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.TemplateSearch
import com.pbh.clickify.overlay.ui.asSeconds
import com.pbh.clickify.overlay.ui.label
import java.util.UUID

/**
 * `MP-10`: everything about the Step or Steps under a tapped dot, floating on the map. Every value is
 * read-only, and a tap on the card does not fall through to the map behind it.
 */
@Composable
internal fun MapCard(
    steps: List<Pair<Int, Step>>,
    thumbnails: Map<UUID, ImageBitmap?>,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.medium)
                .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Box {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, top = 8.dp, end = 44.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                steps.forEach { (number, step) -> StepDetails(number, step, thumbnails) }
            }
            IconButton(onClick = onClose, Modifier.align(Alignment.TopEnd).size(CLOSE_TARGET_DP.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.overlay_close))
            }
        }
    }
}

/** `MP-10`: the Action and all its parameters, repeat and delay, then the search and the Guard. */
@Composable
private fun StepDetails(
    number: Int,
    step: Step,
    thumbnails: Map<UUID, ImageBitmap?>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$number", style = MaterialTheme.typography.titleSmall)
        ActionFacts(step.action)
        Fact(stringResource(R.string.step_repeat), step.repeatCount.toString())
        Fact(stringResource(R.string.step_delay), step.delayMillisecondsAfter.toString())
        step.effectiveSearch?.let { SearchDetails(stringResource(R.string.recognition_section_find), null, it, thumbnails) }
        step.guard?.let {
            val expects =
                stringResource(
                    if (it.expects == Presence.PRESENT) R.string.recognition_guard_present else R.string.recognition_guard_absent,
                )
            SearchDetails(stringResource(R.string.recognition_section_guard), expects, it.search, thumbnails)
        }
    }
}

@Composable
private fun ActionFacts(action: StepAction) {
    when (action) {
        is StepAction.Tap -> Fact(stringResource(R.string.step_hold), action.holdMilliseconds.toString())
        is StepAction.Swipe -> {
            Fact(
                stringResource(R.string.step_kind_swipe),
                stringResource(R.string.map_detail_destination, action.destination.x, action.destination.y),
            )
            Fact(stringResource(R.string.step_duration), action.durationMilliseconds.toString())
        }
        is StepAction.MultiTouch -> action.paths.forEachIndexed { index, path -> PathFact(index + 1, path) }
        is StepAction.Global -> Fact(stringResource(R.string.step_kind_global), action.action.label())
        is StepAction.SetText -> Fact(stringResource(R.string.step_kind_text), action.text)
    }
}

@Composable
private fun PathFact(
    number: Int,
    path: GesturePath,
) {
    Text(
        stringResource(R.string.map_detail_path, number, path.start.x, path.start.y, path.end.x, path.end.y, path.durationMilliseconds),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SearchDetails(
    title: String,
    expects: String?,
    search: TemplateSearch,
    thumbnails: Map<UUID, ImageBitmap?>,
) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        expects?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        val image = thumbnails[search.templateId]
        if (image != null) {
            Image(
                image,
                contentDescription = stringResource(R.string.recognition_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.heightIn(max = CARD_PICTURE_DP.dp),
            )
        } else {
            Text(
                stringResource(R.string.recognition_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            stringResource(R.string.recognition_threshold, (search.threshold * PERCENT).toInt()),
            style = MaterialTheme.typography.bodySmall,
        )
        val region = search.region
        Text(
            if (region == null) {
                stringResource(R.string.recognition_region_all)
            } else {
                stringResource(R.string.map_detail_region, region.left, region.top, region.right, region.bottom)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(R.string.recognition_wait, search.waitMilliseconds.asSeconds()), style = MaterialTheme.typography.bodySmall)
        // MP-7: the outcome is always said in words, whether or not the map draws it.
        Fact(
            stringResource(R.string.recognition_timeout_label),
            if (expects == null) notFound(search.onTimeout) else otherwise(search.onTimeout),
        )
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** `MP-5`, `MP-9`: the picture at a fixed, readable size, or an outlined empty box when it has gone (`TP-29`). */
@Composable
internal fun TemplateThumb(
    image: ImageBitmap?,
    size: Dp,
) {
    if (image != null) {
        Image(
            image,
            contentDescription = stringResource(R.string.recognition_preview),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    } else {
        Box(Modifier.size(size).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline)))
    }
}

private const val CLOSE_TARGET_DP = 40f
private const val CARD_PICTURE_DP = 72f
private const val PERCENT = 100
