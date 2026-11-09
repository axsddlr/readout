package dev.readout.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback scheme for devices without dynamic colour (Android 11 and below, or when the user
 * turns wallpaper colour off). Tonal palettes derived from a single cerulean seed, #00658F.
 *
 * On Android 12+ the wallpaper-derived scheme replaces this one. Machine state is mapped to
 * scheme roles (see [ReadoutPalette]), so it reads correctly under either scheme; only the
 * connection dot is fixed, because "the link is healthy" must never be re-tinted.
 */

val ReadoutLightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    inversePrimary = Color(0xFF85CFFF),

    secondary = Color(0xFF4F616E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E5F5),
    onSecondaryContainer = Color(0xFF0B1D29),

    tertiary = Color(0xFF63597C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF1F1635),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF6FAFE),
    onBackground = Color(0xFF181C1F),
    surface = Color(0xFFF6FAFE),
    onSurface = Color(0xFF181C1F),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceTint = Color(0xFF00658F),
    inverseSurface = Color(0xFF2D3134),
    inverseOnSurface = Color(0xFFEEF1F5),

    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    scrim = Color(0xFF000000),

    surfaceDim = Color(0xFFD6DADF),
    surfaceBright = Color(0xFFF6FAFE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE4E8EC),
    surfaceContainerHighest = Color(0xFFDFE3E7)
)

val ReadoutDarkColors = darkColorScheme(
    primary = Color(0xFF85CFFF),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6C),
    onPrimaryContainer = Color(0xFFC7E7FF),
    inversePrimary = Color(0xFF00658F),

    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF21323E),
    secondaryContainer = Color(0xFF374955),
    onSecondaryContainer = Color(0xFFD2E5F5),

    tertiary = Color(0xFFCDC0E9),
    onTertiary = Color(0xFF342B4B),
    tertiaryContainer = Color(0xFF4B4263),
    onTertiaryContainer = Color(0xFFE9DDFF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceTint = Color(0xFF85CFFF),
    inverseSurface = Color(0xFFDFE3E7),
    inverseOnSurface = Color(0xFF2D3134),

    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
    scrim = Color(0xFF000000),

    surfaceDim = Color(0xFF0F1417),
    surfaceBright = Color(0xFF353A3D),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF181C1F),
    surfaceContainer = Color(0xFF1C2023),
    surfaceContainerHigh = Color(0xFF262A2E),
    surfaceContainerHighest = Color(0xFF313539)
)

/** The only colour kept outside the scheme: a healthy link is always the same green. */
object StateColors {
    val ReadyLight = Color(0xFF2E7D4F)
    val ReadyDark = Color(0xFF7ED6A0)
}
