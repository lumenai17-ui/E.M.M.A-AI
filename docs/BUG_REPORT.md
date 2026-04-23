# Reporte de Revisión de Bugs: E.M.M.A. AI v2

**Fecha:** 21 Abril 2026
**Auditor:** Claude Code Bug Analysis
**Alcance:** Código fuente Kotlin (UI, engine, plugins, providers, servicios)
**Metodología:** Revisión estática de código, análisis de lógica, detección de race conditions, memory leaks, y errores funcionales.

---

## RESUMEN EJECUTIVO

Se identificaron **15 bugs y problemas funcionales**, incluyendo memory leaks, race conditions, lógica incorrecta en manejo de estados, potenciales crashes, y problemas de concurrencia. La mayoría son de severidad media, pero algunos pueden causar crashes o comportamiento errático en producción.

---

## BUGS ENCONTRADOS

### BUG-01: Memory Leak en `LocalGemmaProvider` — Contexto de aplicación almacenado estáticamente
**Archivo:** `app/src/main/java/com/beemovil/llm/local/LocalGemmaProvider.kt:69`
**Severidad:** MEDIA-ALTA

```kotlin
// Store application context for initialization
var appContext: Context? = null
```

El `appContext` es una variable estática que puede retener una referencia a un `Activity` si se asigna incorrectamente desde una pantalla. Esto causará un memory leak si la Activity se destruye pero el contexto sigue referenciado.

**Fix:**
```kotlin
// Use Application context explicitly
lateinit var applicationContext: android.app.Application
```
Y asegurarse de que solo se asigne `applicationContext` desde `Application.onCreate()`.

---

### BUG-02: Race Condition en `EmmaEngine` — `synchronized` + `Mutex` sobre la misma colección
**Archivo:** `app/src/main/java/com/beemovil/core/engine/EmmaEngine.kt`
**Severidad:** MEDIA

Como se documentó en la auditoría de seguridad, `EmmaEngine` usa dos mecanismos de sincronización diferentes (`synchronized(messagesHistory)` y `historyMutex.withLock`) sobre `messagesHistory`. Esto permite que un hilo con `synchronized` y una corrutina con `Mutex` accedan simultáneamente, causando corrupción de estado.

**Fix:** Reemplazar todos los `synchronized(messagesHistory)` por `historyMutex.withLock { }`.

---

### BUG-03: `EmailService` — No cierra conexiones IMAP/SMTP en caso de error
**Archivo:** `app/src/main/java/com/beemovil/email/EmailService.kt`
**Severidad:** MEDIA

En los métodos `fetchInbox()` y `sendEmail()`, las conexiones `Store` y `Folder` se abren pero no se cierran explícitamente en caso de excepción. Esto puede agotar los recursos del pool de conexiones de JavaMail.

**Fix:** Usar `use` o `try-finally` para cerrar `Folder` y `Store`.

---

### BUG-04: `ChatViewModel` — `sendMessage()` no maneja excepciones del motor
**Archivo:** `app/src/main/java/com/beemovil/ui/ChatViewModel.kt`
**Severidad:** MEDIA

```kotlin
fun sendMessage(userText: String, fileUri: String? = null) {
    viewModelScope.launch {
        // ...
        val response = engine.sendMessage(processed, agentId = activeAgentId.value)
        // Si engine.sendMessage() lanza excepción, la corrutina falla silenciosamente
        // y el usuario no ve feedback de error
    }
}
```

Si `engine.sendMessage()` lanza una excepción no capturada, la corrutina se cancela y el usuario queda esperando sin ver ningún mensaje de error.

**Fix:** Agregar `try-catch` alrededor de `engine.sendMessage()` y mostrar un mensaje de error al usuario.

---

### BUG-05: `TelegramBotService` — `autoRegisterChatId` persiste chat IDs sin autenticación
**Archivo:** `app/src/main/java/com/beemovil/telegram/TelegramBotService.kt`
**Severidad:** MEDIA

```kotlin
private fun autoRegisterChatId(chatId: Long) {
    val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
    val current = prefs.getString(PREF_ALLOWED_CHATS, "") ?: ""
    val ids = if (current.isBlank()) mutableSetOf() else current.split(",").toMutableSet()
    ids.add(chatId.toString())
    prefs.edit().putString(PREF_ALLOWED_CHATS, ids.joinToString(",")).apply()
}
```

