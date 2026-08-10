package com.storagerush.app.ui.theme

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
    primary = StorageRushColors.DarkPrimary,
    onPrimary = StorageRushColors.DarkOnPrimary,
    primaryContainer = StorageRushColors.DarkPrimaryContainer,
    onPrimaryContainer = StorageRushColors.DarkOnPrimaryContainer,
    inversePrimary = StorageRushColors.DarkPrimaryContainer,
    secondary = StorageRushColors.DarkSecondary,
    onSecondary = StorageRushColors.DarkOnSecondary,
    secondaryContainer = StorageRushColors.DarkSecondaryContainer,
    onSecondaryContainer = StorageRushColors.DarkOnSecondaryContainer,
    tertiary = StorageRushColors.DarkTertiary,
    onTertiary = StorageRushColors.DarkOnTertiary,
    tertiaryContainer = StorageRushColors.DarkTertiaryContainer,
    onTertiaryContainer = StorageRushColors.DarkOnTertiaryContainer,
    error = ErrorRed,
    onError = NeutralLight,
    errorContainer = Color(0xFF8C1F1F),
    onErrorContainer = SecondaryRedLight,
    background = StorageRushColors.DarkBackground,
    onBackground = StorageRushColors.DarkOnBackground,
    surface = StorageRushColors.DarkSurface,
    onSurface = StorageRushColors.DarkOnSurface,
    surfaceVariant = StorageRushColors.DarkSurfaceVariant,
    onSurfaceVariant = StorageRushColors.DarkOnSurfaceVariant,
    outline = StorageRushColors.DarkOutline,
    outlineVariant = Color(0xFF424A54),
    scrim = Color.Black,
    inverseSurface = Color(0xFFF1F3F4),
    inverseOnSurface = Color(0xFF1F2937),
    surfaceTint = StorageRushColors.DarkPrimary
)

private val LightColorScheme = ColorScheme(
    primary = StorageRushColors.LightPrimary,
    onPrimary = StorageRushColors.LightOnPrimary,
    primaryContainer = StorageRushColors.LightPrimaryContainer,
    onPrimaryContainer = StorageRushColors.LightOnPrimaryContainer,
    inversePrimary = PrimaryGreenLight,
    secondary = StorageRushColors.LightSecondary,
    onSecondary = StorageRushColors.LightOnSecondary,
    secondaryContainer = StorageRushColors.LightSecondaryContainer,
    onSecondaryContainer = StorageRushColors.LightOnSecondaryContainer,
    tertiary = StorageRushColors.LightTertiary,
    onTertiary = StorageRushColors.LightOnTertiary,
    tertiaryContainer = StorageRushColors.LightTertiaryContainer,
    onTertiaryContainer = StorageRushColors.LightOnTertiaryContainer,
    error = ErrorRed,
    onError = NeutralLightCard,
    errorContainer = Color(0xFFF8D7D6),
    onErrorContainer = Color(0xFF5E1B1B),
    background = StorageRushColors.LightBackground,
    onBackground = StorageRushColors.LightOnBackground,
    surface = StorageRushColors.LightSurface,
    onSurface = StorageRushColors.LightOnSurface,
    surfaceVariant = StorageRushColors.LightSurfaceVariant,
    onSurfaceVariant = StorageRushColors.LightOnSurfaceVariant,
    outline = StorageRushColors.LightOutline,
    outlineVariant = Color(0xFFC7C7D0),
    scrim = Color.Black,
    inverseSurface = Color(0xFF1F2937),
    inverseOnSurface = Color(0xFFF9FAFB),
    surfaceTint = StorageRushColors.LightPrimary
)

@Composable
fun StorageRushTheme(
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
        typography = StorageRushTypography,
        content = content
    )
}