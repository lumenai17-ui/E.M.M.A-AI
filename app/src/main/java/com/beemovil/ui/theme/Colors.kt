package com.beemovil.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Bee-Movil Design System — Color Palette
 * 
 * Honeycomb-inspired premium palette.
 * Two themes: Dark (default) and Light.
 */

// ── Brand Colors ──────────────────────────────
val HoneyGold       = Color(0xFFF5A623)   // Primary brand — honey gold
val HoneyAmber      = Color(0xFFD4850A)   // Darker gold for light theme
val HoneyLight      = Color(0xFFFFD54F)   // Light gold highlight
val HoneyGlow       = Color(0xFFFFF8E1)   // Ultra-light gold for glows

// ── Dark Theme ────────────────────────────────
val DarkBackground  = Color(0xFF0D0D0F)   // Deep black
val DarkSurface     = Color(0xFF1A1A2E)   // Navy dark
val DarkCard        = Color(0xFF16213E)   // Card/container
val DarkElevated    = Color(0xFF1C1C2E)   // Elevated surface
val DarkBorder      = Color(0xFF2E2E42)   // Subtle borders

// ── Light Theme ───────────────────────────────
val LightBackground = Color(0xFFF8F9FA)   // Clean white
val LightSurface    = Color(0xFFFFFFFF)   // Pure white
val LightCard       = Color(0xFFF0F0F5)   // Light gray cards
val LightElevated   = Color(0xFFE8E8F0)   // Elevated
val LightBorder     = Color(0xFFD8D8E0)   // Subtle borders

// ── Text Colors ───────────────────────────────
val TextWhite       = Color(0xFFEAEAEA)   // Primary text (dark theme)
val TextGrayLight   = Color(0xFFAAAAAA)   // Secondary text (dark theme)
val TextGrayMuted   = Color(0xFF777790)   // Tertiary/muted (dark theme)
val TextDark        = Color(0xFF1A1A1A)   // Primary text (light theme)
val TextGrayDark    = Color(0xFF666680)   // Secondary text (light theme)
val TextGrayDarker  = Color(0xFF999AAA)   // Tertiary (light theme)

// ── Accent Colors ─────────────────────────────
val AccentViolet    = Color(0xFF7C4DFF)   // Secondary brand
val AccentCyan      = Color(0xFF00E5FF)   // Bright cyan
val AccentGreen     = Color(0xFF00E676)   // Success / Active
val AccentRed       = Color(0xFFFF5252)   // Error / Danger
val AccentBlue      = Color(0xFF0A84FF)   // Links / Info
val AccentPurple    = Color(0xFFBF5AF2)   // Creative / Premium
val AccentOrange    = Color(0xFFFF9500)   // Warnings
val AccentPink      = Color(0xFFFF2D55)   // Alerts
val AccentTeal      = Color(0xFF5AC8FA)   // Subtle accent

// ── Chat Bubble Colors ────────────────────────
// Dark theme
val UserBubbleDark      = Color(0xFF2A2A40)
val AssistantBubbleDark = Color(0xFF1A1A2E)
val ErrorBubbleDark     = Color(0xFF3E1A1A)
// Light theme
val UserBubbleLight     = Color(0xFFE8E8F0)
val AssistantBubbleLight = Color(0xFFFFFFFF)
val ErrorBubbleLight    = Color(0xFFFFE8E8)

// ── Agent Gradient Backgrounds ────────────────
val AgentGoldStart   = Color(0xFF242010)
val AgentGoldEnd     = Color(0xFF1C1C2E)
val AgentGreenStart  = Color(0xFF102418)
val AgentGreenEnd    = Color(0xFF1C1C2E)
val AgentPinkStart   = Color(0xFF241014)
val AgentPinkEnd     = Color(0xFF1C1C2E)
val AgentBlueStart   = Color(0xFF101C24)
val AgentBlueEnd     = Color(0xFF1C1C2E)
val AgentPurpleStart = Color(0xFF1C1024)
val AgentPurpleEnd   = Color(0xFF1C1C2E)

// Legacy aliases for backward compatibility
val BeeYellow       = HoneyGold
val BeeYellowDark   = HoneyAmber
val BeeYellowLight  = HoneyLight
val BeeBlack        = DarkBackground
val BeeBlackLight   = DarkSurface
val BeeGray         = DarkCard
val BeeGrayLight    = Color(0xFF424769)
val BeeWhite        = TextWhite
val BeeGreen        = AccentGreen
val BeeRed          = AccentRed
val BeeBlue         = AccentBlue

// Legacy chat bubble aliases
val UserBubble      = UserBubbleDark
val AssistantBubble = AssistantBubbleDark
val ErrorBubble     = ErrorBubbleDark
