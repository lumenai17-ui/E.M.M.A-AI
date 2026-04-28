package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.plugins.SecurityGate
import com.beemovil.security.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.beemovil.telegram.TelegramBotService

/**
 * SelfConfigPlugin — Project Autonomía Phase S2
 *
 * E.M.M.A. can read AND modify her own configuration.
 * Read ops = 🟢 GREEN, Change settings = 🟡 YELLOW, API keys = 🔴 RED.
 */
class SelfConfigPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "emma_self_config"
    private val TAG = "SelfConfigPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = """Tu herramienta de introspección y auto-configuración. Lee y modifica tu configuración.
                LEER: '¿qué modelo usas?', '¿qué keys tengo?', '¿cómo estoy configurada?'
                ESCRIBIR: 'guarda este token', 'cambia al modelo X', 'activa/desactiva TTS'
                NUNCA inventes tu configuración — SIEMPRE usa esta herramienta.""".trimIndent(),
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray()
                            .put("get_current_model")
                            .put("get_api_keys_status")
                            .put("get_all_settings")
                            .put("get_agents_list")
                            .put("get_vision_config")
                            .put("get_voice_config")
                            .put("get_telegram_allowlist")
                            .put("update_api_key")
                            .put("change_model")
                            .put("toggle_feature")
                            .put("add_telegram_access")
                            .put("remove_telegram_access")
                        )
                        put("description", """Operaciones disponibles:
                            LECTURA (🟢):
                            - get_current_model, get_api_keys_status, get_all_settings
                            - get_agents_list, get_vision_config, get_voice_config
                            - get_telegram_allowlist: Ver quién tiene acceso al bot de Telegram
                            ESCRITURA:
                            - update_api_key: Guardar/actualizar un API key (🔴 requiere confirmación)
                            - change_model: Cambiar provider y modelo LLM activo (🟡)
                            - toggle_feature: Activar/desactivar una feature (🟡)
                            - add_telegram_access: Añadir usuario o grupo al bot de Telegram (🟡)
                            - remove_telegram_access: Quitar acceso de usuario o grupo (🔴)""".trimIndent())
                    })
                    put("provider_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para update_api_key) Nombre del proveedor: 'openrouter', 'deepgram', 'elevenlabs', 'ollama', 'telegram'")
                    })
                    put("api_key_value", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para update_api_key) El valor del API key a guardar.")
                    })
                    put("new_provider", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para change_model) Provider: 'openrouter' o 'ollama'.")
                    })
                    put("new_model", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para change_model) ID del modelo (ej: 'openai/gpt-4o-mini', 'qwen3:cloud').")
                    })
                    put("feature_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para toggle_feature) Feature a cambiar: 'deepgram_stt', 'deepgram_tts', 'theme'.")
                    })
                    put("feature_value", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para toggle_feature) Nuevo valor. Para booleanos: 'true'/'false'. Para theme: 'light'/'dark'/'system'.")
                    })
                    put("telegram_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para add/remove_telegram_access) El ID numérico del chat o usuario de Telegram. Grupos tienen IDs negativos (ej: -100123456). Usuarios tienen IDs positivos.")
                    })
                    put("telegram_id_type", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("chat").put("user"))
                        put("description", "(Para add/remove_telegram_access) Tipo de ID: 'chat' para grupos/canales, 'user' para usuarios individuales.")
                    })
                })
                put("required", JSONArray().put("operation"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val operation = args["operation"] as? String ?: return "Falta 'operation'."

        return withContext(Dispatchers.IO) {
            try {
                when (operation) {
                    // Read ops (Phase S1)
                    "get_current_model" -> getCurrentModel()
                    "get_api_keys_status" -> getApiKeysStatus()
                    "get_all_settings" -> getAllSettings()
                    "get_agents_list" -> getAgentsList()
                    "get_vision_config" -> getVisionConfig()
                    "get_voice_config" -> getVoiceConfig()
                    // Write ops (Phase S2)
                    "update_api_key" -> updateApiKey(args)
                    "change_model" -> changeModel(args)
                    "toggle_feature" -> toggleFeature(args)
                    // Telegram allowlist ops
                    "get_telegram_allowlist" -> getTelegramAllowlist()
                    "add_telegram_access" -> addTelegramAccess(args)
                    "remove_telegram_access" -> removeTelegramAccess(args)
                    else -> "Operación desconocida: $operation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "SelfConfig error", e)
                "Error leyendo configuración: ${e.message}"
            }
        }
    }

    private fun getCurrentModel(): String = buildString {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val provider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
        val model = prefs.getString("selected_model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini"

        appendLine("MODELO LLM ACTIVO:")
        appendLine("  Provider: $provider")
        appendLine("  Modelo: $model")

        // Try to find friendly name from ModelRegistry
        try {
            val modelEntry = com.beemovil.llm.ModelRegistry.findModel(model)
            if (modelEntry != null) {
                appendLine("  Nombre: ${modelEntry.name}")
                appendLine("  Tamaño: ${modelEntry.sizeLabel}")
                appendLine("  Soporta Vision: ${modelEntry.hasVision}")
                appendLine("  Soporta Tools: ${modelEntry.hasTools}")
            }
        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
    }

    private fun getApiKeysStatus(): String = buildString {
        val securePrefs = SecurePrefs.get(context)

        appendLine("ESTADO DE API KEYS (por seguridad no se muestran los valores):")
        appendLine()

        val keys = listOf(
            Triple("OpenRouter", "openrouter_api_key", "LLM principal"),
            Triple("Ollama Cloud", "ollama_api_key", "LLM alternativo/local"),
            Triple("Deepgram", "deepgram_api_key", "Voz (STT + TTS)"),
            Triple("ElevenLabs", "elevenlabs_api_key", "Voz premium (TTS)"),
            Triple("Google OAuth", "google_access_token", "Gmail, Calendar, Tasks"),
            Triple("Telegram Bot", "telegram_bot_token", "Bot de Telegram"),
            Triple("Hermes Tunnel", "hermes_tunnel_token", "Agentes remotos A2A")
        )

        keys.forEach { (name, key, purpose) ->
            val value = securePrefs.getString(key, "") ?: ""
            val status = if (value.isNotBlank()) {
                "✅ Configurada (${value.length} chars)"
            } else {
                "❌ No configurada"
            }
            appendLine("  $name: $status")
            appendLine("    Propósito: $purpose")
        }
    }

    private fun getAllSettings(): String = buildString {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)

        appendLine("═══ CONFIGURACIÓN COMPLETA ═══")
        appendLine()

        appendLine("🤖 LLM:")
        appendLine("  Provider: ${prefs.getString("selected_provider", "openrouter")}")
        appendLine("  Modelo: ${prefs.getString("selected_model", "openai/gpt-4o-mini")}")

        appendLine()
        appendLine("🎤 VOZ:")
        appendLine("  Deepgram STT: ${prefs.getBoolean("use_deepgram_stt", true)}")
        appendLine("  Deepgram TTS: ${prefs.getBoolean("use_deepgram_tts", true)}")
        appendLine("  Voz Deepgram: ${prefs.getString("deepgram_voice", "aura-asteria-en")}")

        appendLine()
        appendLine("🎨 INTERFAZ:")
        appendLine("  Tema: ${prefs.getString("app_theme", "system")}")
        appendLine("  Onboarding: ${if (prefs.getBoolean("onboarding_completed", false)) "Completado" else "Pendiente"}")

        appendLine()
        appendLine("🌐 TÚNEL HERMES:")
        appendLine("  URL: ${prefs.getString("hermes_tunnel_url", "No configurado")}")
        appendLine("  Activo: ${prefs.getBoolean("hermes_tunnel_active", false)}")

        appendLine()
        appendLine("📊 ESTADÍSTICAS:")
        appendLine("  Primera ejecución: ${prefs.getLong("first_run_timestamp", 0).let { 
            if (it > 0) java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) else "N/A" 
        }}")
    }

    private fun getAgentsList(): String = buildString {
        try {
            val db = com.beemovil.database.ChatHistoryDB.getDatabase(context)
            val agents = db.chatHistoryDao().getAllAgentsSync()

            if (agents.isEmpty()) {
                appendLine("No hay agentes personalizados creados.")
                appendLine("Solo está el agente por defecto (E.M.M.A. principal).")
                return@buildString
            }

            appendLine("═══ AGENTES CONFIGURADOS (${agents.size}) ═══")
            appendLine()

            agents.forEach { agent ->
                appendLine("${agent.icon} ${agent.name}")
                appendLine("  ID: ${agent.agentId}")
                appendLine("  Modelo: ${agent.fallbackModel.ifBlank { "Default" }}")
                appendLine("  Prompt: ${agent.systemPrompt.take(100)}...")
                appendLine()
            }
        } catch (e: Exception) {
            appendLine("Error accediendo a la base de datos de agentes: ${e.message}")
        }
    }

    private fun getVisionConfig(): String = buildString {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)

        appendLine("═══ CONFIGURACIÓN VISION ═══")
        appendLine()
        appendLine("👁️ Último modo: ${prefs.getString("vision_mode", "GENERAL")}")
        appendLine("⏱️ Intervalo de captura: ${prefs.getInt("vision_interval", 8)}s")
        appendLine("🎭 Narrador: ${prefs.getString("vision_personality", "default")}")
        appendLine("🎯 Modelo Vision: ${prefs.getString("selected_model", "openai/gpt-4o-mini")}")
        appendLine()
        appendLine("Modos disponibles: GENERAL, DASHCAM, TOURIST, AGENT, MEETING, SHOPPING, POCKET, TRANSLATOR")
    }

    private fun getVoiceConfig(): String = buildString {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val securePrefs = SecurePrefs.get(context)

        appendLine("═══ CONFIGURACIÓN DE VOZ ═══")
        appendLine()

        // STT
        val dgKey = (securePrefs.getString("deepgram_api_key", "") ?: "").isNotBlank()
        val useDgSTT = prefs.getBoolean("use_deepgram_stt", true)
        appendLine("🎤 STT (Speech-to-Text):")
        appendLine("  Motor activo: ${when {
            dgKey && useDgSTT -> "Deepgram (cloud, alta precisión)"
            else -> "Android Nativo (offline)"
        }}")
        appendLine("  Deepgram disponible: ${if (dgKey) "✅" else "❌ (sin API key)"}")

        appendLine()

        // TTS
        val elKey = (securePrefs.getString("elevenlabs_api_key", "") ?: "").isNotBlank()
        val elVoice = securePrefs.getString("elevenlabs_voice_id", "") ?: ""
        val useDgTTS = prefs.getBoolean("use_deepgram_tts", true)
        val dgVoice = prefs.getString("deepgram_voice", "aura-asteria-en") ?: "aura-asteria-en"

        appendLine("🔊 TTS (Text-to-Speech):")
        appendLine("  Motor activo: ${when {
            elKey && elVoice.isNotBlank() -> "ElevenLabs (premium, voz: ${elVoice.take(8)}...)"
            dgKey && useDgTTS -> "Deepgram (voz: $dgVoice)"
            else -> "Android Nativo"
        }}")
        appendLine("  Prioridad: ElevenLabs → Deepgram → Android Nativo")
        appendLine("  ElevenLabs disponible: ${if (elKey) "✅" else "❌"}")
        appendLine("  Deepgram TTS disponible: ${if (dgKey) "✅" else "❌"}")
        appendLine("  Android Nativo: ✅ (siempre disponible)")
    }

    // ═══════════════════════════════════════════════════════════
    // WRITE OPERATIONS (Phase S2)
    // ═══════════════════════════════════════════════════════════

    private suspend fun updateApiKey(args: Map<String, Any>): String {
        val providerName = args["provider_name"] as? String ?: return "Falta 'provider_name'."
        val keyValue = args["api_key_value"] as? String ?: return "Falta 'api_key_value'."

        val keyMapping = mapOf(
            "openrouter" to "openrouter_api_key",
            "deepgram" to "deepgram_api_key",
            "elevenlabs" to "elevenlabs_api_key",
            "ollama" to "ollama_api_key",
            "telegram" to "telegram_bot_token"
        )

        val prefKey = keyMapping[providerName.lowercase()]
            ?: return "Proveedor no reconocido: $providerName. Válidos: ${keyMapping.keys.joinToString()}"

        // SecurityGate: RED — API keys are critical
        val op = SecurityGate.red(
            id, "update_api_key",
            "Guardar API key de ${providerName.uppercase()} (${keyValue.take(6)}...${keyValue.takeLast(4)})"
        )
        if (!SecurityGate.evaluate(op)) {
            return "Actualización de API key cancelada."
        }

        val securePrefs = SecurePrefs.get(context)
        securePrefs.edit().putString(prefKey, keyValue).apply()
        Log.i(TAG, "API key updated: $providerName (${keyValue.length} chars)")

        return "API key de ${providerName.uppercase()} actualizada ✅ (${keyValue.length} caracteres guardados en almacenamiento cifrado)"
    }

    private suspend fun changeModel(args: Map<String, Any>): String {
        val newProvider = args["new_provider"] as? String
        val newModel = args["new_model"] as? String ?: return "Falta 'new_model'."

        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val oldProvider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
        val oldModel = prefs.getString("selected_model", "openai/gpt-4o-mini") ?: "?"

        val targetProvider = newProvider ?: oldProvider

        // Validate model exists in registry
        val modelEntry = com.beemovil.llm.ModelRegistry.findModel(newModel)
        val modelName = modelEntry?.name ?: newModel

        // SecurityGate: YELLOW
        val op = SecurityGate.yellow(
            id, "change_model",
            "Cambiar modelo: $oldModel → $modelName ($targetProvider)"
        )
        if (!SecurityGate.evaluate(op)) {
            return "Cambio de modelo cancelado."
        }

        prefs.edit()
            .putString("selected_provider", targetProvider)
            .putString("selected_model", newModel)
            .apply()
        Log.i(TAG, "Model changed: $oldModel → $newModel ($targetProvider)")

        return buildString {
            appendLine("Modelo cambiado ✅")
            appendLine("  Antes: $oldModel ($oldProvider)")
            appendLine("  Ahora: $modelName ($targetProvider)")
            if (modelEntry != null) {
                appendLine("  Vision: ${if (modelEntry.hasVision) "✅" else "❌"}")
                appendLine("  Tools: ${if (modelEntry.hasTools) "✅" else "❌"}")
            }
            appendLine("⚠️ El cambio aplica en la PRÓXIMA conversación o al reiniciar el chat.")
        }
    }

    private suspend fun toggleFeature(args: Map<String, Any>): String {
        val featureName = args["feature_name"] as? String ?: return "Falta 'feature_name'."
        val featureValue = args["feature_value"] as? String ?: return "Falta 'feature_value'."

        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)

        // SecurityGate: YELLOW
        val op = SecurityGate.yellow(id, "toggle_feature", "Cambiar $featureName → $featureValue")
        if (!SecurityGate.evaluate(op)) {
            return "Cambio cancelado."
        }

        return when (featureName.lowercase()) {
            "deepgram_stt" -> {
                prefs.edit().putBoolean("use_deepgram_stt", featureValue.toBoolean()).apply()
                "Deepgram STT ${if (featureValue.toBoolean()) "activado ✅" else "desactivado ❌"}"
            }
            "deepgram_tts" -> {
                prefs.edit().putBoolean("use_deepgram_tts", featureValue.toBoolean()).apply()
                "Deepgram TTS ${if (featureValue.toBoolean()) "activado ✅" else "desactivado ❌"}"
            }
            "theme" -> {
                val valid = listOf("light", "dark", "system")
                if (featureValue.lowercase() !in valid) {
                    return "Tema inválido. Opciones: ${valid.joinToString()}"
                }
                prefs.edit().putString("app_theme", featureValue.lowercase()).apply()
                "Tema cambiado a: ${featureValue.lowercase()} ✅"
            }
            else -> "Feature no reconocida: $featureName. Válidas: deepgram_stt, deepgram_tts, theme"
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TELEGRAM ALLOWLIST OPERATIONS
    // ═══════════════════════════════════════════════════════════

    private fun getTelegramAllowlist(): String = buildString {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val securePrefs = SecurePrefs.get(context)

        val ownerUsername = securePrefs.getString("telegram_owner_username", "") ?: ""
        val chatStr = prefs.getString(TelegramBotService.PREF_ALLOWED_CHATS, "") ?: ""
        val userStr = prefs.getString(TelegramBotService.PREF_ALLOWED_USERS, "") ?: ""

        val chatIds = if (chatStr.isNotBlank()) chatStr.split(",").mapNotNull { it.trim().toLongOrNull() } else emptyList()
        val userIds = if (userStr.isNotBlank()) userStr.split(",").mapNotNull { it.trim().toLongOrNull() } else emptyList()

        appendLine("═══ TELEGRAM BOT — ACCESO AUTORIZADO ═══")
        appendLine()
        appendLine("👤 Owner: ${if (ownerUsername.isNotBlank()) "@$ownerUsername" else "(no configurado — first-contact rule activa)"}")
        appendLine()

        appendLine("📋 Chat IDs autorizados (${chatIds.size}):")
        if (chatIds.isEmpty()) {
            appendLine("  (ninguno)")
        } else {
            chatIds.forEach { id ->
                val type = if (id < 0) "grupo/supergrupo" else "chat privado"
                appendLine("  • $id ($type)")
            }
        }

        appendLine()
        appendLine("👥 User IDs autorizados (${userIds.size}):")
        if (userIds.isEmpty()) {
            appendLine("  (ninguno)")
        } else {
            userIds.forEach { id ->
                appendLine("  • $id")
            }
        }

        appendLine()
        appendLine("ℹ️ Los usuarios autorizados pueden interactuar con el bot en cualquier grupo. El grupo se auto-registra cuando un usuario autorizado habla ahí.")
    }

    private suspend fun addTelegramAccess(args: Map<String, Any>): String {
        val idStr = args["telegram_id"] as? String ?: return "Falta 'telegram_id'. Proporciona el ID numérico del chat o usuario."
        val idType = (args["telegram_id_type"] as? String)?.lowercase() ?: "chat"

        val id = idStr.toLongOrNull() ?: return "'$idStr' no es un ID numérico válido."

        val prefKey = when (idType) {
            "user" -> TelegramBotService.PREF_ALLOWED_USERS
            else -> TelegramBotService.PREF_ALLOWED_CHATS
        }
        val label = if (idType == "user") "usuario" else "chat/grupo"

        // SecurityGate: YELLOW
        val op = SecurityGate.yellow(this@SelfConfigPlugin.id, "add_telegram_access", "Añadir $label $id a la allowlist de Telegram")
        if (!SecurityGate.evaluate(op)) {
            return "Operación cancelada."
        }

        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val existing = prefs.getString(prefKey, "")?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }?.toMutableSet() ?: mutableSetOf()

        if (id in existing) {
            return "El $label $id ya está en la allowlist."
        }

        existing.add(id)
        prefs.edit().putString(prefKey, existing.joinToString(",")).apply()
        Log.i(TAG, "Telegram access added: $label $id")

        return "✅ $label $id añadido a la allowlist de Telegram. Total ${existing.size} ${label}s autorizados."
    }

    private suspend fun removeTelegramAccess(args: Map<String, Any>): String {
        val idStr = args["telegram_id"] as? String ?: return "Falta 'telegram_id'. Proporciona el ID numérico del chat o usuario."
        val idType = (args["telegram_id_type"] as? String)?.lowercase() ?: "chat"

        val id = idStr.toLongOrNull() ?: return "'$idStr' no es un ID numérico válido."

        val prefKey = when (idType) {
            "user" -> TelegramBotService.PREF_ALLOWED_USERS
            else -> TelegramBotService.PREF_ALLOWED_CHATS
        }
        val label = if (idType == "user") "usuario" else "chat/grupo"

        // SecurityGate: RED — removing access is a sensitive operation
        val op = SecurityGate.red(this@SelfConfigPlugin.id, "remove_telegram_access", "Quitar $label $id de la allowlist de Telegram")
        if (!SecurityGate.evaluate(op)) {
            return "Operación cancelada."
        }

        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val existing = prefs.getString(prefKey, "")?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }?.toMutableSet() ?: mutableSetOf()

        if (id !in existing) {
            return "El $label $id no está en la allowlist."
        }

        existing.remove(id)
        prefs.edit().putString(prefKey, existing.joinToString(",")).apply()
        Log.i(TAG, "Telegram access removed: $label $id")

        return "🗑️ $label $id eliminado de la allowlist de Telegram. Quedan ${existing.size} ${label}s autorizados."
    }
}
