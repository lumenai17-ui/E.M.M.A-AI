package com.beemovil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.beemovil.R
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ChatViewModel,
    onAgentClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
    ) {
        val dynamicState = viewModel.dynamicDashboardState.value

        TopAppBar(
            title = {
                Text("Hola, ${dynamicState.greetingName}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = BeeWhite)
            },
            actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, "Config", tint = BeeGray)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Novelty Core (Cápsulas Inteligentes)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmartCapsule(dynamicState.capsuleNetBattery, dynamicState.isMatrixLoading)
            SmartCapsule(dynamicState.capsuleLocation, dynamicState.isMatrixLoading)
            SmartCapsule(dynamicState.capsuleWeather, dynamicState.isMatrixLoading)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Deep-Insight Card
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            color = Color(0xFF151520),
            border = BorderStroke(1.dp, BeeYellow.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Row(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Filled.AutoAwesome, 
                    "AI", 
                    tint = BeeYellow.copy(alpha = if (dynamicState.isMatrixLoading) alpha else 1f),
                    modifier = Modifier.size(24.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = dynamicState.insightText,
                    color = BeeWhite.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.dashboard_section_access), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BeeWhite, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Main Chat
            MainModuleCard(
                title = stringResource(R.string.dash_card_emma_title),
                subtitle = stringResource(R.string.dash_card_emma_subtitle),
                icon = Icons.Filled.Chat,
                onClick = { onAgentClick("main") }
            )
            // Vision
            MainModuleCard(
                title = stringResource(R.string.dash_card_vision_title),
                subtitle = stringResource(R.string.dash_card_vision_subtitle),
                icon = Icons.Filled.Videocam,
                onClick = { viewModel.currentScreen.value = "live_vision" }
            )
            // Voice
            MainModuleCard(
                title = stringResource(R.string.dash_card_voice_title),
                subtitle = stringResource(R.string.dash_card_voice_subtitle),
                icon = Icons.Filled.Mic,
                onClick = { viewModel.currentScreen.value = "voice" }
            )
            // Notifications
            MainModuleCard(
                title = stringResource(R.string.dash_card_ops_title),
                subtitle = stringResource(R.string.dash_card_ops_subtitle),
                icon = Icons.Filled.Inbox,
                onClick = { viewModel.currentScreen.value = "notifications" }
            )
        }
    }
}

@Composable
fun MainModuleCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(0xFF161622),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(80.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(BeeYellow.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = BeeYellow)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = BeeWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = BeeGray, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowForwardIos, "Go", tint = BeeGray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SmartCapsule(text: String, isLoading: Boolean) {
    Surface(
        color = BeeBlack.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_capsule")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        Text(
            text = if (isLoading) "Recopilando..." else text,
            color = if (isLoading) BeeWhite.copy(alpha = alpha) else BeeYellow,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
