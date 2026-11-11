package dev.readout.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.readout.app.widget.WidgetRefresher

/**
 * Owns the connection to exactly one printer and publishes one [UiState].
 *
 * Polling (2 s live / 5 s failing) keeps the snapshot warm while the app is foregrounded;
 * every command refreshes immediately afterwards so the UI never shows its own optimistic guess.
 */
class PrinterController(context: Context) {

    private val appContext = context.applicationContext
    private val store = PrinterStore(context.applicationContext)
    private val discovery = PrinterDiscovery(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initialInstances = store.instances()
    private val initialConfig = store.active()
    private val _state = MutableStateFlow(
        UiState(
            config = initialConfig,
            instances = initialInstances,
            phase = if (initialConfig == null) ConnectionPhase.Unconfigured else ConnectionPhase.Connecting
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var api: OctoPrintApi? = initialConfig?.let { OctoPrintApi(it.baseUrl, it.apiKey) }
    private var pollJob: Job? = null
    private var foreground = false
    private val connectivity: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var versionLabel: String? = null
    private var powerProbed = false
    private var layerPluginMissing = false
    private var psuSupported = false
    private var relaySupported = false
    private var wledSupported = false
    private var appKeyJob: Job? = null
    private var fleetJob: Job? = null

    /** Reads every configured printer at once; failures become offline rows, not errors. */
    fun refreshFleet() {
        fleetJob?.cancel()
        val instances = store.instances()
        if (instances.isEmpty()) {
            _state.update { it.copy(fleet = emptyList()) }
            return
        }
        fleetJob = scope.launch {
            val statuses = instances.map { instance ->
                val client = OctoPrintApi(instance.baseUrl, instance.apiKey)
                runCatching {
                    val printer = withContext(Dispatchers.IO) { client.printer() }
                    val job = withContext(Dispatchers.IO) { runCatching { client.job() }.getOrNull() }
                    val snapshot = SnapshotParser.parse(printer, job, null)
                    FleetStatus(
                        id = instance.id,
                        name = instance.name,
                        online = true,
                        stateLabel = snapshot.stateLabel,
                        jobName = snapshot.jobName,
                        progress = snapshot.progress
                    )
                }.getOrElse {
                    FleetStatus(instance.id, instance.name, false, "Offline", null, null)
                }
            }
            _state.update { it.copy(fleet = statuses) }
        }
    }

    /** Sends one job command to the selected printers (all of them when ids is empty). */
    fun fleetCommand(action: FleetAction, ids: List<String> = emptyList()) = scope.launch {
        val instances = store.instances().filter { ids.isEmpty() || it.id in ids }
        if (instances.isEmpty()) return@launch
        instances.forEach { instance ->
            val client = OctoPrintApi(instance.baseUrl, instance.apiKey)
            withContext(Dispatchers.IO) {
                runCatching {
                    when (action) {
                        FleetAction.Pause -> client.pause()
                        FleetAction.Resume -> client.resume()
                        FleetAction.Cancel -> client.cancel()
                    }
                }
            }
        }
        val verb = when (action) {
            FleetAction.Pause -> "Pause"
            FleetAction.Resume -> "Resume"
            FleetAction.Cancel -> "Cancel"
        }
        _state.update {
            it.copy(notice = "$verb sent to ${instances.size} printer${if (instances.size == 1) "" else "s"}.")
        }
        refreshFleet()
    }

    /** Files held by any of the selected printers, merged by name. */
    suspend fun fleetFiles(ids: List<String>): List<FleetFile> = withContext(Dispatchers.IO) {
        val instances = store.instances().filter { ids.isEmpty() || it.id in ids }
        val index = linkedMapOf<String, MutableList<String>>()
        instances.forEach { instance ->
            val client = OctoPrintApi(instance.baseUrl, instance.apiKey)
            val files = runCatching { SnapshotParser.parseFiles(client.files()) }.getOrNull().orEmpty()
            files.filterNot { it.isDirectory }.forEach { file ->
                index.getOrPut(file.name) { mutableListOf() }.add(instance.id)
            }
        }
        index.map { (name, owning) -> FleetFile(name, owning) }
            .sortedWith(compareByDescending<FleetFile> { it.printerIds.size }.thenBy { it.name })
    }

    /** Starts one file on every selected printer that holds a copy. */
    fun fleetStart(ids: List<String>, fileName: String) = scope.launch {
        val instances = store.instances().filter { it.id in ids }
        if (instances.isEmpty()) return@launch
        var started = 0
        instances.forEach { instance ->
            val client = OctoPrintApi(instance.baseUrl, instance.apiKey)
            withContext(Dispatchers.IO) {
                val file = runCatching { SnapshotParser.parseFiles(client.files()) }.getOrNull()
                    .orEmpty()
                    .firstOrNull { it.name == fileName && !it.isDirectory }
                if (file != null) {
                    if (runCatching { client.startPrint(file.origin, file.path) }.isSuccess) started++
                }
            }
        }
        _state.update {
            it.copy(
                notice = if (started == 0) {
                    "No printer started $fileName."
                } else {
                    "Started $fileName on $started printer${if (started == 1) "" else "s"}."
                }
            )
        }
        refreshFleet()
    }

    fun start() {
        foreground = true
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            var failures = 0
            while (isActive) {
                val configured = _state.value.config != null
                if (!configured) {
                    delay(1_000)
                    continue
                }
                try {
                    refresh()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unexpected: Throwable) {
                    // The loop has to outlive anything refresh throws. If it died, the app would
                    // stop polling silently and only look fixed after a manual reconnect.
                    android.util.Log.w("Readout", "poll failed", unexpected)
                }
                val live = _state.value.phase == ConnectionPhase.Live
                failures = if (live) 0 else failures + 1
                delay(if (live) LIVE_POLL_MS else retryDelayMs(failures))
            }
        }
    }

    fun stop() {
        foreground = false
        pollJob?.cancel()
        pollJob = null
        discovery.stop()
        _state.update { it.copy(discovering = false) }
    }

    fun shutdown() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        stop()
        scope.cancel()
    }

    /**
     * A new network is the one moment worth trying immediately instead of waiting for the backoff:
     * the phone returning to Wi-Fi is the common case after a drop.
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!foreground) return
            if (pollJob?.isActive != true) start()
            scope.launch { refresh() }
        }
    }

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
    }

    fun refreshNow() {
        scope.launch { refresh() }
    }

    suspend fun connect(config: PrinterConfig): CommandResult = withContext(Dispatchers.IO) {
        val candidate = OctoPrintApi(config.baseUrl, config.apiKey)
        runCatching { candidate.probe() }.fold(
            onSuccess = { label ->
                // Reuse the id when this address is already known, so connecting again edits
                // the instance instead of creating a duplicate.
                val existing = store.instances().firstOrNull {
                    it.baseUrl.trimEnd('/').equals(config.baseUrl.trimEnd('/'), ignoreCase = true)
                }
                val id = config.id.ifBlank { existing?.id ?: java.util.UUID.randomUUID().toString() }
                val normalizedUrl = normalizeUrl(config.baseUrl)
                val instance = config.copy(
                    id = id,
                    name = config.name.ifBlank { existing?.name ?: "Printer" },
                    baseUrl = normalizedUrl
                )
                versionLabel = label
                api = candidate
                powerProbed = false
                layerPluginMissing = false
                psuSupported = false
                relaySupported = false
                wledSupported = false
                store.saveInstance(instance)
                store.setActive(instance.id)
                _state.update {
                    it.copy(
                        config = instance,
                        instances = store.instances(),
                        phase = ConnectionPhase.Connecting,
                        errorMessage = null,
                        notice = null
                    )
                }
                refresh()
                CommandResult.Ok
            },
            onFailure = { error ->
                CommandResult.Failed(error.message ?: "Could not reach that printer")
            }
        )
    }

    /** Switches the active printer without losing the others. */
    fun switchTo(id: String) {
        val instance = store.instances().firstOrNull { it.id == id } ?: return
        store.setActive(id)
        api = OctoPrintApi(instance.baseUrl, instance.apiKey)
        versionLabel = null
        powerProbed = false
        layerPluginMissing = false
        psuSupported = false
        relaySupported = false
        wledSupported = false
        _state.update {
            it.copy(
                config = instance,
                instances = store.instances(),
                snapshot = null,
                phase = ConnectionPhase.Connecting,
                errorMessage = null,
                notice = null,
                powerSwitches = emptyList()
            )
        }
        scope.launch { refresh() }
    }

    /** Removes the active printer; falls back to the next one, or to first-run. */
    fun forgetActive() {
        val active = _state.value.config ?: return
        store.removeInstance(active.id)
        val remaining = store.instances()
        if (remaining.isEmpty()) {
            api = null
            versionLabel = null
            store.setActive(null)
            _state.update { UiState(phase = ConnectionPhase.Unconfigured, instances = emptyList()) }
        } else {
            switchTo(remaining.first().id)
        }
    }

    /**
     * Removes any saved printer. Forgetting the active one falls back to the next printer, or to
     * first-run when none are left. Returns the removed config so the caller can offer undo.
     */
    fun forget(id: String): PrinterConfig? {
        val target = store.instances().firstOrNull { it.id == id } ?: return null
        if (target.id == _state.value.config?.id) {
            forgetActive()
        } else {
            store.removeInstance(id)
            _state.update { it.copy(instances = store.instances()) }
        }
        return target
    }

    /** Puts back a printer removed by [forget]; [makeActive] restores it as the active one. */
    fun restore(config: PrinterConfig, makeActive: Boolean) {
        store.saveInstance(config)
        if (makeActive) {
            switchTo(config.id)
        } else {
            _state.update { it.copy(instances = store.instances()) }
        }
    }

    /** Opens the connect screen while keeping the existing printers in storage. */
    fun addAnother() {
        api = null
        versionLabel = null
        powerProbed = false
        layerPluginMissing = false
        psuSupported = false
        relaySupported = false
        wledSupported = false
        store.setActive(null)
        _state.update {
            it.copy(
                config = null,
                snapshot = null,
                phase = ConnectionPhase.Unconfigured,
                errorMessage = null,
                notice = null,
                instances = store.instances(),
                powerSwitches = emptyList()
            )
        }
    }

    /**
     * App Keys handshake: ask the printer for a token, then poll until an operator approves it in
     * OctoPrint's UI. The granted key is saved and used to connect.
     */
    fun requestAppKey(baseUrl: String) {
        val normalized = normalizeUrl(baseUrl)
        if (normalized.isBlank()) return
        appKeyJob?.cancel()
        appKeyJob = scope.launch {
            _state.update { it.copy(appKey = AppKeyState.Waiting, notice = null) }
            val client = OctoPrintApi(normalized, "")
            try {
                if (!withContext(Dispatchers.IO) { client.appKeysAvailable() }) {
                    _state.update { it.copy(appKey = AppKeyState.Unavailable) }
                    return@launch
                }
                val token = withContext(Dispatchers.IO) { client.requestAppKey("Readout") }
                if (token.isBlank()) throw OctoPrintException("The printer did not return a request token.")
                var granted: String? = null
                val deadline = System.currentTimeMillis() + APP_KEY_TIMEOUT_MS
                while (isActive && System.currentTimeMillis() < deadline) {
                    delay(2_000)
                    val key = withContext(Dispatchers.IO) { client.pollAppKey(token) }
                    if (!key.isNullOrBlank()) {
                        granted = key
                        break
                    }
                }
                if (granted == null) {
                    _state.update {
                        it.copy(
                            appKey = AppKeyState.Failed(
                                "No decision yet. Approve Readout in OctoPrint → Settings → Application Keys, then try again."
                            )
                        )
                    }
                    return@launch
                }
                _state.update { it.copy(appKey = AppKeyState.Idle) }
                connect(PrinterConfig(baseUrl = normalized, apiKey = granted, name = "Printer"))
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update {
                    it.copy(appKey = AppKeyState.Failed(error.message ?: "Could not request access"))
                }
            }
        }
    }

    fun cancelAppKey() {
        appKeyJob?.cancel()
        appKeyJob = null
        _state.update { it.copy(appKey = AppKeyState.Idle) }
    }

    private fun normalizeUrl(raw: String): String =
        raw.trim().trimEnd('/').let {
            if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "http://$it"
        }

    /** Toggles one switch. Optimistic: the next poll confirms the real state. */
    fun togglePower(switch: PowerSwitch) = scope.launch {
        val client = api ?: return@launch
        val target = !switch.on
        val source = switch.source
        runCatching {
            withContext(Dispatchers.IO) {
                when (source) {
                    PowerSource.Psu -> client.setPsu(target)
                    PowerSource.Relay -> client.setRelay(switch.id.removePrefix("relay:"), target)
                    PowerSource.Wled -> client.setWled(target)
                }
            }
        }
            .onSuccess {
                _state.update { state ->
                    state.copy(
                        powerSwitches = state.powerSwitches.map {
                            if (it.id == switch.id) it.copy(on = target) else it
                        }
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(notice = error.message ?: "Power command failed") }
            }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null, errorMessage = null) }
    }

