package com.pbh.clickify.domain.scenario

/**
 * Where an Action happens, kept separate from what it does (ADR-0002).
 *
 * A1 has exactly one form (SM-11). The cursor and window-relative forms macOS has are absent for
 * good — Android offers nothing for them to attach to — and the Template form arrives with A3, at
 * which point this becomes a sealed hierarchy with more than one member.
 */
@JvmInline
value class StepTarget(
    val point: ScreenPoint,
)
