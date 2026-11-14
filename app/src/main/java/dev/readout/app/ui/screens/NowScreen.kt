package dev.readout.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import dev.readout.app.data.CameraAspect
import dev.readout.app.data.CameraSettings
import dev.readout.app.data.ComponentTemperature
import dev.readout.app.data.ConnectionPhase
import dev.readout.app.data.MachineState
import dev.readout.app.data.PrinterSnapshot
import dev.readout.app.data.UiState
import dev.readout.app.ui.ReadoutIcons
import dev.readout.app.ui.components.Plate
import dev.readout.app.ui.components.ReadoutProgress
import dev.readout.app.ui.components.SectionHeading
import dev.readout.app.ui.components.StatusCard
import dev.readout.app.ui.components.StatusDot
import dev.readout.app.ui.components.TemperatureSheet
import dev.readout.app.ui.components.TemperatureTile
import dev.readout.app.ui.components.TileGrid
import dev.readout.app.ui.theme.LocalPalette
import dev.readout.app.ui.theme.ReadoutHeroStyle
import dev.readout.app.ui.theme.ReadoutStyle
import dev.readout.app.ui.theme.Spacing
import dev.readout.app.util.Format

private val ActiveJob = setOf(
    MachineState.Printing, MachineState.Paused, MachineState.Pausing,
    MachineState.Resuming, MachineState.Finishing
)
private val Cancellable = setOf(
    MachineState.Printing, MachineState.Paused, MachineState.Pausing, MachineState.Resuming
)

/**
 * "Now": one tonal card states the machine's condition and carries the job's progress;
 * the primary action sits directly under it; camera and heaters follow.
 */
