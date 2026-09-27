package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.editor.StepActionKind
import com.pbh.clickify.domain.editor.StepDraft
import com.pbh.clickify.domain.editor.withPathAdded
import com.pbh.clickify.domain.editor.withPathRemoved
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.overlay.EditingStep

/**
 * The fields that belong to the Action in force, and only those (OV-21).
 *
 * None of them edits a point. Points are dragged on the **Marker** layer where the user can see
 * what they are aiming at (`OV-7`); a pair of coordinate boxes here would be a worse way to do the
 * same thing and would fight the layer for the same value.
 */
@Composable
internal fun StepActionFields(
    editing: EditingStep,
    onDraft: (StepDraft) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = editing.draft
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (draft.kind) {
            StepActionKind.TAP ->
                NumberField(
                    label = stringResource(R.string.step_hold),
                    value = draft.holdMilliseconds,
                    onValue = { onDraft(draft.copy(holdMilliseconds = it)) },
                    onFocus = onFocus,
                )

            StepActionKind.SWIPE -> {
                NumberField(
                    label = stringResource(R.string.step_duration),
                    value = draft.swipeDurationMilliseconds,
                    onValue = { onDraft(draft.copy(swipeDurationMilliseconds = it)) },
                    onFocus = onFocus,
                )
                PointsHint()
            }

            StepActionKind.MULTI_TOUCH -> MultiTouchFields(editing, onDraft, onFocus)

            StepActionKind.GLOBAL_ACTION ->
                GlobalActionChips(
                    selected = draft.globalAction,
                    onSelected = { onDraft(draft.copy(globalAction = it)) },
                )

            StepActionKind.SET_TEXT ->
                TextEntryField(
                    value = draft.text,
                    onValue = { onDraft(draft.copy(text = it)) },
                    onFocus = onFocus,
                )
        }
    }
}

/** OV-9: one duration per contact, and the contacts themselves are dragged like any other Marker. */
@Composable
private fun MultiTouchFields(
    editing: EditingStep,
    onDraft: (StepDraft) -> Unit,
    onFocus: (Boolean) -> Unit,
) {
    val draft = editing.draft
    draft.paths.forEachIndexed { index, path ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                label = stringResource(R.string.step_contact, index + 1),
                value = path.durationMilliseconds,
                onValue = { milliseconds ->
                    onDraft(
                        draft.copy(
                            paths =
                                draft.paths.mapIndexed { at, existing ->
                                    if (at == index) existing.copy(durationMilliseconds = milliseconds) else existing
                                },
                        ),
                    )
                },
                onFocus = onFocus,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onDraft(draft.withPathRemoved(index)) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.step_remove_contact, index + 1),
                )
            }
        }
    }

    // SM-17: the button disappears at the device's own stroke count rather than offering a
    // contact that would then be refused.
    if (draft.paths.size < editing.limits.maxStrokeCount) {
        TextButton(onClick = { onDraft(draft.withPathAdded(editing.limits, editing.profile)) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.step_add_contact), Modifier.padding(start = 4.dp))
        }
    }
    PointsHint()
}

/** SM-10: the seven fixed operations, by name. */
@Composable
private fun GlobalActionChips(
    selected: GlobalActionKind,
    onSelected: (GlobalActionKind) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        GlobalActionKind.entries.forEach { kind ->
            FilterChip(
                selected = kind == selected,
                onClick = { onSelected(kind) },
                label = { Text(kind.label()) },
            )
        }
    }
}

/** OV-21: said once, wherever a point is part of the Action, so it is never looked for here. */
@Composable
private fun PointsHint() {
    Text(
        text = stringResource(R.string.step_points_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
