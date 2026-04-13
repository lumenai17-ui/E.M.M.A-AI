# 🚀 ROADMAP: E.M.M.A. Ai v1.0 (Powered by Koog)

Este documento traza las fases precisas para convertir la herencia de Bee en una arquitectura modular limpia y potente.

## 🔴 FASE 1: Limpieza y Rebranding (The Purge) - [COMPLETADO]
*Meta: Obtener una aplicación "cascarón" que compile perfecto, tenga la estética de E.M.M.A., pero sin lógica vieja interfiriendo.*

- **Sub-fase 1.1: Identidad Base** [COMPLETADO]
- **Sub-fase 1.2: Extracción Quirúrgica** [COMPLETADO]
- **Sub-fase 1.3: Supervivencia del UI** [COMPLETADO]

---

## 🟡 FASE 2: La Fundación Koog (Brain Transplant) - [COMPLETADO]
*Meta: Establecer e inicializar el motor del agente usando herramientas estándar de la industria sobre Kotlin.*

- **Sub-fase 2.1: Dependencias y Estructura** [COMPLETADO]
- **Sub-fase 2.2: Inicialización del Motor (`EmmaEngine.kt`)** [COMPLETADO]
- **Sub-fase 2.3: Checkpointing y Memoria** [COMPLETADO]

---

## 🟢 FASE 3: Reconexión Sensorial (Rewiring) - [COMPLETADO]
*Meta: Volver a darle vida a la interfaz de usuario conectándola al nuevo cerebro.*

- **Sub-fase 3.1: Voice-to-Text / Micrófono:** [COMPLETADO]
- **Sub-fase 3.2: TTS Embebido Local:** [COMPLETADO] Auto-lectura de respuestas y fallback regional por IP.
- **Sub-fase 3.3: Internacionalización Visual:** [COMPLETADO] Archivos `strings.xml` globales y traducciones.

---

## 🔵 FASE 4: Capacidades Extendidas (Tools & A2A) - [COMPLETADO]
*Meta: El diferencial E.M.M.A. Poder delegar tareas a subsistemas asíncronos y rutear herramientas.*

- **Sub-fase 4.1: Navegador Inteligente como Herramienta:** [COMPLETADO] Despliegue de `BrowserChatPanel` en el puerto de UI interactivo.
- **Sub-fase 4.2: Arquitectura Multiagente (A2A Supervisor):** [COMPLETADO] Separación heurística con `EmmaEngine` fungiendo como Supervisor Ejecutivo sobre Agente Navegador y Agente Redactor.

---

## 🟣 FASE 5: Conectividad y Nube (COMPLETADO)
*Meta: "El Paso 1" (La Bóveda). Crear el almacén seguro de llaves maestras para romper el cascarón local del simulador 10.0.2.2 y abrir el proyecto.*

- **Sub-fase 5.1: Bóveda de Configuración (Settings API UI):** [COMPLETADO] Construir una pantalla de ajustes donde el usuario pueda escribir su API Key pública (OpenRouter / Koog API / Groq) o establecer la bandera cruzada de "Modelo Local Offline" (Gemma).
- **Sub-fase 5.2: Inyector de Headers Seguros:** [COMPLETADO] Actualizar `EmmaEngine` para usar tokens provistos por el `ApiConfigManager` (EncryptedSharedPreferences) dinámicamente.

---

## 🚀 FASE 5.5: Dinamismo de Modelos - [COMPLETADO]
*Meta: Refinamiento Premium de la interfaz de Settings para eliminar la escritura manual de IDs de Modelos mediante Auto-Fetching REST puro.*

- **Provider Chips:** [COMPLETADO] Integrar la clásica botonera Bee-Movil (OpenRouter, Ollama, Gemma, Custom).
- **Network Auto-Fetch:** [COMPLETADO] Autoselección de modelos basada en el proveedor seleccionado y guardado seguro con `EmmaEngine`.
- **Offline Link:** [COMPLETADO] Botón de gestión para HuggingFace y Gemma local.

---

## 🟠 FASE 6: Ecosistema Abierto de Plugins (A2A Skills) - [COMPLETADO]
*Meta: Estandarizar "El Paso 2" y "Paso 3" para inyectar capacidades ilimitadas a los LLMs (Ollama/OpenRouter/Local) sin quebrar el chat principal.*