    fun saveWebcam(url: String?) {
        val config = _state.value.config ?: return
        val cleaned = url?.trim()?.takeIf { it.isNotEmpty() }
        val updated = config.copy(webcamUrl = cleaned)
        store.saveInstance(updated)
        _state.update { it.copy(config = updated, instances = store.instances()) }
    }

    fun savePrinterName(name: String) {
        val config = _state.value.config ?: return
        val updated = config.copy(name = name.ifBlank { "Printer" })
        store.saveInstance(updated)
        _state.update { it.copy(config = updated, instances = store.instances()) }
    }

    // -- discovery -------------------------------------------------------------------------

    fun discoverPrinters() {
        if (_state.value.discovering) return
        _state.update { it.copy(discovering = true, discovered = emptyList()) }
        discovery.start(
            onFound = { found ->
                _state.update { current ->
                    if (current.discovered.any { it.host == found.host && it.port == found.port }) {
                        current
                    } else {
                        current.copy(discovered = current.discovered + found)
                    }
                }
            },
            onFinished = { message ->
                _state.update { it.copy(discovering = false, notice = message ?: it.notice) }
            }
        )
        scope.launch {
            delay(12_000)
            discovery.stop()
            _state.update { it.copy(discovering = false) }
        }
    }

    // -- commands --------------------------------------------------------------------------

