package com.pbh.clickify.feature.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.pbh.clickify.feature.map.ui.placeBeside
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MP-6: a floating box goes beside its anchor, never over it, and never out of the frame. */
class PlaceBesideTest {
    private val bounds = Size(400f, 800f)
    private val box = Size(200f, 100f)

    private fun overlaps(
        at: Offset,
        size: Size,
        anchor: Rect,
    ) = Rect(at, size).overlaps(anchor)

    @Test
    fun `it goes to the right when that fits`() {
        val anchor = Rect(20f, 300f, 60f, 340f)
        val at = placeBeside(anchor, 16f, box, bounds)
        assertEquals(Offset(76f, 320f), at)
    }

    @Test
    fun `it goes to the left when the right does not fit`() {
        val anchor = Rect(300f, 300f, 340f, 340f)
        val at = placeBeside(anchor, 16f, box, bounds)
        assertEquals(84f, at.x)
        assertFalse(overlaps(at, box, anchor))
    }

    @Test
    fun `when neither side fits it goes wholly below the anchor`() {
        val anchor = Rect(100f, 300f, 300f, 340f)
        val at = placeBeside(anchor, 16f, box, bounds)
        assertEquals(356f, at.y)
        assertFalse(overlaps(at, box, anchor))
    }

    @Test
    fun `when neither side and no room below fit it goes wholly above`() {
        val anchor = Rect(100f, 700f, 300f, 740f)
        val at = placeBeside(anchor, 16f, box, bounds)
        assertEquals(584f, at.y)
        assertFalse(overlaps(at, box, anchor))
    }

    @Test
    fun `a box larger than the frame is clamped and does not crash`() {
        val at = placeBeside(Rect(10f, 10f, 50f, 50f), 16f, Size(900f, 1000f), bounds)
        assertEquals(Offset(0f, 0f), at)
        assertTrue(at.x >= 0f && at.y >= 0f)
    }
}
