# 01 — Funcionalidad y bugs

Hallazgos sobre lógica, lifecycle, concurrencia, errores. Excluye seguridad pura (ver `02_SEGURIDAD.md`).

---

## C-03 · SecurityGate sin handler asignado — toda la capa de confirmaciones está rota

**Severidad:** Crítico (bug + seguridad)

**Archivos:**
- `app/src/main/java/com/beemovil/plugins/SecurityGate.kt:50` (declaración)
- Sin asignación en ningún archivo del repo

**Evidencia:**
```kotlin
// SecurityGate.kt:48-67
@Volatile
var confirmationHandler: ConfirmationHandler? = null

suspend fun evaluate(operation: SecureOperation): Boolean {
    return when (operation.level) {
        Level.GREEN -> true
        Level.YELLOW -> confirmationHandler?.requestConfirmation(operation) ?: true   // auto-aprueba
        Level.RED    -> confirmationHandler?.requestConfirmation(operation) ?: false  // auto-bloquea
    }
}
```

`grep -rE "ConfirmationHandler" app/src/main/java` solo encuentra la definición; **nadie asigna el handler**.

**Impacto:**
- Operaciones YELLOW (move file, rename file, organize folder, create/delete schedule, open app, open settings, set brightness, toggle DND, set wallpaper desde URL, leer clipboard, create/edit/clone agente, change model LLM) → ejecutan sin pedir confirmación.
- Operaciones RED (delete file, delete agent, update API key) → siempre devuelven "Cancelado", incluso si el usuario quiere usarlas legítimamente.

**Propuesta:**
- Implementar un `ConfirmationHandler` en `MainActivity` o `ChatViewModel` que muestre un diálogo Compose y suspenda hasta respuesta del usuario.
- Asignarlo en `MainActivity.onCreate` antes de inicializar el motor.
- Cambiar el fallback de YELLOW a `false` (negar por defecto, no aprobar).

---

## H-08 · Bug SQL en ContactManagerPlugin: placeholder literal

**Severidad:** Alto (bug funcional)

**Archivo:** `app/src/main/java/com/beemovil/plugins/builtins/ContactManagerPlugin.kt:43`

**Evidencia:**
```kotlin
val selection = "\${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
```

En Kotlin, `"\${...}"` es escape: la cadena queda literal `${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?`. ContentResolver recibe ese string como columna inexistente.

**Impacto:** Búsqueda de contactos siempre lanza excepción o devuelve 0 resultados. Plugin no funciona.

**Propuesta:** Quitar el backslash: `"${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"`.

---

## L-01 · Strings rotos en CodeSandboxPlugin

**Severidad:** Bajo (bug cosmético, afecta debug)

**Archivo:** `app/src/main/java/com/beemovil/plugins/builtins/CodeSandboxPlugin.kt:39, 52, 86-93`

**Evidencia:**
```kotlin
Log.d(TAG, "Iniciando Rhino Sandbox Task:\n\$script")            // imprime literal "$script"
Log.d(TAG, "Resultado Sandbox: \$result")                          // idem
if (stdoutStr.isNotBlank()) append("Salida Console:\\n\$stdoutStr\\n")
"Excepción en Sandbox: \${e.message}"
```

**Impacto:** Logs y output del sandbox muestran `$result`, `${e.message}` literal en lugar del valor. Imposible diagnosticar errores reales del sandbox.

**Propuesta:** Quitar todos los `\$` → `$` y `\${` → `${`.

---

## M-02 · `runBlocking` en rutas calientes

**Severidad:** Medio

**Archivos:**
- `app/src/main/java/com/beemovil/core/engine/EmmaEngine.kt:165` — `runBlocking(Dispatchers.IO)` dentro del init para construir el "context paragraph". Bloquea durante arranque.
- `app/src/main/java/com/beemovil/vision/VisionCaptureLoop.kt:185` — `runBlocking { BarcodeScanner.scan(...) }` dentro del loop de frames de cámara. ML Kit es asíncrono; el runBlocking serializa todo el pipeline de captura.

**Impacto:**
- ANR posible si `initialize()` se llama desde Main thread.
- Pérdida de frames y latencia en el modo Vision/Shopping.
- Defeats coroutines: si la coroutine padre se cancela, `runBlocking` no la propaga.

**Propuesta:**
- Cambiar `runBlocking` por `withContext(Dispatchers.IO)` y hacer la función `suspend`.
- En el captureLoop: usar `await()` sobre el Task de ML Kit dentro de la misma coroutine.

---

## M-03 · MicrophoneArbiter zombie timeout corta conversaciones largas

**Severidad:** Medio

**Archivo:** `app/src/main/java/com/beemovil/voice/MicrophoneArbiter.kt:27, 200-209`

**Evidencia:**
```kotlin
private const val ZOMBIE_TIMEOUT_MS = 60_000L
...
zombieJob = scope.launch {
    delay(ZOMBIE_TIMEOUT_MS)
    val stillHeld = currentOwner.get()
    if (stillHeld == owner) {
        Log.w(TAG, "ZOMBIE DETECTED: $owner ... — auto-releasing")
        releaseMic(owner)
    }
}
```

**Impacto:** Un `MicOwner.CONVERSATION` que dure más de 60s (conversación natural larga, voz lenta, latencia LLM alta) pierde el mic en mitad de turno. El usuario sigue hablando y la app deja de escuchar sin razón visible.

**Propuesta:**
- Renovar el timeout cuando se detecta actividad (transcripción parcial, audio recibido).
- Opcional: dar timeout distinto por owner (`WAKE_WORD: 5min`, `CONVERSATION: ilimitado mientras haya audio`, `PUSH_TO_TALK: 30s`).

