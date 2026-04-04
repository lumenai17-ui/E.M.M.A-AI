package com.beemovil.skills

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import org.json.JSONObject
import java.util.Calendar

/**
 * AlarmSkill — set alarms and timers using Android system.
 */
class AlarmSkill(private val context: Context) : BeeSkill {
    override val name = "alarm"
    override val description = "Set alarms or timers. Actions: 'set_alarm' (requires 'hour' 0-23, 'minute' 0-59, optional 'label'), 'set_timer' (requires 'seconds', optional 'label'), 'show_alarms'"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["set_alarm","set_timer","show_alarms"]},
            "hour":{"type":"integer","description":"Hour 0-23"},
            "minute":{"type":"integer","description":"Minute 0-59"},
            "seconds":{"type":"integer","description":"Timer duration in seconds"},
            "label":{"type":"string","description":"Alarm/timer label"}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "set_alarm")

        return when (action) {
            "set_alarm" -> {
                val hour = params.optInt("hour", 8)
                val minute = params.optInt("minute", 0)
                val label = params.optString("label", "Bee-Movil Alarm")
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    JSONObject()
                        .put("success", true)
                        .put("message", "⏰ Alarma configurada: ${String.format("%02d:%02d", hour, minute)} - $label")
                } catch (e: Exception) {
                    JSONObject().put("error", "No se pudo crear alarma: ${e.message}")
                }
            }
            "set_timer" -> {
                val seconds = params.optInt("seconds", 60)
                val label = params.optString("label", "Bee-Movil Timer")
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    val min = seconds / 60
                    val sec = seconds % 60
                    JSONObject()
                        .put("success", true)
                        .put("message", "⏱️ Timer: ${min}m ${sec}s - $label")
                } catch (e: Exception) {
                    JSONObject().put("error", "No se pudo crear timer: ${e.message}")
                }
            }
            "show_alarms" -> {
                try {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    JSONObject().put("success", true).put("message", "Mostrando alarmas")
                } catch (e: Exception) {
                    JSONObject().put("error", "Error: ${e.message}")
                }
            }
            else -> JSONObject().put("error", "Action not supported")
        }
    }
}
