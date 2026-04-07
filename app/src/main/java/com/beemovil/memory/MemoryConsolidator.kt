package com.beemovil.memory

import android.content.Context
import android.util.Log
import com.beemovil.llm.ChatMessage
import com.beemovil.llm.LlmFactory
import com.beemovil.security.SecurePrefs
import kotlinx.coroutines.*

/**
 * MemoryConsolidator — Background agent that extracts facts from conversations
 * and maintains the memory system's health.
 *
 * Architecture: 3-tier memory
 *   Layer 1: Working Memory (BeeAgent.messages — in RAM, max 40)
 *   Layer 2: Session Memory (conversations table — SQLite, auto-purge 30d)
 *   Layer 3: Long-Term Memory (memories + soul + action_log — SQLite)
 *
 * Runs asynchronously after conversations end (>3 turns).
 * Uses the user's configured LLM to extract semantic facts.
 */
object MemoryConsolidator {
    private const val TAG = "MemoryConsolidator"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConsolidating = false

    // ═══════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════

    /**
     * Trigger memory consolidation for a completed session.
     * Only runs if the session has >3 user messages (meaningful conversation).
     * Non-blocking — runs in IO coroutine.
     */
    fun consolidateSession(context: Context, sessionId: String) {
        if (isConsolidating) {
            Log.d(TAG, "Already consolidating, skipping")
            return
        }

        scope.launch {
            try {
                isConsolidating = true
                val db = BeeMemoryDB(context)
                val messages = db.getConversationHistory(sessionId, limit = 100)

                // Only consolidate meaningful conversations (>3 user turns)
                val userMessages = messages.count { it["role"] == "user" }
                if (userMessages < 3) {
                    Log.d(TAG, "Session $sessionId has only $userMessages user messages, skipping")
                    return@launch
                }

                Log.i(TAG, "Consolidating session $sessionId ($userMessages user turns)")

                // Step 1: Extract facts using LLM
                val extractedCount = extractFacts(context, db, messages, sessionId)

                // Step 2: Auto-populate soul profile from extracted memories
                updateSoulFromMemories(db)

                // Step 3: Prune old/low-importance memories (keep max 500)
                db.pruneOldMemories(maxCount = 500)

                // Step 4: Deduplicate similar memories
                db.deduplicateMemories()

                Log.i(TAG, "Consolidation complete: $extractedCount new facts extracted")

            } catch (e: Exception) {
                Log.e(TAG, "Consolidation failed: ${e.message}", e)
            } finally {
                isConsolidating = false
            }
        }
    }

    /**
     * Log a skill execution to the action_log table.
     */
    fun logAction(
        context: Context,
        agentId: String,
        skillName: String,
        paramsSummary: String,
        resultSummary: String,
        durationMs: Long,
        sessionId: String
    ) {
        scope.launch {
            try {
                val db = BeeMemoryDB(context)
                db.logAction(agentId, skillName, paramsSummary, resultSummary, durationMs, sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log action: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════
    // INTERNAL: Fact Extraction via LLM
    // ═══════════════════════════════════════

    private suspend fun extractFacts(
        context: Context,
        db: BeeMemoryDB,
        messages: List<Map<String, String>>,
        sessionId: String
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                // Build extraction prompt
                val prompt = db.buildMemoryExtractionPrompt(messages)

                // Get LLM provider
                val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                val securePrefs = SecurePrefs.get(context)
                val provider = prefs.getString("llm_provider", "ollama_cloud") ?: "ollama_cloud"
                val model = prefs.getString("llm_model", "") ?: ""
                val apiKey = when (provider) {
                    "openrouter" -> securePrefs.getString("openrouter_api_key", "") ?: ""
                    "ollama_cloud" -> securePrefs.getString("ollama_api_key", "") ?: ""
                    else -> ""
                }

                // Use a lightweight call (no tools, no streaming)
                val llm = LlmFactory.createProvider(provider, apiKey, model)
                val response = llm.complete(
                    listOf(ChatMessage("user", prompt)),
                    emptyList() // No tools
                )

                val llmResponse = response.text ?: return@withContext 0
                db.processExtractedMemories(llmResponse, sessionId)

            } catch (e: Exception) {
                Log.e(TAG, "Fact extraction failed: ${e.message}")
                0
            }
        }
    }

    // ═══════════════════════════════════════
    // INTERNAL: Soul Auto-Population
    // ═══════════════════════════════════════

    /**
     * Scan recent memories for personal facts and auto-populate soul profile.
     * Detects: name, email, company, phone, location, profession, preferences.
     */
    private fun updateSoulFromMemories(db: BeeMemoryDB) {
        try {
            val personalMemories = db.getAllMemories().filter {
                it.category in listOf("personal", "contacto", "preferencia", "trabajo")
            }

            for (mem in personalMemories) {
                val content = mem.content.lowercase()

                // Name detection
                if (content.contains("me llamo") || content.contains("mi nombre es") || content.contains("soy ")) {
                    val nameMatch = Regex("(?:me llamo|mi nombre es|soy )([A-ZÁÉÍÓÚa-záéíóú]+ ?[A-ZÁÉÍÓÚa-záéíóú]*)").find(mem.content)
                    nameMatch?.groupValues?.get(1)?.let { name ->
                        if (name.length > 2 && db.getSoul("name").isNullOrBlank()) {
                            db.setSoul("name", name.trim())
                            Log.i(TAG, "Soul auto-set: name = $name")
                        }
                    }
                }

                // Email detection
                val emailMatch = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}").find(mem.content)
                emailMatch?.value?.let { email ->
                    if (db.getSoul("email").isNullOrBlank()) {
                        db.setSoul("email", email)
                        Log.i(TAG, "Soul auto-set: email = $email")
                    }
                }

                // Company detection
                if (content.contains("trabajo en") || content.contains("mi empresa") || content.contains("empresa se llama")) {
                    val companyMatch = Regex("(?:trabajo en|mi empresa es|empresa se llama) ([A-ZÁÉÍÓÚa-záéíóú0-9 ]+)").find(mem.content)
                    companyMatch?.groupValues?.get(1)?.let { company ->
                        if (company.length > 2 && db.getSoul("company").isNullOrBlank()) {
                            db.setSoul("company", company.trim())
                            Log.i(TAG, "Soul auto-set: company = $company")
                        }
                    }
                }

                // Phone detection
                val phoneMatch = Regex("\\+?\\d{8,15}").find(mem.content)
                phoneMatch?.value?.let { phone ->
                    if (db.getSoul("phone").isNullOrBlank()) {
                        db.setSoul("phone", phone)
                        Log.i(TAG, "Soul auto-set: phone = $phone")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Soul auto-population failed: ${e.message}")
        }
    }
}
