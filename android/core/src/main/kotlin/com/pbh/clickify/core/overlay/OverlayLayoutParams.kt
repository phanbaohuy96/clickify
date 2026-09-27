package com.pbh.clickify.core.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/**
 * The window attributes every Overlay window shares, and the ways they differ (OV-1 to OV-3).
 *
 * Built here rather than at each call site because one of these flags is load-bearing in a way
 * that is not obvious from its name — see [NEVER_FOCUSABLE].
 */
object OverlayLayoutParams {
    /**
     * OV-3. **Never remove this flag.**
     *
     * `setText` finds its field with `findFocus(FOCUS_INPUT)` (`GX-17`). An Overlay that can take
     * input focus therefore makes every `setText` Step write into Clickify instead of the
     * application being automated — and the failure looks like the other application's fault.
     */
    const val NEVER_FOCUSABLE = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    /** OV-2: a touch outside this window's bounds reaches whatever is underneath. */
    private const val PASS_THROUGH_OUTSIDE = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    /** Every touch passes through, including ones inside the bounds. For a window that only draws. */
    private const val PASS_THROUGH_ENTIRELY = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    /**
     * OV-31: measured against the display, not against what is left of it.
     *
     * By default a window is laid out inside the system bars, so its origin is below the status
     * bar and a y of 1496 lands at 1655 on a phone with a 159-pixel one. A **Gesture** is
     * dispatched in display coordinates, so a Marker placed that way is drawn six millimetres
     * below the point it claims to name (`SM-11`) — and the user aims by the Marker.
     *
     * This applies to the Marker windows alone. The control and the panel are things the user
     * reaches for rather than aims with, and they are better off inside the bars where nothing
     * covers them.
     */
    private const val IN_DISPLAY_COORDINATES =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /**
     * A small window the user interacts with: the floating control.
     *
     * [x] and [y] are where the user last left it (`OV-14`), as a `Gravity.TOP or Gravity.START`
     * offset so the value means the same thing on every screen size.
     *
     * **No `FLAG_BLUR_BEHIND`**, however much the control would suit one — see
     * [com.pbh.clickify.core.designsystem.OverlayGlass]. The blur costs this app every other
     * window's touch input, and there is a test below that says so.
     */
    fun floating(
        x: Int = 0,
        y: Int = 0,
    ): WindowManager.LayoutParams = positioned(x, y)

    /**
     * OV-27: one Marker's handle, as a window of its own.
     *
     * The reason this is not a region of one full-screen window is that Android has no public way
     * to say "this window is touchable **here** and nowhere else". A full-screen window either
     * takes every touch — which makes the phone unusable while Clickify is open — or takes none,
     * which is why placing Markers used to be a mode the user had to remember to leave. A window
     * per Marker is the only shape that gives both: each handle answers touches inside its own few
     * dozen pixels, and `FLAG_NOT_TOUCH_MODAL` sends everything else to the application
     * underneath.
     *
     * The cost is real and bounded: one window per drawn Marker, which is one per tap, two per
     * swipe, and up to two per contact of a multi-touch.
     */
    fun markerHandle(
        x: Int,
        y: Int,
    ): WindowManager.LayoutParams =
        positioned(x, y).apply {
            flags = flags or IN_DISPLAY_COORDINATES
        }

    /**
     * The display-sized layer the lines between paired Markers are drawn on (OV-8, OV-9, OV-31).
     *
     * Draws and nothing else. It is the handles that answer touches, so this one is
     * [PASS_THROUGH_ENTIRELY] at all times and can safely cover the whole screen.
     */
    fun markerLines(
        displayWidth: Int,
        displayHeight: Int,
    ): WindowManager.LayoutParams =
        base().apply {
            // Sized explicitly rather than MATCH_PARENT. The parent of an overlay window is the
            // space inside the system bars even when the window itself is laid out in display
            // coordinates, so MATCH_PARENT would start this layer below the status bar and draw
            // every line at the wrong end of every Marker.
            width = displayWidth
            height = displayHeight
            x = 0
            y = 0
            gravity = Gravity.TOP or Gravity.START
            flags = NEVER_FOCUSABLE or PASS_THROUGH_OUTSIDE or PASS_THROUGH_ENTIRELY or IN_DISPLAY_COORDINATES
        }

