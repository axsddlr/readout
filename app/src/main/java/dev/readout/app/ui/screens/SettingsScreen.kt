package dev.readout.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.readout.app.data.PrinterConfig
import dev.readout.app.data.ThemePreference
import dev.readout.app.ui.components.Plate
import dev.readout.app.ui.components.RowDivider
import dev.readout.app.ui.components.SectionHeading
import dev.readout.app.ui.components.SpecRow
import dev.readout.app.ui.components.SwipeToRemove
import dev.readout.app.ui.theme.Spacing
import dev.readout.app.util.isPermanentlyDenied
import dev.readout.app.util.openNotificationSettings
import dev.readout.app.widget.WidgetPinning

/**
 * Settings is a list of decisions, not a form. Each section holds one kind of choice, rows carry
 * their own state, and the printer's fields live in a sheet so the screen stays scannable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: PrinterConfig?,
    instances: List<PrinterConfig>,
    activeId: String?,
    theme: ThemePreference,
    dynamicColor: Boolean,
    notificationsEnabled: Boolean,
    onTheme: (ThemePreference) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSwitchInstance: (String) -> Unit,
    onRemoveInstance: (PrinterConfig) -> Boolean,
    onAddAnother: () -> Unit,
    onNameChange: (String) -> Unit,
    onWebcamChange: (String?) -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(config?.name) { mutableStateOf(config?.name.orEmpty()) }
    var webcam by remember(config?.webcamUrl) { mutableStateOf(config?.webcamUrl.orEmpty()) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var editingPrinter by remember { mutableStateOf(false) }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        when {
            granted -> onNotificationsChange(true)
            // "Don't allow" is now permanent: the switch can only be turned on from system settings.
            context.isPermanentlyDenied(Manifest.permission.POST_NOTIFICATIONS) -> context.openNotificationSettings()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l)
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(Spacing.xs))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        // -- Appearance ---------------------------------------------------------------------
        SectionHeading("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ThemePreference.entries.forEach { option ->
                FilterChip(
                    selected = theme == option,
                    onClick = { onTheme(option) },
                    label = {
                        Text(
                            when (option) {
                                ThemePreference.System -> "System"
                                ThemePreference.Light -> "Light"
                                ThemePreference.Dark -> "Dark"
                            }
                        )
                    }
                )
            }
        }
        SettingRow(
            title = "Use wallpaper colours",
            supporting = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "Take the palette from your wallpaper instead of Readout's own colours."
            } else {
                "Requires Android 12 or newer."
            },
            onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ({ onDynamicColor(!dynamicColor) })
            } else {
                null
            },
            trailing = {
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = null,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                )
            }
        )

        // -- Printer -------------------------------------------------------------------------
        SectionHeading("Printer")
        SettingRow(
            title = config?.name ?: "No printer connected",
            supporting = config?.displayHost,
            onClick = if (config == null) null else ({ editingPrinter = true }),
            trailing = if (config == null) null else ({
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            })
        )

        // The list only earns its space when there is something to switch between.
        if (instances.size > 1) {
            Spacer(Modifier.height(Spacing.s))
            Plate(Modifier.fillMaxWidth()) {
                Column {
                    instances.forEach { instance ->
                        val active = instance.id == activeId
                        key(instance.id) {
                            SwipeToRemove(label = "Remove", onRemove = { onRemoveInstance(instance) }) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !active) { onSwitchInstance(instance.id) }
                                        .heightIn(min = 56.dp)
                                        .padding(vertical = Spacing.s),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = instance.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = instance.displayHost,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (active) "Active" else "Use",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (active) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                        RowDivider()
                    }
                }
            }
        }

        SettingRow(
            title = "Add another printer",
            trailing = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = onAddAnother
        )
        if (config != null) {
            Spacer(Modifier.height(Spacing.xs))
            RowDivider()
            Spacer(Modifier.height(Spacing.xs))
            TextButton(
                onClick = { confirmDisconnect = true },
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text("Forget this printer", color = MaterialTheme.colorScheme.error)
            }
        }

        // -- Notifications -------------------------------------------------------------------
        SectionHeading("Notifications")
        SettingRow(
            title = "Live print notification",
            supporting = "Progress and a pause action while printing, plus one alert when it ends.",
            onClick = {
                val target = !notificationsEnabled
                when {
                    !target -> onNotificationsChange(false)
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED -> onNotificationsChange(true)
                    else -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            trailing = {
                Switch(checked = notificationsEnabled, onCheckedChange = null)
            }
        )

        // -- Home screen ---------------------------------------------------------------------
        SectionHeading("Home screen")
        Text(
            text = "Pin a widget without leaving the app. Widgets keep themselves current and show " +
                "the machine's state even when Readout is closed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PinButton("Print", Modifier.weight(1f)) {
                pinMessage = pinResult(WidgetPinning.print(context))
            }
            PinButton("Temps", Modifier.weight(1f)) {
                pinMessage = pinResult(WidgetPinning.temps(context))
            }
            PinButton("Camera", Modifier.weight(1f)) {
                pinMessage = pinResult(WidgetPinning.camera(context))
            }
        }
        if (!WidgetPinning.supported(context)) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "This launcher does not support pinning; add the widgets from its widget picker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        pinMessage?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // -- About ---------------------------------------------------------------------------
        SectionHeading("About")
        Text(
            text = "Readout 1.0",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = "An independent OctoPrint client. Not affiliated with OctoPrint or its authors.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.m))
        RowDivider()
        Spacer(Modifier.height(Spacing.m))
        Text(
            text = "No analytics, no accounts, no network calls except to the printer you configured.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xxl))
    }

    if (editingPrinter && config != null) {
        PrinterDetailsSheet(
            config = config,
            name = name,
            webcam = webcam,
            onName = { name = it },
            onWebcam = { webcam = it },
            onSave = {
                onNameChange(name.trim())
                onWebcamChange(webcam.trim().ifBlank { null })
                editingPrinter = false
            },
            onDismiss = { editingPrinter = false }
        )
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Forget this printer?") },
            text = { Text("The address and API key are deleted from this device. Your OctoPrint settings are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    onDisconnect()
                }) { Text("Forget", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Keep") }
            }
        )
    }
}

/**
 * Name, camera and connection in one place: the things that describe *this* printer, separate from
 * the settings that describe the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterDetailsSheet(
    config: PrinterConfig,
    name: String,
    webcam: String,
    onName: (String) -> Unit,
    onWebcam: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = Spacing.l)
                .padding(bottom = Spacing.xxl)
        ) {
            Text("Printer details", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.l))
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
            Spacer(Modifier.height(Spacing.s))
            OutlinedTextField(
                value = webcam,
                onValueChange = onWebcam,
                label = { Text("Camera snapshot URL") },
                placeholder = { Text("http://octopi.local/webcam/?action=snapshot") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Leave blank to use /webcam/?action=snapshot on the printer address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.l))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onSave,
                    enabled = name.trim().isNotEmpty(),
                    modifier = Modifier.height(48.dp)
                ) { Text("Save changes") }
            }
            Spacer(Modifier.height(Spacing.l))
            RowDivider()
            SpecRow(null, "Address", config.displayHost)
            SpecRow(
                null,
                "Over transport",
                if (config.baseUrl.startsWith("https")) "HTTPS" else "HTTP"
            )
            SpecRow(null, "Key", "•••• " + config.apiKey.takeLast(4))
        }
    }
}

/**
 * One row, one decision: a title, an optional explanation, and whatever control changes it. Fixed
 * height and rhythm keep a settings list readable when the rows differ in kind.
 */
@Composable
private fun SettingRow(
    title: String,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.m))
            trailing()
        }
    }
}

@Composable
private fun PinButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun pinResult(accepted: Boolean): String =
    if (accepted) "Pin requested — confirm it on your home screen."
    else "This launcher did not accept the pin request."
