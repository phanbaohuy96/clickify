package com.pbh.clickify.core.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * One Compose window attached to [WindowManager], with the three owners Compose refuses to run
 * without ([ADR-0015]).
 *
 * This is the class ADR-0015 says must be written once and never re-implemented per feature,
 * because getting it wrong leaks a `ViewModelStore` every time the floating control opens — which
 * is many times an hour. [dismiss] clears it, and `OV-19` is the test that says so.
 *
 * Reusable: [show] after [dismiss] builds fresh owners, because a `LifecycleRegistry` that has
 * reached `DESTROYED` cannot be driven back to `RESUMED`.
 */
class OverlayWindow(
    private val context: Context,
    private val windowManager: WindowManager,
) : LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private var lifecycleRegistry = LifecycleRegistry(this)
    private var savedStateController = SavedStateRegistryController.create(this)
    private var composeView: ComposeView? = null

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    val isShowing: Boolean get() = composeView != null

    /**
     * How large the attached window turned out to be, or zero before it has been measured.
     *
     * `WRAP_CONTENT` means only the view knows this, and a caller keeping a window inside the
     * screen (`OV-14`) has to ask rather than assume.
     */
    val measuredWidth: Int get() = composeView?.width ?: 0

    val measuredHeight: Int get() = composeView?.height ?: 0

    /** Attaches the window. Calling this while already showing only updates [params]. */
    fun show(
        params: WindowManager.LayoutParams,
        content: @Composable () -> Unit,
    ) {
        composeView?.let {
            windowManager.updateViewLayout(it, params)
            return
        }

        lifecycleRegistry = LifecycleRegistry(this)
        savedStateController = SavedStateRegistryController.create(this)
        savedStateController.performRestore(null)

        val view =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(this@OverlayWindow)
                setViewTreeViewModelStoreOwner(this@OverlayWindow)
                setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
                setContent(content)
            }

        view.addOnLayoutChangeListener { changed, _, _, _, _, oldLeft, oldTop, oldRight, oldBottom ->
            val resized = changed.width != oldRight - oldLeft || changed.height != oldBottom - oldTop
            if (resized) onResized?.invoke(changed.width, changed.height)
        }

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        windowManager.addView(view, params)
        composeView = view
    }

    /** Moves an already-attached window without rebuilding it (`OV-14`). */
    fun move(params: WindowManager.LayoutParams) {
        composeView?.let { windowManager.updateViewLayout(it, params) }
    }

    /**
     * Called whenever the attached window's size changes, including the first time it has one.
     *
     * Set once by the owner rather than passed to [show], because a window is re-shown on every
     * state change and a listener added each time would be added many times over.
     */
    var onResized: ((width: Int, height: Int) -> Unit)? = null

    /**
     * Runs [block] once the window has a size, which is the first moment anything can be said
     * about where its edges are.
     *
     * `WRAP_CONTENT` means the size is not known at `addView`, so a caller placing the window
     * against an edge (`OV-14`) has to wait rather than guess — and guessing puts it off-screen.
     * Fires immediately if the window is already laid out.
     */
    fun onceMeasured(block: () -> Unit) {
        val view = composeView ?: return
        if (view.width > 0) {
            block()
            return
        }
        view.addOnLayoutChangeListener(
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    changed: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int,
                ) {
                    if (changed.width <= 0) return
                    changed.removeOnLayoutChangeListener(this)
                    block()
                }
            },
        )
    }

    /**
     * Detaches the window and releases everything it owns (`OV-4`, `OV-18`).
     *
     * Safe to call when nothing is showing, and safe to call twice — both happen on the paths that
     * are already going wrong, which are the paths that most need this to work.
     *
     * [immediate] is `TP-7` and nothing else. `removeView` is a **request**: the window survives
     * until the window manager next runs, and on the emulator that was long enough for a
     * screenshot taken 160 ms later to still contain the panel that had been dismissed — a
     * Template cropped out of that frame carries Clickify's own interface inside it and can
     * never match anything again.
     *
     * It is not the default, because `OV-13` re-attaches the control on **every** state change to
     * keep Stop on top. Removing it synchronously there leaves a frame with no control on the
     * screen at all, and a Stop button that blinks is a Stop button the user has to aim at.
     */
    fun dismiss(immediate: Boolean = false) {
        val view = composeView ?: return
        composeView = null

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        view.disposeComposition()
        if (immediate) {
            // Throws when the view is no longer attached, which is exactly the case where the
            // deferred call would have had nothing left to do either.
            runCatching { windowManager.removeViewImmediate(view) }
                .onFailure { runCatching { windowManager.removeView(view) } }
        } else {
            runCatching { windowManager.removeView(view) }
        }
        viewModelStore.clear()
    }
}
