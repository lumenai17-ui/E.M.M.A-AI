package com.beemovil.telegram

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TelegramBotService : Service() {
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_BOT_TOKEN = "EXTRA_BOT_TOKEN"
        const val EXTRA_PROVIDER = "EXTRA_PROVIDER"
        const val EXTRA_MODEL = "EXTRA_MODEL"
        const val EXTRA_API_KEY = "EXTRA_API_KEY"
        const val PREF_ALLOWED_CHATS = "PREF_ALLOWED_CHATS"
        var onStatusChange: ((String, String, Int) -> Unit)? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
