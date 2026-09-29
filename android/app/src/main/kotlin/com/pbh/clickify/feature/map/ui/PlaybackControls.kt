package com.pbh.clickify.feature.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.map.PlaybackSpeed

/** What the controls can ask of Playback (`MP-11`). */
internal class ControlActions(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onReplay: () -> Unit,
    val onCycleSpeed: () -> Unit,
)

/**
 * `MP-11`: previous, play or pause, next, replay and speed, with where Playback is. At the end the play
 * button is the replay button, so replay is not offered twice.
 */
@Composable
internal fun PlaybackControls(
    state: ScenarioMapUiState,
    total: Int,
    actions: ControlActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.map_step_of, (state.currentStep ?: -1) + 1, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GlyphButton(Glyph.PREVIOUS, stringResource(R.string.step_previous), actions.onPrevious)
            if (state.finished) {
                ReplayButton(actions.onReplay)
            } else {
                GlyphButton(
                    if (state.playing) Glyph.PAUSE else Glyph.PLAY,
                    stringResource(if (state.playing) R.string.map_pause else R.string.map_play),
                    actions.onTogglePlay,
                )
            }
            GlyphButton(Glyph.NEXT, stringResource(R.string.step_next), actions.onNext)
            if (!state.finished) ReplayButton(actions.onReplay)
            val speed = state.speed.label()
            val description = stringResource(R.string.map_speed_description, speed)
            TextButton(onClick = actions.onCycleSpeed, modifier = Modifier.semantics { contentDescription = description }) {
                Text(stringResource(R.string.map_speed_label, speed))
            }
        }
    }
}

private fun PlaybackSpeed.label(): String = if (factor % 1.0 == 0.0) factor.toInt().toString() else factor.toString()

@Composable
private fun ReplayButton(onClick: () -> Unit) {
    Box(
        Modifier.size(CONTROL_DP.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.map_replay))
    }
}

internal enum class Glyph { PLAY, PAUSE, PREVIOUS, NEXT }

@Composable
private fun GlyphButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
) {
    val colour = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(CONTROL_DP.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(GLYPH_DP.dp)) { drawGlyph(glyph, colour) }
    }
}

/** The four shapes are drawn rather than taken from an icon set, which has no pause or skip in its core. */
private fun DrawScope.drawGlyph(
    glyph: Glyph,
    colour: Color,
) {
    val s = size.width

    fun triangle(
        x1: Float,
        x2: Float,
    ) = Path().apply {
        moveTo(s * x1, s * 0.1f)
        lineTo(s * x1, s * 0.9f)
        lineTo(s * x2, s * 0.5f)
        close()
    }
    when (glyph) {
        Glyph.PLAY -> drawPath(triangle(0.2f, 0.9f), colour)
        Glyph.PAUSE -> {
            drawRect(
                colour,
                androidx.compose.ui.geometry
                    .Offset(s * 0.2f, s * 0.15f),
                androidx.compose.ui.geometry
                    .Size(s * 0.2f, s * 0.7f),
            )
            drawRect(
                colour,
                androidx.compose.ui.geometry
                    .Offset(s * 0.6f, s * 0.15f),
                androidx.compose.ui.geometry
                    .Size(s * 0.2f, s * 0.7f),
            )
        }
        Glyph.PREVIOUS -> {
            drawRect(
                colour,
                androidx.compose.ui.geometry
                    .Offset(s * 0.1f, s * 0.1f),
                androidx.compose.ui.geometry
                    .Size(s * 0.14f, s * 0.8f),
            )
            drawPath(triangle(0.9f, 0.3f), colour)
        }
        Glyph.NEXT -> {
            drawRect(
                colour,
                androidx.compose.ui.geometry
                    .Offset(s * 0.76f, s * 0.1f),
                androidx.compose.ui.geometry
                    .Size(s * 0.14f, s * 0.8f),
            )
            drawPath(triangle(0.1f, 0.7f), colour)
        }
    }
}

private const val CONTROL_DP = 48f
private const val GLYPH_DP = 24f
