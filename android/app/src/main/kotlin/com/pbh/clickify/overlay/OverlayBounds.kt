package com.pbh.clickify.overlay

import android.content.Context
import android.view.WindowInsets
import android.view.WindowManager

/**
 * How much room an ordinary Overlay window has, as opposed to how large the display is (`OV-31`).
 *
 * The two are not the same and the difference is not cosmetic. A window laid out the default way
 * has its origin **below the status bar** and its bottom **above the navigation bar**, so on the
 * emulator this is 1344×2761 where [currentScreenProfile] reports 1344×2992. Placing the floating
 * control by the display's numbers puts it 159 pixels lower than intended and lets it be dragged
 * under the navigation bar.
 *
 * Marker windows do not use this: they are measured against the display on purpose, because the
 * point a Marker names is the point a Gesture will touch.
 */
data class OverlayBounds(
    val width: Int,
    val height: Int,
    /**
     * OV-32: the navigation bar, in pixels, for the one window that is laid out past it.
     *
     * The panel owns the bottom edge of the **display**, so it is the panel's own content that has
     * to keep its buttons off the gesture bar. Read from the window metrics rather than from
     * Compose's `WindowInsets`, because an overlay window with `FLAG_LAYOUT_NO_LIMITS` is not
     * reliably dispatched insets and a zero here would put Save under the user's thumb.
     */
    val bottomInset: Int = 0,
    /** OV-37: the status bar, for the landscape panel, which is laid out past it. */
    val topInset: Int = 0,
    /**
     * OV-37: the navigation bar when it is on a side rather than the bottom.
     *
     * Read as `right` rather than as `end`: these are window-manager insets, which are absolute.
     * The panel that uses it is laid out with `Gravity.END`, so on a right-to-left phone the two
     * would name different edges — and the honest fix is to look at the layout direction there,
     * not to pretend this number already has.
     */
    val rightInset: Int = 0,
)

fun Context.overlayBounds(): OverlayBounds {
    val metrics = getSystemService(WindowManager::class.java).maximumWindowMetrics
    val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
    return OverlayBounds(
        width = (metrics.bounds.width() - bars.left - bars.right).coerceAtLeast(1),
        height = (metrics.bounds.height() - bars.top - bars.bottom).coerceAtLeast(1),
        bottomInset = bars.bottom,
        topInset = bars.top,
        rightInset = bars.right,
    )
}
