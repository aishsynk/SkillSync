package com.koenig.skilledge.core.theme

import androidx.compose.ui.graphics.Color

// Primary Teal - System backbone
val TealPrimary = Color(0xFF0D8B8B)
val TealLight = Color(0xFF1BA69B)
val TealDark = Color(0xFF066E6E)

// Secondary Accent (Warm Amber)
val AmberSecondary = Color(0xFFD97706)
val AmberLight = Color(0xFFF59C1A)
val AmberDark = Color(0xFFB45309)

// Neutral Colors
val SlateText = Color(0xFF475569)
val SlateTextLight = Color(0xFF64748B)
val SlateTextLighter = Color(0xFF94A3B8)

// Background Colors
val BgLight = Color(0xFFF8FAFC)
val BgLightCard = Color(0xFFFFFFFF)
val BgLightBorder = Color(0xFFE2E8F0)

val BgDark = Color(0xFF0F172A)
val BgDarkCard = Color(0xFF1E293B)
val BgDarkBorder = Color(0xFF334155)

// Semantic Colors
val SuccessGreen = Color(0xFF10B981)
val WarningYellow = Color(0xFFF59C1A)
val ErrorRed = Color(0xFFEF4444)
val InfoBlue = Color(0xFF3B82F6)

// Status Specific
val StatusTeachingNow = TealPrimary
val StatusPreparing = Color(0xFF6366F1) // Indigo
val StatusScheduledToday = InfoBlue
val StatusFree = SuccessGreen
val StatusBlocked = ErrorRed
val StatusUnknown = Color(0xFFB6C2C9)

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
val TransparentTeal10 = TealPrimary.copy(alpha = 0.1f)
val TransparentAmber10 = AmberSecondary.copy(alpha = 0.1f)
val TransparentError10 = ErrorRed.copy(alpha = 0.1f)
