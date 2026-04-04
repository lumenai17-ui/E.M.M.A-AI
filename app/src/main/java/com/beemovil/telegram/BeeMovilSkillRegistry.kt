package com.beemovil.telegram

import android.content.Context
import com.beemovil.memory.BeeMemoryDB
import com.beemovil.skills.*

/**
 * Registry that provides skills to the Telegram bot service.
 * Since the service can't directly access MainActivity's skill map,
 * we create a fresh set of skills here.
 */
object BeeMovilSkillRegistry {

    fun getSkills(context: Context): Map<String, BeeSkill> {
        val skills = mutableMapOf<String, BeeSkill>()

        // Skills that work without UI/Activity context (safe for background service)
        try { skills["calculator"] = CalculatorSkill() } catch (_: Throwable) {}
        try { skills["datetime"] = DateTimeSkill() } catch (_: Throwable) {}
        try { skills["weather"] = WeatherSkill() } catch (_: Throwable) {}
        try { skills["web_search"] = WebSearchSkill() } catch (_: Throwable) {}
        try { skills["battery_saver"] = BatterySaverSkill(context) } catch (_: Throwable) {}
        try { skills["qr_generator"] = QrGeneratorSkill() } catch (_: Throwable) {}

        // Memory (shared with main app)
        try {
            val memoryDB = BeeMemoryDB(context)
            skills["memory"] = com.beemovil.skills.MemorySkill(memoryDB)
        } catch (_: Throwable) {}

        return skills
    }
}
