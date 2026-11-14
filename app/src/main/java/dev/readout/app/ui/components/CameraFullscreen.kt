package dev.readout.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readout.app.data.CameraSettings
import dev.readout.app.data.PrinterSnapshot
import dev.readout.app.ui.theme.Spacing
import dev.readout.app.util.Format
import kotlinx.coroutines.flow.StateFlow

/**
 * The camera filling the screen, the way OctoPrint's own viewer does: black surround, the image
 * fitted rather than cropped, and the system bars out of the way until a swipe brings them back.
 *
 * The job rides along at the bottom — progress, elapsed, remaining and the finishing time — because
 * watching the print is the reason to fill the screen with it. The card behind this keeps its
 * stream open, so entering and leaving fullscreen is not a reconnect.
 */
@Composable
fun CameraFullscreen(
    frameFlow: StateFlow<ImageBitmap?>,
    live: Boolean,
    snapshot: PrinterSnapshot?,
    cameraName: String,
    settings: CameraSettings,
    onFullscreen: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val frame by frameFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // The decoder asks for the source resolution only while this is open; the card behind it keeps
    // its half-size frames.
    DisposableEffect(Unit) {
        onFullscreen(true)
        onDispose { onFullscreen(false) }
    }

    DisposableEffect(settings.ignoreRotationLock) {
        val previous = activity?.requestedOrientation
        // Only override when asked: otherwise the phone's rotation lock keeps working, which is
        // what someone watching from a clamped mount expects.
        if (settings.ignoreRotationLock) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val etaClock = Format
        .etaEpochMillis(System.currentTimeMillis(), snapshot?.printTimeLeftSeconds)
        ?.let { Format.clockTime(context, it) }
        ?: "—"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HideDialogSystemBars()
            if (frame != null) {
                Image(
                    bitmap = frame!!,
                    contentDescription = "Fullscreen camera view of the printer",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Waiting for the camera…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close fullscreen camera")
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White
                ) {
                    Row(
                        Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (settings.showName) {
                            Text(cameraName, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.padding(horizontal = Spacing.xs))
                        }
                        if (settings.showResolution && frame != null) {
                            Text(
                                "${frame!!.width}×${frame!!.height}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.padding(horizontal = Spacing.xs))
                        }
                        Text(
                            text = if (live) "Live" else "Stills",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (snapshot != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(Spacing.m)
                        .clip(MaterialTheme.shapes.large)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = Spacing.l, vertical = Spacing.m)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = snapshot.jobName ?: snapshot.stateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = snapshot.stateLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        Text(
                            text = Format.percent(snapshot.progress),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                    }
                    snapshot.progress?.let { progress ->
                        Spacer(Modifier.height(Spacing.s))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                    Spacer(Modifier.height(Spacing.s))
                    Row(Modifier.fillMaxWidth()) {
                        OverlayMetric("Elapsed", Format.duration(snapshot.printTimeSeconds), Modifier.weight(1f))
                        OverlayMetric("Left", Format.eta(snapshot.printTimeLeftSeconds), Modifier.weight(1f))
                        OverlayMetric("ETA", etaClock, Modifier.weight(1f))
                    }
                    if (snapshot.layer != null && snapshot.layerCount != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = buildString {
                                if (snapshot.layerEstimated) append("≈ ")
                                append("Layer ${snapshot.layer} of ${snapshot.layerCount}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    Format.etaOrigin(snapshot.etaOrigin)?.let { origin ->
                        Text(
                            text = origin,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hides the system bars on the dialog's own window: the dialog is a separate window, so hiding
 * them on the activity window has no effect here.
 */
@Composable
private fun HideDialogSystemBars() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun OverlayMetric(label: String, value: String, modifier: Modifier = Modifier) {    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
