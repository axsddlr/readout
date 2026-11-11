package dev.readout.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.readout.app.data.PreviewQuality
import dev.readout.app.data.PreviewSettings
import dev.readout.app.ui.theme.Spacing

/**
 * The preview's three controls: the layer below, the layer itself, and how much of the whole part is
 * drawn behind them. Shared by the file sheet and the live viewer so the two cannot drift apart.
 */
@Composable
fun PreviewSettingsPanel(settings: PreviewSettings, onChange: (PreviewSettings) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainer)
            .padding(horizontal = Spacing.l)
    ) {
        PreviewToggle(
            title = "Show previous layer",
            checked = settings.previousLayer,
            onChange = { onChange(settings.copy(previousLayer = it)) }
        )
        PreviewToggle(
            title = "Show current layer",
            checked = settings.currentLayer,
            onChange = { onChange(settings.copy(currentLayer = it)) }
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Drawing quality",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.m))
            PreviewQuality.entries.forEach { quality ->
                FilterChip(
                    selected = settings.quality == quality,
                    onClick = { onChange(settings.copy(quality = quality)) },
                    label = { Text(quality.name) },
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
        }
        Text(
            text = settings.qualityDescription,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.m)
        )
    }
}

@Composable
private fun PreviewToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
