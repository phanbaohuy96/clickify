package com.pbh.clickify.overlay

import android.content.Context
import com.pbh.clickify.core.overlay.OverlayLayoutParams
import com.pbh.clickify.core.overlay.OverlayWindow
import com.pbh.clickify.domain.scenario.ScreenPoint

/**
 * Where the floating control sits, and everything that keeps it reachable (`OV-14`).
 *
 * Its own class rather than six more methods on the coordinator, because none of this is about
 * *what* the Overlay shows — it is arithmetic against the edges of a screen, and it is the part
 * `landscape.md` ranks seventh among the category's complaints: a control that sits on top of what
 * you are automating and cannot be moved away.
 */
class ControlPlacement(
    private val context: Context,
    private val window: OverlayWindow,
    private val onSettled: (ScreenPoint) -> Unit,
) {
    var position: ScreenPoint = ScreenPoint(0, 0)
        private set

    /** True until the control has been put against an edge it could actually measure itself for. */
    private var awaitingFirstPlacement = true

    /**
     * How much of the bottom of the screen the control has to stay clear of (`OV-13`).
     *
     * The panel opens against the bottom edge, which is exactly where a user who dragged the
     * control low down has left it — and the control is drawn on top, so it covers the first
     * thing the panel has to say. Pushing it up is the only answer that keeps both usable.
     *
     * Kept apart from [position], which stays the place the user chose: when the panel closes,
     * the control goes back there rather than staying where the panel left it.
     */
    private var keepClearBottom = 0

    /**
     * The same thing along the other axis, for the landscape panel (`OV-37`).
     *
     * A side sheet is full height, so there is no raising the control above it — it has to be
     * moved inwards instead. Exactly one of the two is ever non-zero, because the panel is only
     * ever one shape at a time.
     */
    private var keepClearEnd = 0

    /**
     * Which side the control is against (`OV-14`).
     *
     * Kept, rather than worked out from [position] each time, because the control **changes
     * width**: a run replaces four buttons with one large Stop. Re-deriving from the left edge
     * would leave a right-hand control floating in from the edge the moment a run began.
     */
    private var onRight = false

    init {
        // The two facts this needs — how tall the panel is and how tall the control is — arrive
        // in either order, and both arrive after the window they belong to has been attached.
        // Re-applying on each is the only version that does not depend on which came first: the
        // first attempt ran with a control of height zero and concluded it was already clear.
        window.onResized = { _, _ -> settleAgainstEdge() }
    }

    /**
     * OV-14: where the control starts, before the user has had a say.
     *
     * The right edge, a third of the way down, when there is nothing remembered: the left is where
     * most applications put their own back arrow, and the top is where the status bar and the
     * shade's pull-down already are.
     */
    fun place(remembered: ScreenPoint?) {
        awaitingFirstPlacement = remembered == null
        onRight = remembered == null || remembered.x > 0
        position = remembered ?: ScreenPoint(x = 0, y = context.overlayBounds().height / 3)
        apply()
        settle()
    }

    /** OV-14: live, as the finger moves. The delta is in raw screen pixels. */
    fun moveBy(
        x: Int,
        y: Int,
    ) {
        // From where the control **is**, not from where it would be with nothing in its way —
        // otherwise a drag that starts while the panel is open jumps first and then moves.
        val from = displayed()
        position = clamped(ScreenPoint(from.x + x, from.y + y))
        apply()
    }

    /** OV-13, OV-37: keeps the control out of the space the panel has taken, on whichever edge. */
    fun keepClearOf(
        bottom: Int = 0,
        end: Int = 0,
    ) {
        if (keepClearBottom == bottom && keepClearEnd == end) return
        keepClearBottom = bottom
        keepClearEnd = end
        apply()
    }

    /**
     * OV-37: the phone was rotated, so every edge this class works against has moved.
     *
     * The user's remembered position is deliberately **not** written back from here. It is still
     * the place they chose in the other orientation, and it is what they should find when they
     * rotate back; what happens now is only that the control is put somewhere it can be seen.
     */
    fun onScreenChanged() {
        keepClearBottom = 0
        keepClearEnd = 0
        settleAgainstEdge()
        settle()
    }

    /** Where the control actually goes: the user's choice, raised above anything in the way. */
    fun displayed(): ScreenPoint {
        if (keepClearBottom == 0 && keepClearEnd == 0) return position
        val bounds = context.overlayBounds()
        val floor = bounds.height - keepClearBottom - window.measuredHeight
        val wall = bounds.width - keepClearEnd - window.measuredWidth
        return ScreenPoint(
            x = if (keepClearEnd == 0) position.x else position.x.coerceAtMost(wall.coerceAtLeast(0)),
            y = if (keepClearBottom == 0) position.y else position.y.coerceAtMost(floor.coerceAtLeast(0)),
        )
    }

    /**
     * OV-14: the control settles against the nearer side and stays there.
     *
     * Snapping rather than leaving it mid-screen, because the control's whole job is to be
     * reachable without being in the way, and an edge is the only place that is reliably both.
     */
    fun finishDrag() {
        onRight = (position.x + window.measuredWidth / 2) * 2 >= context.overlayBounds().width
        settleAgainstEdge()
        onSettled(position)
    }

    /**
     * Puts the control where it belongs once its width is known.
     *
     * Nothing about an edge can be worked out before then: a `WRAP_CONTENT` window reports a width
     * of zero until it has been laid out, and "the right edge minus zero" is off the screen.
     * Called again after every re-attach, because `OV-13` re-attaches the control to keep it on
     * top and a fresh window has to be measured again.
     */
    fun settle() {
        window.onceMeasured {
            awaitingFirstPlacement = false
            settleAgainstEdge()
        }
    }

    /** Puts the control back against [onRight]'s side at whatever width it is now. */
    private fun settleAgainstEdge() {
        val bounds = context.overlayBounds()
        val x = if (onRight) bounds.width - window.measuredWidth else 0
        position = clamped(ScreenPoint(x, position.y))
        apply()
    }

    /** OV-14: the whole control stays on the screen, whatever the drag asked for. */
    private fun clamped(at: ScreenPoint): ScreenPoint {
        val bounds = context.overlayBounds()
        return ScreenPoint(
            x = at.x.coerceIn(0, (bounds.width - window.measuredWidth).coerceAtLeast(0)),
            y = at.y.coerceIn(0, (bounds.height - window.measuredHeight).coerceAtLeast(0)),
        )
    }

    private fun apply() {
        val at = displayed()
        window.move(OverlayLayoutParams.floating(x = at.x, y = at.y))
    }
}
