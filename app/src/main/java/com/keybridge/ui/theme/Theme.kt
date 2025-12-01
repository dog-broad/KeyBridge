package com.keybridge.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Distinctive cyan-teal accent for a modern keyboard app aesthetic
private val KeyboardCyan = Color(0xFF00BCD4)
private val KeyboardCyanDark = Color(0xFF00ACC1)
private val KeyboardTeal = Color(0xFF009688)
private val KeyboardTealDark = Color(0xFF00897B)

private val DarkColorScheme = darkColorScheme(
    primary = KeyboardCyanDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D56),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = KeyboardTealDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = AccentPurple,
    onTertiary = Color.White,
    error = ErrorDark,
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = ErrorDark,
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    inverseOnSurface = Color(0xFF0D1117),
    inverseSurface = Color(0xFFE6EDF3),
    inversePrimary = KeyboardCyan,
    surfaceTint = KeyboardCyanDark,
    outlineVariant = Color(0xFF30363D),
    scrim = Color.Black,
)

private val LightColorScheme = lightColorScheme(
    primary = KeyboardCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF006064),
    secondary = KeyboardTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = AccentPurple,
    onTertiary = Color.White,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF24292F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF24292F),
    surfaceVariant = Color(0xFFEFF2F5),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
    inverseOnSurface = Color(0xFFF6F8FA),
    inverseSurface = Color(0xFF24292F),
    inversePrimary = Color(0xFF80DEEA),
    surfaceTint = KeyboardCyan,
    outlineVariant = Color(0xFFD0D7DE),
    scrim = Color.Black,
)

@Composable
fun KeyBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamicColor to false by default to use our custom theme
    dynamicColor: Boolean = false,
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
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
} 