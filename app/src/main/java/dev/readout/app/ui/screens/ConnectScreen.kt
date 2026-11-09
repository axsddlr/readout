package dev.readout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.readout.app.data.AppKeyState
import dev.readout.app.data.CommandResult
import dev.readout.app.data.DiscoveredPrinter
import dev.readout.app.ui.components.Plate
import dev.readout.app.ui.components.SectionHeading
import dev.readout.app.ui.theme.LocalPalette
import dev.readout.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * First run. A drawing-sheet title block, the machines found on this network, and one
 * honest form. No account, no tour, no permissions theatre.
 */
@Composable
fun ConnectScreen(
    discovered: List<DiscoveredPrinter>,
    discovering: Boolean,
    serverMessage: String?,
    existingPrinterName: String? = null,
    onResumeExisting: () -> Unit = {},
    onDiscover: () -> Unit,
    onConnect: suspend (String, String) -> CommandResult,
    appKeyState: AppKeyState = AppKeyState.Idle,
    onRequestAccess: (String) -> Unit = {},
    onCancelAppKey: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl)
    ) {
        if (existingPrinterName != null) {
            TextButton(
                onClick = onResumeExisting,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text("← Back to $existingPrinterName", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(Spacing.m))
        }

        // -- Title block -------------------------------------------------------------------
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "READOUT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    text = "Your printer,\non the record.",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.m))
                Text(
                    text = "A local control surface for one OctoPrint machine. No account, no cloud, " +
                        "no telemetry — your key never leaves this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Spacing.l))
            Box(
                Modifier
                    .size(56.dp)
                    .background(palette.plate, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "01",
                    style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 1.sp),
                    color = palette.onPlate
                )
            }
        }

        SectionHeading("01", "On this network") {
            TextButton(onClick = onDiscover) {
                Text("SCAN", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (discovering) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.s)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(Spacing.m))
                Text(
                    "Listening for OctoPrint…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (discovered.isEmpty() && !discovering) {
            Text(
                text = serverMessage
                    ?: "Nothing advertised itself. Start the scan again, or type the address below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.s)
            )
        }

        discovered.forEach { printer ->
            Plate(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = printer.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${printer.host}:${printer.port}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { address = printer.url },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text("USE") }
                }
            }
        }

        SectionHeading("02", "Address and key")

        OutlinedTextField(
            value = address,
            onValueChange = { address = it; failure = null },
            label = { Text("Printer address") },
            placeholder = { Text("octopi.local or 192.168.1.42:5000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(Modifier.height(Spacing.m))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; failure = null },
            label = { Text("API key") },
            placeholder = { Text("A1B2C3…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = "OctoPrint → Settings → API Key. Stored in this app's private storage.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        failure?.let {
            Spacer(Modifier.height(Spacing.m))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = {
                scope.launch {
                    submitting = true
                    failure = null
                    when (val result = onConnect(address.trim(), key.trim())) {
                        is CommandResult.Ok -> Unit
                        is CommandResult.Failed -> failure = result.message
                    }
                    submitting = false
                }
            },
            enabled = !submitting && address.isNotBlank() && key.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(4.dp)
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(Spacing.m))
            }
            Text(if (submitting) "CHECKING" else "CONNECT", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(Spacing.m))
        when (val state = appKeyState) {
            AppKeyState.Idle -> {
                OutlinedButton(
                    onClick = { onRequestAccess(address.trim()) },
                    enabled = address.isNotBlank() && !submitting,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("REQUEST ACCESS WITH APP KEY", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "For printers with two-factor login. OctoPrint asks an operator to approve " +
                        "this app once; no key is copied by hand.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AppKeyState.Waiting -> {
                Plate(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "WAITING FOR APPROVAL",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "Open OctoPrint → Settings → Application Keys and allow " +
                                "“Readout”. This screen updates on its own.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.s))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(Spacing.m))
                            TextButton(onClick = onCancelAppKey) { Text("CANCEL") }
                        }
                    }
                }
            }

            AppKeyState.Unavailable -> Text(
                text = "The App Keys plugin is not installed on this printer. Use an API key instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            is AppKeyState.Failed -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onCancelAppKey) { Text("TRY AGAIN") }
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.m))
        Text(
            text = "Readout speaks the OctoPrint REST API directly over your network. " +
                "Plain HTTP and self-signed certificates are both accepted because that is " +
                "how printers on a workbench usually behave.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xxl))
    }
}
