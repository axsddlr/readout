package dev.readout.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.readout.app.data.CommandResult
import dev.readout.app.data.PrinterConfig
import dev.readout.app.data.PrinterController
import dev.readout.app.data.PrinterStore
import dev.readout.app.data.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReadoutViewModel(application: Application) : AndroidViewModel(application) {

    private val store = PrinterStore(application)
    val controller = PrinterController(application)

    val state = controller.state

    private val _theme = MutableStateFlow(store.themeMode())
    val theme: StateFlow<ThemePreference> = _theme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(store.dynamicColor())
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _notifications = MutableStateFlow(store.notificationsEnabled())
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    private val _moveDuringPrint = MutableStateFlow(store.moveDuringPrint())
    val moveDuringPrint: StateFlow<Boolean> = _moveDuringPrint.asStateFlow()

    private val _cameraFrame = MutableStateFlow<ImageBitmap?>(null)
    val cameraFrame: StateFlow<ImageBitmap?> = _cameraFrame.asStateFlow()

    /** True while frames are arriving from the MJPEG stream rather than a snapshot every few seconds. */
    private val _cameraLive = MutableStateFlow(false)
    val cameraLive: StateFlow<Boolean> = _cameraLive.asStateFlow()

    /** False once both the stream and the snapshot failed, so the card can offer setup instead. */
    private val _cameraAvailable = MutableStateFlow(true)
    val cameraAvailable: StateFlow<Boolean> = _cameraAvailable.asStateFlow()

    private var cameraVisible = false
    private var cameraJob: Job? = null

    fun setMoveDuringPrint(enabled: Boolean) {
        store.setMoveDuringPrint(enabled)
        _moveDuringPrint.value = enabled
    }

    fun setNotifications(enabled: Boolean) {
        store.setNotificationsEnabled(enabled)
        _notifications.value = enabled
    }

    fun setTheme(preference: ThemePreference) {
        store.setThemeMode(preference)
        _theme.value = preference
    }

    fun setDynamicColor(enabled: Boolean) {
        store.setDynamicColor(enabled)
        _dynamicColor.value = enabled
    }

    suspend fun connect(config: PrinterConfig): CommandResult = controller.connect(config)

    fun onForeground() {
        controller.start()
        if (cameraVisible) startCamera()
    }

    fun onBackground() {
        stopCamera()
        controller.stop()
    }

    /** The camera only runs while its card is on screen and the app is in front. */
    fun onCameraVisible(visible: Boolean) {
        if (cameraVisible == visible) return
        cameraVisible = visible
        if (visible) startCamera() else stopCamera()
    }

    private fun startCamera() {
        if (cameraJob?.isActive == true) return
        cameraJob = viewModelScope.launch { cameraLoop() }
    }

    private fun stopCamera() {
        cameraJob?.cancel()
        cameraJob = null
        _cameraLive.value = false
    }

    /**
     * Reads the stream for as long as it lasts, decoding off the main thread through a one-slot
     * channel: a slow decoder drops frames instead of queuing them, which is what keeps a 25 fps
     * camera from growing a backlog.
     */
    private suspend fun cameraLoop() = coroutineScope {
        val frames = Channel<ByteArray>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val decoder = launch(Dispatchers.Default) {
            for (bytes in frames) {
                decodeFrame(bytes)?.let { _cameraFrame.value = it }
            }
        }
        try {
            while (true) {
                val read = runCatching {
                    controller.streamCamera { bytes ->
                        _cameraLive.value = true
                        _cameraAvailable.value = true
                        frames.trySend(bytes)
                    }
                }.getOrDefault(0)

                if (read == 0) {
                    // No stream: try a still, then wait before asking again.
                    _cameraLive.value = false
                    val still = runCatching { controller.fetchCameraFrame() }.getOrNull()
                    if (still != null) {
                        frames.trySend(still)
                        _cameraAvailable.value = true
                    } else if (_cameraFrame.value == null) {
                        _cameraAvailable.value = false
                    }
                } else {
                    // The stream ended after frames arrived; the next attempt will reconnect.
                    _cameraLive.value = false
                    _cameraAvailable.value = true
                }
                delay(if (_cameraLive.value) 500 else 2_000)
            }
        } finally {
            frames.close()
            decoder.join()
        }
    }

    private fun decodeFrame(bytes: ByteArray): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        // A 4K camera would otherwise allocate tens of megabytes per frame for a card that is a
        // few hundred dp tall.
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 1280) sample *= 2
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )?.asImageBitmap()
    }.getOrNull()

    override fun onCleared() {
        stopCamera()
        controller.shutdown()
        super.onCleared()
    }
}
