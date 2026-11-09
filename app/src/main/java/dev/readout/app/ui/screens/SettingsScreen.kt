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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import dev.readout.app.util.openNotificationSettings
import dev.readout.app.util.isPermanentlyDenied
import dev.readout.app.widget.WidgetPinning

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
        Spacer(Modifier.height(Spacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use wallpaper colours", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Use the system palette instead of the Readout colours."
                    } else {
                        "Requires Android 12 or newer."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Spacing.l))
            Switch(
                checked = dynamicColor,
                onCheckedChange = onDynamicColor,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )
        }

        SectionHeading("Printer")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        )
        Spacer(Modifier.height(Spacing.s))
        Button(
            onClick = { onNameChange(name.trim()) },
            enabled = name.trim().isNotEmpty() && name.trim() != config?.name,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Save name") }

        Spacer(Modifier.height(Spacing.m))
        OutlinedTextField(
            value = webcam,
            onValueChange = { webcam = it },
            label = { Text("Camera snapshot URL") },
            placeholder = { Text("http://octopi.local/webcam/?action=snapshot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        )
        Spacer(Modifier.height(Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Button(
                onClick = { onWebcamChange(webcam.trim().ifBlank { null }) },
                modifier = Modifier.weight(1f).height(48.dp)
            ) { Text("Save URL") }
            OutlinedButton(
                onClick = { webcam = ""; onWebcamChange(null) },
                modifier = Modifier.height(48.dp)
            ) { Text("Reset") }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Leave blank to use /webcam/?action=snapshot on the printer address.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.l))
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
                SpecRow(null, "Address", config?.displayHost ?: "—")
                SpecRow(null, "Over transport", if (config?.baseUrl?.startsWith("https") == true) "HTTPS" else "HTTP")
                SpecRow(null, "Key", if (config == null) "—" else "•••• " + (config.apiKey.takeLast(4)))
                Spacer(Modifier.height(Spacing.s))
                TextButton(
                    onClick = onAddAnother,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text("Add another printer")
                }
                TextButton(
                    onClick = { confirmDisconnect = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text("Forget this printer", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        SectionHeading("Notifications")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live print notification", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "An ongoing notification with progress and a pause action while a job runs, " +
                        "plus one alert when it finishes or stops. Also keeps home-screen widgets current.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onNotificationsChange(false)
                    } else if (
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        onNotificationsChange(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }

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
