package com.pbh.clickify.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pbh.clickify.domain.run.freeTheTouchGesture
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.service.ClickifyAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * GX-12: "free the touch" from the Quick Settings shade.
 *
 * The reason this exists rather than being one more button on the control is the failure it is
 * for. A latched touch is a phone that has stopped answering the finger — no Overlay window can
 * be tapped, and `landscape.md` records that the whole category's answer to it is "reboot". The
 * shade is reachable by a swipe from the edge, which is a gesture the system handles before any
 * application sees it, so it survives the thing the button cannot.
 *
 * It asks the accessibility service directly rather than going through [OverlayService], because
 * it must work when no Scenario is open and no Overlay exists.
 */
class FreeTheTouchTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (ClickifyAccessibilityService.instance != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    override fun onClick() {
        val service = ClickifyAccessibilityService.instance ?: return
        scope.launch {
            service.releaseEverything()
            service.dispatch(freeTheTouchGesture(ScreenPoint(0, 0)))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
