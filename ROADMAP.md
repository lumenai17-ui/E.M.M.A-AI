# 🐝 Bee-Movil Native — Roadmap & Checklist
### Ultima actualizacion: 6 de Abril 2026 · v4.4.0

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
- [ ] Guardar/cargar workflows personalizados (pendiente)
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

### 🎙️ FASE 20 — Deepgram STT/TTS
> *Voz natural para el agente*

- [ ] Reemplazar Google STT con Deepgram Nova (mejor accuracy español)
- [ ] Reemplazar Android TTS con Deepgram Aura (voz natural)
- [ ] Streaming de audio bidireccional
- [ ] Selección de voz por agente

---

### 🖼️ FASE 21 — Image Generation (Stable Diffusion)
- [ ] Integrar Stable Diffusion API para generación de imágenes
- [ ] UI de preview y edición
- [ ] Historial de imágenes generadas

---

### ⚡ FASE 22 — Deploy & Publish
> *El agente puede publicar landing pages en la web*

- [ ] Netlify deploy (subir proyecto HTML generado con API)
- [ ] Vercel deploy alternativo
- [ ] Vista previa en WebView antes de publicar
- [ ] URL generada automáticamente → share
- [ ] Historial de deploys
- [ ] Deploy via Git push (GitHub Pages, Netlify Git)

---

### 📱 FASE 23 — Widget Android
> *Presencia constante en el home screen*

- [ ] Widget pequeño (2x1): quick voice / quick chat
- [ ] Widget mediano (4x2): 4 quick actions + status
- [ ] Widget grande (4x4): mini-dashboard con stats
- [ ] Shortcuts (long press icon → Chat, Voz, Cámara)

---

### 🔄 FASE 24 — Automatizaciones (Workflows)
> *Cadenas de skills con 1 tap*

- [ ] Workflow engine (ejecutar lista ordenada de skills)
- [ ] 8 templates sólidos:
  - [ ] Research & PDF
  - [ ] Morning Briefing (voz)
  - [ ] URL → Landing Page
  - [ ] Foto → IG Post
  - [ ] Email Digest
  - [ ] Data Extractor (URL → Excel)
  - [ ] Blog Writer
  - [ ] Voice Memo → PDF
- [ ] Workflow builder UI (crear/editar)
- [ ] Scheduler (ejecutar a X hora)

---

### 📋 FASE 25 — Onboarding Flow (10 pasos v2.0)
- [ ] Welcome → Identity → Branding → Channels → Google APIs
- [ ] Integrations → Automations → Knowledge Base → Billing → Review
- [ ] Generación de config.json para deployment
- [ ] Context mapping por tipo de negocio

---

### 🛠️ FASE 26 — Skills Expansion (35 → 58)
- [ ] **Comunicación:** WhatsApp, Discord, SMS directo
- [ ] **Inteligencia:** Translate, Summarize, Sentiment, Classify, Rewrite
- [ ] **Multimedia:** Video process, OCR dedicado
- [ ] **Web:** Webhooks, Cron/scheduling
- [ ] **Location:** Maps, Directions
- [ ] **Business:** Reports, Review monitoring

---

### ⚡ FASE 27 — Business Automations (20)
- [ ] **Marketing (7):** Meta Ads, Post Creator, Social Scheduler, GMB, SEO, Lead Capture, Competitor Watch
- [ ] **Web (4):** WordPress Publisher, Blog Autopilot, Landing Express, Newsletter Auto
- [ ] **Operations (5):** Invoice Autopilot, Appointment Bot, Review Responder, Daily Report, Customer Follow-up
- [ ] **E-commerce (4):** Product Catalog, Order Manager, Payment Links, Inventory Alert

---

### 🏪 FASE 28 — Play Store & Distribución
- [ ] Play Store listing + screenshots
- [ ] Políticas de privacidad
- [ ] Beta testing (10-20 testers)
- [ ] Landing page de descarga

---

## 📊 Métricas

| Metrica | Actual | Target v2.0 |
|---------|--------|-------------|
| Version | v4.4.0 | v5.0 |
| Skills | 37 (+delegate, +call_remote) | 58 |
| Pantallas | 16 (+WorkflowScreen) | ~18 |
| Providers LLM | 3 (OpenRouter + Ollama Cloud + Local) | 4 (+Deepgram) |
| Modelos de vision | 7 | 10 |
| Automations | 6 templates | 20 |
| Bugs conocidos | 0 | 0 |
| Tiers de precio | -- | 3 ($25/$40/$100) |
| Fases completadas | 19 de 28 | 28 |
| APK tamano | ~78 MB | Optimizar con R8 |
| Multi-agente | Orquestacion + A2A | Orquestacion + A2A |
| Hito actual | **PROXIMO: Fase 20** (Deepgram STT/TTS) | |

