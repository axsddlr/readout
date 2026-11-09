package dev.readout.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.readout.app.data.ConnectionPhase
import dev.readout.app.data.FileEntry
import dev.readout.app.data.MachineState
import dev.readout.app.data.ThemePreference
import dev.readout.app.notification.PrintNotificationService
import dev.readout.app.ui.components.Masthead
import dev.readout.app.ui.components.PrinterSwitcherSheet
import dev.readout.app.ui.screens.ConnectScreen
import dev.readout.app.ui.screens.FilesScreen
import dev.readout.app.ui.screens.MachineScreen
import dev.readout.app.ui.screens.NowScreen
import dev.readout.app.ui.screens.FleetScreen
import dev.readout.app.ui.screens.SettingsScreen
import dev.readout.app.ui.screens.TimelapseScreen
import dev.readout.app.ui.theme.ReadoutTheme
import dev.readout.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val icon: ImageVector) {
    Now("Now", ReadoutIcons.Printer),
    Files("Files", Icons.AutoMirrored.Filled.List),
    Machine("Machine", ReadoutIcons.Terminal)
}

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
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTimelapses by rememberSaveable { mutableStateOf(false) }
    var showFleet by rememberSaveable { mutableStateOf(false) }
    var showSwitcher by remember { mutableStateOf(false) }
    val destination = Destination.entries[tabIndex.coerceIn(0, Destination.entries.lastIndex)]

    LaunchedEffect(state.notice) {
        state.notice?.let { message ->
            vm.controller.clearNotice()
            snackbarHost.showSnackbar(message)
        }
    }

    val notify: (String) -> Unit = { message -> scope.launch { snackbarHost.showSnackbar(message) } }

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
        val expanded = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                Column(Modifier.statusBarsPadding()) {
                    Masthead(
                        printerName = state.config?.name ?: "Printer",
                        connectionLabel = when (state.phase) {
                            ConnectionPhase.Live -> "LIVE"
                            ConnectionPhase.Connecting -> "LINKING"
                            ConnectionPhase.Offline -> "OFFLINE"
                            ConnectionPhase.Fault -> "NO LINK"
                            ConnectionPhase.Unconfigured -> "SETUP"
                        },
                        live = state.phase == ConnectionPhase.Live,
                        onClick = { showSwitcher = true },
                        actions = {
                            IconAction(
                                icon = Icons.Filled.Settings,
                                label = "Settings",
                                onClick = { showSettings = true }
                            )
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            bottomBar = {
                if (!expanded) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Destination.entries.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = tabIndex == index && !showSettings,
                                onClick = {
                                    showSettings = false
                                    tabIndex = index
                                },
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
                    .then(if (expanded) Modifier.navigationBarsPadding() else Modifier)
            ) {
                if (expanded) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        Destination.entries.forEachIndexed { index, item ->
                            NavigationRailItem(
                                selected = tabIndex == index && !showSettings,
                                onClick = {
                                    showSettings = false
                                    tabIndex = index
                                },
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
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                Box(Modifier.weight(1f)) {
                    BackHandler(enabled = showSettings) { showSettings = false }
                    BackHandler(enabled = showTimelapses) { showTimelapses = false }
                    BackHandler(enabled = showFleet) { showFleet = false }
                    when {
                        showSettings -> SettingsScreen(
                            config = state.config,
                            theme = themePreference,
                            dynamicColor = dynamicColor,
                            notificationsEnabled = notificationsEnabled,
                            onTheme = vm::setTheme,
                            onDynamicColor = vm::setDynamicColor,
                            onNotificationsChange = vm::setNotifications,
                            onNameChange = { vm.controller.savePrinterName(it) },
                            onWebcamChange = { vm.controller.saveWebcam(it) },
                            onDisconnect = {
                                showSettings = false
                                vm.controller.forgetActive()
                            },
                            instances = state.instances,
                            activeId = state.config?.id,
                            onSwitchInstance = { id -> vm.controller.switchTo(id) },
                            onAddAnother = {
                                showSettings = false
                                vm.controller.addAnother()
                            },
                            onBack = { showSettings = false }
                        )

                        showTimelapses -> TimelapseScreen(                            live = state.phase == ConnectionPhase.Live,
                            load = { vm.controller.loadTimelapses() },
                            streamUrl = { entry -> vm.controller.timelapseUrl(entry) },
                            apiKey = state.config?.apiKey.orEmpty(),
                            delete = { entry -> vm.controller.deleteTimelapse(entry) },
                            onBack = { showTimelapses = false }
                        )

                        showFleet -> FleetScreen(
                            fleet = state.fleet,
                            onRefresh = { vm.controller.refreshFleet() },
                            onAction = { action, ids -> vm.controller.fleetCommand(action, ids) },
                            loadFiles = { ids -> vm.controller.fleetFiles(ids) },
                            onStartFile = { ids, name -> vm.controller.fleetStart(ids, name) },
                            onBack = { showFleet = false }
                        )

                        destination == Destination.Now -> NowScreen(
                            state = state,
                            onPause = { vm.controller.pause() },
                            onResume = { vm.controller.resume() },
                            onCancel = { vm.controller.cancelPrint() },
                            onOpenFiles = { tabIndex = 1 },
                            onOpenMachine = { tabIndex = 2 },
                            onOpenSettings = { showSettings = true },
                            onSetTarget = { component, value -> vm.controller.setTarget(component, value) },
                            fetchFrame = { vm.controller.fetchCameraFrame() }
                        )

                        destination == Destination.Files -> FilesScreen(
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
                            onOpenTimelapses = { showTimelapses = true }
                        )

                        else -> MachineScreen(
                            snapshot = state.snapshot,
                            busy = state.busy,
                            powerSwitches = state.powerSwitches,
                            onTogglePower = { switch -> vm.controller.togglePower(switch) },
                            onSetTarget = { component, value -> vm.controller.setTarget(component, value) },
                            onJog = { axis, amount -> vm.controller.jog(axis, amount) },
                            onHome = { vm.controller.home() },
                            onExtrude = { amount -> vm.controller.extrude(amount) },
                            onGcode = { line -> vm.controller.sendGcode(line) },
                            onConnectPrinter = { vm.controller.connectPrinter() },
                            onDisconnectPrinter = { vm.controller.disconnectPrinter() },
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
    if (showSwitcher) {
        PrinterSwitcherSheet(
            instances = state.instances,
            activeId = state.config?.id,
            onSelect = { id ->
                showSwitcher = false
                vm.controller.switchTo(id)
            },
            onAdd = {
                showSwitcher = false
                vm.controller.addAnother()
            },
            onOpenFleet = {
                showSwitcher = false
                showFleet = true
            },
            onDismiss = { showSwitcher = false }
        )
    }
}

@Composable
private fun IconAction(    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
