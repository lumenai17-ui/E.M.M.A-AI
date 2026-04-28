package com.beemovil.ui.screens

import android.content.Intent
import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.tasks.EmmaTask
import com.beemovil.tasks.EmmaSubtask
import com.beemovil.tasks.TaskAttachment
import com.beemovil.tasks.EmmaTaskDB
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: ChatViewModel) {
    val isDark = isDarkTheme()
    val bg = if (isDark) Color(0xFF0F0F16) else LightBackground
    val textPrimary = if (isDark) BeeWhite else TextDark
    val textSecondary = if (isDark) BeeGray else TextGrayDark
    val accent = if (isDark) BeeYellow else BrandBlue
    val cardBg = if (isDark) Color(0xFF161622) else LightSurface
    val dialogBg = if (isDark) Color(0xFF1E1E2C) else LightSurface
    val urgentColor = Color(0xFFE53935)
    val highColor = Color(0xFFFFA726)
    val lowColor = Color(0xFF42A5F5)
    val completedColor = Color(0xFF66BB6A)

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { EmmaTaskDB.getDatabase(context) }
    val dao = remember { db.taskDao() }

    var tasks by remember { mutableStateOf<List<EmmaTask>>(emptyList()) }
    var subtasksMap by remember { mutableStateOf<Map<String, List<EmmaSubtask>>>(emptyMap()) }
    var attachmentsMap by remember { mutableStateOf<Map<String, List<TaskAttachment>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var activeFilter by remember { mutableStateOf("all") }
    var showNewTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<EmmaTask?>(null) }
    var expandedTaskId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    // File picker for manual attachment
    var attachToTaskId by remember { mutableStateOf<String?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && attachToTaskId != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                        val name = uri.lastPathSegment?.substringAfterLast("/") ?: "archivo"
                        val dir = java.io.File(context.filesDir, "task_attachments").also { it.mkdirs() }
                        val file = java.io.File(dir, "${System.currentTimeMillis()}_$name")
                        input?.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                        val mime = context.contentResolver.getType(uri) ?: "*/*"
                        dao.insertAttachment(TaskAttachment(
                            taskId = attachToTaskId!!,
                            fileName = name,
                            filePath = file.absolutePath,
                            mimeType = mime,
                            sizeBytes = file.length()
                        ))
                    } catch (_: Exception) {}
                }
                attachToTaskId = null
                // Inline reload (loadTasks defined later)
                withContext(Dispatchers.IO) {
                    val loaded = dao.getAllActiveTasks()
                    val sMap = mutableMapOf<String, List<EmmaSubtask>>()
                    val aMap = mutableMapOf<String, List<TaskAttachment>>()
                    loaded.forEach { t -> sMap[t.id] = dao.getSubtasks(t.id); aMap[t.id] = dao.getAttachments(t.id) }
                    tasks = loaded; subtasksMap = sMap; attachmentsMap = aMap
                }
            }
        }
    }

    // Load tasks
    fun loadTasks() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                val loaded = when (activeFilter) {
                    "mine" -> dao.getTasksByAssigneeType("user")
                    "emma" -> dao.getTasksByAssigneeType("emma")
                    "delegated" -> dao.getTasksByAssigneeType("external")
                    "completed" -> dao.getCompletedTasks(30)
                    else -> dao.getAllActiveTasks()
                }
                tasks = loaded
                val sMap = mutableMapOf<String, List<EmmaSubtask>>()
                val aMap = mutableMapOf<String, List<TaskAttachment>>()
                loaded.forEach { t ->
                    sMap[t.id] = dao.getSubtasks(t.id)
                    aMap[t.id] = dao.getAttachments(t.id)
                }
                subtasksMap = sMap
                attachmentsMap = aMap
            }
            isLoading = false
        }
    }

    LaunchedEffect(activeFilter) { loadTasks() }

    // Counts
    val pendingCount = tasks.count { it.status != "completed" }
    val completedCount = tasks.count { it.status == "completed" }
    val overdueCount = tasks.count { it.status != "completed" && it.dueDate != null && it.dueDate < System.currentTimeMillis() }

    // ═══════════════════════════════════════
    //  NEW/EDIT TASK DIALOG
    // ═══════════════════════════════════════
    if (showNewTaskDialog || editingTask != null) {
        val isEdit = editingTask != null
        var title by remember(editingTask) { mutableStateOf(editingTask?.title ?: "") }
        var notes by remember(editingTask) { mutableStateOf(editingTask?.notes ?: "") }
        var priority by remember(editingTask) { mutableStateOf(editingTask?.priority ?: 0) }
        var assignee by remember(editingTask) { mutableStateOf(editingTask?.assignee ?: "user") }
        var tags by remember(editingTask) { mutableStateOf(editingTask?.tags ?: "") }
        var dueDateStr by remember(editingTask) {
            mutableStateOf(
                if (editingTask?.dueDate != null) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(editingTask!!.dueDate!!))
                else ""
            )
        }

        AlertDialog(
            onDismissRequest = { showNewTaskDialog = false; editingTask = null },
            containerColor = dialogBg,
            title = {
                Text(
                    if (isEdit) "Editar Tarea" else "Nueva Tarea",
                    color = textPrimary, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Título", color = textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accent, unfocusedBorderColor = textSecondary),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        label = { Text("Notas", color = textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accent, unfocusedBorderColor = textSecondary),
                        modifier = Modifier.fillMaxWidth(), maxLines = 3
                    )
                    // Date picker
                    val cal = Calendar.getInstance()
                    val dateLabel = if (dueDateStr.isNotBlank()) "📅 $dueDateStr" else "📅 Sin fecha límite"
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(context, { _, y, m, d ->
                                dueDateStr = String.format("%04d-%02d-%02d", y, m + 1, d)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, textSecondary.copy(alpha = 0.5f))
                    ) {
                        Text(dateLabel, color = textPrimary, fontSize = 14.sp)
                    }
                    // Priority selector
                    Text("Prioridad", color = textSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Normal", 1 to "Baja", 2 to "Alta", 3 to "Urgente").forEach { (p, label) ->
                            val selected = priority == p
                            val chipColor = when (p) { 3 -> urgentColor; 2 -> highColor; 1 -> lowColor; else -> textSecondary }
                            FilterChip(
                                selected = selected, onClick = { priority = p },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipColor.copy(alpha = 0.2f),
                                    selectedLabelColor = chipColor
                                )
                            )
                        }
                    }
                    // Assignee
                    Text("Asignar a", color = textSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("user" to "Yo", "emma" to "Emma").forEach { (a, label) ->
                            FilterChip(
                                selected = assignee == a, onClick = { assignee = a },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.2f), selectedLabelColor = accent)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = tags, onValueChange = { tags = it },
                        label = { Text("Tags (comma-sep)", color = textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accent, unfocusedBorderColor = textSecondary),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        scope.launch {
                            val dueMs = try { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dueDateStr)?.time } catch (_: Exception) { null }
                            val assigneeType = when (assignee) { "user" -> "user"; "emma" -> "emma"; else -> "external" }
                            withContext(Dispatchers.IO) {
                                if (isEdit) {
                                    dao.updateTask(editingTask!!.copy(title = title, notes = notes, priority = priority, assignee = assignee, assigneeType = assigneeType, dueDate = dueMs, tags = tags.ifBlank { null }, updatedAt = System.currentTimeMillis()))
                                } else {
                                    dao.insertTask(EmmaTask(title = title, notes = notes, priority = priority, assignee = assignee, assigneeType = assigneeType, dueDate = dueMs, tags = tags.ifBlank { null }, source = "manual"))
                                }
                            }
                            showNewTaskDialog = false; editingTask = null; loadTasks()
                        }
                    }
                }) { Text(if (isEdit) "Guardar" else "Crear", color = accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showNewTaskDialog = false; editingTask = null }) { Text("Cancelar", color = textSecondary) }
            }
        )
    }

    // ═══════════════════════════════════════
    //  MAIN SCAFFOLD
    // ═══════════════════════════════════════
    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tareas", fontWeight = FontWeight.Bold, color = textPrimary)
                        if (pendingCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = accent.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
                                Text("$pendingCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { loadTasks() }) { Icon(Icons.Filled.Refresh, "Refresh", tint = accent) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewTaskDialog = true },
                containerColor = accent,
                contentColor = if (isDark) Color.Black else Color.White,
                shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Filled.Add, "Nueva tarea") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filter chips
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "Todas", "mine" to "Mías", "emma" to "Emma", "delegated" to "Delegadas", "completed" to "✓").forEach { (f, label) ->
                    FilterChip(
                        selected = activeFilter == f, onClick = { activeFilter = f },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.2f), selectedLabelColor = accent)
                    )
                }
            }
            // Search bar
            OutlinedTextField(
                value = searchQuery, onValueChange = { q ->
                    searchQuery = q
                    if (q.isBlank()) { loadTasks() } else {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                tasks = dao.searchTasks(q)
                                val sMap = mutableMapOf<String, List<EmmaSubtask>>()
                                val aMap = mutableMapOf<String, List<TaskAttachment>>()
                                tasks.forEach { t -> sMap[t.id] = dao.getSubtasks(t.id); aMap[t.id] = dao.getAttachments(t.id) }
                                subtasksMap = sMap; attachmentsMap = aMap
                            }
                        }
                    }
                },
                placeholder = { Text("🔍 Buscar tareas...", color = textSecondary) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accent, unfocusedBorderColor = textSecondary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(48.dp),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
            // Stats bar
            if (!isLoading && tasks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📋 $pendingCount pendientes", color = textSecondary, fontSize = 11.sp)
                    if (overdueCount > 0) Text("⚠️ $overdueCount vencidas", color = urgentColor, fontSize = 11.sp)
                    Text("✅ $completedCount completadas", color = completedColor, fontSize = 11.sp)
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, modifier = Modifier.size(40.dp))
                }
            } else if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(if (activeFilter == "completed") "No hay completadas aún" else "No tienes tareas pendientes", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Toca + para crear una, o pídele a Emma", color = textSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        val isExpanded = expandedTaskId == task.id
                        val subs = subtasksMap[task.id] ?: emptyList()
                        val attachments = attachmentsMap[task.id] ?: emptyList()
                        val subDone = subs.count { it.completed }
                        val isOverdue = task.status != "completed" && task.dueDate != null && task.dueDate < System.currentTimeMillis()
                        val priorityColor = when (task.priority) { 3 -> urgentColor; 2 -> highColor; 1 -> lowColor; else -> Color.Transparent }
                        val borderColor = if (isOverdue) urgentColor else priorityColor

                        Surface(
                            color = cardBg,
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = if (isDark) 0.dp else 2.dp,
                            border = if (borderColor != Color.Transparent) androidx.compose.foundation.BorderStroke(1.5.dp, borderColor.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.fillMaxWidth().animateContentSize().clickable { expandedTaskId = if (isExpanded) null else task.id }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Complete button
                                    IconButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                if (task.status == "completed") {
                                                    dao.updateTask(task.copy(status = "pending", completedAt = null, updatedAt = System.currentTimeMillis()))
                                                } else {
                                                    dao.updateTask(task.copy(status = "completed", completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                                                }
                                            }
                                            loadTasks()
                                        }
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            if (task.status == "completed") Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                            "Toggle", tint = if (task.status == "completed") completedColor else textSecondary, modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            task.title, color = if (task.status == "completed") textSecondary else textPrimary,
                                            fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                            textDecoration = if (task.status == "completed") TextDecoration.LineThrough else TextDecoration.None,
                                            maxLines = if (isExpanded) 5 else 1, overflow = TextOverflow.Ellipsis
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            // Assignee
                                            val assignLabel = when (task.assigneeType) { "user" -> "👤 Yo"; "emma" -> "🧠 Emma"; else -> "👥 ${task.assignee.take(12)}" }
                                            Text(assignLabel, color = textSecondary, fontSize = 11.sp)
                                            // Due date
                                            if (task.dueDate != null) {
                                                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                                                val dueLabel = if (isOverdue) "⚠️ ${sdf.format(Date(task.dueDate))}" else "📅 ${sdf.format(Date(task.dueDate))}"
                                                Text(dueLabel, color = if (isOverdue) urgentColor else textSecondary, fontSize = 11.sp)
                                            }
                                            // Subtasks progress
                                            if (subs.isNotEmpty()) {
                                                Text("☑ $subDone/${subs.size}", color = if (subDone == subs.size) completedColor else textSecondary, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    // Priority indicator
                                    if (task.priority >= 2) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(priorityColor))
                                    }
                                }
                                // Tags
                                if (!task.tags.isNullOrBlank()) {
                                    Row(Modifier.padding(start = 40.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        task.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(4).forEach { tag ->
                                            Surface(color = accent.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                                                Text(tag, fontSize = 10.sp, color = accent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                }
                                // Expanded: notes, subtasks, attachments, actions
                                if (isExpanded) {
                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider(color = if (isDark) Color(0xFF2A2A3D) else Color(0xFFE0E0E0))
                                    // Selectable notes
                                    if (task.notes.isNotBlank()) {
                                        SelectionContainer {
                                            Text(task.notes, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                                        }
                                    }
                                    // Subtasks + inline add
                                    if (subs.isNotEmpty() || true) {
                                        Spacer(Modifier.height(8.dp))
                                        if (subs.isNotEmpty()) Text("Sub-tareas ($subDone/${subs.size})", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        subs.forEach { sub ->
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                                                IconButton(onClick = {
                                                    scope.launch {
                                                        withContext(Dispatchers.IO) { dao.toggleSubtask(sub.id, !sub.completed, if (!sub.completed) System.currentTimeMillis() else null) }
                                                        loadTasks()
                                                    }
                                                }, modifier = Modifier.size(24.dp)) {
                                                    Icon(if (sub.completed) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, "Toggle", tint = if (sub.completed) completedColor else textSecondary, modifier = Modifier.size(18.dp))
                                                }
                                                SelectionContainer {
                                                    Text(sub.title, color = if (sub.completed) textSecondary else textPrimary, fontSize = 13.sp, textDecoration = if (sub.completed) TextDecoration.LineThrough else TextDecoration.None)
                                                }
                                            }
                                        }
                                        // Inline add subtask
                                        var newSubTitle by remember { mutableStateOf("") }
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                                            OutlinedTextField(
                                                value = newSubTitle, onValueChange = { newSubTitle = it },
                                                placeholder = { Text("+ Sub-tarea...", fontSize = 12.sp, color = textSecondary.copy(alpha = 0.5f)) },
                                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accent, unfocusedBorderColor = Color.Transparent),
                                                modifier = Modifier.weight(1f).height(40.dp), singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(onDone = {
                                                    if (newSubTitle.isNotBlank()) {
                                                        val t = newSubTitle; newSubTitle = ""
                                                        scope.launch { withContext(Dispatchers.IO) { dao.insertSubtask(EmmaSubtask(taskId = task.id, title = t, sortOrder = subs.size)) }; loadTasks() }
                                                    }
                                                })
                                            )
                                            if (newSubTitle.isNotBlank()) {
                                                IconButton(onClick = {
                                                    val t = newSubTitle; newSubTitle = ""
                                                    scope.launch { withContext(Dispatchers.IO) { dao.insertSubtask(EmmaSubtask(taskId = task.id, title = t, sortOrder = subs.size)) }; loadTasks() }
                                                }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Filled.Check, "Add", tint = completedColor, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    // Attachments
                                    if (attachments.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text("Adjuntos (${attachments.size})", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        attachments.forEach { att ->
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                                                val icon = when { att.mimeType?.startsWith("image") == true -> "🖼️"; att.mimeType?.contains("pdf") == true -> "📄"; else -> "📎" }
                                                val sizeLabel = if (att.sizeBytes != null) " (${att.sizeBytes / 1024} KB)" else ""
                                                Text("$icon ${att.fileName}$sizeLabel", color = accent, fontSize = 12.sp, modifier = Modifier.weight(1f)
                                                    .clickable {
                                                        try {
                                                            val file = java.io.File(att.filePath)
                                                            if (!file.exists()) {
                                                                android.widget.Toast.makeText(context, "Archivo no encontrado", android.widget.Toast.LENGTH_SHORT).show()
                                                                return@clickable
                                                            }
                                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, att.mimeType ?: "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            android.widget.Toast.makeText(context, "No se pudo abrir: ${e.message?.take(40)}", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    })
                                                // Share individual file
                                                IconButton(onClick = {
                                                    try {
                                                        val file = java.io.File(att.filePath)
                                                        if (!file.exists()) {
                                                            android.widget.Toast.makeText(context, "Archivo no encontrado", android.widget.Toast.LENGTH_SHORT).show()
                                                            return@IconButton
                                                        }
                                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                                            type = att.mimeType ?: "*/*"
                                                            putExtra(Intent.EXTRA_STREAM, uri)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(Intent.createChooser(intent, "Enviar archivo"))
                                                    } catch (e: Exception) {
                                                        android.widget.Toast.makeText(context, "No se pudo compartir: ${e.message?.take(40)}", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Filled.Share, "Share", tint = accent, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                    // Action buttons: Edit, Share, Delete
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { editingTask = task }, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, accent), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                            Icon(Icons.Filled.Edit, "Edit", tint = accent, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Editar", color = accent, fontSize = 11.sp)
                                        }
                                        // Share button
                                        OutlinedButton(onClick = {
                                            val shareText = buildString {
                                                appendLine("📋 ${task.title}")
                                                if (task.notes.isNotBlank()) appendLine("📝 ${task.notes}")
                                                if (task.dueDate != null) appendLine("📅 Vence: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(task.dueDate))}")
                                                val aLabel = when (task.assigneeType) { "user" -> "Yo"; "emma" -> "Emma"; else -> task.assignee }
                                                appendLine("👤 Asignada a: $aLabel")
                                                if (!task.tags.isNullOrBlank()) appendLine("🏷️ ${task.tags}")
                                                if (subs.isNotEmpty()) appendLine("☑ Sub-tareas: $subDone/${subs.size}")
                                            }
                                            if (attachments.isNotEmpty()) {
                                                val uris = ArrayList<android.net.Uri>()
                                                attachments.forEach { att ->
                                                    try {
                                                        val file = java.io.File(att.filePath)
                                                        if (file.exists()) uris.add(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                                                    } catch (_: Exception) {}
                                                }
                                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                    type = "*/*"
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Compartir tarea"))
                                            } else {
                                                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }
                                                context.startActivity(Intent.createChooser(intent, "Compartir tarea"))
                                            }
                                        }, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                            Icon(Icons.Filled.Share, "Share", tint = accent, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Compartir", color = accent, fontSize = 11.sp)
                                        }
                                        OutlinedButton(onClick = {
                                            scope.launch { withContext(Dispatchers.IO) { dao.deleteTask(task) }; expandedTaskId = null; loadTasks() }
                                        }, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, urgentColor.copy(alpha = 0.5f)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                            Icon(Icons.Filled.Delete, "Delete", tint = urgentColor, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Eliminar", color = urgentColor, fontSize = 11.sp)
                                        }
                                    }
                                    // Second row: Attach file
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        OutlinedButton(onClick = {
                                            attachToTaskId = task.id
                                            filePickerLauncher.launch(arrayOf("*/*"))
                                        }, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                            Icon(Icons.Filled.AttachFile, "Attach", tint = accent, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Adjuntar", color = accent, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
