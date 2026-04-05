# 🐝 Bee-Movil Native — Roadmap & Checklist
### Última actualización: 5 de Abril 2026 · v4.1.0

---

## ✅ COMPLETADO (Fases 1-11 + extras)

### Core & Infraestructura
- [x] Proyecto Kotlin nativo + Jetpack Compose
- [x] Build system (Gradle, Android SDK, JDK 17)
- [x] GitHub repo + CI pipeline
- [x] Bee-Dark theme + paleta Honey
- [x] Dashboard premium (iOS-style, Material Icons)
- [x] Navegación multi-pantalla (12 screens)

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
- [x] **Productividad (5):** web_fetch, generate_pdf, generate_html, generate_spreadsheet, **read_document**
- [x] **Agent Core (4):** **run_code** (JS), **file_manager** (pro), **git** (JGit), **browser_agent**


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
- [x] **Visión desde el chat** (adjuntar imagen → análisis automático)

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

## 🗺️ ROADMAP — Paso a Paso

> Orden optimizado: primero lo que transforma la app de asistente a agente,
> luego estabilizar, luego pulir, luego expandir.

---

### ✅ FASE 12 — Agent Core: JavaScript Runner
> *De asistente a agente: el LLM ahora puede EJECUTAR código*

- [x] WebView invisible para ejecutar JavaScript
- [x] Skill `run_code` — recibe código JS, retorna resultado
- [x] Captura de console.log, errores, return values
- [x] Timeout protection (max 10 segundos)
- [x] Sandbox seguro (sin acceso a DOM real)

---

### ✅ FASE 13 — Agent Core: File Manager Pro
> *El agente puede crear, editar y organizar archivos*

- [x] Skill `file_manager` con 8 acciones
- [x] create_project: multi-archivo (HTML+CSS+JS)
- [x] copy, move, rename, search, tree, info, create_dir
- [x] Acceso a Downloads, Documents, DCIM, Pictures
- [ ] FileExplorerScreen (UI para navegar archivos visualmente)

---

### ✅ FASE 14 — Agent Core: Git Integration
> *El cel se conecta a todo tu ecosistema de código*

- [x] JGit dependency integrada
- [x] Skill `git` con 9 acciones: clone, status, add, commit, push, pull, log, diff, list_repos
- [x] Auth: HTTPS + Personal Access Token
- [x] Repos en /sdcard/BeeMovil/repos/
- [ ] GitScreen (UI para repos, commits, branches)
- [ ] GitHub Token field en Settings

---

### ✅ FASE 14B — Browser Built-in + Agent Navigation
> *Chrome interno que tu AI también controla*

