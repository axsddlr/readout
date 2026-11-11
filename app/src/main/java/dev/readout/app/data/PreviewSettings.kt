package dev.readout.app.data

/** How much of the whole part is drawn behind the selected layer. */
enum class PreviewQuality {    /** Samples the ghost heavily; cheapest on a large file. */
    Low,

    /** Half the ghost's segments. */
    Medium,

    /** Every segment of the ghost. */
    High
}

/**
 * The layer index a following viewer should show, from a 1-based layer count as the printer reports
 * it. Null when there is nothing to follow yet, and clamped so an off-by-one in either direction
 * cannot point outside the file.
 */
fun followedLayerIndex(reported: Int?, total: Int): Int? =
    if (reported == null || total <= 0) null else (reported - 1).coerceIn(0, total - 1)

/** How the toolpath preview draws. The ghost of the whole part is the expensive geometry, so the
 * quality setting samples it rather than thinning the layer the user is actually looking at.
 */
data class PreviewSettings(
    val previousLayer: Boolean = true,
    val currentLayer: Boolean = true,
    val quality: PreviewQuality = PreviewQuality.Medium
) {
    /** Draw one ghost segment out of every [ghostStep]. */
    val ghostStep: Int
        get() = when (quality) {
            PreviewQuality.Low -> 4
            PreviewQuality.Medium -> 2
            PreviewQuality.High -> 1
        }

    /** Stroke width in canvas pixels, before the drawing's own scale is applied. */
    val strokeWidth: Float
        get() = when (quality) {
            PreviewQuality.Low -> 1.2f
            PreviewQuality.Medium -> 1.8f
            PreviewQuality.High -> 2.2f
        }

    val qualityDescription: String
        get() = when (quality) {
            PreviewQuality.Low -> "Fewest segments; fastest on a large file."
            PreviewQuality.Medium -> "Balanced performance and fidelity."
            PreviewQuality.High -> "Every segment of the whole part; smoothest, heaviest."
        }
}
