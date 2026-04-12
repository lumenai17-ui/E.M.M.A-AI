# Mobile Tunnel - Integración E.M.M.A.

## Resumen Ejecutivo

Este documento describe cómo integrar **E.M.M.A. Android** con **Hermes Mobile Tunnel** para lograr comunicación bidireccional sin depender de Firebase/Google.

**Enfoque:** El usuario controla cuándo estar disponible mediante un toggle. La conexión WebSocket permanece abierta solo cuando el usuario elige conectarse.

---

## Arquitectura

```
┌──────────────────────────────────────────────────────────────────┐
│                        FLUJO DE CONEXIÓN                          │
└──────────────────────────────────────────────────────────────────┘

    Usuario abre E.M.M.A.
           │
           ▼
    ┌─────────────────┐
    │  [🔵 Conectar]   │  ← Toggle en UI
    │  con Hermes     │
    └─────────────────┘
           │
           ▼
    WebSocket conecta a ws://servidor:8643/mobile/ws
           │
           ▼
    ┌─────────────────────────────────────┐
    │  Conexión activa                    │
    │  - Hermes puede enviar mensajes    │
    │  - E.M.M.A. puede enviar mensajes  │
    │  - Ping/pong cada 30 segundos      │
    └─────────────────────────────────────┘
           │
           ▼
    Usuario presiona [⛔ Desconectar]
           │
           ▼
    WebSocket se cierra
    Hermes ya no puede comunicarse
```

---

## Lo que Hermes Ya Tiene

Hermes ya implementó:

| Componente | Archivo | Descripción |
|------------|---------|-------------|
| **WebSocket Server** | `gateway/platforms/mobile_tunnel.py` | Servidor en puerto 8643 |
| **Endpoint /mobile/ws** | mobile_tunnel.py | Acepta conexiones WebSocket |
| **Autenticación** | mobile_tunnel.py | Bearer token o query param |
| **Cola de pendientes** | mobile_tunnel.py | Mensajes se guardan si dispositivo offline |
| **HTTP API** | mobile_tunnel.py | `/mobile/devices`, `/mobile/send` |
| **Health check** | mobile_tunnel.py | `/health` endpoint |

---

## Lo que E.M.M.A. Necesita Implementar

### 1. Kotlin: HermesTunnelClient

Crear archivo: `app/src/main/java/com/beemovil/tunnel/HermesTunnelClient.kt`

```kotlin
package com.beemovil.tunnel

import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket para comunicación bidireccional con Hermes.
 * 
 * Uso:
 *   val client = HermesTunnelClient(
 *       serverUrl = "wss://tu-servidor.com/mobile/ws",
 *       deviceId = "emma-phone-001",
 *       token = "tu-secret-key"
 *   )
 *   
 *   client.onMessage = { msg -> println("Recibí: $msg") }
 *   client.connect()
 *   // ... cuando termines ...
 *   client.disconnect()
 */
class HermesTunnelClient(
    private val serverUrl: String,
    private val deviceId: String,
    private val token: String
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)  // Keepalive
        .build()
    
    var onMessage: ((JSONObject) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Conecta al servidor Hermes.
     * Llama onConnected cuando la conexión esté lista.
     */
    fun connect() {
        val request = Request.Builder()
            .url(serverUrl)
            .header("Authorization", "Bearer $token")
            .header("X-Device-ID", deviceId)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
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
                webSocket.close(1000, null)
                onDisconnected?.invoke()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError?.invoke("Error de conexión: ${t.message}")
                onDisconnected?.invoke()
            }
        })
    }
    
    /**
     * Desconecta del servidor.
     */
    fun disconnect() {
        webSocket?.close(1000, "Usuario desconectó")
        webSocket = null
    }
    
    /**
     * Verifica si la conexión está activa.
     */
    fun isConnected(): Boolean = webSocket != null
    
    /**
     * Envía un mensaje genérico.
     */
    fun sendMessage(data: JSONObject) {
        val msg = JSONObject().apply {
            put("type", "message")
            put("data", data)
        }
        webSocket?.send(msg.toString())
    }
    
    /**
     * Envía un mensaje A2A.
     */
    fun sendA2ATask(task: JSONObject) {
        val msg = JSONObject().apply {
            put("type", "a2a")
            put("task", task)
        }
        webSocket?.send(msg.toString())
    }
    
    /**
     * Envía actualización de estado (batería, red, etc.)
     */
    fun sendStatus(state: JSONObject) {
        val msg = JSONObject().apply {
            put("type", "status")
            put("state", state)
        }
        webSocket?.send(msg.toString())
    }
    
    /**
     * Envía ping manual (además del de OkHttp).
     */
    fun ping() {
        val msg = JSONObject().apply { put("type", "ping") }
        webSocket?.send(msg.toString())
    }
    
    private fun handleMessage(msg: JSONObject) {
        val type = msg.optString("type")
        
        when (type) {
            "connected" -> {
                // Servidor confirmó conexión
                val connectedDeviceId = msg.optString("device_id")
                val heartbeatSeconds = msg.optInt("heartbeat", 30)
                onConnected?.invoke()
            }
            
            "pong" -> {
                // Respuesta a ping manual
            }
            
            "message" -> {
                // Mensaje genérico de Hermes
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
                // Otros tipos de mensaje
                onMessage?.invoke(msg)
            }
        }
    }
    
    /**
     * Limpia recursos al destruir.
     */
    fun destroy() {
        disconnect()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }
}
```

