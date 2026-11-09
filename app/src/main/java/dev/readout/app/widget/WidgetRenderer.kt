package dev.readout.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import dev.readout.app.MainActivity
import dev.readout.app.R
import dev.readout.app.data.MachineState
import dev.readout.app.data.PrinterSnapshot
import dev.readout.app.util.Format

/**
 * Widgets are the same plate language as the app, at home-screen scale: the signal band states
 * one thing, the ruler measures, and every number is monospaced so it cannot jitter.
 */
object WidgetRenderer {

    private val ACTIVE = setOf(
        MachineState.Printing, MachineState.Paused, MachineState.Pausing,
        MachineState.Resuming, MachineState.Cancelling, MachineState.Finishing
    )

    /** A reading older than this is called out on the band instead of being shown as current. */
    private const val STALE_AFTER_MS = 5 * 60 * 1000L

    fun printViews(context: Context, snapshot: PrinterSnapshot?, widthDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_print)
        val state = snapshot?.state ?: MachineState.Unknown
        val working = state in ACTIVE
        val failed = state == MachineState.Error || state == MachineState.Disconnected

        val bandColor = when {
            failed -> R.color.widget_band_error
            working -> R.color.widget_plate
            else -> R.color.widget_band_idle
        }
        val bandInk = when {
            failed -> R.color.widget_error_ink
            working -> R.color.widget_on_plate
            else -> R.color.widget_ink
        }

        val updatedAt = WidgetCache.updatedAt(context)
        val stale = working && updatedAt != null && System.currentTimeMillis() - updatedAt > STALE_AFTER_MS
        val bandText = if (stale) {
            "${snapshot?.stateLabel.orEmpty().uppercase()} · STALE"
        } else {
            (snapshot?.stateLabel ?: "Not connected").uppercase()
        }
        views.setTextViewText(R.id.widget_band, bandText)
        views.setTextColor(R.id.widget_band, context.getColor(bandInk))
        views.setInt(R.id.widget_band, "setBackgroundColor", context.getColor(bandColor))

        views.setTextViewText(
            R.id.widget_job,
            snapshot?.jobName ?: when (state) {
                MachineState.Disconnected -> "OctoPrint is not connected to a printer"
                MachineState.Error -> "The printer reported a fault"
                MachineState.Unknown -> "No reading yet — open Readout"
                else -> "No job running"
            }
        )

        views.setTextViewText(R.id.widget_percent, Format.percent(snapshot?.progress))
        views.setTextViewText(
            R.id.widget_eta,
            when {
                !working -> ""
                else -> "${Format.eta(snapshot?.printTimeLeftSeconds)} left"
            }
        )

        // With no job there is no number to show; an em dash at 22 sp reads as a rule, not a value.
        val hasReading = snapshot?.progress != null || working
        val readingVisibility = if (hasReading) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_percent, readingVisibility)
        views.setViewVisibility(R.id.widget_eta, readingVisibility)

        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp.coerceIn(180, 500) * density).toInt()
        val heightPx = (30 * density).toInt()
        views.setImageViewBitmap(
            R.id.widget_ruler,
            rulerBitmap(context, widthPx, heightPx, snapshot?.progress)
        )

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    fun tempsViews(context: Context, snapshot: PrinterSnapshot?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_temps)
        val state = snapshot?.state ?: MachineState.Unknown
        val working = state in ACTIVE
        val failed = state == MachineState.Error || state == MachineState.Disconnected

        val bandColor = when {
            failed -> R.color.widget_band_error
            working -> R.color.widget_plate
            else -> R.color.widget_band_idle
        }
        val bandInk = when {
            failed -> R.color.widget_error_ink
            working -> R.color.widget_on_plate
            else -> R.color.widget_ink
        }

        views.setTextViewText(R.id.widget_band, (snapshot?.stateLabel ?: "Not connected").uppercase())
        views.setTextColor(R.id.widget_band, context.getColor(bandInk))
        views.setInt(R.id.widget_band, "setBackgroundColor", context.getColor(bandColor))

        val nozzle = snapshot?.temperatures?.firstOrNull { it.key.startsWith("tool") }
        val bed = snapshot?.temperatures?.firstOrNull { it.key == "bed" }

        views.setTextViewText(
            R.id.widget_nozzle_value,
            nozzle?.let { Format.targetAndActual(it.actual, it.target) } ?: "—"
        )
        views.setTextViewText(
            R.id.widget_bed_value,
            bed?.let { Format.targetAndActual(it.actual, it.target) } ?: "—"
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    fun cameraViews(
        context: Context,
        snapshot: PrinterSnapshot?,
        bitmap: Bitmap?,
        configured: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_camera)
        val state = snapshot?.state ?: MachineState.Unknown
        val working = state in ACTIVE
        val failed = state == MachineState.Error || state == MachineState.Disconnected

        val bandColor = when {
            failed -> R.color.widget_band_error
            working -> R.color.widget_plate
            else -> R.color.widget_band_idle
        }
        val bandInk = when {
            failed -> R.color.widget_error_ink
            working -> R.color.widget_on_plate
            else -> R.color.widget_ink
        }
        views.setTextViewText(R.id.camera_band, (snapshot?.stateLabel ?: "Not connected").uppercase())
        views.setTextColor(R.id.camera_band, context.getColor(bandInk))
        views.setInt(R.id.camera_band, "setBackgroundColor", context.getColor(bandColor))

        if (bitmap != null) {
            views.setImageViewBitmap(R.id.camera_image, bitmap)
            views.setViewVisibility(R.id.camera_image, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.camera_placeholder, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.camera_image, android.view.View.GONE)
            views.setViewVisibility(R.id.camera_placeholder, android.view.View.VISIBLE)
            views.setTextViewText(
                R.id.camera_placeholder,
                if (configured) "No frame yet — check the camera URL" else "No camera configured"
            )
        }

        views.setTextViewText(
            R.id.camera_caption,
            snapshot?.jobName ?: "Readout"
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    private fun rulerBitmap(context: Context, width: Int, height: Int, progress: Float?): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
        val density = context.resources.displayMetrics.density
        val ink = context.getColor(R.color.widget_ink)
        val track = context.getColor(R.color.widget_track)
        val value = progress ?: 0f
        val baseY = height * 0.72f

        for (index in 0..100) {
            val x = width * index / 100f
            val major = index % 10 == 0
            val mid = index % 5 == 0
            val length = when {
                major -> height * 0.56f
                mid -> height * 0.36f
                else -> height * 0.20f
            }
            paint.color = if (index / 100f <= value) ink else track
            paint.strokeWidth = if (major) 2f * density else 1f * density
            canvas.drawLine(x, baseY, x, baseY - length, paint)
        }

        paint.color = track
        paint.strokeWidth = 1f * density
        canvas.drawLine(0f, baseY, width.toFloat(), baseY, paint)

        if (progress != null) {
            val carriageX = width * value
            paint.color = ink
            paint.strokeWidth = 2.5f * density
            canvas.drawLine(carriageX, baseY - height * 0.85f, carriageX, baseY + height * 0.2f, paint)
        }
        return bitmap
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Widget ids are queried per provider so refreshes never touch widgets that do not exist. */
    fun ids(context: Context, provider: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))
}
