package com.pbh.clickify.domain.recognition

/**
 * A single-channel greyscale image, and the only thing [TemplateMatcher] knows how to look at.
 *
 * A type of its own rather than a `Bitmap`, for two reasons that matter. The matcher touches a
 * pixel millions of times, so the pixels have to be one flat contiguous array rather than a
 * call into a platform object; and keeping Android out of it is what lets the whole of `TP-10`
 * to `TP-17` be tested on the JVM with images built by hand.
 *
 * [pixels] is brightness `0…1`, laid out row by row.
 */
class GrayImage(
    val width: Int,
    val height: Int,
    val pixels: FloatArray,
) {
    init {
        require(pixels.size == width * height) {
            "a ${width}x$height image needs ${width * height} pixels, not ${pixels.size}"
        }
    }

    operator fun get(
        x: Int,
        y: Int,
    ): Float = pixels[y * width + x]

    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /** True when this image is too small to hold [other] anywhere inside it. */
    fun cannotHold(other: GrayImage): Boolean = other.width > width || other.height > height

    /** Downscales by averaging each [factor] × [factor] cell. Returns itself when there is nothing to do. */
    fun downsampled(factor: Int): GrayImage {
        if (factor <= 1) return this
        val newWidth = width / factor
        val newHeight = height / factor
        if (newWidth <= 0 || newHeight <= 0) return this

        val result = FloatArray(newWidth * newHeight)
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                result[y * newWidth + x] = cellAverage(x * factor, y * factor, factor)
            }
        }
        return GrayImage(newWidth, newHeight, result)
    }

    private fun cellAverage(
        fromX: Int,
        fromY: Int,
        factor: Int,
    ): Float {
        var total = 0f
        for (offsetY in 0 until factor) {
            val row = (fromY + offsetY) * width + fromX
            for (offsetX in 0 until factor) {
                total += pixels[row + offsetX]
            }
        }
        return total / (factor * factor).toFloat()
    }

    /**
     * The part of this image inside [left, top, width, height], clipped to what is actually there.
     *
     * Used for the **Search region** (`TP-9`): searching a rectangle is the same code as searching
     * the screen, with a smaller haystack and an offset the caller adds back.
     */
    fun cropped(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): GrayImage {
        val fromX = left.coerceIn(0, this.width)
        val fromY = top.coerceIn(0, this.height)
        val toX = (left + width).coerceIn(fromX, this.width)
        val toY = (top + height).coerceIn(fromY, this.height)
        val cropWidth = toX - fromX
        val cropHeight = toY - fromY
        if (cropWidth <= 0 || cropHeight <= 0) return GrayImage(0, 0, FloatArray(0))

        val result = FloatArray(cropWidth * cropHeight)
        for (y in 0 until cropHeight) {
            pixels.copyInto(
                destination = result,
                destinationOffset = y * cropWidth,
                startIndex = (fromY + y) * this.width + fromX,
                endIndex = (fromY + y) * this.width + fromX + cropWidth,
            )
        }
        return GrayImage(cropWidth, cropHeight, result)
    }
}
