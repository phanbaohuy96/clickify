package com.pbh.clickify.domain.editor

import com.pbh.clickify.domain.scenario.GestureLimits
import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.Guard
import com.pbh.clickify.domain.scenario.ScenarioLimits
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.domain.scenario.StepViolation
import com.pbh.clickify.domain.scenario.TemplateSearch
import com.pbh.clickify.domain.scenario.violations
import java.util.UUID

/** Which of the five Actions (SM-7) a draft currently wears. */
enum class StepActionKind {
    TAP,
    SWIPE,
    MULTI_TOUCH,
    GLOBAL_ACTION,
    SET_TEXT,
}

/** TP-22, SM-8: whether a Step of this kind reads its Target at all. */
val StepActionKind.usesTarget: Boolean
    get() =
        when (this) {
            StepActionKind.TAP, StepActionKind.SWIPE, StepActionKind.MULTI_TOUCH -> true
            StepActionKind.GLOBAL_ACTION, StepActionKind.SET_TEXT -> false
        }

/** The kind this Action already is, so the panel can show the right row selected. */
val StepAction.kind: StepActionKind
    get() =
        when (this) {
            is StepAction.Tap -> StepActionKind.TAP
            is StepAction.Swipe -> StepActionKind.SWIPE
            is StepAction.MultiTouch -> StepActionKind.MULTI_TOUCH
            is StepAction.Global -> StepActionKind.GLOBAL_ACTION
            is StepAction.SetText -> StepActionKind.SET_TEXT
        }

/**
 * One Step as the editor holds it while it is being configured (OV-21).
 *
 * It carries the fields of **all five** Actions at once and not only the one in force, which is
 * the whole point of it existing. Choosing `swipe`, looking at it, and choosing `tap` again must
 * not throw the destination away: a user trying the five Actions out is doing the thing the panel
 * is for, and losing their work for it is the fastest way to make an editor unpleasant.
 * Only [kind] decides what [toStep] builds; everything else waits.
 *
 * Nothing here is clamped. SM-17 refuses an out-of-range value at the editor rather than quietly
 * correcting it, and [violations] is how the panel finds out.
 */
data class StepDraft(
    val stepId: UUID,
    val kind: StepActionKind,
    /** SM-8: kept even by the two Actions that ignore it, so switching back restores the Marker. */
    val target: ScreenPoint,
    val swipeDestination: ScreenPoint,
    val holdMilliseconds: Long = 0L,
    val swipeDurationMilliseconds: Long = DEFAULT_TRAVEL_MILLISECONDS,
    val paths: List<GesturePath> = emptyList(),
    val globalAction: GlobalActionKind = GlobalActionKind.BACK,
    val text: String = "",
    val repeatCount: Int = 1,
    val delayMillisecondsAfter: Int = ScenarioLimits.DEFAULT_DELAY_MILLISECONDS,
    /** TP-19: the Template this Step aims at, or null when its point is simply its point. */
    val search: TemplateSearch? = null,
    /** TP-24: the condition waited for before the Action, or null when there is none. */
    val guard: Guard? = null,
)

/** The Step this draft would save as. */
fun StepDraft.toStep(): Step =
    Step(
        id = stepId,
        action =
            when (kind) {
                StepActionKind.TAP -> StepAction.Tap(holdMilliseconds)
                StepActionKind.SWIPE -> StepAction.Swipe(swipeDestination, swipeDurationMilliseconds)
                StepActionKind.MULTI_TOUCH -> StepAction.MultiTouch(paths)
                StepActionKind.GLOBAL_ACTION -> StepAction.Global(globalAction)
                StepActionKind.SET_TEXT -> StepAction.SetText(text)
            },
        // SM-8: the two Actions that ignore a Target still get one, so the model needs no optional.
        target = StepTarget(target),
        repeatCount = repeatCount,
        delayMillisecondsAfter = delayMillisecondsAfter,
        // TP-22: kept whichever Action is in force, and read only by the three that use a Target.
        search = search,
        guard = guard,
    )

/**
 * This Step opened in the editor, with the four Actions it is *not* filled in from defaults.
 *
 * [profile] is the screen the invented points are measured against. It is nullable because a
 * Scenario has no Screen profile until its first Marker is placed (`SM-14`), and a Step can be
 * opened in that moment.
 */
fun Step.toDraft(profile: ScreenProfile?): StepDraft {
    val current = action
    return StepDraft(
        stepId = id,
        kind = current.kind,
        target = target.point,
        swipeDestination = (current as? StepAction.Swipe)?.destination ?: defaultDestination(target.point, profile),
        holdMilliseconds = (current as? StepAction.Tap)?.holdMilliseconds ?: 0L,
        swipeDurationMilliseconds =
            (current as? StepAction.Swipe)?.durationMilliseconds ?: DEFAULT_TRAVEL_MILLISECONDS,
        paths = (current as? StepAction.MultiTouch)?.paths ?: defaultPaths(target.point, profile),
        globalAction = (current as? StepAction.Global)?.action ?: GlobalActionKind.BACK,
        text = (current as? StepAction.SetText)?.text.orEmpty(),
        repeatCount = repeatCount,
        delayMillisecondsAfter = delayMillisecondsAfter,
        search = search,
        guard = guard,
    )
}

/**
 * Every reason this draft cannot be saved (`SM-17`), which is [Step.violations] over [toStep].
 *
 * Only the Action in force is judged. A destination left over from a swipe the user backed out of
 * is not a reason to refuse the tap they settled on.
 */
fun StepDraft.violations(
    limits: GestureLimits = GestureLimits(),
    profile: ScreenProfile? = null,
    knownTemplates: Set<UUID>? = null,
): List<StepViolation> = toStep().violations(limits, profile, knownTemplates)

/**
 * One more contact for a multi-touch Step, placed beside the last one (`OV-9`).
 *
 * Returns the draft unchanged once [GestureLimits.maxStrokeCount] is reached, so the panel can
 * disable the button and the two agree without a second rule.
 */
fun StepDraft.withPathAdded(
    limits: GestureLimits = GestureLimits(),
    profile: ScreenProfile? = null,
): StepDraft {
    if (paths.size >= limits.maxStrokeCount) return this
    val from = paths.lastOrNull()?.start ?: target
    return copy(paths = paths + GesturePath(nextContact(from, profile), nextContact(from, profile), DEFAULT_TRAVEL_MILLISECONDS))
}

/**
 * One contact fewer, never below one.
 *
 * One path is a swipe rather than a multi-touch, so the last step down is a violation the panel
 * shows (`StepViolation.TooFewPaths`) rather than a removal it refuses — but going to *none*
 * would leave nothing on screen to explain what is wrong.
 */
fun StepDraft.withPathRemoved(index: Int): StepDraft =
    if (paths.size <= 1 || index !in paths.indices) this else copy(paths = paths.filterIndexed { at, _ -> at != index })

/**
 * This draft with [step]'s points, which is what a Marker dragged while the panel is open produces
 * (`OV-21`).
 *
 * Only the Action in force has points, so only its fields move. Everything the draft is holding on
 * behalf of the other four Actions is left alone — dragging a tap's Marker must not throw away the
 * swipe destination the user set five seconds earlier and may yet come back to.
 */
fun StepDraft.withPointsFrom(step: Step): StepDraft =
    when (val moved = step.action) {
        is StepAction.Tap -> copy(target = step.target.point)
        is StepAction.Swipe -> copy(target = step.target.point, swipeDestination = moved.destination)
        is StepAction.MultiTouch -> copy(target = step.target.point, paths = moved.paths)
        is StepAction.Global, is StepAction.SetText -> this
    }
