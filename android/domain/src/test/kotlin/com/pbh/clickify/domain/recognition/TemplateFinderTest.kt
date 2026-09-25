package com.pbh.clickify.domain.recognition

import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenRegion
import com.pbh.clickify.domain.scenario.TemplateSearch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TP-4, TP-5, TP-21, TP-24: the waiting, on virtual time.
 *
 * The clock is injected (`TemplateFinder.elapsedMilliseconds`) and wired to the test scheduler, so
 * a five-second wait is asserted in microseconds and the number asserted is the one the user set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TemplateFinderTest {
    private val templateId = UUID.randomUUID()

    private fun noise(
        width: Int,
        height: Int,
        seed: Int,
    ): GrayImage {
        val random = Random(seed)
        return GrayImage(width, height, FloatArray(width * height) { random.nextFloat() })
    }

    private val template = noise(10, 8, seed = 1)

    private fun frameWithIt(
        atX: Int = 30,
        atY: Int = 20,
    ): GrayImage {
        val haystack = noise(120, 100, seed = 2)
        val copy = haystack.pixels.copyOf()
        for (y in 0 until template.height) {
            for (x in 0 until template.width) {
                copy[(atY + y) * haystack.width + atX + x] = template[x, y]
            }
        }
        return GrayImage(haystack.width, haystack.height, copy)
    }

    private val frameWithoutIt = noise(120, 100, seed = 3)

    /** Hands out a scripted sequence of frames, and repeats the last one for ever. */
    private class Frames(
        private val script: List<GrayImage?>,
    ) : ScreenSource {
        var taken = 0
            private set

        override suspend fun frame(): GrayImage? {
            val frame = script.getOrElse(taken) { script.last() }
            taken++
            return frame
        }
    }

    private fun TestScope.finder(
        frames: Frames,
        available: GrayImage? = template,
    ) = TemplateFinder(
        screen = frames,
        templates = { id -> available.takeIf { id == templateId } },
        elapsedMilliseconds = { testScheduler.currentTime },
    )

    private fun search(
        waitMilliseconds: Int = 5_000,
        threshold: Double = 0.9,
        region: ScreenRegion? = null,
    ) = TemplateSearch(
        templateId = templateId,
        threshold = threshold,
        region = region,
        waitMilliseconds = waitMilliseconds,
    )

    @Test
    fun `a template already on screen is found on the first look`() =
        runTest {
            val frames = Frames(listOf(frameWithIt()))

            val result = assertIs<SearchResult.Found>(finder(frames).await(search()))

            assertEquals(1, frames.taken)
            assertEquals(0L, testScheduler.currentTime, "nothing should have been waited for")
            // TP-17: the centre of the match, not its corner.
            assertEquals(ScreenPoint(30 + 5, 20 + 4), result.at)
        }

    @Test
    fun `a template that appears part way through is waited for`() =
        runTest {
            val frames = Frames(listOf(frameWithoutIt, frameWithoutIt, frameWithIt()))

            val result = finder(frames).await(search(waitMilliseconds = 5_000))

            assertIs<SearchResult.Found>(result)
            assertEquals(3, frames.taken)
            assertEquals(800L, testScheduler.currentTime, "TP-4: two polls at 400ms")
        }

    /** TP-4: the wait is milliseconds, and it is the user's number. */
    @Test
    fun `a template that never appears gives up when the wait runs out`() =
        runTest {
            val frames = Frames(listOf(frameWithoutIt))

            val result = finder(frames).await(search(waitMilliseconds = 1_000))

            assertIs<SearchResult.NotFound>(result)
            assertTrue(testScheduler.currentTime >= 1_000L, "gave up after ${testScheduler.currentTime}ms of a 1000ms wait")
            assertTrue(testScheduler.currentTime < 1_500L, "waited ${testScheduler.currentTime}ms for a 1000ms wait")
        }

    /** TP-21: zero is legitimate, and it is the whole of "close it if it happens to be there". */
    @Test
    fun `a wait of zero still takes one look`() =
        runTest {
            val frames = Frames(listOf(frameWithoutIt))

            assertIs<SearchResult.NotFound>(finder(frames).await(search(waitMilliseconds = 0)))

            assertEquals(1, frames.taken)
            assertEquals(0L, testScheduler.currentTime)
        }

    /** TP-24: the same wait, read the other way round. */
    @Test
    fun `waiting for something to go away ends when it goes away`() =
        runTest {
            val frames = Frames(listOf(frameWithIt(), frameWithIt(), frameWithoutIt))

            val result = finder(frames).await(search(), expects = Presence.ABSENT)

            assertIs<SearchResult.NotFound>(result)
            assertEquals(3, frames.taken)
        }

    @Test
    fun `waiting for something to go away gives up while it is still there`() =
        runTest {
            val frames = Frames(listOf(frameWithIt()))

            val result = finder(frames).await(search(waitMilliseconds = 800), expects = Presence.ABSENT)

            assertIs<SearchResult.Found>(result)
            assertTrue(testScheduler.currentTime >= 800L)
        }

    /**
     * TP-4: a refused frame is a poll that found nothing, and it must not shorten the wait.
     *
     * The platform rate-limits `takeScreenshot` and a secure window blocks it outright, so this is
     * the ordinary case rather than the exotic one.
     */
    @Test
    fun `a refused frame costs the wait nothing`() =
        runTest {
            val frames = Frames(listOf(null, null, frameWithIt()))

            val result = finder(frames).await(search(waitMilliseconds = 5_000))

            assertIs<SearchResult.Found>(result)
            assertEquals(800L, testScheduler.currentTime)
        }

    /** TP-29: at run time a Template whose file has gone is a search that finds nothing. */
    @Test
    fun `a template whose file has gone times out rather than throwing`() =
        runTest {
            val frames = Frames(listOf(frameWithIt()))

            val result = finder(frames, available = null).await(search(waitMilliseconds = 400))

            assertIs<SearchResult.NotFound>(result)
        }

    /** TP-15: the threshold is the user's, and a near-miss is a miss. */
    @Test
    fun `a threshold of one refuses anything short of exact`() =
        runTest {
            val frames = Frames(listOf(frameWithIt()))

            val result = finder(frames).await(search(waitMilliseconds = 0, threshold = 1.0))

            // The patch is an exact copy, so even 1.0 finds it — the point is that the number is
            // read at all, which the next case shows from the other side.
            assertIs<SearchResult.Found>(result)
        }

    @Test
    fun `a high threshold turns a poor match into no match`() =
        runTest {
            val frames = Frames(listOf(frameWithoutIt))

            assertIs<SearchResult.NotFound>(finder(frames).await(search(waitMilliseconds = 0, threshold = 0.5)))
        }

    /** TP-9: the region is where it looks, and the point comes back in the screen's coordinates. */
    @Test
    fun `a search region narrows the haystack without moving the answer`() =
        runTest {
            val frames = Frames(listOf(frameWithIt(atX = 60, atY = 50)))
            val region = ScreenRegion(left = 40, top = 40, right = 110, bottom = 90)

            val result = assertIs<SearchResult.Found>(finder(frames).await(search(region = region)))

            assertEquals(ScreenPoint(60 + 5, 50 + 4), result.at)
        }

    @Test
    fun `a template outside the search region is not found`() =
        runTest {
            val frames = Frames(listOf(frameWithIt(atX = 5, atY = 5)))
            val region = ScreenRegion(left = 60, top = 60, right = 120, bottom = 100)

            val result = finder(frames).await(search(waitMilliseconds = 0, region = region))

            assertIs<SearchResult.NotFound>(result)
        }

    @Test
    fun `an empty region is treated as no region at all`() =
        runTest {
            val frames = Frames(listOf(frameWithIt()))
            val region = ScreenRegion(left = 10, top = 10, right = 10, bottom = 10)

            assertIs<SearchResult.Found>(finder(frames).await(search(region = region)))
        }
}