Cualquier chat ID que pase una sola vez por `isAuthorized` queda persistido permanentemente. Si un atacante logra pasar la autorización una sola vez (ej. owner sin username configurado), su chat ID queda guardado para siempre.

**Fix:** Eliminar `autoRegisterChatId` o requerir confirmación explícita del owner.

---

### BUG-06: `OpenAiCompatibleProvider` — No cierra `response.body` cuando la respuesta no es exitosa
**Archivo:** `app/src/main/java/com/beemovil/llm/OpenAiCompatibleProvider.kt:56-72`
**Severidad:** BAJA-MEDIA

```kotlin
val response = client.newCall(request).execute()
try {
    val responseBody = response.body?.string() ?: throw Exception("Empty response body")
    // ...
} finally {
    response.close()
}
```

Aunque hay un `finally` con `response.close()`, si `response.body` es null, no se cierra el body. Además, `response.close()` cierra el response pero no garantiza que el body se libere si no se leyó.

**Fix:** Usar `response.use { }` para asegurar liberación de recursos.

---

### BUG-07: `OllamaCloudProvider` — Timeout de red infinito para client interno
**Archivo:** `app/src/main/java/com/beemovil/llm/OllamaCloudProvider.kt:36`
**Severidad:** BAJA-MEDIA

```kotlin
private val client = BeeHttpClient.llm
```

Usa el cliente LLM compartido sin timeout específico. Si Ollama Cloud está caído o lento, la petición puede bloquearse indefinidamente.

**Fix:** Usar un cliente con timeout explícito para Ollama Cloud o implementar cancelación de peticiones.

---

### BUG-08: `DeepgramVoiceManager` — No reinicia `nativeSTT` después de error
**Archivo:** `app/src/main/java/com/beemovil/voice/DeepgramVoiceManager.kt:90-119`
**Severidad:** BAJA-MEDIA

```kotlin
override fun onError(error: Int) {
    onError?.invoke("Native STT Error: $error")
}
```

Si el reconocimiento nativo falla, no se reinicia el `nativeSTT`. El siguiente intento de uso fallará silenciosamente.

**Fix:** Reiniciar `nativeSTT = android.speech.SpeechRecognizer.createSpeechRecognizer(context)` en `onError`.

---

### BUG-09: `BeeMemoryDB` — Clave de memoria duplicada al guardar
**Archivo:** `app/src/main/java/com/beemovil/memory/BeeMemoryDB.kt:20-23`
**Severidad:** BAJA

```kotlin
fun saveMemory(memoryFragment: String) {
    val count = getMemoryCount()
    prefs.edit().putString("mem_$count", memoryFragment).apply()
}
```

`getMemoryCount()` devuelve `prefs.all.size`, que incluye TODAS las claves (incluyendo `soul_*`). Si hay 10 souls y 0 memories, `count = 10`, y la primera memoria se guarda como `mem_10` en lugar de `mem_0`.

**Fix:**
```kotlin
fun saveMemory(memoryFragment: String) {
    val count = prefs.all.keys.count { it.startsWith("mem_") }
    prefs.edit().putString("mem_$count", memoryFragment).apply()
}
```

---

### BUG-10: `LocalModelManager` — No verifica espacio antes de descargar (race condition)
**Archivo:** `app/src/main/java/com/beemovil/llm/local/LocalModelManager.kt:193-198`
**Severidad:** BAJA

```kotlin
val availableGB = getAvailableStorageGB()
val requiredGB = model.sizeBytes / 1_073_741_824.0
if (availableGB < requiredGB + 0.5) {
    onComplete(false, "Espacio insuficiente...")
    return
}
```

El espacio disponible se calcula en el hilo principal de llamada, pero la descarga ocurre en un `Thread`. Entre la verificación y el inicio de la descarga, otro proceso puede consumir espacio.

**Fix:** Es un race condition aceptable, pero podría manejarse mejor con bloqueo de descargas concurrentes.

---

### BUG-11: `ChatScreen` — `showMenuForMessage` usa el texto como clave, no es único
**Archivo:** `app/src/main/java/com/beemovil/ui/screens/ChatScreen.kt:61, 280, 367`
**Severidad:** BAJA

```kotlin
var showMenuForMessage by remember { mutableStateOf<String?>(null) }
// ...
onLongClick = { showMenuForMessage = msg.text }
// ...
expanded = showMenuForMessage == msg.text
```

Si dos mensajes tienen el mismo texto, el menú contextual se mostrará para ambos simultáneamente. Además, si el texto es largo o contiene caracteres especiales, puede haber problemas de comparación.

