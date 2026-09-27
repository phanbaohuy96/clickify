package com.pbh.clickify.domain.recognition

import kotlin.math.abs
import kotlin.math.max

/**
 * Finds a **Template** inside a larger image with pyramid normalised cross-correlation (`TP-10`).
 *
 * A port of macOS's `TemplateMatcher.swift`, with one part deliberately left behind: matching at a
 * second scale. Android has one display and `SM-15` refuses to run a **Scenario** against a
 * **Screen profile** that is not the one it was built on, so a **Template** is always searched for
 * at the size it was cropped at — `TP-18` says this at length.
 *
 * Why a pyramid at all: a 120 × 48 **Template** on a 1344 × 2992 screen is about 4 million
 * positions × 5,760 pixels ≈ **23 billion** comparisons for one search. Scanning coarsely at a
 * downscaled level and refining around the candidates brings that down by three orders of
 * magnitude.
 */
object TemplateMatcher {
    /** Top-left corner of the matched area, in pixels of the haystack, and a score of `0…1`. */
    data class Match(
        val x: Int,
        val y: Int,
        val score: Double,
    )

    /** Refinement radius around each coarse candidate, in pixels at native resolution. */
    const val REFINEMENT_RADIUS = 8

    /**
     * How many coarse candidates are kept for refinement (`TP-12`).
     *
     * One is not enough. Real interfaces are full of repeated detail — a row of identical buttons,
     * a table's rules — so the best spot at the coarse level is often not the right one at native
     * resolution.
     */
    const val COARSE_CANDIDATE_LIMIT = 8

    /**
     * The share of its contrast the downscaled **Template** has to keep (`TP-11`).
     *
     * Downscaling too far **wipes out** high-frequency detail — text, one-pixel borders, small
     * checker patterns — turning a coarse template into a nearly flat array. The coarse scan then
     * points somewhere random and the refinement window does not even contain the right position.
     */
    const val CONTRAST_RETENTION = 0.5f

    /**
     * Ceiling on the comparisons one coarse scan may cost (`TP-13`).
     *
     * In direct tension with [CONTRAST_RETENTION]: keeping contrast wants less downscaling, and
     * scanning wants more. When no level satisfies both, **the ceiling wins** — a less accurate
     * match beats freezing the phone for seconds on one **Step**, and narrowing the **Search
     * region** is how the user buys the accuracy back.
     */
    const val COARSE_SCAN_BUDGET = 40_000_000

    fun bestMatch(
        template: GrayImage,
        haystack: GrayImage,
    ): Match? {
        if (template.isEmpty || haystack.isEmpty || haystack.cannotHold(template)) return null

        val factor = downsampleFactor(template, haystack)
        val candidates = coarseCandidates(template, haystack, factor)
        return if (candidates.isEmpty()) {
            scan(template, haystack, null)
        } else {
            refine(template, haystack, factor, candidates)
        }
    }

    /**
     * The cheapest downscale level that still keeps the **Template**'s contrast, within the budget.
     *
     * Not chosen by size, as intuition first suggests: a 32 × 32 **Template** can still be a
     * three-pixel checker pattern, and averaging it 8 × 8 leaves a flat grey square.
     */
    fun downsampleFactor(
        template: GrayImage,
        haystack: GrayImage,
    ): Int {
        val fullContrast = standardDeviation(template.pixels)
        if (fullContrast <= 0f) return 1

        // Descending, because the largest factor is the cheapest one.
        val usable =
            LEVELS.filter {
                it == 1 || (template.width / it >= MINIMUM_COARSE_SIDE && template.height / it >= MINIMUM_COARSE_SIDE)
            }
        val affordable = usable.filter { coarseScanCost(template, haystack, it) <= COARSE_SCAN_BUDGET }
        val preserving =
            affordable.firstOrNull {
                it == 1 || standardDeviation(template.downsampled(it).pixels) >= fullContrast * CONTRAST_RETENTION
            }
        return preserving ?: affordable.firstOrNull() ?: usable.lastOrNull() ?: 1
    }

    private fun coarseCandidates(
        template: GrayImage,
        haystack: GrayImage,
        factor: Int,
    ): List<Match> {
        if (factor <= 1) return emptyList()
        val coarseTemplate = template.downsampled(factor)
        val coarseHaystack = haystack.downsampled(factor)
        if (coarseHaystack.cannotHold(coarseTemplate)) return emptyList()
        return topCandidates(coarseTemplate, coarseHaystack, COARSE_CANDIDATE_LIMIT)
    }

