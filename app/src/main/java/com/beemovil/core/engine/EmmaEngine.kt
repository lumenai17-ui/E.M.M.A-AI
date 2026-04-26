package com.beemovil.core.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.beemovil.core.config.ApiConfigManager
import com.beemovil.llm.ChatMessage
import com.beemovil.llm.LlmFactory
import com.beemovil.llm.ToolDefinition

class EmmaEngine(private val context: Context) {

    private val TAG = "EmmaEngine"
    private val configManager = ApiConfigManager.getInstance(context)
    
    // Tunnel connections are now handled globally in HermesTunnelManager

    private val chatHistoryDB = com.beemovil.database.ChatHistoryDB.getDatabase(context)
    
    private val historyMutex = Mutex()
    private val messagesHistory = mutableListOf<ChatMessage>()

    private val EMMA_SUPERVISOR_PROMPT = """
        Eres E.M.M.A., la Coordinadora Central. Evalúa el pedido del usuario.
        - Si el usuario pide explícitamente REDACTAR un ensayo, correo, o texto muy largo, USA LA TOOL 'delegate_to_writer'.
        - Si el usuario pide BUSCAR en internet, ir a una URL, o necesita navegación web interactiva, USA LA TOOL 'delegate_to_browser'.
        - Si el usuario te pide un CÁLCULO matemático, una lógica compleja, o procesar texto, UTILIZA INMEDIATAMENTE la tool 'execute_js_script' para obtener un resultado determinista sin inventar datos.
        - Si el usuario envía una URL para que ACUMULES conocimiento o LEAS el artículo, saca el texto crudo usando 'scrape_website_text'.
        - Si el usuario adjunta una Foto/Imagen o Documento físico, USA 'read_image_text_ocr' o 'read_document_file' respectivamente para entender qué hay allí adentro.
        - Si el usuario te pide un entregable formal (ensayo en PDF, tabla de Excel/CSV, o generar una página Web), NUNCA lo escribas en el chat. Para PDFs BONITOS o profesionales (propuestas, reportes, catálogos, curriculums, presentaciones) usa 'generate_premium_pdf': genera TODO el HTML+CSS como si fuera una landing page premium con diseño moderno (gradientes, secciones coloridas, tipografía elegante, imágenes con Pollinations) y el sistema lo convierte a PDF preservando el diseño visual exacto. Para PDFs de texto simple, usa 'generate_pdf_document'. Para tablas usa 'generate_csv_table'. Para webs usa 'generate_html_landing'. INCLUIR IMÁGENES: en premium PDF y HTML usa tags <img> normales con URLs de Pollinations: <img src="https://image.pollinations.ai/prompt/descripcion_en_ingles?width=800&height=400&nologo=true">. En PDF básico usa el marcador [IMG:https://image.pollinations.ai/prompt/descripcion_en_ingles] dentro del body_text.
        - ¡TIENES ACCESO A SU TELÉFONO! Si te piden leer la agenda, agendar una junta, prender la linterna, poner una alarma o buscar en los Contactos, DEBES usar el plugin correspondiente ('os_god_mode_operations', 'calendar_os_operations', 'search_android_contacts') sin excusas. NO digas que no tienes acceso.
        - ERES UN COMUNICADOR EN RED: Si te piden mandar un correo o mandar un WhatsApp a alguien, JAMÁS digas que no puedes. Usa 'compose_email_intent' o 'send_whatsapp_message' automáticamente. Si necesitas consultar una API web o extraer datos, usa 'fetch_external_api'.
        - TIENES ACCESO AL ECOSISTEMA GOOGLE DEL USUARIO: Si preguntan por sus emails, usa 'google_gmail'. Si preguntan por su agenda o quieren crear un evento, usa 'google_calendar'. Si preguntan por tareas pendientes, usa 'google_tasks'. Si el usuario NO está conectado a Google, dile que vaya a Settings → Google.
        - PUEDES GENERAR IMÁGENES CON IA: Si el usuario pide 'genera una imagen', 'dibuja', 'crea una ilustración', 'hazme un logo' o cualquier variación, usa 'generate_ai_image'. Traduce el prompt a inglés para mejores resultados y elige el estilo más adecuado. La imagen generada aparecerá automáticamente embebida dentro del chat — NO digas que no puedes mostrarla, el sistema la incrusta automáticamente.
        - PUEDES GENERAR MÚSICA CON IA: Si el usuario pide 'genera música', 'crea una canción', 'haz un beat', 'compón algo', usa 'generate_ai_music'. Describe el estilo musical en inglés (género, mood, instrumentos).
        - PUEDES GENERAR VIDEOS CON IA: Si el usuario pide 'genera un video', 'crea un clip', 'haz un video de...', 'anima esto', usa 'generate_ai_video'. Describe la escena en inglés. Los videos son de 3-10 segundos.
        - PUEDES HABLAR CON VOZ IA: Si el usuario pide 'lee esto en voz alta', 'dime con voz', 'reproduce este texto', 'léeme esto', usa 'speak_with_ai_voice'.
        - REGLA ABSOLUTA DE ARCHIVOS: ESTÁ PROHIBIDO inventar rutas de archivos o simular que generaste algo. Para generar imágenes, PDFs, HTML, CSV, música o video DEBES INVOCAR LA HERRAMIENTA correspondiente (generate_ai_image, generate_premium_pdf, export_pdf, export_csv, generate_html_page, generate_ai_music, generate_ai_video). Si no invocas la herramienta, el usuario NO verá ningún archivo. El sistema embebe automáticamente los archivos generados en el chat con preview, botón de abrir y compartir. NUNCA escribas rutas de archivo en texto plano.
        - TIENES CONCIENCIA DE TI MISMA (Project Autonomía): Si el usuario pregunta '¿por qué no funciona X?', '¿qué modelo uso?', '¿qué permisos tengo?', '¿cómo estás configurada?', '¿qué agentes tengo?', o CUALQUIER pregunta sobre tu propio estado, configuración o salud, usa 'emma_diagnostics' o 'emma_self_config' para obtener datos REALES. NUNCA inventes tu estado — SIEMPRE consulta el plugin.
        - PUEDES AUTO-CONFIGURARTE: Si el usuario dice 'guarda este token', 'pega este API key', 'cambia al modelo X', 'activa/desactiva TTS', usa 'emma_self_config' con las operaciones de escritura (update_api_key, change_model, toggle_feature). Para API keys usa update_api_key con provider_name y api_key_value.
        - PUEDES GESTIONAR AGENTES: Si el usuario dice 'créame un agente de cocina', 'edita el agente X', 'borra el agente Y', usa 'emma_agent_manager'. Genera system_prompts detallados y elige un icono apropiado.
        - PUEDES USAR EL PORTAPAPELES: Si el usuario dice 'copia esto', 'ponlo en el clipboard', '¿qué tengo copiado?', usa 'emma_clipboard'.
        - CONTROLAS EL ENTORNO: Puedes gestionar archivos ('organiza Downloads', 'busca PDFs', 'borra este archivo') con 'emma_file_manager'. Puedes abrir cualquier app ('abre Instagram') con 'emma_app_launcher'. Puedes controlar brillo, No Molestar y wallpaper con 'emma_system_control'.
        - INTELIGENCIA PROACTIVA: Puedes programar tareas recurrentes ('dame un briefing cada mañana', 'reporte semanal') con 'emma_scheduler'. Puedes dar reportes de screen time ('¿cuánto usé el teléfono?', '¿qué apps usé hoy?') con 'emma_app_usage'.
        - Si es charla común, responde de forma amigable, corta y directa en español.
    """.trimIndent()

