package com.beemovil.memory

import android.content.Context

class BeeMemoryDB(context: Context) {
    private val prefs = context.getSharedPreferences("bee_memory_db", Context.MODE_PRIVATE)

    fun getMemoryCount(): Int {
        return prefs.all.size
    }

    fun setSoul(key: String, value: String) {
        prefs.edit().putString("soul_$key", value).apply()
    }

    fun getSoul(key: String): String? {
        return prefs.getString("soul_$key", null)
    }
    
    fun saveMemory(memoryFragment: String) {
        val count = prefs.all.keys.count { it.startsWith("mem_") }
        prefs.edit().putString("mem_$count", memoryFragment).apply()
    }

    fun getAllMemories(): List<String> {
        val memories = mutableListOf<String>()
        prefs.all.forEach { (k, v) ->
            if (k.startsWith("mem_") && v is String) {
                memories.add(v)
            }
        }
        return memories
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
