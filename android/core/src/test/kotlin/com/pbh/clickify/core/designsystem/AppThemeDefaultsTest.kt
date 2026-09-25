package com.pbh.clickify.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * DS-2 and DS-4, which are decisions rather than preferences and are therefore asserted.
 *
 * The Overlay is drawn on top of something it does not own, so it cannot borrow contrast from the
 * background. Everything here exists because of that one sentence.
 */
class AppThemeDefaultsTest {
    @Test
    fun `the overlay is dark at every hour of the day`() {
        // DS-2. A light control over a dark game is a white slab; a light control over a white
        // form disappears. The Overlay does not follow the system, and this is where that is kept.
        // Compared field by field: Material's ColorScheme is not a value type.
        assertEquals(AppThemeDefaults.dark().colorScheme.surface, AppThemeDefaults.overlay().colorScheme.surface)
        assertEquals(AppThemeDefaults.dark().colorScheme.onSurface, AppThemeDefaults.overlay().colorScheme.onSurface)
        assertNotEquals(AppThemeDefaults.light().colorScheme.surface, AppThemeDefaults.overlay().colorScheme.surface)
    }

    @Test
    fun `no surface is pure black`() {
        // A true black reads as a hole cut in the screen rather than as a surface, and on an OLED
        // it is indistinguishable from the pixels being off.
        val overlay = AppThemeDefaults.overlay().colorScheme

        listOf(overlay.surface, overlay.background, overlay.surfaceContainer).forEach {
            assertNotEquals(Color.Black, it)
        }
    }

    @Test
    fun `the accent is the same hue in both schemes, at two depths`() {
        // DS-2: one accent. The light scheme darkens it to keep contrast on white rather than
        // reaching for a second colour.
        val dark = AppThemeDefaults.dark().colorScheme.primary
        val light = AppThemeDefaults.light().colorScheme.primary

        assertTrue(dark.red > dark.blue && dark.green > dark.blue, "the dark accent is amber")
        assertTrue(light.red > light.blue && light.green > light.blue, "so is the light one")
        assertTrue(light.luminance() < dark.luminance(), "and the light scheme's is the deeper")
    }

    @Test
    fun `the overlay outline sits between its own surface and white`() {
        // DS-4: the hairline is the only thing giving an Overlay window an edge over a dark
        // wallpaper, and it has to stay visible over a white page as well. A value close to either
        // end would disappear at that end.
        val scheme = AppThemeDefaults.overlay().colorScheme

        assertTrue(scheme.outline.luminance() - scheme.surface.luminance() > 0.15f, "visible over ink")
        assertTrue(1f - scheme.outline.luminance() > 0.35f, "and still visible over paper")
    }

    @Test
    fun `names and sentences are set in different faces`() {
        // DS-1: a display face for what labels a control, a text face for what is read as a
        // sentence. One family doing both was the thing being replaced.
        val typography = AppThemeDefaults.overlay().typography

        assertNotEquals(typography.headlineMedium.fontFamily, typography.bodyMedium.fontFamily)
        assertEquals(typography.headlineMedium.fontFamily, typography.labelMedium.fontFamily)
        assertEquals(typography.bodyMedium.fontFamily, typography.labelSmall.fontFamily)
    }

    @Test
    fun `the radius tightens as things get smaller`() {
        // DS-3: a chip inside a panel inside a rounded window, all with the same corner, reads as
        // a mistake rather than as a system.
        val shapes = AppThemeDefaults.light().shapes

        assertNotEquals(shapes.extraSmall, shapes.large)
    }

    @Test
    fun `the shared tokens survive the redesign`() {
        val config = AppThemeDefaults.light()

        assertEquals(48.dp, config.decoration.minTouchTarget)
        assertEquals(18.dp, config.decoration.loadingIndicatorSize)
        assertEquals(2.dp, config.decoration.loadingIndicatorStrokeWidth)
    }
}

/** Rec. 709 relative luminance, which is close enough for "is this lighter than that". */
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
