package com.beemovil.llm

/**
 * ModelRegistry — Single source of truth for ALL available AI models.
 *
 * Every model across every provider is defined here with proper IDs,
 * capability tags, and categorization.
 *
 * To add a new model: just add a ModelEntry to the appropriate list.
 */
object ModelRegistry {

    data class ModelEntry(
        val id: String,
        val name: String,
        val provider: String,       // "openrouter" | "ollama" | "local"
        val category: Category,
        val free: Boolean = false,
        val hasVision: Boolean = false,
        val hasTools: Boolean = false,
        val hasThinking: Boolean = false,
        val sizeLabel: String = "",
        val description: String = ""
    )

    enum class Category(val label: String, val icon: String) {
        CHAT("Chat General", "💬"),
        CODE("Código", "💻"),
        VISION("Visión", "👁️"),
        REASONING("Razonamiento", "🧠"),
        AGENT("Agente", "🤖"),
        LOCAL("📱 Local", "📱")
    }

    // ═══════════════════════════════════════════════════════════
    // OPENROUTER MODELS
    // ═══════════════════════════════════════════════════════════

    val OPENROUTER = listOf(
        // ── Free tier ──
        ModelEntry("qwen/qwen3.6-plus:free", "Qwen 3.6+ (Free)", "openrouter",
            Category.CHAT, free = true, hasTools = true,
            sizeLabel = "MoE", description = "Mejor modelo gratuito. Coding + Razonamiento"),
        ModelEntry("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (Free)", "openrouter",
            Category.CHAT, free = true, hasTools = true,
            sizeLabel = "70B", description = "Meta's flagship open model"),
        ModelEntry("nvidia/nemotron-3-super:free", "Nemotron 3 Super (Free)", "openrouter",
            Category.AGENT, free = true, hasTools = true, hasThinking = true,
            sizeLabel = "120B MoE", description = "Optimizado para multi-agente"),
        ModelEntry("stepfun/step-3.5-flash:free", "Step 3.5 Flash (Free)", "openrouter",
            Category.REASONING, free = true, hasTools = true,
            sizeLabel = "MoE", description = "Razonamiento rápido"),
        ModelEntry("z-ai/glm-4.5-air:free", "GLM 4.5 Air (Free)", "openrouter",
            Category.AGENT, free = true, hasTools = true, hasThinking = true,
            sizeLabel = "Lite", description = "Modo thinking + agent"),
        ModelEntry("nvidia/nemotron-3-nano-30b:free", "Nemotron 3 Nano 30B (Free)", "openrouter",
            Category.CHAT, free = true, hasTools = true, hasThinking = true,
            sizeLabel = "30B", description = "Eficiente y preciso"),
        ModelEntry("google/gemma-3-27b-it:free", "Gemma 3 27B (Free)", "openrouter",
            Category.CHAT, free = true, hasVision = true,
            sizeLabel = "27B", description = "Google open-weight con visión"),

        // ── Premium ──
        ModelEntry("google/gemini-2.5-flash", "Gemini 2.5 Flash", "openrouter",
            Category.CHAT, hasTools = true,
            sizeLabel = "Pro", description = "Google Gemini — rápido y capaz"),
        ModelEntry("openai/gpt-4o", "GPT-4o", "openrouter",
            Category.CHAT, hasTools = true, hasVision = true,
            sizeLabel = "Pro", description = "OpenAI GPT-4o multimodal"),
        ModelEntry("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "openrouter",
            Category.CODE, hasTools = true,
            sizeLabel = "Pro", description = "Anthropic — excelente para código"),
        ModelEntry("anthropic/claude-4-sonnet", "Claude 4 Sonnet", "openrouter",
            Category.CODE, hasTools = true,
            sizeLabel = "Pro", description = "Anthropic Claude 4 — coding avanzado"),
        ModelEntry("google/gemini-2.5-pro", "Gemini 2.5 Pro", "openrouter",
            Category.REASONING, hasTools = true, hasThinking = true,
            sizeLabel = "Pro", description = "Google flagship — razonamiento profundo")
    )

