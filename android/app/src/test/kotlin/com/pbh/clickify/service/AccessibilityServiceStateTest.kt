package com.pbh.clickify.service

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The parsing half of PM-6. The setting is a colon-separated list written by the system in two
 * different spellings, and getting it wrong means the app believes it is switched off while it is
 * running — or the reverse, which is worse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityServiceStateTest {
    @Test
    fun `nothing enabled reads as nothing`() {
        assertEquals(emptyList(), AccessibilityServiceState.enabledComponents(null))
        assertEquals(emptyList(), AccessibilityServiceState.enabledComponents(""))
    }

    @Test
    fun `one service is one component`() {
        val components =
            AccessibilityServiceState.enabledComponents(
                "com.pbh.clickify/com.pbh.clickify.service.ClickifyAccessibilityService",
            )

        assertEquals(1, components.size)
        assertEquals("com.pbh.clickify", components.single().packageName)
    }

    @Test
    fun `several services are separated by colons`() {
        val components =
            AccessibilityServiceState.enabledComponents(
                "com.other/.Service:com.pbh.clickify/.service.ClickifyAccessibilityService",
            )

        assertEquals(listOf("com.other", "com.pbh.clickify"), components.map { it.packageName })
    }

    @Test
    fun `the short spelling is understood too`() {
        val components = AccessibilityServiceState.enabledComponents("com.pbh.clickify/.service.Foo")

        assertEquals("com.pbh.clickify.service.Foo", components.single().className)
    }

    @Test
    fun `an entry that is not a component is skipped rather than thrown on`() {
        val components =
            AccessibilityServiceState.enabledComponents("nonsense::com.pbh.clickify/.Service")

        assertEquals(1, components.size)
    }
}
