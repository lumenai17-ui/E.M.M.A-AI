package com.beemovil.agent

/**
 * Pre-built workflow templates for common multi-step tasks.
 * Users can run these directly or use them as starting points for custom workflows.
 */
object WorkflowTemplates {

    val ALL: List<Workflow> by lazy {
        listOf(
            RESEARCH_TO_PDF,
            MORNING_BRIEF,
            CONTENT_CREATOR,
            QUOTE_AND_EMAIL,
            WEB_TO_LANDING,
            DAILY_DIGEST
        )
    }

    /** Research → Write article → Generate PDF */
    val RESEARCH_TO_PDF = Workflow(
        id = "research_pdf",
        name = "Investigar y crear PDF",
        icon = "🔍",
        description = "Busca en internet, redacta un artículo y genera un PDF profesional",
        isTemplate = true,
        steps = listOf(
            WorkflowStep(
                id = "s1", label = "Investigar en Internet", icon = "🌐",
                type = StepType.AGENT, agentId = "main",
                prompt = "Investiga sobre: {input}. Usa web_search para encontrar información actual. Dame un resumen completo con datos y fuentes."
            ),
            WorkflowStep(
                id = "s2", label = "Redactar artículo", icon = "✍️",
                type = StepType.AGENT, agentId = "main",
                prompt = "Con base en esta investigación, redacta un artículo profesional bien estructurado con título, subtítulos y conclusión:\n\n{input}"
            ),
            WorkflowStep(
                id = "s3", label = "Generar PDF", icon = "📄",
                type = StepType.SKILL, skillName = "generate_pdf",
                fixedParams = mapOf(
                    "title" to "Artículo de Investigación",
                    "content" to "{input}"
                )
            )
        )
    )

    /** Calendar + Weather + Device → Morning Brief */
    val MORNING_BRIEF = Workflow(
        id = "morning_brief",
        name = "Morning Brief Completo",
        icon = "☀️",
        description = "Revisa calendario, clima, batería y te da un resumen del día",
        isTemplate = true,
        steps = listOf(
            WorkflowStep(
                id = "s1", label = "Revisar agenda", icon = "📅",
                type = StepType.AGENT, agentId = "agenda",
                prompt = "Dame un resumen completo de los eventos de hoy y recordatorios pendientes."
            ),
            WorkflowStep(
                id = "s2", label = "Consultar clima", icon = "🌤️",
                type = StepType.AGENT, agentId = "main",
                prompt = "Consulta el clima actual con weather. Solo dame un resumen corto: temperatura, condición, si necesito paraguas."
            ),
            WorkflowStep(
                id = "s3", label = "Estado del teléfono", icon = "📱",
                type = StepType.AGENT, agentId = "main",
                prompt = "Usa device_info para revisar batería y almacenamiento. Dame un resumen breve."
            ),
            WorkflowStep(
                id = "s4", label = "Compilar briefing", icon = "☀️",
                type = StepType.AGENT, agentId = "main",
                prompt = """Compila un Morning Brief profesional con esta información:

AGENDA: {input}

Presenta todo como un briefing matutino conciso y motivador. Incluye un mensaje motivacional breve al final."""
            )
        )
    )

    /** Topic → Image → HTML post → Share */
    val CONTENT_CREATOR = Workflow(
        id = "content_creator",
        name = "Crear contenido visual",
        icon = "🎨",
        description = "Genera una imagen, crea un post HTML y prepara para compartir",
        isTemplate = true,
        steps = listOf(
            WorkflowStep(
                id = "s1", label = "Generar imagen", icon = "🖼️",
                type = StepType.AGENT, agentId = "creativo",
                prompt = "Genera una imagen profesional sobre: {input}. Usa image_gen con un prompt detallado en inglés optimizado para DALL-E."
            ),
            WorkflowStep(
                id = "s2", label = "Redactar post", icon = "✍️",
                type = StepType.AGENT, agentId = "main",
                prompt = "Con esta imagen generada, escribe un post corto y atractivo para redes sociales sobre el tema. Incluye emojis, hashtags relevantes. Resultado anterior: {input}"
            ),
            WorkflowStep(
                id = "s3", label = "Crear HTML visual", icon = "🌐",
                type = StepType.SKILL, skillName = "generate_html",
                fixedParams = mapOf(
                    "title" to "Post Visual",
                    "content" to "{input}"
                )
            )
        )
    )

