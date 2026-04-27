package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.tasks.EmmaTask
import com.beemovil.tasks.EmmaSubtask
import com.beemovil.tasks.TaskAttachment
import com.beemovil.tasks.EmmaTaskDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * EmmaTaskPlugin — Universal task manager for E.M.M.A.
 * Supports: create, list, complete, update, delete, assign, add_subtask.
 * Works from chat, voice, vision, and background conversations.
 */
class EmmaTaskPlugin(private val context: Context) : EmmaPlugin {
    override val id = "emma_tasks"
    private val TAG = "EmmaTaskPlugin"
    private val db by lazy { EmmaTaskDB.getDatabase(context) }
    private val dao by lazy { db.taskDao() }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Gestiona las tareas del usuario. Crea, lista, completa, actualiza, " +
                "asigna y organiza tareas. Úsalo cuando el usuario hable de pendientes, tareas, " +
                "to-do, recordatorios, seguimiento, 'qué tengo que hacer', 'agrégame una tarea', " +
                "'mis pendientes de hoy', etc. También para asignar tareas a Emma o a terceras personas.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray()
                            .put("create").put("list").put("complete")
                            .put("update").put("delete").put("assign")
                            .put("add_subtask").put("search").put("attach").put("email_task"))
                        put("description", "Acción a ejecutar sobre las tareas.")
                    })
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Título de la tarea (para create/update).")
                    })
                    put("notes", JSONObject().apply {
                        put("type", "string")
                        put("description", "Notas adicionales de la tarea.")
                    })
                    put("assignee", JSONObject().apply {
                        put("type", "string")
                        put("description", "A quién asignar: 'user' (yo), 'emma', o nombre/email de tercero.")
                    })
                    put("priority", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("normal").put("low").put("high").put("urgent"))
                        put("description", "Prioridad: normal, low, high, urgent.")
                    })
                    put("due_date", JSONObject().apply {
                        put("type", "string")
                        put("description", "Fecha límite. Formato ISO '2026-04-28' o natural: 'mañana', 'viernes', 'hoy'.")
                    })
                    put("due_time", JSONObject().apply {
                        put("type", "string")
                        put("description", "Hora límite: '17:00', '5pm'.")
                    })
                    put("task_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "ID de tarea existente (para complete/update/delete/assign/add_subtask).")
                    })
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Texto de búsqueda (para search). Busca en título y notas.")
                    })
                    put("filter", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("all").put("mine").put("emma").put("delegated").put("overdue").put("today").put("completed"))
                        put("description", "Filtro para listar: all, mine, emma, delegated, overdue, today, completed.")
                    })
                    put("tags", JSONObject().apply {
                        put("type", "string")
                        put("description", "Tags separados por coma: 'trabajo,mahana,urgente'.")
                    })
                    put("subtask_title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Título de la sub-tarea (para add_subtask).")
                    })
                    put("recurrence", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("none").put("daily").put("weekly").put("monthly"))
                        put("description", "Recurrencia: none, daily, weekly, monthly.")
                    })
                    put("recurrence_days", JSONObject().apply {
                        put("type", "string")
                        put("description", "Días para recurrencia semanal: 'MON,WED,FRI'.")
                    })
                    put("source", JSONObject().apply {
                        put("type", "string")
                        put("description", "Origen de la tarea: 'chat', 'voice', 'vision', 'email'. Auto-detectado si no se especifica.")
                    })
                    put("file_path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Path absoluto del archivo a adjuntar (para action='attach'). Usa el path devuelto por otros plugins como export_pdf, generate_image, etc.")
                    })
                    put("file_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "Nombre legible del archivo (opcional, se extrae del path si no se da).")
                    })
                    put("to", JSONObject().apply {
                        put("type", "string")
                        put("description", "Email del destinatario (para action='email_task'). Requerido para enviar tarea por correo.")
                    })
                })
                put("required", JSONArray().put("action"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val action = args["action"] as? String ?: "list"
        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "create" -> createTask(args)
                    "list" -> listTasks(args)
                    "complete" -> completeTask(args)
                    "update" -> updateTask(args)
                    "delete" -> deleteTask(args)
                    "assign" -> assignTask(args)
                    "add_subtask" -> addSubtask(args)
                    "search" -> searchTasks(args)
                    "attach" -> attachFile(args)
                    "email_task" -> emailTask(args)
                    else -> "Acción '$action' no reconocida. Usa: create, list, complete, update, delete, assign, add_subtask, search, attach, email_task."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Task plugin error: ${e.message}", e)
                "❌ Error en tareas: ${e.message}"
            }
        }
    }

    // ═══════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════

    private suspend fun createTask(args: Map<String, Any>): String {
        val title = args["title"] as? String ?: return "❌ Falta el título de la tarea."
        val notes = args["notes"] as? String ?: ""
        val priority = parsePriority(args["priority"] as? String)
        val assignee = args["assignee"] as? String ?: "user"
        val assigneeType = when {
            assignee == "user" || assignee.equals("yo", true) || assignee.equals("mi", true) -> "user"
            assignee == "emma" || assignee.equals("e.m.m.a.", true) -> "emma"
            else -> "external"
        }
        val normalizedAssignee = when (assigneeType) {
            "user" -> "user"
            "emma" -> "emma"
            else -> assignee
        }
        val dueDate = parseDate(args["due_date"] as? String)
        val dueTime = args["due_time"] as? String
        val tags = args["tags"] as? String
        val source = args["source"] as? String ?: "chat"

        // Recurrence
        val recurrenceStr = args["recurrence"] as? String ?: "none"
        val isRecurring = recurrenceStr != "none"
        val recurrenceRule = if (isRecurring) recurrenceStr.uppercase() else null
        val recurrenceDays = args["recurrence_days"] as? String

        val task = EmmaTask(
            title = title,
            notes = notes,
            priority = priority,
            assignee = normalizedAssignee,
            assigneeType = assigneeType,
            dueDate = dueDate,
            dueTime = dueTime,
            tags = tags,
            source = source,
            isRecurring = isRecurring,
            recurrenceRule = recurrenceRule,
            recurrenceDays = recurrenceDays
        )
        dao.insertTask(task)

        val assignLabel = when (assigneeType) {
            "user" -> "ti"
            "emma" -> "mí (Emma)"
            else -> normalizedAssignee
        }
        val dueLabel = if (dueDate != null) " | Vence: ${formatDate(dueDate)}" else ""
        val recurLabel = if (isRecurring) " | 🔄 Recurrente: $recurrenceRule" else ""
        val tagLabel = if (!tags.isNullOrBlank()) " | 🏷️ $tags" else ""

        return "✅ Tarea creada: \"$title\" → asignada a $assignLabel$dueLabel$recurLabel$tagLabel"
    }

    // ═══════════════════════════════════════
    //  LIST
    // ═══════════════════════════════════════

    private suspend fun listTasks(args: Map<String, Any>): String {
        val filter = args["filter"] as? String ?: "all"
        val tasks = when (filter) {
            "mine" -> dao.getTasksByAssigneeType("user")
            "emma" -> dao.getTasksByAssigneeType("emma")
            "delegated" -> dao.getTasksByAssigneeType("external")
            "today" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                }
                dao.getTasksDueBy(cal.timeInMillis)
            }
            "overdue" -> {
                val now = System.currentTimeMillis()
                dao.getTasksDueBy(now).filter { it.status != "completed" }
            }
            "completed" -> dao.getCompletedTasks(20)
            else -> dao.getPendingTasks()
        }

        if (tasks.isEmpty()) {
            return when (filter) {
                "today" -> "🎉 No tienes tareas para hoy."
                "overdue" -> "✅ No tienes tareas vencidas."
                "completed" -> "No hay tareas completadas recientes."
                else -> "📋 No tienes tareas pendientes."
            }
        }

        return formatTaskList(tasks, filter)
    }

    // ═══════════════════════════════════════
    //  COMPLETE
    // ═══════════════════════════════════════

    private suspend fun completeTask(args: Map<String, Any>): String {
        val taskId = args["task_id"] as? String
        val query = args["query"] as? String ?: args["title"] as? String

        val task = if (taskId != null) {
            dao.getTaskById(taskId)
        } else if (query != null) {
            dao.searchTasks(query).firstOrNull()
        } else {
            return "❌ Necesito el ID o título de la tarea a completar."
        }

        if (task == null) return "❌ No encontré esa tarea."

        dao.updateTask(task.copy(
            status = "completed",
            completedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ))
        return "✅ Tarea completada: \"${task.title}\""
    }

    // ═══════════════════════════════════════
    //  UPDATE
    // ═══════════════════════════════════════

    private suspend fun updateTask(args: Map<String, Any>): String {
        val taskId = args["task_id"] as? String
        val query = args["query"] as? String ?: args["title"] as? String

        val task = if (taskId != null) {
            dao.getTaskById(taskId)
        } else if (query != null) {
            dao.searchTasks(query).firstOrNull()
        } else {
            return "❌ Necesito el ID o título de la tarea a actualizar."
        }

        if (task == null) return "❌ No encontré esa tarea."

        val updated = task.copy(
            title = args["title"] as? String ?: task.title,
            notes = args["notes"] as? String ?: task.notes,
            priority = if (args.containsKey("priority")) parsePriority(args["priority"] as? String) else task.priority,
            dueDate = if (args.containsKey("due_date")) parseDate(args["due_date"] as? String) else task.dueDate,
            dueTime = args["due_time"] as? String ?: task.dueTime,
            tags = args["tags"] as? String ?: task.tags,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateTask(updated)
        return "✅ Tarea actualizada: \"${updated.title}\""
    }

    // ═══════════════════════════════════════
    //  DELETE
    // ═══════════════════════════════════════

    private suspend fun deleteTask(args: Map<String, Any>): String {
        val taskId = args["task_id"] as? String
        val query = args["query"] as? String ?: args["title"] as? String

        val task = if (taskId != null) {
            dao.getTaskById(taskId)
        } else if (query != null) {
            dao.searchTasks(query).firstOrNull()
        } else {
            return "❌ Necesito el ID o título de la tarea a eliminar."
        }

        if (task == null) return "❌ No encontré esa tarea."
        dao.deleteTask(task)
        return "🗑️ Tarea eliminada: \"${task.title}\""
    }

    // ═══════════════════════════════════════
    //  ASSIGN
    // ═══════════════════════════════════════

    private suspend fun assignTask(args: Map<String, Any>): String {
        val taskId = args["task_id"] as? String
        val query = args["query"] as? String ?: args["title"] as? String
        val newAssignee = args["assignee"] as? String ?: return "❌ Falta a quién asignar."

        val task = if (taskId != null) {
            dao.getTaskById(taskId)
        } else if (query != null) {
            dao.searchTasks(query).firstOrNull()
        } else {
            return "❌ Necesito el ID o título de la tarea."
        }

        if (task == null) return "❌ No encontré esa tarea."

        val assigneeType = when {
            newAssignee == "user" || newAssignee.equals("yo", true) -> "user"
            newAssignee == "emma" || newAssignee.equals("e.m.m.a.", true) -> "emma"
            else -> "external"
        }
        val normalized = when (assigneeType) {
            "user" -> "user"
            "emma" -> "emma"
            else -> newAssignee
        }

        dao.updateTask(task.copy(
            assignee = normalized,
            assigneeType = assigneeType,
            updatedAt = System.currentTimeMillis()
        ))

        val label = when (assigneeType) {
            "user" -> "ti"
            "emma" -> "mí (Emma)"
            else -> normalized
        }
        return "✅ Tarea \"${task.title}\" asignada a $label"
    }

    // ═══════════════════════════════════════
    //  ADD SUBTASK
    // ═══════════════════════════════════════

    private suspend fun addSubtask(args: Map<String, Any>): String {
        val taskId = args["task_id"] as? String
        val query = args["query"] as? String ?: args["title"] as? String
        val subtaskTitle = args["subtask_title"] as? String
            ?: return "❌ Falta el título de la sub-tarea."

        val task = if (taskId != null) {
            dao.getTaskById(taskId)
        } else if (query != null) {
            dao.searchTasks(query).firstOrNull()
        } else {
            return "❌ Necesito el ID o título de la tarea padre."
        }

        if (task == null) return "❌ No encontré la tarea padre."

        val existing = dao.getSubtasks(task.id)
        val subtask = EmmaSubtask(
            taskId = task.id,
            title = subtaskTitle,
            sortOrder = existing.size
        )
        dao.insertSubtask(subtask)
        return "✅ Sub-tarea agregada a \"${task.title}\": \"$subtaskTitle\" (${existing.size + 1} sub-tareas)"
    }

    // ═══════════════════════════════════════
    //  SEARCH
    // ═══════════════════════════════════════

    private suspend fun searchTasks(args: Map<String, Any>): String {
        val query = args["query"] as? String ?: return "❌ Falta el texto de búsqueda."
        val results = dao.searchTasks(query)
        if (results.isEmpty()) return "🔍 No encontré tareas con \"$query\"."
        return formatTaskList(results, "search: $query")
    }

    // ═══════════════════════════════════════
    //  ATTACH FILE
    // ═══════════════════════════════════════

    private suspend fun attachFile(args: Map<String, Any>): String {
        val filePath = args["file_path"] as? String ?: return "❌ Falta el file_path del archivo a adjuntar."
        val taskId = args["task_id"] as? String
        val query = args["query"] as? String ?: args["title"] as? String

        val task = if (taskId != null) {
            dao.getTaskById(taskId)
        } else if (query != null) {
            dao.searchTasks(query).firstOrNull()
        } else {
            return "❌ Necesito el ID o título de la tarea donde adjuntar."
        }
        if (task == null) return "❌ No encontré esa tarea."

        val file = java.io.File(filePath)
        val fileName = args["file_name"] as? String ?: file.name
        val mimeType = when {
            filePath.endsWith(".pdf") -> "application/pdf"
            filePath.endsWith(".png") -> "image/png"
            filePath.endsWith(".jpg") || filePath.endsWith(".jpeg") -> "image/jpeg"
            filePath.endsWith(".csv") -> "text/csv"
            filePath.endsWith(".html") -> "text/html"
            else -> "application/octet-stream"
        }
        val sizeBytes = if (file.exists()) file.length() else null

        dao.insertAttachment(TaskAttachment(
            taskId = task.id,
            filePath = filePath,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            source = "emma_generated"
        ))

        val sizeLabel = if (sizeBytes != null) " (${sizeBytes / 1024} KB)" else ""
        return "✅ Archivo \"$fileName\"$sizeLabel adjuntado a tarea \"${task.title}\""
    }

    // ═══════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════

    private fun parsePriority(p: String?): Int = when (p?.lowercase()) {
        "urgent", "urgente" -> 3
        "high", "alta" -> 2
        "low", "baja" -> 1
        else -> 0
    }

    private fun parseDate(dateStr: String?): Long? {
        if (dateStr == null) return null
        val cal = Calendar.getInstance()
        return when (dateStr.lowercase().trim()) {
            "hoy", "today" -> cal.timeInMillis
            "mañana", "tomorrow" -> { cal.add(Calendar.DAY_OF_MONTH, 1); cal.timeInMillis }
            "pasado mañana" -> { cal.add(Calendar.DAY_OF_MONTH, 2); cal.timeInMillis }
            "lunes", "monday" -> nextDayOfWeek(Calendar.MONDAY)
            "martes", "tuesday" -> nextDayOfWeek(Calendar.TUESDAY)
            "miercoles", "miércoles", "wednesday" -> nextDayOfWeek(Calendar.WEDNESDAY)
            "jueves", "thursday" -> nextDayOfWeek(Calendar.THURSDAY)
            "viernes", "friday" -> nextDayOfWeek(Calendar.FRIDAY)
            "sabado", "sábado", "saturday" -> nextDayOfWeek(Calendar.SATURDAY)
            "domingo", "sunday" -> nextDayOfWeek(Calendar.SUNDAY)
            else -> {
                try {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time
                } catch (_: Exception) {
                    try {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateStr)?.time
                    } catch (_: Exception) { null }
                }
            }
        }
    }

    private fun nextDayOfWeek(dayOfWeek: Int): Long {
        val cal = Calendar.getInstance()
        val current = cal.get(Calendar.DAY_OF_WEEK)
        var daysAhead = dayOfWeek - current
        if (daysAhead <= 0) daysAhead += 7
        cal.add(Calendar.DAY_OF_MONTH, daysAhead)
        return cal.timeInMillis
    }

    private fun formatDate(ms: Long): String {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))
    }

    private fun formatTaskList(tasks: List<EmmaTask>, filterLabel: String): String {
        val sb = StringBuilder()
        sb.appendLine("📋 Tareas ($filterLabel) — ${tasks.size} resultado(s):")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        tasks.forEachIndexed { i, t ->
            val statusIcon = when (t.status) {
                "completed" -> "✅"
                "in_progress" -> "⏳"
                else -> when (t.priority) {
                    3 -> "🔴"
                    2 -> "🟡"
                    1 -> "🔵"
                    else -> "⚪"
                }
            }
            val assignLabel = when (t.assigneeType) {
                "user" -> "→ Yo"
                "emma" -> "→ Emma"
                else -> "→ ${t.assignee}"
            }
            val dueLabel = if (t.dueDate != null) " | Vence: ${formatDate(t.dueDate)}" else ""
            val tagLabel = if (!t.tags.isNullOrBlank()) " | 🏷️ ${t.tags}" else ""
            val recurLabel = if (t.isRecurring) " | 🔄" else ""

            sb.appendLine("$statusIcon ${i + 1}. ${t.title} $assignLabel$dueLabel$tagLabel$recurLabel")
            if (t.notes.isNotBlank()) {
                sb.appendLine("   📝 ${t.notes.take(80)}")
            }
            sb.appendLine("   ID: ${t.id.take(8)}")
        }
        return sb.toString()
    }

    // ═══════════════════════════════════════
    // EMAIL TASK
    // ═══════════════════════════════════════
    private suspend fun emailTask(args: Map<String, Any>): String {
        val to = args["to"] as? String ?: return "❌ Falta el email del destinatario (parámetro 'to')."
        val taskId = args["task_id"] as? String
        val query = args["query"] as? String ?: args["title"] as? String

        val task = when {
            taskId != null -> dao.getTaskById(taskId)
            query != null -> dao.searchTasks(query).firstOrNull()
            else -> return "❌ Necesito task_id o query para identificar la tarea."
        } ?: return "❌ No encontré la tarea."

        val subs = dao.getSubtasks(task.id)
        val attachments = dao.getAttachments(task.id)

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val body = buildString {
            appendLine("📋 Tarea: ${task.title}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            if (task.notes.isNotBlank()) appendLine("📝 ${task.notes}")
            appendLine("📅 Estado: ${task.status}")
            if (task.dueDate != null) appendLine("📅 Vence: ${sdf.format(Date(task.dueDate))}")
            val aLabel = when (task.assigneeType) { "user" -> "Yo"; "emma" -> "Emma"; else -> task.assignee }
            appendLine("👤 Asignada a: $aLabel")
            if (!task.tags.isNullOrBlank()) appendLine("🏷️ Tags: ${task.tags}")
            if (subs.isNotEmpty()) {
                appendLine("\n☑ Sub-tareas (${subs.count { it.completed }}/${subs.size}):")
                subs.forEach { s -> appendLine("  ${if (s.completed) "✅" else "⬜"} ${s.title}") }
            }
            if (attachments.isNotEmpty()) appendLine("\n📎 Adjuntos: ${attachments.size} archivo(s)")
            appendLine("\n— Enviado desde E.M.M.A. AI")
        }

        val attachPaths = attachments.mapNotNull { att ->
            val f = java.io.File(att.filePath)
            if (f.exists()) f.absolutePath else null
        }

        val subject = "📋 Tarea: ${task.title}"

        try {
            val intent = android.content.Intent(if (attachPaths.size > 1) android.content.Intent.ACTION_SEND_MULTIPLE else android.content.Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                putExtra(android.content.Intent.EXTRA_TEXT, body)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                
                if (attachPaths.isNotEmpty()) {
                    if (attachPaths.size == 1) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            java.io.File(attachPaths.first())
                        )
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    } else {
                        val uris = attachPaths.map { path ->
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                java.io.File(path)
                            )
                        }
                        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, java.util.ArrayList(uris))
                    }
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                val attLabel = if (attachPaths.isNotEmpty()) " con ${attachPaths.size} adjunto(s)" else ""
                return "✅ He preparado el correo para $to$attLabel. Por favor revisa y envía."
            } else {
                return "❌ Error: No se encontró una aplicación de correo electrónico instalada."
            }
        } catch (e: Exception) {
            return "❌ Error preparando el correo: ${e.message}"
        }
    }
}
