package com.pbh.clickify.core.designsystem

/**
 * DS-6: the Overlay's glass, which is translucency and **not** a blur.
 *
 * Android has a real blur for this — `FLAG_BLUR_BEHIND` with `setBlurBehindRadius`, available
 * since API 31 and reported as available on the test device. It is unusable here, and the reason
 * is not aesthetic.
 *
 * Asking a window for a blur makes WindowManager create a **display-wide dim layer** underneath
 * it. That layer is a system surface with the occlusion mode `BLOCK_UNTRUSTED`, and Android's
 * untrusted-touch rules then drop every touch aimed at an untrusted window below it. Every other
 * window this app owns is such a window. With the blur on, the panel, the Markers and the
 * recording layer all stopped receiving input entirely — `InputDispatcher` reported
 * `Untrusted touch due to occlusion by /1000` and named
 * `Dim Layer for - Display 0 … mode=BLOCK_UNTRUSTED` as the obscuring surface, while the control
 * that owned the blur kept working because it sits above the layer.
 *
 * So the choice was never between a frost and a tint. It was between a frost and an editor that
 * answers touches.
 */
object OverlayGlass {
    /**
     * How much of the surface survives, measured against the case that actually exists.
     *
     * Without a blur the background is composited unchanged, so this is the only thing keeping
     * somebody else's paragraph from being readable through the control. At 0.78 it plainly was:
     * sampling a row of Gmail's body text through the control gave 92 where the page was white
     * and 43 where a glyph was — the same contrast as the sharp text beside it, merely dimmed.
     *
     * At 0.88 the same two values are 71 and 62. The text behind survives as a shape and nothing
     * more, while the control's own label sits at 236 against 71 and keeps its contrast. The
     * control's whole job is to be found and pressed in a panic (`OV-13`); it cannot also be a
     * window onto a sentence.
     */
    const val SURFACE_ALPHA = 0.88f
}
