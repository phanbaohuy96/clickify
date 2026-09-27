package com.pbh.clickify.domain.recognition

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TP-10 to TP-17, which is the part of recognition that can be wrong without anyone noticing.
 *
 * Every image here is built by hand rather than loaded, which is the whole reason [GrayImage] is
 * not a `Bitmap`.
 */
class TemplateMatcherTest {
    private fun noise(
        width: Int,
        height: Int,
        seed: Int = 7,
    ): GrayImage {
        val random = Random(seed)
        return GrayImage(width, height, FloatArray(width * height) { random.nextFloat() })
    }

    private fun GrayImage.with(
        patch: GrayImage,
        atX: Int,
        atY: Int,
    ): GrayImage {
        val copy = pixels.copyOf()
        for (y in 0 until patch.height) {
            for (x in 0 until patch.width) {
                copy[(atY + y) * width + atX + x] = patch[x, y]
            }
        }
        return GrayImage(width, height, copy)
    }

    @Test
    fun `a template is found where it was put`() {
        val template = noise(12, 9, seed = 1)
        val haystack = noise(120, 90, seed = 2).with(template, atX = 40, atY = 30)

        val match = assertNotNull(TemplateMatcher.bestMatch(template, haystack))

        assertEquals(40, match.x)
        assertEquals(30, match.y)
        assertTrue(match.score > 0.999, "an exact copy should correlate at 1, not ${match.score}")
    }

    @Test
    fun `a template that is not there scores low`() {
        val template = noise(12, 9, seed = 1)
        val haystack = noise(120, 90, seed = 2)

        val match = assertNotNull(TemplateMatcher.bestMatch(template, haystack))

        assertTrue(match.score < 0.9, "unrelated noise correlated at ${match.score}")
    }

    /** TP-14: `Float` accumulation lets this exceed 1, which is mathematically impossible. */
    @Test
    fun `a score never goes above one`() {
        val template = noise(40, 40, seed = 3)
        val haystack = noise(200, 200, seed = 4).with(template, atX = 80, atY = 80)

        val match = assertNotNull(TemplateMatcher.bestMatch(template, haystack))

        assertTrue(match.score <= 1.0 + 1e-9, "score was ${match.score}")
    }

    @Test
    fun `a flat template matches nothing, because correlation is not defined for it`() {
        val template = GrayImage(4, 4, FloatArray(16) { 0.5f })
        val haystack = noise(40, 40)

        assertNull(TemplateMatcher.bestMatch(template, haystack))
    }

    @Test
    fun `a template bigger than the haystack is refused rather than clamped`() {
        assertNull(TemplateMatcher.bestMatch(noise(40, 40), noise(10, 10)))
        assertNull(TemplateMatcher.bestMatch(GrayImage(0, 0, FloatArray(0)), noise(10, 10)))
    }

    /**
     * TP-16: two identical patches, and the answer has to be the same on every run.
     *
     * Topmost then leftmost, which is what "keep the first spot found" means when the scan walks
     * rows outwards from the origin.
     */
    @Test
    fun `an exact tie goes to the topmost, then the leftmost`() {
        val template = noise(8, 8, seed = 5)
        val haystack =
            noise(80, 80, seed = 6)
                .with(template, atX = 50, atY = 10)
                .with(template, atX = 10, atY = 10)

        val match = assertNotNull(TemplateMatcher.bestMatch(template, haystack))

        assertEquals(10, match.x)
        assertEquals(10, match.y)
    }

    /**
     * TP-11, and the bug it exists to hold shut.
     *
     * A one-pixel checker pattern averages to a flat grey at any downscale, so the level chosen
     * for it has to be 1 — at 8 the coarse template is a featureless square and the refinement
     * window does not even contain the right position.
     */
    @Test
    fun `a template of fine detail is not downscaled into a flat square`() {
        val checker = GrayImage(32, 32, FloatArray(32 * 32) { if ((it / 32 + it % 32) % 2 == 0) 0f else 1f })

        // A haystack small enough that native resolution is inside the ceiling, so the choice is
        // about contrast and nothing else. TP-13 is what happens when it is not, and is below.
        assertEquals(1, TemplateMatcher.downsampleFactor(checker, noise(100, 100)))
    }

    /** TP-11 the other way: a broad shape keeps its contrast, so the cheap level is taken. */
    @Test
    fun `a template of broad shapes is downscaled as far as it can afford`() {
        val halves = GrayImage(64, 64, FloatArray(64 * 64) { if (it % 64 < 32) 0f else 1f })

        assertTrue(TemplateMatcher.downsampleFactor(halves, noise(400, 400)) > 1)
    }

    /** TP-13: when contrast and the ceiling disagree, the ceiling wins. */
    @Test
    fun `the cost ceiling wins over keeping contrast`() {
        val checker = GrayImage(64, 64, FloatArray(64 * 64) { if ((it / 64 + it % 64) % 2 == 0) 0f else 1f })
        val display = noise(1_344, 2_992)

        val factor = TemplateMatcher.downsampleFactor(checker, display)

        assertTrue(factor > 1, "a full-screen scan at native resolution is the thing the pyramid exists to avoid")
    }

    @Test
    fun `a flat template has no contrast to preserve and is left alone`() {
        val flat = GrayImage(16, 16, FloatArray(256) { 0.5f })

        assertEquals(1, TemplateMatcher.downsampleFactor(flat, noise(200, 200)))
    }

    /**
     * TP-12: the coarse level's best spot is often not the right one.
     *
     * The decoy has every 2 × 2 cell's pixels rotated, so **every** downscaled level sees the two
     * as identical and the coarse scan cannot tell them apart. It is also earlier in scan order,
     * so keeping one candidate would keep the wrong one and refine around it. Only refining
     * several finds the real patch.
     *
     * Note what this test could not be: correlation is normalised, so a decoy that is the
     * template scaled and shifted in brightness is not a decoy at all — it scores exactly 1.
     */
    @Test
    fun `several coarse candidates are refined, not just the best one`() {
        val template = noise(24, 24, seed = 11)
        val decoy = template.withEachCellRotated()
        val haystack =
            noise(300, 300, seed = 12)
                .with(decoy, atX = 20, atY = 20)
                .with(template, atX = 200, atY = 200)

        val match = assertNotNull(TemplateMatcher.bestMatch(template, haystack))

        assertEquals(200, match.x)
        assertEquals(200, match.y)
    }

    /** The same pixels in every 2 × 2 cell, in a different order: identical at every coarse level. */
    private fun GrayImage.withEachCellRotated(): GrayImage {
        val rotated = pixels.copyOf()
        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                val topLeft = this[x, y]
                rotated[y * width + x] = this[x + 1, y]
                rotated[y * width + x + 1] = this[x + 1, y + 1]
                rotated[(y + 1) * width + x + 1] = this[x, y + 1]
                rotated[(y + 1) * width + x] = topLeft
            }
        }
        return GrayImage(width, height, rotated)
    }

    @Test
    fun `standard deviation is zero for a flat array`() {
        assertEquals(0f, standardDeviation(FloatArray(16) { 0.3f }))
        assertTrue(standardDeviation(floatArrayOf(0f, 1f, 0f, 1f)) > 0f)
    }
}
