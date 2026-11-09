package dev.readout.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn marks for the four actions Material's core icon set does not carry.
 * Drawn on a 24x24 grid with 2 dp strokes so they sit at the same weight as the system icons.
 */
object ReadoutIcons {

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(7f, 19f); close()
                moveTo(14f, 5f); lineTo(17f, 5f); lineTo(17f, 19f); lineTo(14f, 19f); close()
            }
        }.build()
    }

    val Stop: ImageVector by lazy {
        ImageVector.Builder(
            name = "Stop", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close()
            }
        }.build()
    }

    val Printer: ImageVector by lazy {
        ImageVector.Builder(
            name = "Printer", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Square) {
                // Frame.
                moveTo(4.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 19.5f); lineTo(4.5f, 19.5f); close()
                // Gantry.
                moveTo(4.5f, 9.5f); lineTo(19.5f, 9.5f)
                // Toolhead + nozzle.
                moveTo(9.5f, 9.5f); lineTo(9.5f, 12.5f); lineTo(14.5f, 12.5f); lineTo(14.5f, 9.5f)
                moveTo(12f, 12.5f); lineTo(12f, 14.5f)
                // Bed.
                moveTo(6.5f, 17f); lineTo(17.5f, 17f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 14f); lineTo(13f, 14f); lineTo(13f, 15.5f); lineTo(11f, 15.5f); close()
            }
        }.build()
    }

    val Terminal: ImageVector by lazy {
        ImageVector.Builder(
            name = "Terminal", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Square) {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
                moveTo(7f, 9f); lineTo(10f, 12f); lineTo(7f, 15f)
                moveTo(12f, 15f); lineTo(16f, 15f)
            }
        }.build()
    }

    val Camera: ImageVector by lazy {
        ImageVector.Builder(
            name = "Camera", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Square) {
                moveTo(3f, 8f); lineTo(7f, 8f); lineTo(9f, 5.5f); lineTo(15f, 5.5f); lineTo(17f, 8f); lineTo(21f, 8f)
                lineTo(21f, 19f); lineTo(3f, 19f); close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f) {
                moveTo(15.5f, 13.5f)
                curveTo(15.5f, 15.43f, 13.93f, 17f, 12f, 17f)
                curveTo(10.07f, 17f, 8.5f, 15.43f, 8.5f, 13.5f)
                curveTo(8.5f, 11.57f, 10.07f, 10f, 12f, 10f)
                curveTo(13.93f, 10f, 15.5f, 11.57f, 15.5f, 13.5f)
                close()
            }
        }.build()
    }
}
