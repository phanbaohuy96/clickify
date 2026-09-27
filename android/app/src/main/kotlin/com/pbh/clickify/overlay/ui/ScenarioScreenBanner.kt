package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.scenario.ScreenOrientation
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.matches
import com.pbh.clickify.domain.scenario.orientation
import com.pbh.clickify.overlay.OverlayScreen

/**
 * SM-18: the screen a Scenario was measured on, and what to do when it is not this one.
 *
 * `SM-15` already refuses to run a Scenario against a screen it was not built for, and that refusal
 * arrives at the worst possible moment — after the user has pressed Start and is holding the phone
 * over whatever they meant to automate. This says the same thing while they are still editing, and
 * it carries the only way out of it.
 *
 * **There is no orientation picker, and that is on purpose.** A control that let someone choose
 * "landscape" while holding the phone in portrait would have to transform the points, and there is
 * no honest transform: rotating a coordinate is true about the display and false about the
 * application, whose buttons are somewhere else entirely once it has re-laid itself out
 * (ADR-0013). So the way to change a Scenario's orientation is to hold the phone that way — which
 * is the same thing aiming a Step already requires — and this is the button that then appears.
 */
@Composable
internal fun ScreenBanner(
    built: ScreenProfile?,
    screen: OverlayScreen?,
    onAskRebuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (built == null || screen == null || built.matches(screen.profile)) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.scenario_screen_mismatch_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    stringResource(
                        R.string.scenario_screen_mismatch_body,
                        built.describe(),
                        screen.profile.describe(),
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onAskRebuild) { Text(stringResource(R.string.scenario_rebuild)) }
        }
    }
}

/** "1344 × 2992 portrait" — the two numbers that decide whether a coordinate means anything. */
@Composable
internal fun ScreenProfile.describe(): String =
    stringResource(R.string.scenario_screen_size, widthPixels, heightPixels, orientation.label())

@Composable
internal fun ScreenOrientation.label(): String =
    stringResource(
        when (this) {
            ScreenOrientation.PORTRAIT -> R.string.scenario_orientation_portrait
            ScreenOrientation.LANDSCAPE -> R.string.scenario_orientation_landscape
        },
    )
