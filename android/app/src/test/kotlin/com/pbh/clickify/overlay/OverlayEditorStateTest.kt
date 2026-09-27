package com.pbh.clickify.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OV-33 and PK-1: the two states in which the editor has to be off the screen.
 *
 * Both are derived rather than set, which is the point of testing them here: nothing in the
 * Overlay has to remember to hide a Marker, so nothing can forget.
 */
class OverlayEditorStateTest {
    private val editing =
        OverlayUiState(
            panel = PanelState.ScenarioEditor(),
            lastFinish = com.pbh.clickify.domain.run.FinishReason.Stopped,
        )

    @Test
    fun `done closes the panel, collapses the control and takes the markers with it`() {
        val finished = editing.done()

        assertNull(finished.panel)
        assertTrue(finished.collapsed)
        assertFalse(finished.showMarkers)
        assertFalse(finished.showPanel)
    }

    /**
     * Done is not a save, and this is what says so.
     *
     * Every edit reached the disk when it was made (`FS-15`). If this ever needed a Scenario to
     * copy or a repository to call, the storage rule would have changed underneath it.
     */
    @Test
    fun `done carries no scenario of its own to write`() {
        val scenarioBefore = editing.scenario

        assertTrue(scenarioBefore == editing.done().scenario)
    }

    @Test
    fun `done clears the last run's outcome, because it has been read`() {
        assertNull(editing.done().lastFinish)
    }

    @Test
    fun `collapsing hides the markers even without done`() {
        assertTrue(editing.showMarkers.not() || editing.markers.isEmpty())
        assertFalse(editing.copy(collapsed = true).showMarkers)
    }

    @Test
    fun `picking hides the panel and the markers`() {
        val aiming = editing.copy(picking = true)

        assertTrue(aiming.isPicking)
        assertFalse(aiming.showPanel)
        assertFalse(aiming.showMarkers)
    }

    @Test
    fun `picking and recording are not the same state`() {
        assertFalse(editing.copy(picking = true).isRecording)
        assertFalse(editing.copy(recording = RecordingSession()).isPicking)
    }
}