**Fix:** Usar un ID único del mensaje en lugar del texto.

---

### BUG-12: `MainActivity` — `handleIncomingShareIntent` puede lanzar excepción si `viewModel` no está listo
**Archivo:** `app/src/main/java/com/beemovil/MainActivity.kt:179-201`
**Severidad:** BAJA

```kotlin
viewModel.sendMessage(sharedText ?: "Archivo compartido desde otra app", sharedUri.toString())
```

Si el intent de share llega antes de que `viewModel` esté completamente inicializado, puede haber un crash.

**Fix:** Verificar que `viewModel` esté listo o encolar la acción.

---

### BUG-13: `LocalGemmaProvider` — Truncamiento de prompt puede romper formato Gemma
**Archivo:** `app/src/main/java/com/beemovil/llm/local/LocalGemmaProvider.kt:470-478`
**Severidad:** BAJA

```kotlin
if (prompt.length > promptCap) {
    val keepStart = minOf(prompt.length, promptCap / 3)
    val keepEnd = minOf(prompt.length, promptCap * 2 / 3)
    return prompt.take(keepStart) + "\n...[truncado]...\n" + prompt.takeLast(keepEnd)
}
```

El truncamiento puede cortar en medio de un `<start_of_turn>` o `<end_of_turn>`, dejando el formato Gemma roto y causando que el modelo genere basura.

**Fix:** Truncar por tokens o por delimitadores completos, no por caracteres brutos.

---

### BUG-14: `A2AServer` — Manejo de CORS OPTIONS no cierra socket correctamente
**Archivo:** `app/src/main/java/com/beemovil/a2a/A2AServer.kt:140-158`
**Severidad:** BAJA

```kotlin
"OPTIONS" -> {
    // ... headers ...
    writer.print("\r\n")
    writer.flush()
    return  // <-- Sale del método sin cerrar client.close()
}
```

El return prematuro en OPTIONS sale del `try` sin ejecutar `client.close()`, dejando sockets abiertos.

**Fix:** Mover el cierre del socket a un bloque `finally`.

---

### BUG-15: `EnvironmentScanner` — `getCurrentLocation()` no maneja `SecurityException`
**Archivo:** `app/src/main/java/com/beemovil/core/EnvironmentScanner.kt:44-59`
**Severidad:** BAJA

```kotlin
try {
    val fusedLocationClient = ...
    val location = locationTask.await()
} catch (e: Exception) {
    e.printStackTrace()
}
```

Si Google Play Services no está disponible, `getFusedLocationProviderClient` puede lanzar `SecurityException` o `IllegalStateException`.

**Fix:** Agregar manejo específico para `SecurityException`.

---

### BUG-16: `ModelRegistry` — `getModelOptions("openrouter")` puede devolver lista vacía
**Archivo:** `app/src/main/java/com/beemovil/llm/ModelRegistry.kt:228-230`
**Severidad:** BAJA

```kotlin
fun getModelOptions(provider: String): List<LlmFactory.ModelOption> {
    val entries = when (provider) {
        "openrouter" -> OPENROUTER
        // ...
    }
    return entries.map { it.toModelOption() }
}
```

Si `OPENROUTER` está vacío (ej. datos malformados), devuelve lista vacía y la app crashea al hacer `.first()` en SettingsScreen.

**Fix:** Garantizar que siempre haya al menos un modelo por defecto.

---

## RECOMENDACIONES GENERALES

1. **Implementar `CoroutineExceptionHandler`** en `ChatViewModel` para capturar excepciones no manejadas en corrutinas.
2. **Usar `LifecycleObserver`** para limpiar recursos de voz y cámara cuando la Activity se destruye.
3. **Agregar `@Synchronized` o `Mutex`** en operaciones de escritura de SharedPreferences que se usan concurrentemente.
4. **Implementar retry con backoff exponencial** en las llamadas a APIs de terceros.

---

## CONCLUSION

Los bugs encontrados son manejables. Los más críticos son:

1. **BUG-01** (Memory leak con contexto estático)
2. **BUG-02** (Race condition en EmmaEngine)
3. **BUG-04** (Excepciones no manejadas en sendMessage)
4. **BUG-05** (Auto-registro persistente en Telegram)

El resto son mejoras de robustez que reducirán crashes y comportamiento errático.

---

*Fin del Reporte de Bugs*
