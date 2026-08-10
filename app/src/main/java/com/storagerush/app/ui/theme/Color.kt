package com.storagerush.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============ STORAGE RUSH BRAND COLORS ============

// Primary: Emerald Green (Keep action)
val PrimaryGreen = Color(0xFF10B981)
val PrimaryGreenLight = Color(0xFF34D399)
val PrimaryGreenDark = Color(0xFF059669)

// Secondary: Coral/Red (Trash action)
val SecondaryRed = Color(0xFFF87171)
val SecondaryRedLight = Color(0xFFFCA5A5)
val SecondaryRedDark = Color(0xFFDC2626)

// Tertiary: Soft Blue (neutral accent, future use)
val TertiaryBlue = Color(0xFF3B82F6)
val TertiaryBlueDark = Color(0xFF1D4ED8)

// Neutral surfaces: Clean dark/light theme
val NeutralDark = Color(0xFF1F2937)      // Dark background
val NeutralDarkCard = Color(0xFF111827)  // Darker card overlay
val NeutralLight = Color(0xFFFAFAFA)     // Light background
val NeutralLightCard = Color(0xFFFFFFFF) // Light card overlay

// Text colors
val TextDark = Color(0xFF111827)         // Dark text on light bg
val TextLight = Color(0xFFF9FAFB)        // Light text on dark bg
val TextMuted = Color(0xFF6B7280)        // Muted/secondary text

// Status colors
val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFF87171)
val WarningYellow = Color(0xFFFB923C)
val InfoBlue = Color(0xFF3B82F6)

// Transparent overlays
val DarkOverlay = Color.Black.copy(alpha = 0.6f)
val LightOverlay = Color.White.copy(alpha = 0.8f)

// ============ MATERIAL 3 THEME COLORS ============

object StorageRushColors {
    // Light Theme
    val LightPrimary = PrimaryGreen
    val LightOnPrimary = Color.White
    val LightPrimaryContainer = PrimaryGreenLight
    val LightOnPrimaryContainer = Color(0xFF051B11)
    
    val LightSecondary = SecondaryRed
    val LightOnSecondary = Color.White
    val LightSecondaryContainer = SecondaryRedLight
    val LightOnSecondaryContainer = Color(0xFF3E0D07)
    
    val LightTertiary = TertiaryBlue
    val LightOnTertiary = Color.White
    val LightTertiaryContainer = Color(0xFFDEE8FF)
    val LightOnTertiaryContainer = Color(0xFF001B3E)
    
    val LightBackground = NeutralLight
    val LightOnBackground = TextDark
    val LightSurface = NeutralLightCard
    val LightOnSurface = TextDark
    val LightSurfaceVariant = Color(0xFFE5E7EB)
    val LightOnSurfaceVariant = Color(0xFF4B5563)
    val LightOutline = Color(0xFF79747E)
    
    // Dark Theme
    val DarkPrimary = PrimaryGreenLight
    val DarkOnPrimary = Color(0xFF051B11)
    val DarkPrimaryContainer = PrimaryGreenDark
    val DarkOnPrimaryContainer = PrimaryGreenLight
    
    val DarkSecondary = SecondaryRedLight
    val DarkOnSecondary = Color(0xFF3E0D07)
    val DarkSecondaryContainer = SecondaryRedDark
    val DarkOnSecondaryContainer = SecondaryRedLight
    
    val DarkTertiary = Color(0xFFB5D0FF)
    val DarkOnTertiary = Color(0xFF001B3E)
    val DarkTertiaryContainer = TertiaryBlueDark
    val DarkOnTertiaryContainer = Color(0xFFDEE8FF)
    
    val DarkBackground = NeutralDark
    val DarkOnBackground = TextLight
    val DarkSurface = NeutralDarkCard
    val DarkOnSurface = TextLight
    val DarkSurfaceVariant = Color(0xFF424A54)
    val DarkOnSurfaceVariant = Color(0xFFC7C7D0)
    val DarkOutline = Color(0xFF91919B)
}