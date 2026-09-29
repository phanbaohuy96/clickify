package com.pbh.clickify.feature.map.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import com.pbh.clickify.core.common.DispatcherProvider
import com.pbh.clickify.core.ui.BaseViewModel
import com.pbh.clickify.core.ui.UiEffect
import com.pbh.clickify.domain.map.Beat
import com.pbh.clickify.domain.map.PlaybackPosition
import com.pbh.clickify.domain.map.PlaybackSpeed
import com.pbh.clickify.domain.map.ScenarioMap
import com.pbh.clickify.domain.map.beats
import com.pbh.clickify.domain.map.currentStep
import com.pbh.clickify.domain.map.isFinished
import com.pbh.clickify.domain.map.map
import com.pbh.clickify.domain.map.nextStep
import com.pbh.clickify.domain.map.positionAt
import com.pbh.clickify.domain.map.previousStep
import com.pbh.clickify.domain.map.startOf
import com.pbh.clickify.domain.map.stepBeatIndexOf
import com.pbh.clickify.domain.map.totalMilliseconds
import com.pbh.clickify.domain.model.AppResult
import com.pbh.clickify.domain.repository.ScenarioRepository
import com.pbh.clickify.domain.repository.TemplateFiles
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.math.floor

data class ScenarioMapUiState(
    val loading: Boolean = true,
    /** Null while loading, and after a load that failed ([failed]). */
    val scenario: Scenario? = null,
    val failed: Boolean = false,
    /** `MP-5`: by Template id. A key with a null value is a PNG that has gone (`TP-29`). */
    val thumbnails: Map<UUID, ImageBitmap?> = emptyMap(),
    /** `MP-4`: the Scenario laid out; null until it has loaded. */
    val map: ScenarioMap? = null,
    /** `MP-3`: the profile the phone frame has, which is the current display when the Scenario has none (`MP-2`). */
    val frame: ScreenProfile? = null,
    /** `MP-8`. */
    val beats: List<Beat> = emptyList(),
    val playing: Boolean = false,
    /** Playback's clock, in 1x milliseconds: a tick adds its time times the speed, so changing speed never jumps. */
    val elapsedMilliseconds: Long = 0,
    val speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    /** `MP-10`: the Step numbers the open card shows, counting from 1, or null when there is none. */
    val cardFor: List<Int>? = null,
    /** `MP-13`. */
    val followCamera: Boolean = true,
) {
    val position: PlaybackPosition get() = beats.positionAt(elapsedMilliseconds)

    /** `MP-11`: where ▶ turns into ↺. */
    val finished: Boolean get() = beats.isFinished(position)

    /** The Step Playback is on, counting from 0, or null when the Scenario has none. */
    val currentStep: Int? get() = beats.currentStep(position)
}

sealed interface ScenarioMapEffect : UiEffect {
    /** `MP-14`: does what tapping the row does (`OV-26`). */
    data class OpenOverlay(
        val scenarioId: UUID,
    ) : ScenarioMapEffect
}

/**
 * Reads one Scenario and holds where Playback is on its map. It never writes one (`MP-1`).
 * Playback's clock lives here so that it can be tested; the screen only calls [tick] once per frame.
 */
