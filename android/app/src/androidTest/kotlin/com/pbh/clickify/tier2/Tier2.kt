package com.pbh.clickify.tier2

import android.app.Instrumentation
import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.pbh.clickify.service.ClickifyAccessibilityService
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.fail

/**
 * What every tier 2 test needs before it can assert anything: a connected accessibility service, and
 * a window in front that records what arrived.
 *
 * See `docs/testing.md`. The one rule these tests are written to is that a missing precondition
 * **fails** rather than skips: a silently skipped `SF-1` assertion is the exact failure mode that
 * document warns about — a harness that destroys what it is measuring and then reports a pass.
 */
object Tier2 {
    private const val TAG = "Tier2"
    private const val PACKAGE = "com.pbh.clickify"
    private const val SERVICE = "$PACKAGE/$PACKAGE.service.ClickifyAccessibilityService"
    private const val SERVICE_WAIT_MILLISECONDS = 20_000L
    private const val POLL_MILLISECONDS = 100L

    /**
     * The connected service, granted if need be and then waited for.
     *
     * The grant has to happen **here** rather than in a script that runs first, and that cost a test
     * run to learn: `connectedAndroidTest` uninstalls the application before it installs the two
     * APKs, and a component that is briefly not installed is dropped from
     * `enabled_accessibility_services` by the system. Every grant made beforehand is therefore gone
     * by the time the first test runs — `settings get` answers `null`, and all eight tests fail
     * twenty seconds apart waiting for a service nothing is going to bind.
     *
     * What makes doing it here possible is `UiAutomation.executeShellCommand`: the instrumentation
     * may not grant itself the service, but shell may, and the instrumentation can ask shell.
     *
     * Then the wait. `am instrument` restarts the application's process, the system binds the
     * service into that same process, and that takes a moment — without waiting, the first test in a
     * run fails and the rest pass.
     */
    fun awaitService(): ClickifyAccessibilityService {
        ClickifyAccessibilityService.instance?.let { return it }
        grantTheService()
        return awaitOrNull(SERVICE_WAIT_MILLISECONDS) { ClickifyAccessibilityService.instance }
            ?: fail(
                "The accessibility service never connected, ${SERVICE_WAIT_MILLISECONDS}ms after being enabled. " +
                    "`adb shell dumpsys accessibility` shows what the system thinks is bound. See docs/testing.md.",
            )
    }

    /**
     * `PM-1`…`PM-11` from the outside.
     *
     * The order is the whole of it: a sideloaded build is behind the restricted-settings wall, so
     * `ACCESS_RESTRICTED_SETTINGS` comes **first** or the line after it is reverted without a word —
     * `settings put` appears to succeed and `settings get` answers `null`.
     *
     * This clobbers any other enabled accessibility service, which is correct on a test device and
     * would not be on somebody's phone.
     */
    private fun grantTheService() {
        shell("appops set $PACKAGE ACCESS_RESTRICTED_SETTINGS allow")
        shell("appops set $PACKAGE SYSTEM_ALERT_WINDOW allow")
        shell("settings put secure enabled_accessibility_services $SERVICE")
        shell("settings put secure accessibility_enabled 1")
        val granted = shell("settings get secure enabled_accessibility_services").trim()
        if (granted != SERVICE) {
            fail(
                "the accessibility service was refused: enabled_accessibility_services holds '$granted'. " +
                    "That is the restricted-settings wall, and it means ACCESS_RESTRICTED_SETTINGS did not take.",
            )
        }
    }

