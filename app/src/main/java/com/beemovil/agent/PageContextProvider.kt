package com.beemovil.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * PageContextProvider — Generates smart context about the current web page for the LLM.
 *
 * Features:
 * - Summarizes page state (URL, title, visible elements) for each agent turn
 * - Filters sensitive data (password fields never sent to LLM)
 * - Detects blockers: CAPTCHA, login forms, 2FA, cookie walls
 * - Detects loops: same URL + same action repeated 3x
 * - Suggests contextual actions based on site type
 */
object PageContextProvider {

    // Keywords that indicate blockers the agent can't handle alone
    private val CAPTCHA_KEYWORDS = listOf(
        "captcha", "recaptcha", "hcaptcha", "challenge", "robot", "verify you",
        "i'm not a robot", "human verification", "security check", "cloudflare"
    )
    private val LOGIN_KEYWORDS = listOf(
        "sign in", "log in", "login", "iniciar sesion", "iniciar sesión",
        "inicia sesion", "username", "password", "contraseña", "autenticar"
    )
    private val TWO_FA_KEYWORDS = listOf(
        "two-factor", "2fa", "verification code", "authenticator",
        "codigo de verificacion", "código de verificación", "OTP", "one-time"
    )
    private val COOKIE_WALL_KEYWORDS = listOf(
        "cookie", "accept cookies", "aceptar cookies", "privacy policy",
        "consent", "gdpr", "we use cookies"
    )

    /**
     * Generate a complete context string for the LLM about the current page.
     * This is injected before each agent decision.
     */
    fun generateContext(
        url: String,
        title: String,
        pageText: String,
        elements: List<PageElement>,
        memoryContext: String = ""
    ): String {
        val sb = StringBuilder()

        sb.appendLine("[PAGINA ACTUAL]")
        sb.appendLine("URL: ${url.take(200)}")
        sb.appendLine("Titulo: ${title.take(100)}")

        // Page text summary (first 800 chars, no passwords)
        val safeText = sanitizeText(pageText).take(800)
        sb.appendLine("Contenido: $safeText")

        // Interactive elements (filtered)
        if (elements.isNotEmpty()) {
            sb.appendLine("\n[ELEMENTOS INTERACTIVOS]")
            elements.take(15).forEach { el ->
                if (el.type == "password") {
                    sb.appendLine("- ${el.tag} [password] (selector: ${el.selector}) -- CAMPO OCULTO")
                } else {
                    val desc = buildString {
                        append("- ${el.tag}")
                        if (el.type.isNotBlank()) append(" type=${el.type}")
                        if (el.text.isNotBlank()) append(" \"${el.text.take(40)}\"")
                        if (el.placeholder.isNotBlank()) append(" placeholder=\"${el.placeholder.take(30)}\"")
                        if (el.selector.isNotBlank()) append(" (selector: ${el.selector})")
                    }
                    sb.appendLine(desc)
                }
            }
        }

        // Blocker detection
        val blocker = detectBlocker(pageText, elements)
        if (blocker != null) {
            sb.appendLine("\n[ALERTA] $blocker")
        }

        // Memory context from past visits
        if (memoryContext.isNotBlank()) {
            sb.appendLine("\n$memoryContext")
        }

        return sb.toString()
    }

    /**
     * Detect if the page has a blocker that requires human intervention.
     * Returns a description of the blocker, or null if none detected.
     */
    fun detectBlocker(pageText: String, elements: List<PageElement> = emptyList()): String? {
        val lowerText = pageText.lowercase()

        // CAPTCHA detection
        if (CAPTCHA_KEYWORDS.any { lowerText.contains(it) }) {
            return "CAPTCHA DETECTADO - Necesito ayuda humana para continuar. Resuelve el captcha y toca 'Listo'."
        }

        // 2FA detection
        if (TWO_FA_KEYWORDS.any { lowerText.contains(it) }) {
            return "VERIFICACION 2FA DETECTADA - Ingresa tu codigo de verificacion y toca 'Listo'."
        }

        // Login form detection (only if there are password fields)
        val hasPasswordField = elements.any { it.type == "password" }
        if (hasPasswordField && LOGIN_KEYWORDS.any { lowerText.contains(it) }) {
            return "FORMULARIO DE LOGIN DETECTADO - Inicia sesion manualmente y toca 'Listo'."
        }

        // Cookie wall
        if (COOKIE_WALL_KEYWORDS.count { lowerText.contains(it) } >= 2) {
            // Try to auto-accept cookies first (not a hard blocker)
            return null // Agent can try clicking "Accept"
        }

        return null
    }

