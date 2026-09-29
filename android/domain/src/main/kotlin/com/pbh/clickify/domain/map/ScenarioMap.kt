package com.pbh.clickify.domain.map

import com.pbh.clickify.domain.overlay.Marker
import com.pbh.clickify.domain.overlay.clampedInto
import com.pbh.clickify.domain.overlay.markers
import com.pbh.clickify.domain.scenario.OnTimeout
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import java.util.UUID

/**
 * A Scenario laid out for the Scenario map (`MP-*`), in raw screen pixels.
 *
 * Scaling into a frame is the screen's job and this says nothing about how anything looks. Like
 * [Marker] it is arithmetic over a Scenario, so it lives here and is tested on the JVM. Nothing in
 * it is a branch: every arrow joins one Step's anchor to the next Step's (ADR-0011).
 */
data class ScenarioMap(
    /** `MP-3`: the Scenario's own profile, or null while it has none. */
    val frame: ScreenProfile?,
    /** `MP-4`: one per distinct anchor, in the order their first Step comes. */
    val nodes: List<MapNode>,
    /** `MP-4`: Steps without a point, each with a computed place. */
    val chips: List<MapChip>,
    /** `MP-4`: anchor to the next anchor, in Step order. */
    val arrows: List<MapArrow>,
    /** `MP-4`: [markers] unchanged, so a swipe and a multiTouch keep their shape. */
    val markers: List<Marker>,
    /** `MP-5`: one per effective search. */
    val templates: List<MapTemplate>,
    /** `MP-6`. */
    val guards: List<MapGuard>,
    /** `MP-7`. */
    val giveUps: List<MapGiveUp>,
)

/** `MP-4`: one dot, standing for every Step whose anchor is [point]. */
data class MapNode(
    val point: ScreenPoint,
    val stepNumbers: List<Int>,
    val stepIds: List<UUID>,
)

/** `MP-4`: a Step with no point, placed on the path rather than given coordinates. */
data class MapChip(
    val stepId: UUID,
    val stepNumber: Int,
    val position: ScreenPoint,
    /** The number of the Step with a point before this one, or null when this comes first. */
    val previousAnchorStep: Int?,
    /** The number of the Step with a point after this one, or null when this comes last. */
    val nextAnchorStep: Int?,
)

/** `MP-4`: joins the Steps numbered [fromStep] and [toStep], which are neighbours among those with a point. */
data class MapArrow(
    val fromStep: Int,
    val toStep: Int,
    val from: ScreenPoint,
    val to: ScreenPoint,
)

/** `MP-5`: [center] is where the Template was cropped (`TP-23`). */
data class MapTemplate(
    val stepId: UUID,
    val stepNumber: Int,
    val templateId: UUID,
    val center: ScreenPoint,
)

/** `MP-6`. */
data class MapGuard(
    val stepId: UUID,
    val stepNumber: Int,
    val presence: Presence,
)

enum class GiveUpKind {
    SEARCH,
    GUARD,
}

/**
 * `MP-7`: what happens when this Step's search or Guard runs out of time.
 *
 * [at] is where the Step is drawn, and [before] and [after] are the anchors of the Steps with a
 * point that come either side of it — the two ends a skip is drawn between, never any other Step.
 */
data class MapGiveUp(
    val stepId: UUID,
    val stepNumber: Int,
    val kind: GiveUpKind,
    val outcome: OnTimeout,
    val at: ScreenPoint?,
    val before: ScreenPoint?,
    val after: ScreenPoint?,
)

/** A frame to lay chips out in when the Scenario has no profile, when the screen supplies none. */
val NOMINAL_FRAME = ScreenProfile(NOMINAL_WIDTH, NOMINAL_HEIGHT, NOMINAL_DENSITY, ScreenRotation.PORTRAIT)

/**
 * The map of this Scenario (`MP-4` to `MP-7`).
 *
 * [fallbackFrame] is the current display, used only for the places chips take when there is no
 * profile to take them from (`MP-3`, `MP-4`).
 */
