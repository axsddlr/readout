package dev.readout.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import dev.readout.app.data.PrinterConfig
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.readout.app.data.ConnectionPhase
import dev.readout.app.data.FileEntry
import dev.readout.app.data.MachineState
import dev.readout.app.data.jobInProgress
import dev.readout.app.data.ThemePreference
import dev.readout.app.notification.PrintNotificationService
import dev.readout.app.ui.components.Masthead
import dev.readout.app.ui.components.PrinterSwitcherSheet
import dev.readout.app.ui.screens.ConnectScreen
import dev.readout.app.ui.screens.FilesScreen
import dev.readout.app.ui.screens.MachineScreen
import dev.readout.app.ui.screens.NowScreen
import dev.readout.app.ui.screens.FleetScreen
import dev.readout.app.ui.screens.PreviewScreen
import dev.readout.app.ui.screens.SettingsScreen
import dev.readout.app.ui.screens.TimelapseScreen
import dev.readout.app.ui.theme.ReadoutTheme
import dev.readout.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private enum class Destination(val route: Route, val label: String, val icon: ImageVector) {
    Now(Route.Now, "Now", ReadoutIcons.Printer),
    Files(Route.Files, "Files", Icons.AutoMirrored.Filled.List),
    Machine(Route.Machine, "Machine", ReadoutIcons.Terminal)
}

/** Survives process death and rotation: only the route names go into the bundle. */
private val RouteStackSaver = listSaver<List<Route>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names ->
        names.mapNotNull { name -> Route.entries.firstOrNull { it.name == name } }
            .ifEmpty { Nav.start }
    }
)

@Composable
fun ReadoutRoot(vm: ReadoutViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val themePreference by vm.theme.collectAsStateWithLifecycle()
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onForeground()
                Lifecycle.Event.ON_STOP -> vm.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mode = when (themePreference) {
        ThemePreference.System -> ThemeMode.System
        ThemePreference.Light -> ThemeMode.Light
        ThemePreference.Dark -> ThemeMode.Dark
    }

    ReadoutTheme(mode = mode, dynamicColor = dynamicColor) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            if (state.config == null) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    ConnectScreen(
                        discovered = state.discovered,
                        discovering = state.discovering,
                        serverMessage = state.notice,
                        existingPrinterName = state.instances.firstOrNull()?.name,
                        onResumeExisting = {
                            state.instances.firstOrNull()?.let { vm.controller.switchTo(it.id) }
                        },
                        onDiscover = { vm.controller.discoverPrinters() },
                        appKeyState = state.appKey,
                        onRequestAccess = { url -> vm.controller.requestAppKey(url) },
                        onCancelAppKey = { vm.controller.cancelAppKey() },
                        onConnect = { address, key ->
                            vm.connect(
                                dev.readout.app.data.PrinterConfig(
                                    baseUrl = address,
                                    apiKey = key
                                )
                            )
                        }
                    )
                }
            } else {
                MainShell(vm = vm)
            }
        }
    }
}

