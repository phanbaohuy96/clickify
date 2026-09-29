package com.pbh.clickify.domain.map

import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint

/** `MP-8`: how long a Step's beat lasts at 1×. */
const val STEP_BEAT_MILLISECONDS = 1500L

/** `MP-8`: how long the arrow to the next Step takes to draw at 1×. */
const val TRAVEL_BEAT_MILLISECONDS = 800L

/** The most a repeated Step pulses; the repeat is shown as `×n`, never played n times (`MP-8`). */
const val MAX_PULSES = 3

/**
 * One moment of **Playback** (`MP-8`). The beats are the whole of it: Playback is one path, so this
 * is a flat list with no jump in it (ADR-0011), and it is built from the Scenario alone, taking the
 * path where every Guard holds and every Template is found (`MP-7`).
 */
sealed interface Beat {
    /** How long this beat lasts at 1×. */
    val durationMilliseconds: Long

    /** `MP-8`: a Step lights up. [stepNumbers] is every Step sharing its dot (`MP-4`), counting from 1. */
    data class StepBeat(
        val stepIndex: Int,
        val stepNumbers: List<Int>,
        val pulses: Int,
    ) : Beat {
        override val durationMilliseconds: Long get() = STEP_BEAT_MILLISECONDS
    }

    /**
     * `MP-8`: the arrow from one Step to the next draws, and the real delay is only labelled.
     * A zero-length arrow is still a beat, so the pace does not depend on where the Steps are.
     */
    data class TravelBeat(
        val fromIndex: Int,
        val toIndex: Int,
        val from: ScreenPoint,
        val to: ScreenPoint,
        val delayMilliseconds: Int,
    ) : Beat {
        override val durationMilliseconds: Long get() = TRAVEL_BEAT_MILLISECONDS
    }
}

/** `MP-11`: the speeds the control cycles through; every beat is scaled by [factor]. */
enum class PlaybackSpeed(
    val factor: Double,
) {
    HALF(0.5),
    NORMAL(1.0),
    DOUBLE(2.0),
    TRIPLE(3.0),
    QUINTUPLE(5.0),
    ;

    /** After 5× comes 0.5× again. */
    fun next(): PlaybackSpeed = entries[(ordinal + 1) % entries.size]
}

/** Where **Playback** is: which beat, and how far through it, `0…1`. */
data class PlaybackPosition(
    val beatIndex: Int,
    val progress: Float,
)

/**
 * `MP-8`: a Step beat per Step and a travel beat between each pair, and nothing after the last, so
 * Playback stops there whatever the run count.
 */
fun ScenarioMap.beats(scenario: Scenario): List<Beat> {
    val steps = scenario.steps

    fun placeAt(index: Int): ScreenPoint = placeOf(index + 1) ?: error("Step ${index + 1} has neither a dot nor a chip")

    return steps.indices.flatMap { index ->
        val numbers = nodes.firstOrNull { index + 1 in it.stepNumbers }?.stepNumbers ?: listOf(index + 1)
        val beat = Beat.StepBeat(index, numbers, minOf(steps[index].repeatCount, MAX_PULSES).coerceAtLeast(1))
        if (index == steps.lastIndex) {
            listOf(beat)
        } else {
            listOf(beat, Beat.TravelBeat(index, index + 1, placeAt(index), placeAt(index + 1), steps[index].delayMillisecondsAfter))
        }
    }
}

/** Where the Step numbered [stepNumber] is drawn: its dot, or its chip on the path (`MP-4`). */
fun ScenarioMap.placeOf(stepNumber: Int): ScreenPoint? =
    nodes.firstOrNull { stepNumber in it.stepNumbers }?.point ?: chips.firstOrNull { it.stepNumber == stepNumber }?.position

/** When beat [beatIndex] starts, in 1× milliseconds from the start of Playback. */
fun List<Beat>.startOf(beatIndex: Int): Long = take(beatIndex).sumOf { it.durationMilliseconds }

/** The whole of Playback at 1×, in milliseconds. */
val List<Beat>.totalMilliseconds: Long get() = sumOf { it.durationMilliseconds }

/**
 * `MP-8`: where Playback is after [elapsedMilliseconds] of wall-clock time at [speed], clamped to the
 * end of the last beat.
 */
fun List<Beat>.positionAt(
    elapsedMilliseconds: Long,
    speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
): PlaybackPosition {
    if (isEmpty()) return PlaybackPosition(0, 0f)
    val virtual = (elapsedMilliseconds.coerceAtLeast(0) * speed.factor).toLong()
    var start = 0L
    forEachIndexed { index, beat ->
        val end = start + beat.durationMilliseconds
        if (virtual < end) return PlaybackPosition(index, (virtual - start).toFloat() / beat.durationMilliseconds)
        start = end
    }
    return PlaybackPosition(lastIndex, 1f)
}

/** `MP-11`: the end of the last beat, where ▶ turns into ↺. */
fun List<Beat>.isFinished(position: PlaybackPosition): Boolean = isEmpty() || (position.beatIndex >= lastIndex && position.progress >= 1f)

/** The index of the Step beat for the Step at [stepIndex], or null when there is none. */
fun List<Beat>.stepBeatIndexOf(stepIndex: Int): Int? = indexOfFirst { it is Beat.StepBeat && it.stepIndex == stepIndex }.takeIf { it >= 0 }

/** The Step Playback is on; during a travel beat that is the one it is leaving. Null when there are no beats. */
fun List<Beat>.currentStep(position: PlaybackPosition): Int? =
    when (val beat = getOrNull(position.beatIndex)) {
        is Beat.StepBeat -> beat.stepIndex
        is Beat.TravelBeat -> beat.fromIndex
        null -> null
    }

/** `MP-11`: the Step `⏭` jumps to, stopping at the last. */
fun List<Beat>.nextStep(position: PlaybackPosition): Int? =
    currentStep(position)?.let { minOf(it + 1, count { beat -> beat is Beat.StepBeat } - 1) }

/**
 * `MP-11`: the Step `⏮` jumps to, stopping at the first. During a travel beat that is the Step being
 * left, so one press goes back to what was just shown.
 */
fun List<Beat>.previousStep(position: PlaybackPosition): Int? =
    when (val beat = getOrNull(position.beatIndex)) {
        is Beat.StepBeat -> maxOf(beat.stepIndex - 1, 0)
        is Beat.TravelBeat -> beat.fromIndex
        null -> null
    }
