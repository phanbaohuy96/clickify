package com.pbh.clickify.feature.map

import androidx.lifecycle.SavedStateHandle
import com.pbh.clickify.core.common.DispatcherProvider
import com.pbh.clickify.domain.map.PlaybackSpeed
import com.pbh.clickify.domain.model.AppResult
import com.pbh.clickify.domain.model.DomainError
import com.pbh.clickify.domain.repository.ScenarioRepository
import com.pbh.clickify.domain.repository.StoredScenario
import com.pbh.clickify.domain.repository.TemplateFiles
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.domain.scenario.TemplateSearch
import com.pbh.clickify.feature.map.ui.ScenarioMapEffect
import com.pbh.clickify.feature.map.ui.ScenarioMapViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MP-8, MP-10 to MP-13: where Playback is, what it remembers, and that it survives a picture that is gone. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioMapViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun tap(
        x: Int,
        y: Int,
        search: TemplateSearch? = null,
    ) = Step(action = StepAction.Tap(), target = StepTarget(ScreenPoint(x, y)), search = search)

    private class FakeRepository(
        private val result: AppResult<StoredScenario>,
    ) : ScenarioRepository {
        override fun observeScenarios(): Flow<List<StoredScenario>> = emptyFlow()

        override suspend fun load(id: UUID) = result

        override suspend fun save(scenario: Scenario): AppResult<Unit> = error("the map never writes")

        override suspend fun delete(id: UUID): AppResult<Unit> = error("the map never writes")

        override suspend fun duplicate(id: UUID): AppResult<StoredScenario> = error("the map never writes")
    }

    private class NoTemplates : TemplateFiles {
        override suspend fun ids(scenarioId: UUID): Set<UUID> = emptySet()

        override suspend fun read(
            scenarioId: UUID,
            templateId: UUID,
        ): ByteArray? = null

        override suspend fun write(
            scenarioId: UUID,
            templateId: UUID,
            bytes: ByteArray,
        ) = error("the map never writes")

        override suspend fun sweep(
            scenarioId: UUID,
            keep: Set<UUID>,
        ) = error("the map never writes")
    }

    private val dispatchers =
        object : DispatcherProvider {
            override val default: CoroutineDispatcher get() = dispatcher
            override val io: CoroutineDispatcher get() = dispatcher
            override val main: CoroutineDispatcher get() = dispatcher
        }

    private fun viewModel(
        scenario: Scenario,
        id: String? = scenario.id.toString(),
        result: AppResult<StoredScenario> = AppResult.Success(StoredScenario(scenario)),
        animations: Boolean = true,
    ) = ScenarioMapViewModel(
        SavedStateHandle(id?.let { mapOf("scenarioId" to it) } ?: emptyMap()),
        FakeRepository(result),
        NoTemplates(),
        dispatchers,
        { animations },
        { ScreenProfile(1080, 1920, 420, ScreenRotation.PORTRAIT) },
    )

    private val three = Scenario(name = "s", steps = listOf(tap(1, 1), tap(9, 9), tap(2, 2), tap(9, 9)))

    @Test
    fun `opening plays from step one with the card closed`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(three, state.scenario)
            assertTrue(state.playing)
            assertEquals(0, state.elapsedMilliseconds)
            assertEquals(0, state.currentStep)
            assertNull(state.cardFor)
            assertEquals(7, state.beats.size)
        }

    @Test
    fun `with animations removed it opens paused on step one and plays only when asked`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three, animations = false)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.playing)
            assertEquals(0, viewModel.state.value.currentStep)
            viewModel.tick(1000)
            assertEquals(0, viewModel.state.value.elapsedMilliseconds)

            viewModel.play()
            assertTrue(viewModel.state.value.playing)
        }

    @Test
    fun `a tick advances the clock by its time times the speed`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()

            viewModel.tick(100)
            assertEquals(100, viewModel.state.value.elapsedMilliseconds)
            viewModel.cycleSpeed()
            assertEquals(PlaybackSpeed.DOUBLE, viewModel.state.value.speed)
            viewModel.tick(100)
            assertEquals(300, viewModel.state.value.elapsedMilliseconds)
        }

    @Test
    fun `a tick after a long stop advances at most one capped frame`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()

            viewModel.tick(60_000)
            assertEquals(100, viewModel.state.value.elapsedMilliseconds)
            viewModel.cycleSpeed()
            viewModel.tick(60_000)
            assertEquals(300, viewModel.state.value.elapsedMilliseconds)
        }

    @Test
    fun `a slow speed does not lose the fraction of each frame`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()
            repeat(4) { viewModel.cycleSpeed() }
            assertEquals(PlaybackSpeed.HALF, viewModel.state.value.speed)

            repeat(100) { viewModel.tick(17) }
            assertTrue(kotlin.math.abs(viewModel.state.value.elapsedMilliseconds - 850) <= 1)
        }

    @Test
    fun `paused, a tick does nothing`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()

            viewModel.togglePlay()
            viewModel.tick(500)

            assertFalse(viewModel.state.value.playing)
            assertEquals(0, viewModel.state.value.elapsedMilliseconds)
            viewModel.togglePlay()
            assertTrue(viewModel.state.value.playing)
        }

    @Test
    fun `tapping a step pauses, jumps to it and opens the card, and a merged dot lists every number`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()

            viewModel.tapStep(listOf(3))
            var state = viewModel.state.value
            assertFalse(state.playing)
            assertEquals(2, state.currentStep)
            assertEquals(listOf(3), state.cardFor)

            viewModel.tapStep(listOf(2, 4, 99))
            state = viewModel.state.value
            assertEquals(1, state.currentStep)
            assertEquals(listOf(2, 4), state.cardFor)

            viewModel.tapStep(listOf(99))
            assertEquals(listOf(2, 4), viewModel.state.value.cardFor)
        }

    @Test
    fun `closing the card keeps it paused`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()
            viewModel.tapStep(listOf(2))

            viewModel.closeCard()

            assertNull(viewModel.state.value.cardFor)
            assertFalse(viewModel.state.value.playing)
        }

    @Test
    fun `next and previous jump between steps and leave play and pause alone`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()

            viewModel.next()
            assertEquals(1, viewModel.state.value.currentStep)
            assertEquals(1500 + 800L, viewModel.state.value.elapsedMilliseconds)
            assertTrue(viewModel.state.value.playing)

            viewModel.previous()
            assertEquals(0, viewModel.state.value.currentStep)
            viewModel.previous()
            assertEquals(0, viewModel.state.value.currentStep)

            viewModel.pause()
            repeat(6) { viewModel.next() }
            assertEquals(3, viewModel.state.value.currentStep)
            assertFalse(viewModel.state.value.playing)
        }

    @Test
    fun `replay goes back to step one and plays`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()
            viewModel.tapStep(listOf(3))

            viewModel.replay()

            val state = viewModel.state.value
            assertEquals(0, state.elapsedMilliseconds)
            assertTrue(state.playing)
            assertNull(state.cardFor)
        }

    @Test
    fun `ticking past the end stops at the last step and does not loop, and play then replays`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()

            repeat(200) { viewModel.tick(100) }

            var state = viewModel.state.value
            assertFalse(state.playing)
            assertTrue(state.finished)
            assertEquals(3, state.currentStep)
            assertEquals(state.beats.size - 1, state.position.beatIndex)

            viewModel.play()
            state = viewModel.state.value
            assertTrue(state.playing)
            assertEquals(0, state.elapsedMilliseconds)
        }

    @Test
    fun `moving the camera turns following off and play turns it back on`() =
        runTest(dispatcher) {
            val viewModel = viewModel(three)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.followCamera)

            viewModel.userMovedCamera()
            assertFalse(viewModel.state.value.followCamera)

            viewModel.pause()
            viewModel.play()
            assertTrue(viewModel.state.value.followCamera)
        }

    @Test
    fun `a scenario with no steps has nothing to play`() =
        runTest(dispatcher) {
            val viewModel = viewModel(Scenario(name = "s"))
            advanceUntilIdle()

            viewModel.play()
            viewModel.replay()
            viewModel.next()
            viewModel.tick(100)

            assertFalse(viewModel.state.value.playing)
            assertNull(viewModel.state.value.currentStep)
        }

    @Test
    fun `a missing template gives a null bitmap and not a crash`() =
        runTest(dispatcher) {
            val search = TemplateSearch(UUID.randomUUID())
            val viewModel = viewModel(Scenario(name = "s", steps = listOf(tap(1, 1, search))))
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.thumbnails
                    .containsKey(search.templateId),
            )
            assertNull(viewModel.state.value.thumbnails[search.templateId])
        }

    @Test
    fun `a scenario that cannot be loaded, or an id that is not one, is a failure to show`() =
        runTest(dispatcher) {
            val scenario = Scenario(name = "s")
            val broken = viewModel(scenario, result = AppResult.Failure(DomainError.Unknown))
            val nameless = viewModel(scenario, id = "not-a-uuid")
            advanceUntilIdle()

            assertTrue(broken.state.value.failed && !broken.state.value.loading)
            assertTrue(nameless.state.value.failed)
            nameless.tapStep(listOf(1))
            nameless.openInOverlay()
        }

    @Test
    fun `open in overlay names this scenario`() =
        runTest(dispatcher) {
            val scenario = Scenario(name = "s", steps = listOf(tap(1, 1)))
            val viewModel = viewModel(scenario)
            advanceUntilIdle()

            viewModel.openInOverlay()
            advanceUntilIdle()

            assertEquals(ScenarioMapEffect.OpenOverlay(scenario.id), viewModel.effects.first())
        }
}