- **Sub-fase 6.1: Interfaz Base de ToolDefinition:** [COMPLETADO] Construir un protocolo abstracto (`EmmaPlugin.kt`) que defina esquema JSON, manejador y limpieza de memoria para cada herramienta.
- **Sub-fase 6.2: Auto-Routing System (Tool Calling):** [COMPLETADO] Adaptar `EmmaEngine.kt` para enviar el array inyectado de `tools` en cada petición y ejecutar la intercepción de llamadas a funciones locales.
## ⚡ FASE 7: Control Total del Celular (Android God Mode) - [COMPLETADO]
*Meta: Convertir a E.M.M.A. Ai en un asistente de SO real, capaz de escuchar al usuario y controlar todo el ecosistema de periféricos, chips y datos del teléfono.*

- **Sub-fase 7.1: Sistema de Permisos Global & Voz:** [COMPLETADO] Rutina de permisos solicitando GPS, Micrófono, WiFi y Sensores. Mute switch bidireccional.
- **Sub-fase 7.2: Telemetría Arquitectónica:** [COMPLETADO] Worker de background corriendo cada 15 min, guardando métricas crudas en Room DB.
- **Sub-fase 7.3: Ingestión LLM:** [COMPLETADO] ToolCalling de Sensor de Red, Batería, Lux, GPS y Volumen listos para consumirse vía prompts.

---

## 💬 FASE 8: "The WhatsApp Experience" & Persistencia Viva - [COMPLETADO]
*Meta: Que la pantalla de chat se sienta como una app de mensajería AAA pulida, rápida y que no pierda nunca el contexto al cerrarse.*

- **Sub-fase 8.1: UI Insets & Fluidez Visual:** [COMPLETADO] Ajustar el teclado (ime padding) para que mueva la barra orgánicamente. Incorporar acciones de "Copiar", "Pegar" (ClipboardManager) en las burbujas de chat.
- **Sub-fase 8.2: Multimedia I/O Nativo:** [COMPLETADO] Activar íconos de adjuntar (FilePicker), enviar archivos, renderizar previews de imágenes directamente dentro de las burbujas y grabar Voicenotes nativos en la UI.
- **Sub-fase 8.3: Persistencia de Sesión Completa:** [COMPLETADO] Conectar el `ChatViewModel` a la base de datos `ChatHistoryDB`. Al salir de la app y regresar, la conversación debe recargarse intacta desde la base de datos, manteniendo activo al agente cargado.

---

## 🎛️ FASE 9: El Centro de Mando (Live Dashboard) - [COMPLETADO]
*Meta: Reformar el Home Screen. La mitad superior debe verse viva (mezcla de IA y Telemetría), la parte inferior aloja a tus accesos base.*

- **Sub-fase 9.1: Live Intelligence Matrix:** [COMPLETADO] Motor de Cápsulas Mutantes leyendo Batería, Seguridad de Red y Clima Inverso.
- **Sub-fase 9.2: Insight Automático:** [COMPLETADO] Prompt Ciego impulsado por `EnvironmentScanner.kt` para darle al usuario consejos contextuales.

---

## 👥 FASE 9.5: The Agent Swarm Hub (WhatsApp UX & A2A) - [COMPLETADO]
*Meta: Reemplazar el menú de chats planos por un "WhatsApp de IA". Soportar Múltiples Expertos, Pines y Orquestación de Automatizaciones por Grupos usando el Koog A2A Protocol.*

- **Sub-fase 9.5.1: Motor Relacional BD Room:** Crear esquema de llaves foráneas (`AgentConfig`, `ChatThread`, `GroupMember`).
- [x] **Relational Agent DB:** Desplegar base de datos multi-hilo (Room migrations).
- [x] **A2A Protocol Engine:** Mecanismo `Compressive Handoff` para enrutamiento local/api.
- [x] **WhatsApp-clone Hub:** Pantalla `ConversationsScreen` (Agentes en Pinned Scroll, Lista de chats/grupos, FAB).
- [x] **Routing Inteligente:** Combinación transparente de Koog, Ollama y APIs en un mismo flujo.

---

## 🛠️ FASE 10: Herramientas Nativas de Clase Mundial (Ultra-Skills) - [EN PROGRESO]
*Meta: Convertir a E.M.M.A. AI en la estación de trabajo móvil definitiva dotándola de I/O Multimodal, ejecución local determinista e integración a nivel Dios con las tripas de Android.*

- **Sub-fase 10.1: The Coding Sandbox (Cerebro Determinista)** [COMPLETADO]
  - Motor Mozilla Rhino (V8) corriendo en paralelo. Ejecuta cálculos lógicos o matemáticos puros en JS en milisegundos evitando las "alucinaciones" del LLM y retornando outputs precisos al chat.