    /** Cotización → PDF → Email */
    val QUOTE_AND_EMAIL = Workflow(
        id = "quote_email",
        name = "Cotización + Email",
        icon = "💼",
        description = "Genera una cotización profesional y la envía por email",
        isTemplate = true,
        steps = listOf(
            WorkflowStep(
                id = "s1", label = "Generar cotización", icon = "💼",
                type = StepType.AGENT, agentId = "ventas",
                prompt = "Genera una cotización profesional para: {input}. Incluye IVA 16%, vigencia 15 días, y número de cotización."
            ),
            WorkflowStep(
                id = "s2", label = "Crear PDF", icon = "📄",
                type = StepType.SKILL, skillName = "generate_pdf",
                fixedParams = mapOf(
                    "title" to "Cotización",
                    "content" to "{input}"
                )
            ),
            WorkflowStep(
                id = "s3", label = "Preparar email", icon = "📧",
                type = StepType.AGENT, agentId = "main",
                prompt = "La cotización fue generada y el PDF está listo. Dile al usuario que la cotización está lista y pregúntale a qué email quiere enviarla. Info: {input}"
            )
        )
    )

    /** URL → Scrape → Landing */
    val WEB_TO_LANDING = Workflow(
        id = "web_landing",
        name = "Web → Landing Page",
        icon = "🚀",
        description = "Analiza una URL y crea una landing page inspirada en ella",
        isTemplate = true,
        steps = listOf(
            WorkflowStep(
                id = "s1", label = "Analizar web", icon = "🌐",
                type = StepType.AGENT, agentId = "main",
                prompt = "Usa web_fetch para descargar y analizar esta URL: {input}. Extrae: título, propuesta de valor, colores, estructura, CTA principal."
            ),
            WorkflowStep(
                id = "s2", label = "Diseñar landing", icon = "🎨",
                type = StepType.AGENT, agentId = "main",
                prompt = "Con este análisis, genera el código HTML+CSS completo de una landing page profesional moderna. Usa un diseño limpio, responsive, con hero section, features y CTA. Usa generate_html para crear el archivo.\n\nAnálisis: {input}"
            )
        )
    )

    /** News + Calendar + Weather → Digest */
    val DAILY_DIGEST = Workflow(
        id = "daily_digest",
        name = "Digest diario completo",
        icon = "📰",
        description = "Noticias de tech, agenda, clima → email resumen",
        isTemplate = true,
        steps = listOf(
            WorkflowStep(
                id = "s1", label = "Buscar noticias", icon = "📰",
                type = StepType.AGENT, agentId = "main",
                prompt = "Busca las 5 noticias más importantes de tecnología de hoy usando web_search. Dame un resumen de cada una con fuente."
            ),
            WorkflowStep(
                id = "s2", label = "Revisar agenda", icon = "📅",
                type = StepType.AGENT, agentId = "agenda",
                prompt = "Dame los eventos del día de hoy del calendario."
            ),
            WorkflowStep(
                id = "s3", label = "Compilar digest", icon = "📋",
                type = StepType.AGENT, agentId = "main",
                prompt = """Compila un Daily Digest profesional con:

NOTICIAS Y AGENDA: {input}

Formato: 
📰 TOP 5 NOTICIAS TECH
📅 TU AGENDA DE HOY
💡 TIP DEL DÍA (algo útil de productividad)

Hazlo conciso pero informativo."""
            ),
            WorkflowStep(
                id = "s4", label = "Generar PDF", icon = "📄",
                type = StepType.SKILL, skillName = "generate_pdf",
                fixedParams = mapOf(
                    "title" to "Daily Digest",
                    "content" to "{input}"
                )
            )
        )
    )
}
