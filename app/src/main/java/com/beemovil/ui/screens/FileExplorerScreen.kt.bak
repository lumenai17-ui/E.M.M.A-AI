package com.beemovil.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.beemovil.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════
// File Explorer — Phase 24-A Rewrite
// Real file browser for the entire device
// ═══════════════════════════════════════

enum class SortMode { NAME, SIZE, DATE, TYPE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    onBack: () -> Unit,
    onOpenFile: ((String) -> Unit)? = null,
    onAttachToChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val rootDir = Environment.getExternalStorageDirectory()
    val beeDir = File(rootDir, "BeeMovil")
    var currentDir by remember { mutableStateOf(rootDir) }
    var files by remember { mutableStateOf(listFilesReal(rootDir, SortMode.NAME)) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }

    // State
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var showSortMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var isSelecting by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<List<File>>(emptyList()) }
    var showFileActions by remember { mutableStateOf<File?>(null) }

    // Google Drive state
    val googleAuth = remember { com.beemovil.google.GoogleAuthManager(context) }
    var isDriveMode by remember { mutableStateOf(false) }
    var driveFiles by remember { mutableStateOf<List<com.beemovil.google.GoogleDriveService.DriveFile>>(emptyList()) }
    var driveFolderStack by remember { mutableStateOf(listOf<Pair<String?, String>>("root" to "Mi Drive")) }
    var driveLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // BeeMovil generated dirs
    val beeImageDir = File(rootDir, "Documents/BeeMovil/images")
    val beeVideoDir = File(rootDir, "Documents/BeeMovil/videos")
    val beeGeneratedPaths = remember {
        val paths = mutableSetOf<String>()
        listOf(beeImageDir, beeVideoDir,
            File(rootDir, "Documents/BeeMovil/generated"),
            File(rootDir, "Documents/BeeMovil/PDFs"),
            File(rootDir, "Documents/BeeMovil/HTML")
        ).forEach { dir ->
            if (dir.exists()) dir.listFiles()?.forEach { paths.add(it.absolutePath) }
        }
        paths
    }

    fun refreshFiles() {
        files = listFilesReal(currentDir, sortMode)
    }

    fun navigateTo(dir: File) {
        currentDir = dir
        files = listFilesReal(dir, sortMode)
        selectedFiles = emptySet()
        isSelecting = false
    }

    // Breadcrumb path parts
    val breadcrumbs = remember(currentDir) {
        val parts = mutableListOf<Pair<String, File>>()
        var f: File? = currentDir
        while (f != null && f.absolutePath.length >= rootDir.absolutePath.length) {
            val name = if (f == rootDir) "Almacenamiento" else f.name
            parts.add(0, name to f)
            f = f.parentFile
        }
        parts
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
    ) {
        // ══════ Top Bar ══════
        TopAppBar(
            title = {
                if (isSearching) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { q ->
                            searchQuery = q
                            if (q.length >= 2) {
                                searchResults = searchFilesRecursive(currentDir, q)
                            } else {
                                searchResults = emptyList()
                            }
                        },
                        placeholder = { Text("Buscar archivos...", fontSize = 13.sp, color = BeeGray) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = BeeWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BeeYellow,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = BeeYellow,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                } else if (isSelecting) {
                    Text("${selectedFiles.size} seleccionado(s)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Column {
                        Text("Archivos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            currentDir.absolutePath.removePrefix(rootDir.absolutePath).ifEmpty { "/" },
                            fontSize = 11.sp, color = BeeGray, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    when {
                        isSearching -> { isSearching = false; searchQuery = ""; searchResults = emptyList() }
                        isSelecting -> { isSelecting = false; selectedFiles = emptySet() }
                        currentDir != rootDir && currentDir.parentFile != null -> navigateTo(currentDir.parentFile!!)
                        else -> onBack()
                    }
                }) {
                    Icon(
                        if (isSearching || isSelecting) Icons.Filled.Close else Icons.Filled.ArrowBack,
                        "Back", tint = BeeYellow
                    )
                }
            },
            actions = {
                if (isSelecting) {
                    // Bulk actions
                    IconButton(onClick = {
                        val toShare = selectedFiles.map { File(it) }.filter { it.exists() }
                        shareFiles(context, toShare)
                    }) { Icon(Icons.Outlined.Share, "Share", tint = BeeGrayLight) }
                    IconButton(onClick = {
                        showDeleteConfirm = selectedFiles.map { File(it) }
                    }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFFF4444)) }
                } else {
                    IconButton(onClick = { isSearching = !isSearching }) {
                        Icon(Icons.Filled.Search, "Search", tint = BeeGrayLight)
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, "Sort", tint = BeeGrayLight)
                    }
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Filled.CreateNewFolder, "New Folder", tint = BeeGrayLight)
                    }
                    // Sort dropdown
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (sortMode == mode) Icon(Icons.Filled.Check, null, tint = BeeYellow, modifier = Modifier.size(16.dp))
                                        else Spacer(modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(when(mode) {
                                            SortMode.NAME -> "Nombre"
                                            SortMode.SIZE -> "Tamaño"
                                            SortMode.DATE -> "Fecha"
                                            SortMode.TYPE -> "Tipo"
                                        }, color = BeeWhite)
                                    }
                                },
                                onClick = { sortMode = mode; showSortMenu = false; refreshFiles() }
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BeeBlackLight,
                titleContentColor = BeeWhite
            )
        )

        // ══════ Breadcrumb Navigation ══════
        if (!isSearching) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BeeBlackLight)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(breadcrumbs) { (name, dir) ->
                    val isLast = dir == currentDir
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { if (!isLast) navigateTo(dir) },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                name,
                                fontSize = 12.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLast) BeeYellow else BeeGray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        if (!isLast) {
                            Icon(Icons.Filled.ChevronRight, null, tint = BeeGray.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // ══════ Quick Access Chips ══════
        if (!isSearching && !isSelecting) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickDirs = listOf(
                    "BeeMovil" to beeDir,
                    "Downloads" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DCIM" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "Documents" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "Pictures" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Music" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                )
                items(quickDirs) { (label, dir) ->
                    FilterChip(
                        selected = currentDir.absolutePath == dir.absolutePath,
                        onClick = { if (dir.exists()) navigateTo(dir) },
                        label = { Text(label, fontSize = 11.sp) },
                        leadingIcon = {
                            val icon = when(label) {
                                "BeeMovil" -> Icons.Filled.SmartToy
                                "Downloads" -> Icons.Filled.Download
                                "DCIM" -> Icons.Filled.CameraAlt
                                "Documents" -> Icons.Filled.Description
                                "Pictures" -> Icons.Filled.Photo
                                "Music" -> Icons.Filled.MusicNote
                                else -> Icons.Filled.Folder
                            }
                            Icon(icon, label, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BeeYellow,
                            selectedLabelColor = BeeBlack,
                            selectedLeadingIconColor = BeeBlack,
                            containerColor = BeeGray.copy(alpha = 0.15f),
                            labelColor = BeeGrayLight,
                            iconColor = BeeGrayLight
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
                // Google Drive chip
                item {
                    FilterChip(
                        selected = isDriveMode,
                        onClick = {
                            if (googleAuth.isSignedIn() && googleAuth.hasDriveScope()) {
                                isDriveMode = !isDriveMode
                                if (isDriveMode) {
                                    driveLoading = true
                                    scope.launch {
                                        val token = googleAuth.getAccessToken()
                                        if (token != null) {
                                            val svc = com.beemovil.google.GoogleDriveService(token)
                                            driveFiles = withContext(Dispatchers.IO) { svc.listFiles(null) }
                                        }
                                        driveLoading = false
                                    }
                                }
                            } else if (googleAuth.isSignedIn()) {
                                Toast.makeText(context, "Autoriza Drive en Settings → Google Workspace", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Conecta Google en Settings primero", Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Drive", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.Cloud, "Drive", modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4285F4),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = Color(0xFF4285F4).copy(alpha = 0.15f),
                            labelColor = Color(0xFF4285F4),
                            iconColor = Color(0xFF4285F4)
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }
        }

        // ══════ Stats bar ══════
        val displayFiles = if (isSearching && searchQuery.length >= 2) searchResults else files
        val dirCount = displayFiles.count { it.isDirectory }
        val fileCount = displayFiles.count { it.isFile }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (isSearching && searchQuery.length >= 2) "$fileCount resultados" else "$dirCount carpetas, $fileCount archivos",
                fontSize = 12.sp, color = BeeGray
            )
            Text(humanSizeReal(displayFiles.filter { it.isFile }.sumOf { it.length() }), fontSize = 12.sp, color = BeeGray)
        }

        // ══════ File List ══════
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            items(displayFiles) { file ->
                FileItemReal(
                    file = file,
                    dateFormatter = dateFormatter,
                    isAiGenerated = file.absolutePath in beeGeneratedPaths,
                    isSelected = file.absolutePath in selectedFiles,
                    isSelecting = isSelecting,
                    onClick = {
                        if (isSelecting) {
                            selectedFiles = if (file.absolutePath in selectedFiles)
                                selectedFiles - file.absolutePath
                            else
                                selectedFiles + file.absolutePath
                        } else if (file.isDirectory) {
                            navigateTo(file)
                        } else {
                            showFileActions = file
                        }
                    },
                    onLongClick = {
                        if (!isSelecting) {
                            isSelecting = true
                            selectedFiles = setOf(file.absolutePath)
                        }
                    }
                )
            }

            if (displayFiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.FolderOpen, "Empty", tint = BeeGray.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (isSearching) "Sin resultados para \"$searchQuery\"" else "Carpeta vacia",
                                color = BeeGray, fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ══════ File Actions Bottom Sheet ══════
    showFileActions?.let { file ->
        FileActionsSheet(
            file = file,
            context = context,
            isAiGenerated = file.absolutePath in beeGeneratedPaths,
            onDismiss = { showFileActions = null },
            onOpen = {
                openFileExternal(context, file)
                showFileActions = null
            },
            onShare = {
                shareFiles(context, listOf(file))
                showFileActions = null
            },
            onRename = {
                showRenameDialog = file
                showFileActions = null
            },
            onDelete = {
                showDeleteConfirm = listOf(file)
                showFileActions = null
            },
            onAttachToChat = if (onAttachToChat != null) { {
                onAttachToChat(file.absolutePath)
                showFileActions = null
                Toast.makeText(context, "Enviado al chat", Toast.LENGTH_SHORT).show()
            } } else null
        )
    }

    // ══════ New Folder Dialog ══════
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = BeeBlackLight,
            title = { Text("Nueva carpeta", color = BeeWhite) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("Nombre", color = BeeGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeeYellow,
                        unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                        cursorColor = BeeYellow,
                        focusedTextColor = BeeWhite,
                        unfocusedTextColor = BeeWhite
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        File(currentDir, folderName.trim()).mkdirs()
                        refreshFiles()
                    }
                    showNewFolderDialog = false
                }) { Text("Crear", color = BeeYellow) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancelar", color = BeeGray) }
            }
        )
    }

    // ══════ Rename Dialog ══════
    showRenameDialog?.let { file ->
        var newName by remember { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            containerColor = BeeBlackLight,
            title = { Text("Renombrar", color = BeeWhite) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeeYellow,
                        unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                        cursorColor = BeeYellow,
                        focusedTextColor = BeeWhite,
                        unfocusedTextColor = BeeWhite
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != file.name) {
                        file.renameTo(File(file.parentFile, newName.trim()))
                        refreshFiles()
                    }
                    showRenameDialog = null
                }) { Text("Renombrar", color = BeeYellow) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancelar", color = BeeGray) }
            }
        )
    }

    // ══════ Delete Confirmation ══════
    if (showDeleteConfirm.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = emptyList() },
            containerColor = BeeBlackLight,
            title = { Text("Eliminar ${showDeleteConfirm.size} archivo(s)?", color = BeeWhite) },
            text = {
                Text(
                    showDeleteConfirm.joinToString("\n") { it.name },
                    color = BeeGray, fontSize = 13.sp, maxLines = 5
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm.forEach { it.deleteRecursively() }
                    selectedFiles = emptySet()
                    isSelecting = false
                    refreshFiles()
                    showDeleteConfirm = emptyList()
                    Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show()
                }) { Text("Eliminar", color = Color(0xFFFF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = emptyList() }) { Text("Cancelar", color = BeeGray) }
            }
        )
    }
}

