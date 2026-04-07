# 🐝 Bee-Movil Native — Roadmap & Checklist
### Ultima actualizacion: 7 de Abril 2026 · v5.7.1

---

## ✅ COMPLETADO (Fases 1-18)

### Core & Infraestructura (Fases 1-11)
- [x] Proyecto Kotlin nativo + Jetpack Compose
- [x] Build system (Gradle, Android SDK, JDK 17)
- [x] GitHub repo + CI pipeline
- [x] Bee-Dark theme + paleta Honey
- [x] Dashboard premium (iOS-style, Material Icons)
- [x] Navegación multi-pantalla (15 screens)

### LLM & Agent Loop
- [x] OpenRouter provider (modelos gratuitos + premium)
- [x] Ollama Cloud provider (GLM-4, Qwen, DeepSeek, Gemma 4)
- [x] Agent loop con tool calling automático
- [x] Streaming de respuestas
- [x] Multi-agente: agentes customs con modelo/personalidad propios
- [x] Selector dinámico de modelos

### 35 Skills Nativos
- [x] **Core (7):** device, clipboard, notify, TTS, browser, share, file
- [x] **Memoria (1):** memory (SQLite persistente)
- [x] **Multimedia (10):** camera, image_gen, volume, alarm, flashlight, music, QR, app_launcher, connectivity, brightness
- [x] **Production (8):** calendar, email, weather, web_search, contacts, calculator, datetime, battery_saver
- [x] **Productividad (5):** web_fetch, generate_pdf, generate_html, generate_spreadsheet, read_document
- [x] **Agent Core (4):** run_code (JS), file_manager (pro), git (JGit), browser_agent

### Comunicaciones
- [x] Email IMAP/SMTP (auto-detección de proveedor, App Password)
- [x] Telegram Bot bidireccional
- [x] Share mejorado (archivos + target app: IG, WhatsApp, Twitter)

### Vision AI
- [x] CameraScreen (foto + galería → análisis)
- [x] LiveVisionScreen (CameraX, reconocimiento continuo)
- [x] Modelo de visión independiente del chat
- [x] Gemma 4 31B como default
- [x] Quick prompts (OCR, factura, identificación)
- [x] Visión desde el chat (adjuntar imagen → análisis automático)

### Voz
- [x] VoiceInputManager (STT nativo Android)
- [x] TtsSkill (texto a voz)
- [x] VoiceChatScreen — conversación continua hands-free
- [x] Auto-listen loop (STT → Agent → TTS → repeat)

### UI/UX
- [x] Dashboard iOS-style con hero cards
- [x] Material Icons (cero emojis de texto)
- [x] Arc charts en stats
- [x] Greeting dinámico
- [x] Status chip (AI activa / Sin API)
- [x] Bee logo branding

### Chat & Archivos (v3.8-v3.9)
- [x] File cards inline en chat (icono, nombre, tamaño, abrir, compartir)
- [x] Botón adjuntar (imagen + archivo)
- [x] Document Reader: PDF, DOCX, XLSX, TXT, CSV, JSON
- [x] Image preview inline en burbujas
- [x] FileProvider configurado para Open/Share

---

### ✅ FASE 12 — Agent Core: JavaScript Runner
- [x] WebView invisible para ejecutar JavaScript
- [x] Skill `run_code` — recibe código JS, retorna resultado
- [x] Captura de console.log, errores, return values
- [x] Timeout protection (max 10 segundos)
- [x] Sandbox seguro (sin acceso a DOM real)

### ✅ FASE 13 — Agent Core: File Manager Pro
- [x] Skill `file_manager` con 8 acciones
- [x] create_project: multi-archivo (HTML+CSS+JS)
- [x] copy, move, rename, search, tree, info, create_dir
- [x] Acceso a Downloads, Documents, DCIM, Pictures
- [x] FileExplorerScreen (UI para navegar archivos visualmente)

### ✅ FASE 14 — Agent Core: Git Integration
- [x] JGit dependency integrada
- [x] Skill `git` con 9 acciones: clone, status, add, commit, push, pull, log, diff, list_repos
- [x] Auth: HTTPS + Personal Access Token
- [x] Repos en /sdcard/BeeMovil/repos/
- [x] GitScreen (UI para repos, commits, branches)
- [x] GitHub Token field en Settings

