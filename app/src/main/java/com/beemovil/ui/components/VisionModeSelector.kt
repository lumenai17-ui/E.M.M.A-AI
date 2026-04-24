package com.beemovil.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.vision.VisionMode

/**
 * VisionModeSelector — Phase V5: Los Modos
 *
 * DESIGN: No emojis — uses Material Icons per E.M.M.A. brand guidelines.
 */

data class ModeInfo(
    val mode: VisionMode,
    val icon: ImageVector,
    val label: String
)

val VISION_MODES = listOf(
    ModeInfo(VisionMode.GENERAL, Icons.Filled.Visibility, "General"),
    ModeInfo(VisionMode.DASHCAM, Icons.Filled.DirectionsCar, "Dash"),
    ModeInfo(VisionMode.TOURIST, Icons.Filled.Explore, "Tour"),
    ModeInfo(VisionMode.AGENT, Icons.Filled.Security, "Agent"),
    ModeInfo(VisionMode.MEETING, Icons.Filled.Groups, "Meet"),
    ModeInfo(VisionMode.SHOPPING, Icons.Filled.ShoppingCart, "Shop"),
    ModeInfo(VisionMode.POCKET, Icons.Filled.PhonelinkLock, "Pocket"),
    ModeInfo(VisionMode.TRANSLATOR, Icons.Filled.Translate, "Translate")
)

@Composable
fun VisionModeSelector(
    selectedMode: VisionMode,
    suggestedMode: VisionMode? = null,
    onModeChange: (VisionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VISION_MODES.forEach { info ->
            val isSelected = selectedMode == info.mode
            val isSuggested = suggestedMode == info.mode && !isSelected

            val bgColor by animateColorAsState(
                when {
                    isSelected -> Color.White.copy(alpha = 0.25f)
                    isSuggested -> Color(0xFFF5A623).copy(alpha = 0.15f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                label = "modeBg"
            )

            Surface(
                onClick = { onModeChange(info.mode) },
                color = bgColor,
                shape = RoundedCornerShape(16.dp),
                border = if (isSuggested) {
                    androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF5A623))
                } else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        info.icon, info.label,
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        info.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Suggest a vision mode based on GPS speed.
 */
fun suggestModeBySpeed(speedKmh: Float): VisionMode? = when {
    speedKmh > 15 -> VisionMode.DASHCAM
    speedKmh > 2 -> VisionMode.TOURIST
    else -> null // Don't suggest anything when stationary
}
