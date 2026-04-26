# 05 — Propuesta de acción priorizada

**Modo:** propuesta. No se aplica nada de esto sin tu visto bueno explícito.
**Notación:** esfuerzo {S=horas, M=día, L=multi-día} · riesgo {bajo/medio/alto} · impacto {bajo/medio/alto/crítico}.

---

## SPRINT 0 — Antes de cualquier release pública (parar todo)

| # | Tarea | Esfuerzo | Riesgo | Impacto | ID |
|---|------|---------|---------|--------|----|
| 1 | Rotar el keystore. Generar nuevo `.jks` con contraseña fuerte. Asumir el viejo comprometido. | S | bajo | crítico | C-01 |
| 2 | Reescribir git history para borrar `keystore.properties` del commit `8c8c8db`. Force-push. Notificar a forks. | S | medio (rompe forks) | crítico | C-01 |
| 3 | Cambiar fallback de signing a fallar duro: `?: throw GradleException(...)`. | S | bajo | crítico | C-02 |
| 4 | Implementar `ConfirmationHandler` real en `MainActivity` con diálogo Compose. Asignarlo a `SecurityGate.confirmationHandler` en `onCreate`. Cambiar el fallback YELLOW a `false`. | M | bajo | crítico | C-03 |

**Resultado:** sin estos 4 puntos, una build pública es peligrosa. Con ellos, la app vuelve a un nivel "decente para beta".

---

## SPRINT 1 — Hardening de la superficie LLM (la semana siguiente)

| # | Tarea | Esfuerzo | Riesgo | Impacto | ID |
|---|------|---------|---------|--------|----|
| 5 | `WebApiFetcherPlugin`: allowlist de hosts, bloquear IPs privadas/loopback, pasar por SecurityGate (YELLOW), forzar HTTPS, limitar tamaño body. | M | bajo | crítico | C-04 |
| 6 | `BrowserChatPanel`: quitar `javaScriptEnabled = true` o cambiar a Custom Tabs. Allowlist + confirmación. | S | bajo | crítico | C-05 |
| 7 | `SecurePrefs`: no silenciar fallos. Si EncryptedSharedPreferences falla, lanzar al UI y marcar flag. No borrar archivo cifrado existente. | S | medio (afecta usuarios con state corrupto) | crítico | C-06 |
| 8 | `CrossContextEngine`: envolver inputs externos en delimitadores y advertir al LLM en system prompt. Filtrar patrones obvios de injection. Limitar largo por campo. | M | bajo | alto | H-01 |
| 9 | `WebSearchPlugin` y otros consumidores: mismo tratamiento de delimitadores. | S | bajo | alto | H-02 |
| 10 | `GeminiLiveBackend`: borrar logs de prefijos de keys. Solo loguear `present/len`. | S | bajo | alto | H-03 |
| 11 | `DiagnosticsPlugin.read_logs`: mover a YELLOW. Filtrar regex de keys/tokens antes de devolver. | S | bajo | alto | H-04 |

**Resultado:** prompt-injection deja de ser vector trivial. El LLM puede pedir, pero no actuar sin confirmación.

---

## SPRINT 2 — Permisos y privacidad

| # | Tarea | Esfuerzo | Riesgo | Impacto | ID |
|---|------|---------|---------|--------|----|
| 12 | Quitar `MANAGE_EXTERNAL_STORAGE` del manifest. | S | bajo | alto | H-05 |
| 13 | Auditar cada permiso vs uso real. Eliminar los que no se usan. Documentar el resto. | M | bajo | alto | H-06 |
| 14 | Política de privacidad pública. Onboarding con texto claro de qué datos viajan al LLM. | M | bajo | alto | H-06 |
| 15 | Agregar `network_security_config.xml` con `cleartextTrafficPermitted=false` y dominios trusted. | S | bajo | medio | H-07 |
| 16 | `CertificatePinner` para los 4 hosts críticos (Gemini, OpenRouter, Telegram, Google APIs). Plan de rotación de pins. | M | medio | medio | H-07 |

---

## SPRINT 3 — Bugs funcionales

