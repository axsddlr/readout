package dev.readout.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * After a reboot the widgets have no reading and no process to push one, so they would sit
 * blank until the user opened the app. This refreshes them once at boot.
 *
 * It deliberately does not start the foreground service: a print only becomes "live" again
 * when the app observes it, or when the next widget refresh sees an active job.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED) return
        if (!WidgetRefresher.hasWidgets(context)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRefresher.refreshWithinBudget(context)
            } finally {
                pending.finish()
            }
        }
    }
}
