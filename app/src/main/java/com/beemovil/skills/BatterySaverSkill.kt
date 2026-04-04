package com.beemovil.skills

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject

/**
 * BatterySaverSkill — detailed battery information and saving tips.
 */
class BatterySaverSkill(private val context: Context) : BeeSkill {
    override val name = "battery_saver"
    override val description = "Get detailed battery info and saving tips. Action: 'status' (current battery info with tips)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["status"]}
        }}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percent = (level * 100) / scale

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val chargeSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Cargador AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Inalámbrico"
                else -> "No conectado"
            }

            val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            val voltage = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f

            val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val healthStr = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "⚠️ Sobrecalentamiento"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "⚠️ Sobrevoltaje"
                BatteryManager.BATTERY_HEALTH_COLD -> "❄️ Fría"
                else -> "Desconocida"
            }

            val technology = batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"

            // Generate tips based on current state
            val tips = mutableListOf<String>()
            if (percent < 20 && !isCharging) {
                tips.add("🔴 Batería baja. Activa el ahorro de batería.")
                tips.add("Reduce el brillo de pantalla al mínimo.")
                tips.add("Desactiva WiFi y Bluetooth si no los usas.")
                tips.add("Cierra apps en segundo plano.")
            } else if (percent < 50) {
                tips.add("🟡 Batería media. Modera el uso de apps pesadas.")
                tips.add("Considera reducir el brillo.")
            } else {
                tips.add("🟢 Batería en buen nivel.")
            }
            if (temp > 40) {
                tips.add("⚠️ Temperatura alta (${temp}°C). Evita cargar y jugar al mismo tiempo.")
            }

            val emoji = when {
                isCharging -> "🔌"
                percent > 80 -> "🔋"
                percent > 50 -> "🔋"
                percent > 20 -> "🪫"
                else -> "🪫"
            }

            JSONObject()
                .put("level", percent)
                .put("charging", isCharging)
                .put("charge_source", chargeSource)
                .put("temperature", temp)
                .put("voltage", voltage)
                .put("health", healthStr)
                .put("technology", technology)
                .put("tips", org.json.JSONArray(tips))
                .put("message", "$emoji Batería: $percent% | ${if (isCharging) "Cargando ($chargeSource)" else "Descargando"} | ${temp}°C | Salud: $healthStr")
        } catch (e: Exception) {
            JSONObject().put("error", "Battery error: ${e.message}")
        }
    }
}
