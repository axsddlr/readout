package dev.readout.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchApp() }
    }
}

// Dark Material You scheme (fallback seed #00658F).
private val OnSurface = Color(0xFFDFE3E7)
private val Background = Color(0xFF0A0E10)
private val PrimaryContainer = Color(0xFF004C6C)
private val OnPrimaryContainer = Color(0xFFC7E7FF)
private val Muted = Color(0xFFC1C7CE)
private val ErrorText = Color(0xFFFFB4AB)
private val Outline = Color(0xFF41484D)
private val IdleContainer = Color(0xFF262A2E)
private val PausedContainer = Color(0xFF4C4268)
private val OnPausedContainer = Color(0xFFE9DDFF)
private val Sans = FontFamily.Default

/**
 * The watch is a glance and two verbs. It reads the same printer the phone does, keeps its own
 * copy of the address, and stays useful when the phone is in another room.
 */
@Composable
private fun WatchApp() {
    val context = LocalContext.current
    val store = remember { WearStore(context) }
    var config by remember { mutableStateOf(store.load()) }
    var snapshot by remember { mutableStateOf<WearSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val api = remember(config) {
        config?.let { (url, key) -> WearApi(url, key) }
    }

    LaunchedEffect(api) {
        val client = api ?: return@LaunchedEffect
        while (true) {
            runCatching { withContext(Dispatchers.IO) { client.snapshot() } }
                .onSuccess { snapshot = it; error = null }
                .onFailure { error = it.message ?: "Could not reach the printer" }
            delay(3_000)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (config == null) {
            ConnectForm { url, key ->
                store.save(url, key)
                config = url to key
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Label("Readout")

                val current = snapshot
                if (current == null) {
                    Label("Linking…")
                } else {
                    StatePlate(current)
                    BasicText(
                        text = current.progress?.let { "${(it * 100).toInt()}%" } ?: "—",
                        style = TextStyle(
                            color = OnSurface,
                            fontSize = 32.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (current.printing) Label(eta(current.etaSeconds))
                    Label(temperatures(current), size = 12)
                }

                error?.let {
                    BasicText(
                        text = it,
                        style = TextStyle(
                            color = ErrorText,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            textAlign = TextAlign.Center
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (snapshot?.paused == true) {
                        Action("Resume") {
                            scope.launch { runCatching { withContext(Dispatchers.IO) { api?.resume() } } }
                        }
                    } else {
                        Action("Pause") {
                            scope.launch { runCatching { withContext(Dispatchers.IO) { api?.pause() } } }
                        }
                    }
                }

                Box(
                    Modifier.padding(top = 2.dp).clickable {
                        store.clear()
                        config = null
                        snapshot = null
                        error = null
                    }
                ) {
                    Label("Change", size = 11)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, size: Int = 11) {
    BasicText(
        text = text,
        style = TextStyle(
            color = Muted,
            fontSize = size.sp,
            fontFamily = Sans,
            textAlign = TextAlign.Center
        )
    )
}

@Composable
private fun StatePlate(snapshot: WearSnapshot) {
    val working = snapshot.printing && !snapshot.paused
    val background = when {
        working -> PrimaryContainer
        snapshot.paused -> PausedContainer
        else -> IdleContainer
    }
    val ink = when {
        working -> OnPrimaryContainer
        snapshot.paused -> OnPausedContainer
        else -> OnSurface
    }
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(background)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        BasicText(
            text = sentenceCase(snapshot.headline),
            style = TextStyle(
                color = ink,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(PrimaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = OnPrimaryContainer,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun ConnectForm(onConnect: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Label("Readout")
        Label("Printer address")
        WatchField(url, { url = it }, "192.168.1.50")
        WatchField(key, { key = it }, "API key", password = true)
        Action("Connect") {
            if (url.isNotBlank() && key.isNotBlank()) onConnect(url.trim(), key.trim())
        }
    }
}

@Composable
private fun WatchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(
            color = OnSurface,
            fontSize = 13.sp,
            fontFamily = Sans,
            textAlign = TextAlign.Center
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri
        ),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth()
                    .border(1.dp, Outline, RoundedCornerShape(24.dp))
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    BasicText(
                        text = placeholder,
                        style = TextStyle(color = Muted, fontSize = 11.sp, fontFamily = Sans)
                    )
                }
                inner()
            }
        }
    )
}

private fun sentenceCase(text: String): String =
    text.lowercase().replaceFirstChar { it.titlecase() }

private fun eta(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m left"
        minutes > 0 -> "${minutes}m left"
        else -> "<1m left"
    }
}

private fun temperatures(snapshot: WearSnapshot): String {
    val nozzle = snapshot.nozzleActual?.let { actual ->
        "N ${actual.toInt()}°/${snapshot.nozzleTarget ?: 0}°"
    }
    val bed = snapshot.bedActual?.let { actual ->
        "B ${actual.toInt()}°/${snapshot.bedTarget ?: 0}°"
    }
    return listOfNotNull(nozzle, bed).joinToString("   ")
}
