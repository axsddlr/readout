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

private val Paper = Color(0xFFF7F2E7)
private val Graphite = Color(0xFF131110)
private val Plate = Color(0xFFFFD43B)
private val Muted = Color(0xFF9A9184)
private val OnPlate = Color(0xFF131110)
private val Rust = Color(0xFFFF9B6B)
private val Rail = Color(0xFF3A342C)
private val Mono = FontFamily.Monospace

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
        modifier = Modifier.fillMaxSize().background(Graphite).padding(horizontal = 16.dp),
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
                MonoLabel("READOUT")

                val current = snapshot
                if (current == null) {
                    MonoLabel("LINKING…")
                } else {
                    StatePlate(current)
                    BasicText(
                        text = current.progress?.let { "${(it * 100).toInt()}%" } ?: "—",
                        style = TextStyle(
                            color = Paper,
                            fontSize = 32.sp,
                            fontFamily = Mono,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (current.printing) MonoLabel(eta(current.etaSeconds))
                    MonoLabel(temperatures(current), size = 10)
                }

                error?.let {
                    BasicText(
                        text = it.uppercase(),
                        style = TextStyle(
                            color = Rust,
                            fontSize = 9.sp,
                            fontFamily = Mono,
                            textAlign = TextAlign.Center
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (snapshot?.paused == true) {
                        Action("RESUME") {
                            scope.launch { runCatching { withContext(Dispatchers.IO) { api?.resume() } } }
                        }
                    } else {
                        Action("PAUSE") {
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
                    MonoLabel("CHANGE", size = 9)
                }
            }
        }
    }
}

@Composable
private fun MonoLabel(text: String, size: Int = 9) {
    BasicText(
        text = text,
        style = TextStyle(
            color = Muted,
            fontSize = size.sp,
            fontFamily = Mono,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
    )
}

@Composable
private fun StatePlate(snapshot: WearSnapshot) {
    val working = snapshot.printing && !snapshot.paused
    val background = when {
        working -> Plate
        snapshot.paused -> Color(0xFFB0411B)
        else -> Color(0xFF2A2621)
    }
    val ink = if (working) OnPlate else Paper
    Box(
        Modifier.clip(RoundedCornerShape(3.dp)).background(background)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        BasicText(
            text = snapshot.headline,
            style = TextStyle(
                color = ink,
                fontSize = 10.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        )
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(3.dp)).background(Plate)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = OnPlate,
                fontSize = 11.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
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
        MonoLabel("READOUT")
        MonoLabel("PRINTER ADDRESS")
        WatchField(url, { url = it }, "192.168.1.50")
        WatchField(key, { key = it }, "API key", password = true)
        Action("CONNECT") {
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
            color = Paper,
            fontSize = 13.sp,
            fontFamily = Mono,
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
                    .border(1.dp, Rail, RoundedCornerShape(3.dp))
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    BasicText(
                        text = placeholder,
                        style = TextStyle(color = Muted, fontSize = 11.sp, fontFamily = Mono)
                    )
                }
                inner()
            }
        }
    )
}

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
