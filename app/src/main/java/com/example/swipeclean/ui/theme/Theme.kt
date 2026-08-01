package com.example.swipeclean.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = ColorScheme(
    primary = SwipeCleanColors.DarkPrimary,
    onPrimary = SwipeCleanColors.DarkOnPrimary,
    primaryContainer = SwipeCleanColors.DarkPrimaryContainer,
    onPrimaryContainer = SwipeCleanColors.DarkOnPrimaryContainer,
    inversePrimary = SwipeCleanColors.DarkPrimaryContainer,
    secondary = SwipeCleanColors.DarkSecondary,
    onSecondary = SwipeCleanColors.DarkOnSecondary,
    secondaryContainer = SwipeCleanColors.DarkSecondaryContainer,
    onSecondaryContainer = SwipeCleanColors.DarkOnSecondaryContainer,
    tertiary = SwipeCleanColors.DarkTertiary,
    onTertiary = SwipeCleanColors.DarkOnTertiary,
    tertiaryContainer = SwipeCleanColors.DarkTertiaryContainer,
    onTertiaryContainer = SwipeCleanColors.DarkOnTertiaryContainer,
    error = ErrorRed,
    onError = NeutralLight,
    errorContainer = Color(0xFF8C1F1F),
    onErrorContainer = SecondaryRedLight,
    background = SwipeCleanColors.DarkBackground,
    onBackground = SwipeCleanColors.DarkOnBackground,
    surface = SwipeCleanColors.DarkSurface,
    onSurface = SwipeCleanColors.DarkOnSurface,
    surfaceVariant = SwipeCleanColors.DarkSurfaceVariant,
    onSurfaceVariant = SwipeCleanColors.DarkOnSurfaceVariant,
    outline = SwipeCleanColors.DarkOutline,
    outlineVariant = Color(0xFF424A54),
    scrim = Color.Black,
    inverseSurface = Color(0xFFF1F3F4),
    inverseOnSurface = Color(0xFF1F2937),
    surfaceTint = SwipeCleanColors.DarkPrimary
)

private val LightColorScheme = ColorScheme(
    primary = SwipeCleanColors.LightPrimary,
    onPrimary = SwipeCleanColors.LightOnPrimary,
    primaryContainer = SwipeCleanColors.LightPrimaryContainer,
    onPrimaryContainer = SwipeCleanColors.LightOnPrimaryContainer,
    inversePrimary = PrimaryGreenLight,
    secondary = SwipeCleanColors.LightSecondary,
    onSecondary = SwipeCleanColors.LightOnSecondary,
    secondaryContainer = SwipeCleanColors.LightSecondaryContainer,
    onSecondaryContainer = SwipeCleanColors.LightOnSecondaryContainer,
    tertiary = SwipeCleanColors.LightTertiary,
    onTertiary = SwipeCleanColors.LightOnTertiary,
    tertiaryContainer = SwipeCleanColors.LightTertiaryContainer,
    onTertiaryContainer = SwipeCleanColors.LightOnTertiaryContainer,
    error = ErrorRed,
    onError = NeutralLightCard,
    errorContainer = Color(0xFFF8D7D6),
    onErrorContainer = Color(0xFF5E1B1B),
    background = SwipeCleanColors.LightBackground,
    onBackground = SwipeCleanColors.LightOnBackground,
    surface = SwipeCleanColors.LightSurface,
    onSurface = SwipeCleanColors.LightOnSurface,
    surfaceVariant = SwipeCleanColors.LightSurfaceVariant,
    onSurfaceVariant = SwipeCleanColors.LightOnSurfaceVariant,
    outline = SwipeCleanColors.LightOutline,
    outlineVariant = Color(0xFFC7C7D0),
    scrim = Color.Black,
    inverseSurface = Color(0xFF1F2937),
    inverseOnSurface = Color(0xFFF9FAFB),
    surfaceTint = SwipeCleanColors.LightPrimary
)

@Composable
fun SwipeCleanTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SwipeCleanTypography,
        content = content
    )
}