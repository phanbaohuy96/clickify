package com.pbh.clickify.domain.editor

import com.pbh.clickify.domain.overlay.clampedInto
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget

/**
 * SM-18: the same Steps, measured against [profile] instead of the screen they were built on.
 *
 * The one operation `SM-14` does not allow by itself. A **Scenario** keeps the **Screen profile**
 * captured with its first **Marker** precisely so a mismatch is reported rather than quietly
 * overwritten — but a user who built in portrait and now wants the same **Scenario** in landscape
 * has no other way out, and deleting twelve **Step**s to change one fact is not a way out.
 *
 * What survives is everything except the coordinates' meaning: the **Step**s, their order, their
 * **Action**s, their timings. The points are **clamped** into the new screen rather than
 * transformed, and that is the honest part. There is no transform: rotating a point 90° is true
 * about the display and false about the application, whose buttons are somewhere else entirely
 * once it has re-laid itself out ([ADR-0013]). Clamping only guarantees every **Marker** is
 * reachable so it can be aimed again.
 *
 * A **Scenario** with no **Marker** is returned untouched, because `SM-14` says it has no profile
 * to rebuild.
 *
 * [ADR-0013]: ../../../../../../../docs/adr/0013-raw-pixels.md
 */
fun Scenario.rebuiltFor(profile: ScreenProfile): Scenario =
    if (markerCount == 0) {
        this
    } else {
        copy(steps = steps.map { it.withPointsClampedInto(profile) }, screenProfile = profile)
    }

private fun Step.withPointsClampedInto(profile: ScreenProfile): Step =
    copy(
        target = StepTarget(target.point.clampedInto(profile)),
        action = action.withPointsClampedInto(profile),
        // TP-9: a Search region is display coordinates too, so it is clamped by the same rule.
        // The Template itself is left alone — its pixels are a picture, not a position.
        search = search?.let { it.copy(region = it.region?.clampedInto(profile)) },
        guard = guard?.let { open -> open.copy(search = open.search.copy(region = open.search.region?.clampedInto(profile))) },
    )

private fun StepAction.withPointsClampedInto(profile: ScreenProfile): StepAction =
    when (this) {
        is StepAction.Swipe -> copy(destination = destination.clampedInto(profile))

        is StepAction.MultiTouch ->
            copy(
                paths =
                    paths.map {
                        it.copy(start = it.start.clampedInto(profile), end = it.end.clampedInto(profile))
                    },
            )

        is StepAction.Tap, is StepAction.Global, is StepAction.SetText -> this
    }
