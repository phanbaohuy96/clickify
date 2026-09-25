package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScenarioLimits
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.orientation
import com.pbh.clickify.overlay.OverlayScreen
import java.util.UUID

private const val MILLISECONDS_PER_SECOND = 1_000

/** How wide the number column is: two digits and a hair, so 1 and 12 end on the same pixel. */
private val STEP_NUMBER_WIDTH = 26.dp

/**
 * The Scenario as a whole: its name, how often it runs, and every Step in order (`OV-28`).
 *
 * This is the half of the editor that was missing. Before it, a Scenario could only ever be called
 * "Untitled" and run exactly once, because `Scenario.name`, `runCount` and `countdownMilliseconds`
 * had no way in — and a Step with no Marker (`SM-8`) could only be reached by walking to it from a
 * Step that had one.
 *
 * Laid out in named sections (`OV-38`) rather than as one column of controls. The first version
 * was the fields in the order they occurred to whoever added them, which is how a name field, two
 * numbers and a list of twelve Steps came to look like one undifferentiated wall.
 */
@Composable
fun ScenarioPanel(
    scenario: Scenario,
    actions: ScenarioPanelActions,
    modifier: Modifier = Modifier,
    screen: OverlayScreen? = null,
    confirmingRebuild: Boolean = false,
) {
    // OV-20, exactly as the Step panel counts it: a count rather than a boolean, so focus moving
    // between two fields does not drop the window out of focus in between.
    var focusedFields by remember { mutableIntStateOf(0) }
    val typing = focusedFields > 0
    LaunchedEffect(typing) { actions.onTypingChanged(typing) }
    val onFocus: (Boolean) -> Unit = { gained -> focusedFields += if (gained) 1 else -1 }
    val rebuild: (@Composable BoxScope.() -> Unit)? =
        if (confirmingRebuild) {
            { RebuildConfirm(actions) }
        } else {
            null
        }

    OverlayPanelSheet(
        screen = screen,
        modifier = modifier,
        header = { Header(scenario, actions) },
        footer = { Footer(actions) },
        confirm = rebuild,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
        ) {
            ScreenBanner(built = scenario.screenProfile, screen = screen, onAskRebuild = actions.onAskRebuild)

            key(scenario.id) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PanelSectionLabel(stringResource(R.string.scenario_section_scenario))
                    NameField(scenario, actions, onFocus)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PanelSectionLabel(stringResource(R.string.scenario_section_run))
                    RunSettings(scenario, actions, onFocus)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PanelSectionLabel(stringResource(R.string.scenario_section_record))
                    RecordModes(actions)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StepsHeader(scenario, actions)
                StepList(scenario, actions)
            }
        }
    }
}

