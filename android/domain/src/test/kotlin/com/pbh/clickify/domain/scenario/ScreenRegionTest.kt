package com.pbh.clickify.domain.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenRegionTest {
    private val profile =
        ScreenProfile(widthPixels = 1_080, heightPixels = 2_400, densityDpi = 440, rotation = ScreenRotation.PORTRAIT)

    @Test
    fun `two corners make the same rectangle whichever way they were dragged`() {
        val forwards = ScreenRegion.between(ScreenPoint(100, 200), ScreenPoint(300, 500))
        val backwards = ScreenRegion.between(ScreenPoint(300, 500), ScreenPoint(100, 200))

        assertEquals(forwards, backwards)
        assertEquals(200, forwards.width)
        assertEquals(300, forwards.height)
    }

    @Test
    fun `a rectangle of no area is empty`() {
        assertTrue(ScreenRegion(10, 10, 10, 40).isEmpty)
        assertTrue(ScreenRegion(10, 10, 40, 10).isEmpty)
        assertTrue(!ScreenRegion(10, 10, 40, 40).isEmpty)
    }

    @Test
    fun `clamping leaves nothing outside the screen`() {
        val clamped = ScreenRegion(-50, -80, 5_000, 9_000).clampedInto(profile)

        assertEquals(ScreenRegion(0, 0, 1_080, 2_400), clamped)
    }

    /**
     * TP-9: the default Search region hugs the crop.
     *
     * Half the longer side, floored at 48 dp and capped at 160 dp. At a density of 3 that is
     * 144 and 480 pixels, and a 400-pixel-wide crop asks for 200 — inside both, so 200 it is.
     */
    @Test
    fun `the suggested region is half the longer side of padding`() {
        val crop = ScreenRegion(left = 300, top = 1_000, right = 700, bottom = 1_100)

        val padded = crop.paddedForSearch(profile, density = 3f)

        assertEquals(ScreenRegion(100, 800, 900, 1_300), padded)
    }

    @Test
    fun `a tiny crop still gets the floor of padding`() {
        val crop = ScreenRegion(left = 500, top = 1_000, right = 520, bottom = 1_020)

        val padded = crop.paddedForSearch(profile, density = 3f)

        // 10 pixels asked for, 48 dp × 3 = 144 given.
        assertEquals(500 - 144, padded.left)
        assertEquals(520 + 144, padded.right)
    }

    @Test
    fun `a huge crop is capped rather than padded to the horizon`() {
        val crop = ScreenRegion(left = 40, top = 200, right = 1_040, bottom = 2_200)

        val padded = crop.paddedForSearch(profile, density = 3f)

        // 1000 pixels asked for, 160 dp × 3 = 480 given, then clipped to the screen.
        assertEquals(0, padded.left)
        assertEquals(1_080, padded.right)
        assertEquals(0, padded.top)
        assertEquals(2_400, padded.bottom)
    }

    @Test
    fun `the suggested region never leaves the screen`() {
        val crop = ScreenRegion(left = 0, top = 0, right = 100, bottom = 100)

        val padded = crop.paddedForSearch(profile, density = 3f)

        assertEquals(0, padded.left)
        assertEquals(0, padded.top)
        assertTrue(padded.right <= profile.widthPixels)
        assertTrue(padded.bottom <= profile.heightPixels)
    }
}
