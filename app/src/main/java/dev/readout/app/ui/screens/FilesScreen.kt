package dev.readout.app.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.readout.app.data.CommandResult
import dev.readout.app.data.FileEntry
import dev.readout.app.data.GcodePreview
import dev.readout.app.ui.ReadoutIcons
import dev.readout.app.ui.components.EmptyNote
import dev.readout.app.ui.components.Plate
import dev.readout.app.ui.components.RowDivider
import dev.readout.app.ui.components.SectionHeading
import dev.readout.app.ui.components.SpecRow
import dev.readout.app.ui.theme.Spacing
import dev.readout.app.util.Format
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The file library. A printed index, not a storefront: name, weight, date, and the actions that
 * matter. Long-press starts a batch; selection never hides the list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    live: Boolean,
    loadFiles: suspend () -> List<FileEntry>,
    deleteFiles: suspend (List<FileEntry>) -> Int,
    upload: suspend (
        String, String, Long?, () -> java.io.InputStream, (Float) -> Unit
    ) -> CommandResult,
    onStartPrint: (FileEntry) -> Unit,
    onDelete: (FileEntry) -> Unit,
    onOpenTimelapses: () -> Unit,
    loadPreview: suspend (FileEntry) -> GcodePreview,
    modifier: Modifier = Modifier
) {
    var files by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FileEntry?>(null) }
    var reload by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var uploadState by remember { mutableStateOf<UploadProgress?>(null) }
    val selection = remember { mutableStateMapOf<String, FileEntry>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "upload.gcode"
        var size: Long? = null
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        scope.launch {
            uploadState = UploadProgress(name, 0f)
            val result = upload(
                name,
                mime,
                size,
                { resolver.openInputStream(uri) ?: error("That file could not be opened") }
            ) { progress -> uploadState = uploadState?.copy(progress = progress) }
            uploadState = null
            status = when (result) {
                is CommandResult.Ok -> "Uploaded $name."
                is CommandResult.Failed -> result.message
            }
            reload++
        }
    }

    fun keyOf(file: FileEntry) = file.origin + "/" + file.path

    fun toggle(file: FileEntry) {
        val key = keyOf(file)
        if (selection.containsKey(key)) selection.remove(key) else selection[key] = file
    }

    LaunchedEffect(reload, live) {
        if (!live) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching { loadFiles() }
            .onSuccess { files = it; loading = false }
            .onFailure {
                android.util.Log.e("Readout", "file list failed", it)
                error = it.message ?: "Could not read the file list"
                loading = false
            }
    }

    val filtered = remember(files, query) {
        if (query.isBlank()) files
        else files.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selection.isEmpty()) {
            Column(Modifier.padding(horizontal = Spacing.l)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Filter files") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
                SectionHeading("Library") {
                    TextButton(onClick = { reload++ }) {
                        Text("Refresh")
                    }
                }
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.padding(bottom = Spacing.s)
                ) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        enabled = live && uploadState == null
                    ) {
                        Text("Upload")
                    }
                    androidx.compose.material3.OutlinedButton(onClick = onOpenTimelapses) {
                        Text("Timelapses")
                    }
                }
            }
        } else {
            SelectionBar(
                count = selection.size,
                allSelected = filtered.isNotEmpty() && filtered.all { selection.containsKey(keyOf(it)) },
                onSelectAll = {
                    if (filtered.all { selection.containsKey(keyOf(it)) }) {
                        selection.clear()
                    } else {
                        filtered.forEach { selection[keyOf(it)] = it }
                    }
                },
                onDelete = { confirmBatchDelete = true },
                onClear = { selection.clear() }
            )
        }

        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs)
            )
        }

        uploadState?.let { up ->
            Plate(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs)) {
                Column {
                    Text(
                        text = "Uploading · ${up.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    LinearProgressIndicator(
                        progress = { up.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = "${(up.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when {
            loading -> Row(
                Modifier.fillMaxWidth().padding(Spacing.l),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(Spacing.m))
                Text(
                    "Reading the library…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            error != null -> Column(Modifier.padding(horizontal = Spacing.l)) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { reload++ }) { Text("Try again") }
            }

            filtered.isEmpty() -> Column(Modifier.padding(horizontal = Spacing.l)) {
                EmptyNote(
                    if (query.isBlank()) "This printer has no files yet. Upload one in OctoPrint and it appears here."
                    else "Nothing matches “$query”."
                )
            }

            else -> Plate(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l)
            ) {
                LazyColumn {
                    itemsIndexed(filtered, key = { _, f -> keyOf(f) }, contentType = { _, _ -> "file" }) { index, file ->
                        val checked = selection.containsKey(keyOf(file))
                        if (index > 0) RowDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selection.isEmpty()) selected = file else toggle(file)
                                    },
                                    onLongClick = { toggle(file) }
                                )
                                .heightIn(min = 56.dp)
                                .padding(vertical = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) ReadoutIcons.Folder else ReadoutIcons.File,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(Spacing.l))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        if (file.isDirectory) append("Folder")
                                        else append(Format.bytes(file.sizeBytes))
                                        if (file.dateSeconds != null) {
                                            append(" · ")
                                            append(Format.fileDate(file.dateSeconds))
                                        }
                                        if (file.origin != "local") {
                                            append(" · ")
                                            append(file.origin)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(Spacing.s))
                            if (selection.isNotEmpty()) {
                                Checkbox(checked = checked, onCheckedChange = { toggle(file) })
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { file ->
        FileSheet(
            file = file,
            loadPreview = loadPreview,
            onDismiss = { selected = null },
            onPrint = {
                selected = null
                onStartPrint(file)
            },
            onDelete = {
                selected = null
                onDelete(file)
                reload++
            }
        )
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("Delete ${selection.size} files?") },
            text = { Text("They are removed from the printer's storage. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val targets = selection.values.toList()
                    confirmBatchDelete = false
                    scope.launch {
                        status = "Deleting ${targets.size}…"
                        val deleted = deleteFiles(targets)
                        selection.clear()
                        status = if (deleted == targets.size) {
                            "Deleted $deleted files."
                        } else {
                            "Deleted $deleted of ${targets.size}; the rest failed."
                        }
                        reload++
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.l, end = Spacing.xs)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSelectAll) {
                Text(if (allSelected) "None" else "All")
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onClear) {
                Text("Close")
            }
        }
    }
}

private data class UploadProgress(val name: String, val progress: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSheet(
    file: FileEntry,
    loadPreview: suspend (FileEntry) -> GcodePreview,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit
) {
    // Open fully expanded: the sheet exists to reach "Start print" in one tap, and the default
    // half-expanded state hides it behind a drag.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var preview by remember(file) { mutableStateOf<GcodePreview?>(null) }
    var previewError by remember(file) { mutableStateOf<String?>(null) }
    var layerIndex by remember(file) { mutableIntStateOf(0) }

    LaunchedEffect(file) {
        runCatching { loadPreview(file) }
            .onSuccess {
                preview = it
                layerIndex = (it.layers.size - 1).coerceAtLeast(0)
            }
            .onFailure { previewError = it.message ?: "Could not read the toolpaths" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xxl)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.l))
            ToolpathPreview(
                preview = preview,
                error = previewError,
                layerIndex = layerIndex,
                onLayer = { layerIndex = it }
            )
            Spacer(Modifier.height(Spacing.l))
            SpecRow(null, "Size", Format.bytes(file.sizeBytes))
            SpecRow(null, "Added", Format.fileDate(file.dateSeconds).ifBlank { "—" })
            SpecRow(null, "Location", file.origin)
            SpecRow(null, "Path", file.path)
            Spacer(Modifier.height(Spacing.l))
            Button(
                onClick = onPrint,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Start print") }
            Spacer(Modifier.height(Spacing.s))
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Delete file", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * A top-down drawing of the toolpaths, one layer at a time. It exists so you can recognise a file
 * before printing it; it is not a 3D preview and makes no claim to be.
 */
@Composable
private fun ToolpathPreview(
    preview: GcodePreview?,
    error: String?,
    layerIndex: Int,
    onLayer: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    when {
        error != null -> Text(
            text = "No preview: $error",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.error
        )

        preview == null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.m))
            Text(
                text = "Reading toolpaths…",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }

        preview.isEmpty -> Text(
            text = "This file has no toolpaths to draw.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )

        else -> {
            // Paths are built once in model coordinates; the canvas only scales and flips them,
            // so dragging the layer slider never re-walks thousands of segments.
            val layerPaths = remember(preview) {
                preview.layers.map { layer ->
                    Path().apply {
                        layer.lines.forEach { line ->
                            if (line.extruding) {
                                moveTo(line.x1, line.y1)
                                lineTo(line.x2, line.y2)
                            }
                        }
                    }
                }
            }
            val wholePart = remember(layerPaths) {
                Path().apply { layerPaths.forEach { addPath(it) } }
            }
            val layer = layerPaths.getOrNull(layerIndex)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(scheme.surfaceContainerHighest)
            ) {
                if (layer != null) {
                    val pad = 12f
                    val scale = min(
                        (size.width - pad * 2) / preview.width,
                        (size.height - pad * 2) / preview.height
                    )
                    val ox = (size.width - preview.width * scale) / 2f - preview.minX * scale
                    val oy = (size.height + preview.height * scale) / 2f + preview.minY * scale
                    withTransform({
                        translate(ox, oy)
                        scale(scale, -scale, pivot = Offset.Zero)
                    }) {
                        // The rest of the part in a whisper of colour, so a single layer reads as
                        // part of something rather than a stray shape floating in a box.
                        drawPath(
                            wholePart,
                            scheme.primary.copy(alpha = 0.16f),
                            style = Stroke(width = 1.2f / scale)
                        )
                        drawPath(
                            layer,
                            scheme.primary,
                            style = Stroke(width = 1.8f / scale, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (preview.layers.size > 1) {
                        "Layer ${layerIndex + 1} of ${preview.layers.size}"
                    } else {
                        "Layer 1"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (preview.truncated) {
                    Text(
                        text = "preview limited to the start of the file",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            if (preview.layers.size > 1) {
                Slider(
                    value = layerIndex.toFloat(),
                    onValueChange = { onLayer(it.roundToInt().coerceIn(0, preview.layers.size - 1)) },
                    valueRange = 0f..(preview.layers.size - 1).toFloat()
                )
            }
        }
    }
}
