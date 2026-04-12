package com.beemovil.tunnel

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject

object HermesTunnelManager {
    var client: HermesTunnelClient? = null
        private set

    // Control asincrónico para peticiones A2A
    private val pendingTasks = mutableMapOf<String, CompletableDeferred<String>>()

    fun saveConfig(context: Context, url: String, token: String) {
        val prefs = context.getSharedPreferences("hermes_tunnel_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("server_url", url)
            putString("token", token)
            apply()
        }
    }

    fun getConfig(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences("hermes_tunnel_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("server_url", "")
        val token = prefs.getString("token", "")
        if (url.isNullOrBlank() || token.isNullOrBlank()) return null
        return Pair(url, token)
    }

    fun startTunnel(context: Context, url: String, token: String, onStateChange: ((Boolean) -> Unit)? = null) {
        if (client?.isConnected() == true) return

        // Guardamos las configuraciones para re-hidratado si el servicio reinicia
        saveConfig(context, url, token)

        val deviceId = com.beemovil.security.SecurePrefs.get(context).getString("device_id", "") ?: "emma-phone"

        client = HermesTunnelClient(serverUrl = url, deviceId = deviceId, token = token).apply {
            onConnected = {
                Log.i("TunnelManager", "Hermes Tunnel Conectado.")
                onStateChange?.invoke(true)
            }
            onDisconnected = {
                Log.i("TunnelManager", "Hermes Tunnel Desconectado.")
                onStateChange?.invoke(false)
            }
            onMessage = { msg ->
                val event = msg.optString("event")
                when (event) {
                    "task_complete" -> {
                        val taskId = msg.optString("task_id")
                        val result = msg.optJSONObject("result")?.toString() ?: msg.optString("result", "Completado sin data")
                        pendingTasks[taskId]?.complete(result)
                        pendingTasks.remove(taskId)
                    }
                    else -> {
                        Log.i("TunnelManager", "Mensaje no ruteado recibido: \${msg}")
                    }
                }
            }
            connect()
        }
    }

    fun stopTunnel(context: Context) {
        client?.disconnect()
        client = null
        
        // Removemos las credenciales si el usuario desconecta explícitamente
        val prefs = context.getSharedPreferences("hermes_tunnel_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    suspend fun executeA2ATask(taskType: String, content: String): String {
        val c = client ?: return "Error: Túnel maestro no conectado."
        if (!c.isConnected()) return "Error: Túnel maestro sin conexión a la red."

        val taskId = "task_${System.currentTimeMillis()}"
        val deferred = CompletableDeferred<String>()
        pendingTasks[taskId] = deferred

        val taskJson = JSONObject().apply {
            put("id", taskId)
            put("type", taskType)
            put("content", content)
        }

        c.sendA2ATask(taskJson)
        
        // Esperamos la respuesta asincrónicamente o time-out a los 45s
        return withTimeoutOrNull(45_000L) {
            deferred.await()
        } ?: run {
            pendingTasks.remove(taskId)
            "Timeout: Hermes no respondió tras 45 segundos."
        }
    }

    suspend fun executeDynamicA2ATask(url: String, token: String, taskType: String, content: String): String {
        val taskId = "task_dyn_${System.currentTimeMillis()}"
        val deferred = CompletableDeferred<String>()
        
        val ephemeralClient = HermesTunnelClient(serverUrl = url, deviceId = "emma-dynamic", token = token).apply {
            onConnected = { Log.i("TunnelManager", "Tunnel Efímero Conectado a $url") }
            onMessage = { msg ->
                val event = msg.optString("event")
                if (event == "task_complete" && msg.optString("task_id") == taskId) {
                    val result = msg.optJSONObject("result")?.toString() ?: msg.optString("result", "Completado sin data")
                    deferred.complete(result)
                    this.disconnect()
                }
            }
        }
        
        ephemeralClient.connect()
        delay(1500) // Give it a second to handshake
        
        if (!ephemeralClient.isConnected()) {
            return "Error: Túnel Efímero falló al conectar a $url"
        }
        
        val taskJson = JSONObject().apply {
            put("id", taskId)
            put("type", taskType)
            put("content", content)
        }
        ephemeralClient.sendA2ATask(taskJson)
        
        return withTimeoutOrNull(45_000L) {
            deferred.await()
        } ?: run {
            ephemeralClient.disconnect()
            "Timeout: Túnel Efímero Remoto no respondió tras 45 segundos."
        }
    }
}
