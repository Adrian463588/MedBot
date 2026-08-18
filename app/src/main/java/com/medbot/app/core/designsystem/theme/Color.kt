package com.medbot.app.core.designsystem.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary Medical Emerald & Mint Palette
val MedicalEmerald = Color(0xFF0D7C66)
val MedicalEmeraldDark = Color(0xFF064E3B)
val MedicalEmeraldLight = Color(0xFF10B981)

val MedicalMint = Color(0xFFD1FAE5)
val MedicalMintDark = Color(0xFFA7F3D0)
val MedicalTeal = Color(0xFF14B8A6)
val MedicalTealLight = Color(0xFF5EEAD4)

// Modern Gradient Brushes
val MedicalGradient = Brush.horizontalGradient(
    listOf(Color(0xFF0D7C66), Color(0xFF047857))
)

val CardGradient = Brush.verticalGradient(
    listOf(Color(0xFFFFFFFF), Color(0xFFF9FAFB))
)

val DarkCardGradient = Brush.verticalGradient(
    listOf(Color(0xFF1F2937), Color(0xFF111827))
)

// Accents & Urgency
val UrgencyLowGreen = Color(0xFF10B981)
val UrgencyMediumYellow = Color(0xFFF59E0B)
val UrgencyHighOrange = Color(0xFFF97316)
val UrgencyEmergencyRed = Color(0xFFEF4444)

// Neutral Background & Surfaces (Light)
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF64748B)
val DividerLight = Color(0xFFE2E8F0)

// Neutral Background & Surfaces (Dark)
val BackgroundDark = Color(0xFF0B1315)
val SurfaceDark = Color(0xFF142022)
val SurfaceVariantDark = Color(0xFF1C2D30)
val TextPrimaryDark = Color(0xFFF1F5F9)
val TextSecondaryDark = Color(0xFF94A3B8)
val DividerDark = Color(0xFF283B3E)
