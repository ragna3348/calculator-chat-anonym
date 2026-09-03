package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.utils.AppThemeMode

private val ElegantDarkColorScheme = darkColorScheme(
    primary = ElegantLavender,
    onPrimary = ElegantDeepPurple,
    primaryContainer = ElegantPrimaryContainer,
    onPrimaryContainer = ElegantOnPrimaryContainer,
    secondary = ElegantSecondary,
    onSecondary = ElegantOnSecondary,
    secondaryContainer = ElegantSecondaryContainer,
    onSecondaryContainer = ElegantOnSecondaryContainer,
    tertiary = ElegantTertiary,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = ElegantTertiaryContainer,
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = ElegantDarkBackground,
    onBackground = ElegantDarkTextPrimary,
    surface = ElegantDarkSurface,
    onSurface = ElegantDarkTextPrimary,
    surfaceVariant = ElegantDarkSurfaceVariant,
    onSurfaceVariant = ElegantDarkTextSecondary,
    outline = ElegantDarkBorder,
    outlineVariant = ElegantDarkOutlineVariant,
    error = ElegantError,
    onError = Color(0xFF601410)
)

private val OledColorScheme = darkColorScheme(
    primary = AmberOrange,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF332000),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = CyberGreen,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003824),
    onSecondaryContainer = Color(0xFF8CF4C8),
    tertiary = ElectricBlue,
    background = OledBackground,
    onBackground = OledTextPrimary,
    surface = OledSurface,
    onSurface = OledTextPrimary,
    surfaceVariant = OledSurfaceVariant,
    onSurfaceVariant = OledTextSecondary,
    outline = OledBorder
)

private val SlateColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF075985),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = AmberOrange,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF451A03),
    onSecondaryContainer = Color(0xFFFED7AA),
    tertiary = RoyalPurple,
    background = SlateBackground,
    onBackground = SlateTextPrimary,
    surface = SlateSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    outline = SlateBorder
)

private val CyberColorScheme = darkColorScheme(
    primary = CyberAccent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = AmberOrange,
    onSecondary = Color.Black,
    tertiary = ElectricBlue,
    background = CyberBackground,
    onBackground = Color(0xFFE6FFFA),
    surface = CyberSurface,
    onSurface = Color(0xFFE6FFFA),
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = Color(0xFF8EE3C1),
    outline = CyberBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFEA580C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = Color(0xFF6366F1),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun CalculatorVaultTheme(
    themeMode: AppThemeMode = AppThemeMode.ELEGANT_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.ELEGANT_DARK -> ElegantDarkColorScheme
        AppThemeMode.OLED_BLACK -> OledColorScheme
        AppThemeMode.STEALTH_SLATE -> SlateColorScheme
        AppThemeMode.CYBER_EMERALD -> CyberColorScheme
        AppThemeMode.MINIMAL_LIGHT -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
