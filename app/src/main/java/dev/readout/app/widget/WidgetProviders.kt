package dev.readout.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.readout.app.data.OctoPrintApi
import dev.readout.app.data.PrinterConfig
import dev.readout.app.data.PrinterSnapshot
import dev.readout.app.data.PrinterStore
import dev.readout.app.data.SnapshotParser
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One refresher for every widget. The app and the foreground service push snapshots while they
 * are alive; when the launcher updates a widget (or the device boots), it fetches for itself.
 */
object WidgetRefresher {

    const val ACTION_REFRESH = "dev.readout.app.widget.REFRESH"
    private const val CAMERA_FILE = "widget-camera.jpg"

    fun hasWidgets(context: Context): Boolean =
        WidgetRenderer.ids(context, PrintWidgetProvider::class.java).isNotEmpty() ||
            WidgetRenderer.ids(context, TempsWidgetProvider::class.java).isNotEmpty() ||
            WidgetRenderer.ids(context, CameraWidgetProvider::class.java).isNotEmpty()

    /** Called from the controller after every successful poll. Cheap when no widget exists. */
    fun push(context: Context, snapshot: PrinterSnapshot?) {
        if (!hasWidgets(context)) return
        WidgetCache.write(context, snapshot)
        updateAll(context)
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val snapshot = WidgetCache.read(context)

        WidgetRenderer.ids(context, PrintWidgetProvider::class.java).forEach { id ->
            val width = manager.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            manager.updateAppWidget(id, WidgetRenderer.printViews(context, snapshot, width))
        }

        WidgetRenderer.ids(context, TempsWidgetProvider::class.java).forEach { id ->
            manager.updateAppWidget(id, WidgetRenderer.tempsViews(context, snapshot))
        }
    }

    /** Paints the camera widget from the last cached frame, when one exists. */
    fun updateCamera(context: Context, snapshot: PrinterSnapshot? = WidgetCache.read(context)) {
        val ids = WidgetRenderer.ids(context, CameraWidgetProvider::class.java)
        if (ids.isEmpty()) return
        val configured = PrinterStore(context).active()?.webcamUrl != null
        val bitmap = readCachedCamera(context)
        val manager = AppWidgetManager.getInstance(context)
        ids.forEach { id ->
            manager.updateAppWidget(id, WidgetRenderer.cameraViews(context, snapshot, bitmap, configured))
        }
    }

    suspend fun refreshFromNetwork(context: Context) {
        val config = PrinterStore(context).active()
        if (config == null) {
            WidgetCache.write(context, null)
            updateAll(context)
            return
        }
        val api = OctoPrintApi(config.baseUrl, config.apiKey)
        val printer = withContext(Dispatchers.IO) { runCatching { api.printer() }.getOrNull() } ?: return
        val job = withContext(Dispatchers.IO) { runCatching { api.job() }.getOrNull() }
        val snapshot = SnapshotParser.parse(printer, job, null)
        WidgetCache.write(context, snapshot)
        updateAll(context)
        captureCamera(context, api, config)
    }

    /** Fetches one frame into the cache and repaints. Failures keep the previous frame. */
    suspend fun captureCamera(context: Context, api: OctoPrintApi, config: PrinterConfig) {
        if (WidgetRenderer.ids(context, CameraWidgetProvider::class.java).isEmpty()) return
        val url = config.webcamUrl?.takeIf { it.isNotBlank() }
            ?: (config.baseUrl.trimEnd('/') + "/webcam/?action=snapshot")
        val bytes = withContext(Dispatchers.IO) { runCatching { api.fetchBytes(url) }.getOrNull() } ?: return
        withContext(Dispatchers.IO) {
            runCatching { File(context.cacheDir, CAMERA_FILE).writeBytes(bytes) }
        }
        updateCamera(context, WidgetCache.read(context))
    }

    private fun readCachedCamera(context: Context): Bitmap? {
        val file = File(context.cacheDir, CAMERA_FILE)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun refreshSafely(context: Context, finish: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refreshFromNetwork(context)
            } finally {
                finish()
            }
        }
    }
}

class PrintWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val snapshot = WidgetCache.read(context)
        appWidgetIds.forEach { id ->
            val width = appWidgetManager.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            appWidgetManager.updateAppWidget(id, WidgetRenderer.printViews(context, snapshot, width))
        }
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefresher.ACTION_REFRESH) refresh(context)
    }

    private fun refresh(context: Context) {
        val pending = goAsync()
        WidgetRefresher.refreshSafely(context) { pending.finish() }
    }
}

class TempsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val snapshot = WidgetCache.read(context)
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, WidgetRenderer.tempsViews(context, snapshot))
        }
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefresher.ACTION_REFRESH) refresh(context)
    }

    private fun refresh(context: Context) {
        val pending = goAsync()
        WidgetRefresher.refreshSafely(context) { pending.finish() }
    }
}

class CameraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val snapshot = WidgetCache.read(context)
        val configured = PrinterStore(context).active()?.webcamUrl != null
        val bitmap = runCatching {
            val file = java.io.File(context.cacheDir, "widget-camera.jpg")
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }.getOrNull()
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(
                id,
                WidgetRenderer.cameraViews(context, snapshot, bitmap, configured)
            )
        }
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefresher.ACTION_REFRESH) refresh(context)
    }

    private fun refresh(context: Context) {
        val pending = goAsync()
        WidgetRefresher.refreshSafely(context) { pending.finish() }
    }
}
