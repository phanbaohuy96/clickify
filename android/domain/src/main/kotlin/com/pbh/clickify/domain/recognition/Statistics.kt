package com.pbh.clickify.domain.recognition

import kotlin.math.max
import kotlin.math.sqrt

/**
 * The mean and spread of a patch of pixels, and the correlation that is computed from them.
 *
 * Its own file because [TemplateMatcher] is about *where to look* while this is about *how alike
 * two patches are* — and because the two together were more functions than one object may hold.
 */
internal data class Statistics(
    val mean: Double,
    val deviation: Double,
)

/** The population standard deviation, which is what `TP-11` means by a Template's contrast. */
fun standardDeviation(pixels: FloatArray): Float = (statistics(pixels).deviation / sqrt(max(1, pixels.size).toDouble())).toFloat()

internal fun statistics(pixels: FloatArray): Statistics {
    var total = 0.0
    pixels.forEach { total += it }
    val mean = total / pixels.size
    var variance = 0.0
    pixels.forEach { variance += (it - mean) * (it - mean) }
    return Statistics(mean, sqrt(variance))
}

/**
 * Normalised cross-correlation at one position.
 *
 * Accumulating in `Double` rather than `Float` is **not** micro-tuning (`TP-14`). The form
 * `Σh² − n·h̄²` suffers **catastrophic cancellation**: two large nearly equal numbers are
 * subtracted and the small difference left loses most of its significant digits. At `Float`
 * precision, over a **Template** of some 15,000 pixels, the error reaches ~2·10⁻³ — enough for a
 * score to come out above 1.0, which is impossible.
 *
 * Why it matters here: two nearly identical buttons — an everyday thing in a game — really differ
 * by about 4·10⁻⁵. `Float` noise is forty times that gap, so the matcher would pick between right
 * and wrong **at random**. It did, on macOS, and this is the comment that came out of finding it.
 */
internal fun correlation(
    template: GrayImage,
    templateStatistics: Statistics,
    haystack: GrayImage,
    originX: Int,
    originY: Int,
): Double {
    var windowTotal = 0.0
    var windowSquareTotal = 0.0
    var crossTotal = 0.0

    for (y in 0 until template.height) {
        val haystackRow = (originY + y) * haystack.width + originX
        val templateRow = y * template.width
        for (x in 0 until template.width) {
            val haystackValue = haystack.pixels[haystackRow + x].toDouble()
            windowTotal += haystackValue
            windowSquareTotal += haystackValue * haystackValue
            crossTotal += haystackValue * template.pixels[templateRow + x].toDouble()
        }
    }

    val count = (template.width * template.height).toDouble()
    val windowMean = windowTotal / count
    val windowVariance = windowSquareTotal - count * windowMean * windowMean
    if (windowVariance <= 0.0) return 0.0

    val denominator = sqrt(windowVariance) * templateStatistics.deviation
    if (denominator <= 0.0) return 0.0
    return (crossTotal - count * windowMean * templateStatistics.mean) / denominator
}
