package com.pbh.clickify.domain.overlay

import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import java.util.UUID

/**
 * One on-screen handle showing where a Step will act (OV-5 to OV-9).
 *
 * Deciding what to draw is arithmetic over the Scenario and belongs nowhere near a Composable, so
 * it lives here and is tested on the JVM. Drawing it is the Overlay's job and this says nothing
 * about how it looks.
 */
data class Marker(
    val stepId: UUID,
    /** OV-6: the Step's place in the Scenario, counting from 1. Reordering renumbers. */
    val stepNumber: Int,
    val point: ScreenPoint,
    val role: MarkerRole,
    /** OV-8: the other end of the line this Marker is drawn on, if it is drawn on one. */
    val connectedTo: ScreenPoint? = null,
    /** Which path of a multiTouch Step this belongs to. Always 0 for the other Actions. */
    val pathIndex: Int = 0,
)

enum class MarkerRole {
    /** A tap. One Marker, one point. */
    POINT,

    /** OV-8: where a swipe begins. Carries the Step number. */
    SWIPE_START,

    /** OV-8: where a swipe ends. Drawn as an arrow rather than a number. */
    SWIPE_END,

    /** OV-9: one contact of a multiTouch Step. Every one carries the same Step number. */
    TOUCH_START,

    TOUCH_END,
}

/**
 * Every Marker this Scenario draws, in Step order (OV-5).
 *
 * A Step whose Action ignores its Target contributes none — its place in the order is shown in the
 * Scenario list instead, because a Marker that did nothing would look like a bug (`SM-8`).
 */
fun Scenario.markers(): List<Marker> =
    steps.flatMapIndexed { index, step ->
        val number = index + 1
        when (val action = step.action) {
            is StepAction.Tap ->
                listOf(
                    Marker(step.id, number, step.target.point, MarkerRole.POINT),
                )

            is StepAction.Swipe ->
                listOf(
                    Marker(
                        stepId = step.id,
                        stepNumber = number,
                        point = step.target.point,
                        role = MarkerRole.SWIPE_START,
                        connectedTo = action.destination,
                    ),
                    Marker(
                        stepId = step.id,
                        stepNumber = number,
                        point = action.destination,
                        role = MarkerRole.SWIPE_END,
                        connectedTo = step.target.point,
                    ),
                )

            is StepAction.MultiTouch ->
                action.paths.flatMapIndexed { pathIndex, path ->
                    val moves = path.start != path.end
                    listOfNotNull(
                        Marker(
                            stepId = step.id,
                            stepNumber = number,
                            point = path.start,
                            role = MarkerRole.TOUCH_START,
                            connectedTo = path.end.takeIf { moves },
                            pathIndex = pathIndex,
                        ),
                        if (moves) {
                            Marker(
                                stepId = step.id,
                                stepNumber = number,
                                point = path.end,
                                role = MarkerRole.TOUCH_END,
                                connectedTo = path.start,
                                pathIndex = pathIndex,
                            )
                        } else {
                            null
                        },
                    )
                }

            is StepAction.Global, is StepAction.SetText -> emptyList()
        }
    }

/**
 * OV-10: a Marker stops at the edge of the screen rather than being refused when it is saved.
 *
 * Clamping while dragging is kinder than validating on save — the user finds out where the limit
 * is by meeting it, not by being told afterwards that the work is invalid.
 */
fun ScreenPoint.clampedInto(profile: ScreenProfile): ScreenPoint =
    ScreenPoint(
        x = x.coerceIn(0, profile.widthPixels - 1),
        y = y.coerceIn(0, profile.heightPixels - 1),
    )

/**
 * The Scenario with the Step behind [marker] moved to [to] (OV-7).
 *
 * Which end moves is decided by the Marker's [Marker.role], so dragging the arrow of a swipe edits
 * the destination and dragging its number edits the start. A Marker naming a Step that is no
 * longer there leaves the Scenario untouched rather than throwing — Markers are redrawn from the
 * Scenario, so the two can be a frame apart.
 */
fun Scenario.withMarkerMoved(
    marker: Marker,
    to: ScreenPoint,
): Scenario =
    copy(
        steps =
            steps.map { step ->
                if (step.id != marker.stepId) {
                    step
                } else {
                    when (marker.role) {
                        MarkerRole.POINT, MarkerRole.SWIPE_START -> step.copy(target = StepTarget(to))

                        MarkerRole.SWIPE_END ->
                            when (val action = step.action) {
                                is StepAction.Swipe -> step.copy(action = action.copy(destination = to))
                                else -> step
                            }

                        MarkerRole.TOUCH_START, MarkerRole.TOUCH_END ->
                            when (val action = step.action) {
                                is StepAction.MultiTouch -> step.copy(action = action.movedPath(marker, to))
                                else -> step
                            }
                    }
                }
            },
    )

private fun StepAction.MultiTouch.movedPath(
    marker: Marker,
    to: ScreenPoint,
): StepAction.MultiTouch =
    copy(
        paths =
            paths.mapIndexed { index, path ->
                when {
                    index != marker.pathIndex -> path
                    marker.role == MarkerRole.TOUCH_START -> path.copy(start = to)
                    else -> path.copy(end = to)
                }
            },
    )
