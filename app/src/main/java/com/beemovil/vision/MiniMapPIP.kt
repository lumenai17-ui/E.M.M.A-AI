package com.beemovil.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.*

// Bee color tokens
private val BeeYellow = Color(0xFFD4A843)
private val BeeBlack = Color(0xFF1A1A2E)
private val BeeWhite = Color(0xFFE0E0E0)
private val BeeGray = Color(0xFF888888)
private val NavGreen = Color(0xFF4CAF50)

/**
 * MiniMapPIP — Picture-in-Picture mini map using OpenStreetMap tiles.
 *
 * Features:
 * - Shows current position (blue dot)
 * - Line to destination (green dashed)
 * - Destination marker (red pin)
 * - Expandable/collapsible
 * - Speed + bearing display
 * - POI suggestion chips
 */
@Composable
fun MiniMapPIP(
    gpsData: GpsData,
    navigator: GpsNavigator,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val mapSize = if (isExpanded) 200.dp else 120.dp

    Card(
        modifier = modifier
            .size(mapSize),
        colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(if (isExpanded) 16.dp else 12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (navigator.isNavigating) NavGreen.copy(alpha = 0.6f) else BeeGray.copy(alpha = 0.3f)
        ),
        onClick = { isExpanded = !isExpanded }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Map canvas (GPS visualization)
            MapCanvas(
                currentLat = gpsData.latitude,
                currentLng = gpsData.longitude,
                bearing = gpsData.bearing,
                destLat = navigator.destination?.latitude,
                destLng = navigator.destination?.longitude,
                modifier = Modifier.fillMaxSize()
            )

            // Overlay info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(BeeBlack.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                if (navigator.isNavigating && navigator.destination != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            navigator.update(gpsData).arrow,
                            fontSize = if (isExpanded) 16.sp else 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            navigator.update(gpsData).distance,
                            fontSize = if (isExpanded) 12.sp else 9.sp,
                            color = NavGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            navigator.update(gpsData).eta,
                            fontSize = if (isExpanded) 11.sp else 8.sp,
                            color = BeeGray
                        )
                    }
                } else {
                    // Just show coordinates
                    Text(
                        gpsData.coordsShort,
                        fontSize = 8.sp,
                        color = BeeGray,
                        maxLines = 1
                    )
                }

                if (gpsData.speedKmh > 1 && isExpanded) {
                    Text(
                        "${"%.0f".format(gpsData.speedKmh)} km/h ${gpsData.bearingCardinal}",
                        fontSize = 9.sp,
                        color = BeeWhite
                    )
                }
            }

            // Expand/collapse indicator
            Icon(
                if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                "Toggle",
                tint = BeeGray.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp)
            )
        }
    }
}

/**
 * MapCanvas — Draws a simple GPS map with position, heading, and destination line.
 * Uses a compass-style view rather than actual map tiles to avoid API keys.
 */
