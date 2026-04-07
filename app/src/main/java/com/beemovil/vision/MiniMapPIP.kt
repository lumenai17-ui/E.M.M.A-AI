package com.beemovil.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.*

// Bee color tokens
private val BeeYellow = Color(0xFFD4A843)
private val BeeBlack = Color(0xFF1A1A2E)
private val BeeWhite = Color(0xFFE0E0E0)
private val BeeGray = Color(0xFF888888)
private val NavGreen = Color(0xFF4CAF50)

/**
 * MiniMapPIP — Picture-in-Picture mini map using REAL OpenStreetMap tiles.
 *
 * Features:
 * - Real street-level map tiles from OpenStreetMap (free, no API key)
 * - Disk + memory tile cache (works offline after first load)
 * - GPS blue dot with heading indicator
 * - Green dashed line to destination
 * - Red destination marker
 * - Expandable/collapsible (120dp / 200dp)
 * - Speed + bearing + distance display
 * - Dark map overlay for night/driving mode
 *
 * Tile math: OpenStreetMap uses Slippy Map Tilenames (z/x/y).
 *   x = floor((lng + 180) / 360 * 2^z)
 *   y = floor((1 - ln(tan(lat_rad) + sec(lat_rad)) / PI) / 2 * 2^z)
 */
@Composable
fun MiniMapPIP(
    gpsData: GpsData,
    navigator: GpsNavigator,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val mapSize = if (isExpanded) 200.dp else 120.dp
    val context = LocalContext.current

    // Initialize tile cache with app context
    LaunchedEffect(Unit) {
        OsmTileCache.init(context)
    }

    // Load tiles for current position
    val zoom = if (isExpanded) 16 else 15
    val tileBitmaps = remember { mutableStateMapOf<String, ImageBitmap?>() }

    // Fetch tiles when position or zoom changes
    LaunchedEffect(gpsData.latitude, gpsData.longitude, zoom) {
        if (gpsData.latitude == 0.0) return@LaunchedEffect
        val centerTileX = OsmTileCache.lngToTileX(gpsData.longitude, zoom)
        val centerTileY = OsmTileCache.latToTileY(gpsData.latitude, zoom)

        // Load 3x3 grid of tiles around current position for smooth scrolling
        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = centerTileX + dx
                val ty = centerTileY + dy
                val key = "$zoom/$tx/$ty"
                if (!tileBitmaps.containsKey(key)) {
                    tileBitmaps[key] = null // placeholder
                    withContext(Dispatchers.IO) {
                        val bitmap = OsmTileCache.getTile(zoom, tx, ty)
                        if (bitmap != null) {
                            tileBitmaps[key] = bitmap.asImageBitmap()
                        }
                    }
                }
            }
        }
    }

    Card(
        modifier = modifier.size(mapSize),
        colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(if (isExpanded) 16.dp else 12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (navigator.isNavigating) NavGreen.copy(alpha = 0.6f) else BeeGray.copy(alpha = 0.3f)
        ),
        onClick = { isExpanded = !isExpanded }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Real map canvas with OSM tiles
            OsmMapCanvas(
                currentLat = gpsData.latitude,
                currentLng = gpsData.longitude,
                bearing = gpsData.bearing,
                destLat = navigator.destination?.latitude,
                destLng = navigator.destination?.longitude,
                zoom = zoom,
                tiles = tileBitmaps,
                modifier = Modifier.fillMaxSize()
            )

            // Overlay info bar at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(BeeBlack.copy(alpha = 0.8f))
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
                tint = BeeWhite.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp)
            )
        }
    }
}

/**
 * OsmMapCanvas — Renders real OpenStreetMap tiles with GPS overlay.
 * Tiles are 256x256 PNG images fetched from OSM servers and cached locally.
 */
