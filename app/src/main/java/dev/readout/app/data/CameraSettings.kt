package dev.readout.app.data

/** How the camera card is shaped. */
enum class CameraAspect(val label: String, val ratio: Float?) {
    /** Follow the frames the camera sends. */
    FromImage("From image", null),
    Wide("16:9", 16f / 9f),
    Standard("4:3", 4f / 3f)
}

/**
 * How the camera card behaves and what it says over the picture.
 *
 * Data saver swaps the live stream for a snapshot every few seconds: the stream is the camera's
 * own bitrate (hundreds of KB a second), which is a real cost when watching over mobile data.
 */
data class CameraSettings(
    val aspect: CameraAspect = CameraAspect.FromImage,
    val showResolution: Boolean = false,
    val showName: Boolean = false,
    val dataSaver: Boolean = false
) {
    val streamLive: Boolean get() = !dataSaver
}
