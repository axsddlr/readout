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

    /** Four corner brackets: expand to fullscreen. */
    val Expand: ImageVector by lazy {
        ImageVector.Builder(
            name = "Expand", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
                moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
                moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
                moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
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

    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "Folder", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 6f); lineTo(3f, 18f); lineTo(21f, 18f); lineTo(21f, 8f)
                lineTo(12f, 8f); lineTo(10f, 6f); close()
            }
        }.build()
    }

    val File: ImageVector by lazy {
        ImageVector.Builder(
            name = "File", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 3f); lineTo(14f, 3f); lineTo(19f, 8f); lineTo(19f, 21f); lineTo(6f, 21f); close()
                moveTo(13f, 4.5f); lineTo(13f, 9f); lineTo(17.5f, 9f); close()
            }
        }.build()
    }

    /** Three finder squares and a few modules: reads as "QR code" at 24 dp. */
    val QrCode: ImageVector by lazy {
        ImageVector.Builder(
            name = "QrCode", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
                // Finder patterns: outer ring with a solid centre.
                for ((x, y) in listOf(3f to 3f, 14f to 3f, 3f to 14f)) {
                    moveTo(x, y); lineTo(x + 7f, y); lineTo(x + 7f, y + 7f); lineTo(x, y + 7f); close()
                    moveTo(x + 1.75f, y + 1.75f); lineTo(x + 5.25f, y + 1.75f); lineTo(x + 5.25f, y + 5.25f); lineTo(x + 1.75f, y + 5.25f); close()
                }
            }
            path(fill = SolidColor(Color.Black)) {
                for ((x, y) in listOf(5f to 5f, 16f to 5f, 5f to 16f)) {
                    moveTo(x, y); lineTo(x + 3f, y); lineTo(x + 3f, y + 3f); lineTo(x, y + 3f); close()
                }
                // Data modules.
                for ((x, y) in listOf(14f to 14f, 17.5f to 14f, 14f to 17.5f, 17.5f to 17.5f, 19f to 19f, 15.75f to 15.75f)) {
                    moveTo(x, y); lineTo(x + 2f, y); lineTo(x + 2f, y + 2f); lineTo(x, y + 2f); close()
                }
            }
        }.build()
    }
}
