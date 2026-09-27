package com.pbh.clickify.data.scenario

import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.StepAction

/**
 * The Action half of the file format (FS-6, FS-7), split from [ScenarioMapper] only because the
 * two together are more functions than one file should hold.
 */
internal fun TargetDto.x(): Int = (this as TargetDto.Point).x

internal fun TargetDto.y(): Int = (this as TargetDto.Point).y

internal fun ActionDto.toDomain(): StepAction =
    when (this) {
        is ActionDto.Tap -> StepAction.Tap(holdMilliseconds = holdMilliseconds)
        is ActionDto.Swipe ->
            StepAction.Swipe(
                destination = ScreenPoint(x, y),
                durationMilliseconds = durationMilliseconds,
            )

        is ActionDto.MultiTouch -> StepAction.MultiTouch(paths = paths.map { it.toDomain() })
        is ActionDto.Global ->
            StepAction.Global(
                // FS-12: a name from a newer build falls back rather than throwing. BACK is the
                // least destructive of the seven.
                action = GlobalActionKind.entries.firstOrNull { it.name == action } ?: GlobalActionKind.BACK,
            )

        is ActionDto.SetText -> StepAction.SetText(text = text)
    }

internal fun StepAction.toDto(): ActionDto =
    when (this) {
        is StepAction.Tap -> ActionDto.Tap(holdMilliseconds = holdMilliseconds)
        is StepAction.Swipe ->
            ActionDto.Swipe(
                x = destination.x,
                y = destination.y,
                durationMilliseconds = durationMilliseconds,
            )

        is StepAction.MultiTouch -> ActionDto.MultiTouch(paths = paths.map { it.toDto() })
        is StepAction.Global -> ActionDto.Global(action = action.name)
        is StepAction.SetText -> ActionDto.SetText(text = text)
    }

internal fun PathDto.toDomain(): GesturePath =
    GesturePath(
        start = ScreenPoint(start.x, start.y),
        end = ScreenPoint(end.x, end.y),
        durationMilliseconds = durationMilliseconds,
    )

internal fun GesturePath.toDto(): PathDto =
    PathDto(
        start = PointDto(start.x, start.y),
        end = PointDto(end.x, end.y),
        durationMilliseconds = durationMilliseconds,
    )
