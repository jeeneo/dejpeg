/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class AppTheme { OLED, Dynamic, Light, Dark }

private val DefaultDark = darkColorScheme(
    primary = Color(0xFFFFB951),
    onPrimary = Color(0xFF3E2800),
    primaryContainer = Color(0xFF593C00),
    onPrimaryContainer = Color(0xFFFFDEA8),
    secondary = Color(0xFFD8C4A0),
    onSecondary = Color(0xFF3A2F16),
    secondaryContainer = Color(0xFF524529),
    onSecondaryContainer = Color(0xFFF5DFBA),
    tertiary = Color(0xFFB0CFA2),
    onTertiary = Color(0xFF1D3719),
    tertiaryContainer = Color(0xFF334E2E),
    onTertiaryContainer = Color(0xFFCCEBC0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17120B),
    onBackground = Color(0xFFEDE0CF),
    surface = Color(0xFF17120B),
    onSurface = Color(0xFFEDE0CF),
    surfaceVariant = Color(0xFF4E4539),
    onSurfaceVariant = Color(0xFFD2C4B2),
    outline = Color(0xFF9B8E7E),
    outlineVariant = Color(0xFF4E4539),
    surfaceContainer = Color(0xFF241E16),
    surfaceContainerLow = Color(0xFF1F1A12),
    surfaceContainerHigh = Color(0xFF2F281F),
    surfaceContainerHighest = Color(0xFF3B3329),
    inverseSurface = Color(0xFFEDE0CF),
    inverseOnSurface = Color(0xFF352F22),
    inversePrimary = Color(0xFF785200),
)

private val DefaultLight = lightColorScheme(
    primary = Color(0xFF785200),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA8),
    onPrimaryContainer = Color(0xFF271800),
    secondary = Color(0xFF6C5C3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5DFBA),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF4B6644),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCEBC0),
    onTertiaryContainer = Color(0xFF082108),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF1F1A10),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF1F1A10),
    surfaceVariant = Color(0xFFEFE0CC),
    onSurfaceVariant = Color(0xFF4E4539),
    outline = Color(0xFF807464),
    outlineVariant = Color(0xFFD2C4B2),
    surfaceContainer = Color(0xFFF4E8D5),
    surfaceContainerLow = Color(0xFFFAF0E0),
    surfaceContainerHigh = Color(0xFFEDE3CF),
    surfaceContainerHighest = Color(0xFFE6DBCA),
    inverseSurface = Color(0xFF352F22),
    inverseOnSurface = Color(0xFFFAEFDE),
    inversePrimary = Color(0xFFFFB951),
)

private val OledBlack = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFE0E0E0),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF303030),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFD0D0D0),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF1A1A1A),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFEAEAEA),
    outline = Color(0xFF6A6A6A),
    outlineVariant = Color(0xFF303030),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF242424),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF000000),
)

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Suppress("DEPRECATION")
@Composable
fun DeJPEGTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    oledTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        oledTheme -> OledBlack
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DefaultDark
        else -> DefaultLight
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
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content,
    )
}