@Composable
private fun MainShell(vm: ReadoutViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val themePreference by vm.theme.collectAsStateWithLifecycle()
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
    val notificationsEnabled by vm.notifications.collectAsStateWithLifecycle()
    val moveDuringPrint by vm.moveDuringPrint.collectAsStateWithLifecycle()
    val cameraLive by vm.cameraLive.collectAsStateWithLifecycle()
    val cameraAvailable by vm.cameraAvailable.collectAsStateWithLifecycle()
    val cameraSettings by vm.cameraSettings.collectAsStateWithLifecycle()
    val keepScreenOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val previewSettings by vm.previewSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Only while a job runs: the setting is about watching a print, not holding the phone awake for
    // its own sake.
    val printingNow = state.snapshot?.state?.jobInProgress == true
    val rootView = LocalView.current
    DisposableEffect(rootView, printingNow, keepScreenOn) {
        rootView.keepScreenOn = printingNow && keepScreenOn
        onDispose { rootView.keepScreenOn = false }
    }
    var stack by rememberSaveable(stateSaver = RouteStackSaver) { mutableStateOf(Nav.start) }
    var showSwitcher by remember { mutableStateOf(false) }
    val route = stack.last()

    LaunchedEffect(state.notice) {
        state.notice?.let { message ->
            vm.controller.clearNotice()
            snackbarHost.showSnackbar(message)
        }
    }

    val notify: (String) -> Unit = { message -> scope.launch { snackbarHost.showSnackbar(message) } }

    // Swipe-to-remove. Undo restores the printer; the last one asks first, because removing it
    // drops the app back to first-run and the snackbar would go with it.
    var confirmRemoveLast by remember { mutableStateOf<PrinterConfig?>(null) }
    val removePrinter: (PrinterConfig) -> Boolean = { config ->
        if (state.instances.size <= 1) {
            confirmRemoveLast = config
            false
        } else {
            val wasActive = config.id == state.config?.id
            vm.controller.forget(config.id)?.let { removed ->
                scope.launch {
                    val result = snackbarHost.showSnackbar(
                        message = "Removed ${removed.name} · ${removed.displayHost}",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.controller.restore(removed, wasActive)
                }
            }
            true
        }
    }

    // The service owns its own stop; the app only asks it to start while a job is active,
    // and stops it when the user turns the feature off.
    val printActive = state.snapshot?.state in setOf(
        MachineState.Printing, MachineState.Paused,
        MachineState.Pausing, MachineState.Resuming
    )
    LaunchedEffect(notificationsEnabled, printActive) {
        when {
            !notificationsEnabled -> PrintNotificationService.stop(context)
            printActive -> PrintNotificationService.start(context)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val surface = Nav.surface(maxWidth.value.toInt(), maxHeight.value.toInt())
        // The suite and masthead are top-level chrome. Detail screens bring their own header
        // and back arrow, so both step aside instead of stacking a second bar on top.
        val onTopLevel = route.isTopLevel
        // A reading measure: past an expanded window the content stops growing instead of
        // stretching a card or a line of help across the glass. Centred in the pane the rail
        // leaves, and it never shrinks a phone layout.
        val pane = maxWidth - if (onTopLevel && surface == NavSurface.Rail) 80.dp else 0.dp
        val gutter = ((pane - 840.dp) / 2).coerceAtLeast(0.dp)

        Scaffold(
            topBar = {
                if (onTopLevel) Column(Modifier.statusBarsPadding()) {
                    Masthead(
                        printerName = state.config?.name ?: "Printer",
                        connectionLabel = when (state.phase) {
                            ConnectionPhase.Live -> "Live"
                            ConnectionPhase.Connecting -> "Linking"
                            ConnectionPhase.Offline -> "Offline"
                            ConnectionPhase.Fault -> "No link"
                            ConnectionPhase.Unconfigured -> "Setup"
                        },
                        live = state.phase == ConnectionPhase.Live,
                        onClick = { showSwitcher = true },
                        actions = {
                            IconAction(
                                icon = Icons.Filled.Settings,
                                label = "Settings",
                                onClick = { stack = Nav.push(stack, Route.Settings) }
                            )
                        }
                    )
                }
            },
            bottomBar = {
                if (onTopLevel && surface == NavSurface.BottomBar) {
                    NavigationBar {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = route == item.route,
                                onClick = { stack = Nav.select(stack, item.route) },
                                icon = {
                                    androidx.compose.material3.Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHost) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Scaffold's padding already holds the bars; consume it so imePadding()
                    // only adds what the keyboard covers beyond them.
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
            ) {
                if (onTopLevel && surface == NavSurface.Rail) {
                    NavigationRail {
                        Destination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = route == item.route,
                                onClick = { stack = Nav.select(stack, item.route) },
                                icon = {
                                    androidx.compose.material3.Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }

                Box(Modifier.weight(1f).padding(horizontal = gutter)) {
                    // One handler for the whole stack. Disabled at the root so Back leaves the
                    // app rather than being swallowed.
                    BackHandler(enabled = stack.size > 1) { stack = Nav.back(stack) ?: stack }
                    when (route) {
                        Route.Settings -> SettingsScreen(
                            config = state.config,
                            theme = themePreference,
                            dynamicColor = dynamicColor,
                            notificationsEnabled = notificationsEnabled,
                            moveDuringPrint = moveDuringPrint,
                            keepScreenOn = keepScreenOn,
                            jobActive = printingNow,
                            onTheme = vm::setTheme,
                            onDynamicColor = vm::setDynamicColor,
                            onNotificationsChange = vm::setNotifications,
                            onMoveDuringPrint = vm::setMoveDuringPrint,
                            onKeepScreenOn = vm::setKeepScreenOn,
                            loadSystem = { vm.controller.loadSystem() },
                            onSystemCommand = { source, action ->
                                vm.controller.runSystemCommand(source, action)
                            },
                            onNameChange = { vm.controller.savePrinterName(it) },
                            onWebcamChange = { vm.controller.saveWebcam(it) },
                            onDisconnect = {
                                stack = Nav.select(stack, Route.Now)
                                vm.controller.forgetActive()
                            },
                            instances = state.instances,
                            activeId = state.config?.id,
                            onSwitchInstance = { id -> vm.controller.switchTo(id) },
                            onRemoveInstance = removePrinter,
                            onAddAnother = {
                                stack = Nav.select(stack, Route.Now)
                                vm.controller.addAnother()
                            },
                            onBack = { stack = Nav.back(stack) ?: stack }
                        )

                        Route.Timelapses -> TimelapseScreen(
                            live = state.phase == ConnectionPhase.Live,
                            load = { vm.controller.loadTimelapses() },
                            streamUrl = { entry -> vm.controller.timelapseUrl(entry) },
                            apiKey = state.config?.apiKey.orEmpty(),
                            delete = { entry -> vm.controller.deleteTimelapse(entry) },
                            onBack = { stack = Nav.back(stack) ?: stack }
                        )

                        Route.Fleet -> FleetScreen(
                            fleet = state.fleet,
                            onRefresh = { vm.controller.refreshFleet() },
                            onAction = { action, ids -> vm.controller.fleetCommand(action, ids) },
                            loadFiles = { ids -> vm.controller.fleetFiles(ids) },
                            onStartFile = { ids, name -> vm.controller.fleetStart(ids, name) },
                            onBack = { stack = Nav.back(stack) ?: stack }
                        )

                        Route.Preview -> PreviewScreen(
                            state = state,
                            load = { vm.controller.loadJobPreview() },
                            settings = previewSettings,
                            onPreviewSettings = vm::setPreviewSettings,
                            onBack = { stack = Nav.back(stack) ?: stack }
                        )

                        Route.Now -> NowScreen(
                            state = state,
                            onPause = { vm.controller.pause() },
                            onResume = { vm.controller.resume() },
                            onCancel = { vm.controller.cancelPrint() },
                            onOpenFiles = { stack = Nav.select(stack, Route.Files) },
                            onOpenMachine = { stack = Nav.select(stack, Route.Machine) },
                            onOpenSettings = { stack = Nav.push(stack, Route.Settings) },
                            onSetTarget = { component, value -> vm.controller.setTarget(component, value) },
                            cameraFrame = vm.cameraFrame,
                            cameraLive = cameraLive,
                            cameraAvailable = cameraAvailable,
                            cameraSettings = cameraSettings,
                            onCameraSettings = vm::setCameraSettings,
                            onCameraVisible = vm::onCameraVisible,
                            onOpenPreview = { stack = Nav.push(stack, Route.Preview) }
                        )

                        Route.Files -> FilesScreen(
                            live = state.phase == ConnectionPhase.Live,
                            loadFiles = { vm.controller.loadFiles() },
                            deleteFiles = { files -> vm.controller.deleteFiles(files) },
                            upload = { name, mime, size, open, onProgress ->
                                vm.controller.upload(name, mime, size, open, onProgress)
                            },
                            onStartPrint = { file: FileEntry ->
                                vm.controller.startPrint(file)
                                notify("Print command sent: ${file.name}")
                            },
                            onDelete = { file: FileEntry ->
                                vm.controller.deleteFile(file)
                                notify("Deleted ${file.name}")
                            },
                            onOpenTimelapses = { stack = Nav.push(stack, Route.Timelapses) },
                            loadPreview = { file -> vm.controller.loadPreview(file) },
                            previewSettings = previewSettings,
                            onPreviewSettings = vm::setPreviewSettings
                        )

                        Route.Machine -> MachineScreen(
                            snapshot = state.snapshot,
                            busy = state.busy,
                            powerSwitches = state.powerSwitches,
                            onTogglePower = { switch -> vm.controller.togglePower(switch) },
                            onSetTarget = { component, value -> vm.controller.setTarget(component, value) },
                            onJog = { axis, amount -> vm.controller.jog(axis, amount) },
                            onHome = { axes -> vm.controller.home(axes) },
                            onExtrude = { amount -> vm.controller.extrude(amount) },
                            onGcode = { line -> vm.controller.sendGcode(line) },
                            onConnectPrinter = { vm.controller.connectPrinter() },
                            onDisconnectPrinter = { vm.controller.disconnectPrinter() },
                            onOpenSettings = { stack = Nav.push(stack, Route.Settings) },
                            allowMoveWhilePrinting = moveDuringPrint
                        )
                    }
                }
            }
        }
    }
    confirmRemoveLast?.let { config ->
        AlertDialog(
            onDismissRequest = { confirmRemoveLast = null },
            title = { Text("Remove ${config.name}?") },
            text = {
                Text("It is your only printer. Its address and API key are deleted from this device and Readout returns to setup. Your OctoPrint settings are not touched.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveLast = null
                    showSwitcher = false
                    vm.controller.forget(config.id)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveLast = null }) { Text("Keep") }
            }
        )
    }
    if (showSwitcher) {
        PrinterSwitcherSheet(
            instances = state.instances,
            activeId = state.config?.id,
            onSelect = { id ->
                showSwitcher = false
                vm.controller.switchTo(id)
            },
            onRemove = removePrinter,
            onAdd = {
                showSwitcher = false
                vm.controller.addAnother()
            },
            onOpenFleet = {
                showSwitcher = false
                stack = Nav.push(stack, Route.Fleet)
            },
            onDismiss = { showSwitcher = false }
        )
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
