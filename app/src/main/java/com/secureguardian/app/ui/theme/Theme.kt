package com.secureguardian.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Brand Colors
val DeepNavy = Color(0xFF1A1A2E)
val DeepNavyVariant = Color(0xFF16213E)
val AccentRed = Color(0xFFE94560)
val AccentRedDark = Color(0xFFB83150)
val SafeGreen = Color(0xFF0F9B58)
val SafeGreenDark = Color(0xFF0A7342)
val SuspiciousOrange = Color(0xFFFF8C00)
val SuspiciousOrangeDark = Color(0xFFCC7000)
val PureWhite = Color(0xFFFFFFFF)
val SurfaceLight = Color(0xFFF5F5F5)
val SurfaceDark = Color(0xFF0D0D1A)
val CardLight = Color(0xFFFFFFFF)
val CardDark = Color(0xFF1E1E30)
val TextPrimary = Color(0xFF1A1A2E)
val TextSecondary = Color(0xFF666680)
val TextPrimaryDark = Color(0xFFF0F0FF)
val TextSecondaryDark = Color(0xFF9090A8)
val DividerLight = Color(0xFFE0E0F0)
val DividerDark = Color(0xFF2A2A3E)

private val DarkColorScheme = darkColorScheme(
    primary = AccentRed,
    onPrimary = PureWhite,
    primaryContainer = AccentRedDark,
    onPrimaryContainer = PureWhite,
    secondary = SafeGreen,
    onSecondary = PureWhite,
    secondaryContainer = SafeGreenDark,
    onSecondaryContainer = PureWhite,
    tertiary = SuspiciousOrange,
    onTertiary = PureWhite,
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF252535),
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark,
    error = AccentRed,
    onError = PureWhite,
)

private val LightColorScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = PureWhite,
    primaryContainer = Color(0xFF2A2A5E),
    onPrimaryContainer = PureWhite,
    secondary = SafeGreen,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFD0F0E0),
    onSecondaryContainer = Color(0xFF003820),
    tertiary = SuspiciousOrange,
    onTertiary = PureWhite,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = CardLight,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEEEEF8),
    onSurfaceVariant = TextSecondary,
    outline = DividerLight,
    error = AccentRed,
    onError = PureWhite,
)

@Composable
fun SecureSMSGuardianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled to maintain brand colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
