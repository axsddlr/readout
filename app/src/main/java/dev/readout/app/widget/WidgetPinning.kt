package dev.readout.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/** Pin flow for the launcher's "add widget" request (API 26+). */
object WidgetPinning {

    fun print(context: Context): Boolean = request(context, PrintWidgetProvider::class.java)
    fun temps(context: Context): Boolean = request(context, TempsWidgetProvider::class.java)
    fun camera(context: Context): Boolean = request(context, CameraWidgetProvider::class.java)

    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    private fun request(context: Context, provider: Class<*>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val success = PendingIntent.getBroadcast(
            context,
            provider.hashCode(),
            Intent(context, WidgetPinReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return manager.requestPinAppWidget(ComponentName(context, provider), null, success)
    }
}

/** The system delivers the pin result here; nothing to do, but the flow must have a target. */
class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
