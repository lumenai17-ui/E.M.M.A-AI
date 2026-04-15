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
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.items
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
    val isDark = isDarkTheme()
    val bg = if (isDark) BeeBlack else LightBackground
    val textPrimary = if (isDark) BeeWhite else TextDark
    val textSecondary = if (isDark) TextGrayLight else TextGrayDark
    val textMuted = if (isDark) BeeGray else TextGrayDarker
    val accent = if (isDark) BeeYellow else BrandBlue
    val cardBg = if (isDark) Color(0xFF161622) else LightSurface
    val insightBg = if (isDark) Color(0xFF151520) else LightSurface
    val insightBorder = if (isDark) BeeYellow.copy(alpha = 0.3f) else LightBorder
    val settingsIcon = if (isDark) BeeGray else TextGrayDark

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
    ) {
        val dynamicState = viewModel.dynamicDashboardState.value

        // BUG-12 fix: Refrescar datos cada vez que se muestra el Dashboard
        androidx.compose.runtime.LaunchedEffect(Unit) {
            viewModel.refreshLiveDashboard()
        }

        TopAppBar(
            title = {
                Text("Hola, ${dynamicState.greetingName}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
            },
            actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, "Config", tint = settingsIcon)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Novelty Core (Cápsulas Inteligentes Formato Fijo/Apilado)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(140.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { MapRadarWidget(dynamicState.capsuleLocation, dynamicState.isMatrixLoading) }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { ThermalWeatherWidget(dynamicState.capsuleWeather, dynamicState.isMatrixLoading) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Capsula Cognitiva Central (8-Layer Insight)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).animateContentSize(),
            color = insightBg,
            border = BorderStroke(1.dp, insightBorder),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = if (isDark) 0.dp else 2.dp
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

            Column(modifier = Modifier.padding(16.dp)) {
                // Header (Nativo de Telemetría)
                Text(
                    text = dynamicState.insightHeaderTop,
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dynamicState.insightHeaderBottom,
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.Divider(color = if (isDark) Color.DarkGray.copy(alpha = 0.4f) else LightBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row {
                    Icon(
                        Icons.Filled.AutoAwesome, 
                        "AI", 
                        tint = accent.copy(alpha = if (dynamicState.isMatrixLoading) alpha else 1f),
                        modifier = Modifier.size(24.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = dynamicState.insightText,
                        color = textPrimary.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f).heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.dashboard_section_access), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MainModuleCard(
                title = stringResource(R.string.dash_card_emma_title),
                subtitle = stringResource(R.string.dash_card_emma_subtitle),
                icon = Icons.Filled.Chat,
                onClick = { onAgentClick("main") }
            )
            MainModuleCard(
                title = stringResource(R.string.dash_card_vision_title),
                subtitle = stringResource(R.string.dash_card_vision_subtitle),
                icon = Icons.Filled.Videocam,
                onClick = { viewModel.currentScreen.value = "live_vision" }
            )
            MainModuleCard(
                title = stringResource(R.string.dash_card_voice_title),
                subtitle = stringResource(R.string.dash_card_voice_subtitle),
                icon = Icons.Filled.Mic,
                onClick = { viewModel.currentScreen.value = "voice" }
            )
            MainModuleCard(
                title = "Tareas",
                subtitle = "Tus pendientes de Google Tasks",
                icon = Icons.Filled.TaskAlt,
                onClick = { viewModel.currentScreen.value = "tasks" }
            )
            MainModuleCard(
                title = "Correo",
                subtitle = "Tu bandeja de Gmail",
                icon = Icons.Filled.Email,
                onClick = { viewModel.currentScreen.value = "email_inbox" }
            )

            val recientes = viewModel.allThreads
                .filter { it.threadId != "main" && it.threadId != "default_session" }
                .sortedByDescending { it.lastUpdateMillis }
                .take(3)

            if (recientes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "CHATS RECIENTES",
                    fontSize = 12.sp,
                    color = textMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                
                recientes.forEach { thread ->
                    MainModuleCard(
                        title = thread.title,
                        subtitle = "Retomar conexión",
                        icon = Icons.Filled.Chat,
                        onClick = { onAgentClick(thread.threadId) }
                    )
                }
            }
        }
    }
}

@Composable
fun MainModuleCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val isDark = isDarkTheme()
    val cardBg = if (isDark) Color(0xFF161622) else LightSurface
    val accent = if (isDark) BeeYellow else BrandBlue
    val textPrimary = if (isDark) BeeWhite else TextDark
    val textSecondary = if (isDark) BeeGray else TextGrayDark
    val arrowColor = if (isDark) BeeGray else TextGrayDarker

    Surface(
        onClick = onClick,
        color = cardBg,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = if (isDark) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = textSecondary, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowForwardIos, "Go", tint = arrowColor, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun MapRadarWidget(text: String, isLoading: Boolean) {
    val isDark = isDarkTheme()
    val widgetBg = if (isDark) BeeBlack.copy(alpha = 0.4f) else LightSurface
    val widgetBorder = if (isDark) BeeYellow.copy(alpha = 0.2f) else LightBorder
    val radarColor = if (isDark) Color(0xFF4CAF50) else BrandGreen
    val sweepColor = if (isDark) BeeYellow else BrandGreen
    val accent = if (isDark) BeeYellow else BrandBlue
    val textPrimary = if (isDark) BeeWhite else TextDark

    Surface(
        color = widgetBg,
        border = BorderStroke(1.dp, widgetBorder),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (isDark) 0.dp else 2.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "radar")
        val sweepAngle by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "sweep"
        )
        
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                val radius = size.width.coerceAtMost(size.height) / 2.5f
                
                // Círculos concéntricos
                drawCircle(color = radarColor.copy(alpha = 0.15f), radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(color = radarColor.copy(alpha = 0.1f), radius = radius * 0.6f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(color = radarColor.copy(alpha = 0.05f), radius = radius * 0.2f, center = center)
                
                // Aguja Sweeper
                val sweepGradient = androidx.compose.ui.graphics.Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.8f to sweepColor.copy(alpha = 0.0f),
                    0.90f to sweepColor.copy(alpha = 0.2f),
                    1.0f to sweepColor.copy(alpha = 0.8f),
                    center = center
                )
                rotate(sweepAngle - 360f, center) {
                    drawArc(
                        brush = sweepGradient,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                }
                
                // Línea sólida de la aguja
                val endX = center.x + radius * kotlin.math.cos(Math.toRadians(sweepAngle.toDouble())).toFloat()
                val endY = center.y + radius * kotlin.math.sin(Math.toRadians(sweepAngle.toDouble())).toFloat()
                drawLine(
                    color = sweepColor,
                    start = center,
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("SCANNING...", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(2.dp))
                val cleanText = if (isLoading) "Calculando vector..." else text.replace("📍", "").trim()
                Text(cleanText, color = textPrimary, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ThermalWeatherWidget(text: String, isLoading: Boolean) {
    val isDark = isDarkTheme()
    val accent = if (isDark) BeeYellow else BrandBlue
    val textPrimary = if (isDark) BeeWhite else TextDark

    val infiniteTransition = rememberInfiniteTransition(label = "weather")
    val isSunny = text.contains("Sol", true) || text.contains("Despejado", true)
    val isRain = text.contains("Lluvia", true) || text.contains("Tormenta", true)
    
    val bgColor by infiniteTransition.animateColor(
        initialValue = if (isDark) BeeBlack.copy(alpha = 0.2f) else LightSurface,
        targetValue = if (isDark) BeeBlack.copy(alpha = 0.4f) else LightCard,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "weather_bg"
    )

    Surface(
        color = bgColor,
        border = BorderStroke(1.dp, if (isDark) {
            if(isSunny) BeeYellow.copy(alpha=0.3f) else Color.DarkGray.copy(alpha = 0.3f)
        } else LightBorder),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (isDark) 0.dp else 2.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        val sunnyImages = listOf(
            R.drawable.weather_sunny_6_1775956921605,
            R.drawable.weather_sunny_7_1775956934059
        )
        val rainImages = listOf(
            R.drawable.weather_rain_6_1775956974416,
            R.drawable.weather_rain_7_1775956987372
        )
        val cloudyImages = listOf(
            R.drawable.weather_cloudy_6_1775956946172,
            R.drawable.weather_cloudy_7_1775956960689
        )
        val chosenImage = androidx.compose.runtime.remember(isSunny, isRain) {
            val list = when {
                isSunny -> sunnyImages
                isRain -> rainImages
                else -> cloudyImages
            }
            list.random()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = chosenImage),
                contentDescription = "Estado del clima",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            Box(modifier = Modifier.fillMaxSize().background(
                if (isDark) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.3f)
            ))

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(90.dp)) {
                        drawArc(
                            color = Color.DarkGray.copy(alpha = 0.2f),
                            startAngle = 135f, sweepAngle = 270f, useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                        drawArc(
                            brush = androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color(0xFF2196F3), accent, Color(0xFFF44336), Color(0xFF2196F3))),
                            startAngle = 135f, sweepAngle = 270f, useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                        drawArc(
                            color = Color.White,
                            startAngle = 200f, sweepAngle = 5f, useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
                        )
                    }
                    val tempText = if (isLoading) "--" else {
                        val extracted = text.substringAfter("(", "").substringBefore(")")
                        if (extracted.isNotBlank()) extracted else "--°C"
                    }
                    Text(
                        text = tempText, 
                        color = Color.White, 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.ExtraBold,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 12f))
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                val cleanDesc = if (isLoading) "Sintetizando..." else text.substringAfter(" ").substringBefore("(").trim().take(22)
                Text(
                    text = cleanDesc, 
                    color = Color.White, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 8f))
                )
            }
        }
    }
}

@Composable
fun TelemetryHeartbeatWidget(text: String, isLoading: Boolean) {
    val isDark = isDarkTheme()
    val accent = if (isDark) BeeYellow else BrandGreen
    val textPrimary = if (isDark) BeeWhite else TextDark

    Surface(
        color = if (isDark) BeeBlack.copy(alpha = 0.4f) else LightSurface,
        border = BorderStroke(1.dp, if (isDark) BeeYellow.copy(alpha = 0.2f) else LightBorder),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (isDark) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth().height(80.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "wave")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "phase"
        )
        
        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                val path = androidx.compose.ui.graphics.Path()
                val width = size.width
                val height = size.height
                val midY = height * 0.4f
                
                path.moveTo(0f, midY)
                val stepSize = 4f
                for (x in 0..width.toInt() step stepSize.toInt()) {
                    val normalizedX = x / width
                    val diff = ((normalizedX - 0.5f) * 12f).toDouble()
                    val pulseEnvelope = kotlin.math.exp(-Math.pow(diff, 2.0)).toFloat()
                    val wave = kotlin.math.sin(normalizedX * 15f - phase)
                    
                    val y = midY + (wave * 25f * pulseEnvelope)
                    path.lineTo(x.toFloat(), y.toFloat())
                }
                
                drawPath(
                    path = path,
                    color = accent.copy(alpha = 0.8f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ElectricBolt, "Energy", tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isLoading) "Escaneando Hardware..." else text, color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
