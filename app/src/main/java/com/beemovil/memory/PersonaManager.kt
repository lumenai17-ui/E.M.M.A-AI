package com.beemovil.memory

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * PersonaManager — Fase 1: Arquitectura de Identidad (El Alma)
 * 
 * Reemplaza a BeeMemoryDB. Gestiona la identidad de E.M.M.A. y del usuario
 * mediante 3 archivos JSON físicos, legibles y auditables:
 * - soul.json: Quién es Emma (nombre, propósito, reglas de oro).
 * - heart.json: Historia emocional y dinámica con el usuario.
 * - user_profile.json: Perfil del usuario (gustos, contactos, preferencias).
 */
class PersonaManager(private val context: Context) {

    private val TAG = "PersonaManager"
    private val personaDir: File by lazy {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "EMMA_Persona")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val soulFile = File(personaDir, "soul.json")
    private val heartFile = File(personaDir, "heart.json")
    private val userFile = File(personaDir, "user_profile.json")

    init {
        initializeFilesIfMissing()
    }

    private fun initializeFilesIfMissing() {
        if (!soulFile.exists()) {
            val baseSoul = JSONObject().apply {
                put("name", "E.M.M.A.")
                put("mission", "Asistente inteligente proactivo y guardián del usuario.")
                put("tone", "Profesional, amigable, concisa y extremadamente eficiente.")
                put("golden_rules", listOf(
                    "Nunca asumas información crítica sin verificar.",
                    "Protege la privacidad del usuario sobre todas las cosas.",
                    "Sé proactiva: si algo se puede automatizar, ofrécelo."
                ))
            }
            soulFile.writeText(baseSoul.toString(4))
        }

        if (!heartFile.exists()) {
            val baseHeart = JSONObject().apply {
                put("creation_date", System.currentTimeMillis())
                put("trust_level", "building")
                put("milestones", listOf("Inicialización del núcleo de memoria base."))
                put("active_projects", JSONObject())
            }
            heartFile.writeText(baseHeart.toString(4))
        }

        if (!userFile.exists()) {
            val baseUser = JSONObject().apply {
                put("name", "Usuario")
                put("preferences", JSONObject())
                put("known_facts", JSONObject())
            }
            userFile.writeText(baseUser.toString(4))
        }
    }

    // --- Lectura Segura ---
    
    suspend fun readSoul(): String = withContext(Dispatchers.IO) {
        if (soulFile.exists()) soulFile.readText() else "{}"
    }

    suspend fun readHeart(): String = withContext(Dispatchers.IO) {
        if (heartFile.exists()) heartFile.readText() else "{}"
    }

    suspend fun readUserProfile(): String = withContext(Dispatchers.IO) {
        if (userFile.exists()) userFile.readText() else "{}"
    }

    // --- Escritura y Actualización (Tools) ---

    suspend fun updateUserFact(category: String, factKey: String, factValue: Any): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = JSONObject(readUserProfile())
            val facts = current.optJSONObject(category) ?: JSONObject()
            facts.put(factKey, factValue)
            current.put(category, facts)
            userFile.writeText(current.toString(4))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user fact: ${e.message}")
            false
        }
    }

    suspend fun addHeartMilestone(milestone: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = JSONObject(readHeart())
            val milestones = current.optJSONArray("milestones") ?: org.json.JSONArray()
            milestones.put(milestone)
            current.put("milestones", milestones)
            heartFile.writeText(current.toString(4))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding heart milestone: ${e.message}")
            false
        }
    }

    /** Migración desde el viejo BeeMemoryDB */
    suspend fun migrateFromOldDb(oldMemories: List<String>) = withContext(Dispatchers.IO) {
        if (oldMemories.isEmpty()) return@withContext
        try {
            val current = JSONObject(readUserProfile())
            val legacy = current.optJSONArray("legacy_memories") ?: org.json.JSONArray()
            oldMemories.forEach { legacy.put(it) }
            current.put("legacy_memories", legacy)
            userFile.writeText(current.toString(4))
            Log.i(TAG, "Migrated ${oldMemories.size} old memories.")
        } catch (e: Exception) {
            Log.e(TAG, "Migration error: ${e.message}")
        }
    }
}