- **Sub-fase 10.2: Módulo 1 "The Ingestors" (Lectura y Parseo Entrante) [COMPLETADO]**
  - La capacidad del agente de recibir cualquier archivo o formato y convertirlo en vectores/texto entendible para su análisis.
  - **Image Vision (OCR & Context):** Si envías una foto, E.M.M.A lee texto (OCR usando Google ML Kit) y el "Vision Model" describe de qué trata la imagen y su estructura sin que tengas que narrárselo.
  - **PDF Reader (Parser Extractor):** Extraer el contenido literal (tablas y párrafos) de documentos legajos, permitiendo resúmenes instantáneos de PDFs de decenas de páginas.
  - **Excel / CSV Reader:** Leer estructuradamente libros .xlsx, para extraer hojas, filas o buscar registros específicos en megadatos (Tablas de inventarios/precios).
  - **VoiceNote Ingestor profunda:** No solo convertir lo que dices, sino escuchar audios reenviados de WhatsApp por tu jefe para resumirlos.
  - **HTML DOM Reader (Web Scraper):** Abstraer una URL externa dada de internet, quitarle toda la chatarra (CSS, JS) y darle al agente sólo el contenido base de la noticia/artículo.
  - **Camera Live Stream:** Módulo de visión continua procesando frames a N FPS.

- **Sub-fase 10.3: Módulo 2 "The Generators" (Exportación y Creación Activa) [COMPLETADO]**
  - Pasar de respuestas conversacionales a "Entregables Corporativos".
  - **PDF Architect:** A partir de un borrador text/markdown devuelto por la IA, la app dibuja vectores un documento PDF con márgenes, colores u hojas membrete.
  - **Excel / CSV Builder:** Capacidad de que E.M.M.A estructure columnas de datos tabulados y escupa un archivo .csv u .xlsx directo sin abrir apps externas.
  - **HTML Landing Page Creator:** Programar el cascarón en HTML/CSS, guardarlo como un fichero interno, y abrir un WebView local (Visualizador) para que el usuario navegue el código Web generado (como la "Forja Web").
  - **Visual Chart Generator:** El Agente dibuja gráficos de barras o pasteles localmente tras analizar datos.
  - **HTML Email Formatter:** Diseñar un correo impecable con tablas HTML incrustadas directo en el Cliente de Correo.
  - **ICS Event Generator:** Creación de archivos de invitación de calendario (.ics).

- **Sub-fase 10.4: Módulo 3 "The Operators" (Acciones Sistema OS) [COMPLETADO]**
  - Enlace profundo con los servicios base del androide. Pidiendo directamente al SO que altere su estado.
  - **OS Contact Manager:** "Busca a Luis Felipe y dime su cel". (Lectura SQLite del sistema).
  - **Calendar Injector:** Agendar reuniones, ver la disponibilidad de los próximos lunes.
  - **Alarms & Timers:** "Avísame en 50 minutos para sacar el pavo". (Intent Nativo AlarmClock).
  - **Silent Background Executor:** Tareas diferidas (Cronjobs). "Chequea el clima todos los días a las 6 AM y mándamelo por texto".
  - **System Navigator (God Mode):** Prender linterna, activar WiFi, revisar estado térmico.

- **Sub-fase 10.5: Módulo 4 "The Networkers" (Comunicaciones y Web Activa) [COMPLETADO]**
  - **Send Email Direct:** Capacidad del agente de usar un protocolo SMTP o Intents para redactar y enviar sin intervención directa.
  - **Web API Fetcher Síncrono:** La habilidad del LLM de armar y mandar una llamada GET o POST a una API externa dada por el usuario "Ve a este endpoint de mi CRM y tráeme mis datos".
  - **WhatsApp Automator:** Redacción automática de mensajes vía Accessibility Service (Modo Super Dios) o Android Intents (Tú solo das a 'SEND').
  - **Bluetooth/IoT Commander:** (Habilidad Extrema): El LLM emitiendo comandos seriales base para dispositivos periféricos.

---

## 🚀 FASE 11: The Spaceship HUD & Proactive RAG - [COMPLETADO]
*Meta: Transformar el Dashboard estático en un Head-Up Display dinámico que respira telemetría viva, junto a un Agente Fantasma que predice necesidades usando información contextual profunda en segundo plano.*

- **Sub-fase 11.1: Eye Candy HUD & Authentic Telemetry:** [COMPLETADO]
  - **Aesthetic Polish:** Ralentización de animaciones, estelas difuminadas en el Radar, incrustación de animaciones meteorológicas (Sol/Nubes) en el termostato y fuentes `Monospace`.
  - **Authentic Telemetry (GPS Real):** Inyección de `play-services-location` para que el escáner capture las verdaderas coordenadas del teléfono erradicando el placeholder (CDMX).