---

### 2. Integración en EmmaEngine

Modificar `EmmaEngine.kt` para agregar el cliente tunnel:

```kotlin
class EmmaEngine(
    private val context: Context,
    // ... otros parámetros
) {
    // Cliente WebSocket
    private var tunnelClient: HermesTunnelClient? = null
    
    // Estado de conexión
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    /**
     * Conecta al servidor Hermes con toggle del usuario.
     * Llamar cuando el usuario activa el toggle "Conectar con Hermes".
     */
    fun connectToHermes(serverUrl: String, token: String) {
        if (tunnelClient?.isConnected() == true) {
            return  // Ya conectado
        }
        
        val deviceId = getDeviceId()  // Implementar: obtener ID único
        
        tunnelClient = HermesTunnelClient(
            serverUrl = serverUrl,
            deviceId = deviceId,
            token = token
        ).apply {
            onConnected = {
                _isConnected.value = true
                // Notificar a la UI
                notifyConnectionStatus(true)
                // Enviar estado inicial
                sendStatus(JSONObject().apply {
                    put("battery", getBatteryLevel())
                    put("network", getNetworkType())
                })
            }
            
            onDisconnected = {
                _isConnected.value = false
                notifyConnectionStatus(false)
            }
            
            onMessage = { data ->
                // Procesar mensaje de Hermes
                handleHermesMessage(data)
            }
            
            onError = { error ->
                Log.e("EmmaEngine", "Tunnel error: $error")
            }
        }
        
        tunnelClient?.connect()
    }
    
    /**
     * Desconecta del servidor Hermes.
     * Llamar cuando el usuario desactiva el toggle.
     */
    fun disconnectFromHermes() {
        tunnelClient?.disconnect()
        tunnelClient = null
        _isConnected.value = false
    }
    
    /**
     * Envía mensaje a Hermes.
     * Solo funciona si está conectado.
     */
    fun sendToHermes(message: JSONObject): Boolean {
        return if (tunnelClient?.isConnected() == true) {
            tunnelClient?.sendMessage(message)
            true
        } else {
            false
        }
    }
    
    /**
     * Procesa mensajes recibidos de Hermes.
     */
    private fun handleHermesMessage(data: JSONObject) {
        val event = data.optString("event")
        
        when (event) {
            "task_request" -> {
                // Hermes solicita una tarea
                val task = data.optJSONObject("task")
                handleIncomingTask(task)
            }
            
            "notification" -> {
                // Notificación simple
                val text = data.optString("text")
                showNotification(text)
            }
            
            "task_complete" -> {
                // Una tarea completada
                val taskId = data.optString("task_id")
                val result = data.optJSONObject("result")
                handleTaskComplete(taskId, result)
            }
            
            else -> {
                // Mensaje genérico
                handleGenericMessage(data)
            }
        }
    }
    
    // ... resto del código existente
}
```

---

### 3. UI: Toggle de Conexión

