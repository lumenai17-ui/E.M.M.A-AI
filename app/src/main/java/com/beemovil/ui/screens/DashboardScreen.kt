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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
            .verticalScroll(rememberScrollState())
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

            Column(modifier = Modifier.padding(16.dp)) {
                // Header (Nativo de Telemetría)
                Text(
                    text = dynamicState.insightHeaderTop,
                    color = BeeWhite.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dynamicState.insightHeaderBottom,
                    color = BeeWhite.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.Divider(color = Color.DarkGray.copy(alpha = 0.4f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row {
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
                        lineHeight = 22.sp, // Interlineado más limpio para múltiples líneas
                        modifier = Modifier.weight(1f).heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.dashboard_section_access), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BeeWhite, modifier = Modifier.padding(horizontal = 24.dp))
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

            val recientes = viewModel.allThreads
                .filter { it.threadId != "main" && it.threadId != "default_session" }
                .sortedByDescending { it.lastUpdateMillis }
                .take(3)

            if (recientes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "CHATS RECIENTES",
                    fontSize = 12.sp,
                    color = BeeGray,
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
    Surface(
        onClick = onClick,
        color = Color(0xFF161622),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(64.dp) // Compactado
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(BeeYellow.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = BeeYellow, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = BeeWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = BeeGray, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowForwardIos, "Go", tint = BeeGray, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun MapRadarWidget(text: String, isLoading: Boolean) {
    Surface(
        color = BeeBlack.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, BeeYellow.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
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
                
                // Círculos concéntricos retro-futuristas
                drawCircle(color = Color(0xFF4CAF50).copy(alpha = 0.15f), radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(color = Color(0xFF4CAF50).copy(alpha = 0.1f), radius = radius * 0.6f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(color = Color(0xFF4CAF50).copy(alpha = 0.05f), radius = radius * 0.2f, center = center)
                
                // Aguja Sweeper (Holograma) con estela difuminada
                val sweepGradient = androidx.compose.ui.graphics.Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.8f to BeeYellow.copy(alpha = 0.0f),
                    0.90f to BeeYellow.copy(alpha = 0.2f),
                    1.0f to BeeYellow.copy(alpha = 0.8f),
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
                    color = BeeYellow,
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
                Text("SCANNING...", color = BeeYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(2.dp))
                // Extraer el nombre de la ubicacion o predeterminar
                val cleanText = if (isLoading) "Calculando vector..." else text.replace("📍", "").trim()
                Text(cleanText, color = BeeWhite, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ThermalWeatherWidget(text: String, isLoading: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "weather")
    val isSunny = text.contains("Sol", true) || text.contains("Despejado", true)
    val isRain = text.contains("Lluvia", true) || text.contains("Tormenta", true)
    
    // Fondos animados contextuales
    val bgColor by infiniteTransition.animateColor(
        initialValue = BeeBlack.copy(alpha = 0.2f),
        targetValue = BeeBlack.copy(alpha = 0.4f),
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "weather_bg"
    )

    Surface(
        color = bgColor,
        border = BorderStroke(1.dp, if(isSunny) BeeYellow.copy(alpha=0.3f) else Color.DarkGray.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Sistema Semántico Dinámico Vectorial (Ultra Minimalista)
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
            // 2. Fondo Fotográfico Nativo Exótico (Sin opacidades lentas de red)
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = chosenImage),
                contentDescription = "Estado del clima",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 3. Cristal Orgánico Sutilísimo para Contraste Textual
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))

            // 4. El Neón Central Flotante
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
                            brush = androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color(0xFF2196F3), BeeYellow, Color(0xFFF44336), Color(0xFF2196F3))),
                            startAngle = 135f, sweepAngle = 270f, useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                        drawArc(
                            color = BeeWhite,
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
                        color = BeeWhite, 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.ExtraBold,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 12f))
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                val cleanDesc = if (isLoading) "Sintetizando..." else text.substringAfter(" ").substringBefore("(").trim().take(22)
                Text(
                    text = cleanDesc, 
                    color = BeeYellow, 
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
    Surface(
        color = BeeBlack.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, BeeYellow.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
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
                    // Un pulso tipo EKG en el medio
                    val diff = ((normalizedX - 0.5f) * 12f).toDouble()
                    val pulseEnvelope = kotlin.math.exp(-Math.pow(diff, 2.0)).toFloat()
                    // Onda que viaja (desplazada por 'phase')
                    val wave = kotlin.math.sin(normalizedX * 15f - phase)
                    
                    val y = midY + (wave * 25f * pulseEnvelope)
                    path.lineTo(x.toFloat(), y.toFloat())
                }
                
                drawPath(
                    path = path,
                    color = BeeYellow.copy(alpha = 0.8f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ElectricBolt, "Energy", tint = BeeYellow, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isLoading) "Escaneando Hardware..." else text, color = BeeWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