    private val BROWSER_AGENT_PROMPT = """
        Eres el Agente Especialista en Navegación de E.M.M.A. 
        Tu único objetivo es usar la herramienta 'open_browser' para asistir al usuario. 
        Genera la URL o ruta de búsqueda, llama a la herramienta y despídete con un mensaje corto diciendo que has procedido a abrir la interfaz.
    """.trimIndent()

    private val WRITER_AGENT_PROMPT = """
        Eres el Agente Experto Redactor de E.M.M.A.
        Escribes borradores perfectos, correos corporativos impecables y ensayos estructurados. 
        Desarrolla el texto completo y profesionalmente sin pedir permiso.
    """.trimIndent()

    private var memoryDB: com.beemovil.memory.BeeMemoryDB? = null

    val plugins = mutableMapOf<String, com.beemovil.plugins.EmmaPlugin>()

    fun registerPlugin(plugin: com.beemovil.plugins.EmmaPlugin) {
        plugins[plugin.id] = plugin
    }

    suspend fun initialize() {
        Log.i(TAG, "Inicializando motor Multi-Agente LlmFactory...")
        
        memoryDB = com.beemovil.memory.BeeMemoryDB(context)
        
        // Registrar plugins base
        registerPlugin(com.beemovil.plugins.builtins.DateTimePlugin())
        registerPlugin(com.beemovil.plugins.builtins.WebSearchPlugin())
        registerPlugin(com.beemovil.plugins.builtins.MemoryPlugin(memoryDB!!))
        
        // Registrar hardware Tools (Phase 7 Telemetry/God Mode)
        registerPlugin(com.beemovil.plugins.builtins.FlashlightPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.VolumePlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.TelemetryQueryPlugin(context))
        
        // Registrar Ultra-Skills (Phase 10)
        registerPlugin(com.beemovil.plugins.builtins.CodeSandboxPlugin())
        
        // Registrar Ingestors (Phase 10.2)
        registerPlugin(com.beemovil.plugins.builtins.WebScraperPlugin())
        registerPlugin(com.beemovil.plugins.builtins.AnalyzeImagePlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.ReadDocumentPlugin(context))

        // Registrar Generators (Phase 10.3)
        registerPlugin(com.beemovil.plugins.builtins.ExportPdfPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.PremiumPdfPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.ExportCsvPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.HTMLForgePlugin(context))
        
        // Registrar Operators (Phase 10.4)
        registerPlugin(com.beemovil.plugins.builtins.ContactManagerPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.CalendarOperatorPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.SystemGodModePlugin(context))

        // Registrar Networkers (Phase 10.5)
        registerPlugin(com.beemovil.plugins.builtins.EmailComposerPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.WhatsAppAutomatorPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.WebApiFetcherPlugin())

        // Registrar Google Ecosystem (Sprint 4)
        registerPlugin(com.beemovil.plugins.builtins.GmailPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.PersonalEmailPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.GoogleCalendarPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.GoogleTasksPlugin(context))

        // Registrar Image Generation (Sprint 6)
        registerPlugin(com.beemovil.plugins.builtins.ImageGenerationPlugin(context))

        // Registrar Pollinations Media Studio (Phase 16)
        registerPlugin(com.beemovil.plugins.builtins.MusicGenerationPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.VideoGenerationPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.PollinationsTTSPlugin(context))
        
        // Registrar Agent-to-Agent Delegation (Agentic Loop Phase E)
        registerPlugin(com.beemovil.plugins.builtins.DelegateToAgentPlugin(context))

        // Registrar Self-Control Layer (Project Autonomía Phase S1)
        registerPlugin(com.beemovil.plugins.builtins.DiagnosticsPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.SelfConfigPlugin(context))

        // Registrar Self-Modification Layer (Project Autonomía Phase S2)
        registerPlugin(com.beemovil.plugins.builtins.ClipboardPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.AgentManagerPlugin(context))

        // Registrar Environment Control Layer (Project Autonomía Phase S3)
        registerPlugin(com.beemovil.plugins.builtins.FileManagerPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.AppLauncherPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.SystemControlPlugin(context))

        // Registrar Proactive Intelligence Layer (Project Autonomía Phase S4)
        registerPlugin(com.beemovil.plugins.builtins.SchedulerPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.AppUsagePlugin(context))

        // Registrar LifeStream Intelligence (Signal Query)
        registerPlugin(com.beemovil.plugins.builtins.LifeStreamPlugin(context))

        // Registrar Public APIs Intelligence Layer (v7.2 — Zero-cost contextual data)
        registerPlugin(com.beemovil.plugins.builtins.WeatherPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.CurrencyPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.DictionaryPlugin(context))
        registerPlugin(com.beemovil.plugins.builtins.HolidayPlugin(context))
        
        try {
            if (messagesHistory.isEmpty()) {
                val pastMemories = memoryDB?.getAllMemories()?.joinToString("\n") ?: ""
                val memoryInjection = if (pastMemories.isNotBlank()) "\nMEMORIAS PREVIAS DEL USUARIO:\n$pastMemories\n" else ""
                
                // Inject available agents list for A2A delegation
                val agentInjection = try {
                    val agents = chatHistoryDB.chatHistoryDao().getAllAgentsSync()
                    if (agents.isNotEmpty()) {
                        val agentList = agents.joinToString("\n") { "  - ${it.name} (${it.icon}): ${it.systemPrompt.take(80)}..." }
                        "\n\nAGENTES ESPECIALIZADOS DISPONIBLES (usa delegate_to_agent para delegarles tareas):\n$agentList\n"
                    } else ""
                } catch (e: Exception) { "" }

                // R7: Cross-system context (Tasks, Email, Calendar, behavioral)
                val crossContextInjection = try {
                    val crossEngine = com.beemovil.vision.CrossContextEngine(context)
                    val paragraph = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        crossEngine.buildContextParagraph()
                    }
                    if (paragraph.isNotBlank()) "\n\nCONTEXTO SITUACIONAL DEL USUARIO (usa esto para ser proactivo):\n$paragraph\n" else ""
                } catch (e: Exception) {
                    Log.w(TAG, "CrossContext for chat failed: ${e.message}")
                    ""
                }
                
                messagesHistory.add(ChatMessage("system", EMMA_SUPERVISOR_PROMPT + memoryInjection + agentInjection + crossContextInjection))
            }
            delay(500)
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando Multi-Agent Router: ${e.message}")
        }
    }

    suspend fun clearMemoryAndHistory(customSystemPrompt: String? = null) {
        Log.d(TAG, "Clearing volatile context...")
        historyMutex.withLock {
            messagesHistory.clear()
            val pastMemories = memoryDB?.getAllMemories()?.joinToString("\n") ?: ""
            val memoryInjection = if (pastMemories.isNotBlank()) "\nMEMORIAS PREVIAS DEL USUARIO:\n$pastMemories\n" else ""
            val basePrompt = customSystemPrompt?.takeIf { it.isNotBlank() } ?: EMMA_SUPERVISOR_PROMPT
            messagesHistory.add(ChatMessage("system", basePrompt + memoryInjection))
        }
    }

    suspend fun loadPersistedContext(historyEntities: List<com.beemovil.database.ChatMessageEntity>, customSystemPrompt: String? = null) {
        Log.i(TAG, "Re-Hidratando Contexto Persistente (${historyEntities.size} mensajes)...")
        clearMemoryAndHistory(customSystemPrompt)
        historyMutex.withLock {
            historyEntities.forEach { entity ->
                if (entity.role != "system") {
                    messagesHistory.add(ChatMessage(role = entity.role, content = entity.content))
                }
            }
        }
        Log.d(TAG, "LLM Cortex restaurado con total éxito.")
    }

    suspend fun processUserMessage(
        message: String, 
        forcedProvider: String? = null, 
        forcedModel: String? = null,
        onProgress: ((String) -> Unit)? = null,
        threadId: String? = null,
        senderId: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                historyMutex.withLock { messagesHistory.add(ChatMessage("user", message)) }

                // Intercepción para Agentes de Nube Privada (Hermes A2A Individual)
                if (forcedProvider?.startsWith("hermes-a2a") == true) {
                    Log.i(TAG, "[A2A P2P] Agente configurado vía Túnel Hermes P2P. Desviando payload...")
                    // forcedProvider = "hermes-a2a|wss://x.com|token" (si es efímero)
                    val parts = forcedProvider.split("|")
                    
                    val response = if (parts.size >= 3) {
                        val url = parts[1]
                        val token = parts[2]
                        com.beemovil.tunnel.HermesTunnelManager.executeDynamicA2ATask(url, token, "text_generation", message)
                    } else {
                        // Global Tunnel Fallback
                        com.beemovil.tunnel.HermesTunnelManager.executeA2ATask("text_generation", message)
                    }
                    
                    historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", response)) }
                    return@withContext response
                }

                // Build the tool definitions from active plugins
                val activeTools = plugins.values.map { it.getToolDefinition() }

                Log.d(TAG, "[PASO 1] Ejecutando Engine...")
                
                // Sprint 6 polish: Auto-trim context window to prevent OOM on long sessions
                historyMutex.withLock {
                    if (messagesHistory.size > 60) {
                        val systemMsg = messagesHistory.firstOrNull { it.role == "system" }
                        val trimmed = messagesHistory.takeLast(40).toMutableList()
                        if (systemMsg != null && trimmed.firstOrNull()?.role != "system") {
                            trimmed.add(0, systemMsg)
                        }
                        messagesHistory.clear()
                        messagesHistory.addAll(trimmed)
                        Log.i(TAG, "Context window trimmed to ${messagesHistory.size} messages")
                    }
                }
                
                // ═══════════════════════════════════════════════════════════
                // AGENTIC LOOP — Multi-round sequential tool calling
                // The LLM can chain tools across rounds. Round 2 sees
                // results of Round 1 and can call more tools.
                // ═══════════════════════════════════════════════════════════

                val maxRounds = resolveMaxRounds(forcedProvider, forcedModel)
                val startTime = System.currentTimeMillis()
                val TIMEOUT_MS = 180_000L // 3 minutes max for entire workflow
                var round = 0
                var lastFileResult: String? = null
                val executedToolCalls = mutableListOf<String>() // Anti-repetition tracker

                while (round < maxRounds) {
                    round++

                    // Timeout guard
                    if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                        Log.w(TAG, "[AGENTIC] Timeout global alcanzado en ronda $round")
                        break
                    }

                    if (round == 1) {
                        Log.d(TAG, "[AGENTIC] Ronda $round/$maxRounds — Primera consulta al modelo")
                    } else {
                        Log.d(TAG, "[AGENTIC] Ronda $round/$maxRounds — Continuando con resultados previos")
                        onProgress?.invoke("Ronda $round/$maxRounds: Consultando al modelo...")
                    }

                    // Context compression for local models (protect 8K context window)
                    if (round > 1 && isLocalProvider(forcedProvider)) {
                        compressToolResults(messagesHistory, keepLastN = 1)
                    }

                    val historySnapshot = historyMutex.withLock { messagesHistory.toList() }
                    val (response, toolCalls) = executeProvider(historySnapshot, activeTools, forcedProvider, forcedModel)

                    // ── No tool calls → LLM responded with text → EXIT LOOP ──
                    if (toolCalls.isEmpty()) {
                        historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", response)) }
                        // If we collected a file result in a prior round, return it
                        // so the ViewModel can render the preview
                        return@withContext lastFileResult ?: response
                    }

                    // ── Tool calls detected → execute them ──
                    Log.i(TAG, "[AGENTIC] Ronda $round: ${toolCalls.size} herramientas detectadas")
                    onProgress?.invoke("Ronda $round/$maxRounds: Ejecutando ${toolCalls.size} herramientas...")
                    historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", null, toolCalls, "call_${System.currentTimeMillis()}")) }

                    for (call in toolCalls) {
                        // Anti-repetition: detect identical tool calls
                        val callSignature = "${call.name}::${call.params}"
                        if (executedToolCalls.count { it == callSignature } >= 2) {
                            Log.w(TAG, "[AGENTIC] Anti-repetición: ${call.name} ya ejecutado 2x con mismos params")
                            historyMutex.withLock { messagesHistory.add(ChatMessage("tool", "ERROR: Esta herramienta ya fue ejecutada con los mismos parámetros. No se puede repetir.", toolCallId = call.id)) }
                            continue
                        }
                        executedToolCalls.add(callSignature)

                        val plugin = plugins[call.name]
                        if (plugin != null) {
                            try {
                                val argsMap = call.params.let { json ->
                                    val map = mutableMapOf<String, Any>()
                                    json.keys().forEach { k -> map[k] = json.get(k) }
                                    map
                                }
                                val result = plugin.execute(argsMap)
                                historyMutex.withLock { messagesHistory.add(ChatMessage("tool", result, toolCallId = call.id)) }

                                // Track file results but DON'T exit — let the loop continue
                                if (result.startsWith("TOOL_CALL::file_generated::")) {
                                    lastFileResult = result
                                    Log.i(TAG, "[AGENTIC] Archivo generado en ronda $round, loop continúa")
                                }
                            } catch (e: Exception) {
                                historyMutex.withLock { messagesHistory.add(ChatMessage("tool", "Error interno en herramienta: ${e.message}", toolCallId = call.id)) }
                            }
                        } else {
                            if (call.name == "delegate_to_browser") {
                                val url = call.params.optString("url", "https://google.com")
                                return@withContext "TOOL_CALL::open_browser::$url"
                            }
                            historyMutex.withLock { messagesHistory.add(ChatMessage("tool", "Error: Herramienta '${call.name}' no encontrada", toolCallId = call.id)) }
                        }
                    }

                    // ── Checkpoint: save round progress to Room ──
                    if (threadId != null && round > 1) {
                        try {
                            val toolsSummary = executedToolCalls.takeLast(3).joinToString(", ") { it.substringBefore("::") }
                            chatHistoryDB.chatHistoryDao().insertMessage(
                                com.beemovil.database.ChatMessageEntity(
                                    threadId = threadId,
                                    senderId = senderId ?: "emma",
                                    timestamp = System.currentTimeMillis(),
                                    role = "system",
                                    content = "[CHECKPOINT] Ronda $round/$maxRounds completada. Tools: $toolsSummary"
                                )
                            )
                            Log.d(TAG, "[AGENTIC] Checkpoint guardado para ronda $round")
                        } catch (e: Exception) {
                            Log.w(TAG, "[AGENTIC] No se pudo guardar checkpoint: ${e.message}")
                        }
                    }

                    // If this is the last allowed round, don't loop back to LLM
                    if (round >= maxRounds) {
                        Log.w(TAG, "[AGENTIC] Límite de rondas alcanzado ($maxRounds)")
                        if (lastFileResult != null) {
                            historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", "Workflow completado en $round rondas.")) }
                            return@withContext lastFileResult!!
                        }
                        // One final LLM call to summarize
                        onProgress?.invoke("Ensamblando respuesta final...")
                        val finalSnapshot = historyMutex.withLock { messagesHistory.toList() }
                        val (finalResponse, _) = executeProvider(finalSnapshot, activeTools, forcedProvider, forcedModel)
                        historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", finalResponse)) }
                        return@withContext finalResponse
                    }

                    // Loop continues — LLM will see tool results and decide next action
                }

                // Fallback: if we somehow exit the while without returning
                return@withContext lastFileResult 
                    ?: historyMutex.withLock { messagesHistory.lastOrNull { it.role == "assistant" }?.content }
                    ?: "Procesamiento completado."

            } catch (e: CancellationException) {
                // Coroutine was cancelled (user left chat, app went to background)
                // Don't show error — just silently stop
                Log.w(TAG, "Pipeline cancelado (usuario salió o app en background)")
                return@withContext ""
            } catch (e: Exception) {
                Log.e(TAG, "Excepción en el Pipeline", e)
                
                // Check if this is really a cancellation wrapped in an IOException
                val isCancellation = e.cause is CancellationException ||
                    e.message?.contains("Canceled", true) == true ||
                    e.message?.contains("Socket closed", true) == true ||
                    (e.message?.contains("Unable to resolve host", true) == true && 
                     e.cause?.message?.contains("interrupted", true) == true)
                
                if (isCancellation) {
                    Log.w(TAG, "Conexión interrumpida (app en background), no es falta de internet")
                    return@withContext ""
                }
                val isNetworkError = e.message?.contains("Unable to resolve host", true) == true ||
                                     e.message?.contains("timeout", true) == true ||
                                     e.message?.contains("500", false) == true ||
                                     e.message?.contains("502", false) == true ||
                                     e.message?.contains("503", false) == true

                if (isNetworkError && !isLocalProvider(forcedProvider)) {
                    Log.w(TAG, "Activando Fallback Inteligente hacia modelo local (litertlm:gemma)")
                    onProgress?.invoke("Servidor no disponible. Activando Modo Supervivencia Offline...")
                    
                    try {
                        val activeTools = plugins.values.map { it.getToolDefinition() }
                        val historySnapshot = historyMutex.withLock { messagesHistory.toList() }
                        val (fallbackResponse, _) = executeProvider(historySnapshot, activeTools, "litertlm", "gemma")
                        
                        historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", fallbackResponse)) }
                        return@withContext "⚠️ [Modo Offline]\n$fallbackResponse"
                    } catch (fallbackEx: Exception) {
                        Log.e(TAG, "Fallback local también falló", fallbackEx)
                        return@withContext "⚠️ Sin conexión a internet y el modelo local falló."
                    }
                }

                val friendlyError = when {
                    e.message?.contains("Unable to resolve host", true) == true -> 
                        "Sin conexión a internet. Verifica tu red e intenta de nuevo."
                    e.message?.contains("timeout", true) == true -> 
                        "El servidor tardó demasiado en responder. Intenta de nuevo."
                    e.message?.contains("401", false) == true || e.message?.contains("Unauthorized", true) == true -> 
                        "API Key inválida o expirada. Ve a Settings para verificar."
                    e.message?.contains("429", false) == true -> 
                        "Límite de peticiones alcanzado. Espera un momento e intenta de nuevo."
                    e.message?.contains("model", true) == true -> 
                        "Modelo no disponible. Intenta con otro modelo en Settings."
                    else -> "Error procesando tu mensaje: ${e.message?.take(100)}"
                }
                return@withContext "⚠️ $friendlyError"
            }
        }
    }

    suspend fun dispatchSwarmTask(
        threadId: String, 
        userMessage: String,
        onProgress: ((String) -> Unit)? = null
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Obtener miembros del grupo
                val members = chatHistoryDB.chatHistoryDao().getGroupMembers(threadId)
                
                // Si no hay miembros, o es chat individual (fallback normal)
                if (members.isEmpty()) {
                    // Para derivarlo al proveedor específico, podríamos extraerlo del thread o pasar null y que lo agarre el ViewModel
                    // Como el ViewModel ahora llama a dispatchSwarmTask en vez de processUserMessage directo
                    // Necesitaríamos interceptar forcedProvider en dispatchSwarmTask, pero dispatchSwarmTask no lo recibe.
                    // Oh, wait, we can just let dispatchSwarmTask take forcedProvider!
                    return@withContext processUserMessage(userMessage, onProgress = onProgress)
                }

                val activeTools = plugins.values.map { it.getToolDefinition() }
                var currentContext = userMessage
                var finalResponse = ""

                Log.d(TAG, "[SWARM] Iniciando automatización para Thread: $threadId con ${members.size} agentes")

                // BUG-03 fix: obtener todos los agentes UNA vez fuera del loop
                val allAgents = chatHistoryDB.chatHistoryDao().getAllAgents()

                for ((index, member) in members.sortedBy { it.executionOrder }.withIndex()) {
                    val agentConfig = allAgents.find { it.agentId == member.agentId } ?: continue

                    // Parse Fallback Model (format -> "provider:modelId")
                    val modelParts = agentConfig.fallbackModel.split(":", limit = 2)
                    val providerId = if(modelParts.isNotEmpty()) modelParts[0] else null
                    val modelId = if(modelParts.size > 1) modelParts[1] else null

                    Log.i(TAG, "[SWARM] Handoff al Agente: ${agentConfig.name} [$providerId:$modelId]")
                    onProgress?.invoke("Swarm: ${agentConfig.name} procesando túnel...")

                    // Armar Compressive Handoff
                    val handoffHistory = mutableListOf<ChatMessage>()
                    handoffHistory.add(ChatMessage("system", agentConfig.systemPrompt))
                    
                    if (index == 0) {
                        // Primer eslabón: recibe orden limpia
                        handoffHistory.add(ChatMessage("user", currentContext))
                    } else {
                        // Eslabones posteriores: reciben orden original + Data producida por el agente previo
                        handoffHistory.add(ChatMessage("user", "TAREA ORIGINAL: $userMessage\n\nRESULTADO PREVIO PARA CONTINUAR: $currentContext"))
                    }

                    // Ejecutar dependiendo del motor (Local/Cloud vs Hermes Remoto)
                    val response = if (providerId == "hermes-a2a") {
                        Log.i(TAG, "[SWARM/A2A] Empaquetando petición HTTP/WS a lo largo del Túnel...")
                        
                        val promptCompiler = JSONArray().apply {
                            handoffHistory.forEach { msg ->
                                put(JSONObject().apply {
                                    put("role", msg.role)
                                    put("content", msg.content)
                                })
                            }
                        }
                        
                        val manager = com.beemovil.tunnel.HermesTunnelManager
                        manager.executeA2ATask("text_generation", promptCompiler.toString())
                    } else {
                        val (localResponse, _) = executeProvider(handoffHistory, activeTools, forcedProvider = providerId, forcedModel = modelId)
                        localResponse
                    }

                    // Guardar en Room la intervención de este hilo
                    chatHistoryDB.chatHistoryDao().insertMessage(com.beemovil.database.ChatMessageEntity(
                        threadId = threadId,
                        senderId = agentConfig.agentId,
                        timestamp = System.currentTimeMillis() + index,
                        role = "assistant",
                        content = response
                    ))

                    // Compresión de estado para el siguiente Agente (Handoff)
                    currentContext = response
                    finalResponse = response
                }

                Log.d(TAG, "[SWARM] Orquestación completada. Output final listo.")
                return@withContext finalResponse

            } catch (e: Exception) {
                Log.e(TAG, "Error en Swarm Hub A2A Protocol", e)
                return@withContext "Error en Orquestación: ${e.message}"
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // AGENTIC LOOP HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Adaptive MAX_ROUNDS based on provider capabilities.
     * Cloud models get 5 rounds, free models 3, local models 2.
     */
    private fun resolveMaxRounds(provider: String?, model: String?): Int {
        return when {
            provider == "local" || provider?.startsWith("gemma") == true -> 2
            provider?.startsWith("hermes") == true -> 1
            model?.contains(":free") == true -> 3
            // OpenRouter premium + Ollama Cloud get full 5 rounds
            else -> 5
        }
    }

    /** Check if the provider runs on-device (RAM constrained) */
    private fun isLocalProvider(provider: String?): Boolean {
        return provider == "local" || provider?.startsWith("gemma") == true
    }

    /**
     * Compress tool results from earlier rounds to protect context window.
     * Keeps the last N tool results intact, compresses the rest.
     * Replaces ChatMessage entries in-place since content is val.
     */
    private fun compressToolResults(history: MutableList<ChatMessage>, keepLastN: Int) {
        val toolIndices = history.indices.filter { history[it].role == "tool" }
        if (toolIndices.size <= keepLastN) return
        
        val toCompressIndices = toolIndices.dropLast(keepLastN)
        toCompressIndices.forEach { idx ->
            val msg = history[idx]
            val content = msg.content ?: return@forEach
            val compressed = when {
                content.startsWith("TOOL_CALL::file_generated::") -> {
                    val name = java.io.File(content.removePrefix("TOOL_CALL::file_generated::")).name
                    "Archivo generado: $name ✅"
                }
                content.length > 300 -> content.take(200) + "... [comprimido]"
                else -> return@forEach // Already short, skip
            }
            history[idx] = msg.copy(content = compressed)
        }
        Log.d(TAG, "[AGENTIC] Historial comprimido: ${toCompressIndices.size} resultados de tools reducidos")
    }

    /**
     * @Deprecated Usar dispatchSwarmTask() para enrutamiento de sub-agentes.
     * Este método queda como referencia de la arquitectura mono-agente anterior.
     */
    @Deprecated("Use dispatchSwarmTask instead", ReplaceWith("dispatchSwarmTask(threadId, userMessage)"))
    private fun executeSpecialist(agentId: String, userMessage: String, priorToolCall: com.beemovil.llm.ToolCall): String {
        val specialistHistory = mutableListOf<ChatMessage>()
        val tools = mutableListOf<ToolDefinition>()

        if (agentId == "browser") {
            specialistHistory.add(ChatMessage("system", BROWSER_AGENT_PROMPT))
            tools.add(ToolDefinition(
                name = "open_browser",
                description = "Abre un navegador en la pantalla con una URL específica.",
                parameters = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("url", JSONObject().apply {
                            put("type", "string")
                            put("description", "URL a abrir.")
                        })
                    })
                    put("required", JSONArray().put("url"))
                }
            ))
        } else {
            specialistHistory.add(ChatMessage("system", WRITER_AGENT_PROMPT))
        }

        specialistHistory.add(ChatMessage("user", userMessage))
        
        Log.d(TAG, "Ejecutando Inferencia de Sub-Agente ($agentId)...")
        val (agentResponse, toolCalls) = executeProvider(specialistHistory, tools)
        
        val openBrowserCall = toolCalls.firstOrNull { it.name == "open_browser" }
        if (openBrowserCall != null) {
            val argsStr = openBrowserCall.params.toString()
            val argsJson = try { JSONObject(argsStr) } catch(e:Exception) { JSONObject() }
            val url = argsJson.optString("url", "https://google.com")
            
            // Responder a E.M.M.A supervisor de que se cumplió
            messagesHistory.add(ChatMessage("tool", "Agente Browser ejecutado en $url", toolCallId = priorToolCall.id))
            return "TOOL_CALL::open_browser::$url"
        }
        
        // Guardar la redacción larga en la historia del usuario
        messagesHistory.add(ChatMessage("tool", "Agente devolvió el resultado escrito.", toolCallId = priorToolCall.id))
        messagesHistory.add(ChatMessage("assistant", agentResponse))
        
        return agentResponse
    }

    private fun executeProvider(
        messages: List<ChatMessage>, 
        tools: List<ToolDefinition>,
        forcedProvider: String? = null,
        forcedModel: String? = null
    ): Pair<String, List<com.beemovil.llm.ToolCall>> {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val securePrefs = com.beemovil.security.SecurePrefs.get(context)
        
        val preset = forcedProvider ?: prefs.getString("selected_provider", "openrouter") ?: "openrouter"
        val model = forcedModel ?: prefs.getString("selected_model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini"
        
        val keyName = when(preset) {
            "openrouter" -> "openrouter_api_key"
            "ollama" -> "ollama_api_key"
            else -> "openrouter_api_key"
        }
        val key = securePrefs.getString(keyName, "") ?: ""

        // Smart fallback: if selected provider has no key, try others that do
        val effectivePreset: String
        val effectiveKey: String
        val effectiveModel: String
        
        if (key.isBlank() && forcedProvider == null) {
            Log.w(TAG, "Provider '$preset' has no API key ($keyName is empty). Trying fallback providers...")
            
            // Try providers in priority order: openrouter → ollama
            val fallbackOrder = listOf(
                Triple("openrouter", "openrouter_api_key", "openai/gpt-4o-mini"),
                Triple("ollama", "ollama_api_key", "llama3.3")
            )
            
            val fallback = fallbackOrder.firstOrNull { (_, fKeyName, _) ->
                val fKey = securePrefs.getString(fKeyName, "") ?: ""
                fKey.isNotBlank()
            }
            
            if (fallback != null) {
                effectivePreset = fallback.first
                effectiveKey = securePrefs.getString(fallback.second, "") ?: ""
                effectiveModel = fallback.third
                Log.i(TAG, "✅ Auto-fallback to '$effectivePreset' (has key, ${effectiveKey.length} chars)")
            } else {
                effectivePreset = preset
                effectiveKey = key
                effectiveModel = model
            }
        } else {
            effectivePreset = preset
            effectiveKey = key
            effectiveModel = if (forcedModel != null) model else model
        }

        return try {
            val provider = LlmFactory.createProvider(effectivePreset, effectiveKey, effectiveModel)
            val result = provider.complete(messages, tools)
            Pair(result.text ?: "", result.toolCalls)
        } catch (e: Exception) {
            Log.e(TAG, "executeProvider falló: preset=$effectivePreset model=$effectiveModel keyLen=${effectiveKey.length}", e)

            // Re-throw cancellation so the outer catch in processUserMessage handles it
            if (e is CancellationException) throw e
            if (e.cause is CancellationException || e.message?.contains("Canceled", true) == true || e.message?.contains("Socket closed", true) == true) {
                throw CancellationException("Connection interrupted")
            }
            
            val providerLabel = when(effectivePreset) {
                "openrouter" -> "OpenRouter"
                "ollama" -> "Ollama Cloud"
                else -> effectivePreset
            }
            
            val friendlyMsg = when {
                effectiveKey.isBlank() -> "⚠️ No hay API key para $providerLabel. Ve a Settings → Proveedor AI y configura tu key."
                e.message?.contains("timeout", true) == true -> "⚠️ $providerLabel tardó demasiado. Intenta de nuevo."
                e.message?.contains("401", false) == true -> "⚠️ API key de $providerLabel inválida o expirada. Revísala en Settings."
                e.message?.contains("429", false) == true -> "⚠️ $providerLabel: límite de peticiones alcanzado. Espera un momento."
                e.message?.contains("Unable to resolve host", true) == true -> "⚠️ Sin conexión a internet."
                else -> "⚠️ Error con $providerLabel: ${e.message?.take(80)}"
            }
            Pair(friendlyMsg, emptyList())
        }
    }
}