- **Sub-fase 11.2: El Cerebro Silencioso (WorkManager Ghost):** [COMPLETADO]
  - Instanciar un Worker asíncrono que se despierte periódicamnete para recolectar telemetría local de hardware, escanear el `ChatHistoryDB` para recuperar los últimos temas y consultar rápidamente el `MediaStore` o Descargas para notificar archivos nuevos.
- **Sub-fase 11.3: The Deep Insight (Generador Contextual):** [COMPLETADO]
  - Condensar todos los datos (Clima, Mapas, Batería... ) en un megacompilado que el LLM consume para escribir el 'Insight'. Implementar Glassmorphism.
  - *Golden Polish:* Scroll vertical infinito en Dashboard nativo, Telemetría real síncrona (Open-Meteo), y candados lógicos Anti-Alucinaciones para la IA en segundo plano.

---

## 📡 FASE 11.5: Hermes Mobile Tunnel & Agent Hub Revival - [COMPLETADO]
*Meta: Interconectar E.M.M.A. con el servidor maestro Hermes a través de WebSockets persistentes y dotar de vida (Glassmorphism e interactividad real) al Agent Hub.*

- [x] **Sub-fase 11.5.1: UI/UX Agent Hub Polish:** Modificar `ConversationsScreen` para aplicar `Modifier.clickable` a los agentes (revivir botones), aplicar estilización Glassmorphism, y perfeccionar el FAB.
- [x] **Sub-fase 11.5.2: The Forge Reality Hook:** Conectar el `AgentFactorySheet` a las listas funcionales de Modelos en lugar de enrutadores estáticos.
- [x] **Sub-fase 11.5.3: Hermes Tunnel Protocol:** Implementar `HermesTunnelClient.kt` usando OkHttp WebSockets para bidireccionalidad.
- [x] **Sub-fase 11.5.4: Foreground Tunnel Service:** Levantar `TunnelService.kt` con permisos de bloqueo (Wake Lock) para mantener la conexión push.
- [x] **Sub-fase 11.5.5: EmmaEngine A2A Bridge:** Orquestaciones de delegación en `EmmaEngine` a través del flag A2A hacia WebSocket y toggle visual de estado.

---

## 🔍 FASE 12.5: Auditoría Forense UI/UX & Estabilización - [COMPLETADO]
*Meta: Revisar exhaustivamente cada pantalla, botón, flujo de datos y ciclo de vida de la app para garantizar que todo funcione como promete antes de producción.*

- **Ronda 1 — Funcionalidad Rota:** [COMPLETADO]
  - Persistencia de agentes (DAO conflict strategy), navegación por defecto (Dashboard), inyección de archivos al LLM, integridad de archivos generados.
- **Ronda 2 — Experiencia de Adjuntos:** [COMPLETADO]
  - Persistencia local de adjuntos, detección MIME, preview enriquecido, menú contextual "Abrir"/"Compartir".
- **Ronda 3 — Integración + Polish:** [COMPLETADO]
  - Share Target (ACTION_SEND), eliminación de pantallas fantasma, timestamps reales, clearAll con re-creación de threads.
- **Fase S-1 — Proveedor AI:** [COMPLETADO]
  - Provider/modelo restaurados de SharedPrefs al init (eliminado default "koog"/"llama3"). `updateApiKey()` conectado a SecurePrefs.
- **Fase S-2 — Voice Pipeline:** [COMPLETADO]
  - **Bug crítico:** ElevenLabs URL con `$` escapados → voz clonada jamás conectó. Corregida interpolación + `output_format` como query param. Null safety en fallback chain.
- **Fase S-3 — Integraciones Externas:** [COMPLETADO]
  - Telegram: stub crasheante desactivado, credenciales pre-guardadas. Email: `Thread {}` → coroutine segura, config de SMTP leída correctamente.
- **Fase S-4 — Branding + Polish:** [COMPLETADO]
  - Tema visual restaurado al reiniciar. Contadores reales del DB. Skills dinámicos (18 plugins). Branding "E.M.M.A. AI".

---

## 📈 FASE 13: The App Store Premium Launch (ROADMAP GTM)
*Meta: Orquestar el Deployment Comercial y el Modelo Open-Core.*

- [ ] Empaquetamiento final (`.aab` release) con optimización R8/Minify
- [ ] Firma Criptográfica (`.signingConfig`) y vinculación con BEE Smart Portal
- [ ] Implementación real del Telegram Bot Service (polling + foreground notification)
- [ ] Plugin de generación de imágenes (fal.ai / Together AI) — keys ya pre-guardadas en SecurePrefs
- [ ] Exportación de datos del chat (formato JSON/PDF)
- [ ] Publicación en Play Store

