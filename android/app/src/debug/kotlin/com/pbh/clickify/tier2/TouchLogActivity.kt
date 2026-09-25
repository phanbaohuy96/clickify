package com.pbh.clickify.tier2

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One touch as the application underneath saw it.
 *
 * [x] and [y] are **screen** pixels — `rawX`/`rawY` rather than the view's own coordinates — because
 * that is the space a **Marker** is stored in (`ADR-0013`), and comparing anything else would be
 * comparing against the status bar's height.
 */
data class ArrivedTouch(
    val action: Int,
    val x: Int,
    val y: Int,
    val uptimeMilliseconds: Long,
    val deviceId: Int,
) {
    val isDown: Boolean get() = action == MotionEvent.ACTION_DOWN
    val isUp: Boolean get() = action == MotionEvent.ACTION_UP
    val isCancel: Boolean get() = action == MotionEvent.ACTION_CANCEL

    /** Either way the platform can end a contact. `SF-1` is satisfied by both. */
    val endsTheContact: Boolean get() = isUp || isCancel

    override fun toString(): String = "${MotionEvent.actionToString(action)}($x, $y) at $uptimeMilliseconds from device $deviceId"
}

/**
 * The target app `docs/testing.md` asks for, reduced to what tier 2 actually needs: a window in
 * front that writes down which touches arrived, where, and when.
 *
 * It lives in the **debug** source set: out of every build that ships, and inside the process the
 * tests run in, which is what the touches being readable from memory depends on. Nothing about the
 * app under test changes in order to be tested — the touches this records are the same synthetic
 * touches any other application would receive.
 */
class TouchLogActivity : Activity() {
    private val log = CopyOnWriteArrayList<ArrivedTouch>()
    private lateinit var surface: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = TouchSurface(this, log)
        setContentView(surface)
        // A screen that dims mid-test would end a stroke for a reason that is not the code's.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }

    /** Everything that has arrived so far, oldest first. */
    val arrived: List<ArrivedTouch> get() = log.toList()

    fun forget() = log.clear()

    /**
     * The part of the screen this window is really receiving touches in.
     *
     * Read rather than assumed: a test that hard-coded a pixel would be asserting against this
     * device's insets, and the next device would fail for no reason worth knowing about. Call it on
     * the main thread.
     */
    fun touchableBounds(): Rect {
        val at = IntArray(2)
        surface.getLocationOnScreen(at)
        return Rect(at[0], at[1], at[0] + surface.width, at[1] + surface.height)
    }
}

private class TouchSurface(
    context: Context,
    private val log: MutableList<ArrivedTouch>,
) : View(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        log +=
            ArrivedTouch(
                action = event.actionMasked,
                x = event.rawX.toInt(),
                y = event.rawY.toInt(),
                uptimeMilliseconds = event.eventTime,
                deviceId = event.deviceId,
            )
        // Claiming the stream is what makes the rest of it arrive: a view that refuses the down
        // never sees the up, and the up is the half these tests exist to check.
        return true
    }
}
