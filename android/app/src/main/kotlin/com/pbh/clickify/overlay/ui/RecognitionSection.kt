package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.editor.StepDraft
import com.pbh.clickify.domain.scenario.Guard
import com.pbh.clickify.domain.scenario.OnTimeout
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.ScenarioLimits
import com.pbh.clickify.domain.scenario.TemplateSearch
import com.pbh.clickify.overlay.CropPurpose
import com.pbh.clickify.overlay.EditingStep

/**
 * TP-19: what this Step aims at — a point it was put on, or a picture it goes and finds.
 *
 * Shown only for the three Actions that use a Target (`TP-22`). A `globalAction` has nowhere to
 * put a match, and offering the search there would be offering a setting that does nothing.
 */
@Composable
internal fun FindSection(
    editing: EditingStep,
    actions: StepPanelActions,
) {
    val draft = editing.draft
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelSectionLabel(stringResource(R.string.recognition_section_find))
        val search = draft.search
        if (search == null) {
            EmptySlot(
                explanation = stringResource(R.string.recognition_fixed_point),
                label = stringResource(R.string.recognition_find_add),
                onAdd = { actions.onCropTemplate(CropPurpose.TARGET) },
            )
        } else {
            SearchFields(
                editing = editing,
                search = search,
                onSearch = { actions.onDraftChanged(draft.copy(search = it)) },
                onRecrop = { actions.onCropTemplate(CropPurpose.TARGET) },
                onRemove = { actions.onDraftChanged(draft.copy(search = null)) },
            )
        }
    }
}

/**
 * TP-24: the condition, which is the same search read the other way round.
 *
 * Offered for every Action, `globalAction` included: "press Back once the advert is gone" is as
 * reasonable as anything with a point in it. `TP-25` is what keeps this from being a branch —
 * there is no else here, only the two things [OnTimeout] already meant.
 */
@Composable
internal fun GuardSection(
    editing: EditingStep,
    actions: StepPanelActions,
) {
    val draft = editing.draft
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelSectionLabel(stringResource(R.string.recognition_section_guard))
        val guard = draft.guard
        if (guard == null) {
            EmptySlot(
                explanation = stringResource(R.string.recognition_no_guard),
                label = stringResource(R.string.recognition_guard_add),
                onAdd = { actions.onCropTemplate(CropPurpose.GUARD) },
            )
        } else {
            PresenceChips(guard) { actions.onDraftChanged(draft.copy(guard = guard.copy(expects = it))) }
            SearchFields(
                editing = editing,
                search = guard.search,
                onSearch = { actions.onDraftChanged(draft.copy(guard = guard.copy(search = it))) },
                onRecrop = { actions.onCropTemplate(CropPurpose.GUARD) },
                onRemove = { actions.onDraftChanged(draft.copy(guard = null)) },
            )
        }
    }
}

@Composable
private fun EmptySlot(
    explanation: String,
    label: String,
    onAdd: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAdd) { Text(label) }
    }
}

/** TP-24: *is it there* or *has it gone*, which are the only two conditions there are. */
@Composable
private fun PresenceChips(
    guard: Guard,
    onExpects: (Presence) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = guard.expects == Presence.PRESENT,
            onClick = { onExpects(Presence.PRESENT) },
            label = { Text(stringResource(R.string.recognition_guard_present)) },
        )
        FilterChip(
            selected = guard.expects == Presence.ABSENT,
            onClick = { onExpects(Presence.ABSENT) },
            label = { Text(stringResource(R.string.recognition_guard_absent)) },
        )
    }
}

/**
 * The picture, and the four numbers that decide what finding it means.
 *
 * The preview is the point of this whole block. A **Template** identified only by a UUID is a
 * **Step** the user cannot check without running it, and "which of my six pictures is this" is
 * exactly the question a preview answers and a name would not — they cropped it, they will
 * recognise it.
 */
@Composable
private fun SearchFields(
    editing: EditingStep,
    search: TemplateSearch,
    onSearch: (TemplateSearch) -> Unit,
    onRecrop: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val preview = editing.previews[search.templateId]
        if (preview == null) {
            Text(
                text = stringResource(R.string.recognition_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.widthIn(max = PREVIEW_MAXIMUM),
            )
        } else {
            Image(
                bitmap = preview,
                contentDescription = stringResource(R.string.recognition_preview),
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .widthIn(max = PREVIEW_MAXIMUM)
                        .heightIn(max = PREVIEW_MAXIMUM)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Stepper(
                label = stringResource(R.string.recognition_threshold, (search.threshold * PERCENT).toInt()),
                onLess = { onSearch(search.byThreshold(-THRESHOLD_STEP)) },
                onMore = { onSearch(search.byThreshold(THRESHOLD_STEP)) },
            )
            Stepper(
                label = stringResource(R.string.recognition_wait, search.waitMilliseconds.asSeconds()),
                onLess = { onSearch(search.byWait(-WAIT_STEP)) },
                onMore = { onSearch(search.byWait(WAIT_STEP)) },
            )
            TimeoutChips(search, onSearch)
            RegionRow(search, onSearch)
            Row {
                TextButton(onClick = onRecrop) { Text(stringResource(R.string.recognition_recrop)) }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.recognition_remove)) }
            }
        }
    }
}

/** TP-21: the two things that can happen, named as what happens rather than as a code. */
@Composable
private fun TimeoutChips(
    search: TemplateSearch,
    onSearch: (TemplateSearch) -> Unit,
) {
    Text(
        text = stringResource(R.string.recognition_timeout_label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = search.onTimeout == OnTimeout.STOP_SCENARIO,
            onClick = { onSearch(search.copy(onTimeout = OnTimeout.STOP_SCENARIO)) },
            label = { Text(stringResource(R.string.recognition_timeout_stop)) },
        )
        FilterChip(
            selected = search.onTimeout == OnTimeout.SKIP_STEP,
            onClick = { onSearch(search.copy(onTimeout = OnTimeout.SKIP_STEP)) },
            label = { Text(stringResource(R.string.recognition_timeout_skip)) },
        )
    }
}

/** TP-9: where it looks, and the one press that widens it to everywhere. */
@Composable
private fun RegionRow(
    search: TemplateSearch,
    onSearch: (TemplateSearch) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text =
                stringResource(
                    if (search.region == null) R.string.recognition_region_all else R.string.recognition_region_near,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (search.region != null) {
            TextButton(onClick = { onSearch(search.copy(region = null)) }) {
                Text(stringResource(R.string.recognition_region_widen))
            }
        }
    }
}

private val PREVIEW_MAXIMUM = 88.dp
private const val PERCENT = 100
private const val THRESHOLD_STEP = 0.02
private const val WAIT_STEP = 1_000
