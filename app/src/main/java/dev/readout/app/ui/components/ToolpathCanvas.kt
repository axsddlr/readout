package dev.readout.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.readout.app.data.GcodePreview
import dev.readout.app.data.PreviewSettings
import kotlin.math.min

/**
 * The top-down drawing shared by the file sheet and the live viewer: the whole part as a ghost, the
 * previous layer at partial strength, and the selected layer at full strength.
 *
 * Paths are built in model coordinates once per file; the canvas only scales and flips them, so
 * moving the layer never re-walks the segments.
 */
@Composable
fun ToolpathCanvas(
    preview: GcodePreview,
    layerIndex: Int,
    settings: PreviewSettings,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val layerPaths = remember(preview) {
        preview.layers.map { layer ->
            Path().apply {
                layer.lines.forEach { line ->
                    if (line.extruding) {
                        moveTo(line.x1, line.y1)
                        lineTo(line.x2, line.y2)
                    }
                }
            }
        }
    }
    // The ghost is the whole part, so it is the expensive one to draw; the quality setting decides
    // how many of its segments survive.
    val wholePart = remember(layerPaths, settings.ghostStep) {
        if (settings.ghostStep <= 1) {
            Path().apply { layerPaths.forEach { addPath(it) } }
        } else {
            sampledPath(preview, settings.ghostStep)
        }
    }
    val layer = layerPaths.getOrNull(layerIndex)
    val previous = if (settings.previousLayer) layerPaths.getOrNull(layerIndex - 1) else null
    val current = layer ?: previous ?: return

    Canvas(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainerHighest)
    ) {
        val pad = 12f
        val scale = min(
            (size.width - pad * 2) / preview.xyWidth,
            (size.height - pad * 2) / preview.xyHeight
        )
        val ox = (size.width - preview.xyWidth * scale) / 2f - preview.minX * scale
        val oy = (size.height + preview.xyHeight * scale) / 2f + preview.minY * scale
        withTransform({
            translate(ox, oy)
            scale(scale, -scale, pivot = Offset.Zero)
        }) {
            // The rest of the part in a whisper of colour, so a single layer reads as part of
            // something rather than a stray shape floating in a box.
            drawPath(
                wholePart,
                scheme.primary.copy(alpha = 0.16f),
                style = Stroke(width = 1.2f / scale)
            )
            // The layer underneath, heavier than the ghost so the pair reads as
            // "where it has been, where it is".
            if (previous != null) {
                drawPath(
                    previous,
                    scheme.primary.copy(alpha = 0.42f),
                    style = Stroke(
                        width = (settings.strokeWidth + 0.6f) / scale,
                        cap = StrokeCap.Round
                    )
                )
            }
            if (settings.currentLayer) {
                drawPath(
                    current,
                    scheme.primary,
                    style = Stroke(width = settings.strokeWidth / scale, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/** The whole part as every [step]-th extruding segment: a cheaper ghost for large files. */
private fun sampledPath(preview: GcodePreview, step: Int): Path {
    var counter = 0
    val path = Path()
    for (layer in preview.layers) {
        for (line in layer.lines) {
            if (!line.extruding) continue
            if (counter++ % step != 0) continue
            path.moveTo(line.x1, line.y1)
            path.lineTo(line.x2, line.y2)
        }
    }
    return path
}
