package com.beemovil.skills

import com.beemovil.memory.BeeMemoryDB
import org.json.JSONArray
import org.json.JSONObject

/**
 * Memory skill — allows the agent to remember, recall, and forget facts.
 * This is the bridge between the LLM and the RAG memory system.
 */
class MemorySkill(private val db: BeeMemoryDB) : BeeSkill {
    override val name = "memory"
    override val description = """Manage persistent memories about the user. Actions:
- 'remember': Store a new fact (requires 'fact', optional 'category' and 'importance')
- 'recall': Search memories relevant to a query (requires 'query')
- 'list': List all stored memories
- 'forget': Delete a memory by ID (requires 'id')
- 'soul_set': Set a user profile attribute (requires 'key' and 'value')
- 'soul_get': Get all user profile attributes
- 'stats': Get memory statistics"""

    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["remember","recall","list","forget","soul_set","soul_get","stats"]},
            "fact":{"type":"string","description":"Fact to remember"},
            "query":{"type":"string","description":"Search query for recall"},
            "category":{"type":"string","enum":["personal","trabajo","preferencia","rutina","contacto","general"]},
            "importance":{"type":"integer","description":"1-10, how important is this fact"},
            "id":{"type":"integer","description":"Memory ID to forget"},
            "key":{"type":"string","description":"Soul profile key"},
            "value":{"type":"string","description":"Soul profile value"}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "")

        return when (action) {
            "remember" -> {
                val fact = params.optString("fact", "")
                if (fact.isBlank()) return JSONObject().put("error", "No fact provided")
                val category = params.optString("category", "general")
                val importance = params.optInt("importance", 5)
                db.addMemory(fact, category, importance)
                JSONObject()
                    .put("success", true)
                    .put("message", "Recordaré: $fact")
                    .put("total_memories", db.getMemoryCount())
            }

            "recall" -> {
                val query = params.optString("query", "")
                if (query.isBlank()) return JSONObject().put("error", "No query provided")
                val memories = db.retrieveMemories(query, limit = 5)
                val arr = JSONArray()
                memories.forEach { m ->
                    arr.put(JSONObject().apply {
                        put("id", m.id)
                        put("content", m.content)
                        put("category", m.category)
                        put("importance", m.importance)
                        put("match_score", m.matchScore)
                    })
                }
                JSONObject()
                    .put("memories", arr)
                    .put("count", memories.size)
                    .put("query", query)
            }

            "list" -> {
                val memories = db.getAllMemories()
                val arr = JSONArray()
                memories.take(20).forEach { m ->
                    arr.put(JSONObject().apply {
                        put("id", m.id)
                        put("content", m.content)
                        put("category", m.category)
                        put("importance", m.importance)
                    })
                }
                JSONObject()
                    .put("memories", arr)
                    .put("total", memories.size)
            }

            "forget" -> {
                val id = params.optInt("id", -1)
                if (id < 0) return JSONObject().put("error", "No memory ID provided")
                db.deleteMemory(id)
                JSONObject().put("success", true).put("message", "Memoria #$id eliminada")
            }

            "soul_set" -> {
                val key = params.optString("key", "")
                val value = params.optString("value", "")
                if (key.isBlank()) return JSONObject().put("error", "No key provided")
                db.setSoul(key, value)
                JSONObject().put("success", true).put("message", "Perfil actualizado: $key = $value")
            }

            "soul_get" -> {
                val soul = db.getAllSoul()
                val obj = JSONObject()
                soul.forEach { (k, v) -> obj.put(k, v) }
                JSONObject().put("profile", obj).put("attributes", soul.size)
            }

            "stats" -> {
                JSONObject()
                    .put("total_memories", db.getMemoryCount())
                    .put("soul_attributes", db.getAllSoul().size)
                    .put("recent_sessions", db.getRecentSessions(5).size)
            }

            else -> JSONObject().put("error", "Action '$action' not supported")
        }
    }
}
