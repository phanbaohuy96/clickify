package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.editor.StepActionKind
import com.pbh.clickify.domain.editor.StepDraft
import com.pbh.clickify.domain.editor.usesTarget
import com.pbh.clickify.domain.scenario.ScenarioLimits
import com.pbh.clickify.overlay.CropPurpose
import com.pbh.clickify.overlay.EditingStep
import com.pbh.clickify.overlay.OverlayScreen
import com.pbh.clickify.overlay.PanelExit

/**
 * The third Overlay window (`OV-1`): one Step, open for configuration, as a sheet (`OV-34`).
 *
 * It edits everything about a Step **except where it touches**. Points belong to the Marker layer,
 * where the user can see what they are aiming at (`OV-7`, `OV-21`), and two ways to set the same
 * value would only disagree with each other.
 *
 * Save is offered only when the Step has no violations (`OV-22`, `SM-17`) and is pinned below the
 * scrolling body, so a long Step cannot hide the button that commits it. Delete applies at once,
 * because it changes the Scenario's shape rather than this Step's fields — the same immediacy
 * dragging a Marker already has.
 */
@Composable
fun StepPanel(
    editing: EditingStep,
    actions: StepPanelActions,
    modifier: Modifier = Modifier,
    leaving: PanelExit? = null,
    screen: OverlayScreen? = null,
) {
    // OV-20: how many fields hold the caret, not whether the last event was a gain. A focus moving
    // from one field to the next reports a loss and a gain in the same frame, and a boolean would
    // drop the window out of focus in between — taking the keyboard with it.
    var focusedFields by remember { mutableIntStateOf(0) }
    val typing = focusedFields > 0
    LaunchedEffect(typing) { actions.onTypingChanged(typing) }
    val onFocus: (Boolean) -> Unit = { gained -> focusedFields += if (gained) 1 else -1 }
    val discard: (@Composable BoxScope.() -> Unit)? =
        if (leaving == null) {
            null
        } else {
            { DiscardConfirm(actions) }
        }

    OverlayPanelSheet(
        screen = screen,
        modifier = modifier,
        header = { PanelHeader(editing, actions) },
        footer = { PanelFooter(editing, actions) },
        confirm = discard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            ActionSection(editing.draft) { actions.onDraftChanged(editing.draft.copy(kind = it)) }

            // Keyed so the fields' own text state starts fresh when the Step or its Action changes,
            // instead of a hold duration being shown in a swipe's duration box.
            key(editing.draft.stepId, editing.draft.kind) {
                StepActionFields(editing = editing, onDraft = actions.onDraftChanged, onFocus = onFocus)
                CommonFields(editing.draft, actions.onDraftChanged, onFocus)
            }

            // TP-22: the search belongs to the Target, so it is offered only where there is one.
            if (editing.draft.kind.usesTarget) FindSection(editing, actions)
            GuardSection(editing, actions)

            editing.violations.forEach {
                Text(
                    text = it.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Everything the panel can do, in one value, so the signature stays readable. */
data class StepPanelActions(
    val onDraftChanged: (StepDraft) -> Unit,
    val onTypingChanged: (Boolean) -> Unit,
    val onSave: () -> Unit,
    /** OV-36: up to the Scenario, which is the list of every Step. */
    val onBack: () -> Unit,
    val onCancel: () -> Unit,
    /** OV-36: the pending exit, answered. */
    val onDiscard: () -> Unit,
    val onKeepEditing: () -> Unit,
    val onDelete: () -> Unit,
    /** OV-24: -1 opens the previous Step in the Scenario, +1 the next. */
    val onGo: (Int) -> Unit,
    /** TP-7: take the Overlay off the screen and crop a Template out of what is underneath. */
    val onCropTemplate: (CropPurpose) -> Unit,
    /** Runs this draft once, on its own, and saves nothing. */
    val onTry: () -> Unit,
)

/**
 * OV-36: the way back, and what the Step is, in the order they are read.
 *
 * The back arrow is the whole of this requirement. Before it the only way out of a Step was the
 * close button, which took the panel with it — so editing a second Step meant closing the editor,
 * finding the Marker again and tapping it, and a Step with no Marker (`SM-8`) could not be reached
 * at all without walking to it. Up is one press now, and up is the list of every Step.
 */
@Composable
private fun PanelHeader(
    editing: EditingStep,
    actions: StepPanelActions,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_ROW_INSET),
        ) {
            PanelIconButton(
                onClick = actions.onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = stringResource(R.string.step_back_to_scenario),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.step_panel_title, editing.stepNumber, editing.stepCount),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = editing.draft.kind.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PanelIconButton(
                onClick = actions.onDelete,
                icon = Icons.Default.Delete,
                description = stringResource(R.string.step_delete),
            )
            PanelIconButton(
                onClick = actions.onCancel,
                icon = Icons.Default.Close,
                description = stringResource(R.string.step_cancel),
            )
        }
        // OV-38: the line the body scrolls under. Without it the first field slides up behind the
        // title and the two are read as one broken row.
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * OV-22: Save is pinned, so a Step tall enough to scroll cannot hide the way to commit it.
 *
 * OV-24's two arrows live here rather than in the header. They are about *which* Step is open,
 * which is the same kind of question as Cancel and Save — and the header had five controls in it,
 * which is two more than a row of icons can carry before it stops being read at all.
 */
@Composable
private fun PanelFooter(
    editing: EditingStep,
    actions: StepPanelActions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_ROW_INSET, vertical = 8.dp),
    ) {
        PanelIconButton(
            onClick = { actions.onGo(-1) },
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = stringResource(R.string.step_previous),
            enabled = editing.canGoBack,
        )
        PanelIconButton(
            onClick = { actions.onGo(1) },
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = stringResource(R.string.step_next),
            enabled = editing.canGoForward,
        )
        // GX-1 for one Step. Beside the arrows rather than beside Save, because it is about
        // *this* Step like they are, and because a button next to Save that does not save is the
        // one place a misfire costs the user their edits.
        TextButton(onClick = actions.onTry, enabled = editing.canSave) {
            Text(stringResource(R.string.step_try))
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = actions.onCancel) { Text(stringResource(R.string.step_cancel)) }
        Button(
            onClick = actions.onSave,
            enabled = editing.canSave,
            modifier = Modifier.padding(start = 8.dp, end = PANEL_GUTTER - PANEL_ROW_INSET),
        ) {
            Text(stringResource(R.string.step_save))
        }
    }
}

/** OV-36: asked once, and only when there is something to lose. */
@Composable
private fun BoxScope.DiscardConfirm(actions: StepPanelActions) {
    PanelConfirm(
        title = stringResource(R.string.step_discard_title),
        message = stringResource(R.string.step_discard_message),
        confirmLabel = stringResource(R.string.step_discard_confirm),
        dismissLabel = stringResource(R.string.step_discard_dismiss),
        onConfirm = actions.onDiscard,
        onDismiss = actions.onKeepEditing,
    )
}

/**
 * SM-7: the five Actions, all visible at once so the choice needs no menu to discover — under a
 * heading that says what the row is.
 *
 * The heading and the line under it are not decoration. Five unlabelled chips at the top of a
 * sheet read as filters, which is what chips usually are; nothing said that picking one changes
 * what the Step *does*. The sentence underneath says it in the Step's own terms, and it changes
 * with the selection, so the answer is there before the question is asked.
 */
@Composable
private fun ActionSection(
    draft: StepDraft,
    onKind: (StepActionKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelSectionLabel(stringResource(R.string.step_action_heading))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            StepActionKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == draft.kind,
                    onClick = { onKind(kind) },
                    label = { Text(kind.label()) },
                )
            }
        }
        Text(
            text = draft.kind.describeKind(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** SM-5: the two properties every Step has, whichever Action it carries. */
@Composable
private fun CommonFields(
    draft: StepDraft,
    onDraft: (StepDraft) -> Unit,
    onFocus: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            label = stringResource(R.string.step_repeat),
            value = draft.repeatCount.toLong(),
            onValue = { onDraft(draft.copy(repeatCount = it.toInt())) },
            onFocus = onFocus,
            modifier = Modifier.weight(1f),
            range = ScenarioLimits.stepRepeatCount.toLongRange(),
        )
        NumberField(
            label = stringResource(R.string.step_delay),
            value = draft.delayMillisecondsAfter.toLong(),
            onValue = { onDraft(draft.copy(delayMillisecondsAfter = it.toInt())) },
            onFocus = onFocus,
            modifier = Modifier.weight(1f),
            range = ScenarioLimits.delayMilliseconds.toLongRange(),
        )
    }
}

@Composable
internal fun StepActionKind.label(): String =
    stringResource(
        when (this) {
            StepActionKind.TAP -> R.string.step_kind_tap
            StepActionKind.SWIPE -> R.string.step_kind_swipe
            StepActionKind.MULTI_TOUCH -> R.string.step_kind_multi_touch
            StepActionKind.GLOBAL_ACTION -> R.string.step_kind_global
            StepActionKind.SET_TEXT -> R.string.step_kind_text
        },
    )

/** One sentence per Action, in the Step's own terms rather than the platform's. */
@Composable
private fun StepActionKind.describeKind(): String =
    stringResource(
        when (this) {
            StepActionKind.TAP -> R.string.step_about_tap
            StepActionKind.SWIPE -> R.string.step_about_swipe
            StepActionKind.MULTI_TOUCH -> R.string.step_about_multi_touch
            StepActionKind.GLOBAL_ACTION -> R.string.step_about_global
            StepActionKind.SET_TEXT -> R.string.step_about_text
        },
    )

private fun IntRange.toLongRange(): LongRange = first.toLong()..last.toLong()
