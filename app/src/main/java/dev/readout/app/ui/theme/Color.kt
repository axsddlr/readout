package dev.readout.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * "Service manual" world.
 *
 * Paper and graphite stock, one signal colour (a machine-plate yellow), rust for work in
 * progress, teal for ready, red for fault. Colour is reserved for machine state and for
 * the single primary action; everything else is ink on paper.
 */

// -- Stock -------------------------------------------------------------------------------------

private val Paper = Color(0xFFF7F2E7)
private val PaperRaised = Color(0xFFFBF7EE)
private val Newsprint = Color(0xFFEDE5D6)
private val Ink = Color(0xFF1E1A16)
private val Graphite = Color(0xFF131110)
private val GraphiteRaised = Color(0xFF191614)
private val GraphiteCard = Color(0xFF211D1A)

// -- Signal ------------------------------------------------------------------------------------

private val PlateYellow = Color(0xFFFFD43B)
private val PlateYellowInk = Color(0xFF241C00)
private val Rust = Color(0xFFB0411B)
private val Teal = Color(0xFF0E6E68)
private val TealLight = Color(0xFF7ADBD3)
private val Alarm = Color(0xFFBA1A1A)
private val AlarmDark = Color(0xFFFFB4AB)

val ReadoutLightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFFDF7EA),
    primaryContainer = PlateYellow,
    onPrimaryContainer = PlateYellowInk,
    inversePrimary = Color(0xFFE8C547),

    secondary = Color(0xFF5D5347),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADFCB),
    onSecondaryContainer = Ink,

    tertiary = Teal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA9F2EA),
    onTertiaryContainer = Color(0xFF00201E),

    error = Alarm,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = Newsprint,
    onSurfaceVariant = Color(0xFF4E463A),
    surfaceTint = Ink,
    inverseSurface = Color(0xFF342F2A),
    inverseOnSurface = Color(0xFFF8EFE3),

    outline = Color(0xFF7F7465),
    outlineVariant = Color(0xFFD6CBBA),
    scrim = Color.Black,

    surfaceDim = Color(0xFFE3DAC9),
    surfaceBright = Color(0xFFFFFCF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4EEE2),
    surfaceContainer = Color(0xFFEFE8DA),
    surfaceContainerHigh = Color(0xFFE9E1D2),
    surfaceContainerHighest = Color(0xFFE3DAC9)
)

val ReadoutDarkColors = darkColorScheme(
    primary = Color(0xFFF3EADA),
    onPrimary = Color(0xFF241F19),
    primaryContainer = PlateYellow,
    onPrimaryContainer = PlateYellowInk,
    inversePrimary = Color(0xFF6E5E00),

    secondary = Color(0xFFD3C4AE),
    onSecondary = Color(0xFF38301F),
    secondaryContainer = Color(0xFF3B342A),
    onSecondaryContainer = Color(0xFFEFE1CC),

    tertiary = TealLight,
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF00504B),
    onTertiaryContainer = Color(0xFF9FF2EA),

    error = AlarmDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Graphite,
    onBackground = Color(0xFFEEE6DA),
    surface = GraphiteRaised,
    onSurface = Color(0xFFEEE6DA),
    surfaceVariant = Color(0xFF2A2621),
    onSurfaceVariant = Color(0xFFD3C7B6),
    surfaceTint = Color(0xFFF3EADA),
    inverseSurface = Color(0xFFEEE6DA),
    inverseOnSurface = Color(0xFF322E29),

    outline = Color(0xFF9B8F7E),
    outlineVariant = Color(0xFF4A443A),
    scrim = Color.Black,

    surfaceDim = Color(0xFF131110),
    surfaceBright = Color(0xFF3A3530),
    surfaceContainerLowest = Color(0xFF0E0C0B),
    surfaceContainerLow = Color(0xFF1B1816),
    surfaceContainer = GraphiteCard,
    surfaceContainerHigh = Color(0xFF2B2723),
    surfaceContainerHighest = Color(0xFF36312C)
)

/** Machine-state accents. Deliberately outside the M3 scheme: state must not be re-tinted by wallpaper. */
object MachineColors {
    val WorkLight = Rust
    val WorkDark = Color(0xFFFF9B6B)
    val ReadyLight = Teal
    val ReadyDark = TealLight
    val Plate = PlateYellow
    val OnPlate = PlateYellowInk
}
