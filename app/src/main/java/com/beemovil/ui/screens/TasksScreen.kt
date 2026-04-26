package com.beemovil.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.google.GoogleAuthManager
import com.beemovil.google.GoogleTasksService
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuth = remember { GoogleAuthManager(context) }
    val isSignedIn = googleAuth.isSignedIn()
    val accessToken = googleAuth.getAccessToken()

    var tasks by remember { mutableStateOf<List<GoogleTasksService.TaskItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showNewTaskDialog by remember { mutableStateOf(false) }

    LaunchedEffect(accessToken) {
        if (accessToken != null) {
            isLoading = true
            errorMsg = null
            withContext(Dispatchers.IO) {
                try {
                    val service = GoogleTasksService(accessToken)
                    tasks = service.listTasks(showCompleted = false)
                } catch (e: Exception) {
                    errorMsg = e.message
                }
            }
            isLoading = false
        }
    }

    if (showNewTaskDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newNotes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTaskDialog = false },
            containerColor = dialogBg,
            title = { Text("Nueva Tarea", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Título", color = textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                            focusedBorderColor = accent, unfocusedBorderColor = textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        label = { Text("Notas (opcional)", color = textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                            focusedBorderColor = accent, unfocusedBorderColor = textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank() && accessToken != null) {
                            scope.launch {
                                showNewTaskDialog = false
                                isLoading = true
                                withContext(Dispatchers.IO) {
                                    try {
                                        val service = GoogleTasksService(accessToken)
                                        service.createTask(newTitle, newNotes)
                                        tasks = service.listTasks(showCompleted = false)
                                    } catch (e: Exception) {
                                        errorMsg = e.message
                                    }
                                }
                                isLoading = false
                            }
                        }
                    }
                ) { Text("Crear", color = accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showNewTaskDialog = false }) {
                    Text("Cancelar", color = textSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("Tareas", fontWeight = FontWeight.Bold, color = textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (accessToken != null) {
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                withContext(Dispatchers.IO) {
                                    try {
                                        val service = GoogleTasksService(accessToken)
                                        tasks = service.listTasks(showCompleted = false)
                                    } catch (e: Exception) {
                                        errorMsg = e.message
                                    }
                                }
                                isLoading = false
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, "Actualizar", tint = accent)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (accessToken != null) {
                FloatingActionButton(
                    onClick = { showNewTaskDialog = true },
                    containerColor = accent,
                    contentColor = if (isDark) BeeBlack else Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Add, "Nueva tarea")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isSignedIn || accessToken == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.TaskAlt,
                            contentDescription = "Tasks",
                            tint = accent.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            "Conecta tu cuenta de Google",
                            color = textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Para ver tus tareas, ve a Settings → Google Workspace y conecta tu cuenta.",
                            color = textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Button(
                            onClick = { viewModel.currentScreen.value = "settings" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F1F1F)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("G", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ir a Settings", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Cargando tareas...", color = textSecondary, fontSize = 14.sp)
                    }
                }
            } else if (errorMsg != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(Icons.Filled.Warning, "Error", tint = Color(0xFFF44336), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Error al cargar tareas", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMsg ?: "", color = textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.currentScreen.value = "settings" },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (isDark) BeeBlack else Color.White)
                        ) { Text("Verificar conexión Google") }
                    }
                }
            } else if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No tienes tareas pendientes", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Toca + para crear una", color = textSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accent = accent,
                            onComplete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val service = GoogleTasksService(accessToken)
                                            service.completeTask(task.id)
                                            tasks = service.listTasks(showCompleted = false)
                                        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val service = GoogleTasksService(accessToken)
                                            service.deleteTask(task.id)
                                            tasks = service.listTasks(showCompleted = false)
                                        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskItemCard(
    task: GoogleTasksService.TaskItem,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = if (isDark) 0.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onComplete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (task.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        "Complete",
                        tint = if (task.completed) Color(0xFF4CAF50) else textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        color = if (task.completed) textSecondary else textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = if (expanded) 5 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (task.dueDate != null) {
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                        Text(
                            "Vence: ${sdf.format(java.util.Date(task.dueDate))}",
                            color = accent.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
                if (expanded) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, "Eliminar", tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (expanded && task.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(task.notes, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
