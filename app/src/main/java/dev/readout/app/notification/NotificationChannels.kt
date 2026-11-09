package dev.readout.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dev.readout.app.R

object NotificationChannels {

    const val STATUS = "print_status"
    const val EVENTS = "print_events"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val status = NotificationChannel(
            STATUS,
            context.getString(R.string.notification_status_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_status_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val events = NotificationChannel(
            EVENTS,
            context.getString(R.string.notification_events_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_events_channel_description)
        }

        manager.createNotificationChannels(listOf(status, events))
    }
}
