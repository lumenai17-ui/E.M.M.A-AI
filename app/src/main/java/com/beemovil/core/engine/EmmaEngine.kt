package com.beemovil.core.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val chatHistoryDB = com.beemovil.database.ChatHistoryDB.getDatabase(context)
    
    private val messagesHistory = mutableListOf<ChatMessage>()

    private val EMMA_SUPERVISOR_PROMPT = """
        Eres E.M.M.A., la Coordinadora Central. Evalúa el pedido del usuario.
        - Si el usuario pide explícitamente REDACTAR un ensayo, correo, o texto muy largo, USA LA TOOL 'delegate_to_writer'.
        - Si el usuario pide BUSCAR en internet, ir a una URL, o necesita navegación web interactiva, USA LA TOOL 'delegate_to_browser'.
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

    fun clearMemoryAndHistory() {
        Log.d(TAG, "Clearing volatile context...")
        messagesHistory.clear()
        val pastMemories = memoryDB?.getAllMemories()?.joinToString("\n") ?: ""
        val memoryInjection = if (pastMemories.isNotBlank()) "\nMEMORIAS PREVIAS DEL USUARIO:\n\$pastMemories\n" else ""
        messagesHistory.add(ChatMessage("system", EMMA_SUPERVISOR_PROMPT + memoryInjection))
    }

    suspend fun processUserMessage(message: String): String {
        return withContext(Dispatchers.IO) {
            try {
                messagesHistory.add(ChatMessage("user", message))

                // Build the tool definitions from active plugins
                val activeTools = plugins.values.map { it.getToolDefinition() }

                Log.d(TAG, "[PASO 1] Ejecutando Engine...")
                val (response1, toolCalls1) = executeProvider(messagesHistory.toList(), activeTools)
                
                if (toolCalls1.isNotEmpty()) {
                    Log.i(TAG, "[PASO 2] El modelo decidió llamar a ${toolCalls1.size} herramientas.")
                    // 1. Agregar la intención del assistant a la historia
                    messagesHistory.add(ChatMessage("assistant", null, toolCalls1, "call_" + System.currentTimeMillis()))
                    
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
                                messagesHistory.add(ChatMessage("tool", result, toolCallId = call.id))
                            } catch (e: Exception) {
                                messagesHistory.add(ChatMessage("tool", "Error interno en herramienta: ${e.message}", toolCallId = call.id))
                            }
                        } else {
                            // Si la herramienta no existe (e.g. alucinación temporal o legacy call)
                            if (call.name == "delegate_to_browser") {
                                val url = call.params.optString("url", "https://google.com")
                                return@withContext "TOOL_CALL::open_browser::$url"
                            }
                            messagesHistory.add(ChatMessage("tool", "Error: Herramienta no encontrada", toolCallId = call.id))
                        }
                    }
                    
                    // 3. Segunda Llamada (Follow-up) -> El Paso 2
                    Log.d(TAG, "[PASO 3] Consultando de nuevo con resultados inyectados...")
                    val (finalResponse, _) = executeProvider(messagesHistory.toList(), activeTools)
                    messagesHistory.add(ChatMessage("assistant", finalResponse))
                    return@withContext finalResponse
                }

                // Sin herramientas
                messagesHistory.add(ChatMessage("assistant", response1))
                return@withContext response1

            } catch (e: Exception) {
                Log.e(TAG, "Excepción en el Pipeline", e)
                return@withContext "Error de red neuronal local: ${e.message}"
            }
        }
    }

    suspend fun dispatchSwarmTask(threadId: String, userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Obtener miembros del grupo
                val members = chatHistoryDB.chatHistoryDao().getGroupMembers(threadId)
                
                // Si no hay miembros, o es chat individual (fallback normal)
                if (members.isEmpty()) {
                    return@withContext processUserMessage(userMessage)
                }

                val activeTools = plugins.values.map { it.getToolDefinition() }
                var currentContext = userMessage
                var finalResponse = ""

                Log.d(TAG, "[SWARM] Iniciando automatización para Thread: $threadId con ${members.size} agentes")

                for ((index, member) in members.sortedBy { it.executionOrder }.withIndex()) {
                    // Fetch Agent Config
                    val agents = chatHistoryDB.chatHistoryDao().getAllAgents()
                    val agentConfig = agents.find { it.agentId == member.agentId } ?: continue

                    // Parse Fallback Model (format -> "provider:modelId")
                    val modelParts = agentConfig.fallbackModel.split(":", limit = 2)
                    val providerId = if(modelParts.isNotEmpty()) modelParts[0] else null
                    val modelId = if(modelParts.size > 1) modelParts[1] else null

                    Log.i(TAG, "[SWARM] Handoff al Agente: ${agentConfig.name} [$providerId:$modelId]")

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

                    // Ejecutar pasando el provider específico de este Agente
                    val (response, _) = executeProvider(handoffHistory, activeTools, forcedProvider = providerId, forcedModel = modelId)

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
        
        val key = when(preset) {
            "openrouter" -> securePrefs.getString("openrouter_api_key", "") ?: ""
            "ollama" -> securePrefs.getString("ollama_api_key", "") ?: ""
            else -> ""
        }

        var provider: com.beemovil.llm.LlmProvider? = null

        try {
            provider = LlmFactory.createProvider(preset, key, model)
            // LlmFactory abstrae la configuración final.
            // pero LlmFactory abstrae esto. Para Custom podemos inyectarlo o solo usar OpenAI compatible.
        } catch (e: Exception) {
            return Pair("Error instanciando LLM: ${e.message}", emptyList())
        }

        val result = provider.complete(messages, tools)
        return Pair(result.text ?: "", result.toolCalls)
    }
}
