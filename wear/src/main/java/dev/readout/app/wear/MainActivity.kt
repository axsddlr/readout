package dev.readout.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.AppScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReadoutWearTheme { WatchApp() }
        }
    }
}

/**
 * The watch is a glance and two verbs. It reads the same printer the phone does, keeps its own
 * copy of the address, and stays useful when the phone is in another room.
 *
 * One [AppScaffold] hosts both screens so the time text stays put when switching between them.
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

    AppScaffold {
        if (config == null) {
            ConnectScreen { url, key ->
                store.save(url, key)
                config = url to key
            }
        } else {
            GlanceScreen(
                snapshot = snapshot,
                error = error,
                onPause = { scope.launch { runCatching { withContext(Dispatchers.IO) { api?.pause() } } } },
                onResume = { scope.launch { runCatching { withContext(Dispatchers.IO) { api?.resume() } } } },
                onChangePrinter = {
                    store.clear()
                    config = null
                    snapshot = null
                    error = null
                }
            )
        }
    }
}
