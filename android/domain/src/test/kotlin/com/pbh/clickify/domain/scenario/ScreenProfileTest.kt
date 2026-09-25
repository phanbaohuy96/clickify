package com.pbh.clickify.domain.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenProfileTest {
    private val pixel =
        ScreenProfile(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 440,
            rotation = ScreenRotation.PORTRAIT,
        )

    @Test
    fun `an identical profile matches`() {
        assertTrue(pixel.matches(pixel.copy()))
        assertEquals(emptyList(), pixel.differencesFrom(pixel.copy()))
    }

    @Test
    fun `a rotated phone reports rotation and nothing else`() {
        val rotated = pixel.copy(rotation = ScreenRotation.LANDSCAPE_LEFT)

        assertEquals(listOf(ScreenProfileDifference.ROTATION), pixel.differencesFrom(rotated))
        assertFalse(pixel.matches(rotated))
    }

    @Test
    fun `every difference is reported, not just the first`() {
        val other = pixel.copy(widthPixels = 1440, heightPixels = 3200, densityDpi = 560)

        assertEquals(
            listOf(
                ScreenProfileDifference.WIDTH,
                ScreenProfileDifference.HEIGHT,
                ScreenProfileDifference.DENSITY,
            ),
            pixel.differencesFrom(other),
        )
    }

    @Test
    fun `a density change alone still blocks`() {
        // Same pixels, different dpi: the screen is the same size and everything drawn on it is not.
        assertFalse(pixel.matches(pixel.copy(densityDpi = 420)))
    }
}
