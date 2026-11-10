package dev.readout.app.data

/** One straight move in the XY plane. Travel moves are kept but marked, so the UI can ignore them. */
data class PreviewLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val extruding: Boolean
)

data class PreviewLayer(val lines: List<PreviewLine>)

/** Toolpaths grouped by the Z they were printed at, plus the bounds the drawing must fit. */
data class GcodePreview(
    val layers: List<PreviewLayer>,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val truncated: Boolean
) {
    val isEmpty: Boolean get() = layers.isEmpty()
    val width: Float get() = (maxX - minX).coerceAtLeast(0.001f)
    val height: Float get() = (maxY - minY).coerceAtLeast(0.001f)
}

/**
 * Reads a G-code stream into per-layer line segments. Only the moves are needed for a picture, so
 * this deliberately understands a small subset of G-code: G0/G1, G90/G91, M82/M83, G92.
 */
object GcodePreviewParser {

    fun parse(
        reader: java.io.BufferedReader,
        maxSegments: Int = 150_000
    ): GcodePreview {
        var x = 0f
        var y = 0f
        var z = 0f
        var e = 0f
        var absoluteXyz = true
        var absoluteE = true
        var layerZ = 0f

        val layers = mutableListOf<PreviewLayer>()
        var current = mutableListOf<PreviewLine>()
        var segments = 0
        var truncated = false

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        fun notePoint(px: Float, py: Float) {
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
        }

        fun flush() {
            // Only layers that actually extrude are worth drawing; a trailing travel move would
            // otherwise become an empty final layer and the preview would open blank.
            if (current.any { it.extruding }) layers += PreviewLayer(current)
            current = mutableListOf()
        }

        var line = reader.readLine()
        while (line != null) {
            if (segments >= maxSegments) {
                truncated = true
                break
            }
            val code = line.substringBefore(';').trim()
            if (code.isNotEmpty()) {
                val tokens = code.split(' ')
                when (tokens[0].uppercase()) {
                    "G0", "G00", "G1", "G01" -> {
                        var nx = x
                        var ny = y
                        var nz = z
                        var ne = e
                        var hasE = false
                        for (i in 1 until tokens.size) {
                            val token = tokens[i]
                            if (token.isEmpty()) continue
                            val value = token.substring(1).toFloatOrNull() ?: continue
                            when (token[0].uppercaseChar()) {
                                'X' -> nx = if (absoluteXyz) value else x + value
                                'Y' -> ny = if (absoluteXyz) value else y + value
                                'Z' -> nz = if (absoluteXyz) value else z + value
                                'E' -> {
                                    hasE = true
                                    ne = if (absoluteE) value else e + value
                                }
                            }
                        }
                        val extruding = hasE && if (absoluteE) ne > e + 1e-4f else ne > 1e-4f
                        // A new Z only starts a layer when the move actually extrudes, and only
                        // once the current layer has extrusions: the travel toward a new layer
                        // belongs to that layer, not to an empty one of its own.
                        if (extruding && nz != layerZ && current.any { it.extruding }) {
                            flush()
                            layerZ = nz
                        } else if (extruding) {
                            layerZ = nz
                        }
                        if (nx != x || ny != y) {
                            current += PreviewLine(x, y, nx, ny, extruding)
                            segments++
                            if (extruding) {
                                notePoint(x, y)
                                notePoint(nx, ny)
                            }
                            x = nx
                            y = ny
                        } else {
                            x = nx
                            y = ny
                        }
                        z = nz
                        e = ne
                    }

                    "G90" -> absoluteXyz = true
                    "G91" -> absoluteXyz = false
                    "M82" -> absoluteE = true
                    "M83" -> absoluteE = false
                    "G92" -> {
                        for (i in 1 until tokens.size) {
                            val token = tokens[i]
                            val value = token.substring(1).toFloatOrNull() ?: continue
                            when (token[0].uppercaseChar()) {
                                'E' -> e = value
                                'X' -> x = value
                                'Y' -> y = value
                                'Z' -> z = value
                            }
                        }
                    }
                }
            }
            line = reader.readLine()
        }
        flush()

        if (minX > maxX) {
            // Nothing extruded (all travel, or not a G-code file): fall back to a unit box.
            minX = 0f; maxX = 1f; minY = 0f; maxY = 1f
        }
        return GcodePreview(layers, minX, maxX, minY, maxY, truncated)
    }
}
