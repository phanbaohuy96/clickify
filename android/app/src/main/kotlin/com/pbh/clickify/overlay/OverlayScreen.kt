package com.pbh.clickify.overlay

import android.content.Context
import com.pbh.clickify.domain.scenario.ScreenOrientation
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.orientation

/**
 * The screen the Overlay is drawing on **right now** (`OV-37`).
 *
 * Both halves at once, because every window this application owns needs one or the other and they
 * have to describe the same moment: [profile] is the display a **Gesture** lands on, [bounds] is
 * what is left of it after the system bars. Reading them separately is how a panel ends up sized
 * for a screen the control is no longer on.
 *
 * Held in [OverlayUiState] rather than read from a `Context` inside a Composable, and that is the
 * whole point of the class. An Overlay window survives a rotation — nothing recreates it — so a
 * `remember(context)` around a screen measurement keeps the size the phone had when the window was
 * attached, forever.
 */
data class OverlayScreen(
    val profile: ScreenProfile,
    val bounds: OverlayBounds,
) {
    val landscape: Boolean get() = profile.orientation == ScreenOrientation.LANDSCAPE
}

fun Context.overlayScreen(): OverlayScreen = OverlayScreen(profile = currentScreenProfile(), bounds = overlayBounds())
