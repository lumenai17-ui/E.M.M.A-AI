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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuth = remember { GoogleAuthManager(context) }
    val isSignedIn = googleAuth.isSignedIn()
    val accessToken = googleAuth.getAccessToken()

    var tasks by remember { mutableStateOf<List<GoogleTasksService.TaskItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showNewTaskDialog by remember { mutableStateOf(false) }

    // Load tasks on first open
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

    // New Task Dialog
    if (showNewTaskDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newNotes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTaskDialog = false },
            containerColor = Color(0xFF1E1E2C),
            title = { Text("Nueva Tarea", color = BeeWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Título", color = BeeGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                            focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        label = { Text("Notas (opcional)", color = BeeGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                            focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray
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
                ) { Text("Crear", color = BeeYellow, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showNewTaskDialog = false }) {
                    Text("Cancelar", color = BeeGray)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFF0F0F16),
        topBar = {
            TopAppBar(
                title = { Text("Tareas", fontWeight = FontWeight.Bold, color = BeeWhite) },
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
                            Icon(Icons.Filled.Refresh, "Actualizar", tint = BeeYellow)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (accessToken != null) {
                FloatingActionButton(
                    onClick = { showNewTaskDialog = true },
                    containerColor = BeeYellow,
                    contentColor = BeeBlack,
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
                // Not connected state
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
                            tint = BeeYellow.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            "Conecta tu cuenta de Google",
                            color = BeeWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Para ver tus tareas, ve a Settings → Google Workspace y conecta tu cuenta.",
                            color = BeeGray,
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
                        CircularProgressIndicator(color = BeeYellow, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Cargando tareas...", color = BeeGray, fontSize = 14.sp)
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
                        Text("Error al cargar tareas", color = BeeWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMsg ?: "", color = BeeGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.currentScreen.value = "settings" },
                            colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack)
                        ) { Text("Verificar conexión Google") }
                    }
                }
            } else if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No tienes tareas pendientes", color = BeeWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Toca + para crear una", color = BeeGray, fontSize = 14.sp)
                    }
                }
            } else {
                // Task list
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onComplete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val service = GoogleTasksService(accessToken)
                                            service.completeTask(task.id)
                                            tasks = service.listTasks(showCompleted = false)
                                        } catch (_: Exception) {}
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
                                        } catch (_: Exception) {}
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
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF161622),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Checkbox
                IconButton(
                    onClick = onComplete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (task.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        "Complete",
                        tint = if (task.completed) Color(0xFF4CAF50) else BeeGray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        color = if (task.completed) BeeGray else BeeWhite,
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
                            color = BeeYellow.copy(alpha = 0.7f),
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
                Text(task.notes, color = BeeGray, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