---

## M-04 · WakeWordService — wake locks y timing mágico

**Severidad:** Medio

**Archivo:** `app/src/main/java/com/beemovil/service/WakeWordService.kt:101-111, 172-180`

**Hallazgos:**
1. `wakeLock.acquire(5000)` sin `release()` explícito (línea 108). Funciona porque tiene timeout, pero deja al Power Manager registrando uso sin necesidad.
2. `Handler(mainLooper).postDelayed({ wakeEngine?.start(...) }, 20000)` reinicia el motor 20s después. Si la conversación termina en 5s, hay 15s de "agujero" donde "Hello Emma" no se detecta. Si la conversación dura 30s, el `start()` se llama mientras `CONVERSATION` aún tiene el mic → conflicto vía arbiter (denegado, log warning, pero motor queda en estado raro).

**Impacto:** Wake word no responde durante ~20s después de cada activación. UX inconsistente. Posible doble activación si el motor lanza múltiples callbacks.

**Propuesta:**
- Suscribirse a `MicrophoneArbiter.onMicReleased` en lugar de un timer fijo. Reiniciar el motor cuando el mic queda libre (`MicOwner.NONE`).
- Aplicar de-bounce a `onWakeDetected` (ej. ignorar detecciones <2s después de la última).
- Liberar wake lock explícitamente en `onDestroy` por si fuera reusable.

---

## M-05 · WakeWordService bypass de Do Not Disturb por defecto

**Severidad:** Medio (UX)

**Archivo:** `app/src/main/java/com/beemovil/service/WakeWordService.kt:140-160`

**Evidencia:**
```kotlin
val channel = NotificationChannel(alertChannel, ..., NotificationManager.IMPORTANCE_HIGH).apply {
    setBypassDnd(true)
    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
}
...
.setCategory(NotificationCompat.CATEGORY_CALL)
.setFullScreenIntent(pendingIntent, true)
```

**Impacto:** Una falsa detección de wake word (ruido ambiental que se parezca a "Hello Emma") despierta la pantalla y suena con prioridad llamada, ignorando DND. Molesto en cine, junta, dormido.

**Propuesta:**
- Hacer opcional el bypass DND vía toggle en Settings (default off).
- Reducir `IMPORTANCE_HIGH` a `IMPORTANCE_DEFAULT` salvo que el usuario lo eleve.

---

## M-06 · `confirmationHandler` global single-instance no es thread-safe en escritura concurrente

**Severidad:** Medio

**Archivo:** `app/src/main/java/com/beemovil/plugins/SecurityGate.kt:50`

@Volatile garantiza visibilidad pero no atomicidad de "comprobar y asignar". Si dos pantallas (por ejemplo Chat y LiveVision) intentan instalar su handler simultáneamente, una sobrescribe a la otra. La sobrescrita queda con un handler de una pantalla que ya no existe → memory leak + invocación a UI muerta.

**Propuesta:**
- Usar lista de handlers en lugar de single var, o
- Usar `LifecycleObserver` para auto-quitar el handler en `ON_DESTROY`.

---

## M-07 · MicArbiter callbacks single-listener globales

**Severidad:** Medio

**Archivo:** `app/src/main/java/com/beemovil/voice/MicrophoneArbiter.kt:43-47`

```kotlin
var onMicAcquired: ((MicOwner, String) -> Unit)? = null
var onMicReleased: ((MicOwner) -> Unit)? = null
var onMicDenied: ((MicOwner, String) -> Unit)? = null
var onWakeWordPaused: (() -> Unit)? = null
var onWakeWordResumed: (() -> Unit)? = null
```

Single-value listeners. Si dos componentes los necesitan (Chat + LiveVision + WakeWordService), se pisan. El último gana, los demás quedan ciegos.

**Propuesta:** Convertir a `SharedFlow<MicEvent>` o lista de listeners.

---

## L-08 · Excepciones silenciosas (41 catch vacíos)

**Severidad:** Bajo (mantenibilidad)

`grep -rE "catch \(_?:?\s*Exception\)\s*\{\s*\}" app/src/main/java --include="*.kt" | wc -l` → **41**

Muchos casos en plugins, vision, voice, telemetry. Cuando algo falla, no se loggea, no se reporta al usuario, y simplemente se continúa con un valor por defecto.

**Impacto:** Bugs silenciosos. Imposible diagnosticar fallos en producción.

**Propuesta:** Reemplazar `catch (_: Exception) {}` por `catch (e: Exception) { Log.w(TAG, "...", e) }`. Idealmente, manejar errores específicos donde tenga sentido.

---

## I-funcionalidad · ConversationEngine — observaciones

**Archivo:** `app/src/main/java/com/beemovil/voice/ConversationEngine.kt`

- `scope = CoroutineScope(Dispatchers.Main + SupervisorJob())` — vive con el ConversationEngine, se cancela en `stop()`. OK.
- `start()` (línea 70): el comentario dice "Acquire mic through arbiter" pero si falla ya hay `state` en valor previo: si quedaba en `ConversationState.ERROR`, no se resetea antes del fallo. Cosmético.

---

## I-funcionalidad · ChatHistoryDB tiene migraciones, pero el TelemetryDatabase no

**Archivo:** `app/src/main/java/com/beemovil/telemetry/TelemetryDatabase.kt`

Usa `.fallbackToDestructiveMigration()`. En upgrade de versión la tabla `telemetry_log` se borra. Si la app crece y este DB acumula datos importantes, se perderán.

**Propuesta:** Agregar `Migration` explícita o documentar que el log es desechable.
