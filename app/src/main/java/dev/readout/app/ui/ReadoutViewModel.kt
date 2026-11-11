package dev.readout.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.readout.app.data.CameraRead
import dev.readout.app.data.CameraSettings
import dev.readout.app.data.CommandResult
import dev.readout.app.data.PrinterConfig
import dev.readout.app.data.PreviewSettings
import dev.readout.app.data.PrinterController
import dev.readout.app.data.PrinterStore
import dev.readout.app.data.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    private val _previewSettings = MutableStateFlow(store.previewSettings())
    val previewSettings: StateFlow<PreviewSettings> = _previewSettings.asStateFlow()

    private val _cameraSettings = MutableStateFlow(store.cameraSettings())
    val cameraSettings: StateFlow<CameraSettings> = _cameraSettings.asStateFlow()

    fun setCameraSettings(settings: CameraSettings) {
        store.setCameraSettings(settings)
        _cameraSettings.value = settings
    }

    fun setPreviewSettings(settings: PreviewSettings) {
        store.setPreviewSettings(settings)
        _previewSettings.value = settings
    }

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
            var lastDecoded = 0L
            for (bytes in frames) {
                // The camera pushes ~25 fps; a small card cannot show that and the decode cost is
                // real, so the UI settles on about ten frames a second.
                val now = SystemClock.elapsedRealtime()
                if (now - lastDecoded < MIN_FRAME_INTERVAL_MS) continue
                lastDecoded = now
                decodeFrame(bytes)?.let { _cameraFrame.value = it }
            }
        }
        try {
            val job = coroutineContext[Job]
            while (true) {
                // Data saver skips the stream entirely: the camera's bitrate is the cost it exists
                // to avoid, and a still every few seconds is enough to glance at.
                if (!_cameraSettings.value.streamLive) {
                    _cameraLive.value = false
                    val still = runCatching { controller.fetchCameraFrame() }.getOrNull()
                    if (still != null) {
                        frames.trySend(still)
                        _cameraAvailable.value = true
                    } else if (_cameraFrame.value == null) {
                        _cameraAvailable.value = false
                    }
                    delay(DATA_SAVER_MS)
                    continue
                }
                val result = runCatching {
                    controller.readCamera(
                        onFrame = { bytes ->
                            _cameraLive.value = true
                            _cameraAvailable.value = true
                            frames.trySend(bytes)
                        },
                        // Cancelling the coroutine cannot interrupt a blocking socket read, so the
                        // reader is told to stop between frames; leaving the card — or switching on
                        // data saver — therefore closes the camera connection within a frame.
                        keepGoing = { job?.isActive != false && _cameraSettings.value.streamLive }
                    )
                }.getOrNull()

                // A stop that raced with the read must not be mistaken for the stream ending.
                currentCoroutineContext().ensureActive()

                when (result) {
                    is CameraRead.Frames -> {
                        // The stream ended after frames arrived; the next attempt will reconnect.
                        _cameraLive.value = false
                        _cameraAvailable.value = result.count > 0
                        delay(RETRY_STREAM_MS)
                    }

                    is CameraRead.Single -> {
                        // The URL answers with one image, so it is a still camera being polled.
                        _cameraLive.value = false
                        _cameraAvailable.value = true
                        frames.trySend(result.bytes)
                        delay(RETRY_STILL_MS)
                    }

                    else -> {
                        _cameraLive.value = false
                        val still = runCatching { controller.fetchCameraFrame() }.getOrNull()
                        if (still != null) {
                            frames.trySend(still)
                            _cameraAvailable.value = true
                        } else if (_cameraFrame.value == null) {
                            _cameraAvailable.value = false
                        }
                        delay(RETRY_STILL_MS)
                    }
                }
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

    private companion object {
        const val MIN_FRAME_INTERVAL_MS = 100L
        const val RETRY_STREAM_MS = 500L
        const val RETRY_STILL_MS = 2_000L
        const val DATA_SAVER_MS = 5_000L
    }
}