Agregar a la pantalla principal de E.M.M.A.:

```kotlin
// En tu ViewModel o similar
val isConnected by viewModel.isConnected.collectAsState()

// En tu Composable o Layout
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(
        imageVector = if (isConnected) Icons.Default.Circle else Icons.Default.Circle,
        contentDescription = null,
        tint = if (isConnected) Color.Green else Color.Gray
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        text = if (isConnected) "Conectado a Hermes" else "Desconectado"
    )
    Spacer(modifier = Modifier.weight(1f))
    Switch(
        checked = isConnected,
        onCheckedChange = { connect ->
            if (connect) {
                // Mostrar diálogo de configuración del servidor
                showDialog = true
            } else {
                viewModel.disconnectFromHermes()
            }
        }
    )
}
```

---

### 4. Diálogo de Configuración

```kotlin
@Composable
fun HermesConnectionDialog(
    onDismiss: () -> Unit,
    onConnect: (serverUrl: String, token: String) -> Unit
) {
    var serverUrl by remember { mutableStateOf("wss://") }
    var token by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conectar con Hermes") },
        text = {
            Column {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("URL del servidor") },
                    placeholder = { Text("wss://tu-servidor.com/mobile/ws") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token de autenticación") },
                    placeholder = { Text("tu-secret-key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConnect(serverUrl, token) }) {
                Text("Conectar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
```

---

### 5. Almacenamiento de Configuración

Guardar URL y token para reconexión automática:

```kotlin
// Usar DataStore para persistencia
class HermesPreferences(context: Context) {
    private val dataStore = context.createDataStore("hermes_prefs")
    
    val serverUrl: Flow<String> = dataStore.data
        .map { it[PreferencesKeys.SERVER_URL] ?: "" }
    
    val token: Flow<String> = dataStore.data
        .map { it[PreferencesKeys.TOKEN] ?: "" }
    
    suspend fun saveConfig(url: String, token: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SERVER_URL] = url
            prefs[PreferencesKeys.TOKEN] = token
        }
    }
    
    private object PreferencesKeys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token")
    }
}
```

---

### 6. Reconexión Automática

```kotlin
class HermesTunnelClient(...) {
    // ... código existente
    
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelay = 5_000L  // 5 segundos
    
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        onError?.invoke("Error de conexión: ${t.message}")
        
        // Reintentar conexión si no fue cierre manual
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            scope.launch {
                delay(reconnectDelay)
                connect()
            }
        }
    }
    
    override fun onOpen(webSocket: WebSocket, response: Response) {
        reconnectAttempts = 0  // Reset al conectar exitosamente
        onConnected?.invoke()
    }
}
```

---

## Protocolo de Mensajes

### De E.M.M.A. a Hermes

```kotlin
// Ping (keepalive)
tunnelClient.ping()

// Mensaje genérico
tunnelClient.sendMessage(JSONObject().apply {
    put("text", "Hola Hermes!")
})

// Tarea A2A
tunnelClient.sendA2ATask(JSONObject().apply {
    put("id", "task-123")
    put("type", "query")
    put("content", "Analiza este documento")
    put("artifacts", JSONArray().apply {
        put(JSONObject().apply {
            put("type", "file")
            put("uri", "file:///path/to/doc.pdf")
        })
    })
})

// Estado del dispositivo
tunnelClient.sendStatus(JSONObject().apply {
    put("battery", 85)
    put("network", "wifi")
    put("location", JSONObject().apply {
        put("lat", 19.4326)
        put("lon", -99.1332)
    })
})
```

### De Hermes a E.M.M.A.

```kotlin
// Configurar handler antes de conectar
tunnelClient.onMessage = { data ->
    val event = data.optString("event")
    
    when (event) {
        "notification" -> {
            // Mostrar notificación al usuario
            val text = data.optString("text")
            showNotification(text)
        }
        
        "task_request" -> {
            // Hermes solicita una tarea al agente móvil
            val task = data.optJSONObject("task")
            processTask(task)
        }
        
        "task_complete" -> {
            // Una tarea del usuario completada
            val taskId = data.optString("task_id")
            val result = data.optString("result")
            showTaskResult(taskId, result)
        }
    }
}
```

---

## Flujo Completo de Uso

