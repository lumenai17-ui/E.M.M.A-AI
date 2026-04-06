package com.beemovil.skills

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import org.json.JSONObject

/**
 * MusicControlSkill — control any music player (Spotify, YouTube Music, etc.)
 * via media key events.
 */
class MusicControlSkill(private val context: Context) : BeeSkill {
    override val name = "music_control"
    override val description = "Control music playback. Actions: 'play', 'pause', 'toggle' (play/pause), 'next', 'previous', 'stop'"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["play","pause","toggle","next","previous","stop"]}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "toggle")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val keyCode = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return JSONObject().put("error", "Unknown action: $action")
        }

        return try {
            // Send media key event
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)

            val emoji = when (action) {
                "play" -> "[PLAY]"
                "pause" -> "[PAUSE]"
                "toggle" -> "[TOGGLE]"
                "next" -> "[NEXT]"
                "previous" -> "[PREV]"
                "stop" -> "[STOP]"
                else -> "[MUSIC]"
            }

            JSONObject()
                .put("success", true)
                .put("action", action)
                .put("is_music_active", audioManager.isMusicActive)
                .put("message", "$emoji Música: $action")
        } catch (e: Exception) {
            JSONObject().put("error", "Error: ${e.message}")
        }
    }
}