    fun pause() = action { it.pause() }

    fun resume() = action { it.resume() }

    fun cancelPrint() = action { it.cancel() }

    fun connectPrinter() = action { it.connectPrinter() }

    fun disconnectPrinter() = action { it.disconnectPrinter() }

    fun setTarget(component: ComponentTemperature, celsius: Int) = action {
        when {
            component.key == "bed" -> it.setBedTarget(celsius)
            component.key == "chamber" -> it.setChamberTarget(celsius)
            else -> it.setToolTarget(component.index, celsius)
        }
    }

    fun jog(axis: String, millimetres: Float) = action { it.jog(axis, millimetres) }

    fun home() = action { it.home() }

    fun extrude(millimetres: Float) = action { it.extrude(millimetres) }

    fun sendGcode(line: String) = action { it.sendGcode(listOf(line)) }

    fun startPrint(file: FileEntry) = action { it.startPrint(file.origin, file.path) }

    fun deleteFile(file: FileEntry) = action { it.deleteFile(file.origin, file.path) }

    suspend fun loadFiles(): List<FileEntry> = withContext(Dispatchers.IO) {
        val client = api ?: throw OctoPrintException("No printer configured")
        SnapshotParser.parseFiles(client.files())
    }

    private val previewCache = mutableMapOf<String, GcodePreview>()