- [x] BrowserSkill con 13 acciones (navigate, read, click, type, fill_form, extract, screenshot...)
- [x] BrowserScreen con URL bar, loading indicator, WebView completo
- [x] Hero card en Dashboard (Browser + Code)
- [x] Preview de proyectos HTML (file:// URLs)
- [x] Cookies persistentes (sesiones se mantienen)
- [ ] Google Sign-In → compartir sesión al WebView

---

### 🟢 FASE 15 — Bug Sweep, Settings & Fixes
> *Estabilizar todo + campo GitHub token en Settings*

- [ ] **Settings nuevos campos:**
  - [ ] GitHub Personal Access Token
  - [ ] Default browser homepage
- [ ] Auditoría de bugs por pantalla:
  - [ ] Settings: campos email SMTP, selector de modelo
  - [ ] Camera: flow de permisos, modelo de visión
  - [ ] Email: conexión IMAP, envío SMTP
  - [ ] Chat: manejo de errores de API, timeouts
  - [ ] Voice: reconocimiento en español, edge cases
  - [ ] Telegram: reconexión automática
  - [ ] Browser: edge cases de navegación
- [ ] Crash reporting (try/catch en todos los skills)
- [ ] Error messages claros (no stack traces)
- [ ] Manejo de permisos (camera, mic, storage, contacts, calendar)
- [ ] Memory leaks check
- [ ] Network timeout handling

---

### 🔵 FASE 16 — Homogenización UI
> *Todas las pantallas al nivel del Dashboard iOS-style*

- [ ] ChatScreen → rediseño con paleta Honey + iOS spacing
- [ ] SettingsScreen → grouped sections estilo iOS Settings
- [ ] CameraScreen → controles premium, resultados bonitos
- [ ] LiveVisionScreen → overlay estilo visor profesional
- [ ] VoiceChatScreen → animaciones más fluidas
- [ ] EmailInboxScreen → look like Apple Mail dark
- [ ] AgentCreatorScreen → wizard UI paso a paso
- [ ] **BrowserScreen** → mejorar UI (tabs, bookmarks, history)
- [ ] **FileExplorerScreen** → diseño clean de navegador de archivos
- [ ] **GitScreen** → diseño de commits y branches
- [ ] Tipografía consistente (design tokens)
- [ ] Paleta de colores centralizada
- [ ] Animaciones de transición entre pantallas

---

### 🟣 FASE 17 — Auditoría de Código & Seguridad
> *Profesionalizar antes de publicar*

**Código:**
- [ ] Refactor: eliminar código duplicado
- [ ] Arquitectura: ViewModel separation of concerns
- [ ] Dependency injection (factory pattern limpio)
- [ ] Unit tests para skills críticos (code runner, file manager, git)
- [ ] ProGuard/R8 rules (ofuscación)

**Seguridad:**
- [ ] API keys: encriptar en EncryptedSharedPreferences
- [ ] Certificate pinning para API calls
- [ ] Input sanitization (prevenir injection en code runner)
- [ ] Permisos mínimos (audit AndroidManifest)
- [ ] File access: sandboxed por default, SAF para acceso amplio
- [ ] No API keys en logs
- [ ] Git tokens encriptados

---

### ⚡ FASE 18 — Deploy & Publish
> *Cierra el loop: investigar → crear → publicar*

- [ ] Netlify deploy (subir proyecto HTML generado con API)
- [ ] Vercel deploy alternativo
- [ ] Vista previa en WebView antes de publicar
- [ ] URL generada automáticamente → share
- [ ] Historial de deploys
- [ ] Deploy via Git push (GitHub Pages, Netlify Git)

---

### 📱 FASE 19 — Widget Android
> *Presencia constante en el home screen*

- [ ] Widget pequeño (2x1): quick voice / quick chat
- [ ] Widget mediano (4x2): 4 quick actions + status
- [ ] Widget grande (4x4): mini-dashboard con stats
- [ ] Shortcuts (long press icon → Chat, Voz, Cámara)

---

### 🔄 FASE 20 — Automatizaciones (Workflows)
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

## ⭐ FASES FUTURAS (priorizar después)

### Fase 21 — Onboarding & Polish
- [ ] Wizard de primera vez (3-4 pasos: API key, modelo, demo)
- [ ] Tour guiado de features
- [ ] Splash screen animada con la abejita

### Fase 22 — Social & Content
- [ ] Post creator (imagen + caption)
- [ ] Share directo a IG/Twitter/LinkedIn
- [ ] Content scheduler

### Fase 23 — LLM Local (on-device)
- [ ] llama.cpp / llama.rn en el celular
- [ ] Model manager (download/delete)
- [ ] Fallback: local → cloud
- [ ] 0 costo, 0 latencia, offline

### Fase 24 — Orquestación Multi-Agente
- [ ] Agentes que delegan a otros agentes
- [ ] Research → Writer → Publisher chain
- [ ] Modelo por agente especializado

### Fase 25 — RAG & Inteligencia
- [ ] Leer PDFs/docs locales como contexto
- [ ] Web research chain (buscar → leer → sintetizar)
- [ ] Resumen de emails largos

### Fase 26 — Comunicaciones Avanzadas
- [ ] WhatsApp Business API integration
- [ ] Discord Bot
- [ ] Notificaciones push proactivas

### Fase 27 — Play Store & Distribución
- [ ] Play Store listing + screenshots
- [ ] Políticas de privacidad
- [ ] Beta testing (10-20 testers)
- [ ] Landing page de descarga

### Fase 28 — Desktop (Electron)
- [ ] Electron app para Windows/Mac
- [ ] UI adaptada para pantalla grande

---

## 📊 Métricas

| Métrica | Valor |
|---|---|
| Versión | v4.1.0 |
| Skills | 35 |
| Pantallas | 13 |
| Providers LLM | 2 (OpenRouter + Ollama Cloud) |
| Modelos de visión | 6 |
| Fases completadas | 15 de 28 |
| Target BEE Smart v2.0 | 58 skills, 20 automations |
| APK tamaño actual | ~20 MB |
| Hito actual | AGENTE + BROWSER (navega, ejecuta, crea, sube) |
