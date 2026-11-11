package dev.readout.app.data

/** Everything the UI needs to describe the machine in one immutable object. */
data class PrinterSnapshot(
    val state: MachineState,
    val stateLabel: String,
    val temperatures: List<ComponentTemperature>,
    val jobName: String? = null,
    val progress: Float? = null,
    val printTimeSeconds: Long? = null,
    val printTimeLeftSeconds: Long? = null,
    val etaOrigin: String? = null,
    val layer: Int? = null,
    val layerCount: Int? = null,
    /** True when the layer was derived from completion rather than reported by a plugin. */
    val layerEstimated: Boolean = false,
    val jobOrigin: String? = null,
    val jobPath: String? = null,
    val octoPrintVersion: String? = null,
    val connectionText: String? = null
)

data class ComponentTemperature(
    val key: String,
    val label: String,
    val actual: Float,
    val target: Float,
    val index: Int
) {
    val isHeating: Boolean get() = target > 0f && actual < target - 1.5f
    val isAtTarget: Boolean get() = target > 0f && kotlin.math.abs(actual - target) <= 1.5f
}

enum class MachineState {
    Idle, Starting, Printing, Paused, Pausing, Resuming, Cancelling, Finishing, Error, Disconnected, Unknown
}

/** True while a job occupies the machine — the states where moving an axis can ruin a print. */
val MachineState.jobInProgress: Boolean
    get() = this in setOf(
        MachineState.Starting, MachineState.Printing, MachineState.Paused, MachineState.Pausing,
        MachineState.Resuming, MachineState.Finishing, MachineState.Cancelling
    )

/** Movement stays live unless a job is running and the user has opted in to moving anyway. */
fun movementAllowed(state: MachineState?, allowWhilePrinting: Boolean): Boolean =
    allowWhilePrinting || state == null || !state.jobInProgress

data class PrinterConfig(
    val id: String = "",
    val baseUrl: String,
    val apiKey: String,
    val name: String = "Printer",
    val webcamUrl: String? = null
) {
    val displayHost: String
        get() = baseUrl.removePrefix("http://").removePrefix("https://").trimEnd('/')
}

/** The two ways to read one camera: a single JPEG, and the live stream of them. */
data class CameraUrls(val still: String, val stream: String)

/**
 * Resolves the camera URLs from the configured webcam address.
 *
 * OctoPrint's webcam proxy answers both `?action=snapshot` and `?action=stream` on the same path, so
 * one configured URL implies the other. A configured *stream* URL is honoured for streaming and
 * reversed for the still, which is what someone who pastes a stream link expects. An endpoint that
 * matches neither shape is used for both and simply falls back to stills if it is not a stream.
 */
fun cameraUrls(config: PrinterConfig): CameraUrls {
    val base = config.baseUrl.trimEnd('/')
    val configured = config.webcamUrl?.takeIf { it.isNotBlank() }
    return when {
        configured == null -> CameraUrls(
            still = "$base/webcam/?action=snapshot",
            stream = "$base/webcam/?action=stream"
        )

        "action=stream" in configured -> CameraUrls(
            still = configured.replace("action=stream", "action=snapshot"),
            stream = configured
        )

        "action=snapshot" in configured -> CameraUrls(
            still = configured,
            stream = configured.replace("action=snapshot", "action=stream")
        )

        else -> CameraUrls(still = configured, stream = configured)
    }
}

/** What opening the camera URL produced. */
sealed interface CameraRead {
    /** A multipart stream that ended after [count] frames. */
    data class Frames(val count: Int) : CameraRead

    /** The URL answered with one image rather than a stream. */
    data class Single(val bytes: ByteArray) : CameraRead {
        override fun equals(other: Any?): Boolean =
            other is Single && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** No camera at that address, or something that was not an image at all. */
    data object Nothing : CameraRead
}

data class FileEntry(
    val name: String,
    val path: String,
    val origin: String,
    val sizeBytes: Long,
    val dateSeconds: Long?,
    val isDirectory: Boolean
)

data class TimelapseEntry(
    val name: String,
    val sizeBytes: Long,
    val dateSeconds: Long?
)

/** One switchable output, whatever plugin provides it. */
data class PowerSwitch(
    val id: String,
    val name: String,
    val on: Boolean,
    val source: PowerSource
)

enum class PowerSource { Psu, Relay, Wled }

data class RelayState(val id: String, val name: String, val on: Boolean)

/** One printer's state inside the fleet view. */
data class FleetStatus(
    val id: String,
    val name: String,
    val online: Boolean,
    val stateLabel: String,
    val jobName: String?,
    val progress: Float?
)

enum class FleetAction { Pause, Resume, Cancel }

/** A job file and the printers that hold a copy, for starting one print across a fleet. */
data class FleetFile(val name: String, val printerIds: List<String>)

enum class ConnectionPhase { Unconfigured, Connecting, Live, Offline, Fault }

data class DiscoveredPrinter(
    val name: String,
    val host: String,
    val port: Int
) {
    val url: String get() = if (port == 80 || port == 0) "http://$host" else "http://$host:$port"
}

data class UiState(
    val config: PrinterConfig? = null,
    val instances: List<PrinterConfig> = emptyList(),
    val phase: ConnectionPhase = ConnectionPhase.Unconfigured,
    val snapshot: PrinterSnapshot? = null,
    val lastUpdateMs: Long? = null,
    val errorMessage: String? = null,
    val notice: String? = null,
    val busy: Boolean = false,
    val discovered: List<DiscoveredPrinter> = emptyList(),
    val discovering: Boolean = false,
    val powerSwitches: List<PowerSwitch> = emptyList(),
    val appKey: AppKeyState = AppKeyState.Idle,
    val fleet: List<FleetStatus> = emptyList()
)

sealed interface CommandResult {
    data object Ok : CommandResult
    data class Failed(val message: String) : CommandResult
}

/** State of the App Keys (2FA) handshake on the connect screen. */
sealed interface AppKeyState {
    data object Idle : AppKeyState
    data object Waiting : AppKeyState
    data object Unavailable : AppKeyState
    data class Failed(val message: String) : AppKeyState
}
