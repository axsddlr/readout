package dev.readout.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.readout.app.data.FleetAction
import dev.readout.app.data.FleetFile
import dev.readout.app.data.FleetStatus
import dev.readout.app.ui.components.EmptyNote
import dev.readout.app.ui.components.Plate
import dev.readout.app.ui.components.RowDivider
import dev.readout.app.ui.theme.Spacing
import dev.readout.app.util.Format

/**
 * The fleet: every configured printer in one table. Commands apply to the selected printers,
 * which default to all of them. Starting a print is the one action that needs a file, so it
 * shows only files the selected printers actually hold.
 */
@Composable
fun FleetScreen(
    fleet: List<FleetStatus>,
    onRefresh: () -> Unit,
    onAction: (FleetAction, List<String>) -> Unit,
    loadFiles: suspend (List<String>) -> List<FleetFile>,
    onStartFile: (List<String>, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onRefresh() }

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmCancel by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }

    // Keep the selection honest as printers come and go: new printers start selected.
    LaunchedEffect(fleet) {
        val ids = fleet.map { it.id }.toSet()
        selected = if (selected.isEmpty()) ids else selected.intersect(ids)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Spacing.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(Spacing.xs))
            Text("Fleet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) {
                Text("Refresh")
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selected.size} of ${fleet.size} selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { selected = fleet.map { it.id }.toSet() }) {
                Text("All")
            }
            TextButton(onClick = { selected = emptySet() }) {
                Text("None")
            }
        }

        when {
            fleet.isEmpty() -> Column(Modifier.padding(horizontal = Spacing.l)) {
                EmptyNote("No printers configured yet.")
            }

            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.l, end = Spacing.l, bottom = Spacing.m
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.weight(1f)
            ) {
                items(fleet, key = { it.id }) { machine ->
                    val checked = machine.id in selected
                    Plate(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - machine.id else selected + machine.id
                                }
                                .heightIn(min = 64.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selected = if (checked) selected - machine.id else selected + machine.id
                                }
                            )
                            Spacer(Modifier.width(Spacing.m))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = machine.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(Spacing.xxs))
                                Text(
                                    text = buildString {
                                        append(machine.stateLabel)
                                        machine.progress?.let {
                                            append(" · ")
                                            append(Format.percent(it))
                                        }
                                        machine.jobName?.let { job ->
                                            append(" · ")
                                            append(job)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (machine.online) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilledTonalButton(
                        onClick = { onAction(FleetAction.Pause, selected.toList()) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = selected.isNotEmpty()
                    ) { Text("Pause") }
                    FilledTonalButton(
                        onClick = { onAction(FleetAction.Resume, selected.toList()) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = selected.isNotEmpty()
                    ) { Text("Resume") }
                }
                Spacer(Modifier.height(Spacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedButton(
                        onClick = { showFiles = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = selected.isNotEmpty()
                    ) { Text("Start file…") }
                    OutlinedButton(
                        onClick = { confirmCancel = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = selected.isNotEmpty()
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel on ${selected.size} printer${if (selected.size == 1) "" else "s"}?") },
            text = {
                Text(
                    "The selected printers will stop their current job. This cannot be undone " +
                        "from here; the print has to be started again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    onAction(FleetAction.Cancel, selected.toList())
                }) { Text("Cancel all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep printing") }
            }
        )
    }

    if (showFiles) {
        FleetFilePicker(
            printerIds = selected.toList(),
            loadFiles = loadFiles,
            onPick = { fileName ->
                showFiles = false
                onStartFile(selected.toList(), fileName)
            },
            onDismiss = { showFiles = false }
        )
    }
}

@Composable
private fun FleetFilePicker(
    printerIds: List<String>,
    loadFiles: suspend (List<String>) -> List<FleetFile>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var files by remember { mutableStateOf<List<FleetFile>?>(null) }

    LaunchedEffect(printerIds) {
        files = loadFiles(printerIds)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a file") },
        text = {
            val current = files
            when {
                current == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(Spacing.m))
                    Text("Reading the selected printers…")
                }

                current.isEmpty() -> Text("None of the selected printers has a file.")

                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(current, key = { it.name }) { file ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(file.name) }
                                .padding(vertical = Spacing.s)
                        ) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "on ${file.printerIds.size} of ${printerIds.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RowDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
