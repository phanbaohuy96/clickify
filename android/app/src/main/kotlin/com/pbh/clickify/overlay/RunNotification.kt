package com.pbh.clickify.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pbh.clickify.R

/** Low importance: this notification is a handle to reach for, not news to be told. */
private const val CHANNEL_ID = "clickify-run"

/**
 * Everything the notification shows, and nothing else (`OV-29`).
 *
 * A value of its own so the service can re-post only when what the user would read has changed. A
 * notification rebuilt on every countdown tick flickers, loses its place in the shade, and costs
 * a hundred needless binder calls a second.
 */
data class RunNotificationState(
    val scenarioName: String = "",
    val phase: Phase = Phase.IDLE,
    val stepNumber: Int = 0,
    val stepCount: Int = 0,
) {
    /**
     * What the notification says, which is not the same set of states the control shows.
     *
     * Counting down is its own phase because it is the one moment a run exists without a Step
     * number: without it the title read "Step 0 of 1", which is a sentence no user should ever
     * be shown.
     */
    enum class Phase { IDLE, COUNTING_DOWN, RUNNING }

    val running: Boolean get() = phase != Phase.IDLE
}

internal fun OverlayUiState.notification(): RunNotificationState =
    RunNotificationState(
        scenarioName = scenarioName,
        phase =
            when (run) {
                is OverlayUiState.RunState.Stopped -> RunNotificationState.Phase.IDLE
                is OverlayUiState.RunState.CountingDown -> RunNotificationState.Phase.COUNTING_DOWN
                else -> RunNotificationState.Phase.RUNNING
            },
        stepNumber = (run as? OverlayUiState.RunState.Running)?.stepNumber ?: 0,
        stepCount = stepCount,
    )

fun Context.createRunNotificationChannel() {
    val channel =
        NotificationChannel(
            CHANNEL_ID,
            getString(R.string.run_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

fun Context.updateRunNotification(state: RunNotificationState) {
    getSystemService(NotificationManager::class.java)
        .notify(OverlayService.NOTIFICATION_ID, buildRunNotification(state))
}

/**
 * The notification the Overlay lives behind (`GX-7`, `OV-29`).
 *
 * Ongoing and unswipeable on purpose: something driving the phone by itself should never be
 * invisible, and this is the one surface that survives the Overlay being unreachable.
 *
 * It offers **only what is possible at that moment**. Stop used to be here at every moment, which
 * looked harmless and was not: pressing it with nothing running asked the Overlay for a state only
 * a live run can leave, and stranded the whole editor behind "Stopping…". The state machine
 * refuses that now (`OV-25`), and this stops asking for it.
 */
fun Context.buildRunNotification(state: RunNotificationState): Notification =
    Notification
        .Builder(this, CHANNEL_ID)
        .setContentTitle(title(state))
        .setContentText(text(state))
        .setSmallIcon(R.drawable.ic_notification)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .apply {
            // OV-15, GX-12: "free the touch" is on every surface, because a latched touch is
            // exactly the situation in which no Overlay window can be tapped at all.
            if (state.running) {
                addAction(serviceAction(OverlayService.ACTION_STOP, R.string.overlay_stop))
                addAction(serviceAction(OverlayService.ACTION_FREE_TOUCH, R.string.overlay_free_the_touch))
            } else {
                addAction(serviceAction(OverlayService.ACTION_FREE_TOUCH, R.string.overlay_free_the_touch))
                addAction(serviceAction(OverlayService.ACTION_CLOSE, R.string.overlay_close))
            }
        }.build()

private fun Context.title(state: RunNotificationState): String =
    when (state.phase) {
        RunNotificationState.Phase.IDLE -> state.scenarioName.ifBlank { getString(R.string.app_name) }
        RunNotificationState.Phase.COUNTING_DOWN -> getString(R.string.run_notification_about_to_start)
        RunNotificationState.Phase.RUNNING -> getString(R.string.overlay_step_of, state.stepNumber, state.stepCount)
    }

private fun Context.text(state: RunNotificationState): String =
    if (state.running) {
        state.scenarioName.ifBlank { getString(R.string.run_notification_title) }
    } else {
        getString(R.string.run_notification_ready)
    }

private fun Context.serviceAction(
    action: String,
    label: Int,
): Notification.Action =
    Notification.Action
        .Builder(
            null,
            getString(label),
            PendingIntent.getService(
                this,
                action.hashCode(),
                Intent(this, OverlayService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()