    /**
     * Streams a G-code file and returns its toolpaths. Parsed on IO and kept for the session, so
     * tapping the same file twice costs nothing; the cache is small and bounded.
     */
    suspend fun loadPreview(file: FileEntry): GcodePreview = withContext(Dispatchers.IO) {
        val key = file.origin + "/" + file.path
        previewCache[key]?.let { return@withContext it }
        val client = api ?: throw OctoPrintException("No printer configured")
        val preview = client.openFile(file.origin, file.path).bufferedReader().use { reader ->
            GcodePreviewParser.parse(reader)
        }
        if (previewCache.size >= 4) previewCache.clear()
        previewCache[key] = preview
        preview
    }

    /**
     * The file the printer is running, parsed on demand. This is what the live viewer draws, and
     * parsing it also gives the Now card its layer estimate on the next poll.
     */
    suspend fun loadJobPreview(): GcodePreview {
        val snapshot = _state.value.snapshot
        val origin = snapshot?.jobOrigin
        val path = snapshot?.jobPath
        if (origin.isNullOrBlank() || path.isNullOrBlank()) {
            throw OctoPrintException("The printer is not running a file")
        }
        return loadPreview(
            FileEntry(
                name = path.substringAfterLast('/'),
                path = path,
                origin = origin,
                sizeBytes = 0L,
                dateSeconds = null,
                isDirectory = false
            )
        )
    }

