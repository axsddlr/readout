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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.readout.app.ui.components.DashedRule
import dev.readout.app.ui.components.Plate
import dev.readout.app.ui.components.SectionHeading
import dev.readout.app.ui.components.SpecRow
import dev.readout.app.ui.theme.Spacing
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
        if (granted) onNotificationsChange(true)
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
            Text("SETTINGS", style = MaterialTheme.typography.labelMedium)
        }

        SectionHeading("01", "Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ThemePreference.entries.forEach { option ->
                FilterChip(
                    selected = theme == option,
                    onClick = { onTheme(option) },
                    label = {
                        Text(
                            when (option) {
                                ThemePreference.System -> "SYSTEM"
                                ThemePreference.Light -> "LIGHT"
                                ThemePreference.Dark -> "DARK"
                            }
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Match my wallpaper", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Use the system palette instead of the service-manual colours."
                    } else {
                        "Requires Android 12 or newer."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = dynamicColor,
                onCheckedChange = onDynamicColor,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )
        }

        SectionHeading("02", "Printer")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(Modifier.height(Spacing.s))
        Button(
            onClick = { onNameChange(name.trim()) },
            enabled = name.trim().isNotEmpty() && name.trim() != config?.name,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(4.dp)
        ) { Text("SAVE NAME") }

        Spacer(Modifier.height(Spacing.m))
        OutlinedTextField(
            value = webcam,
            onValueChange = { webcam = it },
            label = { Text("Camera snapshot URL") },
            placeholder = { Text("http://octopi.local/webcam/?action=snapshot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(Modifier.height(Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Button(
                onClick = { onWebcamChange(webcam.trim().ifBlank { null }) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(4.dp)
            ) { Text("SAVE URL") }
            OutlinedButton(
                onClick = { webcam = ""; onWebcamChange(null) },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(4.dp)
            ) { Text("RESET") }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Leave blank to use /webcam/?action=snapshot on the printer address.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.l))
        Plate(Modifier.fillMaxWidth()) {
            Column {
                instances.forEach { instance ->
                    val active = instance.id == activeId
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
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (active) "ACTIVE" else "USE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    DashedRule()
                }
                Spacer(Modifier.height(Spacing.s))
                SpecRow(null, "Address", config?.displayHost ?: "—")
                SpecRow(null, "Over transport", if (config?.baseUrl?.startsWith("https") == true) "HTTPS" else "HTTP")
                SpecRow(null, "Key", if (config == null) "—" else "•••• " + (config.apiKey.takeLast(4)))
                Spacer(Modifier.height(Spacing.s))
                TextButton(
                    onClick = onAddAnother,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text("ADD ANOTHER PRINTER")
                }
                TextButton(
                    onClick = { confirmDisconnect = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text("FORGET THIS PRINTER", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        SectionHeading("03", "Notifications")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live print notification", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "An ongoing notification with progress and a pause action while a job runs, " +
                        "plus one alert when it finishes or stops. Also keeps home-screen widgets current.",
                    style = MaterialTheme.typography.labelSmall,
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

        SectionHeading("04", "Home screen")
        Text(
            text = "Pin a widget without leaving the app. Widgets keep themselves current and show " +
                "the machine's state even when Readout is closed.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PinButton("PRINT", Modifier.weight(1f)) {
                pinMessage = pinResult(WidgetPinning.print(context))
            }
            PinButton("TEMPS", Modifier.weight(1f)) {
                pinMessage = pinResult(WidgetPinning.temps(context))
            }
            PinButton("CAMERA", Modifier.weight(1f)) {
                pinMessage = pinResult(WidgetPinning.camera(context))
            }
        }
        if (!WidgetPinning.supported(context)) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "This launcher does not support pinning; add the widgets from its widget picker.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        pinMessage?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionHeading("05", "About")
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
        DashedRule()
        Spacer(Modifier.height(Spacing.m))
        Text(
            text = "No analytics, no accounts, no network calls except to the printer you configured.",
            style = MaterialTheme.typography.labelSmall,
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
                }) { Text("FORGET", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("KEEP") }
            }
        )
    }
}

@Composable
private fun PinButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun pinResult(accepted: Boolean): String =
    if (accepted) "Pin requested — confirm it on your home screen."
    else "This launcher did not accept the pin request."
