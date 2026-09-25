package com.pbh.clickify.overlay.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.core.overlay.windowDragHandle
import com.pbh.clickify.domain.run.FinishReason
import com.pbh.clickify.overlay.OverlayUiState

/** One row, and the row is the control. Everything is sized from this. */
private val CONTROL_HEIGHT = 48.dp

/** Smaller than Material's 48dp default, which is what made six buttons unaffordable. */
private val BUTTON_SIZE = 40.dp

private val ICON_SIZE = 22.dp

/**
 * The control that is always reachable (OV-12, OV-13, OV-14).
 *
 * **One row.** It was two — a caption above a row of buttons — and the window measured 840×228
 * pixels on the test device while the user was building a Scenario. It is 792×144 now: a third of
 * the height gone, and it gained a button on the way. The caption is gone rather than shrunk,
 * because the Scenario's name belongs in the panel where there is room to change it, and
 * repeating it over somebody's game earns nothing.
 *
 * The exception is a failed run. [FinishReason] appears as a second line, because it is the one
 * thing the control has to say that the user did not already know (`OV-17`), and it goes away by
 * itself on the next action.
 *
 * Six buttons and no more. `landscape.md` ranks "controls that sit on top of what you are
 * automating" seventh among the category's complaints; everything not on this row is one tap away
 * in the panel.
 */
@Composable
fun FloatingControl(
    state: OverlayUiState,
    actions: FloatingControlActions,
    modifier: Modifier = Modifier,
) {
    if (state.collapsed) {
        CollapsedBubble(state = state, actions = actions, modifier = modifier)
        return
    }

    OverlaySurface(modifier = modifier.widthIn(max = 360.dp), glass = true) {
        Column {
            FinishLine(state)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(CONTROL_HEIGHT)) {
                DragGrip(
                    onDragBy = actions.onDragBy,
                    onDragFinished = actions.onDragFinished,
                    height = CONTROL_HEIGHT,
                )
                when (val run = state.run) {
                    OverlayUiState.RunState.Stopped ->
                        when {
                            state.isPicking -> PickingRow(actions)
                            state.isRecording -> RecordingRow(state.recording?.touches ?: 0, actions)
                            else -> IdleRow(actions)
                        }

                    is OverlayUiState.RunState.CountingDown ->
                        RunningRow(
                            caption = stringResource(R.string.overlay_starting_in, run.remainingMilliseconds / 1000 + 1),
                            actions = actions,
                        )

                    is OverlayUiState.RunState.Running ->
                        RunningRow(
                            caption =
                                if (state.skippedSteps == 0) {
                                    stringResource(R.string.overlay_step_of, run.stepNumber, run.stepCount)
                                } else {
                                    // TP-21: a skipped Step is a Step doing what it was told, and
                                    // the one thing worse than seeing it is not seeing it.
                                    stringResource(
                                        R.string.overlay_step_of_skipped,
                                        run.stepNumber,
                                        run.stepCount,
                                        state.skippedSteps,
                                    )
                                },
                            actions = actions,
                        )

                    OverlayUiState.RunState.Stopping ->
                        RunningRow(caption = stringResource(R.string.overlay_stopping), actions = actions, onStop = null)
                }
            }
        }
    }
}

/** Every button on the control, in one value, so the signature stays readable. */
data class FloatingControlActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    /** RD-1: begins swallowing touches, recording them, and handing them on. */
    val onRecord: () -> Unit,
    /** RD-3: ends the session and turns what was caught into Steps. */
    val onStopRecording: () -> Unit,
    /** PK-1: hides the Overlay so one Step can be aimed at the screen underneath. */
    val onAddStep: () -> Unit,
    /** PK-3: the way out of aiming without leaving a Step behind. */
    val onCancelPick: () -> Unit,
    val onOpenPanel: () -> Unit,
    /** OV-33: nothing is written here that was not written already — this is "I am finished". */
    val onDone: () -> Unit,
    val onFreeTheTouch: () -> Unit,
    val onToggleCollapsed: () -> Unit,
    /** OV-14: a delta in raw screen pixels, because the window moves out from under the finger. */
    val onDragBy: (x: Int, y: Int) -> Unit,
    val onDragFinished: () -> Unit,
)

/** OV-17: why the last run ended. Shown only when there is something to say. */
@Composable
private fun FinishLine(state: OverlayUiState) {
    val finish = state.lastFinish ?: return
    Text(
        text = finish.describeFinish(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp),
    )
}

