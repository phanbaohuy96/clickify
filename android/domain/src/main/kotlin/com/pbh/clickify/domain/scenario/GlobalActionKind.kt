package com.pbh.clickify.domain.scenario

/**
 * The fixed system operations Android permits, and the only keyboard-shaped thing available
 * (SM-10).
 *
 * Named, never numbered: `AccessibilityService.GLOBAL_ACTION_*` constants are integers, and a
 * scenario.json full of integers is unreadable and breaks if the platform ever renumbers them.
 * Mirrors macOS DM-21, which stores key names as strings for the same reason.
 */
enum class GlobalActionKind {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    LOCK_SCREEN,
    TAKE_SCREENSHOT,
}
