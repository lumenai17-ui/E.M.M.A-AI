package com.beemovil.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.beemovil.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class MediaItem(
    val file: File,
    val name: String,
    val path: String,
    val isVideo: Boolean,
    val sizeStr: String,
    val dateStr: String,
    val dateGroupKey: String,
    val timestamp: Long
)

/**
 * MediaGalleryScreen — Phase 23-C
 * Grid gallery for AI-generated images and videos.
 * Supports full-screen viewer, share, delete, and cross-integration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    onBack: () -> Unit,
    onAttachToChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<MediaItem?>(null) }

    // Load media files
    val allMedia = remember { mutableStateListOf<MediaItem>() }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        allMedia.clear()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val groupFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Images
        val imgDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "BeeMovil/images"
        )
        if (imgDir.exists()) {
            imgDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp") }?.forEach { f ->
                val bytes = f.length()
                allMedia.add(MediaItem(
                    file = f, name = f.name, path = f.absolutePath, isVideo = false,
                    sizeStr = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB",
                    dateStr = dateFormat.format(Date(f.lastModified())),
                    dateGroupKey = groupFormat.format(Date(f.lastModified())),
                    timestamp = f.lastModified()
                ))
            }
        }

        // Videos
        val vidDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "BeeMovil/videos"
        )
        if (vidDir.exists()) {
            vidDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("mp4", "mov", "webm") }?.forEach { f ->
                val bytes = f.length()
                allMedia.add(MediaItem(
                    file = f, name = f.name, path = f.absolutePath, isVideo = true,
                    sizeStr = if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB",
                    dateStr = dateFormat.format(Date(f.lastModified())),
                    dateGroupKey = groupFormat.format(Date(f.lastModified())),
                    timestamp = f.lastModified()
                ))
            }
        }

        // Sort newest first
        allMedia.sortByDescending { it.timestamp }
    }

    val filteredMedia = when (selectedTab) {
        1 -> allMedia.filter { !it.isVideo }
        2 -> allMedia.filter { it.isVideo }
        else -> allMedia.toList()
    }

    // Full screen viewer
    if (selectedMedia != null) {
        MediaDetailView(
            item = selectedMedia!!,
            onBack = { selectedMedia = null },
            onDelete = { showDeleteConfirm = it },
            onAttachToChat = onAttachToChat
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Media Gallery", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${allMedia.count { !it.isVideo }} imagenes · ${allMedia.count { it.isVideo }} videos",
                            fontSize = 11.sp, color = BeeGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = BeeWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = BeeGrayLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BeeBlack)
            )
        },
        containerColor = BeeBlack,
        floatingActionButton = {
            // No FAB for now — generation happens from chat
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BeeBlackLight,
                contentColor = BeeYellow,
                indicator = { /* default indicator */ },
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Todo (${allMedia.size})", fontSize = 13.sp) },
                    selectedContentColor = BeeYellow,
                    unselectedContentColor = BeeGray
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Imagenes (${allMedia.count { !it.isVideo }})", fontSize = 13.sp) },
                    selectedContentColor = BeeYellow,
                    unselectedContentColor = BeeGray
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Videos (${allMedia.count { it.isVideo }})", fontSize = 13.sp) },
                    selectedContentColor = BeeYellow,
                    unselectedContentColor = BeeGray
                )
            }

            if (filteredMedia.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.PhotoLibrary, "Empty",
                            tint = BeeGray.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Sin media generada",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Di \"genera una imagen de...\" en cualquier chat",
                            fontSize = 13.sp, color = BeeGray.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Media Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(filteredMedia) { item ->
                        MediaGridItem(
                            item = item,
                            onClick = { selectedMedia = item }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = BeeBlackLight,
            title = { Text("Eliminar ${if (item.isVideo) "video" else "imagen"}?", color = BeeWhite) },
            text = { Text(item.name, color = BeeGray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    item.file.delete()
                    allMedia.remove(item)
                    if (selectedMedia == item) selectedMedia = null
                    showDeleteConfirm = null
                    Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Eliminar", color = Color(0xFFFF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancelar", color = BeeGray)
                }
            }
        )
    }
}

/**
 * Grid item — thumbnail with video overlay
 */
