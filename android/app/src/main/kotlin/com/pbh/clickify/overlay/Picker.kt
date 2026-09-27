package com.pbh.clickify.overlay

import com.pbh.clickify.domain.recording.RecordedTouch
import com.pbh.clickify.overlay.ui.RecordingEvent

/**
 * PK-1: one gesture, aimed at the screen underneath, which becomes one **Step**.
 *
 * The whole of picking is here, and most of it is what it refuses to do. It does **not** hand the
 * touch back to the application underneath, which is the one thing that makes it different from
 * [Recorder]. Recording re-performs what the user did, because the point is to reproduce a
 * sequence and the application has to advance through it. Picking is aiming: the user is choosing
 * a place on a screen they want to keep looking at, and a screen that navigated away under the
 * finger would take the thing being aimed at with it.
 *
 * That also means picking cannot loop the way `RD-5` could. Nothing is re-emitted, so there is
 * nothing for the layer to catch a second time, and neither of that bug's two guards is needed.
 */
class Picker {
    private val assembler = TouchAssembler()

    /** Returns the aimed touch as soon as the finger leaves, or null while it is still down. */
    fun accept(event: RecordingEvent): RecordedTouch? = assembler.accept(event)
}