```
┌──────────────────────────────────────────────────────────────────┐
│                    ESCENARIO: NOTIFICACIÓN PUSH                  │
└──────────────────────────────────────────────────────────────────┘

1. Usuario abre E.M.M.A.
2. Usuario activa toggle "Conectar con Hermes"
3. E.M.M.A. conecta WebSocket
4. Usuario hace otra cosa (app en background)
5. Hermes completa una tarea larga
6. Hermes envía mensaje por WebSocket:
   {"type": "message", "data": {"event": "task_complete", ...}}
7. E.M.M.A. recibe mensaje (Foreground Service mantiene conexión)
8. E.M.M.A. muestra notificación local
9. Usuario toca notificación, ve resultado

┌──────────────────────────────────────────────────────────────────┐
│              ESCENARIO: COMUNICACIÓN A2A BIDIRECCIONAL           │
└──────────────────────────────────────────────────────────────────┘

1. Usuario: "Analiza este documento en mi servidor"
2. E.M.M.A. envía tarea A2A:
   {"type": "a2a", "task": {"id": "t1", "type": "analyze", ...}}
3. Ejecuta análisis local (si tiene capacidad)
4. Si necesita más capacidad:
   - Envía tarea a Hermes via WebSocket
   - Hermes ejecuta con más recursos
   - Hermes envía resultado de vuelta
5. E.M.M.A. muestra resultado al usuario
```

---

## Consideraciones Importantes

### Foreground Service

Para mantener la conexión en background:

```kotlin
class TunnelService : Service() {
    private var tunnelClient: HermesTunnelClient? = null
    
    override fun onCreate() {
        super.onCreate()
        // Crear notificación de foreground
        val notification = createNotification()
        startForeground(1, notification)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Conectar WebSocket
        tunnelClient?.connect()
        return START_STICKY
    }
    
    override fun onDestroy() {
        tunnelClient?.disconnect()
        super.onDestroy()
    }
    
    // ... implementar createNotification()
}
```

### Permisos Necesarios

Agregar a `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### Optimización de Batería

- Usar `START_STICKY` pero reconectar con backoff exponencial
- Enviar heartbeats cada 30 segundos (no más frecuente)
- Pausar reconexiones si el usuario desactiva el toggle

---

## Testing

### Prueba Manual con wscat

```bash
# Instalar wscat
npm install -g wscat

# Conectar
wscat -c "ws://localhost:8643/mobile/ws" \
  -H "Authorization: Bearer tu-secret-key" \
  -H "X-Device-ID: test-device-001"

# Enviar mensaje
> {"type": "ping"}
< {"type": "pong", "timestamp": 1234567890}

# Ver desde Hermes
curl http://localhost:8643/mobile/devices -H "Authorization: Bearer tu-secret-key"
```

### Prueba desde E.M.M.A.

```kotlin
// En unidad de test
@Test
fun testTunnelConnection() = runTest {
    val client = HermesTunnelClient(
        serverUrl = "ws://localhost:8643/mobile/ws",
        deviceId = "test-device",
        token = "test-key"
    )
    
    var received = false
    client.onConnected = { received = true }
    
    client.connect()
    delay(2000)  // Esperar conexión
    
    assertTrue(received)
    assertTrue(client.isConnected())
    
    client.disconnect()
    assertFalse(client.isConnected())
}
```

---

## Checklist de Implementación

- [ ] Crear `HermesTunnelClient.kt`
- [ ] Agregar toggle en UI de E.M.M.A.
- [ ] Implementar diálogo de configuración
- [ ] Agregar Foreground Service para background
- [ ] Persistir configuración (DataStore)
- [ ] Integrar con EmmaEngine para routing de mensajes
- [ ] Agregar manejo de errores y reconexión
- [ ] Probar con servidor Hermes
- [ ] Agregar tests unitarios
- [ ] Documentar en README de E.M.M.A.

---

## Contacto

Para dudas sobre la implementación:
- Skill: `mobile-tunnel` en Hermes
- Código Hermes: `gateway/platforms/mobile_tunnel.py`
- Especificación: Este documento

---

*Documento generado para integración E.M.M.A. - Hermes Mobile Tunnel*
*Versión: 1.0*