@Composable
fun MediaGridItem(item: MediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        if (item.isVideo) {
            // Video thumbnail — gradient placeholder with play icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1A1020), Color(0xFF2A1525))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayCircle, "Video",
                    tint = Color(0xFFFF6B35).copy(alpha = 0.8f),
                    modifier = Modifier.size(36.dp)
                )
                // Duration badge
                Text(
                    "VIDEO",
                    fontSize = 8.sp, color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        } else {
            // Image thumbnail
            val bitmap = remember(item.path) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 } // Small thumbnail
                    BitmapFactory.decodeFile(item.path, opts)
                } catch (_: Exception) { null }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BeeGray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.BrokenImage, "Error", tint = BeeGray, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * Full-screen media detail view with info and actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailView(
    item: MediaItem,
    onBack: () -> Unit,
    onDelete: (MediaItem) -> Unit,
    onAttachToChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    // Zoom state for images
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (item.isVideo) "Video" else "Imagen",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                        Text(item.dateStr, fontSize = 11.sp, color = BeeGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = BeeWhite)
                    }
                },
                actions = {
                    // Share
                    IconButton(onClick = {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", item.file)
                            val mime = if (item.isVideo) "video/*" else "image/*"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir"))
                        } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Outlined.Share, "Share", tint = BeeWhite)
                    }
                    // Delete
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFFF6666))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Preview area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    // Video — show play prompt
                    Surface(
                        onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", item.file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFF6B35).copy(alpha = 0.2f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    color = Color(0xFFFF6B35).copy(alpha = 0.9f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.PlayArrow, "Play",
                                            tint = Color.White, modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Toca para reproducir",
                                    fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    // Image — zoomable
                    val bitmap = remember(item.path) {
                        try { BitmapFactory.decodeFile(item.path) } catch (_: Exception) { null }
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                                        if (scale > 1f) {
                                            offset = Offset(offset.x + pan.x, offset.y + pan.y)
                                        } else {
                                            offset = Offset.Zero
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1.5f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                scale = 3f
                                            }
                                        }
                                    )
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Info + actions bar
            Surface(
                color = Color(0xFF0E0E18),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // File info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                if (item.isVideo) "Video AI" else "Imagen AI",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BeeWhite
                            )
                            Text(item.dateStr, fontSize = 12.sp, color = BeeGray)
                        }
                        Surface(
                            color = if (item.isVideo) Color(0xFFFF6B35).copy(alpha = 0.15f) else Color(0xFFBF5AF2).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                item.sizeStr,
                                fontSize = 12.sp,
                                color = if (item.isVideo) Color(0xFFFF6B35) else Color(0xFFBF5AF2),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Prompt from filename
                    val prompt = item.name
                        .substringAfter("_", "")
                        .substringAfter("_", "")
                        .substringBeforeLast(".")
                        .replace("_", " ")
                    if (prompt.isNotBlank() && prompt.length > 3) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = BeeGray.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp)) {
                                Icon(Icons.Outlined.AutoAwesome, "Prompt", tint = BeeYellow, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    prompt,
                                    fontSize = 12.sp, color = BeeGrayLight,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Share
                        MediaActionButton(
                            icon = Icons.Outlined.Share,
                            label = "Compartir",
                            color = Color(0xFF5AC8FA)
                        ) {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", item.file)
                                val mime = if (item.isVideo) "video/*" else "image/*"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir"))
                            } catch (_: Exception) {}
                        }

                        // Open with
                        MediaActionButton(
                            icon = Icons.Outlined.OpenInNew,
                            label = "Abrir",
                            color = Color(0xFF4CAF50)
                        ) {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", item.file)
                                val mime = if (item.isVideo) "video/*" else "image/*"
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }

                        // Attach to chat
                        if (onAttachToChat != null) {
                            MediaActionButton(
                                icon = Icons.Outlined.Chat,
                                label = "Al chat",
                                color = BeeYellow
                            ) {
                                onAttachToChat(item.path)
                                Toast.makeText(context, "Adjuntado al chat", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // Delete
                        MediaActionButton(
                            icon = Icons.Outlined.Delete,
                            label = "Eliminar",
                            color = Color(0xFFFF4444)
                        ) {
                            onDelete(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = BeeGray)
    }
}
