package com.pbh.clickify.domain.editor

import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import java.util.UUID

// The four things the Step panel does to a Scenario, and the one rule they all obey.
//
// That rule is SM-14: the Screen profile follows the Markers. It is applied in one place here
// rather than at each call site, because a Scenario that gained a Marker without gaining a profile
// would hold raw pixels measured against nothing — which is exactly what ADR-0013 exists to stop.

/** A new Step at [at]: a tap, because it is the Action that needs no further decision to be valid. */
fun newStep(at: ScreenPoint): Step = Step(action = StepAction.Tap(), target = StepTarget(at))

/**
 * The Scenario as it would look with [step] in place of the one that shares its identifier.
 *
 * For **drawing only**: it applies none of `SM-14`'s Screen profile rules, because a draft that
 * has not been saved must not be able to change the Scenario's profile. It is what lets the
 * Markers show the Step being configured rather than the Step last written to disk (`OV-21`).
 */
fun Scenario.previewing(step: Step): Scenario = copy(steps = steps.map { if (it.id == step.id) step else it })

/** [step] appended, and the Screen profile captured if this is the Scenario's first Marker. */
fun Scenario.withStepAdded(
    step: Step,
    profile: ScreenProfile,
): Scenario = copy(steps = steps + step).withProfileFollowingMarkers(profile)

/**
 * RD-8: a recording is **added** to the Scenario rather than replacing it.
 *
 * Recording more onto something half-built is the ordinary case — the user records, tries it,
 * records the next part — and a session that silently threw away the earlier work would make the
 * button too dangerous to press.
 */
fun Scenario.withStepsAdded(
    added: List<Step>,
    profile: ScreenProfile,
): Scenario = if (added.isEmpty()) this else copy(steps = steps + added).withProfileFollowingMarkers(profile)

/**
 * The Step with the same identifier replaced by [step].
 *
 * [profile] is needed because a replacement can *create* the first Marker — changing a `setText`
 * Step to a `tap` does exactly that (`SM-8`).
 */
fun Scenario.withStepReplaced(
    step: Step,
    profile: ScreenProfile,
): Scenario = copy(steps = steps.map { if (it.id == step.id) step else it }).withProfileFollowingMarkers(profile)

/** The Step gone, and every Marker after it renumbered by being one place earlier (`OV-6`). */
fun Scenario.withStepRemoved(stepId: UUID): Scenario = copy(steps = steps.filterNot { it.id == stepId }).withProfileDroppedIfUnused()

/**
 * The Step moved [by] places in the order, which renumbers its Marker and its neighbours' (`OV-6`).
 *
 * A move past either end is not an error and does nothing: the buttons that call this are disabled
 * there, and a Step being dragged off the end of a list is a slip, not an instruction.
 */
fun Scenario.withStepMoved(
    stepId: UUID,
    by: Int,
): Scenario {
    val from = steps.indexOfFirst { it.id == stepId }
    val to = from + by
    if (from < 0 || to !in steps.indices) return this
    val reordered = steps.toMutableList()
    reordered.add(to, reordered.removeAt(from))
    return copy(steps = reordered)
}

/**
 * `SM-14`: the profile is captured when the Scenario gains its first Marker, and kept.
 *
 * Kept, and not re-captured: a Scenario built in portrait keeps its portrait profile when a Step is
 * edited in landscape, so the mismatch shows up at `SM-15` where it can be explained, rather than
 * being quietly overwritten with a screen the coordinates were never measured against.
 */
private fun Scenario.withProfileFollowingMarkers(current: ScreenProfile): Scenario =
    when {
        markerCount == 0 -> withProfileDroppedIfUnused()
        screenProfile == null -> copy(screenProfile = current)
        else -> this
    }

/**
 * `SM-14`, the other direction: a Scenario with no Marker left forgets its Screen profile.
 *
 * The profile exists to make coordinates meaningful. With no coordinates there is nothing left to
 * protect, and a profile kept past its last Marker would block a run (`SM-15`) that could not press
 * anything wrong — the user would be told the screen had changed by a Scenario that no longer
 * touches the screen anywhere.
 */
private fun Scenario.withProfileDroppedIfUnused(): Scenario =
    if (markerCount == 0 && screenProfile != null) copy(screenProfile = null) else this
