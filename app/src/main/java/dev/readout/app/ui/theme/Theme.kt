package dev.readout.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { System, Light, Dark }

/** Colours the app needs that Material's scheme cannot express without being re-tinted. */
data class ReadoutPalette(
    val plate: Color,
    val onPlate: Color,
    val work: Color,
    val ready: Color,
    val hairline: Color
)

val LocalPalette = staticCompositionLocalOf {
    ReadoutPalette(Color(0xFFFFD43B), Color(0xFF241C00), Color(0xFFB0411B), Color(0xFF0E6E68), Color(0xFFD6CBBA))
}

@Composable
fun ReadoutTheme(
    mode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
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

    val palette = ReadoutPalette(
        plate = MachineColors.Plate,
        onPlate = MachineColors.OnPlate,
        work = if (dark) MachineColors.WorkDark else MachineColors.WorkLight,
        ready = if (dark) MachineColors.ReadyDark else MachineColors.ReadyLight,
        hairline = scheme.outlineVariant
    )

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ReadoutTypography,
            shapes = ReadoutShapes,
            content = content
        )
    }
}
