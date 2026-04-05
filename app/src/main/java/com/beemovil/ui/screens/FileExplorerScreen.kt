package com.beemovil.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    onBack: () -> Unit,
    onOpenFile: ((String) -> Unit)? = null
) {
    val beeDir = File(Environment.getExternalStorageDirectory(), "BeeMovil")
    var currentDir by remember { mutableStateOf(beeDir) }
    var files by remember { mutableStateOf(listFiles(beeDir)) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }

    // Breadcrumb path
    val pathParts = remember(currentDir) {
        val rel = currentDir.absolutePath.removePrefix(Environment.getExternalStorageDirectory().absolutePath)
        rel.split("/").filter { it.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
    ) {
        // ══════ Top Bar ══════
        TopAppBar(
            title = {
                Column {
                    Text("Archivos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        currentDir.absolutePath.removePrefix(Environment.getExternalStorageDirectory().absolutePath),
                        fontSize = 11.sp, color = BeeGray, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (currentDir != beeDir && currentDir.parentFile != null &&
                        currentDir.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath)) {
                        currentDir = currentDir.parentFile!!
                        files = listFiles(currentDir)
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow)
                }
            },
            actions = {
                // Go to BeeMovil root
                IconButton(onClick = {
                    currentDir = beeDir
                    files = listFiles(beeDir)
                }) {
                    Icon(Icons.Filled.Home, "Home", tint = BeeGray)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BeeBlackLight,
                titleContentColor = BeeWhite
            )
        )

        // ══════ Quick Access Chips ══════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BeeBlackLight)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickDirs = listOf(
                "BeeMovil" to beeDir,
                "Projects" to File(beeDir, "projects"),
                "Repos" to File(beeDir, "repos"),
                "Downloads" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            )
            quickDirs.forEach { (label, dir) ->
                FilterChip(
                    selected = currentDir == dir,
                    onClick = {
                        if (dir.exists()) {
                            currentDir = dir
                            files = listFiles(dir)
                        }
                    },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BeeYellow,
                        selectedLabelColor = BeeBlack,
                        containerColor = BeeGray.copy(alpha = 0.2f),
                        labelColor = BeeGray
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        // ══════ Stats bar ══════
        val dirCount = files.count { it.isDirectory }
        val fileCount = files.count { it.isFile }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$dirCount carpetas, $fileCount archivos", fontSize = 12.sp, color = BeeGray)
            Text(humanSize(files.filter { it.isFile }.sumOf { it.length() }), fontSize = 12.sp, color = BeeGray)
        }

        // ══════ File List ══════
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            items(files) { file ->
                FileItem(
                    file = file,
                    dateFormatter = dateFormatter,
                    onClick = {
                        if (file.isDirectory) {
                            currentDir = file
                            files = listFiles(file)
                        } else {
                            onOpenFile?.invoke(file.absolutePath)
                        }
                    }
                )
            }

            if (files.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.FolderOpen, "Empty", tint = BeeGray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Carpeta vacia", color = BeeGray, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItem(
    file: File,
    dateFormatter: SimpleDateFormat,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = BeeBlackLight,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            val (icon, color) = when {
                file.isDirectory -> Icons.Filled.Folder to BeeYellow
                file.extension.lowercase() in listOf("pdf") -> Icons.Filled.PictureAsPdf to Color(0xFFE53935)
                file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif") -> Icons.Filled.Image to Color(0xFFAB47BC)
                file.extension.lowercase() in listOf("html", "htm") -> Icons.Filled.Language to Color(0xFF00BCD4)
                file.extension.lowercase() in listOf("css") -> Icons.Filled.Palette to Color(0xFF2196F3)
                file.extension.lowercase() in listOf("js", "json") -> Icons.Filled.Code to Color(0xFFFFCA28)
                file.extension.lowercase() in listOf("kt", "java") -> Icons.Filled.Code to Color(0xFF7E57C2)
                file.extension.lowercase() in listOf("csv", "xlsx", "xls") -> Icons.Filled.TableChart to Color(0xFF4CAF50)
                file.extension.lowercase() in listOf("docx", "doc", "txt", "md") -> Icons.Filled.Description to Color(0xFF42A5F5)
                file.extension.lowercase() in listOf("xml") -> Icons.Filled.DataObject to Color(0xFFFF7043)
                else -> Icons.Filled.InsertDriveFile to BeeGray
            }

            Icon(icon, file.name, tint = color, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = BeeWhite,
                    fontSize = 14.sp,
                    fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (file.isFile) {
                        Text(humanSize(file.length()), fontSize = 11.sp, color = BeeGray)
                    } else {
                        val count = file.listFiles()?.size ?: 0
                        Text("$count items", fontSize = 11.sp, color = BeeGray)
                    }
                    Text(
                        dateFormatter.format(Date(file.lastModified())),
                        fontSize = 11.sp, color = BeeGray
                    )
                }
            }

            if (file.isDirectory) {
                Icon(Icons.Filled.ChevronRight, "Open", tint = BeeGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun listFiles(dir: File): List<File> {
    if (!dir.exists()) {
        dir.mkdirs()
        return emptyList()
    }
    return (dir.listFiles() ?: emptyArray())
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes.toFloat() / 1024 / 1024)} MB"
    else -> "${"%.2f".format(bytes.toFloat() / 1024 / 1024 / 1024)} GB"
}
