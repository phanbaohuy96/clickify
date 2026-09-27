package com.pbh.clickify.core.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OV-18 and OV-19. ADR-0015 calls hand-wiring these owners "where this goes wrong if it goes
 * wrong", and a leaked ViewModelStore is invisible until a phone has been running for hours — so
 * the counting is done here instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayWindowTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Records every view added and removed, which is what the requirements are about. */
    private class RecordingWindowManager : WindowManager {
        val attached = mutableListOf<View>()
        var updates = 0

        override fun addView(
            view: View?,
            params: android.view.ViewGroup.LayoutParams?,
        ) {
            attached += view!!
        }

        override fun updateViewLayout(
            view: View?,
            params: android.view.ViewGroup.LayoutParams?,
        ) {
            updates++
        }

        override fun removeView(view: View?) {
            attached -= view!!
        }

        override fun removeViewImmediate(view: View?) = removeView(view)

        @Deprecated("Deprecated in Java", ReplaceWith("throw UnsupportedOperationException()"))
        @Suppress("DEPRECATION")
        override fun getDefaultDisplay(): android.view.Display = throw UnsupportedOperationException()
    }

    private class CountingViewModel : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    private fun window(manager: WindowManager) = OverlayWindow(context, manager)

    /** The default factory cannot reach a private test class, so it is built explicitly. */
    private fun viewModelIn(overlay: OverlayWindow): CountingViewModel =
        ViewModelProvider.create(
            overlay,
            viewModelFactory { initializer { CountingViewModel() } },
        )[CountingViewModel::class]

    @Test
    fun `showing attaches exactly one window`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)

        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }

        assertEquals(1, manager.attached.size)
        assertTrue(overlay.isShowing)
    }

    @Test
    fun `showing twice moves the window rather than attaching another`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)

        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
        overlay.show(OverlayLayoutParams.floating(x = 50, y = 50)) { Text("hello") }

        assertEquals(1, manager.attached.size)
        assertEquals(1, manager.updates)
    }

    @Test
    fun `dismissing detaches the window`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)

        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
        overlay.dismiss()

        assertEquals(emptyList(), manager.attached)
        assertFalse(overlay.isShowing)
    }

    @Test
    fun `dismissing clears the view models the window owned`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)
        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
        val viewModel = viewModelIn(overlay)

        overlay.dismiss()

        assertTrue(viewModel.cleared, "a ViewModel outliving its overlay is ADR-0015's leak")
    }

    @Test
    fun `opening and closing many times leaves nothing attached and nothing alive`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)
        val viewModels = mutableListOf<CountingViewModel>()

        repeat(50) {
            overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
            viewModels += viewModelIn(overlay)
            overlay.dismiss()
        }

        assertEquals(emptyList(), manager.attached)
        assertEquals(50, viewModels.size)
        assertTrue(viewModels.all { it.cleared }, "one leak per opening is many an hour")
    }

    @Test
    fun `the lifecycle reaches resumed while showing and destroyed after`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)

        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
        assertEquals(Lifecycle.State.RESUMED, overlay.lifecycle.currentState)

        overlay.dismiss()
        assertEquals(Lifecycle.State.DESTROYED, overlay.lifecycle.currentState)
    }

    @Test
    fun `a window shown again after being dismissed gets a working lifecycle`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)

        overlay.show(OverlayLayoutParams.floating()) { Text("first") }
        overlay.dismiss()
        overlay.show(OverlayLayoutParams.floating()) { Text("second") }

        // A LifecycleRegistry that has reached DESTROYED cannot be driven back to RESUMED, so
        // showing again has to build fresh owners rather than reuse them.
        assertEquals(Lifecycle.State.RESUMED, overlay.lifecycle.currentState)
        assertEquals(1, manager.attached.size)
    }

    @Test
    fun `dismissing something that was never shown does nothing`() {
        val manager = RecordingWindowManager()

        window(manager).dismiss()

        assertEquals(emptyList(), manager.attached)
    }

    @Test
    fun `dismissing twice is not an error`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)

        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
        overlay.dismiss()
        overlay.dismiss()

        assertEquals(emptyList(), manager.attached)
    }

    @Test
    fun `a window that has never been shown has no size to report`() {
        val overlay = window(RecordingWindowManager())

        assertEquals(0, overlay.measuredWidth)
        assertEquals(0, overlay.measuredHeight)
    }

    /**
     * OV-14: nothing about an edge can be worked out before a `WRAP_CONTENT` window has been laid
     * out, and "the right edge minus zero" is off the screen.
     */
    @Test
    fun `waiting to be measured runs the block once the window has a size`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)
        var measured = 0

        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
        overlay.onceMeasured { measured++ }
        assertEquals(0, measured, "nothing has been laid out yet")

        manager.attached.single().layout(0, 0, 320, 96)

        assertEquals(1, measured)
        assertEquals(320, overlay.measuredWidth)
        assertEquals(96, overlay.measuredHeight)
    }

    @Test
    fun `waiting to be measured runs at once when the window already has a size`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)
        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
        manager.attached.single().layout(0, 0, 200, 50)
        var measured = 0

        overlay.onceMeasured { measured++ }

        assertEquals(1, measured)
    }

    @Test
    fun `waiting to be measured on a window that is not showing does nothing`() {
        var measured = 0

        window(RecordingWindowManager()).onceMeasured { measured++ }

        assertEquals(0, measured)
    }

    /** DS-4, OV-13: the control is moved clear of the panel, and it needs the panel's height. */
    @Test
    fun `a resize is reported, and a layout that changes nothing is not`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)
        val sizes = mutableListOf<Pair<Int, Int>>()
        overlay.onResized = { width, height -> sizes += width to height }

        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
        val view = manager.attached.single()
        view.layout(0, 0, 320, 96)
        view.layout(0, 0, 320, 96)
        view.layout(0, 0, 320, 140)

        assertEquals(listOf(320 to 96, 320 to 140), sizes)
    }

    @Test
    fun `moving an attached window does not rebuild it`() {
        val manager = RecordingWindowManager()
        val overlay = window(manager)

        overlay.show(OverlayLayoutParams.floating()) { Text("hello") }
        overlay.move(OverlayLayoutParams.floating(x = 10, y = 20))

        assertEquals(1, manager.attached.size)
        assertEquals(1, manager.updates)
    }
}
