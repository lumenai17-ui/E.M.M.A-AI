package com.beemovil.tunnel

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HermesTunnelClient(
    private val serverUrl: String,
    private val deviceId: String,
    private val token: String
) {
    private var webSocket: WebSocket? = null
    // C-02 fix: flag real de conexión actualizado por callbacks del socket
    @Volatile private var isAlive = false
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    var onMessage: ((JSONObject) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelay = 5_000L
    
    fun connect() {
        Log.i("HermesTunnel", "Conectando al Túnel Hermes en: $serverUrl")
        val request = Request.Builder()
            .url(serverUrl)
            .header("Authorization", "Bearer $token")
            .header("X-Device-ID", deviceId)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("HermesTunnel", "Conexión Túnel Abierta.")
                reconnectAttempts = 0
                isAlive = true
                onConnected?.invoke()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    handleMessage(msg)
                } catch (e: Exception) {
                    onError?.invoke("JSON inválido: ${e.message}")
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isAlive = false
                webSocket.close(1000, null)
                onDisconnected?.invoke()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isAlive = false
                onError?.invoke("Error de conexión: ${t.message}")
                onDisconnected?.invoke()
                
                if (reconnectAttempts < maxReconnectAttempts) {
                    reconnectAttempts++
                    Log.d("HermesTunnel", "Reintentando ($reconnectAttempts/$maxReconnectAttempts)...")
                    scope.launch {
                        delay(reconnectDelay)
                        connect()
                    }
                }
            }
        })
    }
    
    fun disconnect() {
        isAlive = false
        webSocket?.close(1000, "Usuario desconectó")
        webSocket = null
    }
    
    // C-02 fix: usa flag real en lugar de solo verificar que el objeto exista
    fun isConnected(): Boolean = isAlive
    
    fun sendMessage(data: JSONObject) {
        val msg = JSONObject().apply {
            put("type", "message")
            put("data", data)
        }
        webSocket?.send(msg.toString())
    }
    
    fun sendA2ATask(task: JSONObject) {
        val msg = JSONObject().apply {
            put("type", "a2a")
            put("task", task)
        }
        webSocket?.send(msg.toString())
    }
    
    fun sendStatus(state: JSONObject) {
        val msg = JSONObject().apply {
            put("type", "status")
            put("state", state)
        }
        webSocket?.send(msg.toString())
    }
    
    fun ping() {
        val msg = JSONObject().apply { put("type", "ping") }
        webSocket?.send(msg.toString())
    }
    
    private fun handleMessage(msg: JSONObject) {
        val type = msg.optString("type")
        
        when (type) {
            "connected" -> {
                Log.i("HermesTunnel", "Handshake confirmado por servidor.")
            }
            "pong" -> { }
            "message" -> {
                val data = msg.optJSONObject("data")
                if (data != null) {
                    onMessage?.invoke(data)
                }
            }
            "error" -> {
                val errorMsg = msg.optString("message")
                onError?.invoke(errorMsg)
            }
            else -> {
                onMessage?.invoke(msg)
            }
        }
    }
    
    fun destroy() {
        disconnect()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }
}