    /**
     * Runs a command as shell. The instrumentation may do what the app itself may not.
     *
     * **The flag is not optional, and it is the trap this whole tier is about.** Asking for a
     * `UiAutomation` registers one, and a registered `UiAutomation` is itself an accessibility
     * service that the platform gives exclusive use of accessibility to: the moment it appears, every
     * other service is suppressed. The log says so in as many words —
     * `UiAutomationManager: Registering UiTestAutomationService`, and `dumpsys accessibility`
     * immediately reports `Bound services:{}` while still listing ours under `Enabled services`.
     *
     * So a suite that reaches for shell in order to enable the service under test thereby disables
     * it, and the symptom is not an error but a twenty-second wait for a service the system has been
     * told to hold back. `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` is the whole difference. It has
     * to be passed on the **first** call, because the flags belong to the registration.
     */
    fun shell(command: String): String {
        val descriptor = automation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString() }
    }

    private val automation: UiAutomation by lazy {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    }

    /** Polls [produce] until it answers with something, or gives up and answers null. */
    fun <T> awaitOrNull(
        timeoutMilliseconds: Long,
        produce: () -> T?,
    ): T? {
        val deadline = SystemClock.uptimeMillis() + timeoutMilliseconds
        while (SystemClock.uptimeMillis() < deadline) {
            produce()?.let { return it }
            SystemClock.sleep(POLL_MILLISECONDS)
        }
        return produce()
    }

    /** Polls until [condition] holds, and fails saying what never happened. */
    fun awaitTrue(
        timeoutMilliseconds: Long,
        what: String,
        condition: () -> Boolean,
    ) {
        awaitOrNull(timeoutMilliseconds) { true.takeIf { condition() } }
            ?: fail("waited ${timeoutMilliseconds}ms and $what never happened")
    }

    val instrumentation: Instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /**
     * Writes a measurement into logcat under one tag, which `tools/testing/tier2.sh` prints after the
     * run.
     *
     * The numbers in `docs/testing.md` come from here. A passing test that reports nothing means the
     * next person has to rebuild the harness to learn what the machine actually does, and a ceiling
     * nobody can see the distance to is a ceiling nobody can tighten.
     */
    fun note(message: String) {
        Log.i(TAG, message)
    }
}

/**
 * The target app, in front, for the length of one test.
 *
 * [bounds] is read from the window rather than assumed, so a test picks points that this screen
 * really delivers to it instead of points that happen to work on one emulator.
 */
class TargetApp private constructor(
    private val scenario: ActivityScenario<TouchLogActivity>,
    private val activity: TouchLogActivity,
    val bounds: Rect,
) : AutoCloseable {
    val arrived: List<ArrivedTouch> get() = activity.arrived

    fun forget() = activity.forget()

    /** Waits for the touch stream to go quiet, so an assertion is not racing a trailing event. */
    fun settle(quietMilliseconds: Long = SETTLE_MILLISECONDS) {
        var seen = -1
        while (seen != arrived.size) {
            seen = arrived.size
            SystemClock.sleep(quietMilliseconds)
        }
    }

    override fun close() = scenario.close()

    companion object {
        private const val SETTLE_MILLISECONDS = 300L

        fun launch(): TargetApp {
            val scenario = ActivityScenario.launch(TouchLogActivity::class.java)
            val found = AtomicReference<TouchLogActivity>()
            val frame = AtomicReference<Rect>()
            scenario.onActivity {
                found.set(it)
                frame.set(it.touchableBounds())
            }
            val activity = found.get() ?: error("the target Activity never started")
            val bounds = frame.get()
            check(!bounds.isEmpty) { "the target Activity reported an empty window: $bounds" }
            // Compose nothing, wait for nothing else: the window has to be the one in front before
            // a gesture is dispatched, or the touch lands on whatever still is.
            SystemClock.sleep(SETTLE_MILLISECONDS)
            return TargetApp(scenario, activity, bounds)
        }
    }
}

/** Every contact that started. */
fun List<ArrivedTouch>.downs(): List<ArrivedTouch> = filter { it.isDown }

/**
 * `SF-1` in one line: nothing is still being touched.
 *
 * Counting is enough because `RD-2`'s one-finger-at-a-time also holds for what this app dispatches —
 * `GX-15` starts every stroke of a gesture together, and the tests that use this dispatch one.
 */
fun List<ArrivedTouch>.assertNoPointerLeftDown() {
    val started = count { it.isDown }
    val ended = count { it.endsTheContact }
    if (started != ended) {
        fail("$started contact(s) started and only $ended ended — a finger was left on the screen:\n${joinToString("\n")}")
    }
}
