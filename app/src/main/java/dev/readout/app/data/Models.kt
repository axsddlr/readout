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