fun Scenario.map(fallbackFrame: ScreenProfile = NOMINAL_FRAME): ScenarioMap {
    val bounds = screenProfile ?: fallbackFrame
    val anchored = steps.withIndex().filter { it.value.hasMarker }
    val anchorOf = { index: Int -> steps[index].target.point }

    val nodes =
        anchored
            .groupBy { anchorOf(it.index) }
            .map { (point, group) ->
                MapNode(point, group.map { it.index + 1 }, group.map { it.value.id })
            }

    val arrows =
        anchored.zipWithNext().mapNotNull { (one, other) ->
            val from = anchorOf(one.index)
            val to = anchorOf(other.index)
            MapArrow(one.index + 1, other.index + 1, from, to).takeIf { from != to }
        }

    val chips = chipsFor(anchored.map { it.index }, bounds)

    return ScenarioMap(
        frame = screenProfile,
        nodes = nodes,
        chips = chips,
        arrows = arrows,
        markers = markers(),
        templates =
            steps.mapIndexedNotNull { index, step ->
                step.effectiveSearch?.let { MapTemplate(step.id, index + 1, it.templateId, step.target.point) }
            },
        guards = steps.mapIndexedNotNull { index, step -> step.guard?.let { MapGuard(step.id, index + 1, it.expects) } },
        giveUps = giveUps(anchored.map { it.index }, chips),
    )
}

/** `MP-4`: every Step without a point, a run of them at a time. */
private fun Scenario.chipsFor(
    anchoredIndexes: List<Int>,
    bounds: ScreenProfile,
): List<MapChip> {
    val pointless = steps.indices.filter { it !in anchoredIndexes }
    if (anchoredIndexes.isEmpty()) {
        return pointless.mapIndexed { column, index ->
            val y = bounds.heightPixels * (column + 1) / (pointless.size + 1)
            MapChip(steps[index].id, index + 1, ScreenPoint(bounds.widthPixels / 2, y), null, null)
        }
    }
    return pointless
        .runsOfNeighbours()
        .flatMap { run ->
            val before = anchoredIndexes.lastOrNull { it < run.first() }
            val after = anchoredIndexes.firstOrNull { it > run.last() }
            run.mapIndexed { slot, index ->
                val place = chipPlace(before?.let { steps[it].target.point }, after?.let { steps[it].target.point }, slot, run.size)
                MapChip(steps[index].id, index + 1, place.clampedInto(bounds), before?.plus(1), after?.plus(1))
            }
        }
}

/** `MP-4`: [slot] of [count] chips in a row, between [before] and [after] where those exist. */
private fun chipPlace(
    before: ScreenPoint?,
    after: ScreenPoint?,
    slot: Int,
    count: Int,
): ScreenPoint =
    when {
        before != null && after != null && before != after ->
            ScreenPoint(
                before.x + (after.x - before.x) * (slot + 1) / (count + 1),
                before.y + (after.y - before.y) * (slot + 1) / (count + 1),
            )

        // Nowhere along a segment of no length to spread out on, so they hang below the dot.
        before != null -> ScreenPoint(before.x, before.y + CHIP_OFFSET_PIXELS * (slot + 1))
        after != null -> ScreenPoint(after.x, after.y - CHIP_OFFSET_PIXELS * (count - slot))
        else -> error("a Scenario with chips and no anchor is laid out as a column")
    }

private fun List<Int>.runsOfNeighbours(): List<List<Int>> {
    val runs = mutableListOf<MutableList<Int>>()
    for (index in this) {
        if (runs.isNotEmpty() && runs.last().last() == index - 1) runs.last() += index else runs += mutableListOf(index)
    }
    return runs
}

private fun Scenario.giveUps(
    anchoredIndexes: List<Int>,
    chips: List<MapChip>,
): List<MapGiveUp> =
    steps.flatMapIndexed { index, step ->
        val before = anchoredIndexes.lastOrNull { it < index }?.let { steps[it].target.point }
        val after = anchoredIndexes.firstOrNull { it > index }?.let { steps[it].target.point }
        val at = if (step.hasMarker) step.target.point else chips.firstOrNull { it.stepId == step.id }?.position
        listOfNotNull(
            step.effectiveSearch?.let { MapGiveUp(step.id, index + 1, GiveUpKind.SEARCH, it.onTimeout, at, before, after) },
            step.guard?.let { MapGiveUp(step.id, index + 1, GiveUpKind.GUARD, it.search.onTimeout, at, before, after) },
        )
    }

/** How far a chip sits from the dot it hangs off, in raw pixels. */
private const val CHIP_OFFSET_PIXELS = 96
private const val NOMINAL_WIDTH = 1080
private const val NOMINAL_HEIGHT = 1920
private const val NOMINAL_DENSITY = 420
