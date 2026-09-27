package com.pbh.clickify.data.scenario

import com.pbh.clickify.domain.scenario.Guard
import com.pbh.clickify.domain.scenario.OnTimeout
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.ScreenRegion
import com.pbh.clickify.domain.scenario.TemplateSearch
import java.util.UUID

/**
 * The recognition half of the file format (TP-19, TP-21, TP-24), split from [ScenarioMapper] only
 * because the two together are more functions than one file should hold.
 *
 * Reading is the forgiving direction, as everywhere else in FS-12: a name this build does not know
 * falls back to the **safest** member rather than throwing. For a timeout that is *stop the
 * Scenario* — a run that ends is a run the user can see, and a run that silently skipped a Step it
 * should have stopped at is not. For a Guard it is *present*, which is the reading that makes the
 * Step wait rather than the one that makes it fire.
 */
internal fun SearchDto.toDomain(): TemplateSearch =
    TemplateSearch(
        templateId = UUID.fromString(template),
        threshold = threshold,
        region = region?.toDomain(),
        waitMilliseconds = waitMilliseconds,
        onTimeout = OnTimeout.entries.firstOrNull { it.name == onTimeout } ?: OnTimeout.STOP_SCENARIO,
    )

internal fun TemplateSearch.toDto(): SearchDto =
    SearchDto(
        template = templateId.toString(),
        threshold = threshold,
        region = region?.toDto(),
        waitMilliseconds = waitMilliseconds,
        onTimeout = onTimeout.name,
    )

internal fun GuardDto.toDomain(): Guard =
    Guard(
        search = search.toDomain(),
        expects = Presence.entries.firstOrNull { it.name == expects } ?: Presence.PRESENT,
    )

internal fun Guard.toDto(): GuardDto = GuardDto(search = search.toDto(), expects = expects.name)

internal fun RegionDto.toDomain(): ScreenRegion = ScreenRegion(left = left, top = top, right = right, bottom = bottom)

internal fun ScreenRegion.toDto(): RegionDto = RegionDto(left = left, top = top, right = right, bottom = bottom)
