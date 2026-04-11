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

- **Sub-fase 8.1: UI Insets & Fluidez Visual:** [Point 1] Ajustar el teclado (ime padding) para que mueva la barra orgánicamente. Incorporar acciones de "Copiar", "Pegar" (ClipboardManager) en las burbujas de chat.
- **Sub-fase 8.2: Multimedia I/O Navivo:** [Point 1] Activar íconos de adjuntar (FilePicker), enviar archivos, renderizar previews de imágenes directamente dentro de las burbujas y grabar Voicenotes nativos en la UI.
- **Sub-fase 8.3: Persistencia de Sesión Completa:** [Point 2] Conectar el `ChatViewModel` a la base de datos `ChatHistoryDB`. Al salir de la app y regresar, la conversación debe recargarse intacta desde la base de datos, manteniendo activo al agente cargado.

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

## 🛠️ FASE 10: Herramientas Nativas de Clase Mundial (Ultra-Skills) - [REVISION PENDIENTE]
*Meta: Extender los plugins de E.M.M.A para operaciones complejas (Business y Code).*

- **Sub-fase 10.1: Exportador de Documentos:** [Point 4] Skill para generar archivos PDF estructurados localmente o páginas HTML y guardarlas en el almacenamiento nativo de Android.
- **Sub-fase 10.2: Coding Sandbox:** [Point 4] Área especial donde el LLM pueda tirar scripts en su propio ambiente o validar sintaxis de código de programación.
