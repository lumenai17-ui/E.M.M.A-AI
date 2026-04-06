package com.beemovil.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.agent.*
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*

/**
 * WorkflowScreen — Visual workflow executor with n8n-style node display.
 *
 * Shows workflow templates as cards, and when executing, displays each step
 * as a connected node with real-time status updates (pending → running → done).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    var selectedWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var userInput by remember { mutableStateOf("") }
    var workflowRun by remember { mutableStateOf<WorkflowRun?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    // Workflow runner
    val runner = remember {
        WorkflowRunner(
            agentResolver = { agentId -> viewModel.resolveAgent(agentId) },
            skills = viewModel.getSkills(),
            onStateChanged = { run ->
                workflowRun = run
                if (run.isComplete || run.isFailed) {
                    isRunning = false
                }
            }
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
                    "⚡ Workflows",
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

        if (workflowRun != null) {
            // ── Execution View ──
            WorkflowExecutionView(
                run = workflowRun!!,
                isRunning = isRunning,
                onDismiss = {
                    if (!isRunning) {
                        workflowRun = null
                        selectedWorkflow = null
                        userInput = ""
                    }
                }
            )
        } else if (selectedWorkflow != null) {
            // ── Input View ──
            WorkflowInputView(
                workflow = selectedWorkflow!!,
                userInput = userInput,
                onInputChange = { userInput = it },
                onRun = {
                    isRunning = true
                    val wf = selectedWorkflow!!
                    val input = userInput
                    Thread {
                        runner.execute(wf, input)
                    }.start()
                },
                onBack = { selectedWorkflow = null }
            )
        } else {
            // ── Template Gallery ──
            WorkflowGallery(
                onSelect = { workflow ->
                    selectedWorkflow = workflow
                    userInput = ""
                }
            )
        }
    }
}

/**
 * Gallery of available workflow templates.
 */
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

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = BeeBlackLight.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BeeGray.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔧", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Workflows custom — próximamente",
                        fontSize = 13.sp, color = BeeGray
                    )
                    Text(
                        "Podrás crear y guardar tus propios flujos",
                        fontSize = 11.sp, color = BeeGray.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Card for a single workflow template.
 */
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
                Text(workflow.icon, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        workflow.name,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = BeeWhite
                    )
                    Text(
                        workflow.description,
                        fontSize = 12.sp, color = BeeGray,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Filled.PlayArrow, "Run", tint = BeeYellow, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step preview — mini node chain
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                workflow.steps.forEachIndexed { index, step ->
                    Surface(
                        color = Color(0xFF1A1A3E),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "${step.icon} ${step.label}",
                            fontSize = 9.sp, color = BeeGrayLight,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            maxLines = 1
                        )
                    }
                    if (index < workflow.steps.lastIndex) {
                        Text("→", fontSize = 10.sp, color = BeeGray.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

/**
 * Input view — user provides the starting input for the workflow.
 */
@Composable
private fun WorkflowInputView(
    workflow: Workflow,
    userInput: String,
    onInputChange: (String) -> Unit,
    onRun: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = BeeGray)
            }
            Text(workflow.icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                workflow.name,
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = BeeWhite
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(workflow.description, fontSize = 13.sp, color = BeeGray)

        Spacer(modifier = Modifier.height(16.dp))

        // Steps preview
        Text("PASOS DEL FLUJO", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        workflow.steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
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
                Text("${step.icon} ${step.label}", fontSize = 13.sp, color = BeeWhite)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (step.type == StepType.AGENT) "🤖" else "🔧",
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Input field
        Text("📝 ¿QUÉ QUIERES HACER?", fontSize = 10.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
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
            modifier = Modifier.fillMaxWidth().height(120.dp),
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BeeWhite,
                unfocusedTextColor = BeeWhite,
                focusedBorderColor = BeeYellow,
                unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                cursorColor = BeeYellow
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Run button
        Button(
            onClick = onRun,
            enabled = userInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BeeYellow,
                contentColor = BeeBlack,
                disabledContainerColor = BeeGray.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, "Run", modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Ejecutar Workflow",
                fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Execution view — shows real-time node states while workflow runs.
 */
@Composable
private fun WorkflowExecutionView(
    run: WorkflowRun,
    isRunning: Boolean,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(run.workflow.icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    run.workflow.name,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = BeeWhite
                )
                Text(
                    when {
                        run.isComplete -> "✅ Completado"
                        run.isFailed -> "❌ Falló"
                        isRunning -> "🔄 Ejecutando..."
                        else -> "⏳ Preparando..."
                    },
                    fontSize = 12.sp,
                    color = when {
                        run.isComplete -> Color(0xFF4CAF50)
                        run.isFailed -> Color(0xFFF44336)
                        else -> BeeYellow
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Node chain (n8n style) ──
        run.stepStates.forEachIndexed { index, stepState ->
            WorkflowNode(stepState = stepState, stepNumber = index + 1)

            // Connector line between nodes
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

        // ── Final Output ──
        if (run.isComplete || run.isFailed) {
            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                color = if (run.isComplete) Color(0xFF1A3E1A) else Color(0xFF3E1A1A),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (run.isComplete) Color(0xFF4CAF50).copy(alpha = 0.4f)
                    else Color(0xFFF44336).copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        if (run.isComplete) "📋 RESULTADO FINAL" else "❌ ERROR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (run.isComplete) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        run.finalOutput.take(2000),
                        fontSize = 12.sp,
                        color = BeeWhite.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Done button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BeeGray.copy(alpha = 0.3f),
                    contentColor = BeeWhite
                ),
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("← Volver a Workflows", fontSize = 14.sp)
            }
        }
    }
}

/**
 * Single workflow node — visual representation of a step with status.
 */
@Composable
private fun WorkflowNode(stepState: StepState, stepNumber: Int) {
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

    val statusIcon = when (stepState.status) {
        StepStatus.PENDING -> "⏳"
        StepStatus.RUNNING -> "🔄"
        StepStatus.COMPLETED -> "✅"
        StepStatus.FAILED -> "❌"
        StepStatus.SKIPPED -> "⏭️"
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Step number circle
                Surface(
                    color = borderColor.copy(alpha = 0.3f),
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(step.icon, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            step.label,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = if (stepState.status == StepStatus.SKIPPED) BeeGray else BeeWhite
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (step.type == StepType.AGENT) "🤖 ${step.agentId}" else "🔧 ${step.skillName}",
                            fontSize = 9.sp, color = BeeGray
                        )
                    }
                    if (stepState.elapsedMs > 0) {
                        Text(
                            "${stepState.elapsedMs / 1000.0}s",
                            fontSize = 9.sp, color = BeeGray.copy(alpha = 0.6f)
                        )
                    }
                }

                Text(statusIcon, fontSize = 18.sp)
            }

            // Output preview (when completed)
            if (stepState.status == StepStatus.COMPLETED && stepState.output.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF0D0D1A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stepState.output.take(300) + if (stepState.output.length > 300) "..." else "",
                        fontSize = 11.sp, color = BeeGrayLight,
                        modifier = Modifier.padding(8.dp),
                        lineHeight = 16.sp
                    )
                }
            }

            // Error message
            if (stepState.status == StepStatus.FAILED && stepState.error.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Error: ${stepState.error}",
                    fontSize = 11.sp, color = Color(0xFFF44336)
                )
            }
        }
    }
}