### ✅ FASE 14B — Browser Built-in + Agent Navigation
- [x] BrowserSkill con 13 acciones (navigate, read, click, type, fill_form, extract, screenshot...)
- [x] BrowserScreen con URL bar, loading indicator, WebView completo
- [x] Hero card en Dashboard (Browser + Code)
- [x] Preview de proyectos HTML (file:// URLs)
- [x] Cookies persistentes (sesiones se mantienen)

### ✅ FASE 15 — Bug Sweep (v4.2.1 → v4.2.3)
> 38 bugs corregidos en 3 fases

- [x] Memory leak: trimHistory() caps at 40 messages
- [x] Free model tool calling (inject as text in system prompt)
- [x] Browser: screenshot crash, null WebView, infinite reload loop
- [x] CodeRunner: null WebView crash
- [x] Camera: bitmap null/zero-dimension crashes
- [x] Voice: recursive startListening() stack overflow fix
- [x] VoiceInputManager: auto-init + locale fix
- [x] Agent configs: skill names, tool count, categories corrected
- [x] Email UID passthrough fix
- [x] IMAP Store resource leak fix
- [x] Git resource leak (withRepo pattern)
- [x] Telegram: single thread executor (stampede fix)
- [x] CustomAgentDB null safety
- [x] Missing permissions: CALL_PHONE, SET_ALARM, MANAGE_EXTERNAL_STORAGE, WAKE_LOCK
- [x] **BeeHttpClient** (centralized OkHttpClient singleton: default/longPoll/llm)
- [x] Crash log rotation (keeps 5, device info, getLastCrashLog API)

### ✅ FASE 16 — Homogenización UI
- [x] SettingsScreen actualizado (35 skills, v4.2.1)

### ✅ FASE 17 — Encriptación de Credenciales (v4.2.4)
- [x] SecurePrefs.kt — EncryptedSharedPreferences (AES-256)
- [x] 7 secretos migrados: API keys, tokens, passwords
- [x] Migración automática transparente al usuario
- [x] 10 archivos actualizados para usar SecurePrefs

### ✅ FASE 18 — LLM Local On-Device (v4.2.4 → v4.2.5)
- [x] LocalGemmaProvider — MediaPipe LlmInference
- [x] LocalModelManager — download, storage, lifecycle
- [x] Gemma 4 E2B (~2.6 GB) y E4B (~3.7 GB) via HuggingFace litert-community
- [x] .litertlm format (LiteRT-LM framework)
- [x] Download con progress bar en Settings
- [x] Selector: Cloud vs Local en Settings
- [x] Ollama fallback si MediaPipe falla
- [x] Tool calling via text injection (misma técnica que free models)
- [x] Fix: URLs correctas de HuggingFace, provider switch auto-seleccion

---

## 🔜 ROADMAP — Lo que sigue

> Orden: Estabilizar + Multi-Agente → UI polish → Deploy → Widgets → Expansión

---

### ⚡ FASE 18B — Bug Sweep 2 + Multi-Agente + A2A Gateway
> *Antes de pulir UI, convertir Bee-Movil de "asistente" a "orquestador de agentes"*

**18B-1 — ✅ Bug Sweep Round 2 (v4.2.6) — 12 items resueltos**
- [x] BUG-1: Herramientas Dashboard → prefillAgentChat (editable, no auto-send)
- [x] BUG-2: WebSearch reescrito (DuckDuckGo HTML, resultados reales)
- [x] BUG-3: PDF storage con fallback Android 11+
- [x] BUG-4: AgentCreator muestra modelos de TODOS los providers
- [x] BUG-5: Agentes default ahora editables
- [x] BUG-6: Email: Yahoo eliminado, dominio propio, Gmail App Password hint
- [x] BUG-7: Versiones actualizadas a v4.2.5→v4.2.6
- [x] BUG-8/9: Skill counts corregidos (25→35)
- [x] MEJORA-1: Provider quick-selector en Dashboard
- [x] FEATURE-7: Archivos organizados (PDFs/, HTML/, Excel/)
- [x] Fix: Download crash (timeout, thread safety, storage) — anterior commit

**18B-1.5 — ✅ Provider Audit + ModelRegistry (v4.2.7)**
- [x] ModelRegistry.kt — fuente única de verdad para 50+ modelos
- [x] Ollama Cloud: 26 modelos (GLM-5, Kimi K2.5, MiniMax, Nemotron, Qwen3.5)
- [x] OpenRouter: 12 modelos (7 free + 5 premium)
- [x] HuggingFace Token en Settings para descarga local
- [x] Modelos agrupados por categoría (Chat/Code/Vision/Reasoning/Agent)
- [x] Fix: Local provider no requiere API key (crash fix)

**18B-2 — ✅ delegate_to_agent (Multi-Agente desde el Chat)**
- [x] Nuevo skill `delegate_to_agent` — el agente principal llama a otros agentes como tools
- [x] Delegación por petición del usuario: "pásale esto al agente de ventas"
- [x] Delegación autónoma: el agente decide solo cuándo delegar
- [x] UI inline en el chat: burbuja visual mostrando delegación
- [x] El agente principal recibe el resultado y continúa la conversación
- [x] Error handling: agente no encontrado, timeout, modelo no disponible

**18B-3 — ✅ Workflow Engine + WorkflowScreen (estilo n8n)**
- [x] `Workflow` data class: lista de pasos, cada paso = agente + prompt + input/output mapping
- [x] `WorkflowRunner` — ejecuta pasos secuencialmente con estado visible
- [x] **WorkflowScreen** — nueva pantalla visual tipo n8n:
  - [x] Nodos conectados representando agentes/skills
  - [x] Estado en vivo por nodo: ⏳ esperando → 🔄 ejecutando → ✅ listo
  - [x] Output fluyendo visualmente entre nodos
- [x] 6 Templates pre-construidos:
  - [x] Research → Redacción → PDF
  - [x] Morning Brief (agenda + clima + device)
  - [x] Content Creator (imagen → post → HTML)
  - [x] Quote + Email (ventas → PDF → email)
  - [x] URL → Landing Page
  - [x] Daily Digest (noticias + agenda → PDF)
- [x] Guardar/cargar workflows personalizados (Phase 21)
- [x] Pantalla 16: WorkflowScreen en Dashboard

**18B-4 — 🌐 A2A Gateway (Agent-to-Agent Protocol de Google)**
- [ ] **Cliente A2A** — Bee-Movil envía tareas a agentes externos:
  - [x] Registro de agentes remotos (URL + Agent Card) — RemoteAgentRegistry
  - [x] Enviar Task a agente externo (OpenClaw, BEE-Dashboard, etc.)
  - [x] Recibir resultados y mostrarlos en el chat
- [x] **Servidor A2A** — Bee-Movil recibe tareas de agentes externos:
  - [x] Mini HTTP server (WiFi local, port 8765)
  - [x] Agent Card publicando capacidades (37 skills)
  - [x] POST /tasks → recibir tareas
  - [x] GET /tasks/:id → estado de tarea
  - [ ] SSE streaming de progreso (pendiente)
- [ ] UI en Settings para configurar agentes remotos (pendiente)
- [x] Skill `call_remote_agent` para usar desde el chat

---

### FASE 19 — UI Premium (Completada v4.4.0)
> *Rediseno visual completo — percepcion de producto premium*

- [x] Reemplazar emojis restantes por Material Icons (40+ archivos, 0 emojis)
- [x] Google Font Inter con 4 pesos tipograficos (Regular, Medium, SemiBold, Bold)
- [x] Paleta de colores expandida (10 accents + brand + light/dark)
- [x] Sombras y elevation en Cards (profundidad visual)
- [x] Suggestion Chips (LazyRow con 8 pills scrolleables en ChatScreen)
- [x] Transiciones animadas entre pantallas (AnimatedContent fade+slide)
- [x] Grid de iconos con fondos gradiente en Dashboard (radialGradient)
- [x] SelectionContainer para copiar texto de burbujas
- [x] Micro-animaciones (heartbeat, pulse, slide transitions)
- [x] Tema claro/oscuro toggle (Dark/Light/System en Settings, persistente)
- [x] Dashboard rewrite IG-style (hero greeting, AI insight, stories agents)
- [x] Chat Premium (gestures, context menu, file cards, markdown)
- [x] PremiumBottomNav (animated gold indicator, Material Icons)
- [x] Zero-emoji policy completa (40+ archivos, skills, agents, LLM providers)

---

### FASE 19B — Bug Sweep 3 + Smart Routing (Completada v4.4.1)
> *8 bugs criticos arreglados + provider inteligente*

- [x] Custom agents: system prompt fix
- [x] Workflows: modelo/provider fix
- [x] Vision: smart provider routing (no hardcoded)
- [x] Camera/LiveVision: smart provider routing
- [x] Local model: prompt limits fix
- [x] Voz en modo local: API key exemption
- [x] Chat imagen memory: persist across prompts
- [x] Multi-image attach: GetMultipleContents

---

### FASE 19C — Tasks + Dashboard Upgrade (Completada v4.5.0)
- [x] TaskDB (SQLite) con CRUD, prioridad, status tracking
- [x] TaskScreen con vista lista/calendario
- [x] Widget de pendientes en Dashboard
- [x] Dashboard 3ra fila: Notificaciones + Tareas

---

### FASE 19D — Notification Intelligence Agent (Completada v4.5.1)
- [x] BeeNotificationService (NotificationListenerService)
- [x] NotificationLogDB (SQLite, auto-purge 30 dias)
- [x] NotificationDashboardScreen + NotificationConfigScreen
- [x] NotificationQuerySkill (consultas por lenguaje natural)
- [x] Config ON/OFF por app, 100% local

---

### FASE 20 — Deepgram STT/TTS (Completada v4.6.0)
> *Voz mejorada para el agente*

- [x] DeepgramSTT: Nova-3 REST API, auto silence detection, multi-idioma
- [x] DeepgramTTS: Aura con 5 voces (Asteria, Luna, Stella, Orion, Arcas)
- [x] DeepgramVoiceManager: orquestador unificado con fallback a nativo
- [x] Settings: API key (SecurePrefs), voice selector, STT/TTS toggles
- [x] Spanish TTS: usa voz nativa Android (Aura es solo ingles)
- [x] Tap-to-interrupt: detiene TTS y reinicia escucha

---

### FASE 20C — Chat Memory + RAG + Multi-Image (Completada v4.8.0)
> *Sesión actual — estabilización y mejoras*

- [x] Pull-to-refresh en Dashboard, Conversations, Tasks, Notifications
- [x] PermissionDialog premium (branded, no OS-level)
- [x] Dashboard AI Scanner (5-phase boot animation + Memory widget)
- [x] Sequential multi-image processing (OOM crash fix)

---

### FASE 20D — Workflow Resilience + Files (Completada v5.0.0)
> *Workflows de producción + archivos inteligentes*

- [x] Workflow delivery actions (Copy/Share/Save/Send-to-Chat)
- [x] **AttachmentManager** — procesador universal de archivos:
  - [x] Image: base64 1200px, persisted to BeeMovil/attachments/
  - [x] PDF: PdfRenderer + raw text extraction (50 pages)
  - [x] DOCX: ZIP parser → word/document.xml → text
  - [x] XLSX: ZIP parser → sharedStrings + sheet1 → CSV
  - [x] CSV/TXT/JSON/MD: full inline content
- [x] Chat attachment chips (pending files UI + processing spinner)
- [x] sendMessageWithAttachments() — persistent context injection
- [x] BeeAgent.injectAttachmentContext() — survives across conversation
- [x] FileExplorer: Adjuntos + Generados quick tabs
- [x] **Workflow model selector** por step (ModelPicker dialog)
- [x] **Error recovery** (Reintentar / Saltar / Cancelar)
- [x] **WorkflowHistoryDB** — SQLite persistent run history
- [x] History tab with delete (single, batch, clear all)
- [x] Re-execute from history
- [x] Auto-save results to BeeMovil/generated/

---

## 🔜 ROADMAP — Fases pendientes

> Orden: Custom Workflows → File Explorer → Browser → Deploy → Intelligence → Widgets → Lanzamiento

---

### ✅ FASE 21 — Custom Workflows + AI Generator + Full Scheduler (Completada v5.1.0)
> *El usuario crea, edita y programa sus propios flujos*

**21-A: Custom Workflow DB + Editor + 3-Tab UI**
- [x] `CustomWorkflowDB.kt` — SQLite CRUD, JSON steps/schedule, triggers
- [x] `WorkflowEditorScreen.kt` — Full editor (steps, agents, models, schedule config)
- [x] `WorkflowScreen.kt` — 3 tabs (Templates | Mis Flujos | Historial)
- [x] `CustomWorkflowsView` — list/create/edit/delete/execute custom workflows
- [x] Schedule configuration: frequency, time, days, WiFi/battery/boot triggers
- [x] Per-step model override via ModelPicker

**21-B: AI Workflow Generator**
- [x] `GenerateWorkflowSkill.kt` — create/edit workflows from natural language
- [x] Auto-saves to CustomWorkflowDB on generation
- [x] Registered as skill #38

**21-C: Full Workflow Scheduler**
- [x] `WorkflowScheduler.kt` — AlarmManager exact alarms + WorkManager fallback
- [x] `WorkflowSchedulerWorker.kt` — background execution + notifications
- [x] `WorkflowSchedulerReceiver` — boot + alarm triggers
- [x] `WifiTriggerReceiver` — WiFi connect trigger
- [x] `BatteryTriggerReceiver` — low battery trigger
- [x] AndroidManifest: SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM, receivers
- [x] Auto-reschedule on boot and app start
- [x] Results saved to history + file + notification

---

### ✅ FASE 22 — Vision Pro Audit & Face Intelligence (Completada v5.7.1)
> *Auditoria completa del modo Vision Pro, fix del microfono y ML Kit Faces*

- [x] Lifecycle management (DisposableEffect, stopSpeaking en onDispose)
- [x] TTS leak fix (dgVoice.stopSpeaking en todos los exit paths)
- [x] Local model routing (Gemma 4 con hasVision=true en ModelRegistry)
- [x] Network-aware provider routing (offline → local, online → cloud)
- [x] UI: Eliminadas Quick Actions + POI limits logrando "Clean Vision Mode"
- [x] Mic Fix: `RECORD_AUDIO` permission arreglado via `RequestMultiplePermissions`
- [x] Face Tracking en vivo con ML Kit (FaceAnalyzer) + Canvas Drawing
- [x] Face Metadata LLM injection ("1 rostros en vivo", con tracker id)
- [x] Concurrency limits: System prompts ultra-concisos (1-2 oraciones)
- [x] Microphone sync fix (TTS ↔ STT state machine)

---

### ✅ FASE 23 — Media IA: Imagen + Video + Galeria (Completada v5.5.0)
> *Generacion de imagenes y videos con IA desde cualquier chat*

**23-A: Multi-Provider Image Generation**
- [x] `ImageGenSkill.kt` reescrito — 3 providers con auto-fallback:
  - [x] fal.ai (Flux Schnell/Pro — el mas rapido, 2-4 seg)
  - [x] Together AI (Flux.1 Schnell — creditos gratis de inicio)
  - [x] OpenRouter (DALL-E 3 — pagado, existente)
- [x] Auto-download a BeeMovil/images/
- [x] Size: square/landscape/portrait
- [x] Model selection: flux-schnell/flux-pro/dall-e-3
- [x] Base64 image handling (Together AI)
- [x] Async queue polling (fal.ai)

**23-B: AI Video Generation**
- [x] `VideoGenSkill.kt` — Kling 2.1 via fal.ai:
  - [x] Text-to-video (5s o 10s)
  - [x] Image-to-video (anima fotos)
  - [x] Standard (rapido) y Pro (calidad)
- [x] Async queue: submit → poll 3s → download MP4
- [x] Auto-download a BeeMovil/videos/
- [x] Video inline en chat (play button card + system player)

**23-C: Media Gallery**
- [x] `MediaGalleryScreen.kt` — galeria visual:
  - [x] Grid 3 columnas con thumbnails
  - [x] Tabs: Todo / Imagenes / Videos
  - [x] Full-screen viewer con pinch-to-zoom
  - [x] Video play → system player
  - [x] Acciones: Compartir, Abrir, Al Chat, Eliminar
  - [x] Prompt preview extraido del filename
  - [x] Empty state con hint de generacion
- [x] Dashboard: boton "Media" (purple) en quick actions
- [x] Settings: seccion "MEDIA IA" con cost warning (naranja)
- [x] fal.ai + Together AI API key fields (SecurePrefs)
- [x] Skill count: 39 (image_gen multi + video_gen)

---

### ✅ FASE 24 — File Explorer Real + Google Workspace (Completada v5.6.0)
> *Rewrite completo del navegador de archivos + Google Workspace*

**24-A: File Explorer Rewrite**
- [x] Navegacion desde /storage/emulated/0/ (TODO el almacenamiento)
- [x] Quick access: Home, Downloads, DCIM/Camera, BeeMovil, Documents, Pictures, Music
- [x] Breadcrumb navigation interactiva (cada segmento clickeable)
- [x] Acciones por archivo: compartir, copiar, mover, renombrar, eliminar
- [x] Crear carpeta nueva
- [x] Busqueda recursiva con filtros (5 niveles, max 50)
- [x] Ordenar por: nombre, tamano, fecha, tipo
- [x] Multi-select para operaciones masivas (long-press)
- [x] Preview de imagenes (thumbnail 1/8 sample)
- [x] Preview de videos (.mp4) con play icon
- [x] Boton "Enviar al chat" (adjuntar al agente activo)
- [x] Badge "AI" en archivos generados por el agente
- [x] 16 file type icons con colores distintos

**24-B: Google Workspace Integration**
- [x] `GoogleAuthManager.kt` — Credential Manager sign-in (one-tap)
- [x] Persistent session: email, name, photo, tokens (SecurePrefs)
- [x] Incremental scope tracking: Drive, Gmail, Calendar
- [x] `GoogleDriveService.kt` — REST API v3 (list, search, upload, download, createFolder, delete, storage)
- [x] `GoogleDriveSkill.kt` — Skill #40 (6 acciones para el agente)
- [x] Tab "Drive" (chip azul) en File Explorer
- [x] Settings: seccion GOOGLE WORKSPACE con branded sign-in
- [x] Connected state: avatar, email, ServiceBadge per-service
- [x] Soul auto-populate: nombre y email al hacer sign-in
- [x] compileSdk/targetSdk 34→35 (Credential Manager requirement)
- [x] `google-api-services-calendar:v3` ready (Phase 24-B-4)
- [ ] Gmail OAuth2 (IMAP XOAUTH2) — pendiente config Cloud Console
- [ ] Calendar API enhancement — pendiente scope authorization

---

### FASE 25 — Agent Intelligence + Chat Fix (COMPLETADA v5.7.0)
> *Hacer que los agentes se sientan inteligentes de verdad — PRIORIDAD*

**25-A: Auto-Memory + Soul Profile**
- [x] Auto-extract memorias despues de cada conversacion (>3 turnos)
- [x] Regex-based fact extraction (nombre, email, empresa, preferencias)
- [x] Soul profile auto-population (se llena solo)
- [x] Memory deduplication (no repetir hechos)

**25-B: File Reading Fix (el agente no lee sus propios archivos)**
- [x] Fix: archivos generados por agente -> path se inyecta al contexto
- [x] Fix: "Enviar al chat" desde FileExplorer -> leer TODO tipo de archivo
- [x] HTML, JS, CSS generados -> contextChunk completo
- [x] filePaths persisten en ChatHistoryDB (columna nueva)
- [x] Imagenes/videos se siguen mostrando al reabrir chat

**25-C: Email Directo (sin abrir Gmail)**
- [x] `EmailSkill.kt` rewrite: usar `EmailService.sendEmail()` directo con OAuth
- [x] Si tiene Google Workspace -> envia via XOAUTH2 automaticamente
- [x] Si tiene IMAP/SMTP config -> envia via SMTP directo
- [x] Fallback: Intent (solo si no hay config)
- [x] El agente CONFIRMA antes de enviar

**25-D: Dashboard AI Insight Fix**
- [x] Eliminar frases hardcoded (SIEMPRE usar LLM — cloud o local)
- [x] Fallback solo: "Cargando..." mientras LLM inicia (primeros 30 seg)
- [x] Mas contexto: memorias + soul + calendario + hora + dia
- [x] Refresh button (tap para regenerar)
- [x] Variedad: tip, dato curioso, recordatorio, motivacion (rotar tipo)

**25-E: Action Log + Thinking Visible**
- [x] Tabla `action_log`: timestamp, agent, skill, params, result, duration
- [x] `ActionLogScreen.kt` con timeline visual
- [x] `<think>` tags parsing -> UI expandible "Pensando..."
- [x] `NavigateAppSkill.kt` — el agente navega screens del app
- [x] Audit completo: que puede y que NO puede hacer el agente

---

### FASE 26 — Browser Agent Mode
> *WebView + chat panel + JS bridge = Puppeteer movil*

**26-A: Split View + Chat Panel**
- [ ] Layout: WebView (70%) + Agent Chat (30%)
- [ ] Chat panel con historial de acciones del browser
- [ ] Toggle full-screen / split mode
- [ ] Agent puede "ver" y "actuar" sobre la pagina

**26-B: JS Bridge (WebView - Agent)**
- [ ] read_page -> evaluateJavascript()
- [ ] click_element -> querySelector(selector).click()
- [ ] fill_form -> set input values
- [ ] extract_links -> todos los a href de la pagina
- [ ] extract_data -> CSS selector -> tabla de datos
- [ ] screenshot -> captura como base64 para vision AI
- [ ] scroll_to -> scroll a posicion/elemento

---

### FASE 27 — Deploy Agent (Netlify + Vercel)
> *Publica sitios web directamente desde el telefono*

- [ ] `DeploySkill.kt` — skill de deploy con 2 providers
- [ ] Netlify: deploy via API (drag-and-drop zip)
- [ ] Vercel: deploy via API (project upload)
- [ ] Deploy desde FileExplorer (selecciona carpeta -> deploy)
- [ ] Deploy desde chat ("publica mi landing page")
- [ ] Historial de deploys con URLs activas
- [ ] API keys en Settings (Netlify token, Vercel token)
- [ ] Badge "LIVE" en archivos que tienen deploy activo

---

### FASE 28 — Widget Android
- [ ] Widgets 2x1, 4x2, 4x4 + shortcuts
- [ ] Quick actions desde home screen
- [ ] Status del agente en widget

---

### FASE 29 — Onboarding Flow v2.0
- [ ] 10-step guided setup (brand, personality, API keys, skills)
- [ ] Template-based agent generation
- [ ] Welcome wizard para nuevos usuarios

---

### FASE 30 — Skills Expansion (40 a 65+)
- [ ] 25 nuevos skills de negocio/productividad
- [ ] `SSHTerminalSkill.kt` — SSH a servidores remotos (terminal CLI, controlar apps)
- [ ] Social media posting
- [ ] Invoice generation
- [ ] Data analysis
- [ ] Music control via SSH/API (Spotify, YouTube Music)

---

### FASE 31 — Business Automations (20)
- [ ] CRM, invoicing, inventory, scheduling, reporting
- [ ] Templates de automatizacion por industria

---

### FASE 32 — Play Store + Distribucion
- [ ] R8 optimization, ProGuard, APK size reduction
- [ ] Privacy policy, screenshots, listing
- [ ] Google OAuth scope verification (Gmail -> Gmail API REST si necesario)
- [ ] Release keystore SHA-1 para OAuth Client ID produccion
- [ ] Beta testing round

---

## POST-LANZAMIENTO

### FASE 33 — Social Media Hub

### FASE 34 — Voice Realtime Mode (Full-Duplex)
> *Conversacion estilo Alexa — siempre escuchando*

- [ ] ForegroundService con wake word detection ("Hey Bee")
- [ ] Deepgram WebSocket streaming STT (conexion persistente)
- [ ] VAD (Voice Activity Detection) — detecta voz en tiempo real
- [ ] Barge-in: mic abierto durante TTS, si hablas = corta al instante
- [ ] Echo cancellation (AcousticEchoCanceler)
- [ ] Streaming TTS: empieza a hablar antes de respuesta completa
- [ ] Full-duplex audio pipeline
- [ ] Battery optimization: DSP-level keyword detection si hardware lo permite

---

## Metricas

| Metrica | Actual | Target v6.0 |
|---------|--------|-------------|
| Version | v5.6.1 | v6.0 |
| Skills | 40 | 65+ |
| Pantallas | 19 | ~24 |
| Providers LLM | 3 + 2 media | 4 + 3 media |
| Fases completadas | 27 de 34 | 32 (pre-launch) |
| APK tamano | ~85 MB | Optimizar R8 |
| Google Workspace | ✅ Configurado (Web Client ID activo) |
| Hito actual | **PROXIMO: Fase 25** (Browser Agent Mode) |

---
