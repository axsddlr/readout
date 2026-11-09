package dev.readout.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.readout.app.data.PrinterConfig
import dev.readout.app.ui.theme.Spacing

/**
 * Printer switcher. The masthead is the door: tap the machine's name, choose another instance.
 * Switching never destroys the others.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSwitcherSheet(
    instances: List<PrinterConfig>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenFleet: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl)) {
            Text(
                text = "Printers",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.s))
            instances.forEach { instance ->
                val active = instance.id == activeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !active) { onSelect(instance.id) }
                        .padding(vertical = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = instance.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = instance.displayHost,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (active) "Active" else "Use",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                RowDivider()
            }
            Spacer(Modifier.height(Spacing.s))
            TextButton(onClick = onOpenFleet) {
                Text("Fleet view →", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onAdd) {
                Text("Add another printer →", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
