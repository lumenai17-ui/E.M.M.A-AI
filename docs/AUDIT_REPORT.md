# Reporte de Auditoría: E.M.M.A. Agent Hub & A2A Routing

**Fecha de Auditoría:** 12 Abril 2026
**Objetivo:** Identificar la desconexión funcional y estética de la pantalla `ConversationsScreen.kt` (Agent Hub), diagnosticar el aislamiento de La Forja de Agentes, y evaluar la integración del nuevo túnel de red WebSocket (Hermes).

---

## 1. Auditoría de Fidelidad Visual (UI Gap)
**Archivo:** `app/src/main/java/com/beemovil/ui/screens/ConversationsScreen.kt`

**Diagnóstico Estético:**
La captura compartida tiene un diseño opaco debido al uso estricto de componentes Material 3 estándar sin el trabajo de estilización (glassmorphism) planeado. 
- **Carrusel de Expertos:** Se usa un rectangulo `Box` con `clip(CircleShape)` y color de fondo estricto `Color(0xFF1E1E2C)`. 
- **Ausencia de Neón:** No existen bordes luminosos ni sombras externas (`shadow`) que simulen el "glow" premium.
- **Tipografía:** Faltan contrastes en pesos de fuentes entre encabezados y subtítulos (se usa `11.sp` para nombres y se recorta bruscamente a 10 caracteres).

---

## 2. Auditoría Funcional: El Síndrome de los "Botones Muertos"
**Archivos:** `ConversationsScreen.kt`, `ChatViewModel.kt`

**Hallazgos Críticos:**
- **Inexistencia de Interactividad:** La razón principal por la que tocar a E.M.M.A., Live Vision, o un agente recién creado no hace absolutamente nada, es una ausencia arquitectónica simple pero letal: **Falta el `Modifier.clickable { ... }`**.
    - El `LazyRow` dibuja la `Column` del agente pero no despacha ningún evento.
    - El `LazyColumn` dibuja el `Row` del hilo conversacional pero tampoco es cliqueable.
- **Desconexión con la Navegación:** Al no haber un evento cliqueable, el estado de navegación `viewModel.currentScreen.value` jamás cambia a "chat", y nunca se inyecta el `ID` del agente seleccionado (Ej: `viewModel.prefillAgentChat(agentId, prompt)`).

---

## 3. Estado de La Forja y Catálogo de Modelos (AgentFactorySheet)
**Archivo:** `app/src/main/java/com/beemovil/ui/components/AgentFactorySheet.kt`

**Hallazgos:**
- **Modelos Hardcodeados:** La lista de modelos (`modelOptions`) se está extrayendo estáticamente de un stub interno (`com.beemovil.llm.ModelRegistry`). No están vinculados dinámicamente al estado en vivo del motor (`engine`).
- **Persistencia Aislada:** Cuando el usuario da `FORJAR AGENTE`, la función `viewModel.forgeAgent()` efectivamente inserta el agente en la base de datos local `Room` (`chatHistoryDB`), pero al estar "muerta" la interfaz (falta de Clicks), el agente nace pero nadie puede platicar con él.

---

## 4. Nuevo Alcance: Integración Hermes Mobile Tunnel (A2A Network)
**Archivos Afectados:** `EmmaEngine.kt`, Módulo `tunnel` (Inexistente).

**Impacto Arquitectónico:**
La arquitectura base de Koog Engine estaba enfocada a procesamiento offline en el dispositivo o llamadas REST a OpenRouter. El nuevo estudio `mobile-tunnel-emma-integration.md` introduce un cambio de paradigma gigante:
- **WebSocket Permanente:** Ahora E.M.M.A. actuará como esclavo/maestro satelital en segundo plano vía `HermesTunnelClient`.
- **Necesidad de UI Inmediata:** El `Agent Hub` (o la cabecera del dashboard superior) necesita alojar obligatoriamente un **Toggle (Interruptor) de Conexión de Túnel** para darle al usuario control sobre cuándo abrir el tubo de comunicación con el clúster remoto.
- **A2A Bidireccional:** El Agent Hub deberá ser capaz de mostrar notificaciones y tareas asíncronas recibidas por `Hermes`. No solo los chats generados por el usuario.

---

## VEREDICTO DE AUDITORÍA
La pantalla `ConversationsScreen` actualmente es solo una fachada inerte. Los datos existen debajo de la superficie (Base de datos Room guarda los enjambres), pero las tuberías entre la Interfaz Gráfica y el Motor `ChatViewModel` están cerradas (ausencia de bindings e intents). El diseño requiere refactorización para acoplar estilo glassmorphism, habilitar eventos de toque e integrar los controles de red del nuevo túnel de Hermes.
