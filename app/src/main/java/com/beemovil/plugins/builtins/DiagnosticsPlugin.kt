package com.beemovil.plugins.builtins

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.core.content.ContextCompat
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.security.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * DiagnosticsPlugin — Project Autonomía Phase S1
 *
 * E.M.M.A.'s self-awareness: she can diagnose her own health,
 * check permissions, test connectivity, and troubleshoot problems.
 * All operations are 🟢 GREEN (read-only, no confirmation needed).
 */
class DiagnosticsPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "emma_diagnostics"
    private val TAG = "DiagnosticsPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = """Tu herramienta de auto-diagnóstico interno. Úsala SIEMPRE que el usuario pregunte: 
                '¿por qué no funciona X?', '¿qué permisos tengo?', '¿estás conectada?', 
                '¿cómo estás de salud?', o cualquier pregunta sobre tu estado interno.
                También puedes leer tus propios logs para diagnosticar errores en tiempo real.
                NUNCA inventes tu estado — SIEMPRE consulta este plugin.""".trimIndent(),
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray()
                            .put("health_check")
                            .put("permission_audit")
                            .put("storage_report")
                            .put("feature_status")
                            .put("connectivity_test")
                            .put("why_not_working")
                            .put("read_logs")
                        )
                        put("description", """Operación de diagnóstico:
                            - health_check: Estado general de todos los proveedores y servicios
                            - permission_audit: Verificar permisos del sistema (cámara, mic, GPS, etc.)
                            - storage_report: Uso de almacenamiento del dispositivo
                            - feature_status: Estado de todas las features configuradas
                            - connectivity_test: Probar conectividad a cada proveedor
                            - why_not_working: Diagnóstico guiado de un problema específico
                            - read_logs: Leer tus propios logs de runtime (logcat) para diagnosticar errores""".trimIndent())
                    })
                    put("target", JSONObject().apply {
                        put("type", "string")
                        put("description", """Para why_not_working: qué componente investigar: 'tts', 'stt', 'vision', 'llm', 'google', 'email'.
                            Para read_logs: filtro opcional de tag (ej: 'GeminiLiveBackend', 'ConversationEngine', 'WakeWord', 'error'). Si no se especifica, lee los últimos logs generales.""".trimIndent())
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
                    "health_check" -> healthCheck()
                    "permission_audit" -> permissionAudit()
                    "storage_report" -> storageReport()
                    "feature_status" -> featureStatus()
                    "connectivity_test" -> connectivityTest()
                    "why_not_working" -> {
                        val target = args["target"] as? String ?: "general"
                        whyNotWorking(target)
                    }
                    "read_logs" -> {
                        val filter = args["target"] as? String
                        readLogs(filter)
                    }
                    else -> "Operación desconocida: $operation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Diagnostics error", e)
                "Error en diagnóstico: ${e.message}"
            }
        }
    }

    private fun healthCheck(): String = buildString {
        appendLine("═══ DIAGNÓSTICO DE SALUD E.M.M.A. ═══")
        appendLine()

        // API Keys status
        val securePrefs = SecurePrefs.get(context)
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)

        val keys = mapOf(
            "OpenRouter" to (securePrefs.getString("openrouter_api_key", "") ?: ""),
            "Ollama Cloud" to (securePrefs.getString("ollama_api_key", "") ?: ""),
            "Deepgram" to (securePrefs.getString("deepgram_api_key", "") ?: ""),
            "ElevenLabs" to (securePrefs.getString("elevenlabs_api_key", "") ?: ""),
            "Google AI (Gemini)" to (securePrefs.getString("google_ai_key", "") ?: ""),
            "Google OAuth" to (securePrefs.getString("google_access_token", "") ?: "")
        )

        appendLine("🔑 API KEYS:")
        keys.forEach { (name, key) ->
            val status = if (key.isNotBlank()) "✅ Configurada (${key.length} chars)" else "❌ No configurada"
            appendLine("  $name: $status")
        }

        appendLine()
        appendLine("⚙️ MODELO ACTIVO:")
        val provider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
        val model = prefs.getString("selected_model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini"
        appendLine("  Provider: $provider")
        appendLine("  Modelo: $model")

        appendLine()
        appendLine("🎤 VOZ:")
        val useDeepgramSTT = prefs.getBoolean("use_deepgram_stt", true)
        val useDeepgramTTS = prefs.getBoolean("use_deepgram_tts", true)
        val deepgramKey = keys["Deepgram"]?.isNotBlank() ?: false
        val elevenLabsKey = keys["ElevenLabs"]?.isNotBlank() ?: false
        appendLine("  Deepgram STT: ${if (useDeepgramSTT && deepgramKey) "✅ Activo" else if (!deepgramKey) "❌ Sin API key" else "⏸️ Desactivado"}")
        appendLine("  Deepgram TTS: ${if (useDeepgramTTS && deepgramKey) "✅ Activo" else if (!deepgramKey) "❌ Sin API key" else "⏸️ Desactivado"}")
        appendLine("  ElevenLabs TTS: ${if (elevenLabsKey) "✅ Configurado" else "❌ Sin API key"}")
        appendLine("  Fallback: Android Nativo (siempre disponible)")

        appendLine()

        // Permissions quick summary
        val criticalPerms = listOf(
            "Cámara" to Manifest.permission.CAMERA,
            "Micrófono" to Manifest.permission.RECORD_AUDIO,
            "GPS" to Manifest.permission.ACCESS_FINE_LOCATION,
            "Almacenamiento" to Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val permIssues = criticalPerms.filter {
            ContextCompat.checkSelfPermission(context, it.second) != PackageManager.PERMISSION_GRANTED
        }
        if (permIssues.isEmpty()) {
            appendLine("🛡️ PERMISOS: ✅ Todos los permisos críticos concedidos")
        } else {
            appendLine("🛡️ PERMISOS: ⚠️ Faltan: ${permIssues.joinToString { it.first }}")
        }
    }

    private fun permissionAudit(): String = buildString {
        appendLine("═══ AUDITORÍA DE PERMISOS ═══")
        appendLine()

        val permissions = listOf(
            "📷 Cámara" to Manifest.permission.CAMERA,
            "🎤 Micrófono" to Manifest.permission.RECORD_AUDIO,
            "📍 GPS (Fine)" to Manifest.permission.ACCESS_FINE_LOCATION,
            "📍 GPS (Coarse)" to Manifest.permission.ACCESS_COARSE_LOCATION,
            "📁 Leer Almacenamiento" to Manifest.permission.READ_EXTERNAL_STORAGE,
            "📁 Escribir Almacenamiento" to Manifest.permission.WRITE_EXTERNAL_STORAGE,
            "👤 Contactos" to Manifest.permission.READ_CONTACTS,
            "📞 Teléfono" to Manifest.permission.CALL_PHONE,
            "📅 Leer Calendario" to Manifest.permission.READ_CALENDAR,
            "📅 Escribir Calendario" to Manifest.permission.WRITE_CALENDAR,
            "🔔 Notificaciones" to Manifest.permission.POST_NOTIFICATIONS,
            "📳 Vibración" to Manifest.permission.VIBRATE,
            "⏰ Alarmas" to Manifest.permission.SET_ALARM,
            "📶 Estado WiFi" to Manifest.permission.ACCESS_WIFI_STATE,
            "📱 Estado Teléfono" to Manifest.permission.READ_PHONE_STATE,
            "🏃 Actividad Física" to Manifest.permission.ACTIVITY_RECOGNITION
        )

        var granted = 0
        var denied = 0
        permissions.forEach { (name, perm) ->
            val ok = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            if (ok) granted++ else denied++
            appendLine("  ${if (ok) "✅" else "❌"} $name")
        }

        appendLine()
        appendLine("Resumen: $granted concedidos, $denied denegados de ${permissions.size} total")
    }

    private fun storageReport(): String = buildString {
        appendLine("═══ REPORTE DE ALMACENAMIENTO ═══")
        appendLine()

        // Internal storage
        val internalPath = Environment.getDataDirectory()
        val internalStats = StatFs(internalPath.path)
        val internalFree = internalStats.availableBytes / (1024 * 1024)
        val internalTotal = internalStats.totalBytes / (1024 * 1024)
        val internalUsed = internalTotal - internalFree
        val internalPct = if (internalTotal > 0) (internalUsed * 100 / internalTotal) else 0

        appendLine("📱 ALMACENAMIENTO INTERNO:")
        appendLine("  Total: ${formatMB(internalTotal)}")
        appendLine("  Usado: ${formatMB(internalUsed)} ($internalPct%)")
        appendLine("  Libre: ${formatMB(internalFree)}")

        // App-specific
        appendLine()
        appendLine("📦 DATOS DE E.M.M.A.:")
        val appDir = context.filesDir
        val cacheDir = context.cacheDir
        val appSize = dirSize(appDir)
        val cacheSize = dirSize(cacheDir)
        appendLine("  App data: ${formatMB(appSize / (1024 * 1024))}")
        appendLine("  Cache: ${formatMB(cacheSize / (1024 * 1024))}")

        // External (Downloads, DCIM, etc.)
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            appendLine()
            appendLine("📂 CARPETAS EXTERNAS:")
            val folders = mapOf(
                "Downloads" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "DCIM" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "Documents" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "Pictures" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            )
            folders.forEach { (name, dir) ->
                if (dir.exists()) {
                    val count = dir.listFiles()?.size ?: 0
                    val size = dirSize(dir) / (1024 * 1024)
                    appendLine("  $name: $count archivos (${formatMB(size)})")
                }
            }
        }
    }

    private fun featureStatus(): String = buildString {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val securePrefs = SecurePrefs.get(context)

        appendLine("═══ ESTADO DE FEATURES ═══")
        appendLine()

        appendLine("🤖 LLM:")
        appendLine("  Provider: ${prefs.getString("selected_provider", "openrouter")}")
        appendLine("  Modelo: ${prefs.getString("selected_model", "openai/gpt-4o-mini")}")

        appendLine()
        appendLine("🎤 VOZ:")
        appendLine("  Deepgram STT: ${prefs.getBoolean("use_deepgram_stt", true)}")
        appendLine("  Deepgram TTS: ${prefs.getBoolean("use_deepgram_tts", true)}")
        appendLine("  Deepgram Voice: ${prefs.getString("deepgram_voice", "aura-asteria-en")}")
        appendLine("  ElevenLabs Voice ID: ${securePrefs.getString("elevenlabs_voice_id", "")?.take(8) ?: "N/A"}...")

        appendLine()
        appendLine("👁️ VISION:")
        appendLine("  Último modo usado: ${prefs.getString("vision_mode", "GENERAL")}")

        appendLine()
        appendLine("🌐 INTEGRACIONES:")
        appendLine("  Google OAuth: ${if ((securePrefs.getString("google_access_token", "") ?: "").isNotBlank()) "✅ Conectado" else "❌ No conectado"}")
        appendLine("  Hermes Tunnel: ${prefs.getString("hermes_tunnel_url", "No configurado")}")
        appendLine("  Telegram Bot: ${if ((securePrefs.getString("telegram_bot_token", "") ?: "").isNotBlank()) "✅ Configurado" else "❌ No configurado"}")

        appendLine()
        appendLine("🎨 UI:")
        appendLine("  Tema: ${prefs.getString("app_theme", "system")}")
        appendLine("  Onboarding completado: ${prefs.getBoolean("onboarding_completed", false)}")

        // Count plugins
        appendLine()
        appendLine("🔌 PLUGINS: 27+ registrados en EmmaEngine")
    }

    private fun connectivityTest(): String = buildString {
        appendLine("═══ TEST DE CONECTIVIDAD ═══")
        appendLine()

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "OpenRouter" to "https://openrouter.ai/api/v1/models",
            "Deepgram" to "https://api.deepgram.com/v1",
            "ElevenLabs" to "https://api.elevenlabs.io/v1/voices",
            "Pollinations (Imágenes)" to "https://image.pollinations.ai/prompt/test?width=1&height=1&nologo=true",
            "Google APIs" to "https://www.googleapis.com/discovery/v1/apis"
        )

        endpoints.forEach { (name, url) ->
            val result = try {
                val request = Request.Builder().url(url).head().build()
                val response = client.newCall(request).execute()
                val code = response.code
                response.close()
                when {
                    code in 200..399 -> "✅ OK ($code)"
                    code == 401 -> "⚠️ Responde pero necesita auth ($code)"
                    code == 403 -> "⚠️ Acceso denegado ($code)"
                    else -> "⚠️ HTTP $code"
                }
            } catch (e: Exception) {
                "❌ Error: ${e.message?.take(40)}"
            }
            appendLine("  $name: $result")
        }
    }

    private fun whyNotWorking(target: String): String = buildString {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val securePrefs = SecurePrefs.get(context)

        appendLine("═══ DIAGNÓSTICO: ${target.uppercase()} ═══")
        appendLine()

        when (target.lowercase()) {
            "tts", "voz", "hablar" -> {
                appendLine("Verificando Text-to-Speech...")
                val dgKey = securePrefs.getString("deepgram_api_key", "") ?: ""
                val elKey = securePrefs.getString("elevenlabs_api_key", "") ?: ""
                val useDgTTS = prefs.getBoolean("use_deepgram_tts", true)

                if (dgKey.isBlank() && elKey.isBlank()) {
                    appendLine("❌ CAUSA: No hay API key de Deepgram ni ElevenLabs.")
                    appendLine("💡 SOLUCIÓN: Ve a Settings → Voice y agrega un API key de Deepgram o ElevenLabs.")
                    appendLine("   Alternativa: El TTS nativo de Android debería funcionar como fallback.")
                } else if (dgKey.isNotBlank() && !useDgTTS) {
                    appendLine("⚠️ Deepgram key existe pero TTS está desactivado en settings.")
                    appendLine("💡 Actívalo en Settings → Voice → Use Deepgram TTS.")
                } else {
                    appendLine("✅ Keys configuradas. El problema puede ser de conectividad.")
                    appendLine("💡 Ejecuta 'connectivity_test' para verificar.")
                }
            }
            "stt", "micrófono", "escuchar" -> {
                val micPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val dgKey = securePrefs.getString("deepgram_api_key", "") ?: ""

                if (!micPerm) {
                    appendLine("❌ CAUSA: Permiso de micrófono no concedido.")
                    appendLine("💡 SOLUCIÓN: Ve a configuración del teléfono → Apps → E.M.M.A. → Permisos → Micrófono.")
                } else if (dgKey.isBlank()) {
                    appendLine("⚠️ Sin Deepgram API key. Usando reconocimiento nativo de Android.")
                    appendLine("💡 Para mejor reconocimiento, agrega un API key de Deepgram en Settings.")
                } else {
                    appendLine("✅ Micrófono y Deepgram configurados correctamente.")
                }
            }
            "vision", "cámara" -> {
                val camPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val provider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
                val apiKey = securePrefs.getString("openrouter_api_key", "") ?: ""

                if (!camPerm) {
                    appendLine("❌ CAUSA: Permiso de cámara no concedido.")
                    appendLine("💡 SOLUCIÓN: Ve a configuración del teléfono → Apps → E.M.M.A. → Permisos → Cámara.")
                } else if (apiKey.isBlank()) {
                    appendLine("❌ CAUSA: Sin API key para $provider.")
                    appendLine("💡 SOLUCIÓN: Vision necesita un LLM con soporte de imágenes. Configura OpenRouter en Settings.")
                } else {
                    appendLine("✅ Cámara y provider configurados. Vision debería funcionar.")
                    appendLine("💡 Si hay errores, puede ser el modelo seleccionado. Prueba con uno que soporte vision (ej: gpt-4o-mini, gemini-flash).")
                }
            }
            "llm", "modelo", "respuestas" -> {
                val provider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
                val model = prefs.getString("selected_model", "openai/gpt-4o-mini") ?: "?"
                val key = securePrefs.getString("openrouter_api_key", "") ?: ""

                appendLine("Provider: $provider | Modelo: $model")
                if (key.isBlank()) {
                    appendLine("❌ CAUSA: Sin API key para $provider.")
                    appendLine("💡 SOLUCIÓN: Agrega tu API key en Settings.")
                } else {
                    appendLine("✅ API key presente (${key.length} chars). Ejecuta 'connectivity_test' para verificar conectividad.")
                }
            }
            "google", "gmail", "tasks" -> {
                val token = securePrefs.getString("google_access_token", "") ?: ""
                if (token.isBlank()) {
                    appendLine("❌ CAUSA: No has conectado tu cuenta de Google.")
                    appendLine("💡 SOLUCIÓN: Ve a Settings → Google → Conectar cuenta.")
                } else {
                    appendLine("✅ Google OAuth conectado. Token presente.")
                    appendLine("💡 Si hay errores, el token puede haber expirado. Reconecta en Settings → Google.")
                }
            }
            else -> {
                appendLine("No reconozco '$target'. Componentes diagnosticables:")
                appendLine("  tts, stt, vision, llm, google, email")
                appendLine("💡 Ejecuta 'health_check' para un diagnóstico general completo.")
            }
        }
    }

    /**
     * Read logcat output — Emma can see her own runtime logs!
     * Uses `logcat -d` (dump mode) to get recent logs without blocking.
     *
     * @param filter Optional tag or keyword filter (e.g. "GeminiLiveBackend", "error")
     */
    private fun readLogs(filter: String?): String = buildString {
        appendLine("═══ LOGS DE RUNTIME (LOGCAT) ═══")
        appendLine()

        try {
            // Build logcat command
            // -d = dump and exit (non-blocking)
            // -t 200 = last 200 lines
            // --pid = our process only
            val pid = android.os.Process.myPid()
            val command = if (filter.isNullOrBlank()) {
                // No filter — get last 150 lines from our process
                arrayOf("logcat", "-d", "-t", "150", "--pid=$pid")
            } else {
                // With filter — get more lines and grep later
                arrayOf("logcat", "-d", "-t", "500", "--pid=$pid")
            }

            val process = Runtime.getRuntime().exec(command)
            val reader = process.inputStream.bufferedReader()
            val lines = reader.readLines()
            process.waitFor()

            // Apply filter if specified
            val filteredLines = if (!filter.isNullOrBlank()) {
                val filterLower = filter.lowercase()
                lines.filter { line ->
                    line.lowercase().contains(filterLower)
                }.takeLast(100) // Max 100 filtered lines
            } else {
                lines.takeLast(100) // Last 100 lines
            }

            if (filteredLines.isEmpty()) {
                appendLine("No se encontraron logs ${if (filter != null) "con filtro '$filter'" else ""}.")
                appendLine()
                appendLine("💡 Tags útiles para filtrar:")
                appendLine("  - GeminiLiveBackend (errores de Gemini API)")
                appendLine("  - ConversationEngine (flujo de conversación)")
                appendLine("  - WakeWord / NativeWakeWord (detección de Hello Emma)")
                appendLine("  - PipelineBackend (backend Pipeline)")
                appendLine("  - DeepgramVoice (STT/TTS)")
                appendLine("  - EmmaEngine (motor principal)")
                appendLine("  - error (cualquier error)")
            } else {
                if (filter != null) {
                    appendLine("Filtro: '$filter' | ${filteredLines.size} líneas encontradas")
                } else {
                    appendLine("Últimas ${filteredLines.size} líneas del proceso E.M.M.A.")
                }
                appendLine("─".repeat(50))

                filteredLines.forEach { line ->
                    // Clean up logcat format for readability
                    appendLine(line.take(200)) // Cap line length
                }
            }

            appendLine()
            appendLine("─".repeat(50))
            appendLine("📊 PID: $pid | Total líneas leídas: ${lines.size}")

        } catch (e: Exception) {
            appendLine("❌ Error leyendo logcat: ${e.message}")
            appendLine("💡 Algunos dispositivos restringen acceso a logcat.")
        }
    }

    // ── Helpers ──

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) { 0 }
    }

    private fun formatMB(mb: Long): String {
        return if (mb > 1024) "${mb / 1024} GB" else "$mb MB"
    }
}
