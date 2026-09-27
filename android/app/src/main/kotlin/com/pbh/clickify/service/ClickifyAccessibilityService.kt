package com.pbh.clickify.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.pbh.clickify.domain.run.Gesture
import com.pbh.clickify.domain.run.GestureDispatcher
import com.pbh.clickify.domain.run.GestureOutcome
import com.pbh.clickify.domain.scenario.GestureLimits
import com.pbh.clickify.domain.scenario.GlobalActionKind
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * The only way Clickify can touch another application, and therefore the whole app's floor.
 *
 * Holds no run state and makes no decisions: the rules about order, repetition and stopping live
 * in `ScenarioRunner`, which is tested on the JVM. This class translates and nothing else.
 *
 * Declared `isAccessibilityTool="false"` (PM-3). Clickify is an automation tool, not an assistive
 * one, and the cost of saying so is written down in PM-11.
 */
class ClickifyAccessibilityService :
    AccessibilityService(),
    GestureDispatcher {
    /** One gesture at a time. Two in flight would fight over the same finger. */
    private val inFlight = Mutex()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /** Nothing is observed in A1. Recognition is A3, and it does not arrive through events. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** The bounds the platform actually reports, rather than the constants assumed (SM-17). */
    val gestureLimits: GestureLimits
        get() =
            GestureLimits(
                maxStrokeCount = GestureDescription.getMaxStrokeCount(),
                maxGestureDurationMilliseconds = GestureDescription.getMaxGestureDuration(),
            )

    override suspend fun dispatch(gesture: Gesture): GestureOutcome =
        inFlight.withLock {
            suspendCancellableCoroutine { continuation ->
                val description =
                    GestureDescription
                        .Builder()
                        .apply {
                            gesture.strokes.take(GestureDescription.getMaxStrokeCount()).forEach { stroke ->
                                val path =
                                    Path().apply {
                                        moveTo(stroke.from.x.toFloat(), stroke.from.y.toFloat())
                                        lineTo(stroke.to.x.toFloat(), stroke.to.y.toFloat())
                                    }
                                // GX-15: every stroke starts at the same moment.
                                // willContinue is never set in A1 — GX-10: a continuing stroke
                                // that is never continued is the stuck finger this all guards
                                // against, so the app simply does not create one.
                                addStroke(
                                    GestureDescription.StrokeDescription(
                                        path,
                                        0L,
                                        stroke.durationMilliseconds,
                                    ),
                                )
                            }
                        }.build()

                val callback =
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (continuation.isActive) continuation.resume(GestureOutcome.COMPLETED)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (continuation.isActive) continuation.resume(GestureOutcome.CANCELLED)
                        }
                    }

                // A refusal here is immediate and synchronous, and no callback will follow it.
                if (!dispatchGesture(description, callback, null)) {
                    Log.w(TAG, "the system refused a gesture of ${gesture.strokes.size} stroke(s)")
                    if (continuation.isActive) continuation.resume(GestureOutcome.CANCELLED)
                }
            }
        }

    /** GX-16. */
    override suspend fun performGlobalAction(action: GlobalActionKind): Boolean = performGlobalAction(action.toPlatformConstant())

    /** GX-17: the whole string at once, into whatever holds input focus. Not typing. */
    override suspend fun setText(text: String): Boolean {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return try {
            if (!focused.isEditable) {
                false
            } else {
                focused.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            text,
                        )
                    },
                )
            }
        } finally {
            @Suppress("DEPRECATION")
            focused.recycle()
        }
    }

    /**
     * GX-9, GX-11.
     *
     * A1 never dispatches a stroke with `willContinue`, so every gesture this service sends ends by
     * itself. What is left to guarantee is that none is still running when the caller believes the
     * run is over — taking the lock waits for exactly that, and taking it cannot fail.
     */
    override suspend fun releaseEverything() {
        inFlight.withLock { }
    }

    companion object {
        private const val TAG = "ClickifyService"

        /**
         * The connected service, or null.
         *
         * A static handle is how Android hands an `AccessibilityService` to the rest of an app —
         * the system constructs it, so it cannot be injected. PM-6 is the reason nothing else
         * treats this as the answer to "is the service enabled": it is null whenever the process
         * has been restarted, which is most of the time between two visits to Settings.
         */
        @Volatile
        var instance: ClickifyAccessibilityService? = null
            private set
    }
}

private fun GlobalActionKind.toPlatformConstant(): Int =
    when (this) {
        GlobalActionKind.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
        GlobalActionKind.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
        GlobalActionKind.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        GlobalActionKind.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        GlobalActionKind.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        GlobalActionKind.LOCK_SCREEN -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
        GlobalActionKind.TAKE_SCREENSHOT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
    }
