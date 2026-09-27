package com.pbh.clickify.domain.recognition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrayImageTest {
    @Test
    fun `a pixel is found by row and column`() {
        val image = GrayImage(2, 2, floatArrayOf(0f, 0.25f, 0.5f, 1f))

        assertEquals(0f, image[0, 0])
        assertEquals(0.25f, image[1, 0])
        assertEquals(0.5f, image[0, 1])
        assertEquals(1f, image[1, 1])
    }

    @Test
    fun `downsampling averages each cell`() {
        val image = GrayImage(2, 2, floatArrayOf(0f, 1f, 1f, 0f))

        val smaller = image.downsampled(2)

        assertEquals(1, smaller.width)
        assertEquals(1, smaller.height)
        assertEquals(0.5f, smaller[0, 0])
    }

    @Test
    fun `downsampling by one, or past nothing, changes nothing`() {
        val image = GrayImage(3, 3, FloatArray(9) { it / 9f })

        assertEquals(3, image.downsampled(1).width)
        assertEquals(3, image.downsampled(8).width)
    }

    @Test
    fun `a crop is the pixels inside the rectangle`() {
        val image = GrayImage(3, 2, floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f))

        val cropped = image.cropped(left = 1, top = 0, width = 2, height = 2)

        assertEquals(2, cropped.width)
        assertEquals(2, cropped.height)
        assertEquals(listOf(1f, 2f, 4f, 5f), cropped.pixels.toList())
    }

    /** TP-9: a Search region dragged past the edge is still a region, not an exception. */
    @Test
    fun `a crop is clipped to what is actually there`() {
        val image = GrayImage(2, 2, floatArrayOf(0f, 1f, 2f, 3f))

        val cropped = image.cropped(left = 1, top = 1, width = 99, height = 99)

        assertEquals(1, cropped.width)
        assertEquals(1, cropped.height)
        assertEquals(3f, cropped[0, 0])
    }

    @Test
    fun `a crop entirely outside the image is empty rather than negative`() {
        val image = GrayImage(2, 2, floatArrayOf(0f, 1f, 2f, 3f))

        assertTrue(image.cropped(left = 9, top = 9, width = 4, height = 4).isEmpty)
    }

    @Test
    fun `an image cannot hold one bigger than itself`() {
        val small = GrayImage(2, 2, FloatArray(4))
        val big = GrayImage(4, 4, FloatArray(16))

        assertTrue(small.cannotHold(big))
        assertTrue(!big.cannotHold(small))
    }
}
