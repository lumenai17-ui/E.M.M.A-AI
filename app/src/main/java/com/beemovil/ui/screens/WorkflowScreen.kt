package com.beemovil.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.agent.*
import com.beemovil.llm.ModelRegistry
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import com.beemovil.workflow.WorkflowHistoryDB
import com.beemovil.workflow.WorkflowRunRecord
import com.beemovil.workflow.CustomWorkflowDB
import com.beemovil.workflow.CustomWorkflowRecord

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * WorkflowScreen — Visual workflow executor with:
 * - Template gallery (tab 1)
 * - Run history with delete (tab 2)
 * - Per-step model selector
 * - Error recovery UI (retry/skip/cancel)
 * - File delivery on completion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onSendToChat: ((String, String) -> Unit)? = null,
    historyDB: WorkflowHistoryDB? = null,
    customDB: CustomWorkflowDB? = null
) {
    var selectedWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var userInput by remember { mutableStateOf("") }
    var workflowRun by remember { mutableStateOf<WorkflowRun?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }  // 0=Templates, 1=Mis Workflows, 2=History
    var historyRuns by remember { mutableStateOf<List<WorkflowRunRecord>>(emptyList()) }
    var showHistoryDetail by remember { mutableStateOf<WorkflowRunRecord?>(null) }
    var customWorkflows by remember { mutableStateOf<List<CustomWorkflowRecord>>(emptyList()) }
    var showEditor by remember { mutableStateOf(false) }
    var editingCustomRecord by remember { mutableStateOf<CustomWorkflowRecord?>(null) }

    // Per-step model overrides
    val modelOverrides = remember { mutableStateMapOf<String, String>() }
    var showModelPicker by remember { mutableStateOf<String?>(null) } // step ID

    // Load data per tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            customWorkflows = customDB?.getAllWorkflows() ?: emptyList()
        }
        if (selectedTab == 2) {
            historyRuns = historyDB?.getAllRuns() ?: emptyList()
        }
    }

    // Workflow runner
    val runner = remember {
        WorkflowRunner(
            agentResolver = { agentId -> viewModel.resolveAgent(agentId) },
            skills = viewModel.getSkills(),
            onStateChanged = { run ->
                workflowRun = run
                if (run.isComplete || run.isFailed || run.isCancelled) {
                    isRunning = false
                    // Refresh history
                    historyRuns = historyDB?.getAllRuns() ?: emptyList()
                }
            },
            llmFactory = { provider, model ->
                com.beemovil.llm.LlmFactory.createProvider(
                    providerType = provider, model = model,
                    apiKey = viewModel.getApiKey(provider)
                )
            },
            historyDB = historyDB
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
    ) {
        // ── Top Bar ──
        Surface(
            color = BeeBlackLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = BeeWhite)
                }
                Text(
                    "Workflows",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BeeWhite,
                    modifier = Modifier.weight(1f)
                )
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = BeeYellow
                    )
                }
            }
        }

        // ── Tabs: Templates | Mis Workflows | Historial ──
        TabRow(
            selectedTabIndex = if (workflowRun != null || selectedWorkflow != null || showEditor) selectedTab else selectedTab,
            containerColor = BeeBlackLight,
            contentColor = BeeYellow
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { if (!isRunning) selectedTab = 0 },
                text = { Text("Templates", fontSize = 12.sp) },
                selectedContentColor = BeeYellow,
                unselectedContentColor = BeeGray
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    customWorkflows = customDB?.getAllWorkflows() ?: emptyList()
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mis Flujos", fontSize = 12.sp)
                        if (customWorkflows.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Surface(color = BeeYellow, shape = CircleShape, modifier = Modifier.size(16.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("${customWorkflows.size}", fontSize = 9.sp, color = BeeBlack, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                selectedContentColor = BeeYellow,
                unselectedContentColor = BeeGray
            )
            Tab(
                selected = selectedTab == 2,
                onClick = {
                    selectedTab = 2
                    historyRuns = historyDB?.getAllRuns() ?: emptyList()
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Historial", fontSize = 12.sp)
                        if (historyRuns.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Surface(color = BeeYellow, shape = CircleShape, modifier = Modifier.size(16.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("${historyRuns.size}", fontSize = 9.sp, color = BeeBlack, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                selectedContentColor = BeeYellow,
                unselectedContentColor = BeeGray
            )
        }

        // ── Content ──
        if (showEditor && customDB != null) {
            WorkflowEditorScreen(
                viewModel = viewModel,
                customDB = customDB,
                existingRecord = editingCustomRecord,
                onSave = { id ->
                    showEditor = false
                    editingCustomRecord = null
                    customWorkflows = customDB.getAllWorkflows()
                    selectedTab = 1
                },
                onBack = {
                    showEditor = false
                    editingCustomRecord = null
                }
            )
        } else if (workflowRun != null) {
            WorkflowExecutionView(
                run = workflowRun!!,
                isRunning = isRunning,
                runner = runner,
                onDismiss = {
                    if (!isRunning) {
                        workflowRun = null
                        selectedWorkflow = null
                        userInput = ""
                        modelOverrides.clear()
                    }
                },
                onSendToChat = { content -> onSendToChat?.invoke("main", content) }
            )
        } else if (showHistoryDetail != null) {
            HistoryDetailView(
                record = showHistoryDetail!!,
                onBack = { showHistoryDetail = null },
                onRerun = { record ->
                    showHistoryDetail = null
                    val template = WorkflowTemplates.ALL.find { it.id == record.workflowId }
                        ?: customDB?.getWorkflow(record.workflowId)?.toWorkflow()
                    if (template != null) {
                        selectedWorkflow = template
                        userInput = record.userInput
                        selectedTab = 0
                    }
                },
                onSendToChat = { content -> onSendToChat?.invoke("main", content) }
            )
        } else if (selectedWorkflow != null) {
            WorkflowInputView(
                workflow = selectedWorkflow!!,
                userInput = userInput,
                onInputChange = { userInput = it },
                modelOverrides = modelOverrides,
                onModelClick = { stepId -> showModelPicker = stepId },
                onRun = {
                    isRunning = true
                    val wf = selectedWorkflow!!
                    val input = userInput
                    val overrides = modelOverrides.toMap()
                    Thread {
                        runner.execute(wf, input, overrides)
                    }.start()
                },
                onBack = {
                    selectedWorkflow = null
                    modelOverrides.clear()
                }
            )
        } else if (selectedTab == 0) {
            WorkflowGallery(
                onSelect = { workflow ->
                    selectedWorkflow = workflow
                    userInput = ""
                    modelOverrides.clear()
                }
            )
        } else if (selectedTab == 1) {
            CustomWorkflowsView(
                workflows = customWorkflows,
                customDB = customDB,
                onRefresh = { customWorkflows = customDB?.getAllWorkflows() ?: emptyList() },
                onSelect = { record ->
                    selectedWorkflow = record.toWorkflow()
                    userInput = ""
                    modelOverrides.clear()
                },
                onEdit = { record ->
                    editingCustomRecord = record
                    showEditor = true
                },
                onCreate = {
                    editingCustomRecord = null
                    showEditor = true
                }
            )
        } else {
            WorkflowHistoryView(
                runs = historyRuns,
                historyDB = historyDB,
                onRefresh = { historyRuns = historyDB?.getAllRuns() ?: emptyList() },
                onViewDetail = { record -> showHistoryDetail = record },
                onRerun = { record ->
                    val template = WorkflowTemplates.ALL.find { it.id == record.workflowId }
                        ?: customDB?.getWorkflow(record.workflowId)?.toWorkflow()
                    if (template != null) {
                        selectedWorkflow = template
                        userInput = record.userInput
                        selectedTab = 0
                    }
                }
            )
        }
    }

    // ── Model Picker Dialog ──
    if (showModelPicker != null) {
        ModelPickerDialog(
            currentProvider = viewModel.currentProvider.value,
            currentModel = modelOverrides[showModelPicker] ?: "",
            onSelect = { modelId ->
                if (modelId.isBlank()) {
                    modelOverrides.remove(showModelPicker!!)
                } else {
                    modelOverrides[showModelPicker!!] = modelId
                }
                showModelPicker = null
            },
            onDismiss = { showModelPicker = null }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// GALLERY
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WorkflowGallery(onSelect: (Workflow) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Automatiza tareas complejas con flujos de múltiples agentes",
                fontSize = 12.sp, color = BeeGray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(WorkflowTemplates.ALL) { workflow ->
            WorkflowTemplateCard(workflow = workflow, onClick = { onSelect(workflow) })
        }
    }
}

@Composable
private fun WorkflowTemplateCard(workflow: Workflow, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = BeeBlackLight,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BeeGray.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountTree, workflow.name, tint = BeeYellow, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(workflow.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BeeWhite)
                    Text(workflow.description, fontSize = 12.sp, color = BeeGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Surface(color = BeeYellow.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                    Text("${workflow.steps.size} pasos", fontSize = 10.sp, color = BeeYellow,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// CUSTOM WORKFLOWS TAB
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CustomWorkflowsView(
    workflows: List<CustomWorkflowRecord>,
    customDB: CustomWorkflowDB?,
    onRefresh: () -> Unit,
    onSelect: (CustomWorkflowRecord) -> Unit,
    onEdit: (CustomWorkflowRecord) -> Unit,
    onCreate: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Create button
        item {
            Surface(
                onClick = onCreate,
                color = BeeYellow.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, BeeYellow.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, "New", tint = BeeYellow, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crear workflow", color = BeeYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (workflows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AutoAwesome, "Empty", tint = BeeGray, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Sin workflows custom", color = BeeGray, fontSize = 14.sp)
                        Text("Crea tu primer flujo personalizado", color = BeeGray.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
        }

        items(workflows.size) { index ->
            val record = workflows[index]
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BeeBlackLight,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (record.isScheduled) BeeYellow.copy(alpha = 0.3f) else BeeGray.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountTree, record.name, tint = BeeYellow, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.name, color = BeeWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${record.steps.size} pasos", fontSize = 10.sp, color = BeeGray)
                                Text("·", fontSize = 10.sp, color = BeeGray)
                                Text(record.getRelativeUpdated(), fontSize = 10.sp, color = BeeGray)
                                if (record.runCount > 0) {
                                    Text("· ${record.runCount}x", fontSize = 10.sp, color = BeeGray)
                                }
                            }
                        }
                    }

                    if (record.description.isNotBlank()) {
                        Text(record.description, fontSize = 11.sp, color = BeeGray.copy(alpha = 0.7f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp))
                    }

                    if (record.isScheduled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(color = BeeYellow.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Schedule, "Schedule", tint = BeeYellow, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(record.schedule!!.getDisplayText(), fontSize = 10.sp, color = BeeYellow)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onSelect(record) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                            shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(8.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, "Run", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Ejecutar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onEdit(record) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, BeeGray.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeWhite),
                            shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(8.dp)
                        ) {
                            Icon(Icons.Filled.Edit, "Edit", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Editar", fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = {
                                customDB?.deleteWorkflow(record.id)
                                onRefresh()
                                Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFF44336).copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HISTORY
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WorkflowHistoryView(
    runs: List<WorkflowRunRecord>,
    historyDB: WorkflowHistoryDB?,
    onRefresh: () -> Unit,
    onViewDetail: (WorkflowRunRecord) -> Unit,
    onRerun: (WorkflowRunRecord) -> Unit
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedForDelete = remember { mutableStateListOf<Long>() }
    var isSelectMode by remember { mutableStateOf(false) }

    if (runs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.History, "Empty", tint = BeeGray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sin historial", color = BeeGray, fontSize = 16.sp)
                Text("Los workflows ejecutados aparecerán aquí", color = BeeGray.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectMode) {
                TextButton(onClick = {
                    isSelectMode = false
                    selectedForDelete.clear()
                }) {
                    Text("Cancelar", color = BeeGray)
                }
                Text("${selectedForDelete.size} seleccionados", color = BeeWhite, fontSize = 13.sp)
                TextButton(
                    onClick = {
                        historyDB?.deleteRuns(selectedForDelete.toList())
                        selectedForDelete.clear()
                        isSelectMode = false
                        onRefresh()
                    },
                    enabled = selectedForDelete.isNotEmpty()
                ) {
                    Text("Borrar", color = if (selectedForDelete.isNotEmpty()) Color(0xFFF44336) else BeeGray,
                        fontWeight = FontWeight.Bold)
                }
            } else {
                Text("${runs.size} ejecuciones", color = BeeGray, fontSize = 12.sp)
                Row {
                    TextButton(onClick = { isSelectMode = true }) {
                        Text("Seleccionar", color = BeeYellow, fontSize = 12.sp)
                    }
                    TextButton(onClick = { showClearDialog = true }) {
                        Text("Borrar todo", color = Color(0xFFF44336), fontSize = 12.sp)
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(runs) { record ->
                HistoryCard(
                    record = record,
                    isSelectMode = isSelectMode,
                    isSelected = record.id in selectedForDelete,
                    onToggleSelect = {
                        if (record.id in selectedForDelete) selectedForDelete.remove(record.id)
                        else selectedForDelete.add(record.id)
                    },
                    onClick = {
                        if (isSelectMode) {
                            if (record.id in selectedForDelete) selectedForDelete.remove(record.id)
                            else selectedForDelete.add(record.id)
                        } else {
                            onViewDetail(record)
                        }
                    },
                    onRerun = { onRerun(record) },
                    onDelete = {
                        historyDB?.deleteRun(record.id)
                        onRefresh()
                        Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Clear all dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Borrar historial", color = BeeWhite) },
            text = { Text("Se borrarán todas las ejecuciones anteriores. Esta acción no se puede deshacer.", color = BeeGray) },
            confirmButton = {
                TextButton(onClick = {
                    historyDB?.clearHistory()
                    showClearDialog = false
                    onRefresh()
                }) {
                    Text("Borrar todo", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar", color = BeeGray)
                }
            },
            containerColor = BeeBlackLight
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    record: WorkflowRunRecord,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onRerun: () -> Unit,
    onDelete: () -> Unit
) {
    val statusIcon = when {
        record.isComplete -> "✅"
        record.isFailed -> "❌"
        record.isCancelled -> "🚫"
        record.isRunning -> "🔄"
        else -> "⏳"
    }
    val statusColor = when {
        record.isComplete -> Color(0xFF4CAF50)
        record.isFailed || record.isCancelled -> Color(0xFFF44336)
        else -> BeeYellow
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleSelect
            ),
        color = if (isSelected) BeeYellow.copy(alpha = 0.15f) else BeeBlackLight,
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(1.dp, BeeYellow) else BorderStroke(1.dp, BeeGray.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = BeeYellow, uncheckedColor = BeeGray)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusIcon, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(record.workflowName, color = BeeWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(record.getRelativeTime(), fontSize = 11.sp, color = BeeGray)
                    if (record.elapsedMs > 0) {
                        Text("⏱ ${record.getElapsedFormatted()}", fontSize = 11.sp, color = BeeGray)
                    }
                }
                if (record.userInput.isNotBlank()) {
                    Text(
                        record.userInput.take(60) + if (record.userInput.length > 60) "..." else "",
                        fontSize = 11.sp, color = BeeGray.copy(alpha = 0.7f), maxLines = 1
                    )
                }
            }

            if (!isSelectMode) {
                IconButton(onClick = onRerun, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Replay, "Re-run", tint = BeeYellow, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HISTORY DETAIL
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HistoryDetailView(
    record: WorkflowRunRecord,
    onBack: () -> Unit,
    onRerun: (WorkflowRunRecord) -> Unit,
    onSendToChat: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = BeeGray) }
            Column(modifier = Modifier.weight(1f)) {
                Text(record.workflowName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeWhite)
                Text("${record.getRelativeTime()} · ${record.getElapsedFormatted()}", fontSize = 12.sp, color = BeeGray)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input
        if (record.userInput.isNotBlank()) {
            Text("INPUT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BeeYellow)
            Surface(color = BeeBlackLight, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(record.userInput, fontSize = 12.sp, color = BeeGrayLight, modifier = Modifier.padding(10.dp), lineHeight = 16.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Output
        if (record.finalOutput.isNotBlank()) {
            Text(
                if (record.isComplete) "RESULTADO" else "OUTPUT PARCIAL",
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (record.isComplete) Color(0xFF4CAF50) else BeeYellow
            )
            Surface(
                color = if (record.isComplete) Color(0xFF1A3E1A) else BeeBlackLight,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(record.finalOutput.take(5000), fontSize = 12.sp, color = BeeWhite.copy(alpha = 0.9f),
                    modifier = Modifier.padding(10.dp), lineHeight = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Workflow", record.finalOutput))
                    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BeeGray.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeWhite)
            ) {
                Icon(Icons.Filled.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copiar", fontSize = 12.sp)
            }

            Button(
                onClick = { onRerun(record) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack)
            ) {
                Icon(Icons.Filled.Replay, "Rerun", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Re-ejecutar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (record.finalOutput.isNotBlank() && onSendToChat != null) {
            Button(
                onClick = { onSendToChat(record.finalOutput) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = BeeWhite)
            ) {
                Icon(Icons.Filled.Send, "Send", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Enviar al Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// INPUT VIEW (with model selector)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WorkflowInputView(
    workflow: Workflow,
    userInput: String,
    onInputChange: (String) -> Unit,
    modelOverrides: Map<String, String>,
    onModelClick: (String) -> Unit,
    onRun: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = BeeGray) }
            Icon(Icons.Filled.AccountTree, workflow.name, tint = BeeYellow, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(workflow.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeWhite)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(workflow.description, fontSize = 13.sp, color = BeeGray)
        Spacer(modifier = Modifier.height(16.dp))

        // Steps with model selector
        Text("PASOS DEL FLUJO", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        workflow.steps.forEachIndexed { index, step ->
            val hasOverride = modelOverrides.containsKey(step.id)
            val overrideModel = modelOverrides[step.id]

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                color = if (hasOverride) BeeYellow.copy(alpha = 0.08f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = BeeYellow.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("${index + 1}", fontSize = 11.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(step.label, fontSize = 13.sp, color = BeeWhite)
                        Text(
                            if (step.type == StepType.AGENT) "Agent: ${step.agentId}" else "Skill: ${step.skillName}",
                            fontSize = 9.sp, color = BeeGray
                        )
                    }

                    // Model selector chip
                    if (step.type == StepType.AGENT) {
                        Surface(
                            onClick = { onModelClick(step.id) },
                            color = if (hasOverride) BeeYellow.copy(alpha = 0.2f) else BeeGray.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.SmartToy, "Model", tint = if (hasOverride) BeeYellow else BeeGray,
                                    modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    if (hasOverride) {
                                        ModelRegistry.findModel(overrideModel ?: "")?.name?.take(15) ?: overrideModel?.take(15) ?: "?"
                                    } else "Default",
                                    fontSize = 9.sp,
                                    color = if (hasOverride) BeeYellow else BeeGray,
                                    fontWeight = if (hasOverride) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Input field
        Text("INPUT", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = userInput,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    when (workflow.id) {
                        "research_pdf" -> "Ej: Tendencias de IA en 2026"
                        "quote_email" -> "Ej: 50 camisas polo a $15 USD"
                        "content_creator" -> "Ej: Un logo futurista de una abeja dorada"
                        "web_landing" -> "Ej: https://ejemplo.com"
                        else -> "Describe tu solicitud..."
                    },
                    color = BeeGray
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                cursorColor = BeeYellow
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRun,
            enabled = userInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BeeYellow, contentColor = BeeBlack,
                disabledContainerColor = BeeGray.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, "Run", modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ejecutar Workflow", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// EXECUTION VIEW (with error recovery)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WorkflowExecutionView(
    run: WorkflowRun,
    isRunning: Boolean,
    runner: WorkflowRunner,
    onDismiss: () -> Unit,
    onSendToChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccountTree, run.workflow.name, tint = BeeYellow, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(run.workflow.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeWhite)
                Text(
                    when {
                        run.isComplete -> "Completado"
                        run.isCancelled -> "Cancelado"
                        run.isFailed -> "Error"
                        run.pendingErrorAction -> "Esperando acción..."
                        isRunning -> "Ejecutando..."
                        else -> "Preparando..."
                    },
                    fontSize = 12.sp,
                    color = when {
                        run.isComplete -> Color(0xFF4CAF50)
                        run.isFailed || run.isCancelled -> Color(0xFFF44336)
                        run.pendingErrorAction -> Color(0xFFFF9800)
                        else -> BeeYellow
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Node chain ──
        run.stepStates.forEachIndexed { index, stepState ->
            WorkflowNode(
                stepState = stepState,
                stepNumber = index + 1,
                showErrorActions = stepState.status == StepStatus.FAILED && run.pendingErrorAction && run.currentStepIndex == index,
                onRetry = { runner.resolveError(StepErrorAction.RETRY) },
                onSkip = { runner.resolveError(StepErrorAction.SKIP) },
                onCancel = { runner.resolveError(StepErrorAction.CANCEL) }
            )

            if (index < run.stepStates.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 28.dp)
                        .width(2.dp)
                        .height(20.dp)
                        .background(
                            when (stepState.status) {
                                StepStatus.COMPLETED -> BeeYellow.copy(alpha = 0.6f)
                                StepStatus.FAILED -> Color(0xFFF44336).copy(alpha = 0.6f)
                                else -> BeeGray.copy(alpha = 0.2f)
                            }
                        )
                )
            }
        }

        // ── Final Output + Delivery ──
        if (run.isComplete || run.isFailed || run.isCancelled) {
            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                color = if (run.isComplete) Color(0xFF1A3E1A) else Color(0xFF3E1A1A),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp,
                    if (run.isComplete) Color(0xFF4CAF50).copy(alpha = 0.4f)
                    else Color(0xFFF44336).copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        when {
                            run.isComplete -> "RESULTADO FINAL"
                            run.isCancelled -> "RESULTADO PARCIAL"
                            else -> "ERROR"
                        },
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (run.isComplete) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(run.finalOutput.take(2000), fontSize = 12.sp,
                        color = BeeWhite.copy(alpha = 0.9f), lineHeight = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delivery actions
            Text("ENTREGAR RESULTADO", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = BeeYellow, modifier = Modifier.padding(bottom = 8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Workflow Result", run.finalOutput))
                        Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BeeGray.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeWhite)
                ) {
                    Icon(Icons.Filled.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "BeeMovil: ${run.workflow.name}")
                            putExtra(Intent.EXTRA_TEXT, run.finalOutput)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir"))
                    },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BeeGray.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeWhite)
                ) {
                    Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compartir", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        try {
                            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val fileName = "beemovil_${run.workflow.id}_$ts.txt"
                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val file = File(downloadsDir, fileName)
                            file.writeText(run.finalOutput)
                            Toast.makeText(context, "Guardado: $fileName", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BeeGray.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeWhite)
                ) {
                    Icon(Icons.Filled.SaveAlt, "Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Guardar", fontSize = 12.sp)
                }
                Button(
                    onClick = { onSendToChat?.invoke(run.finalOutput) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack)
                ) {
                    Icon(Icons.Filled.Send, "Send", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Al Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BeeGray.copy(alpha = 0.3f), contentColor = BeeWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Volver a Workflows", fontSize = 14.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// NODE (with error action buttons)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WorkflowNode(
    stepState: StepState,
    stepNumber: Int,
    showErrorActions: Boolean = false,
    onRetry: () -> Unit = {},
    onSkip: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val step = stepState.step

    val borderColor = when (stepState.status) {
        StepStatus.PENDING -> BeeGray.copy(alpha = 0.2f)
        StepStatus.RUNNING -> BeeYellow
        StepStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.6f)
        StepStatus.FAILED -> Color(0xFFF44336).copy(alpha = 0.6f)
        StepStatus.SKIPPED -> BeeGray.copy(alpha = 0.15f)
    }
    val bgColor = when (stepState.status) {
        StepStatus.RUNNING -> Color(0xFF1A1A3E)
        StepStatus.COMPLETED -> Color(0xFF0A1A0A)
        StepStatus.FAILED -> Color(0xFF1A0A0A)
        else -> BeeBlackLight
    }
    val statusText = when (stepState.status) {
        StepStatus.PENDING -> "..."
        StepStatus.RUNNING -> "RUN"
        StepStatus.COMPLETED -> "OK"
        StepStatus.FAILED -> "ERR"
        StepStatus.SKIPPED -> "SKIP"
    }
    val statusColor = when (stepState.status) {
        StepStatus.PENDING -> BeeGray
        StepStatus.RUNNING -> BeeYellow
        StepStatus.COMPLETED -> Color(0xFF4CAF50)
        StepStatus.FAILED -> Color(0xFFF44336)
        StepStatus.SKIPPED -> BeeGray.copy(alpha = 0.5f)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = borderColor.copy(alpha = 0.3f), shape = CircleShape, modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("$stepNumber", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BeeWhite)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(step.label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = if (stepState.status == StepStatus.SKIPPED) BeeGray else BeeWhite)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (step.type == StepType.AGENT) "Agent: ${step.agentId}" else "Skill: ${step.skillName}",
                            fontSize = 9.sp, color = BeeGray
                        )
                    }
                    if (stepState.elapsedMs > 0) {
                        Text("${stepState.elapsedMs / 1000.0}s", fontSize = 9.sp, color = BeeGray.copy(alpha = 0.6f))
                    }
                }
                Text(statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
            }

            // Output preview
            if (stepState.status == StepStatus.COMPLETED && stepState.output.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = Color(0xFF0D0D1A), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stepState.output.take(300) + if (stepState.output.length > 300) "..." else "",
                        fontSize = 11.sp, color = BeeGrayLight,
                        modifier = Modifier.padding(8.dp), lineHeight = 16.sp
                    )
                }
            }

            // Error message
            if (stepState.status == StepStatus.FAILED && stepState.error.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Error: ${stepState.error}", fontSize = 11.sp, color = Color(0xFFF44336))
            }

            // ── Error Recovery Buttons ──
            if (showErrorActions) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(Icons.Filled.Replay, "Retry", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Reintentar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, BeeGray.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeWhite),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(Icons.Filled.SkipNext, "Skip", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Saltar", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFFF44336).copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(Icons.Filled.Cancel, "Cancel", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Cancelar", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// MODEL PICKER DIALOG
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ModelPickerDialog(
    currentProvider: String,
    currentModel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val models = remember(currentProvider) {
        ModelRegistry.getModelsForProvider(currentProvider)
            .filter { it.hasTools } // Only models that support tools for workflows
    }
    val grouped = remember(models) { models.groupBy { it.category } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Modelo para este paso", color = BeeWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                // Default option
                item {
                    Surface(
                        onClick = { onSelect("") },
                        color = if (currentModel.isBlank()) BeeYellow.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, "Default", tint = BeeGray, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Default (modelo del agente)", color = BeeWhite, fontSize = 13.sp)
                                Text("Usa el modelo configurado globalmente", color = BeeGray, fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                grouped.forEach { (category, categoryModels) ->
                    item {
                        Text(category.label.uppercase(), fontSize = 10.sp, color = BeeYellow,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(categoryModels) { model ->
                        Surface(
                            onClick = { onSelect(model.id) },
                            color = if (model.id == currentModel) BeeYellow.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.name, color = BeeWhite, fontSize = 12.sp)
                                    Text(model.description, color = BeeGray, fontSize = 10.sp, maxLines = 1)
                                }
                                if (model.free) {
                                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text("FREE", fontSize = 8.sp, color = Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = BeeGray) }
        },
        containerColor = BeeBlackLight
    )
}
