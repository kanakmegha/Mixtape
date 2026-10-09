package com.example.privatemusiclibrary.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MixtapeDarkColorScheme = darkColorScheme(
    primary = MixtapeVioletPrimary,
    onPrimary = Color.White,
    primaryContainer = MixtapeVioletContainer,
    onPrimaryContainer = MixtapeOnVioletContainer,

    secondary = MixtapeLimeAccent,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = MixtapeLimeContainer,
    onSecondaryContainer = MixtapeOnLimeContainer,

    tertiary = MixtapeCyanTertiary,
    onTertiary = Color(0xFF0F172A),

    background = MixtapeBackgroundDark,
    onBackground = MixtapeTextPrimary,

    surface = MixtapeSurfaceDark,
    onSurface = MixtapeTextPrimary,
    surfaceVariant = MixtapeSurfaceVariantDark,
    onSurfaceVariant = MixtapeTextSecondary,

    outline = MixtapeOutlineDark,
    outlineVariant = MixtapeOutlineVariantDark,

    error = MixtapeErrorDark,
    onError = Color.White,
    errorContainer = MixtapeErrorContainerDark,
    onErrorContainer = MixtapeOnErrorContainerDark
)

private val MixtapeLightColorScheme = lightColorScheme(
    primary = MixtapeVioletDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF4C1D95),

    secondary = Color(0xFF65A30D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECFCCB),
    onSecondaryContainer = Color(0xFF1A2E05),

    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,

    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),

    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),

    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),

    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun PrivateMusicLibraryTheme(
    darkTheme: Boolean = true, // Premium music apps default to rich dark theme
    dynamicColor: Boolean = false, // Keep consistent Mixtape brand identity
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MixtapeDarkColorScheme else MixtapeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
