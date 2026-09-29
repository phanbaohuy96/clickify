package com.pbh.clickify.feature.map.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pbh.clickify.R
import com.pbh.clickify.core.designsystem.OverlayTheme
import com.pbh.clickify.core.ui.BaseScreen
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenOrientation
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.orientation
import com.pbh.clickify.feature.onboarding.readPermissionStatus
import com.pbh.clickify.feature.scenario.ui.findActivity
import com.pbh.clickify.overlay.OverlayService

/**
 * The Scenario map (`MP-1` to `MP-15`): one Scenario as a phone preview that plays itself, read-only.
 *
 * Nothing here runs anything and nothing here writes; the one button hands the Scenario to the
 * Overlay exactly as tapping its row does (`OV-26`). What is drawn is Playback, which is not evidence
 * that the Scenario works (`MP-15`).
 */
@Composable
fun ScenarioMapScreen(
    onSetUp: () -> Unit,
    viewModel: ScenarioMapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    BaseScreen(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is ScenarioMapEffect.OpenOverlay ->
                    if (!context.readPermissionStatus().ready) {
                        onSetUp()
                    } else {
                        OverlayService.open(context, effect.scenarioId)
                        // OV-26, as on the list: the Overlay is for use over another application.
                        context.findActivity()?.moveTaskToBack(true)
                    }
            }
        },
    ) { state, _ ->
        ImmersiveWhileShown()
        OverlayTheme {
            // The outer scaffold is the Activity's theme, so without this every Text and Icon here
            // that sets no colour inherits a light-theme ink onto this dark screen (MP-2).
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val scenario = state.scenario
                    val map = state.map
                    val frame = state.frame
                    when {
                        state.loading -> Unit
                        scenario == null || map == null || frame == null ->
                            Text(stringResource(R.string.map_not_found), Modifier.safeDrawingPadding().padding(16.dp))
                        else -> LoadedMap(scenario, state, frame, viewModel)
                    }
                }
            }
        }
    }
}

/** `MP-2`: the system bars are hidden, a swipe from the edge shows them for a moment, and leaving brings them back (`MP-14`). */
@Composable
private fun ImmersiveWhileShown() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val behaviour = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (behaviour != null) controller.systemBarsBehavior = behaviour
        }
    }
}

@Composable
private fun LoadedMap(
    scenario: Scenario,
    state: ScenarioMapUiState,
    frame: ScreenProfile,
    viewModel: ScenarioMapViewModel,
) {
    // MP-8: one call per frame; the clock and its speed are the ViewModel's, so that they can be tested.
    val tick by rememberUpdatedState(viewModel::tick)
    LaunchedEffect(state.playing) {
        if (!state.playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            tick(now - last)
            last = now
        }
    }
    val map = state.map ?: return
    val frameActions =
        FrameActions(
            onTapStep = viewModel::tapStep,
            onEmptyTap = viewModel::closeCard,
            onUserMovedCamera = viewModel::userMovedCamera,
        )
    val controls =
        ControlActions(viewModel::previous, viewModel::next, viewModel::togglePlay, viewModel::replay, viewModel::cycleSpeed)
    Column(Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TopBar(scenario, map.frame, viewModel::openInOverlay)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val (width, height) = phoneFrameSize(maxWidth, maxHeight, frame.widthPixels / frame.heightPixels.toFloat())
            PhoneFrame(scenario, map, frame, state, frameActions, Modifier.size(width, height))
        }
        PlaybackControls(state, scenario.steps.size, controls, Modifier.padding(bottom = 8.dp))
    }
}

/** `MP-2`: Back, the name, the caption with the run count as a counter and never as an arrow, and Open in Overlay. */
@Composable
private fun TopBar(
    scenario: Scenario,
    profile: ScreenProfile?,
    onOpenInOverlay: () -> Unit,
) {
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { back?.onBackPressed() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.global_back))
        }
        Column(Modifier.weight(1f)) {
            Text(scenario.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                caption(scenario, profile),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onOpenInOverlay) { Text(stringResource(R.string.map_open_overlay), maxLines = 1) }
    }
}

/** `MP-2`: the profile, and the run count as a counter and never as an arrow (`MP-4`). */
@Composable
private fun caption(
    scenario: Scenario,
    profile: ScreenProfile?,
): String {
    val screen =
        if (profile == null) {
            stringResource(R.string.scenario_orientation_auto)
        } else {
            val orientation =
                stringResource(
                    if (profile.orientation == ScreenOrientation.LANDSCAPE) {
                        R.string.scenario_orientation_landscape
                    } else {
                        R.string.scenario_orientation_portrait
                    },
                )
            stringResource(R.string.map_caption_profile, profile.widthPixels, profile.heightPixels, orientation)
        }
    val repeat =
        when (val count = scenario.runCount) {
            is RunCount.Times -> stringResource(R.string.map_caption_repeat, count.count).takeIf { count.count > 1 }
            RunCount.UntilStopped -> stringResource(R.string.map_caption_until_stopped)
        }
    return listOfNotNull(screen, repeat).joinToString("  ")
}

/** What the frame can ask of the screen (`MP-10`, `MP-13`). */
internal class FrameActions(
    val onTapStep: (List<Int>) -> Unit,
    val onEmptyTap: () -> Unit,
    val onUserMovedCamera: () -> Unit,
)
