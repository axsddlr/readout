package dev.readout.app.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales

/**
 * The glance: state, a progress ring around the percentage, time left and heaters, with the one
 * verb (Pause / Resume) in the edge button where a thumb lands. Scrolls for large fonts.
 */
@Composable
fun GlanceScreen(
    snapshot: WearSnapshot?,
    error: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onChangePrinter: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val active = snapshot?.printing == true

    val scrollIndicator: @Composable BoxScope.() -> Unit = {
        if (!LocalScrollCaptureInProgress.current) ScrollIndicator(listState)
    }
    val list: @Composable BoxScope.(PaddingValues) -> Unit = { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    StateChip(snapshot)
                }
            }

            if (snapshot != null) {
                item {
                    ProgressRing(
                        snapshot = snapshot,
                        modifier = Modifier.transformedHeight(this, transformationSpec)
                    )
                }
                temperatures(snapshot)?.let { line ->
                    item {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(top = 6.dp, bottom = 4.dp)
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "Linking…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            error?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = onChangePrinter,
                    label = {
                        Text(
                            "Change printer",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }
        }
    }

    if (active) {
        val paused = snapshot?.paused == true
        ScreenScaffold(
            scrollState = listState,
            scrollIndicator = scrollIndicator,
            edgeButton = {
                EdgeButton(
                    onClick = if (paused) onResume else onPause,
                    // Starting a scroll from the edge button should still scroll the list.
                    modifier = Modifier.scrollable(
                        listState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = rememberOverscrollEffect()
                    )
                ) {
                    if (paused) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("Resume")
                    } else {
                        Text("Pause")
                    }
                }
            },
            content = list
        )
    } else {
        // No job, no verb: the list fills the screen without an edge button.
        ScreenScaffold(scrollState = listState, scrollIndicator = scrollIndicator, content = list)
    }
}

/** A pill whose tone is the state; the state is always written too. */
@Composable
private fun StateChip(snapshot: WearSnapshot?) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        snapshot == null -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        snapshot.printing && snapshot.paused -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        snapshot.printing -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    Text(
        text = snapshot?.headline?.let(::sentenceCase) ?: "Readout",
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(container, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/** The percentage inside a ring that fills as the job does; time left beneath it. */
@Composable
private fun ProgressRing(snapshot: WearSnapshot, modifier: Modifier = Modifier) {
    val progress = snapshot.progress
    val percent = progress?.let { "${(it * 100).toInt()}%" } ?: "—"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(percent.takeIf { progress != null }?.let { "$it complete" } ?: "No progress reported")
                    eta(snapshot.etaSeconds).takeIf { it.isNotEmpty() }?.let { append(", $it") }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            if (snapshot.printing) {
                CircularProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = percent,
                    style = MaterialTheme.typography.numeralMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                eta(snapshot.etaSeconds).takeIf { it.isNotEmpty() && snapshot.printing }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Setup. Wear Material 3 has no text field, so the two inputs are foundation fields styled
 * from the theme; Connect sits in the edge button.
 */
@Composable
fun ConnectScreen(onConnect: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val ready = url.isNotBlank() && key.isNotBlank()

    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = {
            if (!LocalScrollCaptureInProgress.current) ScrollIndicator(listState)
        },
        edgeButton = {
            EdgeButton(
                onClick = { if (ready) onConnect(url.trim(), key.trim()) },
                enabled = ready,
                modifier = Modifier.scrollable(
                    listState,
                    orientation = Orientation.Vertical,
                    reverseDirection = true,
                    overscrollEffect = rememberOverscrollEffect()
                )
            ) {
                Text("Connect")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("Connect a printer")
                }
            }
            item {
                WatchField(
                    value = url,
                    onChange = { url = it },
                    placeholder = "Printer address",
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }
            item {
                WatchField(
                    value = key,
                    onChange = { key = it },
                    placeholder = "API key",
                    password = true,
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                )
            }
        }
    }
}

@Composable
private fun WatchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(scheme.primary),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri
        ),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(scheme.surfaceContainer, MaterialTheme.shapes.large)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                inner()
            }
        }
    )
}

internal fun sentenceCase(text: String): String =
    text.lowercase().replaceFirstChar { it.titlecase() }

internal fun eta(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m left"
        minutes > 0 -> "${minutes}m left"
        else -> "<1m left"
    }
}

internal fun temperatures(snapshot: WearSnapshot): String? {
    val nozzle = snapshot.nozzleActual?.let { "Nozzle ${it.toInt()}°/${snapshot.nozzleTarget ?: 0}°" }
    val bed = snapshot.bedActual?.let { "Bed ${it.toInt()}°/${snapshot.bedTarget ?: 0}°" }
    return listOfNotNull(nozzle, bed).joinToString("\n").ifEmpty { null }
}

// -- Previews ------------------------------------------------------------------------------------

private val PreviewPrinting = WearSnapshot(
    stateLabel = "Printing", printing = true, paused = false, progress = 0.41f, etaSeconds = 2410,
    nozzleActual = 209.6f, nozzleTarget = 210, bedActual = 59.8f, bedTarget = 60
)

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun GlancePrintingPreview() {
    ReadoutWearTheme {
        androidx.wear.compose.material3.AppScaffold {
            GlanceScreen(PreviewPrinting, error = null, onPause = {}, onResume = {}, onChangePrinter = {})
        }
    }
}

@WearPreviewDevices
@Composable
private fun GlancePausedPreview() {
    ReadoutWearTheme {
        androidx.wear.compose.material3.AppScaffold {
            GlanceScreen(PreviewPrinting.copy(paused = true), error = null, onPause = {}, onResume = {}, onChangePrinter = {})
        }
    }
}

@WearPreviewDevices
@Composable
private fun ConnectPreview() {
    ReadoutWearTheme {
        androidx.wear.compose.material3.AppScaffold { ConnectScreen { _, _ -> } }
    }
}
