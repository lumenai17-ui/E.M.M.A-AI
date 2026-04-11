# E.M.M.A. AI V2 (Agent Swarm Hub)

![E.M.M.A. Hero](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

**E.M.M.A. AI (Enhanced Movil Multi Agent)** es un ecosistema operativo Android nativo para la orquestación, gestión y despliegue de enjambres de Inteligencia Artificial (Agent Swarm). Construida bajo un enfoque local y de nube híbrida, esta plataforma actúa como tu propio centro de control A2A (Agent-To-Agent).

## 🚀 Características Principales (Fase 9+)

*   **Dynamic Dashboard (Centro de Mando):** Panel en tiempo real construido con Jetpack Compose y Glassmorphism (Deep Space Black) que consolida la telemetría del dispositivo (batería, red, ubicación) usando un `EnvironmentScanner` para generar *Insights Automáticos*.
*   **The Agent Swarm Hub (WhatsApp-like UX):** Una interfaz nativa para la orquestación distribuida. Aloja agentes "Pineados" y mantiene hilos de conversación (Threads).
*   **A2A Protocol & Compressive Handoff:** Orquestador de enrutamiento impulsado por `Koog Engine`. Permite a múltiples agentes (ej. Investigador + Redactor) pasarse la memoria y procesar tareas secuencialmente bajo un *Single Thread*.
*   **On-Device AI (Ollama + Gemma 4):** Soporte de modelos en el propio dispositivo (Native Offline) descargando `.litertlm` (como Gamma 4 E2B/E4B) garantizando la privacidad absoluta usando inferencia LiteRT sin conexión.
*   **The Agent Factory (La Forja):** Pantalla de parametrización para crear inteligencias a la medida inyectando System Prompts y seleccionando de un registro validado de cerebros en la Nube (GPT-4o, Claude 3) o nativos locales.

## 🛠 Arquitectura Técnica

*   **Lenguaje:** Kotlin + Jetpack Compose UI
*   **Base de Datos Relacional:** Room Database V2 (Multi-Thread Relacional)
    *   `AgentConfigEntity` -> Almacén de AND/Firmas de Agentes
    *   `ChatThreadEntity` -> Registro de Flujos Compartidos
*   **Red de Proveedores (`ModelRegistry`):**
    *   *OpenRouter API* para modelos LLM de Nube (Anthropic, OpenAI, Meta).
    *   *Deepgram* para asimilación de Voz (Speech-To-Text / Text-To-Speech) en Ultra Baja Latencia.
    *   *HuggingFace / LiteRT* para pesos reducidos corriendo en CPUs ARM.

## ⚠️ Estado del Proyecto
*   ✅ Fase 8: Telemetría & LiveVision Completada.
*   ✅ Fase 9: Dashboard Matrix Completada.
*   ✅ Fase 9.5 & 9.6: The Agent Swarm Hub y Agent Factory Construidos.
*   ⏳ Fase 10: *Herramientas Nativas de Clase Mundial (Ultra-Skills)* - En Espera.

---

*Arquitecto: **Usuario / LumenAI***
*Powered by Koog Engine y Bee-Movil Vision.*