// ══════════════════════════════════════
// File Item — with thumbnail + long press
// ══════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemReal(
    file: File,
    dateFormatter: SimpleDateFormat,
    isAiGenerated: Boolean,
    isSelected: Boolean,
    isSelecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val ext = file.extension.lowercase()
    val isImage = ext in listOf("png", "jpg", "jpeg", "webp", "gif")
    val isVideo = ext in listOf("mp4", "mov", "webm", "avi")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = if (isSelected) BeeYellow.copy(alpha = 0.12f) else BeeBlackLight,
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) BorderStroke(1.dp, BeeYellow.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox or icon/thumbnail
            if (isSelecting) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = BeeYellow,
                        uncheckedColor = BeeGray
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Thumbnail or icon
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (isImage && file.exists() && file.length() < 10_000_000) {
                    val bitmap = remember(file.absolutePath) {
                        try {
                            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                            BitmapFactory.decodeFile(file.absolutePath, opts)
                        } catch (_: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        FileIcon(file)
                    }
                } else if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1020), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayCircle, "Video", tint = Color(0xFFFF6B35), modifier = Modifier.size(24.dp))
                    }
                } else {
                    FileIcon(file)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.name,
                        color = BeeWhite,
                        fontSize = 14.sp,
                        fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isAiGenerated) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = BeeYellow.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("AI", fontSize = 9.sp, color = BeeYellow, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (file.isFile) {
                        Text(humanSizeReal(file.length()), fontSize = 11.sp, color = BeeGray)
                    } else {
                        val count = try { file.listFiles()?.size ?: 0 } catch (_: Exception) { 0 }
                        Text("$count items", fontSize = 11.sp, color = BeeGray)
                    }
                    Text(
                        dateFormatter.format(Date(file.lastModified())),
                        fontSize = 11.sp, color = BeeGray
                    )
                }
            }

            if (file.isDirectory && !isSelecting) {
                Icon(Icons.Filled.ChevronRight, "Open", tint = BeeGray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FileIcon(file: File) {
    val ext = file.extension.lowercase()
    val (icon, color) = when {
        file.isDirectory -> Icons.Filled.Folder to BeeYellow
        ext in listOf("pdf") -> Icons.Filled.PictureAsPdf to Color(0xFFE53935)
        ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> Icons.Filled.Image to Color(0xFFAB47BC)
        ext in listOf("mp4", "mov", "webm", "avi") -> Icons.Filled.Videocam to Color(0xFFFF6B35)
        ext in listOf("mp3", "wav", "ogg", "m4a") -> Icons.Filled.MusicNote to Color(0xFF66BB6A)
        ext in listOf("html", "htm") -> Icons.Filled.Language to Color(0xFF00BCD4)
        ext in listOf("css") -> Icons.Filled.Palette to Color(0xFF2196F3)
        ext in listOf("js", "json") -> Icons.Filled.Code to Color(0xFFFFCA28)
        ext in listOf("kt", "java", "py") -> Icons.Filled.Code to Color(0xFF7E57C2)
        ext in listOf("csv", "xlsx", "xls") -> Icons.Filled.TableChart to Color(0xFF4CAF50)
        ext in listOf("docx", "doc") -> Icons.Filled.Description to Color(0xFF42A5F5)
        ext in listOf("txt", "md") -> Icons.Filled.TextSnippet to Color(0xFF90A4AE)
        ext in listOf("xml") -> Icons.Filled.DataObject to Color(0xFFFF7043)
        ext in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Filled.FolderZip to Color(0xFF8D6E63)
        ext in listOf("apk") -> Icons.Filled.Android to Color(0xFF66BB6A)
        else -> Icons.Filled.InsertDriveFile to BeeGray
    }
    Icon(icon, file.name, tint = color, modifier = Modifier.size(32.dp))
}

// ══════════════════════════════════════
// File Actions Bottom Sheet
// ══════════════════════════════════════
@Composable
private fun FileActionsSheet(
    file: File,
    context: Context,
    isAiGenerated: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAttachToChat: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BeeBlackLight,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIcon(file)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, color = BeeWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(humanSizeReal(file.length()), fontSize = 12.sp, color = BeeGray)
                    if (isAiGenerated) {
                        Text("Generado por IA", fontSize = 11.sp, color = BeeYellow)
                    }
                }
            }
        },
        text = {
            Column {
                FileActionItem(Icons.Outlined.OpenInNew, "Abrir", Color(0xFF4CAF50), onOpen)
                FileActionItem(Icons.Outlined.Share, "Compartir", Color(0xFF5AC8FA), onShare)
                if (onAttachToChat != null) {
                    FileActionItem(Icons.Outlined.Chat, "Enviar al chat", BeeYellow, onAttachToChat)
                }
                FileActionItem(Icons.Outlined.Edit, "Renombrar", Color(0xFFFF9800), onRename)
                HorizontalDivider(color = BeeGray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                FileActionItem(Icons.Outlined.Delete, "Eliminar", Color(0xFFFF4444), onDelete)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun FileActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, fontSize = 15.sp, color = BeeWhite)
        }
    }
}

