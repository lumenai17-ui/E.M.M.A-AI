package com.beemovil.skills

import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.provider.Settings
import org.json.JSONObject

/**
 * Control phone volume and vibration.
 */
class VolumeSkill(private val context: Context) : BeeSkill {
    override val name = "volume"
    override val description = "Control phone volume or vibrate. Actions: 'get' (current volume), 'set' (requires 'level' 0-100), 'vibrate' (optional 'duration' ms), 'mute', 'unmute'"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["get","set","vibrate","mute","unmute"]},
            "level":{"type":"integer","description":"Volume level 0-100"},
            "duration":{"type":"integer","description":"Vibration duration in ms (default 500)"}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "get")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        return when (action) {
            "get" -> {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val percent = (current * 100) / max
                JSONObject()
                    .put("volume", percent)
                    .put("max", max)
                    .put("current", current)
                    .put("ringerMode", when (audioManager.ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                        else -> "normal"
                    })
            }
            "set" -> {
                val level = params.optInt("level", 50)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (level * max) / 100
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                JSONObject().put("success", true).put("volume", level).put("message", "Volumen: $level%")
            }
            "vibrate" -> {
                val duration = params.optLong("duration", 500)
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }
                    JSONObject().put("success", true).put("message", "Vibrando ${duration}ms")
                } else {
                    JSONObject().put("error", "Vibrator not available")
                }
            }
            "mute" -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                JSONObject().put("success", true).put("message", "[MUTE] Silenciado")
            }
            "unmute" -> {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max / 2, 0)
                JSONObject().put("success", true).put("message", "[VOL] Volumen restaurado")
            }
            else -> JSONObject().put("error", "Action not supported")
        }
    }
}
