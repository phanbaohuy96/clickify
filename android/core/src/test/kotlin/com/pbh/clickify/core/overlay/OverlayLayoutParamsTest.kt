package com.pbh.clickify.core.overlay

import android.view.Gravity
import android.view.WindowManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OV-3 is the reason this file exists. The flag it checks has an innocent name and a consequence
 * three documents away, so it is asserted rather than trusted.
 */
class OverlayLayoutParamsTest {
    private fun WindowManager.LayoutParams.has(flag: Int) = flags and flag == flag

    @Test
    fun `no overlay window takes input focus`() {
        val windows =
            listOf(
                OverlayLayoutParams.floating(),
                OverlayLayoutParams.markerHandle(x = 10, y = 10),
                OverlayLayoutParams.markerLines(1_344, 2_992),
                OverlayLayoutParams.panel(typing = false, displayWidth = 1_344, bottomInsetPixels = 72),
            )

        windows.forEach {
            assertTrue(
                it.has(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
                "an overlay that takes focus sends every setText Step into Clickify",
            )
        }
    }

    @Test
    fun `the panel is the one exception, and only while a field holds the caret`() {
        // OV-20. The exception is narrow on purpose and is written down twice — here, and in the
        // coordinator, which closes the panel before a run can start.
        assertTrue(
            !OverlayLayoutParams
                .panel(
                    typing = true,
                    displayWidth = 1_344,
                    bottomInsetPixels = 72,
                ).has(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            "a window that cannot take focus cannot open a keyboard, and setText needs one",
        )
    }

    @Test
    fun `every overlay window is an application overlay`() {
        assertEquals(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            OverlayLayoutParams.floating().type,
        )
    }

    @Test
    fun `a touch outside the floating control reaches what is underneath`() {
        assertTrue(
            OverlayLayoutParams.floating().has(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL),
        )
    }

    /**
     * OV-27. A Marker handle is a window of its own so that it can answer touches without the
     * whole screen having to, and the full-screen layer left over draws only.
     */
    @Test
    fun `a marker handle answers touches and the layer behind it never does`() {
        val handle = OverlayLayoutParams.markerHandle(x = 0, y = 0)
        val lines = OverlayLayoutParams.markerLines(1_344, 2_992)

        assertTrue(!handle.has(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
        assertTrue(handle.has(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL))
        assertTrue(lines.has(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))
    }

    @Test
    fun `a marker handle is only as big as itself, and the line layer covers the display`() {
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, OverlayLayoutParams.markerHandle(0, 0).width)
        assertEquals(1_344, OverlayLayoutParams.markerLines(1_344, 2_992).width)
        assertEquals(2_992, OverlayLayoutParams.markerLines(1_344, 2_992).height)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, OverlayLayoutParams.floating().width)
    }

    /**
     * OV-31, and the reason it is asserted rather than trusted: without these flags a window's
     * origin is below the status bar, so a Marker at y = 1496 is drawn at 1655 while the Gesture
     * it describes still lands at 1496. The Marker is what the user aims with.
     */
    @Test
    fun `marker windows are measured against the display, and the control is not`() {
        val handle = OverlayLayoutParams.markerHandle(x = 0, y = 0)
        val lines = OverlayLayoutParams.markerLines(1_344, 2_992)

        listOf(handle, lines).forEach {
            assertTrue(it.has(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN))
            assertTrue(it.has(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS))
        }
        // The control is reached for rather than aimed with, and is better off inside the system
        // bars where nothing covers it and it cannot be dragged under the navigation bar.
        assertTrue(!OverlayLayoutParams.floating().has(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS))
    }

    /**
     * OV-32: the panel is the exception, and it is measured against the display for a third reason.
     *
     * It is neither aimed with nor merely reached for — it **owns the bottom edge**. Laid out
     * inside the system bars it stopped short of the screen by the height of the navigation bar,
     * and a strip of the application underneath showed through below a sheet that is supposed to
     * be sitting on the edge of the phone.
     */
    @Test
    fun `the panel reaches the bottom of the display and is sized against it`() {
        val params = OverlayLayoutParams.panel(typing = false, displayWidth = 1_344, bottomInsetPixels = 72)

        assertTrue(params.has(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN))
        assertTrue(params.has(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS))
        assertEquals(Gravity.BOTTOM or Gravity.START, params.gravity)
        // Sized explicitly rather than MATCH_PARENT: the parent of an overlay window is the space
        // inside the system bars even when the window is laid out past them.
        assertEquals(1_344, params.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.height)
    }

    /** OV-20 still holds across that change: focus is the one flag the panel may drop. */
    @Test
    fun `the panel keeps its display coordinates while it is being typed into`() {
        val typing = OverlayLayoutParams.panel(typing = true, displayWidth = 1_344, bottomInsetPixels = 72)

        assertTrue(typing.has(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS))
        assertTrue(!typing.has(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
    }

    /**
     * DS-6: **no Overlay window may ask for a blur behind it.** This is a regression test.
     *
     * The control would suit one, the platform offers one from API 31, and the test device
     * reports it as available. Turning it on makes WindowManager create a display-wide dim layer
     * whose occlusion mode is `BLOCK_UNTRUSTED`, and Android then drops every touch aimed at an
     * untrusted window beneath it — which is every other window this application owns. It was
     * measured: with the blur on, the panel, the Markers and the recording layer stopped
     * answering touches entirely, and `InputDispatcher` named the dim layer as the reason.
     *
     * The floating control kept working throughout, because it sits above the layer. That is what
     * makes this worth a test rather than a comment: the window a developer is looking at while
     * they add the blur is the one window that does not break.
     */
    @Test
    fun `no overlay window asks for a blur behind it`() {
        val windows =
            listOf(
                OverlayLayoutParams.floating(),
                OverlayLayoutParams.panel(typing = false, displayWidth = 1_344, bottomInsetPixels = 72),
                OverlayLayoutParams.markerHandle(x = 0, y = 0),
                OverlayLayoutParams.markerLines(1_344, 2_992),
                OverlayLayoutParams.recordingLayer(1_344, 2_992, listening = true),
                OverlayLayoutParams.sidePanel(typing = false, width = 1_100, displayHeight = 1_344, endInsetPixels = 0),
            )

        windows.forEach { assertTrue(!it.has(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)) }
    }

    /** OV-32: the negative offset is the half that the flags alone could not do. */
    @Test
    fun `the panel is pushed down past the navigation bar`() {
        assertEquals(-72, OverlayLayoutParams.panel(typing = false, displayWidth = 1_344, bottomInsetPixels = 72).y)
    }

    /**
     * OV-37: the landscape panel, which is the same window against a different edge.
     *
     * A bottom sheet on a screen 1344 pixels tall has a peek height a fifth of what it has in
     * portrait, and every field in it stretched across 2992 pixels. Against the end edge it has
     * the display's whole height and a width a form can be read at.
     */
    @Test
    fun `the side panel is full height against the end edge`() {
        val params = OverlayLayoutParams.sidePanel(typing = false, width = 1_100, displayHeight = 1_344, endInsetPixels = 72)

        assertEquals(1_100, params.width)
        assertEquals(1_344, params.height)
        assertEquals(Gravity.TOP or Gravity.END, params.gravity)
        assertEquals(-72, params.x)
        assertEquals(0, params.y)
    }

    @Test
    fun `the side panel obeys every rule the bottom one does`() {
        val side = OverlayLayoutParams.sidePanel(typing = false, width = 1_100, displayHeight = 1_344, endInsetPixels = 0)
        val typing = OverlayLayoutParams.sidePanel(typing = true, width = 1_100, displayHeight = 1_344, endInsetPixels = 0)

        assertTrue(side.has(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
        assertTrue(side.has(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL))
        assertTrue(side.has(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS))
        assertTrue(!side.has(WindowManager.LayoutParams.FLAG_BLUR_BEHIND))
        // OV-20, the one exception, and it is the same exception on both edges.
        assertTrue(!typing.has(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
    }

    @Test
    fun `a marker handle sits where its marker is`() {
        val params = OverlayLayoutParams.markerHandle(x = 540, y = 1_820)

        assertEquals(540, params.x)
        assertEquals(1_820, params.y)
        assertEquals(Gravity.TOP or Gravity.START, params.gravity)
    }

    @Test
    fun `the control remembers where it was left`() {
        val params = OverlayLayoutParams.floating(x = 120, y = 640)

        assertEquals(120, params.x)
        assertEquals(640, params.y)
    }
}