@Suppress("TooManyFunctions") // one function per control of the screen (`MP-11`), each a line or two
@HiltViewModel
class ScenarioMapViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val repository: ScenarioRepository,
        private val templates: TemplateFiles,
        private val dispatchers: DispatcherProvider,
        private val animations: AnimationsEnabled,
        private val display: CurrentDisplay,
    ) : BaseViewModel<ScenarioMapUiState, ScenarioMapEffect>(ScenarioMapUiState()) {
        init {
            val id = savedState.get<String>("scenarioId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (id == null) {
                setState { copy(loading = false, failed = true) }
            } else {
                launch { load(id) }
            }
        }

        /** The part of a millisecond a scaled tick left over, so that 0.5x does not lose half of every frame. */
        private var remainder = 0.0

        private suspend fun load(id: UUID) {
            when (val result = repository.load(id)) {
                is AppResult.Failure -> setState { copy(loading = false, failed = true) }
                is AppResult.Success -> {
                    val scenario = result.data.scenario
                    val map = scenario.map(display.profile())
                    val beats = map.beats(scenario)
                    // MP-12: it plays on opening, unless the phone has removed animations.
                    setState {
                        copy(
                            loading = false,
                            scenario = scenario,
                            map = map,
                            frame = map.frame ?: display.profile(),
                            beats = beats,
                            playing = beats.isNotEmpty() && animations.enabled(),
                        )
                    }
                    val thumbnails = scenario.templateIds.associateWith { decode(id, it) }
                    setState { copy(thumbnails = thumbnails) }
                }
            }
        }

        /** `TP-29`: a file that is gone, or that is not a picture, is a null and not a crash. */
        private suspend fun decode(
            scenarioId: UUID,
            templateId: UUID,
        ): ImageBitmap? =
            templates.read(scenarioId, templateId)?.let { bytes ->
                withContext(dispatchers.default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }

        /** `MP-11`: ▶ at the end is ↺, and playing turns camera following back on (`MP-13`). */
        fun play() {
            if (currentState.beats.isEmpty()) return
            if (currentState.finished) replay() else setState { copy(playing = true, followCamera = true, cardFor = null) }
        }

        fun pause() = setState { copy(playing = false) }

        fun togglePlay() = if (currentState.playing) pause() else play()

        /** `MP-11`: `⏭`, which does not change play or pause. */
        fun next() = currentState.let { jumpTo(it.beats.nextStep(it.position)) }

        /** `MP-11`: `⏮`. */
        fun previous() = currentState.let { jumpTo(it.beats.previousStep(it.position)) }

        /** `MP-11`: back to Step 1, then play. */
        fun replay() {
            if (currentState.beats.isEmpty()) return
            remainder = 0.0
            setState { copy(elapsedMilliseconds = 0, playing = true, followCamera = true, cardFor = null) }
        }

        /** `MP-11`: 0.5x, 1x, 2x, 3x, 5x, and round again. */
        fun cycleSpeed() = setState { copy(speed = speed.next()) }

        /**
         * `MP-10`: a tap on a dot or a chip pauses, makes its Step current and opens the card. A merged
         * dot passes every number it stands for (`MP-4`), and a number the Scenario does not have is ignored.
         */
        fun tapStep(stepNumbers: List<Int>) {
            val known = stepNumbers.filter { it in 1..currentState.beats.count { beat -> beat is Beat.StepBeat } }
            if (known.isEmpty()) return
            jumpTo(known.first() - 1)
            setState { copy(playing = false, cardFor = known) }
        }

        fun closeCard() = setState { copy(cardFor = null) }

        /** `MP-13`: a manual pan or zoom stops the camera following, until [play]. */
        fun userMovedCamera() = setState { copy(followCamera = false) }

        /**
         * One frame of Playback (`MP-8`): moves the clock by [frameDeltaMillis] at this speed, and stops
         * at the last Step. It is called from the screen's frame loop, and does nothing while paused.
         */
        fun tick(frameDeltaMillis: Long) {
            val state = currentState
            if (!state.playing || state.beats.isEmpty()) return
            val total = state.beats.totalMilliseconds
            val exact =
                state.elapsedMilliseconds + remainder + frameDeltaMillis.coerceIn(0, MAX_FRAME_DELTA_MILLISECONDS) * state.speed.factor
            val elapsed = floor(exact).toLong().coerceAtMost(total)
            remainder = if (elapsed >= total) 0.0 else exact - elapsed
            setState { copy(elapsedMilliseconds = elapsed, playing = elapsed < total) }
        }

        private fun jumpTo(stepIndex: Int?) {
            val beat = stepIndex?.let { currentState.beats.stepBeatIndexOf(it) } ?: return
            remainder = 0.0
            setState { copy(elapsedMilliseconds = beats.startOf(beat), cardFor = null) }
        }

        fun openInOverlay() {
            currentState.scenario?.let { sendEffect(ScenarioMapEffect.OpenOverlay(it.id)) }
        }
    }

/**
 * The longest a single frame may move Playback. The Compose frame clock pauses while the app is stopped,
 * so the first frame after coming back reports all the time spent away; without this cap Playback would
 * jump by that much.
 */
internal const val MAX_FRAME_DELTA_MILLISECONDS = 100L
