package dev.readout.app.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * Fallback scheme for watches without dynamic colour: the phone app's cerulean seed (#00658F)
 * at dark tones, so the two surfaces read as one product.
 */
private val ReadoutWearColors = ColorScheme(
    primary = Color(0xFF85CFFF),
    primaryDim = Color(0xFF6BB8E8),
    primaryContainer = Color(0xFF004C6C),
    onPrimary = Color(0xFF00344C),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB6C9D8),
    secondaryDim = Color(0xFF9FB2C1),
    secondaryContainer = Color(0xFF374955),
    onSecondary = Color(0xFF21323E),
    onSecondaryContainer = Color(0xFFD2E5F5),
    tertiary = Color(0xFFCDC0E9),
    tertiaryDim = Color(0xFFB5A9D1),
    tertiaryContainer = Color(0xFF4B4263),
    onTertiary = Color(0xFF342B4B),
    onTertiaryContainer = Color(0xFFE9DDFF),
    surfaceContainerLow = Color(0xFF181C1F),
    surfaceContainer = Color(0xFF1C2023),
    surfaceContainerHigh = Color(0xFF262A2E),
    onSurface = Color(0xFFDFE3E7),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
    background = Color(0xFF000000),
    onBackground = Color(0xFFDFE3E7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/** Wear Material 3 theme: the watch's dynamic colours when it offers them, else the seed. */
@Composable
fun ReadoutWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dynamicColorScheme(LocalContext.current) ?: ReadoutWearColors,
        content = content
    )
}
