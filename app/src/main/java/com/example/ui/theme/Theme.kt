package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SunsetOrangeDark,
    onPrimary = Color(0xFF2B1103),
    primaryContainer = SunsetOrangeDarkContainer,
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = SkyBlueDark,
    onSecondary = Color(0xFF00253B),
    secondaryContainer = SkyBlueDarkContainer,
    onSecondaryContainer = Color(0xFFCDE9FE),
    tertiary = EmeraldLiveDark,
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = EmeraldLiveDarkContainer,
    onTertiaryContainer = Color(0xFFBBF7D0),
    background = DarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB4BECE),
    outline = DarkOutline,
    outlineVariant = Color(0xFF283040),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = SunsetOrange,
    onPrimary = Color.White,
    primaryContainer = SunsetOrangeContainer,
    onPrimaryContainer = OnSunsetOrangeContainer,
    secondary = SkyBlue,
    onSecondary = Color.White,
    secondaryContainer = SkyBlueContainer,
    onSecondaryContainer = OnSkyBlueContainer,
    tertiary = EmeraldLive,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldLiveContainer,
    onTertiaryContainer = Color(0xFF14532D),
    background = WarmAlabaster,
    onBackground = SlateInk,
    surface = WarmSurface,
    onSurface = SlateInk,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = SlateInkSoft,
    outline = WarmOutline,
    outlineVariant = Color(0xFFE7E2D9),
    error = SundayCoral,
    onError = Color.White
)

@Composable
fun LhlTimetableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
