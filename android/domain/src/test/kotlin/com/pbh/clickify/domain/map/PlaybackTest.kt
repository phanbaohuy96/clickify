package com.pbh.clickify.domain.map

import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MP-7, MP-8, MP-11: the beats of Playback and where a clock puts it. Drawing them is not tested here. */
class PlaybackTest {
    private val profile = ScreenProfile(1080, 1920, 420, ScreenRotation.PORTRAIT)

    private fun tap(
        x: Int,
        y: Int,
        repeat: Int = 1,
        delay: Int = 200,
    ) = Step(action = StepAction.Tap(), target = StepTarget(ScreenPoint(x, y)), repeatCount = repeat, delayMillisecondsAfter = delay)

    private fun global() = Step(action = StepAction.Global(GlobalActionKind.BACK), target = StepTarget(ScreenPoint(0, 0)))

    private fun beatsOf(vararg steps: Step): List<Beat> {
        val scenario = Scenario(name = "s", steps = steps.toList(), screenProfile = profile)
        return scenario.map().beats(scenario)
    }

    @Test
    fun `taps give a step beat then a travel beat and none after the last`() {
        val beats = beatsOf(tap(100, 100, delay = 300), tap(200, 300), tap(400, 500))

        assertEquals(5, beats.size)
        assertEquals(listOf(0, 1, 2), beats.filterIsInstance<Beat.StepBeat>().map { it.stepIndex })
        assertTrue(beats.last() is Beat.StepBeat)
        val first = beats[1] as Beat.TravelBeat
        assertEquals(0, first.fromIndex)
        assertEquals(1, first.toIndex)
        assertEquals(ScreenPoint(100, 100), first.from)
        assertEquals(ScreenPoint(200, 300), first.to)
        assertEquals(300, first.delayMilliseconds)
        assertEquals(STEP_BEAT_MILLISECONDS, beats[0].durationMilliseconds)
        assertEquals(TRAVEL_BEAT_MILLISECONDS, beats[1].durationMilliseconds)
    }

    @Test
    fun `a chip in the middle has its own step beat and the travel beats run to and from its place`() {
        val scenario = Scenario(name = "s", steps = listOf(tap(100, 100), global(), tap(300, 300)), screenProfile = profile)
        val map = scenario.map()
        val beats = map.beats(scenario)
        val chip = map.chips.single().position

        assertEquals(5, beats.size)
        assertEquals(chip, (beats[1] as Beat.TravelBeat).to)
        assertEquals(chip, (beats[3] as Beat.TravelBeat).from)
        assertEquals(listOf(2), (beats[2] as Beat.StepBeat).stepNumbers)
    }

    @Test
    fun `coincident anchors still travel and their beats name every number of the shared dot`() {
        val beats = beatsOf(tap(100, 100), tap(100, 100), tap(500, 500))

        val travel = beats[1] as Beat.TravelBeat
        assertEquals(travel.from, travel.to)
        assertEquals(5, beats.size)
        assertEquals(listOf(1, 2), (beats[0] as Beat.StepBeat).stepNumbers)
        assertEquals(listOf(1, 2), (beats[2] as Beat.StepBeat).stepNumbers)
        assertEquals(listOf(3), (beats[4] as Beat.StepBeat).stepNumbers)
    }

    @Test
    fun `pulses are the repeat but never more than three`() {
        val pulses =
            beatsOf(tap(1, 1, repeat = 1), tap(2, 2, repeat = 2), tap(3, 3, repeat = 3), tap(4, 4, repeat = 50))
                .filterIsInstance<Beat.StepBeat>()
                .map { it.pulses }

        assertEquals(listOf(1, 2, 3, 3), pulses)
    }

    @Test
    fun `an empty scenario has no beats and is finished at once`() {
        val beats = beatsOf()

        assertTrue(beats.isEmpty())
        assertEquals(PlaybackPosition(0, 0f), beats.positionAt(5_000))
        assertTrue(beats.isFinished(PlaybackPosition(0, 0f)))
        assertNull(beats.currentStep(PlaybackPosition(0, 0f)))
        assertNull(beats.nextStep(PlaybackPosition(0, 0f)))
        assertNull(beats.previousStep(PlaybackPosition(0, 0f)))
        assertNull(beats.stepBeatIndexOf(0))
    }