// ══════════════════════════════════════
// Utilities
// ══════════════════════════════════════
private fun listFilesReal(dir: File, sortMode: SortMode): List<File> {
    if (!dir.exists()) return emptyList()
    val raw = (dir.listFiles() ?: emptyArray())
        .filter { !it.name.startsWith(".") } // Hide hidden files

    return when (sortMode) {
        SortMode.NAME -> raw.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        SortMode.SIZE -> raw.sortedWith(compareBy({ !it.isDirectory }, { -it.length() }))
        SortMode.DATE -> raw.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified() }))
        SortMode.TYPE -> raw.sortedWith(compareBy({ !it.isDirectory }, { it.extension.lowercase() }, { it.name.lowercase() }))
    }
}

private fun searchFilesRecursive(dir: File, query: String, maxResults: Int = 50): List<File> {
    val results = mutableListOf<File>()
    val q = query.lowercase()
    fun search(d: File, depth: Int) {
        if (depth > 5 || results.size >= maxResults) return
        try {
            d.listFiles()?.forEach { f ->
                if (f.name.lowercase().contains(q)) results.add(f)
                if (f.isDirectory && !f.name.startsWith(".") && results.size < maxResults) search(f, depth + 1)
            }
        } catch (_: Exception) {}
    }
    search(dir, 0)
    return results
}

private fun humanSizeReal(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes.toFloat() / 1024 / 1024)} MB"
    else -> "${"%.2f".format(bytes.toFloat() / 1024 / 1024 / 1024)} GB"
}

private fun openFileExternal(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = when (ext) {
            "pdf" -> "application/pdf"
            "html", "htm" -> "text/html"
            "csv" -> "text/csv"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "txt", "md" -> "text/plain"
            "json" -> "application/json"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No hay app para abrir este archivo", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFiles(context: Context, files: List<File>) {
    try {
        if (files.size == 1) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", files[0])
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir"))
        } else {
            val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir ${files.size} archivos"))
        }
    } catch (_: Exception) {}
}