    suspend fun loadTimelapses(): List<TimelapseEntry> = withContext(Dispatchers.IO) {
        val client = api ?: throw OctoPrintException("No printer configured")
        client.timelapses()
    }

    suspend fun deleteTimelapse(entry: TimelapseEntry): Boolean = withContext(Dispatchers.IO) {
        val client = api ?: return@withContext false
        runCatching { client.deleteTimelapse(entry.name) }.isSuccess
    }

    /** Timelapse downloads live on the web endpoint, not the API, and still need the key. */
    fun timelapseUrl(entry: TimelapseEntry): String {
        val base = _state.value.config?.baseUrl?.trimEnd('/') ?: return ""
        val encoded = java.net.URLEncoder.encode(entry.name, "UTF-8")
        return "$base/download/timelapse/$encoded"
    }

    /** Deletes sequentially and reports how many the printer accepted. */
    suspend fun deleteFiles(files: List<FileEntry>): Int = withContext(Dispatchers.IO) {
        val client = api ?: return@withContext 0
        files.count { file ->
            runCatching { client.deleteFile(file.origin, file.path) }.isSuccess
        }
    }

    suspend fun upload(
        fileName: String,
        mimeType: String,
        sizeBytes: Long?,
        openStream: () -> java.io.InputStream,
        onProgress: (Float) -> Unit
    ): CommandResult = withContext(Dispatchers.IO) {
        val client = api ?: return@withContext CommandResult.Failed("No printer configured")
        runCatching {
            client.uploadFile(fileName, mimeType, sizeBytes, openStream, onProgress)
        }.fold(
            onSuccess = { CommandResult.Ok },
            onFailure = { CommandResult.Failed(it.message ?: "Upload failed") }
        )
    }

    /** A single JPEG from the camera — the fallback for setups that serve no live stream. */
    suspend fun fetchCameraFrame(): ByteArray? = withContext(Dispatchers.IO) {
        val config = _state.value.config ?: return@withContext null
        runCatching { api?.fetchBytes(cameraUrls(config).still) }.getOrNull()
    }

    /**
     * Opens the camera's stream URL and reports what came back: streamed frames (each handed to
     * [onFrame]), a single image, or nothing. The content type decides, so a URL that only ever
     * serves stills is polled rather than waiting on a stream that will never arrive.
     *
     * [keepGoing] lets the caller end the read: a blocking socket cannot be cancelled from outside.
     */
    suspend fun readCamera(
        onFrame: (ByteArray) -> Unit,
        keepGoing: () -> Boolean = { true }
    ): CameraRead = withContext(Dispatchers.IO) {
        val config = _state.value.config ?: return@withContext CameraRead.Nothing
        val client = api ?: return@withContext CameraRead.Nothing
        client.stream(cameraUrls(config).stream) { input, contentType ->
            when {
                contentType.contains("multipart", ignoreCase = true) ->
                    CameraRead.Frames(MjpegFrameReader(sink = onFrame).read(input, keepGoing))

                contentType.startsWith("image/", ignoreCase = true) -> {
                    val bytes = input.readBytes()
                    if (bytes.isEmpty()) CameraRead.Nothing else CameraRead.Single(bytes)
                }

                else -> CameraRead.Nothing
            }
        }
    }

