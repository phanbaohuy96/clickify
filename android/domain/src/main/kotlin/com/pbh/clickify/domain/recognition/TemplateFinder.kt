package com.pbh.clickify.domain.recognition

import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.TemplateSearch
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.min

/** TP-2: one frame of the display as greyscale, or null when the platform would not give one. */
fun interface ScreenSource {
    suspend fun frame(): GrayImage?
}

/** TP-27: a **Template**'s pixels, or null when its file has gone (`TP-29`). */
fun interface TemplateSource {
    suspend fun template(id: UUID): GrayImage?
}

/** What one look at the screen found. */
sealed interface SearchResult {
    data class Found(
        val at: ScreenPoint,
        val score: Double,
    ) : SearchResult

    data object NotFound : SearchResult
}

/** Whether this result is the state the caller was waiting for. */
fun SearchResult.satisfies(expects: Presence): Boolean =
    when (expects) {
        Presence.PRESENT -> this is SearchResult.Found
        Presence.ABSENT -> this is SearchResult.NotFound
    }

/**
 * Waits for a **Template** to appear, or to go away (`TP-4`, `TP-5`, `TP-21`, `TP-24`).
 *
 * The wait is measured in **milliseconds elapsed** and never in polls. A device that refuses a
 * frame — the platform rate-limits `takeScreenshot`, and a secure window blocks it outright — would
 * otherwise shorten the user's wait by however many frames it swallowed.
 *
 * [elapsedMilliseconds] is injected so the whole of this class runs on virtual time in tests. It is
 * the only clock here; nothing measures duration any other way.
 */
class TemplateFinder(
    private val screen: ScreenSource,
    private val templates: TemplateSource,
    private val elapsedMilliseconds: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * Polls until [expects] holds or the wait runs out, and returns whatever the last look found.
     *
     * A **Template** whose file has gone reports *not found* on every poll, exactly as `TP-29`
     * says: at run time a missing file is a search that finds nothing, which is the `onTimeout`
     * the user chose rather than a crash. The editor is where that is caught as a mistake.
     */
    suspend fun await(
        search: TemplateSearch,
        expects: Presence = Presence.PRESENT,
    ): SearchResult {
        val started = elapsedMilliseconds()
        while (true) {
            val result = lookOnce(search)
            if (result.satisfies(expects)) return result
            val left = search.waitMilliseconds - (elapsedMilliseconds() - started)
            if (left <= 0L) return result
            // TP-5: sleeping between two looks is what makes the second one a different screen.
            delay(min(POLL_MILLISECONDS, left))
        }
    }

    private suspend fun lookOnce(search: TemplateSearch): SearchResult {
        val template = templates.template(search.templateId) ?: return SearchResult.NotFound
        val frame = screen.frame() ?: return SearchResult.NotFound
        return frame.find(template, search)
    }

    private companion object {
        /** TP-4: the platform refuses `takeScreenshot` more often than about every 333 ms. */
        const val POLL_MILLISECONDS = 400L
    }
}

/**
 * One look for [template] inside this frame, honouring the **Search region** and the threshold.
 *
 * The region is cropped out and searched on its own rather than masked, which is the whole of
 * `TP-9`'s saving: a smaller haystack is a cheaper scan, and the offset is added back to the point
 * found so the caller never sees the region's coordinate space.
 */
internal fun GrayImage.find(
    template: GrayImage,
    search: TemplateSearch,
): SearchResult {
    val region = search.region?.takeUnless { it.isEmpty }
    val fromX = region?.left?.coerceIn(0, width) ?: 0
    val fromY = region?.top?.coerceIn(0, height) ?: 0
    val haystack = if (region == null) this else cropped(region.left, region.top, region.width, region.height)

    val match = TemplateMatcher.bestMatch(template, haystack) ?: return SearchResult.NotFound
    if (match.score < search.threshold) return SearchResult.NotFound
    // TP-17: the Target is the centre of what was matched, not its corner.
    return SearchResult.Found(
        at = ScreenPoint(fromX + match.x + template.width / 2, fromY + match.y + template.height / 2),
        score = match.score,
    )
}
