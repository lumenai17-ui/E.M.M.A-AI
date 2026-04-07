package com.beemovil.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.agent.*
import com.beemovil.llm.ModelRegistry
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import com.beemovil.workflow.*

/**
 * WorkflowEditorScreen — Create and edit custom workflows.
 *
 * Features:
 * - Name, description, icon
 * - Add/remove/reorder steps
 * - Per-step: agent, skill, prompt, model override
 * - Schedule configuration (time, days, triggers)
 * - Save to CustomWorkflowDB
 * - AI can pre-fill via GenerateWorkflowSkill
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorScreen(
    viewModel: ChatViewModel,
    customDB: CustomWorkflowDB,
    existingRecord: CustomWorkflowRecord? = null,
    prefilledWorkflow: Workflow? = null,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isEditing = existingRecord != null

    // Editable state
    var name by remember { mutableStateOf(existingRecord?.name ?: prefilledWorkflow?.name ?: "") }
    var description by remember { mutableStateOf(existingRecord?.description ?: prefilledWorkflow?.description ?: "") }
    var icon by remember { mutableStateOf(existingRecord?.icon ?: prefilledWorkflow?.icon ?: "FLOW") }
    val steps = remember {
        mutableStateListOf<EditableStep>().apply {
            val source = existingRecord?.steps ?: prefilledWorkflow?.steps ?: emptyList()
            addAll(source.mapIndexed { i, s -> EditableStep.fromWorkflowStep(s, i) })
        }
    }

    // Schedule
    var showSchedule by remember { mutableStateOf(existingRecord?.schedule != null) }
    var scheduleFreq by remember { mutableStateOf(existingRecord?.schedule?.frequency ?: ScheduleFrequency.DAILY) }
    var scheduleHour by remember { mutableIntStateOf(existingRecord?.schedule?.hour ?: 7) }
    var scheduleMinute by remember { mutableIntStateOf(existingRecord?.schedule?.minute ?: 0) }
    var scheduleDays by remember { mutableStateOf(existingRecord?.schedule?.daysOfWeek ?: setOf(1,2,3,4,5)) }
    var requireWifi by remember { mutableStateOf(existingRecord?.schedule?.requireWifi ?: false) }
    var notifyComplete by remember { mutableStateOf(existingRecord?.schedule?.notifyOnComplete ?: true) }
    var createTask by remember { mutableStateOf(existingRecord?.schedule?.createTask ?: false) }
    var triggerLowBattery by remember { mutableStateOf(existingRecord?.schedule?.triggerOnLowBattery ?: false) }
    var triggerWifi by remember { mutableStateOf(existingRecord?.schedule?.triggerOnWifiConnect ?: false) }
    var triggerBoot by remember { mutableStateOf(existingRecord?.schedule?.triggerOnBoot ?: false) }

    // Step editor dialog
    var editingStep by remember { mutableStateOf<EditableStep?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    var modelPickerStepIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
    ) {
        // Top bar
        Surface(color = BeeBlackLight) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, "Close", tint = BeeGray)
                }
                Text(
                    if (isEditing) "Editar Workflow" else "Nuevo Workflow",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeWhite,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        if (name.isBlank()) {
                            Toast.makeText(context, "Nombre requerido", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (steps.isEmpty()) {
                            Toast.makeText(context, "Agrega al menos un paso", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        val workflow = Workflow(
                            id = existingRecord?.id ?: "custom_${System.currentTimeMillis()}",
                            name = name,
                            icon = icon,
                            description = description,
                            steps = steps.map { it.toWorkflowStep() },
                            isTemplate = false
                        )

                        val schedule = if (showSchedule) {
                            ScheduleConfig(
                                frequency = scheduleFreq,
                                hour = scheduleHour,
                                minute = scheduleMinute,
                                daysOfWeek = scheduleDays,
                                requireWifi = requireWifi,
                                notifyOnComplete = notifyComplete,
                                createTask = createTask,
                                triggerOnLowBattery = triggerLowBattery,
                                triggerOnWifiConnect = triggerWifi,
                                triggerOnBoot = triggerBoot
                            )
                        } else null

                        if (isEditing) {
                            customDB.updateWorkflow(existingRecord!!.id, workflow, schedule)
                        } else {
                            customDB.saveWorkflow(workflow, schedule)
                        }

                        // Activate/cancel scheduler
                        if (schedule != null) {
                            WorkflowScheduler.schedule(context, workflow.id, schedule)
                            Toast.makeText(context, "Workflow guardado y programado", Toast.LENGTH_SHORT).show()
                        } else {
                            WorkflowScheduler.cancel(context, workflow.id)
                            Toast.makeText(context, "Workflow guardado", Toast.LENGTH_SHORT).show()
                        }
                        onSave(workflow.id)
                    },
                    enabled = name.isNotBlank() && steps.isNotEmpty()
                ) {
                    Text("Guardar", color = if (name.isNotBlank() && steps.isNotEmpty()) BeeYellow else BeeGray,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Name & Description
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del workflow", color = BeeGray) },
                    placeholder = { Text("Ej: Mi reporte semanal", color = BeeGray.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                        focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                        cursorColor = BeeYellow, focusedLabelColor = BeeYellow
                    )
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)", color = BeeGray) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                        focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                        cursorColor = BeeYellow, focusedLabelColor = BeeYellow
                    )
                )
            }

            // ── Steps ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PASOS", fontSize = 11.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                    Text("${steps.size} pasos", fontSize = 11.sp, color = BeeGray)
                }
            }

            itemsIndexed(steps) { index, step ->
                StepEditorCard(
                    step = step,
                    stepNumber = index + 1,
                    onEdit = { editingStep = step },
                    onDelete = { steps.removeAt(index) },
                    onMoveUp = { if (index > 0) { val s = steps.removeAt(index); steps.add(index - 1, s) } },
                    onMoveDown = { if (index < steps.lastIndex) { val s = steps.removeAt(index); steps.add(index + 1, s) } },
                    onModelClick = { modelPickerStepIndex = index; showModelPicker = true },
                    isFirst = index == 0,
                    isLast = index == steps.lastIndex
                )
            }

            // Add step button
            item {
                Surface(
                    onClick = {
                        val newStep = EditableStep(
                            id = "s${steps.size + 1}",
                            label = "Paso ${steps.size + 1}",
                            icon = "STEP",
                            type = StepType.AGENT,
                            agentId = "main",
                            prompt = "",
                            modelOverride = null
                        )
                        steps.add(newStep)
                        editingStep = newStep
                    },
                    color = BeeYellow.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BeeYellow.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, "Add", tint = BeeYellow, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Agregar paso", color = BeeYellow, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Schedule ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PROGRAMAR", fontSize = 11.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = showSchedule,
                        onCheckedChange = { showSchedule = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = BeeYellow, checkedTrackColor = BeeYellow.copy(alpha = 0.3f))
                    )
                }
            }

            if (showSchedule) {
                item { ScheduleEditor(
                    frequency = scheduleFreq, onFreqChange = { scheduleFreq = it },
                    hour = scheduleHour, onHourChange = { scheduleHour = it },
                    minute = scheduleMinute, onMinuteChange = { scheduleMinute = it },
                    days = scheduleDays, onDaysChange = { scheduleDays = it },
                    requireWifi = requireWifi, onWifiChange = { requireWifi = it },
                    notify = notifyComplete, onNotifyChange = { notifyComplete = it },
                    createTask = createTask, onTaskChange = { createTask = it },
                    triggerBattery = triggerLowBattery, onBatteryChange = { triggerLowBattery = it },
                    triggerWifi = triggerWifi, onTriggerWifiChange = { triggerWifi = it },
                    triggerBoot = triggerBoot, onBootChange = { triggerBoot = it }
                ) }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // ── Step editor dialog ──
    if (editingStep != null) {
        StepEditorDialog(
            step = editingStep!!,
            availableAgents = viewModel.availableAgents,
            availableSkills = viewModel.getSkills().keys.toList(),
            onSave = { updated ->
                val idx = steps.indexOfFirst { it.id == updated.id }
                if (idx >= 0) steps[idx] = updated
                editingStep = null
            },
            onDismiss = { editingStep = null }
        )
    }

    // ── Model picker ──
    if (showModelPicker && modelPickerStepIndex in steps.indices) {
        ModelPickerForEditor(
            currentProvider = viewModel.currentProvider.value,
            currentModel = steps[modelPickerStepIndex].modelOverride ?: "",
            onSelect = { modelId ->
                val step = steps[modelPickerStepIndex]
                steps[modelPickerStepIndex] = step.copy(modelOverride = modelId.ifBlank { null })
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }
}

// ═══════════════════════════════════════════════════════
// Step Editor Card
// ═══════════════════════════════════════════════════════

@Composable
private fun StepEditorCard(
    step: EditableStep,
    stepNumber: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onModelClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BeeBlackLight,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BeeGray.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Number badge
                Surface(color = BeeYellow.copy(alpha = 0.2f), shape = CircleShape, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("$stepNumber", fontSize = 12.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        step.label.ifBlank { "Paso $stepNumber" },
                        fontSize = 14.sp, color = BeeWhite, fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (step.type == StepType.AGENT) "Agent: ${step.agentId}" else "Skill: ${step.skillName}",
                        fontSize = 10.sp, color = BeeGray
                    )
                }

                // Move buttons
                if (!isFirst) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = BeeGray, modifier = Modifier.size(18.dp))
                    }
                }
                if (!isLast) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = BeeGray, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFF44336).copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }

            if (step.prompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    step.prompt.take(100) + if (step.prompt.length > 100) "..." else "",
                    fontSize = 11.sp, color = BeeGray.copy(alpha = 0.7f), lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Model chip
                Surface(
                    onClick = onModelClick,
                    color = if (step.modelOverride != null) BeeYellow.copy(alpha = 0.15f) else BeeGray.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SmartToy, "Model", tint = if (step.modelOverride != null) BeeYellow else BeeGray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            if (step.modelOverride != null) {
                                ModelRegistry.findModel(step.modelOverride)?.name?.take(18) ?: step.modelOverride.take(18)
                            } else "Default",
                            fontSize = 9.sp, color = if (step.modelOverride != null) BeeYellow else BeeGray
                        )
                    }
                }

                // Edit button
                Surface(onClick = onEdit, color = BeeGray.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, "Edit", tint = BeeGray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Editar", fontSize = 9.sp, color = BeeGray)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Step Editor Dialog
// ═══════════════════════════════════════════════════════

@Composable
private fun StepEditorDialog(
    step: EditableStep,
    availableAgents: List<AgentConfig>,
    availableSkills: List<String>,
    onSave: (EditableStep) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(step.label) }
    var type by remember { mutableStateOf(step.type) }
    var agentId by remember { mutableStateOf(step.agentId) }
    var skillName by remember { mutableStateOf(step.skillName) }
    var prompt by remember { mutableStateOf(step.prompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar paso", color = BeeWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Nombre del paso", color = BeeGray) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                        focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                        cursorColor = BeeYellow, focusedLabelColor = BeeYellow
                    )
                )

                // Type selector
                Text("Tipo", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == StepType.AGENT,
                        onClick = { type = StepType.AGENT },
                        label = { Text("Agente", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BeeYellow.copy(alpha = 0.2f),
                            selectedLabelColor = BeeYellow
                        )
                    )
                    FilterChip(
                        selected = type == StepType.SKILL,
                        onClick = { type = StepType.SKILL },
                        label = { Text("Skill directo", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BeeYellow.copy(alpha = 0.2f),
                            selectedLabelColor = BeeYellow
                        )
                    )
                }

                if (type == StepType.AGENT) {
                    Text("Agente", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        availableAgents.forEach { agent ->
                            FilterChip(
                                selected = agentId == agent.id,
                                onClick = { agentId = agent.id },
                                label = { Text(agent.name.take(12), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BeeYellow.copy(alpha = 0.2f),
                                    selectedLabelColor = BeeYellow
                                )
                            )
                        }
                    }
                } else {
                    Text("Skill", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        availableSkills.take(15).forEach { skill ->
                            FilterChip(
                                selected = skillName == skill,
                                onClick = { skillName = skill },
                                label = { Text(skill.take(14), fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BeeYellow.copy(alpha = 0.2f),
                                    selectedLabelColor = BeeYellow
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("Prompt / instrucción", color = BeeGray) },
                    placeholder = { Text("Usa {input} para recibir el resultado del paso anterior", color = BeeGray.copy(alpha = 0.4f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                        focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                        cursorColor = BeeYellow, focusedLabelColor = BeeYellow
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(step.copy(
                    label = label.ifBlank { "Paso" },
                    type = type,
                    agentId = agentId,
                    skillName = skillName,
                    prompt = prompt
                ))
            }) {
                Text("Guardar", color = BeeYellow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = BeeGray) }
        },
        containerColor = BeeBlackLight
    )
}

// ═══════════════════════════════════════════════════════
// Schedule Editor
// ═══════════════════════════════════════════════════════

@Composable
private fun ScheduleEditor(
    frequency: ScheduleFrequency, onFreqChange: (ScheduleFrequency) -> Unit,
    hour: Int, onHourChange: (Int) -> Unit,
    minute: Int, onMinuteChange: (Int) -> Unit,
    days: Set<Int>, onDaysChange: (Set<Int>) -> Unit,
    requireWifi: Boolean, onWifiChange: (Boolean) -> Unit,
    notify: Boolean, onNotifyChange: (Boolean) -> Unit,
    createTask: Boolean, onTaskChange: (Boolean) -> Unit,
    triggerBattery: Boolean, onBatteryChange: (Boolean) -> Unit,
    triggerWifi: Boolean, onTriggerWifiChange: (Boolean) -> Unit,
    triggerBoot: Boolean, onBootChange: (Boolean) -> Unit
) {
    Surface(
        color = BeeBlackLight,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BeeYellow.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Frequency
            Text("Frecuencia", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ScheduleFrequency.DAILY to "Diario",
                    ScheduleFrequency.WEEKLY to "Semanal",
                    ScheduleFrequency.CUSTOM to "Custom"
                ).forEach { (freq, label) ->
                    FilterChip(
                        selected = frequency == freq,
                        onClick = { onFreqChange(freq) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BeeYellow.copy(alpha = 0.2f),
                            selectedLabelColor = BeeYellow
                        )
                    )
                }
            }

            // Time
            Text("Hora", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Hour picker (simplified as +/- buttons)
                Surface(color = BeeGray.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onHourChange(if (hour > 0) hour - 1 else 23) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Remove, "Menos", tint = BeeGray, modifier = Modifier.size(16.dp))
                        }
                        Text("%02d".format(hour), color = BeeWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onHourChange(if (hour < 23) hour + 1 else 0) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Add, "Mas", tint = BeeGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Text(":", color = BeeWhite, fontSize = 20.sp)
                Surface(color = BeeGray.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMinuteChange(if (minute > 0) minute - 5 else 55) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Remove, "Menos", tint = BeeGray, modifier = Modifier.size(16.dp))
                        }
                        Text("%02d".format(minute), color = BeeWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onMinuteChange(if (minute < 55) minute + 5 else 0) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Add, "Mas", tint = BeeGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Days of week
            if (frequency != ScheduleFrequency.DAILY) {
                Text("Días", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dayLabels = listOf("L" to 1, "M" to 2, "Mi" to 3, "J" to 4, "V" to 5, "S" to 6, "D" to 7)
                    dayLabels.forEach { (label, day) ->
                        Surface(
                            onClick = {
                                onDaysChange(if (day in days) days - day else days + day)
                            },
                            color = if (day in days) BeeYellow.copy(alpha = 0.3f) else BeeGray.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if (day in days) BeeYellow else BeeGray)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = BeeGray.copy(alpha = 0.15f))

            // Options
            Text("OPCIONES", fontSize = 10.sp, color = BeeGray, fontWeight = FontWeight.Bold)
            ScheduleToggle("Solo con WiFi", requireWifi, onWifiChange)
            ScheduleToggle("Notificar al completar", notify, onNotifyChange)
            ScheduleToggle("Crear tarea en calendario", createTask, onTaskChange)

            HorizontalDivider(color = BeeGray.copy(alpha = 0.15f))

            // Triggers
            Text("TRIGGERS ADICIONALES", fontSize = 10.sp, color = BeeGray, fontWeight = FontWeight.Bold)
            ScheduleToggle("Al conectar WiFi", triggerWifi, onTriggerWifiChange)
            ScheduleToggle("Batería baja (<20%)", triggerBattery, onBatteryChange)
            ScheduleToggle("Al encender teléfono", triggerBoot, onBootChange)
        }
    }
}

@Composable
private fun ScheduleToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = BeeWhite)
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            modifier = Modifier.height(24.dp),
            colors = SwitchDefaults.colors(checkedThumbColor = BeeYellow, checkedTrackColor = BeeYellow.copy(alpha = 0.3f))
        )
    }
}

// ═══════════════════════════════════════════════════════
// Model Picker for Editor
// ═══════════════════════════════════════════════════════

@Composable
private fun ModelPickerForEditor(
    currentProvider: String,
    currentModel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val models = remember(currentProvider) {
        ModelRegistry.getModelsForProvider(currentProvider).filter { it.hasTools }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modelo para este paso", color = BeeWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                Surface(
                    onClick = { onSelect("") },
                    color = if (currentModel.isBlank()) BeeYellow.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Default (modelo global)", color = BeeWhite, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                }
                models.forEach { model ->
                    Surface(
                        onClick = { onSelect(model.id) },
                        color = if (model.id == currentModel) BeeYellow.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.name, color = BeeWhite, fontSize = 12.sp)
                                Text(model.description, color = BeeGray, fontSize = 10.sp)
                            }
                            if (model.free) {
                                Surface(color = Color(0xFF4CAF50).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                    Text("FREE", fontSize = 8.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = BeeGray) } },
        containerColor = BeeBlackLight
    )
}

// ═══════════════════════════════════════════════════════
// Data class
// ═══════════════════════════════════════════════════════

data class EditableStep(
    val id: String,
    val label: String,
    val icon: String = "STEP",
    val type: StepType = StepType.AGENT,
    val agentId: String = "main",
    val skillName: String = "",
    val prompt: String = "",
    val modelOverride: String? = null,
    val fixedParams: Map<String, String> = emptyMap()
) {
    fun toWorkflowStep() = WorkflowStep(
        id = id, label = label, icon = icon, type = type,
        agentId = agentId, skillName = skillName, prompt = prompt,
        modelOverride = modelOverride, fixedParams = fixedParams
    )

    companion object {
        fun fromWorkflowStep(step: WorkflowStep, index: Int) = EditableStep(
            id = step.id.ifBlank { "s${index + 1}" },
            label = step.label, icon = step.icon, type = step.type,
            agentId = step.agentId, skillName = step.skillName,
            prompt = step.prompt, modelOverride = step.modelOverride,
            fixedParams = step.fixedParams
        )
    }
}