| # | Tarea | Esfuerzo | Riesgo | Impacto | ID |
|---|------|---------|---------|--------|----|
| 17 | `ContactManagerPlugin`: arreglar SQL placeholder (quitar `\$`). | S | bajo | alto | H-08 |
| 18 | `CodeSandboxPlugin`: arreglar todos los `\$` y `\${` literales. | S | bajo | bajo | L-01 |
| 19 | `MicrophoneArbiter`: timeout renovable por actividad, distinto por owner. | S | medio | medio | M-03 |
| 20 | `WakeWordService`: liberar wake lock explícitamente; reiniciar motor por evento, no por timer. De-bounce. | M | bajo | medio | M-04 |
| 21 | `WakeWordService`: hacer opcional el bypass DND. | S | bajo | medio | M-05 |
| 22 | `MicArbiter` y `SecurityGate`: callbacks → SharedFlow o lista. | M | bajo | medio | M-06, M-07 |
| 23 | Eliminar `runBlocking` en `EmmaEngine.initialize()` y `VisionCaptureLoop`. | M | medio (cambia firmas) | medio | M-02 |

---

## SPRINT 4 — Calidad y deuda técnica

| # | Tarea | Esfuerzo | Riesgo | Impacto | ID |
|---|------|---------|---------|--------|----|
| 24 | Reemplazar 41 catch vacíos por log + handling apropiado. | M | bajo | bajo | L-08 |
| 25 | Habilitar lint en release con baseline. CI gating. | S | bajo | bajo | L-04 |
| 26 | Catálogo de versiones (`libs.versions.toml`). | S | bajo | bajo | I-deps |
| 27 | OWASP Dependency-Check en CI. | S | bajo | bajo | I-deps |
| 28 | Tests unitarios prioritarios: SecurityGate, MicArbiter, CrossContextEngine, parsers LLM. | L | bajo | alto | L-03 |
| 29 | Migrar a versiones estables de `androidx.security` y `androidx.credentials` cuando salgan. | S | bajo | bajo | L-07 |

---

## Matriz de decisión rápida

| Si tienes ... | Empieza por |
|--------------|-------------|
| 4 horas hoy | C-01 (rotar keystore) + C-02 (signing fallback) |
| 1 día | + C-03 (handler de SecurityGate) |
| 1 semana | + Sprint 1 entero (hardening LLM) |
| 1 mes | + Sprint 2 + 3 |
| 1 trimestre | + Sprint 4 (tests son el mayor escudo a futuro) |

---

## Lo que NO recomiendo hacer

- **No reescribir el SecurityGate desde cero.** El diseño de niveles GREEN/YELLOW/RED es bueno; solo falta el handler. Implementar el handler es más rápido y menos riesgoso.
- **No quitar todos los plugins peligrosos.** Son la propuesta de valor. Mejor: meterlos en un grupo "advanced" que el usuario active explícitamente con un toggle de "yo asumo el riesgo de IA con autonomía".
- **No agregar más fuentes al `CrossContextEngine` antes de arreglar la injection.** Hoy mete email/calendar/tasks; agregar SMS/WhatsApp/llamadas amplifica el problema.
- **No publicar a Play Store sin Sprint 0 completo.** El `MANAGE_EXTERNAL_STORAGE` y la firma debug solos pueden ser causa de rechazo.

---

## Notas finales

Este reporte cubre lo que vi en lectura estática del código. Cosas que **NO se verificaron** por la regla de "solo lectura, sin modificar":

- No se ejecutó la app, así que no se observó comportamiento real de:
  - Wake word detection en condiciones reales (false positives)
  - Latencia de Gemini Live REST (vs WebSocket)
  - Battery drain con servicios foreground
  - Ningún path con Internet activo
- No se corrió `./gradlew lint` ni `./gradlew dependencyCheckAnalyze` (requiere build).
- No se ejecutó MobSF u otro scanner de APK.
- No se validó la firma actual del APK ni se inspeccionó el AAR final.
- No se hizo análisis dinámico (Frida, network inspector).

Si quieres, en una segunda ronda puedo:
- Correr lint y dependency-check (con build, no modifica fuente).
- Generar un APK debug y pasarlo por MobSF para análisis estático profundo.
- Hacer auditoría manual más profunda a un subsistema específico (Vision, Voice, Plugins, etc.).
