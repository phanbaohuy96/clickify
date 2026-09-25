package com.pbh.clickify.overlay.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OV-34: the arithmetic behind drag-to-expand, and the handoff to the body's own scroll.
 *
 * Tested here rather than on a device because the handoff is decided entirely by what `consume`
 * returns: reporting more than it used would swallow the scroll the body was owed, and reporting
 * less would scroll the body while the sheet was still growing. Both look like a stuck finger.
 */
class OverlaySheetStateTest {
    private fun state() = OverlaySheetState(peekHeight = 1000f, fullHeight = 2000f)

    @Test
    fun `it starts at the peek height`() {
        assertEquals(1000f, state().height)
    }

    @Test
    fun `dragging up grows the sheet by what the finger gave`() {
        val sheet = state()

        assertEquals(-300f, sheet.consume(-300f))
        assertEquals(1300f, sheet.height)
    }

    /**
     * The handoff, in one assertion.
     *
     * At full height there is no sheet left to gain, so nothing is consumed — and an unconsumed
     * delta is exactly what the body's scroll then receives.
     */
    @Test
    fun `a fully open sheet consumes nothing, which is what hands the scroll on`() {
        val sheet = state()
        sheet.consume(-1000f)

        assertEquals(2000f, sheet.height)
        assertEquals(0f, sheet.consume(-400f))
    }

    @Test
    fun `it takes only the part of a drag that fits`() {
        val sheet = state()

        assertEquals(-1000f, sheet.consume(-5000f))
        assertEquals(2000f, sheet.height)
    }

    /**
     * OV-37: landscape, expressed as the two heights being equal.
     *
     * The sheet is already as tall as the display, so there is nothing to expand into and every
     * pixel of the swipe is left for the body — which is the same handoff as portrait's second
     * phase, reached on the first pixel instead of after the first four hundred.
     */
    @Test
    fun `a sheet with nothing to expand into hands every pixel to the body`() {
        val side = OverlaySheetState(peekHeight = 1344f, fullHeight = 1344f)

        assertEquals(0f, side.consume(-500f))
        assertEquals(0f, side.consume(500f))
        assertEquals(1344f, side.height)
    }

    @Test
    fun `dragging down shrinks it no further than the peek height`() {
        val sheet = state()
        sheet.consume(-1000f)

        assertEquals(1000f, sheet.consume(5000f))
        assertEquals(1000f, sheet.height)
        assertEquals(0f, sheet.consume(200f))
    }
}
