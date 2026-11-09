package dev.readout.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.readout.app.data.ComponentTemperature
import dev.readout.app.data.ConnectionPhase
import dev.readout.app.data.MachineState
import dev.readout.app.data.PrinterSnapshot
import dev.readout.app.data.UiState
import dev.readout.app.ui.ReadoutIcons
import dev.readout.app.ui.components.AnnotatedSchematic
import dev.readout.app.ui.components.Plate
import dev.readout.app.ui.components.RulerProgress
import dev.readout.app.ui.components.SectionHeading
import dev.readout.app.ui.components.SpecRow
import dev.readout.app.ui.components.StatusPlate
import dev.readout.app.ui.components.TemperatureSheet
import dev.readout.app.ui.theme.LocalPalette
import dev.readout.app.ui.theme.ReadoutHeroStyle
import dev.readout.app.ui.theme.ReadoutStyle
import dev.readout.app.ui.theme.Spacing
import dev.readout.app.util.Format
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * "Now" — the sheet you look at. One plate states the machine's condition, one ruler its
 * progress, one diagram its temperatures, and one row of controls acts on it.
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
    fetchFrame: suspend () -> ByteArray?,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    val palette = LocalPalette.current
    var editing by remember { mutableStateOf<ComponentTemperature?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        StatusPlate(
            state = snapshot?.state ?: MachineState.Unknown,
            stateLabel = snapshot?.stateLabel ?: when (state.phase) {
                ConnectionPhase.Connecting -> "Connecting"
                ConnectionPhase.Fault -> "No contact"
                ConnectionPhase.Offline -> "Offline"
                else -> "Ready"
            },
            jobName = snapshot?.jobName,
            fallbackDetail = when {
                state.phase == ConnectionPhase.Offline -> state.errorMessage ?: "Last reading kept below"
                state.phase == ConnectionPhase.Fault -> state.errorMessage ?: "The printer did not answer"
                else -> state.config?.displayHost ?: "No machine"
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l)
        ) {
            Spacer(Modifier.height(Spacing.l))

            // -- Job sheet -----------------------------------------------------------------
            if (snapshot?.state == MachineState.Printing ||
                snapshot?.state == MachineState.Paused ||
                snapshot?.state == MachineState.Pausing ||
                snapshot?.state == MachineState.Resuming ||
                snapshot?.state == MachineState.Finishing
            ) {
                JobSheet(snapshot = snapshot, onCancelRequested = { confirmCancel = true })
            } else {
                IdleSheet(snapshot = snapshot, onOpenFiles = onOpenFiles)
            }

            // -- Controls ------------------------------------------------------------------
            Spacer(Modifier.height(Spacing.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                when (snapshot?.state) {
                    MachineState.Printing -> Button(
                        onClick = onPause,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("PAUSE", style = MaterialTheme.typography.labelLarge)
                    }

                    MachineState.Paused -> Button(
                        onClick = onResume,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("RESUME", style = MaterialTheme.typography.labelLarge)
                    }

                    else -> Button(
                        onClick = onOpenFiles,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("OPEN FILES", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (snapshot?.state in setOf(
                        MachineState.Printing, MachineState.Paused,
                        MachineState.Pausing, MachineState.Resuming
                    )
                ) {
                    OutlinedButton(
                        onClick = { confirmCancel = true },
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("CANCEL", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenMachine,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("MACHINE", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // -- Camera --------------------------------------------------------------------
            SectionHeading("02", "Camera") {
                Text(
                    text = "2 s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CameraPlate(
                enabled = state.phase == ConnectionPhase.Live,
                configured = state.config?.webcamUrl != null,
                fetchFrame = fetchFrame,
                onConfigure = onOpenSettings
            )

            // -- Temperatures --------------------------------------------------------------
            SectionHeading("03", "Temperatures")

            Plate(Modifier.fillMaxWidth()) {
                Column {
                    val temperatures = snapshot?.temperatures.orEmpty()
                    if (temperatures.isEmpty()) {
                        Text(
                            "No temperature reported yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AnnotatedSchematic(components = temperatures)
                        Spacer(Modifier.height(Spacing.s))
                        temperatures.forEachIndexed { index, component ->
                            SpecRow(
                                number = index + 1,
                                label = component.label,
                                value = Format.targetAndActual(component.actual, component.target),
                                secondary = when {
                                    component.isHeating -> "heating to ${component.target.toInt()}°"
                                    component.isAtTarget -> "at temperature"
                                    component.target > 0f -> "cooling to ${component.target.toInt()}°"
                                    else -> "off"
                                },
                                onClick = { editing = component }
                            )
                        }
                        Text(
                            text = "Tap a component to set its target.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.l))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            if (state.phase == ConnectionPhase.Live) palette.ready else palette.work,
                            RoundedCornerShape(3.dp)
                        )
                )
                Spacer(Modifier.width(Spacing.s))
                Text(
                    text = buildString {
                        append(snapshot?.octoPrintVersion ?: "OctoPrint")
                        append(" · ")
                        append(Format.age(state.lastUpdateMs))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
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
            text = {
                Text("The machine will stop where it is. This cannot be undone from the app.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    onCancel()
                }) { Text("CANCEL PRINT", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("KEEP PRINTING") }
            }
        )
    }
}

@Composable
private fun JobSheet(snapshot: PrinterSnapshot, onCancelRequested: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = Format.percent(snapshot.progress),
                style = ReadoutHeroStyle,
                color = scheme.onSurface
            )
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.padding(bottom = Spacing.xs)) {
                Text(
                    text = "COMPLETE",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                if (snapshot.layer != null && snapshot.layerCount != null) {
                    Text(
                        text = "layer ${snapshot.layer} / ${snapshot.layerCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.s))
        RulerProgress(progress = snapshot.progress)
        Spacer(Modifier.height(Spacing.m))
        Row(Modifier.fillMaxWidth()) {
            Metric("ELAPSED", Format.duration(snapshot.printTimeSeconds), Modifier.weight(1f))
            Metric("REMAINING", Format.eta(snapshot.printTimeLeftSeconds), Modifier.weight(1f))
        }
        Format.etaOrigin(snapshot.etaOrigin)?.let { note ->
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(text = value, style = ReadoutStyle, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun IdleSheet(snapshot: PrinterSnapshot?, onOpenFiles: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = when (snapshot?.state) {
                MachineState.Error -> "The machine reported a fault."
                MachineState.Disconnected -> "OctoPrint is not connected to a printer."
                MachineState.Starting -> "Bringing the machine up."
                else -> if (snapshot == null) "Waiting for the first reading." else "The machine is idle."
            },
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = if (snapshot == null) {
                "Readout asks the printer for its state every two seconds. If this persists, " +
                    "open Settings and check the address."
            } else {
                "No job is running. Start one from the file library, or use the machine sheet " +
                    "to heat, home, or send G-code."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.m))
        TextButton(onClick = onOpenFiles, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Browse files →", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CameraPlate(
    enabled: Boolean,
    configured: Boolean,
    fetchFrame: suspend () -> ByteArray?,
    onConfigure: () -> Unit
) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var attempted by remember { mutableStateOf(false) }
    val palette = LocalPalette.current

    LaunchedEffect(enabled, configured) {
        if (!enabled || !configured) return@LaunchedEffect
        while (true) {
            val bytes = runCatching { fetchFrame() }.getOrNull()
            attempted = true
            if (bytes != null) {
                frame = runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
            delay(2_000)
        }
    }

    if (!configured) {
        Plate(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = ReadoutIcons.Camera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text("No camera configured", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Add a snapshot URL to see the print.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onConfigure) {
                    Text("SET UP", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
    ) {
        val current = frame
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = "Live camera frame from the printer",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.s)
                    .background(palette.plate, RoundedCornerShape(2.dp))
                    .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CAM 01",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = palette.onPlate
                )
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (!attempted || !enabled) "Waiting for a camera frame" else "No camera image",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Check the camera URL in Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