@Composable
private fun OsmMapCanvas(
    currentLat: Double,
    currentLng: Double,
    bearing: Float,
    destLat: Double?,
    destLng: Double?,
    zoom: Int,
    tiles: Map<String, ImageBitmap?>,
    modifier: Modifier = Modifier
) {
    val posColor = Color(0xFF2196F3) // Blue dot
    val destColor = Color(0xFFF44336) // Red pin
    val lineColor = NavGreen.copy(alpha = 0.7f)
    val darkOverlay = BeeBlack.copy(alpha = 0.25f) // Subtle dark tint for readability

    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height
        val tileSize = 256f
        val n = (1 shl zoom).toDouble()

        // Calculate exact pixel position of current location
        val exactTileX = (currentLng + 180.0) / 360.0 * n
        val latRad = Math.toRadians(currentLat)
        val exactTileY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n

        // Pixel offset within the center tile
        val centerTileX = exactTileX.toInt()
        val centerTileY = exactTileY.toInt()
        val pixelOffsetX = ((exactTileX - centerTileX) * tileSize).toFloat()
        val pixelOffsetY = ((exactTileY - centerTileY) * tileSize).toFloat()

        // Draw tiles in a 3x3 grid centered on current position
        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = centerTileX + dx
                val ty = centerTileY + dy
                val key = "$zoom/$tx/$ty"
                val tileBitmap = tiles[key]

                // Calculate where this tile should be drawn on canvas
                val drawX = canvasW / 2f - pixelOffsetX + dx * tileSize
                val drawY = canvasH / 2f - pixelOffsetY + dy * tileSize

                if (tileBitmap != null) {
                    // Draw the real map tile
                    drawImage(
                        image = tileBitmap,
                        dstOffset = IntOffset(drawX.toInt(), drawY.toInt()),
                        dstSize = IntSize(tileSize.toInt(), tileSize.toInt())
                    )
                } else {
                    // Loading placeholder — dark grid
                    drawRect(
                        color = Color(0xFF1E1E3F),
                        topLeft = Offset(drawX, drawY),
                        size = Size(tileSize, tileSize)
                    )
                    // Grid lines as placeholder
                    drawRect(
                        color = Color(0xFF2A2A4E),
                        topLeft = Offset(drawX, drawY),
                        size = Size(tileSize, tileSize),
                        style = Stroke(0.5f)
                    )
                }
            }
        }

        // Dark overlay for better contrast with HUD elements
        drawRect(darkOverlay)

        val cx = canvasW / 2f
        val cy = canvasH / 2f

        // ── Destination line and marker ──
        if (destLat != null && destLng != null && currentLat != 0.0) {
            // Calculate destination pixel position relative to center
            val destExactX = (destLng + 180.0) / 360.0 * n
            val destLatRad = Math.toRadians(destLat)
            val destExactY = (1.0 - ln(tan(destLatRad) + 1.0 / cos(destLatRad)) / PI) / 2.0 * n

            val destPixelX = cx + ((destExactX - exactTileX) * tileSize).toFloat()
            val destPixelY = cy + ((destExactY - exactTileY) * tileSize).toFloat()

            // Clamp to visible area (draw line to edge if destination is off-screen)
            val clampedX = destPixelX.coerceIn(8f, canvasW - 8f)
            val clampedY = destPixelY.coerceIn(8f, canvasH - 8f)

            // Dashed line to destination
            drawLine(
                lineColor, Offset(cx, cy), Offset(clampedX, clampedY),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            )

            // Destination marker (only if on-screen)
            if (destPixelX in 0f..canvasW && destPixelY in 0f..canvasH) {
                drawCircle(Color.White, 9f, Offset(destPixelX, destPixelY))
                drawCircle(destColor, 7f, Offset(destPixelX, destPixelY))
            } else {
                // Edge indicator — small red dot at clamped position
                drawCircle(destColor.copy(alpha = 0.7f), 5f, Offset(clampedX, clampedY))
            }
        }

        // ── Current position (blue dot with pulse + heading) ──
        // Accuracy circle
        drawCircle(posColor.copy(alpha = 0.08f), 24f, Offset(cx, cy))
        drawCircle(posColor.copy(alpha = 0.15f), 16f, Offset(cx, cy))
        // White border
        drawCircle(Color.White, 9f, Offset(cx, cy))
        // Blue dot
        drawCircle(posColor, 7f, Offset(cx, cy))

        // Heading indicator (triangle pointing in bearing direction)
        val headAngle = Math.toRadians(bearing.toDouble())
        val headTipX = cx + (16f * sin(headAngle)).toFloat()
        val headTipY = cy - (16f * cos(headAngle)).toFloat()
        val headLeftX = cx + (7f * sin(headAngle - 2.5)).toFloat()
        val headLeftY = cy - (7f * cos(headAngle - 2.5)).toFloat()
        val headRightX = cx + (7f * sin(headAngle + 2.5)).toFloat()
        val headRightY = cy - (7f * cos(headAngle + 2.5)).toFloat()

        val headPath = Path().apply {
            moveTo(headTipX, headTipY)
            lineTo(headLeftX, headLeftY)
            lineTo(headRightX, headRightY)
            close()
        }
        drawPath(headPath, posColor)
    }
}

