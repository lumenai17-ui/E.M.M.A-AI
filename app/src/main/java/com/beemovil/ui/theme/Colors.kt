package com.beemovil.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Bee-Movil Design System — Color Palette
 * 
 * Two themes: Dark (honeycomb) and Light (HelloEmma brand blue/green).
 */

// ── Brand Colors ──────────────────────────────
val HoneyGold       = Color(0xFFF5A623)   // Primary brand — honey gold (dark theme)
val HoneyAmber      = Color(0xFFD4850A)   // Darker gold
val HoneyLight      = Color(0xFFFFD54F)   // Light gold highlight
val HoneyGlow       = Color(0xFFFFF8E1)   // Ultra-light gold for glows

// ── Brand Colors (Light theme) ────────────────
val BrandBlue       = Color(0xFF4A6FA5)   // Primary blue (light theme accent)
val BrandBlueDark   = Color(0xFF3A5A8A)   // Darker blue for press states
val BrandBlueLight  = Color(0xFFD4E0F0)   // Light blue tint
val BrandGreen      = Color(0xFF5AAE7E)   // Primary green (light theme secondary)
val BrandGreenDark  = Color(0xFF4A9A6E)   // Darker green for press states
val BrandGreenLight = Color(0xFFC8E2CE)   // Light green (user bubbles light theme)

// ── Dark Theme ────────────────────────────────
val DarkBackground  = Color(0xFF0D0D0F)   // Deep black
val DarkSurface     = Color(0xFF1A1A2E)   // Navy dark
val DarkCard        = Color(0xFF16213E)   // Card/container
val DarkElevated    = Color(0xFF1C1C2E)   // Elevated surface
val DarkBorder      = Color(0xFF2E2E42)   // Subtle borders

// ── Light Theme ───────────────────────────────
val LightBackground = Color(0xFFF5F0E8)   // Warm cream
val LightSurface    = Color(0xFFFFFFFF)   // Pure white (cards)
val LightCard       = Color(0xFFF0EDE6)   // Cream soft
val LightElevated   = Color(0xFFE8E4DD)   // Beige soft
val LightBorder     = Color(0xFFD8D4CC)   // Cream border

// ── Text Colors ───────────────────────────────
val TextWhite       = Color(0xFFEAEAEA)   // Primary text (dark theme)
val TextGrayLight   = Color(0xFFAAAAAA)   // Secondary text (dark theme)
val TextGrayMuted   = Color(0xFF777790)   // Tertiary/muted (dark theme)
val TextDark        = Color(0xFF1A2B4A)   // Primary text (light theme) — navy
val TextGrayDark    = Color(0xFF6B7B8A)   // Secondary text (light theme)
val TextGrayDarker  = Color(0xFF9BA5AE)   // Tertiary (light theme)

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
val UserBubbleLight     = BrandGreenLight        // Soft green
val AssistantBubbleLight = Color(0xFFFFFFFF)      // White
val ErrorBubbleLight    = Color(0xFFFFE8E8)       // Soft red

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

// Light theme agent gradients
val AgentGoldStartLight   = Color(0xFFFFF8E1)
val AgentGoldEndLight     = Color(0xFFF5F0E8)
val AgentGreenStartLight  = Color(0xFFE8F5E9)
val AgentGreenEndLight    = Color(0xFFF5F0E8)
val AgentPinkStartLight   = Color(0xFFFFE8E8)
val AgentPinkEndLight     = Color(0xFFF5F0E8)
val AgentBlueStartLight   = Color(0xFFE3F2FD)
val AgentBlueEndLight     = Color(0xFFF5F0E8)
val AgentPurpleStartLight = Color(0xFFF3E5F5)
val AgentPurpleEndLight   = Color(0xFFF5F0E8)

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

/**
 * Theme-aware color helper.
 * Use: val isDark = isDarkTheme() at the top of each Composable,
 * then use `if (isDark) darkColor else lightColor` patterns.
 */
@Composable
fun isDarkTheme(): Boolean {
    return BeeThemeState.forceDark.value
        ?: androidx.compose.foundation.isSystemInDarkTheme()
}

/**
 * Theme-aware color tokens.
 * Each function returns the correct color based on the active theme.
 */
@Composable fun themeBackground() = if (isDarkTheme()) DarkBackground else LightBackground
@Composable fun themeSurface() = if (isDarkTheme()) DarkSurface else LightSurface
@Composable fun themeCard() = if (isDarkTheme()) DarkCard else LightCard
@Composable fun themeElevated() = if (isDarkTheme()) DarkElevated else LightElevated
@Composable fun themeBorder() = if (isDarkTheme()) DarkBorder else LightBorder
@Composable fun themeTextPrimary() = if (isDarkTheme()) TextWhite else TextDark
@Composable fun themeTextSecondary() = if (isDarkTheme()) TextGrayLight else TextGrayDark
@Composable fun themeTextMuted() = if (isDarkTheme()) TextGrayMuted else TextGrayDarker
@Composable fun themeAccent() = if (isDarkTheme()) HoneyGold else BrandBlue
@Composable fun themeAccentSecondary() = if (isDarkTheme()) HoneyLight else BrandGreen
@Composable fun themeUserBubble() = if (isDarkTheme()) UserBubbleDark else UserBubbleLight
@Composable fun themeAssistantBubble() = if (isDarkTheme()) AssistantBubbleDark else AssistantBubbleLight
@Composable fun themeInputBackground() = if (isDarkTheme()) DarkElevated else LightSurface
@Composable fun themeNavBar() = if (isDarkTheme()) DarkBackground else LightSurface
@Composable fun themeTopBar() = if (isDarkTheme()) DarkSurface else LightSurface
