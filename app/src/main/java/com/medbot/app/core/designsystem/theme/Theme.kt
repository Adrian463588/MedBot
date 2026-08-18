package com.medbot.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape

private val DarkColorScheme = darkColorScheme(
    primary = MedicalTealLight,
    onPrimary = MedicalEmeraldDark,
    primaryContainer = MedicalEmeraldDark,
    onPrimaryContainer = MedicalMint,
    secondary = MedicalMintDark,
    onSecondary = MedicalEmeraldDark,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = Color(0xFFD7B9E8),
    onTertiary = Color(0xFF382044),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark,
    outlineVariant = DividerDark,
    error = UrgencyEmergencyRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = MedicalEmerald,
    onPrimary = Color.White,
    primaryContainer = MedicalMint,
    onPrimaryContainer = MedicalEmeraldDark,
    secondary = MedicalTeal,
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = Color(0xFF6C4A7A),
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight,
    outlineVariant = DividerLight,
    error = UrgencyEmergencyRed,
    onError = Color.White
)

@Composable
fun MedBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = MedBotTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8),
            small = RoundedCornerShape(12),
            medium = RoundedCornerShape(16),
            large = RoundedCornerShape(20),
            extraLarge = RoundedCornerShape(28)
        ),
        content = content
    )
}