    /**
     * Detect if the agent is stuck in a loop.
     * Returns true if the last 3 actions are identical.
     */
    fun detectLoop(actionHistory: List<Pair<String, String>>): Boolean {
        if (actionHistory.size < 3) return false
        val last3 = actionHistory.takeLast(3)
        if (last3.all { it == last3.first() }) {
            val action = last3.first().first
            // Whitelist safe repetitive actions (e.g., trying to delegate multiple times or screenshot)
            if (action == "screenshot" || action == "delegate_to_agent" || action == "call_remote_agent" || action == "search_web" || action == "send") {
                return false
            }
            return true
        }
        return false
    }

    /**
     * Generate the browser agent system prompt.
     */
    fun getBrowserAgentPrompt(): String = """
        Eres un agente de navegacion web integrado en la app Bee-Movil.
        Tienes control total del browser. En cada turno recibes el estado actual de la pagina.
        
        REGLAS:
        1. Siempre usa read_page PRIMERO para ver la pagina antes de actuar
        2. Usa get_elements para encontrar selectores CSS exactos antes de click/type
        3. NUNCA inventes selectores - usa solo los que ves en la pagina
        4. Si detectas CAPTCHA, login, o 2FA → PAUSA y pide ayuda al usuario
        5. Si no encuentras un elemento despues de 2 intentos → pregunta al usuario
        6. Maximo 40 pasos por tarea.
        7. Dirígete al usuario de manera amigable, casual y directa. Usa un tono cercano, nada robótico.
        8. Si te piden extraer información para exportarla, procesar datos y entregarlos como archivo, usa la herramienta save_to_file.
        9. NUNCA leas o envies campos de contrasena al usuario
        10. Si la pagina cambia inesperadamente, usa read_page para re-evaluar
        
        ACCIONES DISPONIBLES:
        - navigate(url): Navegar a URL
        - read_page: Leer contenido de la pagina actual
        - click(selector): Click en elemento (CSS selector o texto visible)
        - type(selector, text): Escribir en campo
        - fill_form(fields): Llenar multiples campos [{selector, value}]
        - extract_links: Obtener todos los links
        - extract_tables: Obtener datos de tablas
        - get_elements(selector?): Listar elementos interactivos
        - run_js(code): Ejecutar JavaScript
        - screenshot: Captura de pantalla
        - scroll_to(selector): Scroll a un elemento
        - wait_for(selector): Esperar a que aparezca un elemento
        - highlight(selector): Resaltar visualmente un elemento
        - back/forward: Navegacion historial
        - current: URL y titulo actual
        - save_to_file(filename, content): Guardar un archivo .txt, .md o .csv en Descargas con el contenido dado.
        - delegate_to_agent(agent_id, task): Enviar resumen y URLs a un agente local (ej: 'vision', 'sales', 'telegram')
        - call_remote_agent(agent_url, task): Enviar tareas a un nodo A2A externo o bot de servidor.
        
        FORMATO DE RESPUESTA:
        Piensa paso a paso. Usa una herramienta por turno. 
        Habla como humano mientras ejecutas herramientas (ej: "¡Claro! Voy a revisar esa página...").
        Si necesitas ayuda humana, responde: [NEED_HELP] seguido de la explicacion amigable.
        Si completaste la tarea: [TASK_DONE] seguido de tu resumen humano.
    """.trimIndent()

    /**
     * Sanitize page text — remove sensitive content before sending to LLM.
     */
    private fun sanitizeText(text: String): String {
        return text
            .replace(Regex("password[:\\s]*\\S+", RegexOption.IGNORE_CASE), "[PASSWORD OCULTO]")
            .replace(Regex("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"), "[TARJETA OCULTA]")
            .replace(Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b"), "[SSN OCULTO]")
    }
}

/**
 * PageElement — Represents an interactive element on the page.
 * Parsed from BrowserSkill.get_elements() JSON output.
 */
data class PageElement(
    val tag: String,
    val type: String = "",
    val name: String = "",
    val id: String = "",
    val placeholder: String = "",
    val value: String = "",
    val text: String = "",
    val selector: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): PageElement = PageElement(
            tag = json.optString("tag", ""),
            type = json.optString("type", ""),
            name = json.optString("name", ""),
            id = json.optString("id", ""),
            placeholder = json.optString("placeholder", ""),
            value = if (json.optString("type") == "password") "" else json.optString("value", ""),
            text = json.optString("text", ""),
            selector = json.optString("selector", "")
        )

        fun fromJsonArray(arr: JSONArray): List<PageElement> {
            val list = mutableListOf<PageElement>()
            for (i in 0 until arr.length()) {
                list.add(fromJson(arr.getJSONObject(i)))
            }
            return list
        }
    }
}
