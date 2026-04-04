package com.beemovil.skills

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject

class NotifySkill(private val context: Context) : BeeSkill {
    override val name = "notify"
    override val description = "Send a push notification to the user's phone. Requires 'title' and 'message'"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "title":{"type":"string","description":"Notification title"},
            "message":{"type":"string","description":"Notification body text"},
            "priority":{"type":"string","enum":["low","normal","high"],"description":"Notification priority"}
        },"required":["title","message"]}
    """.trimIndent())

    companion object {
        private const val CHANNEL_ID = "beemovil_agent"
        private const val CHANNEL_NAME = "Bee-Movil Agent"
        private var notifId = 1000
    }

    override fun execute(params: JSONObject): JSONObject {
        val title = params.optString("title", "Bee-Movil")
        val message = params.optString("message", "")
        val priority = params.optString("priority", "normal")

        if (message.isBlank()) return JSONObject().put("error", "Message is empty")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = when (priority) {
                "high" -> NotificationManager.IMPORTANCE_HIGH
                "low" -> NotificationManager.IMPORTANCE_LOW
                else -> NotificationManager.IMPORTANCE_DEFAULT
            }
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance)
            nm.createNotificationChannel(channel)
        }

        val composePriority = when (priority) {
            "high" -> NotificationCompat.PRIORITY_HIGH
            "low" -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(composePriority)
            .setAutoCancel(true)
            .build()

        val id = notifId++
        nm.notify(id, notification)

        return JSONObject()
            .put("success", true)
            .put("notificationId", id)
            .put("message", "Notificación enviada: $title")
    }
}
