# 🐝 Bee-Movil Native — Roadmap & Checklist
### Última actualización: 4 de Abril 2026 · v3.7.3

---

## ✅ COMPLETADO (Fases 1-11)

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

### 30 Skills Nativos
- [x] **Core (7):** device, clipboard, notify, TTS, browser, share, file
- [x] **Memoria (1):** memory (SQLite persistente)
- [x] **Multimedia (10):** camera, image_gen, volume, alarm, flashlight, music, QR, app_launcher, connectivity, brightness
- [x] **Production (8):** calendar, email, weather, web_search, contacts, calculator, datetime, battery_saver
- [x] **Productividad (4):** web_fetch, generate_pdf, generate_html, generate_spreadsheet

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

---

## 🗺️ ROADMAP — Próximas Fases

---

### 🔴 FASE 12 — Automatizaciones (Mission Control)
> *El diferenciador: cadenas de skills que se ejecutan con 1 tap*

- [ ] Workflow engine (ejecutar lista ordenada de skills)
- [ ] Workflow builder UI (crear/editar/reordenar pasos)
- [ ] Templates pre-hechos (ej: "Research & PDF", "Photo → IG Post")
- [ ] Guardar workflows favoritos
- [ ] WorkflowScreen nueva (listar, ejecutar, ver progreso)
- [ ] Scheduler (opcional: ejecutar workflow a X hora)

---

### 🟠 FASE 13 — Deploy Skill
> *Cierra el loop: investigar → crear → publicar*

- [ ] Netlify deploy (subir HTML generado con API)
- [ ] Vercel deploy alternativo
- [ ] Vista previa en WebView antes de publicar
- [ ] URL generada automáticamente → share
- [ ] Historial de deploys

---

### 🟡 FASE 14 — Widget Android
> *Presencia constante en el home screen del usuario*

- [ ] Widget pequeño (2x1): quick voice / quick chat
- [ ] Widget mediano (4x2): 4 quick actions + status
- [ ] Widget grande (4x4): mini-dashboard con stats
- [ ] Shortcuts (long press icon → Chat, Voz, Cámara)

---

### 🟢 FASE 15 — Bug Sweep & Fixes
> *Estabilizar todo antes de seguir construyendo*

- [ ] Auditoría de bugs por pantalla:
  - [ ] Settings: campos email SMTP, selector de modelo
  - [ ] Camera: flow de permisos, modelo de visión
  - [ ] Email: conexión IMAP, envío SMTP
  - [ ] Chat: manejo de errores de API, timeouts
  - [ ] Voice: reconocimiento en español, edge cases
  - [ ] Telegram: reconexión automática
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
- [ ] Unit tests para skills críticos
- [ ] ProGuard/R8 rules (ofuscación)

**Seguridad:**
- [ ] API keys: encriptar en EncryptedSharedPreferences
- [ ] Certificate pinning para API calls
- [ ] Input sanitization (prevenir injection)
- [ ] Permisos mínimos (audit AndroidManifest)
- [ ] File access: sandboxed directories only
- [ ] No API keys en logs

---

## ⭐ FASES FUTURAS

### Fase 18 — Onboarding & Polish
- [ ] Wizard de primera vez (3-4 pasos: API key, modelo, demo)
- [ ] Tour guiado de features
- [ ] Splash screen animada con la abejita

### Fase 19 — Social & Content
- [ ] Post creator (imagen + caption)
- [ ] Share directo a IG/Twitter/LinkedIn
- [ ] Content scheduler

### Fase 20 — LLM Local (on-device)
- [ ] llama.cpp / llama.rn en el celular
- [ ] Model manager (download/delete)
- [ ] Fallback: local → cloud
- [ ] 0 costo, 0 latencia, offline

### Fase 21 — Orquestación Multi-Agente
- [ ] Agentes que delegan a otros agentes
- [ ] Research → Writer → Publisher chain
- [ ] Modelo por agente especializado

### Fase 22 — RAG & Inteligencia
- [ ] Leer PDFs/docs locales como contexto
- [ ] Web research chain (buscar → leer → sintetizar)
- [ ] Resumen de emails largos

### Fase 23 — Play Store & Distribución
- [ ] Play Store listing + screenshots
- [ ] Políticas de privacidad
- [ ] Beta testing (10-20 testers)
- [ ] Landing page de descarga

### Fase 24 — Desktop (Electron)
- [ ] Electron app para Windows/Mac
- [ ] UI adaptada para pantalla grande

---

## 📊 Métricas

| Métrica | Valor |
|---|---|
| Versión | v3.7.3 |
| Skills | 30 |
| Pantallas | 12 |
| Providers LLM | 2 (OpenRouter + Ollama Cloud) |
| Modelos de visión | 6 |
| Fases completadas | 11 de 24 |
| Target BEE Smart v2.0 | 58 skills, 20 automations |
