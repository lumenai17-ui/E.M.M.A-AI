package com.beemovil.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Premium palette (keep in sync with DashboardScreen)
private val NavBg = Color(0xFF0A0A0C)
private val NavBorder = Color(0xFF1A1A24)
private val Gold = Color(0xFFF5A623)
private val GoldDim = Color(0xFFD4850A)
private val Muted = Color(0xFF555566)

data class NavItem(
    val screen: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
)

private val navItems = listOf(
    NavItem("dashboard", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem("conversations", "Agentes", Icons.Filled.Forum, Icons.Outlined.Forum),
    NavItem("email_inbox", "Correo", Icons.Filled.Email, Icons.Outlined.Email),
    NavItem("settings", "Config", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun PremiumBottomNav(
    currentScreen: String,
    onNavigate: (String) -> Unit
) {
    // Top edge line
    Surface(
        color = NavBg,
        shadowElevation = 0.dp
    ) {
        Column {
            // Golden accent line at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(NavBorder)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    val isSelected = currentScreen == item.screen
                    PremiumNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigate(item.screen) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumNavItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Gold else Muted,
        animationSpec = tween(250), label = "iconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Gold else Color.Transparent,
        animationSpec = tween(250), label = "textColor"
    )
    val bgAlpha by animateColorAsState(
        targetValue = if (isSelected) Gold.copy(alpha = 0.1f) else Color.Transparent,
        animationSpec = tween(250), label = "bgAlpha"
    )
    val pillWidth by animateDpAsState(
        targetValue = if (isSelected) 56.dp else 0.dp,
        animationSpec = tween(300), label = "pill"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        // Indicator pill above icon
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isSelected) Brush.horizontalGradient(listOf(GoldDim, Gold, GoldDim))
                    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                )
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Icon with background circle for selected
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(bgAlpha)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isSelected) item.iconSelected else item.iconUnselected,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        // Label (only visible when selected)
        if (isSelected) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                item.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold
            )
        }
    }
}