@Composable
fun NowScreen(
    state: UiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenMachine: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetTarget: (ComponentTemperature, Int) -> Unit,
    cameraFrame: StateFlow<ImageBitmap?>,
    cameraLive: Boolean,
    cameraAvailable: Boolean,
    cameraSettings: CameraSettings,
    onCameraSettings: (CameraSettings) -> Unit,
    onCameraFullscreen: (Boolean) -> Unit,
    onCameraVisible: (Boolean) -> Unit,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val palette = LocalPalette.current
    var editing by remember { mutableStateOf<ComponentTemperature?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var showCameraSettings by rememberSaveable { mutableStateOf(false) }
    var fullscreenCamera by rememberSaveable { mutableStateOf(false) }
    val machineState = snapshot?.state
    val cameraName = state.config?.let { config ->
        config.webcamUrl?.let { url -> runCatching { java.net.URI(url).host }.getOrNull() }
            ?: "Printer webcam"
    } ?: "Printer webcam"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l)
    ) {
        Spacer(Modifier.height(Spacing.s))

        // -- Status -------------------------------------------------------------------------
        StatusCard(
            state = machineState,
            stateLabel = snapshot?.stateLabel ?: when (state.phase) {
                ConnectionPhase.Connecting -> "Connecting"
                ConnectionPhase.Fault -> "No contact"
                ConnectionPhase.Offline -> "Offline"
                else -> "Ready"
            },
            detail = snapshot?.jobName ?: when {
                state.phase == ConnectionPhase.Offline -> state.errorMessage ?: "Showing the last reading"
                state.phase == ConnectionPhase.Fault -> state.errorMessage ?: "The printer did not answer"
                machineState == MachineState.Error -> "The machine reported a fault"
                machineState == MachineState.Disconnected -> "OctoPrint is not connected to a printer"
                machineState == MachineState.Starting -> "Bringing the machine up"
                snapshot == null -> "Waiting for the first reading"
                else -> "Nothing is printing"
            }
        ) {
            if (snapshot != null && machineState in ActiveJob) {
                JobProgress(snapshot)
            }
        }

        // -- Actions ------------------------------------------------------------------------
        Spacer(Modifier.height(Spacing.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val primaryModifier = Modifier.weight(1f).heightIn(min = 56.dp)
            when (machineState) {
                MachineState.Printing -> Button(onClick = onPause, modifier = primaryModifier) {
                    Icon(ReadoutIcons.Pause, contentDescription = null)
                    Spacer(Modifier.width(Spacing.s))
                    Text("Pause")
                }

                MachineState.Paused -> Button(onClick = onResume, modifier = primaryModifier) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(Spacing.s))
                    Text("Resume")
                }

                else -> Button(onClick = onOpenFiles, modifier = primaryModifier) {
                    Text("Start a print")
                }
            }

            if (machineState in Cancellable) {
                OutlinedButton(
                    onClick = { confirmCancel = true },
                    modifier = Modifier.heightIn(min = 56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel")
                }
            } else {
                FilledTonalButton(onClick = onOpenMachine, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Machine")
                }
            }
        }

        // -- Camera -------------------------------------------------------------------------
        SectionHeading(
            title = "Camera",
            trailing = {
                IconButton(onClick = { showCameraSettings = true }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Camera settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        CameraCard(
            enabled = state.phase == ConnectionPhase.Live,
            frameFlow = cameraFrame,
            live = cameraLive,
            available = cameraAvailable,
            stateLabel = snapshot?.stateLabel,
            settings = cameraSettings,
            cameraName = cameraName,
            onVisible = onCameraVisible,
            onOpenFullscreen = { fullscreenCamera = true },
            onConfigure = onOpenSettings
        )

        // -- Toolpaths ----------------------------------------------------------------------
        // A job is the only time a layer can be followed, so the entry point only exists then.
        if (snapshot?.jobName != null) {
            SectionHeading("Toolpaths")
            Plate(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPreview)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(ReadoutIcons.File, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Spacing.l))
                    Column(Modifier.weight(1f)) {
                        Text("G-code preview", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = when {
                                snapshot.layer != null && snapshot.layerCount != null ->
                                    "Layer ${snapshot.layer} of ${snapshot.layerCount}, updating live"
                                else -> "Watch the toolpaths advance with the print"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // -- Temperatures -------------------------------------------------------------------
        SectionHeading("Temperatures")
        val temperatures = snapshot?.temperatures.orEmpty()
        if (temperatures.isEmpty()) {
            Plate(Modifier.fillMaxWidth()) {
                Text(
                    "No temperature reported yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.s)
                )
            }
        } else {
            TileGrid(temperatures) { component, tileModifier ->
                TemperatureTile(component, tileModifier, onClick = { editing = component })
            }
        }

        // -- Provenance ---------------------------------------------------------------------
        Spacer(Modifier.height(Spacing.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(
                if (state.phase == ConnectionPhase.Live) palette.ready else MaterialTheme.colorScheme.outline,
                size = 6.dp
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                text = when {
                    state.phase == ConnectionPhase.Live ->
                        "${snapshot?.octoPrintVersion ?: "OctoPrint"} · updated ${Format.age(state.lastUpdateMs)}"
                    state.lastUpdateMs == null -> "Trying to reach the printer…"
                    // Saying it is retrying matters: otherwise a stale screen looks like a dead end.
                    else -> "Reconnecting… last update ${Format.age(state.lastUpdateMs)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
    }

    editing?.let { component ->
        TemperatureSheet(
            component = component,
            onDismiss = { editing = null },
            onApply = { value ->
                onSetTarget(component, value)
                editing = null
            }
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this print?") },
            text = { Text("The machine will stop where it is. This cannot be undone from the app.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    onCancel()
                }) { Text("Cancel print", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep printing") }
            }
        )
    }

    if (showCameraSettings) {
        CameraSettingsSheet(
            settings = cameraSettings,
            onChange = onCameraSettings,
            onDismiss = { showCameraSettings = false }
        )
    }

    if (fullscreenCamera) {
        dev.readout.app.ui.components.CameraFullscreen(
            frameFlow = cameraFrame,
            live = cameraLive,
            snapshot = snapshot,
            cameraName = cameraName,
            settings = cameraSettings,
            onFullscreen = onCameraFullscreen,
            onClose = { fullscreenCamera = false }
        )
    }
}

/**
 * The camera's own settings: the shape of the card, what is printed over the picture, and whether
 * to spend the stream's bandwidth at all. One camera per printer, so the values are app-wide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraSettingsSheet(
    settings: CameraSettings,
    onChange: (CameraSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xxl)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Camera settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "The picture comes from OctoPrint's webcam; these choices only change how Readout shows it.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.l))

            Text("Aspect ratio", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CameraAspect.entries.forEach { aspect ->
                    FilterChip(
                        selected = settings.aspect == aspect,
                        onClick = { onChange(settings.copy(aspect = aspect)) },
                        label = { Text(aspect.label) }
                    )
                }
            }

            Spacer(Modifier.height(Spacing.s))
            CameraToggle(
                title = "Show resolution",
                checked = settings.showResolution,
                onChange = { onChange(settings.copy(showResolution = it)) }
            )
            CameraToggle(
                title = "Show name",
                checked = settings.showName,
                onChange = { onChange(settings.copy(showName = it)) }
            )
            CameraToggle(
                title = "Data saver webcam",
                checked = settings.dataSaver,
                onChange = { onChange(settings.copy(dataSaver = it)) }
            )
            Text(
                "Takes a snapshot every few seconds instead of pulling the live stream, which costs" +
                    " the camera's full bitrate.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.s)
            )
            CameraToggle(
                title = "Ignore rotation lock",
                checked = settings.ignoreRotationLock,
                onChange = { onChange(settings.copy(ignoreRotationLock = it)) }
            )
            Text(
                "Fullscreen follows the sensor even when the phone's rotation is locked, which is" +
                    " how a clamped phone still turns the camera the right way up.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CameraToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Percentage, bar, and times — drawn inside the status card, in its content colour. */
@Composable
private fun JobProgress(snapshot: PrinterSnapshot) {
    val content = androidx.compose.material3.LocalContentColor.current
    val muted = content.copy(alpha = 0.72f)
    Spacer(Modifier.height(Spacing.xl))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(Format.percent(snapshot.progress), style = ReadoutHeroStyle, color = content)
        Spacer(Modifier.weight(1f))
        if (snapshot.layer != null && snapshot.layerCount != null) {
            Text(
                // An estimated layer is marked, because it is derived from the completion
                // percentage rather than read from the machine.
                text = if (snapshot.layerEstimated) {
                    "≈ Layer ${snapshot.layer} of ${snapshot.layerCount}"
                } else {
                    "Layer ${snapshot.layer} of ${snapshot.layerCount}"
                },
                style = MaterialTheme.typography.labelLarge,
                color = muted,
                modifier = Modifier.padding(bottom = Spacing.s)
            )
        }
    }
    Spacer(Modifier.height(Spacing.m))
    ReadoutProgress(progress = snapshot.progress, color = content, trackColor = content.copy(alpha = 0.18f))
    Spacer(Modifier.height(Spacing.l))
    Row(Modifier.fillMaxWidth()) {
        Metric("Elapsed", Format.duration(snapshot.printTimeSeconds), Modifier.weight(1f))
        Metric("Left", Format.eta(snapshot.printTimeLeftSeconds), Modifier.weight(1f))
    }
    // The clock time the job should end, for as long as it is actually moving. A paused job has
    // no meaningful ETA, so it is omitted rather than shown stale.
    if (snapshot.state == MachineState.Printing) {
        Format.etaEpochMillis(System.currentTimeMillis(), snapshot.printTimeLeftSeconds)?.let { eta ->
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "ETA " + Format.clockTime(LocalContext.current, eta),
                style = MaterialTheme.typography.labelLarge,
                color = muted
            )
        }
    }
    Format.etaOrigin(snapshot.etaOrigin)?.let { note ->
        Spacer(Modifier.height(Spacing.s))
        Text(note, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    val content = androidx.compose.material3.LocalContentColor.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.72f))
        Text(value, style = ReadoutStyle, color = content)
    }
}

@Composable
private fun CameraCard(
    enabled: Boolean,
    frameFlow: StateFlow<ImageBitmap?>,
    live: Boolean,
    available: Boolean,
    stateLabel: String?,
    settings: CameraSettings,
    cameraName: String,
    onVisible: (Boolean) -> Unit,
    onOpenFullscreen: () -> Unit,
    onConfigure: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // Collected here, not in the screen: a frame arriving must recompose the picture, not the
    // status card, temperatures and the rest of Now at 25 times a second.
    val frame by frameFlow.collectAsStateWithLifecycle()

    // The stream lives only as long as this card is composed and connected: leaving Now, closing
    // the app or losing the link all stop it rather than leaving a socket open.
    DisposableEffect(enabled) {
        onVisible(enabled)
        onDispose { onVisible(false) }
    }

    if (frame == null && !available) {
        Plate(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(ReadoutIcons.Camera, contentDescription = null, tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(Spacing.l))
                Column(Modifier.weight(1f)) {
                    Text("No camera found", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Set a camera URL in Settings if it is not at the printer's own address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onConfigure) { Text("Set up") }
            }
        }
        return
    }

    val picture = frame
    val ratio = settings.aspect.ratio
        ?: picture?.let { it.width.toFloat() / it.height.toFloat() }?.takeIf { it > 0f }
        ?: (16f / 9f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHigh)
    ) {
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = "Camera view of the printer",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (stateLabel != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.m),
                    shape = CircleShape,
                    color = scheme.inverseSurface.copy(alpha = 0.72f),
                    contentColor = scheme.inverseOnSurface
                ) {
                    Text(
                        stateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)
                    )
                }
            }
            // A snapshot every few seconds is not live, and saying so is the difference between a
            // viewer and a promise.
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.m),
                shape = CircleShape,
                color = scheme.inverseSurface.copy(alpha = 0.72f),
                contentColor = scheme.inverseOnSurface
            ) {
                Text(
                    text = if (live) "Live" else "Stills",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)
                )
            }
            if (settings.showName || settings.showResolution) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    if (settings.showName) {
                        Surface(
                            shape = CircleShape,
                            color = scheme.inverseSurface.copy(alpha = 0.72f),
                            contentColor = scheme.inverseOnSurface
                        ) {
                            Text(
                                cameraName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)
                            )
                        }
                    }
                    if (settings.showResolution) {
                        Surface(
                            shape = CircleShape,
                            color = scheme.inverseSurface.copy(alpha = 0.72f),
                            contentColor = scheme.inverseOnSurface
                        ) {
                            Text(
                                "${picture.width}×${picture.height}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)
                            )
                        }
                    }
                }
            }
            // The mark alone, in white: a filled pill here reads as a control for the picture rather
            // than a way to step back from it.
            IconButton(
                onClick = onOpenFullscreen,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.s)
            ) {
                Icon(
                    imageVector = ReadoutIcons.Expand,
                    contentDescription = "Fullscreen camera",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (enabled) "Connecting to the camera" else "Waiting for the printer",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}
