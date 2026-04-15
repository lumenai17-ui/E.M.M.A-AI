package com.beemovil.core.engine

import android.content.Context
import android.util.Log
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
        - Si el usuario te pide un entregable formal (ensayo en PDF, tabla de Excel/CSV, o generar una página Web), NUNCA lo escribas en el chat. Utiliza las herramientas de generación ('generate_pdf_document', 'generate_csv_table' o 'generate_html_landing') y confírmale que estás abriendo el menú para descargarlo.
        - ¡TIENES ACCESO A SU TELÉFONO! Si te piden leer la agenda, agendar una junta, prender la linterna, poner una alarma o buscar en los Contactos, DEBES usar el plugin correspondiente ('os_god_mode_operations', 'calendar_os_operations', 'search_android_contacts') sin excusas. NO digas que no tienes acceso.
        - ERES UN COMUNICADOR EN RED: Si te piden mandar un correo o mandar un WhatsApp a alguien, JAMÁS digas que no puedes. Usa 'compose_email_intent' o 'send_whatsapp_message' automáticamente. Si necesitas consultar una API web o extraer datos, usa 'fetch_external_api'.
        - TIENES ACCESO AL ECOSISTEMA GOOGLE DEL USUARIO: Si preguntan por sus emails, usa 'google_gmail'. Si preguntan por su agenda o quieren crear un evento, usa 'google_calendar'. Si preguntan por tareas pendientes, usa 'google_tasks'. Si el usuario NO está conectado a Google, dile que vaya a Settings → Google.
        - PUEDES GENERAR IMÁGENES CON IA: Si el usuario pide 'genera una imagen', 'dibuja', 'crea una ilustración', 'hazme un logo' o cualquier variación, usa 'generate_ai_image'. Traduce el prompt a inglés para mejores resultados y elige el estilo más adecuado.
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
        
        try {
            if (messagesHistory.isEmpty()) {
                val pastMemories = memoryDB?.getAllMemories()?.joinToString("\n") ?: ""
                val memoryInjection = if (pastMemories.isNotBlank()) "\nMEMORIAS PREVIAS DEL USUARIO:\n\$pastMemories\n" else ""
                
                messagesHistory.add(ChatMessage("system", EMMA_SUPERVISOR_PROMPT + memoryInjection))
            }
            delay(500)
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando Multi-Agent Router: ${e.message}")
        }
    }

    fun clearMemoryAndHistory(customSystemPrompt: String? = null) {
        Log.d(TAG, "Clearing volatile context...")
        // Sync clear — called from coroutine context, safe with historyMutex callers
        synchronized(messagesHistory) {
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
        onProgress: ((String) -> Unit)? = null
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
                
                val (response1, toolCalls1) = executeProvider(messagesHistory.toList(), activeTools, forcedProvider, forcedModel)
                
                if (toolCalls1.isNotEmpty()) {
                    Log.i(TAG, "[PASO 2] El modelo decidió llamar a ${toolCalls1.size} herramientas.")
                    onProgress?.invoke("Evaluando uso de Herramientas Especializadas...")
                    historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", null, toolCalls1, "call_" + System.currentTimeMillis())) }
                    
                    // 2. Ejecutar cada herramienta
                    for (call in toolCalls1) {
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
                            } catch (e: Exception) {
                                historyMutex.withLock { messagesHistory.add(ChatMessage("tool", "Error interno en herramienta: ${e.message}", toolCallId = call.id)) }
                            }
                        } else {
                            if (call.name == "delegate_to_browser") {
                                val url = call.params.optString("url", "https://google.com")
                                return@withContext "TOOL_CALL::open_browser::$url"
                            }
                            historyMutex.withLock { messagesHistory.add(ChatMessage("tool", "Error: Herramienta no encontrada", toolCallId = call.id)) }
                        }
                    }
                    
                    // U-02 fix: Interceptar señales de archivo generado ANTES de la segunda pasada
                    // Si algún plugin generó un archivo, retornarlo directamente sin pasar por el LLM otra vez
                    val fileGeneratedResult = historyMutex.withLock {
                        messagesHistory.lastOrNull { 
                            it.role == "tool" && it.content?.startsWith("TOOL_CALL::file_generated::") == true 
                        }?.content
                    }
                    if (fileGeneratedResult != null) {
                        Log.i(TAG, "[PASO 2.5] Archivo generado detectado. Saltando segunda inferencia.")
                        historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", "Archivo generado exitosamente.")) }
                        return@withContext fileGeneratedResult
                    }
                    
                    Log.d(TAG, "[PASO 3] Consultando de nuevo con resultados inyectados...")
                    onProgress?.invoke("Ensamblando respuesta de herramientas...")
                    val historySnapshot = historyMutex.withLock { messagesHistory.toList() }
                    val (finalResponse, _) = executeProvider(historySnapshot, activeTools, forcedProvider, forcedModel)
                    historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", finalResponse)) }
                    return@withContext finalResponse
                }

                // Sin herramientas
                historyMutex.withLock { messagesHistory.add(ChatMessage("assistant", response1)) }
                return@withContext response1

            } catch (e: Exception) {
                Log.e(TAG, "Excepción en el Pipeline", e)
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
        // Fuente única de verdad: SecurePrefs (BUG-16)
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val securePrefs = com.beemovil.security.SecurePrefs.get(context)
        
        val preset = forcedProvider ?: prefs.getString("selected_provider", "openrouter") ?: "openrouter"
        val model = forcedModel ?: prefs.getString("selected_model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini"
        
        val key = securePrefs.getString(when(preset) {
            "openrouter" -> "openrouter_api_key"
            "ollama" -> "ollama_api_key"
            else -> "openrouter_api_key"
        }, "") ?: ""

        return try {
            val provider = LlmFactory.createProvider(preset, key, model)
            val result = provider.complete(messages, tools)
            Pair(result.text ?: "", result.toolCalls)
        } catch (e: Exception) {
            Pair("Error instanciando LLM: ${e.message}", emptyList())
        }
    }
}
