package com.koenig.skilledge.core.theme

import androidx.compose.ui.graphics.Color

// SkillEdge Enterprise Brand System
val NavyPrimary = Color(0xFF0A1128)
val NavyCard = Color(0xFF1E293B)
val CyanAccent = Color(0xFF0D8B8B)
val CyanLight = Color(0xFF14B8A6)
val ElectricBlue = Color(0xFF2563EB)
val GlassBorder = Color(0xFF334155)

// Primary Teal - System backbone
val TealPrimary = Color(0xFF0D8B8B)
val TealLight = Color(0xFF1BA69B)
val TealDark = Color(0xFF066E6E)

// Secondary Accent (Warm Amber)
val AmberSecondary = Color(0xFFD97706)
val AmberLight = Color(0xFFF59C1A)
val AmberDark = Color(0xFFB45309)

// Neutral Colors
val SlateText = Color(0xFF94A3B8)
val SlateTextLight = Color(0xFFCBD5E1)
val SlateTextLighter = Color(0xFFE2E8F0)

// Background Colors
val BgLight = Color(0xFF0A1128)
val BgLightCard = Color(0xFF1E293B)
val BgLightBorder = Color(0xFF334155)

val BgDark = Color(0xFF0A1128)
val BgDarkCard = Color(0xFF1E293B)
val BgDarkBorder = Color(0xFF334155)

// Semantic Colors
val SuccessGreen = Color(0xFF10B981)
val WarningYellow = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)
val InfoBlue = Color(0xFF3B82F6)

// Status Specific
val StatusTeachingNow = CyanAccent
val StatusPreparing = Color(0xFF6366F1) // Indigo
val StatusScheduledToday = InfoBlue
val StatusFree = SuccessGreen
val StatusBlocked = ErrorRed
val StatusUnknown = Color(0xFF64748B)

// Readiness Levels
val ReadinessReady = SuccessGreen
val ReadinessPrep = WarningYellow
val ReadinessBlocked = ErrorRed
val ReadinessUnknown = Color(0xFF9CA3AF)

// Feedback Risk
val RiskLow = SuccessGreen
val RiskMedium = WarningYellow
val RiskHigh = ErrorRed

// Transparent variants
val TransparentTeal10 = TealPrimary.copy(alpha = 0.15f)
val TransparentAmber10 = AmberSecondary.copy(alpha = 0.15f)
val TransparentError10 = ErrorRed.copy(alpha = 0.15f)
val TransparentBlue10 = ElectricBlue.copy(alpha = 0.15f)