    @Test
    fun `position at zero, mid step, mid travel and past the end`() {
        val beats = beatsOf(tap(1, 1), tap(2, 2))

        assertEquals(PlaybackPosition(0, 0f), beats.positionAt(0))
        assertEquals(PlaybackPosition(0, 0.5f), beats.positionAt(750))
        assertEquals(PlaybackPosition(1, 0.5f), beats.positionAt(1500 + 400))
        assertEquals(PlaybackPosition(2, 0f), beats.positionAt(1500 + 800))
        assertEquals(PlaybackPosition(2, 1f), beats.positionAt(999_999))
        assertEquals(PlaybackPosition(0, 0f), beats.positionAt(-5))
        assertFalse(beats.isFinished(beats.positionAt(1500 + 800)))
        assertTrue(beats.isFinished(beats.positionAt(999_999)))
        assertEquals(3800L, beats.totalMilliseconds)
        assertEquals(2300L, beats.startOf(2))
    }

    @Test
    fun `speed scales every beat`() {
        val beats = beatsOf(tap(1, 1), tap(2, 2))

        assertEquals(PlaybackPosition(0, 0.5f), beats.positionAt(1500, PlaybackSpeed.HALF))
        assertEquals(PlaybackPosition(0, 0.5f), beats.positionAt(750, PlaybackSpeed.NORMAL))
        assertEquals(PlaybackPosition(0, 0.5f), beats.positionAt(375, PlaybackSpeed.DOUBLE))
        assertEquals(PlaybackPosition(0, 0.5f), beats.positionAt(250, PlaybackSpeed.TRIPLE))
        assertEquals(PlaybackPosition(0, 0.5f), beats.positionAt(150, PlaybackSpeed.QUINTUPLE))
    }

    @Test
    fun `next and previous stop at the ends and travel counts as the step it leaves`() {
        val beats = beatsOf(tap(1, 1), tap(2, 2), tap(3, 3))
        val first = PlaybackPosition(0, 0.2f)
        val middle = PlaybackPosition(2, 0.2f)
        val last = PlaybackPosition(4, 1f)
        val travel = PlaybackPosition(1, 0.5f)

        assertEquals(1, beats.nextStep(first))
        assertEquals(0, beats.previousStep(first))
        assertEquals(2, beats.nextStep(middle))
        assertEquals(0, beats.previousStep(middle))
        assertEquals(2, beats.nextStep(last))
        assertEquals(1, beats.previousStep(last))
        assertEquals(1, beats.nextStep(travel))
        assertEquals(0, beats.previousStep(travel))
        assertEquals(0, beats.currentStep(travel))
        assertEquals(4, beats.stepBeatIndexOf(2))
        assertNull(beats.stepBeatIndexOf(9))
    }

    @Test
    fun `a step's place is its dot, or its chip, or nothing`() {
        val scenario = Scenario(name = "s", steps = listOf(tap(100, 100), global(), tap(300, 300)), screenProfile = profile)
        val map = scenario.map()

        assertEquals(ScreenPoint(100, 100), map.placeOf(1))
        assertEquals(map.chips.single().position, map.placeOf(2))
        assertNull(map.placeOf(9))
    }

    @Test
    fun `speed cycles from half up to five times and back`() {
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.HALF.next())
        assertEquals(PlaybackSpeed.DOUBLE, PlaybackSpeed.NORMAL.next())
        assertEquals(PlaybackSpeed.TRIPLE, PlaybackSpeed.DOUBLE.next())
        assertEquals(PlaybackSpeed.QUINTUPLE, PlaybackSpeed.TRIPLE.next())
        assertEquals(PlaybackSpeed.HALF, PlaybackSpeed.QUINTUPLE.next())
        assertEquals(listOf(0.5, 1.0, 2.0, 3.0, 5.0), PlaybackSpeed.entries.map { it.factor })
    }
}
