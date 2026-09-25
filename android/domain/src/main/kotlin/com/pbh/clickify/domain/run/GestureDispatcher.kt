package com.pbh.clickify.domain.run

import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.ScreenPoint

/**
 * The only way Clickify can touch another application, expressed without Android in it.
 *
 * `AccessibilityService.dispatchGesture()` is what implements this. Keeping the interface here
 * means the runner — which owns every rule about stopping, and is therefore the part that must not
 * be wrong — is tested on the JVM against a fake.
 */
interface GestureDispatcher {
    /** Suspends until the system reports the gesture finished or cancelled (GX-6). */
    suspend fun dispatch(gesture: Gesture): GestureOutcome

    /** GX-16. */
    suspend fun performGlobalAction(action: GlobalActionKind): Boolean

    /** GX-17. Fails when nothing editable holds input focus. */
    suspend fun setText(text: String): Boolean

    /**
     * GX-9, GX-11: ends every stroke this dispatcher still has in flight.
     *
     * Called on **every** exit path, including the ones that are already going wrong. It must not
     * throw, and it must be safe to call when nothing is in flight.
     */
    suspend fun releaseEverything()
}

/** One or more strokes performed together (GX-13 to GX-15). */
data class Gesture(
    val strokes: List<Stroke>,
) {
    /** The longest stroke decides how long the whole gesture takes. */
    val durationMilliseconds: Long get() = strokes.maxOfOrNull { it.durationMilliseconds } ?: 0L
}

data class Stroke(
    val from: ScreenPoint,
    val to: ScreenPoint,
    val durationMilliseconds: Long,
)

enum class GestureOutcome {
    COMPLETED,

    /** The system took the gesture away — another service dispatched one, or the screen went off. */
    CANCELLED,
}
