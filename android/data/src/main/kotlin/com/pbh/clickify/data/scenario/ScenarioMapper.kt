package com.pbh.clickify.data.scenario

import com.pbh.clickify.domain.repository.StoredScenario
import com.pbh.clickify.domain.scenario.Guard
import com.pbh.clickify.domain.scenario.OnTimeout
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRegion
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.domain.scenario.TemplateSearch
import com.pbh.clickify.domain.scenario.clampedToLimits
import java.util.UUID

/**
 * Between the file format and the model, in both directions.
 *
 * Reading is the forgiving direction (FS-12): a missing field takes its default, an unreadable id
 * becomes a fresh one, an unknown enum name falls back rather than throwing. Writing is the exact
 * direction — what comes out is what the specification describes.
 */
internal fun ScenarioDto.toDomain(): Scenario =
    Scenario(
        id = id.toUuidOrRandom(),
        name = name.ifBlank { Scenario.REPAIRED_NAME },
        steps = steps.map { it.toDomain() },
        runCount = repeat.toDomain(),
        countdownMilliseconds = countdownMilliseconds,
        screenProfile = screenProfile?.toDomain(),
    ).clampedToLimits()

internal fun Scenario.toDto(): ScenarioDto =
    ScenarioDto(
        // TP-28: the lowest version that can express this Scenario. A sequence of plain taps is
        // still exactly a version 1 file, and writing 2 for it would make every Scenario on the
        // phone unreadable to the previous build in exchange for nothing.
        schemaVersion = if (usesRecognition) RECOGNITION_SCHEMA_VERSION else BASE_SCHEMA_VERSION,
        id = id.toString(),
        name = name,
        repeat = runCount.toDto(),
        countdownMilliseconds = countdownMilliseconds,
        screenProfile = screenProfile?.toDto(),
        steps = steps.map { it.toDto() },
    )

private fun RepeatDto.toDomain(): RunCount =
    when (this) {
        is RepeatDto.Count -> RunCount.Times(value)
        RepeatDto.UntilStopped -> RunCount.UntilStopped
    }

private fun RunCount.toDto(): RepeatDto =
    when (this) {
        is RunCount.Times -> RepeatDto.Count(count)
        RunCount.UntilStopped -> RepeatDto.UntilStopped
    }

private fun ScreenProfileDto.toDomain(): ScreenProfile =
    ScreenProfile(
        widthPixels = widthPixels,
        heightPixels = heightPixels,
        densityDpi = densityDpi,
        rotation =
            ScreenRotation.entries.firstOrNull { it.name == rotation }
                ?: ScreenRotation.PORTRAIT,
    )

private fun ScreenProfile.toDto(): ScreenProfileDto =
    ScreenProfileDto(
        widthPixels = widthPixels,
        heightPixels = heightPixels,
        densityDpi = densityDpi,
        rotation = rotation.name,
    )

private fun StepDto.toDomain(): Step =
    Step(
        id = id.toUuidOrRandom(),
        action = action.toDomain(),
        target = StepTarget(ScreenPoint(target.x(), target.y())),
        repeatCount = repeat,
        delayMillisecondsAfter = delayMillisecondsAfter,
        search = search?.toDomain(),
        guard = guard?.toDomain(),
    )

private fun Step.toDto(): StepDto =
    StepDto(
        id = id.toString(),
        action = action.toDto(),
        target = TargetDto.Point(x = target.point.x, y = target.point.y),
        repeat = repeatCount,
        delayMillisecondsAfter = delayMillisecondsAfter,
        search = search?.toDto(),
        guard = guard?.toDto(),
    )

private fun String.toUuidOrRandom(): UUID = runCatching { UUID.fromString(this) }.getOrElse { UUID.randomUUID() }

/** FS-5: a file without recognition in it stays a version 1 file (TP-28). */
private const val BASE_SCHEMA_VERSION = 1

private const val RECOGNITION_SCHEMA_VERSION = 2
