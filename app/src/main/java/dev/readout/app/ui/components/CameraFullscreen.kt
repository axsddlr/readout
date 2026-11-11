package dev.readout.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readout.app.data.CameraSettings
import dev.readout.app.ui.theme.Spacing
import kotlinx.coroutines.flow.StateFlow

/**
 * The camera filling the screen, the way OctoPrint's own viewer does: black surround, the image
 * fitted rather than cropped, and the system bars out of the way until a swipe brings them back.
 *
 * The card behind this keeps its stream open, so entering and leaving fullscreen is not a
 * reconnect.
 */
@Composable
fun CameraFullscreen(
    frameFlow: StateFlow<ImageBitmap?>,
    live: Boolean,
    stateLabel: String?,
    cameraName: String,
    settings: CameraSettings,
    onClose: () -> Unit
) {
    val frame by frameFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(settings.ignoreRotationLock) {
        val window = activity?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val previous = activity?.requestedOrientation
        // Only override when asked: otherwise the phone's rotation lock keeps working, which is
        // what someone watching from a clamped mount expects.
        if (settings.ignoreRotationLock) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
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

            if (stateLabel != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(Spacing.m),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White
                ) {
                    Text(
                        stateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)
                    )
                }
            }
        }
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
