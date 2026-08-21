package com.miolauncher.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MioGreen = Color(0xFF4CAF50)
val MioGreenDark = Color(0xFF2E7D32)
val MioDirtBrown = Color(0xFF6B4F2A)
val MioStone = Color(0xFF7A7A7A)
val MioGrass = Color(0xFF68B75C)
val MioNight = Color(0xFF1A1C21)
val MioNightLight = Color(0xFF252830)
val MioPanel = Color(0xFF2E3138)
val MioText = Color(0xFFE8EAED)
val MioTextDim = Color(0xFF9AA0A6)
val MioAccent = Color(0xFF7CB342)

private val DarkColors = darkColorScheme(
    primary = MioGreen,
    onPrimary = Color.White,
    secondary = MioAccent,
    background = MioNight,
    onBackground = MioText,
    surface = MioPanel,
    onSurface = MioText,
    surfaceVariant = MioNightLight,
    onSurfaceVariant = MioTextDim,
    primaryContainer = MioGreenDark,
    onPrimaryContainer = Color.White,
)

private val LightColors = lightColorScheme(
    primary = MioGreenDark,
    onPrimary = Color.White,
    secondary = MioAccent,
    background = Color(0xFFF2F3F5),
    onBackground = Color(0xFF1C1D21),
    surface = Color.White,
    onSurface = Color(0xFF1C1D21),
    surfaceVariant = Color(0xFFE4E6E8),
    onSurfaceVariant = Color(0xFF5F6368),
    primaryContainer = MioGreen,
    onPrimaryContainer = Color.White,
)

@Composable
fun MioLauncherTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MioTypography,
        content = content,
    )
}
