package dev.readout.app.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.readout.app.data.TimelapseEntry
import dev.readout.app.ui.ReadoutIcons
import dev.readout.app.ui.components.EmptyNote
import dev.readout.app.ui.components.Plate
import dev.readout.app.ui.components.RowDivider
import dev.readout.app.ui.theme.Spacing
import dev.readout.app.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The timelapse archive: a printed index of finished films, played in place. Not a gallery —
 * no thumbnails to fetch, no scroll of posters.
 */
@Composable
fun TimelapseScreen(
    live: Boolean,
    load: suspend () -> List<TimelapseEntry>,
    streamUrl: (TimelapseEntry) -> String,
    apiKey: String,
    delete: suspend (TimelapseEntry) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var entries by remember { mutableStateOf<List<TimelapseEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf<TimelapseEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<TimelapseEntry?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload, live) {
        if (!live) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching { load() }
            .onSuccess { entries = it; loading = false }
            .onFailure { error = it.message ?: "Could not read the timelapse archive"; loading = false }
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
            Text("Timelapses", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { reload++ }) {
                Text("Refresh")
            }
        }

        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l)
            )
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
                    "Reading the archive…",
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

            entries.isEmpty() -> Column(Modifier.padding(horizontal = Spacing.l)) {
                EmptyNote(
                    "No rendered timelapses yet. OctoPrint renders them when a print finishes; " +
                        "the archive appears here."
                )
            }

            else -> Plate(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l)
            ) {
                LazyColumn {
                    itemsIndexed(entries, key = { _, e -> e.name }) { index, entry ->
                        if (index > 0) RowDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { playing = entry }
                                .heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(Spacing.l))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append(Format.bytes(entry.sizeBytes))
                                        if (entry.dateSeconds != null) {
                                            append(" · ")
                                            append(Format.fileDate(entry.dateSeconds))
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { confirmDelete = entry }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete ${entry.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this timelapse?") },
            text = { Text(entry.name) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        val deleted = delete(entry)
                        status = if (deleted) "Deleted ${entry.name}." else "Could not delete ${entry.name}."
                        reload++
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Keep") }
            }
        )
    }

    playing?.let { entry ->
        TimelapsePlayer(
            entry = entry,
            url = streamUrl(entry),
            apiKey = apiKey,
            onDismiss = { playing = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelapsePlayer(
    entry: TimelapseEntry,
    url: String,
    apiKey: String,
    onDismiss: () -> Unit
) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.xxl)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.m))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setOnPreparedListener { mediaPlayer ->
                                duration = mediaPlayer.duration.toLong()
                                start()
                                playing = true
                            }
                            setOnCompletionListener { playing = false }
                            setOnErrorListener { _, _, _ ->
                                failed = true
                                true
                            }
                            setVideoURI(Uri.parse(url), mapOf("X-Api-Key" to apiKey))
                            view = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (failed) {
                    Text(
                        text = "This video could not be played.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val player = view ?: return@IconButton
                    if (player.isPlaying) {
                        player.pause()
                        playing = false
                    } else {
                        if (player.currentPosition >= player.duration - 250) player.seekTo(0)
                        player.start()
                        playing = true
                    }
                }) {
                    Icon(
                        imageVector = if (playing) ReadoutIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Slider(
                    value = position.toFloat(),
                    onValueChange = { position = it.toLong() },
                    onValueChangeFinished = { view?.seekTo(position.toInt()) },
                    valueRange = 0f..(duration.coerceAtLeast(1L)).toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${clock(position)} / ${clock(duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            view?.let { player ->
                if (player.duration > 0) duration = player.duration.toLong()
                position = player.currentPosition.toLong()
                playing = player.isPlaying
            }
            delay(400)
        }
    }

    DisposableEffect(Unit) {
        onDispose { view?.stopPlayback() }
    }
}

private fun clock(milliseconds: Long): String {
    val total = (milliseconds / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
