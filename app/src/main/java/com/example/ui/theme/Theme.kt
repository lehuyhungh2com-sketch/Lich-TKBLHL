package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.data.local.AppAccentPalette
import com.example.data.local.AppDensityMode
import com.example.data.local.AppFontScale

val LocalCompactDensity = compositionLocalOf { false }

private fun buildDarkColorScheme(palette: AppAccentPalette): ColorScheme {
    val (primary, onPrimary, primaryContainer, onPrimaryContainer) = when (palette) {
        AppAccentPalette.EMERALD -> Quad(EmeraldAccentDark, Color(0xFF022C22), EmeraldAccentDarkContainer, Color(0xFFA7F3D0))
        AppAccentPalette.OCEAN_BLUE -> Quad(OceanAccentDark, Color(0xFF082F49), OceanAccentDarkContainer, Color(0xFFBAE6FD))
        AppAccentPalette.ROYAL_INDIGO -> Quad(IndigoAccentDark, Color(0xFF1E1B4B), IndigoAccentDarkContainer, Color(0xFFE0E7FF))
        else -> Quad(SunsetOrangeDark, Color(0xFF2B1103), SunsetOrangeDarkContainer, Color(0xFFFFDCC2))
    }

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
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
}

private fun buildLightColorScheme(palette: AppAccentPalette): ColorScheme {
    val (primary, onPrimary, primaryContainer, onPrimaryContainer) = when (palette) {
        AppAccentPalette.EMERALD -> Quad(EmeraldAccent, Color.White, EmeraldAccentContainer, OnEmeraldAccentContainer)
        AppAccentPalette.OCEAN_BLUE -> Quad(OceanAccent, Color.White, OceanAccentContainer, OnOceanAccentContainer)
        AppAccentPalette.ROYAL_INDIGO -> Quad(IndigoAccent, Color.White, IndigoAccentContainer, OnIndigoAccentContainer)
        else -> Quad(SunsetOrange, Color.White, SunsetOrangeContainer, OnSunsetOrangeContainer)
    }

    val background = when (palette) {
        AppAccentPalette.EMERALD -> Color(0xFFF3F8F5)
        AppAccentPalette.OCEAN_BLUE -> Color(0xFFF2F6FA)
        AppAccentPalette.ROYAL_INDIGO -> Color(0xFFF4F4FA)
        else -> WarmAlabaster
    }

    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = SkyBlue,
        onSecondary = Color.White,
        secondaryContainer = SkyBlueContainer,
        onSecondaryContainer = OnSkyBlueContainer,
        tertiary = EmeraldLive,
        onTertiary = Color.White,
        tertiaryContainer = EmeraldLiveContainer,
        onTertiaryContainer = Color(0xFF14532D),
        background = background,
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
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun LhlTimetableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentPalette: AppAccentPalette = AppAccentPalette.SUNSET_ORANGE,
    fontScale: AppFontScale = AppFontScale.NORMAL,
    densityMode: AppDensityMode = AppDensityMode.COMFORTABLE,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        accentPalette == AppAccentPalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> buildDarkColorScheme(accentPalette)
        else -> buildLightColorScheme(accentPalette)
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = (baseDensity.fontScale * fontScale.multiplier).coerceIn(0.85f, 1.65f)
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalCompactDensity provides (densityMode == AppDensityMode.COMPACT)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
