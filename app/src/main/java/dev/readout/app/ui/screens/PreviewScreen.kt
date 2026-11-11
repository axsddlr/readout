package dev.readout.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.readout.app.data.GcodePreview
import dev.readout.app.data.PreviewSettings
import dev.readout.app.data.UiState
import dev.readout.app.data.followedLayerIndex
import dev.readout.app.ui.components.PreviewSettingsPanel
import dev.readout.app.ui.components.SectionHeading
import dev.readout.app.ui.components.SpecRow
import dev.readout.app.ui.components.ToolpathCanvas
import dev.readout.app.ui.theme.Spacing
import dev.readout.app.util.Format
import kotlin.math.roundToInt

/**
 * The running print's toolpaths, following the machine layer by layer.
 *
 * OctoPrint reports no layer on its own, so the layer comes from the snapshot: a plugin's exact
 * count when one is installed, otherwise the estimate from this file's own layer durations. The
 * canvas is the same renderer the file sheet uses, so what you checked before printing is what you
 * watch afterwards.
 */
@Composable
fun PreviewScreen(
    state: UiState,
    load: suspend () -> GcodePreview,
    settings: PreviewSettings,
    onPreviewSettings: (PreviewSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var preview by remember { mutableStateOf<GcodePreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var follow by rememberSaveable { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var layerIndex by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme
    val snapshot = state.snapshot
    val reported = snapshot?.layer

    LaunchedEffect(reload) {
        preview = null
        error = null
        runCatching { load() }
            .onSuccess {
                preview = it
                layerIndex = (it.layers.size - 1).coerceAtLeast(0)
            }
            .onFailure { error = it.message ?: "Could not read the toolpaths" }
    }

    // Following is the default; dragging the slider is what stops it, and the switch is how the
    // viewer says so instead of fighting the finger.
    LaunchedEffect(preview, reported, follow) {
        val total = preview?.layers?.size ?: return@LaunchedEffect
        if (follow) {
            followedLayerIndex(reported, total)?.let { layerIndex = it }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l)
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(Spacing.xs))
            Text("G-code preview", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { reload++ }) { Text("Reload") }
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(
                    imageVector = if (showSettings) Icons.Filled.Close else Icons.Filled.Settings,
                    contentDescription = if (showSettings) "Hide preview settings" else "Preview settings",
                    tint = if (showSettings) scheme.onSurface else scheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = snapshot?.jobName ?: "No job loaded",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 2
        )
        Spacer(Modifier.height(Spacing.m))

        val current = preview
        when {
            error != null -> Text(
                text = "No preview: $error",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error
            )

            current == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.m))
                Text(
                    "Reading the running file…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            current.isEmpty -> Text(
                "This file has no toolpaths to draw.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )

            else -> {
                ToolpathCanvas(
                    preview = current,
                    layerIndex = layerIndex,
                    settings = settings,
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
                Spacer(Modifier.height(Spacing.s))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            append("Layer ${layerIndex + 1} of ${current.layers.size}")
                            if (reported != null && snapshot.layerEstimated) append(" · estimated")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    if (current.truncated) {
                        Text(
                            "partial file",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                if (showSettings) {
                    Spacer(Modifier.height(Spacing.s))
                    PreviewSettingsPanel(settings = settings, onChange = onPreviewSettings)
                }
                if (current.layers.size > 1) {
                    Slider(
                        value = layerIndex.toFloat(),
                        onValueChange = {
                            follow = false
                            layerIndex = it.roundToInt().coerceIn(0, current.layers.size - 1)
                        },
                        valueRange = 0f..(current.layers.size - 1).toFloat()
                    )
                }

                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Follow the print", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = when {
                                follow && reported == null -> "Waiting for a layer reading from the printer"
                                follow -> if (snapshot?.layerEstimated == true) {
                                    "Tracking an estimate from this file's own layer times"
                                } else {
                                    "Tracking the printer's layer count"
                                }
                                else -> "Manual: drag the slider, or switch following back on"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = follow,
                        onCheckedChange = { on ->
                            follow = on
                            if (on) followedLayerIndex(reported, current.layers.size)?.let { layerIndex = it }
                        }
                    )
                }

                Spacer(Modifier.height(Spacing.s))
                SectionHeading("This layer")
                current.layers.getOrNull(layerIndex)?.let { layer ->
                    SpecRow(null, "At height", Format.millimetres(layer.z, 2))
                    SpecRow(null, "Commands", Format.count(layer.commands))
                    if (layer.filamentMm > 0f) {
                        SpecRow(null, "Filament", Format.millimetres(layer.filamentMm, 2))
                    }
                    if (layer.seconds > 0f) {
                        SpecRow(null, "Layer time", Format.duration(layer.seconds.toLong()))
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}