    private fun action(block: suspend (OctoPrintApi) -> Unit): Job = scope.launch {
        val client = api ?: return@launch
        _state.update { it.copy(busy = true) }
        try {
            block(client)
            refresh()
        } catch (error: Exception) {
            _state.update { it.copy(notice = error.message ?: "The printer rejected that command") }
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun refresh() {
        val client = api ?: return
        try {
            val printer = withContext(Dispatchers.IO) { client.printer() }
            val job = withContext(Dispatchers.IO) { runCatching { client.job() }.getOrNull() }
            if (versionLabel == null) {
                versionLabel = withContext(Dispatchers.IO) { runCatching { client.probe() }.getOrNull() }
            }
            val snapshot = withLayer(SnapshotParser.parse(printer, job, versionLabel), client)
            WidgetRefresher.push(appContext, snapshot)
            _state.update {
                it.copy(
                    phase = ConnectionPhase.Live,
                    snapshot = snapshot,
                    lastUpdateMs = System.currentTimeMillis(),
                    errorMessage = null
                )
            }
            refreshPower(client)
        } catch (error: Exception) {
            val message = error.message ?: "Could not reach the printer"
            _state.update {
                it.copy(
                    phase = if (it.snapshot != null) ConnectionPhase.Offline else ConnectionPhase.Fault,
                    errorMessage = message
                )
            }
        }
    }

    /**
     * Fills in the layer the job is on, in the order of what can be trusted: a plugin's exact
     * count, then a value OctoPrint itself reported, then an estimate from the parsed file. The
     * estimate needs the file to have been opened once, so it appears only for files the user has
     * already looked at rather than downloading every job in the background.
     */
    private suspend fun withLayer(snapshot: PrinterSnapshot, client: OctoPrintApi): PrinterSnapshot {
        if (!snapshot.state.jobInProgress) {
            return snapshot.copy(layer = null, layerCount = null, layerEstimated = false)
        }
        if (snapshot.layer != null && snapshot.layerCount != null) return snapshot

        if (!layerPluginMissing) {
            val reading = withContext(Dispatchers.IO) {
                runCatching { client.displayLayerProgress() }.getOrNull()
            }
            if (reading != null) {
                return snapshot.copy(
                    layer = reading.first,
                    layerCount = reading.second,
                    layerEstimated = false
                )
            }
            layerPluginMissing = true
        }

        val key = listOfNotNull(snapshot.jobOrigin, snapshot.jobPath).joinToString("/")
        val preview = previewCache[key] ?: return snapshot
        val current = estimateLayer(preview, snapshot.progress) ?: return snapshot
        return snapshot.copy(
            layer = current,
            layerCount = preview.layers.size,
            layerEstimated = true
        )
    }

    private suspend fun refreshPower(client: OctoPrintApi) {
        if (!powerProbed) {
            powerProbed = true
            val psu = withContext(Dispatchers.IO) { runCatching { client.psuState() }.getOrNull() }
            val relays = withContext(Dispatchers.IO) { runCatching { client.octoRelays() }.getOrNull() }
            val wled = withContext(Dispatchers.IO) { runCatching { client.wledState() }.getOrNull() }
            psuSupported = psu != null
            relaySupported = relays != null
            wledSupported = wled != null
            _state.update { it.copy(powerSwitches = switches(psu, relays, wled)) }
            return
        }
        if (!psuSupported && !relaySupported && !wledSupported) return
        val psu = if (psuSupported) {
            withContext(Dispatchers.IO) { runCatching { client.psuState() }.getOrNull() }
        } else {
            null
        }
        val relays = if (relaySupported) {
            withContext(Dispatchers.IO) { runCatching { client.octoRelays() }.getOrNull() }
        } else {
            null
        }
        val wled = if (wledSupported) {
            withContext(Dispatchers.IO) { runCatching { client.wledState() }.getOrNull() }
        } else {
            null
        }
        _state.update { it.copy(powerSwitches = switches(psu, relays, wled)) }
    }

    private fun switches(
        psu: Boolean?,
        relays: List<RelayState>?,
        wled: Boolean?
    ): List<PowerSwitch> {
        val result = mutableListOf<PowerSwitch>()
        if (psu != null) result += PowerSwitch("psu", "Printer supply", psu, PowerSource.Psu)
        relays?.forEach { relay ->
            result += PowerSwitch("relay:${relay.id}", relay.name, relay.on, PowerSource.Relay)
        }
        if (wled != null) result += PowerSwitch("wled", "WLED lights", wled, PowerSource.Wled)
        return result
    }
}

private const val APP_KEY_TIMEOUT_MS = 180_000L
private const val LIVE_POLL_MS = 2_000L

/**
 * Backoff for a printer that is not answering. Capped at 15 s so a printer that comes back is
 * noticed by itself, which is the whole point: nobody should have to press Connect.
 */
internal fun retryDelayMs(failures: Int): Long = when {
    failures <= 1 -> 2_000L
    failures == 2 -> 4_000L
    failures == 3 -> 8_000L
    else -> 15_000L
}