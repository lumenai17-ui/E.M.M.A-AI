# E.M.M.A. AI - Feature & Capabilities Matrix 🐝

Este documento describe todas las capacidades implementadas en el **Enhanced Movil Multi Agent (E.M.M.A. AI)**.

## 🎛️ 1. Dynamic Dashboard & Telemetry
- [x] **Arquitectura Glassmorphism:** UI construida con Jetpack Compose y diseño premium oscuro.
- [x] **Scanner Ambiental (DeviceScanner):** Telemetría continua de Sensores (Luz, Magnético, Proximidad), Batería, Nivel de Carga, y Estado Térmico.
- [x] **Red y Conectividad:** Lectura en tiempo real de intensidad Wi-Fi/Datos móviles en el panel superior.
- [x] **Insights Dinámicos:** Los sensores alimentan un motor de reglas para disparar advertencias visuales (Ej: Iconos naranjas cuando la batería es baja o rojo si el teléfono está en sobrecalentamiento térmico).

## 🧠 2. The Agent Swarm Hub (Hub A2A)
- [x] **WhatsApp-like Experience:** Layout horizontal de *Pinned Experts* y lista vertical de conversaciones.
- [x] **Multi-Threading Real:** Room DB con `ChatThreadEntity` y `AgentConfigEntity` soportando memoria a largo plazo segmentada.
- [x] **Protocolo Koog-Engine (A2A - Agent to Agent):** Permite pasar el contexto de un agente a otro.
- [x] **Pre-Seed Automático:** Auto-inyecta a los pilares arquitectónicos ("E.M.M.A. Chat", "Live Vision" y "Deep Voice") en la primera instalación.

## 👁️ 3. Visual & Audio Engines
- [x] **LiveVision (Screen):** Interfaz para análisis visual en tiempo real usando el feed de la cámara.
- [x] **DeepVoice Manager:** Integración nativa con `Deepgram` para asimilar audio (STT / TTS) en muy baja latencia.

## 🛠️ 4. The Agent Factory (La Forja)
- [x] **Botón 'Forge':** Creación dinámica de avatares a través de un Bottom Sheet animado en Android.
- [x] **Editor de ADN:** Asignación de Nombre, Icono y *System Prompt* personalizado.
- [x] **Selector Dinámico del ModelRegistry:** Asignación de cerebro (Cloud, Ollama Local o Native Offline) conectando instantáneamente a las API correspondientes.

## 🤖 5. Red de Inferencia Homologada (`ModelRegistry`)
- [x] **OpenRouter (Cloud):** GPT-4o, Claude 3.5+, Gemini 2.5 Pro, Llama 3.3.
- [x] **Ollama (Local/Edge):** Gemma 4, Qwen 3 MoE, DeepSeek R1, Command R+.
- [x] **LiteRT Native:** Inferencia 100% aislada corriendo pesos nativos `.litertlm` (Gemma 4 E2B/E4B) garantizando privacidad offline.

---
_Cualquier adición a las habilidades (Fase 10 - Ultra-Skills) se marcará aquí al ser completada._
