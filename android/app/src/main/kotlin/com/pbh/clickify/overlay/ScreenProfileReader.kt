package com.pbh.clickify.overlay

import android.content.Context
import android.view.Surface
import android.view.WindowManager
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation

/**
 * The Screen profile in force right now (SM-13).
 *
 * Uses the **full** window metrics rather than the bounds left over after insets: a Gesture reaches
 * the whole display, including whatever a navigation bar is sitting on, so a coordinate has to mean
 * something there too.
 *
 * **Must be called on a visual Context** — an Activity, or the window context `OverlayService`
 * creates. A Service's own Context is not associated with a display, and `Context.display` does not
 * return null there, it throws `UnsupportedOperationException`. That is how this was found: not by
 * a test, but by the process dying on the first tap that reached it on an emulator.
 */
fun Context.currentScreenProfile(): ScreenProfile {
    val windowManager = getSystemService(WindowManager::class.java)
    val bounds = windowManager.maximumWindowMetrics.bounds
    val display = display
    return ScreenProfile(
        widthPixels = bounds.width(),
        heightPixels = bounds.height(),
        densityDpi = resources.configuration.densityDpi,
        rotation =
            when (display?.rotation) {
                Surface.ROTATION_90 -> ScreenRotation.LANDSCAPE_LEFT
                Surface.ROTATION_180 -> ScreenRotation.PORTRAIT_UPSIDE_DOWN
                Surface.ROTATION_270 -> ScreenRotation.LANDSCAPE_RIGHT
                else -> ScreenRotation.PORTRAIT
            },
    )
}
