package dev.readout.app.data

/** One straight move in the XY plane. Travel moves are kept but marked, so the UI can ignore them. */
data class PreviewLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val extruding: Boolean
)

/**
 * One printed layer: the segments to draw, plus what the slicer put in it.
 *
 * @param z the height this layer was printed at, mm
 * @param commands G-code commands read while this layer was current
 * @param filamentMm filament advanced by this layer's extrusions, mm
 * @param seconds move time at the commanded feedrates; acceleration and dwells are not modelled
 */
data class PreviewLayer(
    val lines: List<PreviewLine>,
    val z: Float,
    val commands: Int,
    val filamentMm: Float,
    val seconds: Float
)

/** Toolpaths grouped by the Z they were printed at, plus the bounds the drawing must fit. */
data class GcodePreview(
    val layers: List<PreviewLayer>,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float,
    val truncated: Boolean,
    /** The slicer's own estimate from a `;TIME:` or "estimated printing time" comment, if it left one. */
    val slicerSeconds: Long? = null
) {
    val isEmpty: Boolean get() = layers.isEmpty()

    /** Extent in the plane the preview draws, used to fit the drawing. */
    val xyWidth: Float get() = (maxX - minX).coerceAtLeast(0.001f)
    val xyHeight: Float get() = (maxY - minY).coerceAtLeast(0.001f)

    val modelWidth: Float get() = (maxX - minX).coerceAtLeast(0f)
    val modelDepth: Float get() = (maxY - minY).coerceAtLeast(0f)
    val modelHeight: Float get() = (maxZ - minZ).coerceAtLeast(0f)

    /**
     * What a slicer would call the layer height: the median step between consecutive layers, so
     * one adaptive layer in an otherwise 0.2 mm file does not move the number.
     */
    val layerHeight: Float
        get() {
            if (layers.size < 2) return 0f
            val steps = layers.zipWithNext { a, b -> b.z - a.z }.filter { it > 0.0001f }.sorted()
            if (steps.isEmpty()) return 0f
            return if (steps.size % 2 == 1) {
                steps[steps.size / 2]
            } else {
                (steps[steps.size / 2 - 1] + steps[steps.size / 2]) / 2f
            }
        }

    val filamentMm: Float get() = layers.fold(0f) { total, layer -> total + layer.filamentMm }

    /** What the moves themselves take, ignoring acceleration. Null when the file has no feedrates. */
    val moveSeconds: Long?
        get() {
            val seconds = layers.fold(0f) { total, layer -> total + layer.seconds }
            return if (seconds > 0f) seconds.toLong() else null
        }

    /** The slicer's number when there is one; otherwise what the moves take. */
    val printSeconds: Long? get() = slicerSeconds ?: moveSeconds

    val printTimeIsEstimate: Boolean get() = slicerSeconds == null && moveSeconds != null
}

/**
 * Reads a G-code stream into per-layer line segments and their statistics. The subset is small on
 * purpose: G0/G1, G90/G91, M82/M83, G92, plus the two comment forms slicers use to state a duration.
 */
object GcodePreviewParser {

    private val curaTime = Regex("""^;TIME:(\d+)\s*$""")

    private val durations = Regex(
        """(?i)^;\s*(?:estimated printing time(?:[^=]*)?|total estimated time|estimated print time)\s*[:=]\s*(.+)$"""
    )

    private val durationPart = Regex(
        """(\d+(?:[.,]\d+)?)\s*(d|days?|h|hours?|m|mins?|minutes?|s|secs?|seconds?)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        reader: java.io.BufferedReader,
        maxSegments: Int = 150_000
    ): GcodePreview {
        var x = 0f
        var y = 0f
        var z = 0f
        var e = 0f
        // Feedrate is modal: it carries across lines and layers until the next F word.
        var feed = 0f
        var absoluteXyz = true
        var absoluteE = true
        var layerZ = 0f

        val layers = mutableListOf<PreviewLayer>()
        var current = mutableListOf<PreviewLine>()
        var commands = 0
        var filament = 0f
        var seconds = 0f
        var segments = 0
        var truncated = false
        var slicerSeconds: Long? = null

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        fun notePoint(px: Float, py: Float, pz: Float) {
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
            if (pz < minZ) minZ = pz
            if (pz > maxZ) maxZ = pz
        }

        fun flush() {
            // Only layers that actually extrude are worth drawing; a trailing travel move would
            // otherwise become an empty final layer and the preview would open blank.
            if (current.any { it.extruding }) {
                layers += PreviewLayer(current, layerZ, commands, filament, seconds)
            }
            current = mutableListOf()
            commands = 0
            filament = 0f
            seconds = 0f
        }

        fun readDuration(text: String): Long? {
            var total = 0.0
            var matched = false
            for (part in durationPart.findAll(text)) {
                val value = part.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
                val factor = when (part.groupValues[2].lowercase().first()) {
                    'd' -> 86_400.0
                    'h' -> 3_600.0
                    'm' -> 60.0
                    else -> 1.0
                }
                total += value * factor
                matched = true
            }
            return if (matched && total > 0) total.toLong() else null
        }

        var line = reader.readLine()
        while (line != null) {
            if (segments >= maxSegments) {
                truncated = true
                break
            }
            if (slicerSeconds == null && line.startsWith(";")) {
                val trimmed = line.trim()
                curaTime.find(trimmed)?.let { slicerSeconds = it.groupValues[1].toLongOrNull() }
                if (slicerSeconds == null) {
                    durations.find(trimmed)?.let { slicerSeconds = readDuration(it.groupValues[1]) }
                }
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
                                'F' -> if (value > 0f) feed = value
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
                        // Counted after any flush, so the move that opens a layer is charged to it.
                        commands++
                        if (nx != x || ny != y) {
                            val distance = kotlin.math.hypot(nx - x, ny - y)
                            if (feed > 0f) seconds += distance * 60f / feed
                            if (extruding) {
                                // ne is already resolved to an absolute position in both modes,
                                // so the advance is always the difference.
                                filament += (ne - e).coerceAtLeast(0f)
                                notePoint(x, y, z)
                                notePoint(nx, ny, nz)
                            }
                            current += PreviewLine(x, y, nx, ny, extruding)
                            segments++
                        }
                        x = nx
                        y = ny
                        z = nz
                        e = ne
                    }

                    "G90" -> {
                        absoluteXyz = true
                        commands++
                    }

                    "G91" -> {
                        absoluteXyz = false
                        commands++
                    }

                    "M82" -> {
                        absoluteE = true
                        commands++
                    }

                    "M83" -> {
                        absoluteE = false
                        commands++
                    }

                    "G92" -> {
                        commands++
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

                    else -> commands++
                }
            }
            line = reader.readLine()
        }
        flush()

        if (minX > maxX) {
            // Nothing extruded (all travel, or not a G-code file): fall back to a unit box.
            minX = 0f; maxX = 1f; minY = 0f; maxY = 1f
            minZ = 0f; maxZ = 0f
        }
        return GcodePreview(
            layers = layers,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            minZ = minZ,
            maxZ = maxZ,
            truncated = truncated,
            slicerSeconds = slicerSeconds
        )
    }
}