/** Everything the Scenario panel can do, in one value, so the signature stays readable. */
data class ScenarioPanelActions(
    val onRenamed: (String) -> Unit,
    val onRunCountChanged: (RunCount) -> Unit,
    val onCountdownChanged: (Int) -> Unit,
    val onTypingChanged: (Boolean) -> Unit,
    val onAddStep: () -> Unit,
    val onOpenStep: (UUID) -> Unit,
    /** OV-6: -1 moves the Step one place earlier, +1 one place later. */
    val onMoveStep: (UUID, Int) -> Unit,
    val onDeleteStep: (UUID) -> Unit,
    /** SM-18: ask about re-measuring, and the two answers. */
    val onAskRebuild: () -> Unit,
    val onKeepScreen: () -> Unit,
    val onRebuild: () -> Unit,
    /** RD-9: a session that notes each touch and hands nothing back to the app underneath. */
    val onRecordSilently: () -> Unit,
    val onFreeTheTouch: () -> Unit,
    val onOpenApp: () -> Unit,
    val onCloseOverlay: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * OV-33, SM-18: the Scenario's name, and what the Overlay knows about the screen it lives on.
 *
 * The line under the name is the whole of the orientation interface in the ordinary case. A
 * Scenario with no Marker yet has no screen of its own and says so — it will take whichever one
 * the phone is in when the first point is placed — and one with Markers names the screen it was
 * measured against. Neither is a control, because neither is a choice.
 */
@Composable
private fun Header(
    scenario: Scenario,
    actions: ScenarioPanelActions,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_ROW_INSET),
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = PANEL_GUTTER - PANEL_ROW_INSET)) {
                Text(
                    text = scenario.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            R.string.scenario_subtitle,
                            pluralStringResource(R.plurals.overlay_steps, scenario.steps.size, scenario.steps.size),
                            scenario.screenProfile?.orientation?.label()
                                ?: stringResource(R.string.scenario_orientation_auto),
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PanelIconButton(
                onClick = actions.onDismiss,
                icon = Icons.Default.Close,
                description = stringResource(R.string.step_cancel),
            )
        }
        // OV-38: without this the run settings scroll up behind the title and the two are read as
        // one broken row — it was the first thing wrong with this panel on a real screen.
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * FS-15: the name is saved as it is typed, like everything else about a Scenario.
 *
 * A blank name is kept blank in the field and refused on the way out, rather than snapping back to
 * something while the user is still mid-word.
 */
@Composable
private fun NameField(
    scenario: Scenario,
    actions: ScenarioPanelActions,
    onFocus: (Boolean) -> Unit,
) {
    LabelledTextField(
        label = stringResource(R.string.scenario_name),
        value = scenario.name,
        onValue = actions.onRenamed,
        onFocus = onFocus,
    )
}

/** SM-2, SM-3: how many times the whole Scenario runs, and how long before the first Step. */
@Composable
private fun RunSettings(
    scenario: Scenario,
    actions: ScenarioPanelActions,
    onFocus: (Boolean) -> Unit,
) {
    val untilStopped = scenario.runCount is RunCount.UntilStopped

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = untilStopped,
            onClick = {
                actions.onRunCountChanged(
                    if (untilStopped) RunCount.Times(1) else RunCount.UntilStopped,
                )
            },
            label = { Text(stringResource(R.string.scenario_until_stopped)) },
        )
        if (!untilStopped) {
            NumberField(
                label = stringResource(R.string.scenario_run_count),
                value = (scenario.runCount as RunCount.Times).count.toLong(),
                onValue = { actions.onRunCountChanged(RunCount.Times(it.toInt())) },
                onFocus = onFocus,
                modifier = Modifier.weight(1f),
                range = ScenarioLimits.runCount.first.toLong()..ScenarioLimits.runCount.last.toLong(),
            )
        }
        NumberField(
            label = stringResource(R.string.scenario_countdown),
            value = (scenario.countdownMilliseconds / MILLISECONDS_PER_SECOND).toLong(),
            onValue = { actions.onCountdownChanged((it * MILLISECONDS_PER_SECOND).toInt()) },
            onFocus = onFocus,
            modifier = Modifier.weight(1f),
            range = 0L..(ScenarioLimits.countdownMilliseconds.last / MILLISECONDS_PER_SECOND).toLong(),
        )
    }
}

/** OV-38: the list's own heading, and the one button that adds to it. */
@Composable
private fun StepsHeader(
    scenario: Scenario,
    actions: ScenarioPanelActions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        PanelSectionLabel(
            text =
                stringResource(
                    R.string.scenario_subtitle,
                    stringResource(R.string.scenario_section_steps),
                    scenario.steps.size.toString(),
                ),
            modifier = Modifier.weight(1f),
        )
        PanelIconButton(
            onClick = actions.onAddStep,
            icon = Icons.Default.Add,
            description = stringResource(R.string.overlay_add_step),
        )
    }
}

/**
 * OV-28: every Step, in order, reachable whether or not it draws a Marker.
 *
 * The list is the answer to `SM-8`. A `setText` or `globalAction` Step has nothing on the screen
 * to tap, and before this the only route to one was walking to it from a neighbour that did.
 */
