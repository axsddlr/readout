package dev.readout.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.readout.app.data.ComponentTemperature
import dev.readout.app.ui.theme.ReadoutHeroStyle
import dev.readout.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Target temperature, set the way an operator sets it: a big number, a coarse slider,
 * and the five temperatures this material actually uses.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemperatureSheet(
    component: ComponentTemperature,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    val presets = when (component.key) {
        "bed" -> listOf(0, 55, 60, 70, 80, 100)
        "chamber" -> listOf(0, 30, 40, 50, 60)
        else -> listOf(0, 180, 200, 215, 230, 260)
    }
    val initial = if (component.target > 0f) component.target else presets[2].toFloat()
    var value by remember(component.key) { mutableFloatStateOf(initial) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl)) {
            Text(
                text = component.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${value.roundToInt()}°",
                    style = ReadoutHeroStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(Spacing.m))
                Text(
                    text = "now ${component.actual.toInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.m)
                )
            }
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = 0f..300f,
                steps = 59
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = value.roundToInt() == preset,
                        onClick = { value = preset.toFloat() },
                        label = { Text(if (preset == 0) "Off" else "$preset°") }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Button(
                    onClick = { onApply(value.roundToInt()) },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text("Set target") }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(50.dp)
                ) { Text("Close") }
            }
        }
    }
}
