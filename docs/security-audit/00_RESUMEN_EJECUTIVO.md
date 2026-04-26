# E.M.M.A. AI — Resumen ejecutivo de auditoría

**Repo:** `lumenai17-ui/E.M.M.A-AI`
**Commit auditado:** `4a6262d`
**Tamaño:** 35.6k LOC Kotlin · 149 archivos · 38 plugins · ~30 dependencias
**Modalidad:** auditoría estática solo lectura. Cero cambios al repo.

---

## Conteo por severidad

| Severidad | Cantidad |
|-----------|----------|
| Crítico   | 6 |
| Alto      | 8 |
| Medio     | 8 |
| Bajo      | 8 |
| Info      | 5 |

---

## Top 5 hallazgos urgentes

1. **C-01 · Contraseñas del keystore en git history.** Aunque `keystore.properties` se borró en `c8332733`, las contraseñas (`beemovil2026` para store y key) siguen en el commit `8c8c8db`. Cualquiera con acceso al repo (público) las recupera con `git log -p`.
2. **C-02 · Release builds firman con debug key cuando no hay keystore.** En `app/build.gradle.kts:71` el fallback usa `signingConfigs.getByName("debug")`, lo que firma una build "release" con la clave de debug pública.
3. **C-03 · `SecurityGate` no protege nada.** El `ConfirmationHandler` no se asigna en ningún lugar del código. Todas las operaciones YELLOW (cambiar modelo LLM, abrir apps/ajustes, mover archivos, set wallpaper desde URL, leer clipboard, crear/editar agentes, etc.) auto-aprueban en silencio. Las RED (delete) se auto-bloquean.
4. **C-04 · `WebApiFetcherPlugin` = SSRF/exfiltración por LLM.** El LLM puede hacer GET/POST a cualquier URL con cualquier body sin allowlist, sin gate, sin filtro. Alcanza redes internas (`192.168.x.x`, `127.0.0.1`).
5. **H-01 · Inyección de prompt en cada llamada LLM.** `CrossContextEngine` mete asuntos de email, calendarios, tareas y memoria en TODOS los prompts sin sanitizar. Un email malicioso puede hijackear las herramientas del agente.

---

## Vista por categoría

### Funcionalidad / Bugs (`01_FUNCIONALIDAD_BUGS.md`)
- Bugs lógicos en SecurityGate (toda la capa de confirmaciones está rota)
- Bug SQL en `ContactManagerPlugin` (placeholder Kotlin escapado mal)
- Bugs de logging en `CodeSandboxPlugin` (`\$var` literal)
- `runBlocking` en hot paths (init de Engine, captura de cámara)
- Zombie timeout del `MicrophoneArbiter` mata conversaciones largas
- Lifecycle del wake word con timing mágico (20s) y races con arbiter
- 41 catch vacíos esconden errores

### Seguridad (`02_SEGURIDAD.md`)
- Secretos en historial de git
- Build firmado con debug key
- SecurePrefs cae a plaintext silencioso
- WebView con JavaScript habilitado y URL controlable por LLM
- Inyección de prompt vía email/web/wiki
- Logs imprimen prefijos de API keys
- Sin certificate pinning, sin Network Security Config
- Permiso `MANAGE_EXTERNAL_STORAGE` declarado pero no usado

### Calidad de código (`04_CALIDAD_CODIGO.md`)
- 1 solo test file (180 líneas) en 35.6k LOC
- 41 catch vacíos
- Lint deshabilitado en release
- Strings escapados mal en varios plugins
- Singletons con callbacks single-listener (riesgo de listener huérfano)

### Dependencias (`03_DEPENDENCIAS.md`)
- Varias `*-alpha` (security-crypto, credentials)
- Apache POI pesado (APK bloat)
- Sin gestión central de versiones

---

## Veredicto

E.M.M.A. tiene una superficie de ataque muy grande para una app móvil:
- 21 permisos peligrosos
- 38 plugins que el LLM puede invocar
- 5 servicios foreground (mic, camera, location)
- 7 fuentes de contexto que entran al prompt sin filtrar
- Acceso a contactos, email, calendar, tasks, archivos

Y la capa de confirmación que debería frenar al LLM está **inactiva** (handler nunca asignado). El gate es decorativo.

**Antes de publicar:** corregir C-01..C-06 y H-01..H-04. Sin esos, una app con este nivel de autonomía no debería entrar a Play Store.

Ver `05_PROPUESTA_ACCION.md` para el plan priorizado.
