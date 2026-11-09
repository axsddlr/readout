package dev.readout.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.readout.app.data.MachineState

enum class ThemeMode { System, Light, Dark }

/** A container colour and the content colour that sits on it. */
@Immutable
data class Tone(val container: Color, val content: Color)

/**
 * Colours the app needs beyond Material's roles. State maps onto scheme roles so it follows
 * the wallpaper; only [ready] is fixed.
 */
@Immutable
data class ReadoutPalette(
    val ready: Color,
    val working: Tone,
    val paused: Tone,
    val fault: Tone,
    val idle: Tone
) {
    /** The tone for a machine state. State is always also written as text. */
    fun forState(state: MachineState?): Tone = when (state) {
        MachineState.Printing, MachineState.Resuming, MachineState.Finishing,
        MachineState.Cancelling -> working
        MachineState.Paused, MachineState.Pausing -> paused
        MachineState.Error, MachineState.Disconnected -> fault
        else -> idle
    }

    companion object {
        fun from(scheme: ColorScheme, dark: Boolean) = ReadoutPalette(
            ready = if (dark) StateColors.ReadyDark else StateColors.ReadyLight,
            working = Tone(scheme.primaryContainer, scheme.onPrimaryContainer),
            paused = Tone(scheme.tertiaryContainer, scheme.onTertiaryContainer),
            fault = Tone(scheme.errorContainer, scheme.onErrorContainer),
            idle = Tone(scheme.surfaceContainerHigh, scheme.onSurface)
        )
    }
}

val LocalPalette = staticCompositionLocalOf { ReadoutPalette.from(ReadoutLightColors, dark = false) }

@Composable
fun ReadoutTheme(
    mode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> ReadoutDarkColors
        else -> ReadoutLightColors
    }

    CompositionLocalProvider(LocalPalette provides ReadoutPalette.from(scheme, dark)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ReadoutTypography,
            shapes = ReadoutShapes,
            content = content
        )
    }
}