    // ═══════════════════════════════════════════════════════════
    // OLLAMA CLOUD MODELS
    // IDs: use the exact tag format from ollama.com/search?c=cloud
    // ═══════════════════════════════════════════════════════════

    val OLLAMA_CLOUD = listOf(
        // ── Flagship / Chat ──
        ModelEntry("gemma4:cloud", "Gemma 4 (Vision + Audio)", "ollama",
            Category.CHAT, hasVision = true, hasTools = true, hasThinking = true,
            sizeLabel = "31B", description = "Google — visión, audio, tools, thinking"),
        ModelEntry("qwen3.5:cloud", "Qwen 3.5 (Vision + Tools)", "ollama",
            Category.CHAT, hasVision = true, hasTools = true, hasThinking = true,
            sizeLabel = "35B", description = "Alibaba multimodal flagship"),
        ModelEntry("kimi-k2.5:cloud", "Kimi K2.5 (Vision + Agent)", "ollama",
            Category.AGENT, hasVision = true, hasTools = true, hasThinking = true,
            sizeLabel = "MoE", description = "Moonshot AI — visión + agente nativo"),
        ModelEntry("glm-5:cloud", "GLM-5 (Reasoning)", "ollama",
            Category.REASONING, hasTools = true, hasThinking = true,
            sizeLabel = "744B/40B active", description = "Z.ai — razonamiento largo y agentes"),
        ModelEntry("minimax-m2.7:cloud", "MiniMax M2.7 (Code + Agent)", "ollama",
            Category.CODE, hasTools = true, hasThinking = true,
            sizeLabel = "MoE", description = "Coding y workflows agénticos"),
        ModelEntry("minimax-m2.5:cloud", "MiniMax M2.5", "ollama",
            Category.CODE, hasTools = true, hasThinking = true,
            sizeLabel = "MoE", description = "Productividad y código"),

        // ── Reasoning ──
        ModelEntry("nemotron-3-super:cloud", "Nemotron 3 Super 120B", "ollama",
            Category.REASONING, hasTools = true, hasThinking = true,
            sizeLabel = "120B MoE", description = "NVIDIA — multi-agente complejo"),
        ModelEntry("nemotron-3-nano:cloud", "Nemotron 3 Nano", "ollama",
            Category.CHAT, hasTools = true, hasThinking = true,
            sizeLabel = "30B", description = "NVIDIA — eficiente y preciso"),
        ModelEntry("qwen3-next:cloud", "Qwen 3 Next 80B", "ollama",
            Category.REASONING, hasTools = true, hasThinking = true,
            sizeLabel = "80B", description = "Qwen3 avanzado — eficiencia + velocidad"),
        ModelEntry("deepseek-r1:cloud", "DeepSeek R1 (Reasoning)", "ollama",
            Category.REASONING, hasTools = true, hasThinking = true,
            sizeLabel = "671B", description = "Razonamiento profundo tipo O3"),

        // ── Code ──
        ModelEntry("qwen3-coder-next:cloud", "Qwen3 Coder Next", "ollama",
            Category.CODE, hasTools = true,
            sizeLabel = "Cloud", description = "Optimizado para agentic coding"),
        ModelEntry("devstral-small-2:cloud", "Devstral Small 2 (24B)", "ollama",
            Category.CODE, hasVision = true, hasTools = true,
            sizeLabel = "24B", description = "Mistral — explorar código + multi-file"),
        ModelEntry("devstral-2:cloud", "Devstral 2 (123B)", "ollama",
            Category.CODE, hasTools = true,
            sizeLabel = "123B", description = "Mistral flagship dev agent"),

        // ── Vision ──
        ModelEntry("qwen3-vl:cloud", "Qwen3 VL (Vision)", "ollama",
            Category.VISION, hasVision = true, hasTools = true, hasThinking = true,
            sizeLabel = "Cloud", description = "Modelo de visión más potente de Qwen"),
        ModelEntry("ministral-3:cloud", "Ministral 3 (Edge Vision)", "ollama",
            Category.VISION, hasVision = true, hasTools = true,
            sizeLabel = "3-14B", description = "Mistral edge — liviano con visión"),

        // ── General (can run local or cloud) ──
        ModelEntry("llama3.3", "Llama 3.3 70B", "ollama",
            Category.CHAT, hasTools = true,
            sizeLabel = "70B", description = "Meta flagship open"),
        ModelEntry("qwen3", "Qwen 3 32B", "ollama",
            Category.CHAT, hasTools = true, hasThinking = true,
            sizeLabel = "32B", description = "Alibaba dense model"),
        ModelEntry("qwen3:235b", "Qwen 3 235B (MoE)", "ollama",
            Category.REASONING, hasTools = true, hasThinking = true,
            sizeLabel = "235B MoE", description = "Alibaba MoE grande"),
        ModelEntry("gemma3:27b", "Gemma 3 27B", "ollama",
            Category.CHAT, hasVision = true,
            sizeLabel = "27B", description = "Google open-weight anterior"),
        ModelEntry("mistral", "Mistral 7B", "ollama",
            Category.CHAT, hasTools = true,
            sizeLabel = "7B", description = "Modelo base ligero"),
        ModelEntry("command-r-plus", "Command R+ 104B", "ollama",
            Category.CHAT, hasTools = true,
            sizeLabel = "104B", description = "Cohere enterprise"),
        ModelEntry("phi4", "Phi-4 14B", "ollama",
            Category.REASONING,
            sizeLabel = "14B", description = "Microsoft — razonamiento compacto"),
        ModelEntry("rnj-1:cloud", "RNJ-1 8B (STEM)", "ollama",
            Category.CODE, hasTools = true,
            sizeLabel = "8B", description = "Essential AI — código y STEM"),

        // ── Vision-only models (camera/inline) ──
        ModelEntry("llava", "LLaVA 7B", "ollama",
            Category.VISION, hasVision = true,
            sizeLabel = "7B", description = "Vision encoder + Vicuna"),
        ModelEntry("llama3.2-vision", "Llama 3.2 Vision 11B", "ollama",
            Category.VISION, hasVision = true,
            sizeLabel = "11B", description = "Meta VLM"),
        ModelEntry("moondream", "Moondream 2", "ollama",
            Category.VISION, hasVision = true,
            sizeLabel = "2B", description = "Ultra-ligero para visión")
    )

