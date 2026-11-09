package dev.readout.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.readout.app.data.ComponentTemperature
import dev.readout.app.data.MachineState
import dev.readout.app.data.PowerSource
import dev.readout.app.data.PowerSwitch
import dev.readout.app.data.PrinterSnapshot
import dev.readout.app.ui.components.DashedRule
import dev.readout.app.ui.components.Plate
import dev.readout.app.ui.components.SectionHeading
import dev.readout.app.ui.components.SpecRow
import dev.readout.app.ui.components.TemperatureSheet
import dev.readout.app.ui.theme.Spacing
import dev.readout.app.util.Format

/**
 * The machine sheet: every control the printer exposes, grouped the way a technician would
 * group them, each in reach with a thumb.
 */
@Composable
fun MachineScreen(
    snapshot: PrinterSnapshot?,
    busy: Boolean,
    onSetTarget: (ComponentTemperature, Int) -> Unit,
    onJog: (String, Float) -> Unit,
    onHome: () -> Unit,
    onExtrude: (Float) -> Unit,
    onGcode: (String) -> Unit,
    onConnectPrinter: () -> Unit,
    onDisconnectPrinter: () -> Unit,
    onOpenSettings: () -> Unit,
    powerSwitches: List<PowerSwitch> = emptyList(),
    onTogglePower: (PowerSwitch) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<ComponentTemperature?>(null) }
    var step by remember { mutableStateOf(10f) }
    var extrudeAmount by remember { mutableStateOf("10") }
    var command by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l)
    ) {
        if (powerSwitches.isNotEmpty()) {
            SectionHeading("00", "Power")
            Plate(Modifier.fillMaxWidth()) {
                Column {
                    powerSwitches.forEachIndexed { index, switch ->
                        if (index > 0) {
                            Spacer(Modifier.height(Spacing.s))
                            DashedRule()
                            Spacer(Modifier.height(Spacing.s))
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = switch.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        switch.source == PowerSource.Psu ->
                                            "PSU Control · " + if (switch.on) "live" else "off"
                                        switch.source == PowerSource.Wled ->
                                            "WLED · " + if (switch.on) "on" else "off"
                                        switch.on -> "OctoRelay · on"
                                        else -> "OctoRelay · off"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onTogglePower(switch) },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(if (switch.on) "TURN OFF" else "TURN ON")
                            }
                        }
                    }
                }
            }
        }

        SectionHeading("01", "Temperatures")
        Plate(Modifier.fillMaxWidth()) {
            Column {
                val temperatures = snapshot?.temperatures.orEmpty()
                if (temperatures.isEmpty()) {
                    Text(
                        "No components reported. Is the printer connected to OctoPrint?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                temperatures.forEachIndexed { index, component ->
                    SpecRow(
                        number = index + 1,
                        label = component.label,
                        value = Format.targetAndActual(component.actual, component.target),
                        secondary = "tap to set a target",
                        onClick = { editing = component }
                    )
                }
            }
        }

        SectionHeading("02", "Movement")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf(1f, 10f, 50f).forEach { value ->
                FilterChip(
                    selected = step == value,
                    onClick = { step = value },
                    label = { Text("${value.toInt()} mm") }
                )
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            JogButton("X −", Modifier.weight(1f), busy) { onJog("x", -step) }
            JogButton("X +", Modifier.weight(1f), busy) { onJog("x", step) }
        }
        Spacer(Modifier.height(Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            JogButton("Y −", Modifier.weight(1f), busy) { onJog("y", -step) }
            JogButton("Y +", Modifier.weight(1f), busy) { onJog("y", step) }
        }
        Spacer(Modifier.height(Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            JogButton("Z −", Modifier.weight(1f), busy) { onJog("z", -step) }
            JogButton("Z +", Modifier.weight(1f), busy) { onJog("z", step) }
        }
        Spacer(Modifier.height(Spacing.s))
        OutlinedButton(
            onClick = onHome,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(4.dp)
        ) { Text("HOME ALL AXES") }

        SectionHeading("03", "Extrude")
        OutlinedTextField(
            value = extrudeAmount,
            onValueChange = { extrudeAmount = it.filter { ch -> ch.isDigit() }.take(3) },
            label = { Text("Millimetres") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(Modifier.height(Spacing.s))
        val amount = extrudeAmount.toFloatOrNull() ?: 0f
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Button(
                onClick = { onExtrude(amount) },
                enabled = !busy && amount > 0f,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(4.dp)
            ) { Text("EXTRUDE") }
            OutlinedButton(
                onClick = { onExtrude(-amount) },
                enabled = !busy && amount > 0f,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(4.dp)
            ) { Text("RETRACT") }
        }

        SectionHeading("04", "Command")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("G-code") },
                placeholder = { Text("G28 X Y") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp)
            )
            Spacer(Modifier.width(Spacing.s))
            Button(
                onClick = {
                    val line = command.trim()
                    if (line.isNotEmpty()) {
                        onGcode(line)
                        history.add(0, line)
                        if (history.size > 6) history.removeAt(history.lastIndex)
                        command = ""
                    }
                },
                enabled = !busy && command.isNotBlank(),
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(4.dp)
            ) { Text("SEND") }
        }
        Spacer(Modifier.height(Spacing.s))
        if (history.isEmpty()) {
            Text(
                "Commands are written to the printer, not into a terminal session. The printer's " +
                    "own response log stays in OctoPrint.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            history.forEach { line ->
                Text(
                    text = "› $line",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = Spacing.xxs)
                )
            }
        }

        SectionHeading("05", "Connection")
        Plate(Modifier.fillMaxWidth()) {
            Column {
                SpecRow(null, "OctoPrint", snapshot?.octoPrintVersion ?: "unknown")
                SpecRow(
                    null,
                    "Printer",
                    when (snapshot?.state) {
                        MachineState.Disconnected -> "disconnected"
                        MachineState.Error -> "fault"
                        null -> "no reading"
                        else -> "connected"
                    }
                )
                Spacer(Modifier.height(Spacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Button(
                        onClick = onConnectPrinter,
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("CONNECT") }
                    OutlinedButton(
                        onClick = onDisconnectPrinter,
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("DISCONNECT") }
                }
            }
        }

        Spacer(Modifier.height(Spacing.l))
        TextButton(onClick = onOpenSettings, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Printer settings →", style = MaterialTheme.typography.labelLarge)
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
}

@Composable
private fun JogButton(label: String, modifier: Modifier, busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !busy,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
