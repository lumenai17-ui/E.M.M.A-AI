# 🧠 E.M.M.A. Ai - Product & Feature Tracker

Este documento es el registro maestro ("Single Source of Truth") de las capacidades, features y dependencias del sistema E.M.M.A. Ai. Llevamos este control para coordinar las fases de marketing, validación de MVP y eventual publicación en tiendas.

## 🎯 Capacidades y Features (MVP v1.0)

### 1. Núcleo Inteligente (Agentic Engine)
- **Koog Core Architecture:** Framework base para Multi-agentes y protocolos escalables asíncronos.
- **Supervisor Routing (Inferencia de 2 pasos):** Emma actúa como Directora del Sistema. No resuelve consultas pesadas; evalúa si debe delegarlas a subsistemas o responder casualmente, reduciendo alucinaciones y el costo computacional.
- **Ecosistema Multi-Agente (Protocolo A2A Activo):**
  - *Agente Especialista en Navegación:* Aislado y condicionado para interactuar solamente con peticiones de búsqueda.
  - *Agente Especialista Redactor:* Perfil ajustado para redacción corporativa, correos y ensayos de largo formato.
- **Soporte Llama 3 Local:** Pipeline seguro vía OkHttp apuntando a `localhost` (`10.0.2.2:11434`), garantizando cero fuga de privacidad en nube. Capacidad de enchufarse inmediatamente a Vertex AI o Koog API Rest.

### 2. Reconexión Sensorial (Sensory Rewiring)
- **Speech-To-Text (Oídos interactivos):** Soporte de dictado por voz natural que se inyecta directamente al circuito de pensamiento del Engine. Soporta ruteo local o envío a Deepgram (Nova-2).
- **Auto-Lectura TTS (Boca robótica):** El sistema detecta cuando Ollama termina de emitir su respuesta en milisegundos y la asimila con sonido en el dispositivo del usuario.
- **Fallback Sensorial Adaptativo:** El idioma primario se adapta dinámicamente (`Locale.getDefault()`); usa el TTS general de Android de forma gratuita y fluida, saltando a servidores premium de Deepgram (Modelo "Aura") si el idioma es Inglés.

### 3. Interfaz Visual e Inmersión UX
- **Chat Interactivo Compose:** Interfaz gráfica Neón híbrida (*BeeYellow* & *BeeBlack*) construida en Jetpack Compose puro con "Lazy Columns" reactivas para dibujar burbujas conversacionales hiper-rápidas.
- **Tool Calling (Componentes Modulares Dinámicos):** 
  - La IA tiene el poder de llamar a la herramienta `open_browser`.
  - Esta herramienta gatilla el `BrowserChatPanel`: Un sistema *ModalBottomSheet* que despliega un Webview embebido cubriendo el 75% de la pantalla. El usuario navega la web sin desconectarse visualmente de la plática principal en el 25% sobrante del fondo superior.
- **Internacionalización Dinámica (i18n):** Todo el esqueleto del App (Textos base, alertas, tooltips) migrado a base XML dual (Inglés y Español) que permuta su apariencia traduciéndose en vivo según el teléfono del cliente internacional.

---

## ⚖️ Licencias y Stack Tecnológico (Compliance de Publicación)

Antes de subir a Google Play Store / Web, este es el estatus legal y técnico de nuestras dependencias activas:

| Componente | Uso en E.M.M.A. | Licencia | Consideraciones de Publicación |
| :--- | :--- | :--- | :--- |
| **Android SDK / Jetpack Compose** | Ecosistema UI Visual | Apache 2.0 | 100% Libre para comercialización. |
| **Kotlin (Coroutines 1.10.x)** | Asincronismo de Tool Calling | Apache 2.0 | 100% Libre para comercialización. |
| **Koog Framework (`ai.koog`)** | Enrutador Agentic y DSL A2A | Apache 2.0 / MIT | Verificar términos de distribución abiertos del repositorio Koog. Normalmente abierto y seguro. |
| **OkHttp3** | Conector Local (Ollama) HTTP | Apache 2.0 | Libre de uso. Retener el aviso legal interno. |
| **Ollama Engine** | Inferencia Backend Local | MIT License | Se puede usar libremente. *Nota: E.M.M.A sólo se conecta a él, no lo embebe en su .APK directamente.* |
| **Llama 3 (Meta)** | Modelos Generativos de prueba | Meta Llama 3 License | Gratuito comercialmente si se está bajo los ~700 millones de usos. Obliga a poner *"Powered by Meta Llama 3"* en tus créditos/configuraciones. |
| **Deepgram API** | Voz (TTS Aura / STT Nova) | Propiedad API / SaaS | La librería/HTTP es tuya, pero usar la API en producción requerirá que pagues y gestiones facturación comercial B2B con ellos directamente. |

---
*Documento mantenido activamente. Última actualización: FASE 4 completada (El Supervisor).*