    // ═══════════════════════════════════════════════════════════
    // LOCAL ON-DEVICE MODELS
    // ═══════════════════════════════════════════════════════════

    val LOCAL = listOf(
        ModelEntry("gemma4-e2b", "⚡ Gemma 4 E2B (Rápido)", "local",
            Category.LOCAL,
            sizeLabel = "~2.6 GB", description = "Optimizado para velocidad. Chat general."),
        ModelEntry("gemma4-e4b", "🧠 Gemma 4 E4B (Inteligente)", "local",
            Category.LOCAL,
            sizeLabel = "~3.7 GB", description = "Mayor razonamiento. Requiere más RAM.")
    )

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    /** Get all models for a specific provider */
    fun getModelsForProvider(provider: String): List<ModelEntry> = when (provider) {
        "openrouter" -> OPENROUTER
        "ollama" -> OLLAMA_CLOUD
        "local" -> LOCAL
        else -> OPENROUTER
    }

    /** Get all vision-capable models (for CameraScreen etc) */
    fun getVisionModels(): List<ModelEntry> {
        return (OLLAMA_CLOUD + OPENROUTER).filter { it.hasVision }
    }

    /** Get models grouped by category for a provider */
    fun getModelsGrouped(provider: String): Map<Category, List<ModelEntry>> {
        return getModelsForProvider(provider).groupBy { it.category }
    }

    /** Find a model by ID across all providers */
    fun findModel(modelId: String): ModelEntry? {
        return (OPENROUTER + OLLAMA_CLOUD + LOCAL).find { it.id == modelId }
    }

    /** Convert to LlmFactory.ModelOption for backward compat */
    fun ModelEntry.toModelOption(): LlmFactory.ModelOption {
        return LlmFactory.ModelOption(id = id, name = name, free = free)
    }

    fun getModelOptions(provider: String): List<LlmFactory.ModelOption> {
        return getModelsForProvider(provider).map { it.toModelOption() }
    }
}
