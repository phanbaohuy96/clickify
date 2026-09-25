package com.pbh.clickify.domain.scenario

/**
 * A point in raw device pixels, inside the Scenario's [ScreenProfile] (SM-11, SM-12).
 *
 * Never a fraction of the screen. ADR-0013 explains why normalising looks right and is not.
 */
data class ScreenPoint(
    val x: Int,
    val y: Int,
)
