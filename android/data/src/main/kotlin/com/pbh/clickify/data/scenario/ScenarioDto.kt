package com.pbh.clickify.data.scenario

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * The on-disk shape of a Scenario (FS-5 to FS-10).
 *
 * Kept separate from the domain model on purpose: this is a **file format**, and a format changes
 * for reasons the model does not share. Every discriminator and field name here is part of the
 * specification and cannot be renamed by refactoring the model.
 */
@Serializable
data class ScenarioDto(
    val schemaVersion: Int,
    val id: String,
    val name: String = "",
    /** FS-9: an integer, or the string `until-stopped`. */
    val repeat: RepeatDto = RepeatDto.Count(1),
    val countdownMilliseconds: Int = 3_000,
    val screenProfile: ScreenProfileDto? = null,
    val steps: List<StepDto> = emptyList(),
)

/** FS-9. Serialised as a bare integer or a bare string, never as an object. */
@Serializable(with = RepeatSerializer::class)
sealed interface RepeatDto {
    data class Count(
        val value: Int,
    ) : RepeatDto

    data object UntilStopped : RepeatDto {
        const val TOKEN = "until-stopped"
    }
}

@Serializable
data class ScreenProfileDto(
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int,
    val rotation: String,
)

@Serializable
data class StepDto(
    val id: String,
    val action: ActionDto,
    val target: TargetDto,
    val repeat: Int = 1,
    val delayMillisecondsAfter: Int = 100,
    /** TP-19: absent in a schema 1 file, and absent from any Step that aims at a plain point. */
    val search: SearchDto? = null,
    /** TP-24. */
    val guard: GuardDto? = null,
)

/**
 * A Template search on disk (TP-19, TP-21).
 *
 * `template` names a file under `templates/`, and `onTimeout` is a name rather than a number for
 * the same reason `globalAction` is (SM-10): the file has to stay readable.
 */
@Serializable
data class SearchDto(
    val template: String,
    val threshold: Double = 0.90,
    val region: RegionDto? = null,
    val waitMilliseconds: Int = 5_000,
    val onTimeout: String = "STOP_SCENARIO",
)

/** FS-7 again: four named sides, never an array. */
@Serializable
data class RegionDto(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

@Serializable
data class GuardDto(
    val search: SearchDto,
    val expects: String = "PRESENT",
)

/** FS-7: separate `x` and `y`, never an array. */
@Serializable
data class PointDto(
    val x: Int,
    val y: Int,
)

/** FS-6: the discriminator is spelled out, so the file reads as prose rather than as a dump. */
@Serializable
@JsonClassDiscriminator("kind")
sealed interface TargetDto {
    @Serializable
    @SerialName("point")
    data class Point(
        val x: Int,
        val y: Int,
    ) : TargetDto
}

@Serializable
@JsonClassDiscriminator("kind")
sealed interface ActionDto {
    @Serializable
    @SerialName("tap")
    data class Tap(
        val holdMilliseconds: Long = 0L,
    ) : ActionDto

    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val x: Int,
        val y: Int,
        val durationMilliseconds: Long,
    ) : ActionDto

    @Serializable
    @SerialName("multiTouch")
    data class MultiTouch(
        val paths: List<PathDto>,
    ) : ActionDto

    @Serializable
    @SerialName("globalAction")
    data class Global(
        val action: String,
    ) : ActionDto

    @Serializable
    @SerialName("setText")
    data class SetText(
        val text: String,
    ) : ActionDto
}

@Serializable
data class PathDto(
    val start: PointDto,
    val end: PointDto,
    val durationMilliseconds: Long,
)