@Composable
private fun MapCanvas(
    currentLat: Double,
    currentLng: Double,
    bearing: Float,
    destLat: Double?,
    destLng: Double?,
    modifier: Modifier = Modifier
) {
    val gridColor = Color(0xFF2A2A4E)
    val posColor = Color(0xFF2196F3) // Blue dot
    val destColor = Color(0xFFF44336) // Red pin
    val lineColor = NavGreen.copy(alpha = 0.6f)
    val northColor = Color(0xFFFF5722)

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = minOf(cx, cy) * 0.85f

        // Background gradient (dark map style)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E1E3F), Color(0xFF0D0D1A)),
                center = Offset(cx, cy),
                radius = radius * 1.5f
            )
        )

        // Grid lines (simulated map grid)
        val gridStep = radius / 3
        for (i in -3..3) {
            val offset = i * gridStep
            // Horizontal
            drawLine(
                gridColor, Offset(0f, cy + offset), Offset(size.width, cy + offset),
                strokeWidth = 0.5f
            )
            // Vertical
            drawLine(
                gridColor, Offset(cx + offset, 0f), Offset(cx + offset, size.height),
                strokeWidth = 0.5f
            )
        }

        // Compass circle
        drawCircle(
            color = gridColor.copy(alpha = 0.3f),
            radius = radius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1f)
        )
        drawCircle(
            color = gridColor.copy(alpha = 0.2f),
            radius = radius * 0.5f,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(0.5f)
        )

        // North indicator (rotated by bearing)
        val northAngle = Math.toRadians((-bearing).toDouble())
        val northX = cx + (radius * 0.9f * sin(northAngle)).toFloat()
        val northY = cy - (radius * 0.9f * cos(northAngle)).toFloat()
        drawCircle(northColor, 4f, Offset(northX, northY))

        // Destination line and marker
        if (destLat != null && destLng != null && currentLat != 0.0) {
            val results = FloatArray(3)
            android.location.Location.distanceBetween(
                currentLat, currentLng, destLat, destLng, results
            )
            val destBearing = if (results.size > 1) results[1] else 0f
            val destDist = results[0]

            // Scale distance to fit in circle (logarithmic for large distances)
            val scaledDist = when {
                destDist > 10000 -> radius * 0.85f
                destDist > 1000 -> radius * (0.5f + 0.35f * (destDist / 10000f))
                else -> radius * (destDist / 2000f).coerceIn(0.1f, 0.85f)
            }

            // Rotate relative to phone bearing
            val relAngle = Math.toRadians((destBearing - bearing).toDouble())
            val destX = cx + (scaledDist * sin(relAngle)).toFloat()
            val destY = cy - (scaledDist * cos(relAngle)).toFloat()

            // Dashed line to destination
            drawLine(
                lineColor, Offset(cx, cy), Offset(destX, destY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )

            // Destination marker (red circle with white border)
            drawCircle(Color.White, 8f, Offset(destX, destY))
            drawCircle(destColor, 6f, Offset(destX, destY))
        }

        // Current position (blue dot with pulse effect)
        drawCircle(posColor.copy(alpha = 0.15f), 18f, Offset(cx, cy))
        drawCircle(posColor.copy(alpha = 0.3f), 12f, Offset(cx, cy))
        drawCircle(Color.White, 7f, Offset(cx, cy))
        drawCircle(posColor, 5f, Offset(cx, cy))

        // Heading indicator (triangle pointing forward)
        val headPath = Path().apply {
            moveTo(cx, cy - 14f)
            lineTo(cx - 5f, cy - 6f)
            lineTo(cx + 5f, cy - 6f)
            close()
        }
        drawPath(headPath, posColor)
    }
}

/**
 * NavigationHUD — Full navigation overlay for LiveVision.
 * Shows arrow, distance, ETA, instruction, and POI suggestions.
 */
@Composable
fun NavigationHUD(
    navUpdate: NavigationUpdate,
    destinationName: String,
    onPoiClick: (String) -> Unit,
    poiSuggestions: List<PoiSuggestion>,
    modifier: Modifier = Modifier
) {
    if (navUpdate.phase == NavPhase.IDLE) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (navUpdate.phase) {
                NavPhase.ARRIVED -> NavGreen.copy(alpha = 0.85f)
                NavPhase.ARRIVING -> NavGreen.copy(alpha = 0.25f)
                NavPhase.CLOSE -> BeeBlack.copy(alpha = 0.85f)
                else -> BeeBlack.copy(alpha = 0.75f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (navUpdate.phase != NavPhase.ARRIVED) {
            androidx.compose.foundation.BorderStroke(1.dp, NavGreen.copy(alpha = 0.4f))
        } else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Main nav row: arrow + distance + ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Big arrow
                Text(
                    navUpdate.arrow,
                    fontSize = 32.sp
                )
                Spacer(modifier = Modifier.width(12.dp))

                // Distance + destination
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        navUpdate.distance,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (navUpdate.phase == NavPhase.ARRIVED) BeeBlack else NavGreen
                    )
                    Text(
                        destinationName,
                        fontSize = 12.sp,
                        color = if (navUpdate.phase == NavPhase.ARRIVED) BeeBlack.copy(alpha = 0.7f) else BeeGray,
                        maxLines = 1
                    )
                }

                // ETA + Speed
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        navUpdate.eta,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (navUpdate.phase == NavPhase.ARRIVED) BeeBlack else BeeWhite
                    )
                    if (navUpdate.speedKmh > 1) {
                        Text(
                            "${"%.0f".format(navUpdate.speedKmh)} km/h",
                            fontSize = 10.sp,
                            color = BeeGray
                        )
                    }
                }
            }

            // Instruction text
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                navUpdate.instruction,
                fontSize = 13.sp,
                color = if (navUpdate.phase == NavPhase.ARRIVED) BeeBlack else BeeWhite,
                lineHeight = 18.sp
            )

            // POI suggestions (contextual)
            if (poiSuggestions.isNotEmpty() && navUpdate.phase != NavPhase.ARRIVED) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    poiSuggestions.forEach { poi ->
                        Surface(
                            onClick = { onPoiClick(poi.searchQuery) },
                            color = BeeBlack.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(poi.emoji, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(poi.label, fontSize = 9.sp, color = BeeGray)
                            }
                        }
                    }
                }
            }
        }
    }
}