@Composable
private fun StepList(
    scenario: Scenario,
    actions: ScenarioPanelActions,
) {
    if (scenario.steps.isEmpty()) {
        Text(
            text = stringResource(R.string.scenario_no_steps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }

    scenario.steps.forEachIndexed { index, step ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        StepRow(
            step = step,
            number = index + 1,
            canMoveUp = index > 0,
            canMoveDown = index < scenario.steps.lastIndex,
            actions = actions,
        )
    }
}

/**
 * OV-38: one Step, as two lines rather than one.
 *
 * What it does is the line that is read; what it costs in time is the line underneath, in the
 * quieter colour. They were one line before, which meant either the timing was missing — and a
 * Scenario is mostly timing — or it ran into the coordinates and neither could be scanned.
 */
@Composable
private fun StepRow(
    step: Step,
    number: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: ScenarioPanelActions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { actions.onOpenStep(step.id) }.padding(vertical = 2.dp),
    ) {
        Text(
            // Right-aligned in a fixed column, so the titles start at the same pixel whether the
            // Scenario has nine Steps or ninety.
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = STEP_NUMBER_WIDTH),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = step.summary(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = step.timing(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // One icon family for the pair. They were a chevron and a filled triangle, which sit at
        // different heights in their own boxes and read as two unrelated controls.
        PanelIconButton(
            onClick = { actions.onMoveStep(step.id, -1) },
            icon = Icons.Default.KeyboardArrowUp,
            description = stringResource(R.string.step_move_up),
            enabled = canMoveUp,
        )
        PanelIconButton(
            onClick = { actions.onMoveStep(step.id, 1) },
            icon = Icons.Default.KeyboardArrowDown,
            description = stringResource(R.string.step_move_down),
            enabled = canMoveDown,
        )
        PanelIconButton(
            onClick = { actions.onDeleteStep(step.id) },
            icon = Icons.Default.Delete,
            description = stringResource(R.string.step_delete),
        )
    }
}

/** SM-18: asked before it happens, because a clamped point has forgotten where it used to be. */
@Composable
private fun BoxScope.RebuildConfirm(actions: ScenarioPanelActions) {
    PanelConfirm(
        title = stringResource(R.string.scenario_rebuild_title),
        message = stringResource(R.string.scenario_rebuild_message),
        confirmLabel = stringResource(R.string.scenario_rebuild_confirm),
        dismissLabel = stringResource(R.string.scenario_rebuild_dismiss),
        onConfirm = actions.onRebuild,
        onDismiss = actions.onKeepScreen,
    )
}

/**
 * The three things that have nowhere else to live.
 *
 * "Free the touch" is here as well as on the running control, the notification and the Quick
 * Settings tile (`GX-11`, `GX-12`). The copies that matter in an emergency are the last two — no
 * Overlay can be tapped while a touch is latched — and this one is simply where a user who is
 * reading rather than panicking will look for it.
 */
@Composable
private fun Footer(actions: ScenarioPanelActions) {
    Column {
        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_TEXT_BUTTON_INSET, vertical = 4.dp),
        ) {
            TextButton(onClick = actions.onFreeTheTouch) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.overlay_free_the_touch))
            }
            Spacer(Modifier.weight(1f))
            PanelIconButton(
                onClick = actions.onOpenApp,
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                description = stringResource(R.string.overlay_open_app),
            )
            TextButton(onClick = actions.onCloseOverlay) { Text(stringResource(R.string.overlay_close)) }
        }
    }
}

/**
 * RD-9: the two recordings, named by what they do to the application underneath.
 *
 * The control's record button is the ordinary one and stays where it is (`RD-5`) — it is the mode
 * that makes a sequence spanning several screens recordable at all. Silent is the other half, and
 * it lives here rather than beside it because choosing between two modes is a decision, and the
 * control is a row of verbs.
 *
 * Both sentences say what happens to the app underneath rather than naming a mode. "Silent" means
 * nothing to somebody who has not read RD-9; "the app will not react" is the whole difference.
 */
@Composable
private fun RecordModes(actions: ScenarioPanelActions) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.scenario_record_silent_about),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = actions.onRecordSilently) {
            Text(stringResource(R.string.scenario_record_silent))
        }
    }
}
