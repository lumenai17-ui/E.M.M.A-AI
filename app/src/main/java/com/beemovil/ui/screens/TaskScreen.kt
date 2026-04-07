package com.beemovil.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.memory.BeeTask
import com.beemovil.memory.TaskDB
import com.beemovil.memory.TaskPriority
import com.beemovil.memory.TaskStatus
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// Premium palette
private val Bg = Color(0xFF08080A)
private val CardBg = Color(0xFF111118)
private val CardBorder = Color(0xFF1C1C2E)
private val Gold = Color(0xFFF5A623)
private val Txt = Color(0xFFF2F2F7)
private val TxtSub = Color(0xFF8E8E9A)
private val TxtMuted = Color(0xFF555566)
private val Green = Color(0xFF34C759)
private val Red = Color(0xFFFF3B30)
private val Orange = Color(0xFFFF9500)
private val Blue = Color(0xFF0A84FF)
private val Purple = Color(0xFFBF5AF2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: ChatViewModel,
    taskDB: TaskDB
) {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(taskDB.getAllTasks()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<BeeTask?>(null) }
    var currentFilter by remember { mutableStateOf<TaskStatus?>(null) }
    var showCalendar by remember { mutableStateOf(false) }

    // Refresh tasks
    fun refresh() { tasks = taskDB.getAllTasks(currentFilter) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top Bar ──
        Surface(
            color = CardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.TaskAlt, "Tasks", tint = Gold, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tareas", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Txt)
                        val pending = tasks.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS }
                        Text("$pending pendientes", fontSize = 13.sp, color = TxtSub)
                    }
                    // Refresh button
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = TxtSub)
                    }
                    // Calendar toggle
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(
                            if (showCalendar) Icons.Filled.ViewList else Icons.Outlined.CalendarMonth,
                            "Toggle view",
                            tint = if (showCalendar) Gold else TxtSub
                        )
                    }
                    // Add button
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "Add task", tint = Gold)
                    }
                }

                // Filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipItem("Todas", currentFilter == null, Gold) {
                        currentFilter = null; refresh()
                    }
                    FilterChipItem("Pendiente", currentFilter == TaskStatus.PENDING, Orange) {
                        currentFilter = TaskStatus.PENDING; refresh()
                    }
                    FilterChipItem("En curso", currentFilter == TaskStatus.IN_PROGRESS, Blue) {
                        currentFilter = TaskStatus.IN_PROGRESS; refresh()
                    }
                    FilterChipItem("Listas", currentFilter == TaskStatus.COMPLETED, Green) {
                        currentFilter = TaskStatus.COMPLETED; refresh()
                    }
                }
            }
        }

        // ── Content ──
        if (showCalendar) {
            CalendarView(taskDB = taskDB, onTaskClick = { editingTask = it })
        } else {
            if (tasks.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.TaskAlt, "No tasks", tint = TxtMuted, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sin tareas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TxtSub)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Toca + para agregar una tarea", fontSize = 14.sp, color = TxtMuted)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Filled.Add, "Add", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nueva tarea", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggle = {
                                if (task.status == TaskStatus.COMPLETED) {
                                    taskDB.updateTask(task.copy(status = TaskStatus.PENDING, completedAt = 0))
                                } else {
                                    taskDB.completeTask(task.id)
                                }
                                refresh()
                            },
                            onEdit = { editingTask = task },
                            onDelete = { taskDB.deleteTask(task.id); refresh() }
                        )
                    }
                }
            }
        }
    }

    // Add/Edit dialog
    if (showAddDialog || editingTask != null) {
        TaskEditDialog(
            existing = editingTask,
            onDismiss = { showAddDialog = false; editingTask = null },
            onSave = { task ->
                if (editingTask != null) {
                    taskDB.updateTask(task)
                } else {
                    taskDB.addTask(task)
                }
                showAddDialog = false
                editingTask = null
                refresh()
            }
        )
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) color.copy(alpha = 0.15f) else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = if (!selected) null else null
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) color else TxtMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    task: BeeTask,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = task.status == TaskStatus.COMPLETED
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> Red
        TaskPriority.HIGH -> Orange
        TaskPriority.NORMAL -> Blue
        TaskPriority.LOW -> TxtMuted
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor)
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Checkbox
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                if (isCompleted) {
                    Icon(Icons.Filled.CheckCircle, "Done", tint = Green, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Outlined.RadioButtonUnchecked, "Pending", tint = TxtMuted, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCompleted) TxtMuted else Txt,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.description.isNotBlank()) {
                    Text(
                        task.description,
                        fontSize = 13.sp,
                        color = TxtSub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Due date
                if (task.dueDate > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale("es")).format(Date(task.dueDate))
                    val isOverdue = task.dueDate < System.currentTimeMillis() && !isCompleted
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Schedule,
                            "Due",
                            tint = if (isOverdue) Red else TxtMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            dateStr,
                            fontSize = 12.sp,
                            color = if (isOverdue) Red else TxtMuted
                        )
                    }
                }
            }

            // Delete
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Close, "Delete", tint = TxtMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CalendarView(taskDB: TaskDB, onTaskClick: (BeeTask) -> Unit) {
    val calendar = remember { Calendar.getInstance() }
    var currentMonth by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var currentYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }

    // Get tasks for this month
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, currentYear)
        set(Calendar.MONTH, currentMonth)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
    }
    val startOfMonth = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    val endOfMonth = cal.timeInMillis
    val monthTasks = remember(currentMonth, currentYear) {
        taskDB.getTasksByDate(startOfMonth, endOfMonth)
    }
    val taskDays = monthTasks.groupBy {
        Calendar.getInstance().apply { timeInMillis = it.dueDate }.get(Calendar.DAY_OF_MONTH)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Month navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                if (currentMonth == 0) { currentMonth = 11; currentYear-- } else currentMonth--
            }) {
                Icon(Icons.Filled.ChevronLeft, "Prev", tint = Txt)
            }
            val monthName = SimpleDateFormat("MMMM yyyy", Locale("es")).format(
                Calendar.getInstance().apply { set(Calendar.MONTH, currentMonth); set(Calendar.YEAR, currentYear) }.time
            ).replaceFirstChar { it.uppercase() }
            Text(monthName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Txt)
            IconButton(onClick = {
                if (currentMonth == 11) { currentMonth = 0; currentYear++ } else currentMonth++
            }) {
                Icon(Icons.Filled.ChevronRight, "Next", tint = Txt)
            }
        }

        // Day headers
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach { day ->
                Text(day, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TxtMuted,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Calendar grid
        val firstDay = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val startOffset = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
        val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.MONTH) == currentMonth && today.get(Calendar.YEAR) == currentYear
        val todayDay = today.get(Calendar.DAY_OF_MONTH)

        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val day = cellIndex - startOffset + 1

                        if (day in 1..daysInMonth) {
                            val hasTasks = taskDays.containsKey(day)
                            val isToday = isCurrentMonth && day == todayDay
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isToday -> Gold.copy(alpha = 0.2f)
                                            hasTasks -> Blue.copy(alpha = 0.08f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable {
                                        taskDays[day]?.firstOrNull()?.let { onTaskClick(it) }
                                    }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$day",
                                        fontSize = 15.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) Gold else Txt
                                    )
                                    if (hasTasks) {
                                        val count = taskDays[day]!!.size
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (count > 1) Orange else Blue)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Tasks for today display
        Spacer(modifier = Modifier.height(16.dp))
        if (monthTasks.isNotEmpty()) {
            Text(
                "Tareas del mes",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TxtSub,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(monthTasks) { task ->
                    val dateStr = SimpleDateFormat("dd MMM", Locale("es")).format(Date(task.dueDate))
                    Surface(
                        onClick = { onTaskClick(task) },
                        color = CardBg,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dateStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(task.title, fontSize = 14.sp, color = Txt, modifier = Modifier.weight(1f))
                            val pColor = when (task.priority) {
                                TaskPriority.URGENT -> Red
                                TaskPriority.HIGH -> Orange
                                else -> TxtMuted
                            }
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(pColor))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditDialog(
    existing: BeeTask?,
    onDismiss: () -> Unit,
    onSave: (BeeTask) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var priority by remember { mutableStateOf(existing?.priority ?: TaskPriority.NORMAL) }
    var status by remember { mutableStateOf(existing?.status ?: TaskStatus.PENDING) }
    var hasDueDate by remember { mutableStateOf((existing?.dueDate ?: 0) > 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Text(
                if (existing != null) "Editar tarea" else "Nueva tarea",
                color = Txt, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titulo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Txt,
                        unfocusedTextColor = Txt,
                        cursorColor = Gold,
                        focusedLabelColor = Gold,
                        unfocusedLabelColor = TxtMuted
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripcion (opcional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Txt,
                        unfocusedTextColor = Txt,
                        cursorColor = Gold,
                        focusedLabelColor = Gold,
                        unfocusedLabelColor = TxtMuted
                    )
                )

                // Priority selector
                Text("Prioridad", fontSize = 13.sp, color = TxtSub)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { p ->
                        val pColor = when (p) {
                            TaskPriority.URGENT -> Red
                            TaskPriority.HIGH -> Orange
                            TaskPriority.NORMAL -> Blue
                            TaskPriority.LOW -> TxtMuted
                        }
                        val label = when (p) {
                            TaskPriority.URGENT -> "Urgente"
                            TaskPriority.HIGH -> "Alta"
                            TaskPriority.NORMAL -> "Normal"
                            TaskPriority.LOW -> "Baja"
                        }
                        Surface(
                            onClick = { priority = p },
                            color = if (priority == p) pColor.copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                label, fontSize = 12.sp,
                                color = if (priority == p) pColor else TxtMuted,
                                fontWeight = if (priority == p) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Status (only for editing)
                if (existing != null) {
                    Text("Estado", fontSize = 13.sp, color = TxtSub)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TaskStatus.PENDING, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED).forEach { s ->
                            val sLabel = when (s) {
                                TaskStatus.PENDING -> "Pendiente"
                                TaskStatus.IN_PROGRESS -> "En curso"
                                TaskStatus.COMPLETED -> "Lista"
                                TaskStatus.CANCELLED -> "Cancelada"
                            }
                            val sColor = when (s) {
                                TaskStatus.PENDING -> Orange
                                TaskStatus.IN_PROGRESS -> Blue
                                TaskStatus.COMPLETED -> Green
                                TaskStatus.CANCELLED -> Red
                            }
                            Surface(
                                onClick = { status = s },
                                color = if (status == s) sColor.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    sLabel, fontSize = 12.sp,
                                    color = if (status == s) sColor else TxtMuted,
                                    fontWeight = if (status == s) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        onSave(
                            BeeTask(
                                id = existing?.id ?: 0,
                                title = title.trim(),
                                description = description.trim(),
                                status = status,
                                priority = priority,
                                agentId = existing?.agentId ?: "main",
                                dueDate = existing?.dueDate ?: 0,
                                createdAt = existing?.createdAt ?: now,
                                completedAt = if (status == TaskStatus.COMPLETED) now else 0
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                enabled = title.isNotBlank()
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TxtSub)
            }
        }
    )
}