/**
 * OsmTileCache — Disk + memory cache for OpenStreetMap tiles.
 *
 * - Memory: LruCache (max 30 tiles = ~1MB)
 * - Disk: {app_files}/map_tiles/{z}/{x}/{y}.png
 * - Network: https://tile.openstreetmap.org/{z}/{x}/{y}.png
 * - Tiles expire after 7 days (re-download on next use)
 */
object OsmTileCache {
    private const val TAG = "OsmTileCache"
    private const val TILE_EXPIRE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    private const val MAX_MEMORY_TILES = 30
    private const val USER_AGENT = "BeeMovil/5.7.2 Android"

    private var cacheDir: File? = null

    // In-memory LRU cache (max ~1MB for 30 tiles of ~30KB each)
    private val memoryCache = LruCache<String, Bitmap>(MAX_MEMORY_TILES)

    fun init(context: android.content.Context) {
        if (cacheDir == null) {
            cacheDir = File(context.filesDir, "map_tiles").also { it.mkdirs() }
        }
    }

    /**
     * Get a tile bitmap (memory → disk → network).
     * Returns null if all sources fail.
     */
    fun getTile(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"

        // 1. Memory cache
        memoryCache.get(key)?.let { return it }

        // 2. Disk cache
        val file = getDiskFile(z, x, y)
        if (file.exists() && !isExpired(file)) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(key, bitmap)
                    return bitmap
                }
            } catch (e: Exception) {
                Log.w(TAG, "Disk read failed for $key: ${e.message}")
            }
        }

        // 3. Network download
        return try {
            downloadTile(z, x, y)
        } catch (e: Exception) {
            Log.w(TAG, "Download failed for $key: ${e.message}")
            null
        }
    }

    private fun downloadTile(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"
        // OSM tile server — free, no API key needed
        // Must include proper User-Agent per OSM Tile Usage Policy
        val url = "https://tile.openstreetmap.org/$z/$x/$y.png"

        val connection = URL(url).openConnection().apply {
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 5000
            readTimeout = 5000
        }

        val bitmap = connection.getInputStream().use { stream ->
            BitmapFactory.decodeStream(stream)
        }

        if (bitmap != null) {
            // Save to disk
            try {
                val file = getDiskFile(z, x, y)
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Disk write failed for $key: ${e.message}")
            }

            // Save to memory
            memoryCache.put(key, bitmap)
        }

        return bitmap
    }

    private fun getDiskFile(z: Int, x: Int, y: Int): File {
        return File(cacheDir ?: File("/tmp"), "$z/$x/$y.png")
    }

    private fun isExpired(file: File): Boolean {
        return System.currentTimeMillis() - file.lastModified() > TILE_EXPIRE_MS
    }

    // ═══════════════════════════════════════
    // Tile coordinate math (Slippy Map)
    // ═══════════════════════════════════════

    /** Convert longitude to tile X number at given zoom */
    fun lngToTileX(lng: Double, zoom: Int): Int {
        return ((lng + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    /** Convert latitude to tile Y number at given zoom */
    fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)).toInt()
    }

    /** Get total cached tiles count */
    fun getCacheStats(): Pair<Int, Long> {
        val dir = cacheDir ?: return Pair(0, 0L)
        var count = 0
        var bytes = 0L
        dir.walkTopDown().filter { it.isFile }.forEach { 
            count++
            bytes += it.length()
        }
        return Pair(count, bytes)
    }

    /** Clear all cached tiles */
    fun clearCache() {
        memoryCache.evictAll()
        cacheDir?.deleteRecursively()
        cacheDir?.mkdirs()
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
