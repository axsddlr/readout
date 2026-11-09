package dev.readout.app.debug

import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import dev.readout.app.data.ComponentTemperature
import dev.readout.app.data.MachineState
import dev.readout.app.data.PrinterSnapshot
import dev.readout.app.ui.theme.ReadoutTheme
import dev.readout.app.ui.theme.ThemeMode
import dev.readout.app.widget.WidgetRenderer

/**
 * Debug-only host that inflates the real widget RemoteViews, so the layout and ruler bitmap can
 * be inspected without adding a widget to the launcher. Not part of the release APK.
 */
class WidgetPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReadoutTheme(mode = ThemeMode.Dark) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF131110))
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("WIDGET / PRINTING", color = Color(0xFFEEE6DA))
                    WidgetHost(WidgetRenderer.printViews(this@WidgetPreviewActivity, printingSnapshot(), 320))

                    Text("WIDGET / IDLE", color = Color(0xFFEEE6DA))
                    WidgetHost(WidgetRenderer.printViews(this@WidgetPreviewActivity, idleSnapshot(), 320))

                    Text("WIDGET / TEMPERATURES", color = Color(0xFFEEE6DA))
                    WidgetHost(WidgetRenderer.tempsViews(this@WidgetPreviewActivity, printingSnapshot()), widthDp = 150)

                    Text("WIDGET / CAMERA (FRAME)", color = Color(0xFFEEE6DA))
                    WidgetHost(
                        WidgetRenderer.cameraViews(
                            this@WidgetPreviewActivity,
                            printingSnapshot(),
                            fakeFrame(),
                            configured = true
                        ),
                        heightDp = 180
                    )

                    Text("WIDGET / CAMERA (NO FRAME)", color = Color(0xFFEEE6DA))
                    WidgetHost(
                        WidgetRenderer.cameraViews(
                            this@WidgetPreviewActivity,
                            printingSnapshot(),
                            null,
                            configured = true
                        ),
                        heightDp = 180
                    )
                }
            }
        }
    }

    @Composable
    private fun WidgetHost(views: RemoteViews, widthDp: Int = 320, heightDp: Int? = null) {
        AndroidView(
            factory = { context ->
                views.apply(context, null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .width(widthDp.dp)
                .then(if (heightDp != null) Modifier.height(heightDp.dp) else Modifier)
        )
    }

    /** Synthetic frame for layout inspection only; never ships. */
    private fun fakeFrame(): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(640, 360, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 640f, 360f,
            intArrayOf(0xFF2A2621.toInt(), 0xFF4A443A.toInt()),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 640f, 360f, paint)
        paint.shader = null
        paint.color = 0xFFFFD43B.toInt()
        canvas.drawCircle(520f, 90f, 42f, paint)
        paint.color = 0xFFEEE6DA.toInt()
        canvas.drawRect(120f, 220f, 520f, 300f, paint)
        paint.color = 0xFF7A6F5E.toInt()
        canvas.drawRect(120f, 300f, 520f, 312f, paint)
        return bitmap
    }

    private fun printingSnapshot() = PrinterSnapshot(
        state = MachineState.Printing,
        stateLabel = "Printing",
        temperatures = listOf(
            ComponentTemperature("tool0", "Nozzle", 209.6f, 210f, 0),
            ComponentTemperature("bed", "Bed", 59.4f, 60f, 0)
        ),
        jobName = "benchy_hull_0.2mm_PLA.gcode",
        progress = 0.417f,
        printTimeSeconds = 1720,
        printTimeLeftSeconds = 2410,
        etaOrigin = "average",
        octoPrintVersion = "OctoPrint 1.10.2"
    )

    private fun idleSnapshot() = PrinterSnapshot(
        state = MachineState.Idle,
        stateLabel = "Idle",
        temperatures = listOf(
            ComponentTemperature("tool0", "Nozzle", 21.4f, 0f, 0),
            ComponentTemperature("bed", "Bed", 21.1f, 0f, 0)
        ),
        jobName = null,
        progress = null,
        octoPrintVersion = "OctoPrint 1.10.2"
    )
}