@Composable
private fun IdleRow(actions: FloatingControlActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The one accent on the row: Start is what the control is for, and everything beside it is
        // a way of getting ready to press it.
        ControlButton(Icons.Default.PlayArrow, R.string.overlay_start, MaterialTheme.colorScheme.primary, actions.onStart)
        // RD-1. Deliberately **not** red while idle. A red dot is the universal sign that
        // recording is already happening, and a button that claims that while nothing is being
        // recorded teaches the user to distrust the one indicator that matters.
        IconButton(onClick = actions.onRecord, modifier = Modifier.size(BUTTON_SIZE)) {
            Icon(
                painter = painterResource(R.drawable.ic_record),
                contentDescription = stringResource(R.string.overlay_record),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
        ControlButton(Icons.Default.Add, R.string.overlay_add_step, onClick = actions.onAddStep)
        ControlButton(Icons.AutoMirrored.Filled.List, R.string.overlay_open_panel, onClick = actions.onOpenPanel)
        ControlButton(Icons.Default.Check, R.string.overlay_done, onClick = actions.onDone)
        ControlButton(Icons.Default.KeyboardArrowDown, R.string.overlay_collapse, onClick = actions.onToggleCollapsed)
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * OV-13: Stop is the largest target on the control at every moment of a run.
 *
 * A pill rather than the circle it used to be, and still larger: 96 by 40 is more area than a
 * 64dp disc, and it fits in one row instead of forcing a second one. It also carries the word,
 * which the circle could not.
 *
 * [onStop] is null once the runner has been asked and is finishing its last stroke (`GX-8`) —
 * asking twice does nothing, and a button that does nothing should not look pressable.
 */
@Composable
private fun RunningRow(
    caption: String,
    actions: FloatingControlActions,
    onStop: (() -> Unit)? = actions.onStop,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Surface(
            onClick = { onStop?.invoke() },
            enabled = onStop != null,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(width = 96.dp, height = 40.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.overlay_stop), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        // GX-11: a latched touch happens during a run, which is why this lives here. Small on
        // purpose — the notification and the Quick Settings tile are the copies meant for the
        // moment when no Overlay can be tapped at all.
        IconButton(onClick = actions.onFreeTheTouch, modifier = Modifier.size(BUTTON_SIZE)) {
            Icon(
                painter = painterResource(R.drawable.ic_free_the_touch),
                contentDescription = stringResource(R.string.overlay_free_the_touch),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * RD-6: what the control says while it is swallowing the user's touches.
 *
 * A pulsing red dot and a count, in the same one row as everything else. The count is there
 * because a recording session gives no other feedback — the application underneath reacts exactly
 * as it would to a finger (`RD-5`), so without it nothing on screen says the session is still
 * running.
 *
 * Finishing is a **word**, and the accent colour rather than red. Stopping a recording keeps what
 * was caught; the tick this used to be was a 64dp amber disc that read as a warning.
 */
@Composable
private fun RecordingRow(
    touches: Int,
    actions: FloatingControlActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RecordingDot()
        Text(
            text = pluralStringResource(R.plurals.overlay_touches, touches, touches),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Surface(
            onClick = actions.onStopRecording,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(width = 92.dp, height = 40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.overlay_stop_recording),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * PK-3: what the control says while the screen is armed for one gesture.
 *
 * The hint is on the control rather than on the picking layer itself, and that is not a layout
 * preference. The layer's entire job is to take the next touch; a Cancel button drawn inside it
 * would be a target the layer is also trying to record, and the two readings of one tap cannot
 * both be right. The control is a separate window, so a tap on Cancel is unambiguous.
 */
@Composable
private fun PickingRow(actions: FloatingControlActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.pick_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 196.dp),
        )
        TextButton(onClick = actions.onCancelPick) { Text(stringResource(R.string.pick_cancel)) }
        Spacer(Modifier.width(4.dp))
    }
}

/** The one thing on screen that says a touch is being taken rather than passed on (`RD-6`). */
@Composable
private fun RecordingDot() {
    val transition = rememberInfiniteTransition(label = "recording")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier =
            Modifier
                .padding(start = 4.dp)
                .size(10.dp)
                .alpha(pulse)
                .background(MaterialTheme.colorScheme.error, CircleShape),
    )
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: Int,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(BUTTON_SIZE)) {
        Icon(icon, contentDescription = stringResource(description), tint = tint, modifier = Modifier.size(ICON_SIZE))
    }
}

/**
 * OV-12: what is left when the control is in the way.
 *
 * It keeps saying whether something is running, because a control small enough to forget is a
 * control that can be running without the user noticing.
 */
@Composable
private fun CollapsedBubble(
    state: OverlayUiState,
    actions: FloatingControlActions,
    modifier: Modifier = Modifier,
) {
    val colours = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (state.running || state.isRecording) colours.error else colours.surfaceContainerHigh)
                .border(1.dp, colours.outline, CircleShape)
                .windowDragHandle(
                    onDragBy = actions.onDragBy,
                    onDragFinished = actions.onDragFinished,
                    onTap = actions.onToggleCollapsed,
                ),
    ) {
        if (state.running || state.isRecording) {
            // The step number rather than an icon: collapsed is exactly when the user cannot see
            // the caption, and "how far has it got" is the one thing they will want from a glance.
            Text(
                text =
                    state.recording?.touches?.toString()
                        ?: (state.run as? OverlayUiState.RunState.Running)?.stepNumber?.toString()
                        ?: "…",
                style = MaterialTheme.typography.titleMedium,
                color = colours.onError,
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.overlay_expand),
                tint = colours.primary,
            )
        }
    }
}

/** OV-17: why the last run ended, in one line, where the user is already looking. */
@Composable
private fun FinishReason.describeFinish(): String =
    when (this) {
        FinishReason.Completed -> stringResource(R.string.overlay_finished)
        FinishReason.Stopped -> stringResource(R.string.overlay_stopped)
        is FinishReason.ScreenProfileMismatch -> stringResource(R.string.overlay_screen_changed)
        is FinishReason.GestureCancelled -> stringResource(R.string.overlay_gesture_cancelled, stepIndex + 1)
        is FinishReason.NoFocusedField -> stringResource(R.string.overlay_no_field, stepIndex + 1)
        is FinishReason.GlobalActionRefused -> stringResource(R.string.overlay_action_refused, stepIndex + 1)
        is FinishReason.TemplateNotFound -> stringResource(R.string.overlay_template_not_found, stepIndex + 1)
        is FinishReason.GuardUnmet -> stringResource(R.string.overlay_guard_unmet, stepIndex + 1)
    }
