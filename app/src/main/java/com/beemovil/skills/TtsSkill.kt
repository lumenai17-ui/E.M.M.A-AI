package com.beemovil.skills

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONObject
import java.util.Locale

class TtsSkill(private val context: Context) : BeeSkill {
    override val name = "tts"
    override val description = "Speak text out loud using text-to-speech. Requires 'text'. Optional: 'language' (es, en, pt)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "text":{"type":"string","description":"Text to speak out loud"},
            "language":{"type":"string","description":"Language code: es, en, pt. Default: es"}
        },"required":["text"]}
    """.trimIndent())

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("es", "MX")
                Log.i("TtsSkill", "TTS initialized")
            } else {
                Log.e("TtsSkill", "TTS init failed: $status")
            }
        }
    }

    override fun execute(params: JSONObject): JSONObject {
        val text = params.optString("text", "")
        if (text.isBlank()) return JSONObject().put("error", "No text to speak")

        if (!ttsReady || tts == null) {
            return JSONObject().put("error", "TTS not initialized yet, try again in a moment")
        }

        val lang = params.optString("language", "es")
        val locale = when (lang) {
            "en" -> Locale.US
            "pt" -> Locale("pt", "BR")
            else -> Locale("es", "MX")
        }
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "bee_${System.currentTimeMillis()}")

        return JSONObject()
            .put("success", true)
            .put("message", "Hablando: ${text.take(50)}...")
            .put("language", lang)
            .put("length", text.length)
    }
}
