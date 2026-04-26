# 04 — Calidad de código y tests

---

## L-03 · Cobertura de tests minúscula

- **Tests existentes:** 1 archivo (`app/src/test/java/com/beemovil/vision/VisionIntegrationTest.kt`, 180 líneas).
- **Cubre:** `VisionAssessor`, `EmergencyProtocol` (parcial), parsers puros.
- **No cubre:**
  - 38 plugins (incluyendo los que tocan filesystem, contactos, llamadas a APIs externas).
  - Lógica del LLM router (`EmmaEngine`).
  - `MicrophoneArbiter` (concurrencia crítica, lifecycle de mic).
  - `ConversationEngine` (state machine).
  - `CrossContextEngine` (compresión de contexto).
  - `SecurityGate` (toda la capa, que está rota — un test simple lo habría detectado).
  - DAOs de Room (ChatHistoryDao, TelemetryDao).
  - Parsers de respuesta LLM (formato OpenAI vs Gemini vs Ollama).

**Propuesta:**
- Tests unitarios prioritarios:
  1. `SecurityGate.evaluate()` con mocks de handler (detectaría C-03).
  2. `MicrophoneArbiter` (preempción, prioridad, zombie timeout).
  3. `CrossContextEngine.compressToParagraph()` (no pasa-injection y respeta MAX_PARAGRAPH_CHARS).
  4. Parser de respuestas LLM (OpenAI/Gemini/Ollama).
  5. SecurePrefs migración (verificar que después de migrar, el plain pref está vacío).
- Tests de instrumentación:
  - DB migrations 1→2→3 con datos sintéticos.
  - Lifecycle de servicios foreground.

---

## L-08 · Excepciones silenciosas (cubierto en 01)

41 catch vacíos. Esto deteriora drásticamente la mantenibilidad — bugs silenciosos en producción.

---

## L-04 · Lint deshabilitado para release

**Archivo:** `app/build.gradle.kts:74-77`

```kotlin
lint {
    abortOnError = false
    checkReleaseBuilds = false
}
```

Comentario en el build dice "Prevent lint from blocking release builds". Esto significa que warnings/errors del Android Lint no detienen la build. Algunos catches son legítimos pero esta configuración los oculta TODOS.

**Propuesta:**
- Habilitar `abortOnError = true` para CI.
- Crear `lint-baseline.xml` con `./gradlew updateLintBaseline` para grandfather los warnings actuales.
- Nuevos warnings rompen build → forzar fix conforme se introducen.

---

## I-calidad · Tipos de strings escapados mal

Aparte de `CodeSandboxPlugin` (L-01) y `ContactManagerPlugin` (H-08), un `grep` rápido encuentra otros candidatos a revisar:

```
$ grep -rn "\\\\\\\$" app/src/main/java --include="*.kt" | head -10
```

Recomiendo búsqueda manual de `\$` y `\${` en todo el repo y verificar que ninguno debería ser interpolación.

---

## I-calidad · Singletons globales

Patrón recurrente:
- `MicrophoneArbiter` (object)
- `SecurityGate` (object con `confirmationHandler` mutable)
- `BeeHttpClient` (object con clientes lazy)
- `BeeThemeState` (mutable global)

Singletons globales hacen difícil:
- Testear (no se pueden inyectar mocks).
- Reset entre tests.
- Configurar variantes (debug vs release).

**Propuesta:** Migrar gradualmente a inyección por DI (Hilt o Koin) — al menos para `MicArbiter` y `SecurityGate`. No urgente, pero impacta tests.

---

## I-calidad · Comentarios en español + inglés mezclados

El código alterna español ("Infiltrando agenda nativa", "Catástrofe de Red") con inglés. Bien para una app cuyos usuarios hablan español, pero los TAGs de log y mensajes técnicos serían más fáciles de buscar si fueran inglés. Cuestión de estilo.

---

## I-calidad · TODOs en strings de prompts

Varios `TODO` aparecen como **palabra del idioma** ("Absolutamente TODO el código del sitio") en descripciones de tools, no como marcadores. La búsqueda `grep -E TODO` da falsos positivos.

---

## I-calidad · Plugins mezclando responsabilidades

Algunos plugins (`SystemGodModePlugin`, `WhatsAppAutomatorPlugin`, `WebApiFetcherPlugin`) tienen la lógica de:
1. Parseo de args
2. Validación
3. Construcción de Intent/HTTP
4. Ejecución
5. Mensaje de respuesta al LLM

Todo en un `execute()` largo. Cuesta auditar permisos y validación. Considerar separar en helpers (`buildIntent()`, `validate()`, `executeNetwork()`) para cada plugin grande.

---

## I-calidad · Nombres de paquete legacy "beemovil"

`namespace = "com.beemovil"`, `applicationId = "com.beemovil.emma"`. La marca actual es E.M.M.A. AI pero el paquete sigue como "beemovil". Migrar el applicationId rompe Play Store; pero los nombres de paquete internos (`com.beemovil.*`) podrían moverse a `com.emma.*` con un refactor (riesgo: rompe ProGuard rules).

No urgente, solo es una pequeña deuda técnica.

---

## Métricas (referencia)

- LOC Kotlin: 35,612
- Archivos Kotlin: 149
- Plugins: 38 builtins
- Pantallas Compose: 12 archivos en `ui/screens/`
- Servicios foreground: 4 (TunnelService, TelegramBotService, EmmaTaskService, PocketVisionService, WakeWordService — total 5)
- DBs Room: 2 (ChatHistory v3, Telemetry v2)
- Permisos manifest: 31

App grande para mobile. Para una sola persona/equipo pequeño manteniéndola, **la cobertura de tests es el mayor riesgo de regresiones a futuro**.