    /**
     * The panel (`OV-1`), anchored to the bottom edge where the thumb already is.
     *
     * [typing] is the **one** place in this application where [NEVER_FOCUSABLE] is dropped,
     * and `OV-20` is the requirement that says why it may be. A `setText` Step's string has to be
     * typed somewhere, and a window that cannot take input focus cannot open a keyboard — so the
     * flag goes while a text field in the panel holds the caret, and comes straight back when it
     * does not.
     *
     * What makes that safe is not this function but when it is called: the panel is closed before
     * a run can start, so the window `findFocus(FOCUS_INPUT)` would find during a `setText` Step
     * is never this one. `OverlayCoordinator` is where that is enforced.
     */
    fun panel(
        typing: Boolean,
        displayWidth: Int,
        bottomInsetPixels: Int,
    ): WindowManager.LayoutParams =
        base().apply {
            // OV-32: the panel owns the bottom edge of the **display**, not of what is left of it.
            //
            // A bottom-anchored overlay window is laid out inside the system bars, so it stopped
            // short of the screen by the height of the navigation bar — seventy-two pixels of
            // somebody else's application showing through beneath a sheet that is supposed to be
            // sitting on the edge of the phone.
            //
            // The flags alone do not fix it: `FLAG_LAYOUT_IN_SCREEN` does not move the parent
            // frame's bottom edge, so `Gravity.BOTTOM` still resolved to the top of the navigation
            // bar. It takes the negative offset as well, which `FLAG_LAYOUT_NO_LIMITS` is what
            // permits. The content pads itself back off the bar, as every bottom sheet does.
            width = displayWidth
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = 0
            y = -bottomInsetPixels
            gravity = Gravity.BOTTOM or Gravity.START
            flags = panelFlags(typing)
        }

    /**
     * OV-37: the same panel in landscape, as a full-height sheet against the end edge.
     *
     * A bottom sheet on a screen 1344 pixels tall is the wrong shape twice over. Its peek height
     * is a fifth of what it is in portrait, so nothing fits; and every field is stretched across
     * 2992 pixels, which is a text box the width of the phone holding two digits. Against the end
     * edge the sheet has the whole height to use and a readable width, and the rest of the screen
     * — the part the user is automating — stays visible beside it rather than underneath it.
     *
     * [endInsetPixels] is the navigation bar, which in landscape is on a side rather than the
     * bottom. The window is pushed out under it for the same reason the bottom sheet is pushed
     * under its own (`OV-32`): a sheet anchored to an edge should reach that edge. The content
     * pads itself back off the bar.
     */
    fun sidePanel(
        typing: Boolean,
        width: Int,
        displayHeight: Int,
        endInsetPixels: Int,
    ): WindowManager.LayoutParams =
        base().apply {
            this.width = width
            height = displayHeight
            x = -endInsetPixels
            y = 0
            gravity = Gravity.TOP or Gravity.END
            flags = panelFlags(typing)
        }

    /** OV-20: the panel's flags, and the one exception to [NEVER_FOCUSABLE] in this application. */
    private fun panelFlags(typing: Boolean): Int =
        if (typing) {
            PASS_THROUGH_OUTSIDE or IN_DISPLAY_COORDINATES
        } else {
            NEVER_FOCUSABLE or PASS_THROUGH_OUTSIDE or IN_DISPLAY_COORDINATES
        }

    /**
     * RD-1: the layer that swallows a touch so it can be recorded, covering the whole display.
     *
     * [listening] is the whole of pass-through recording (`RD-5`). While it is true this window
     * takes every touch, which is how a touch gets recorded at all. While it is false it takes
     * none — and it has to be false for the moment Clickify **re-emits** that touch to the
     * application underneath, because a `dispatchGesture` is delivered to the topmost window that
     * accepts touches and that window would otherwise be this one. Recording its own re-emission
     * is a loop with no end.
     *
     * In display coordinates for the same reason the Marker layer is (`OV-31`): a touch is
     * recorded by its raw screen position and replayed at that position, and the two have to be
     * the same number.
     */
    fun recordingLayer(
        displayWidth: Int,
        displayHeight: Int,
        listening: Boolean,
    ): WindowManager.LayoutParams =
        base().apply {
            width = displayWidth
            height = displayHeight
            x = 0
            y = 0
            gravity = Gravity.TOP or Gravity.START
            flags =
                if (listening) {
                    NEVER_FOCUSABLE or IN_DISPLAY_COORDINATES
                } else {
                    NEVER_FOCUSABLE or PASS_THROUGH_ENTIRELY or IN_DISPLAY_COORDINATES
                }
        }

    private fun positioned(
        x: Int,
        y: Int,
    ): WindowManager.LayoutParams =
        base().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            flags = NEVER_FOCUSABLE or PASS_THROUGH_OUTSIDE
            this.x = x
            this.y = y
        }

    private fun base(): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
        }
}