    private fun refine(
        template: GrayImage,
        haystack: GrayImage,
        factor: Int,
        candidates: List<Match>,
    ): Match? {
        val radius = REFINEMENT_RADIUS + factor
        var best: Match? = null
        candidates.forEach { candidate ->
            val centreX = candidate.x * factor
            val centreY = candidate.y * factor
            val region =
                Region(
                    minX = max(0, centreX - radius),
                    minY = max(0, centreY - radius),
                    maxX = minOf(haystack.width - template.width, centreX + radius),
                    maxY = minOf(haystack.height - template.height, centreY + radius),
                )
            val refined = scan(template, haystack, region)
            if (refined != null && refined.score > (best?.score ?: Double.NEGATIVE_INFINITY)) best = refined
        }
        return best
    }

    /**
     * Separated correlation peaks at the coarse level (`TP-12`).
     *
     * Non-maximum suppression at half the **Template**'s size; without it every candidate would
     * pile up around one peak and keeping several of them would buy nothing.
     */
    private fun topCandidates(
        template: GrayImage,
        haystack: GrayImage,
        limit: Int,
    ): List<Match> {
        val templateStatistics = statistics(template.pixels)
        if (templateStatistics.deviation <= 0.0) return emptyList()

        val suppression = Match(max(1, template.width / 2), max(1, template.height / 2), 0.0)
        val candidates = mutableListOf<Match>()

        for (originY in 0..haystack.height - template.height) {
            for (originX in 0..haystack.width - template.width) {
                val score = correlation(template, templateStatistics, haystack, originX, originY)
                candidates.offer(Match(originX, originY, score), suppression, limit)
            }
        }
        return candidates
    }

    /**
     * Keeps this position only if it beats the peak it belongs to, or starts a new peak.
     *
     * [suppression] carries the half-template size in its x and y; it is a [Match] rather than a
     * pair only so that this file has one shape for "a position and a score" instead of two.
     */
    private fun MutableList<Match>.offer(
        candidate: Match,
        suppression: Match,
        limit: Int,
    ) {
        val near = indexOfFirst { abs(it.x - candidate.x) < suppression.x && abs(it.y - candidate.y) < suppression.y }
        if (near >= 0) {
            if (candidate.score > this[near].score) this[near] = candidate
            return
        }
        add(candidate)
        sortByDescending { it.score }
        if (size > limit) removeAt(lastIndex)
    }

    private data class Region(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
    )

    private fun scan(
        template: GrayImage,
        haystack: GrayImage,
        region: Region?,
    ): Match? {
        val templateStatistics = statistics(template.pixels)
        // A flat single-colour Template has nothing to match; correlation is not defined for it.
        if (templateStatistics.deviation <= 0.0) return null

        val bounds = region ?: Region(0, 0, haystack.width - template.width, haystack.height - template.height)
        if (bounds.maxX < bounds.minX || bounds.maxY < bounds.minY) return null

        var best: Match? = null
        for (originY in bounds.minY..bounds.maxY) {
            for (originX in bounds.minX..bounds.maxX) {
                val score = correlation(template, templateStatistics, haystack, originX, originY)
                // TP-16: strictly greater, so a tie keeps the spot found first — topmost, then
                // leftmost — and the result is the same on every run.
                if (score > (best?.score ?: Double.NEGATIVE_INFINITY)) best = Match(originX, originY, score)
            }
        }
        return best
    }

    private fun coarseScanCost(
        template: GrayImage,
        haystack: GrayImage,
        factor: Int,
    ): Int {
        val positions =
            max(0, haystack.width / factor - template.width / factor + 1).toLong() *
                max(0, haystack.height / factor - template.height / factor + 1).toLong()
        val perPosition = max(1, (template.width / factor) * (template.height / factor)).toLong()
        return (positions * perPosition).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Descending, because the largest downscale factor is the cheapest one. */
    private val LEVELS = listOf(8, 4, 2, 1)

    /** Below this a coarse **Template** has too few cells left to correlate meaningfully. */
    private const val MINIMUM_COARSE_SIDE = 4
}
