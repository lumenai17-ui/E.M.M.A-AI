package com.beemovil.vision

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * EmergencyProtocol — Phase V8: El Perfil
 *
 * Two triggers:
 * 1. Panic button (long-press 3s) → sends immediately
 * 2. Auto-detect (VisionAssessor.SEND_ALERT) → confirms first
 *
 * Sends: GPS location + context + Google Maps link
 * Via: WhatsApp or SMS (configurable)
 */
class EmergencyProtocol(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyProtocol"
        private const val PREFS = "emma_emergency"
    }

    data class EmergencyConfig(
        val enabled: Boolean = false,
        val contactName: String = "",
        val contactNumber: String = "",
        val sendMethod: SendMethod = SendMethod.WHATSAPP,
        val autoDetectEnabled: Boolean = true,
        val confirmationTimeoutMs: Long = 5000
    )

    enum class SendMethod { WHATSAPP, SMS, BOTH }

    // Cooldown to prevent spam
    private var lastAlertTime = 0L
    private val COOLDOWN_MS = 120_000L // 2 minutes minimum between alerts

    /**
     * Load config from SharedPrefs.
     */
    fun loadConfig(): EmergencyConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return EmergencyConfig(
            enabled = prefs.getBoolean("enabled", false),
            contactName = prefs.getString("contact_name", "") ?: "",
            contactNumber = prefs.getString("contact_number", "") ?: "",
            sendMethod = try {
                SendMethod.valueOf(prefs.getString("send_method", "WHATSAPP") ?: "WHATSAPP")
            } catch (_: Exception) { SendMethod.WHATSAPP },
            autoDetectEnabled = prefs.getBoolean("auto_detect", true),
            confirmationTimeoutMs = prefs.getLong("confirmation_timeout", 5000)
        )
    }

    /**
     * Save config to SharedPrefs.
     */
    fun saveConfig(config: EmergencyConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putBoolean("enabled", config.enabled)
            putString("contact_name", config.contactName)
            putString("contact_number", config.contactNumber)
            putString("send_method", config.sendMethod.name)
            putBoolean("auto_detect", config.autoDetectEnabled)
            putLong("confirmation_timeout", config.confirmationTimeoutMs)
            apply()
        }
        Log.i(TAG, "Config saved: contact=${config.contactName}, method=${config.sendMethod}")
    }

    /**
     * Trigger emergency alert. Returns the message sent.
     */
    fun triggerEmergency(
        lat: Double, lng: Double,
        address: String,
        visionContext: String
    ): String? {
        val config = loadConfig()
        if (!config.enabled || config.contactNumber.isBlank()) {
            Log.w(TAG, "Emergency not configured")
            return null
        }

        // Cooldown check
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < COOLDOWN_MS) {
            Log.w(TAG, "Emergency cooldown active (${(COOLDOWN_MS - (now - lastAlertTime)) / 1000}s left)")
            return null
        }
        lastAlertTime = now

        // Build emergency message
        val message = buildEmergencyMessage(lat, lng, address, visionContext)

        // Vibrate alert pattern: SOS (... --- ...)
        vibrateAlert()

        // Send
        when (config.sendMethod) {
            SendMethod.WHATSAPP -> sendWhatsApp(config.contactNumber, message)
            SendMethod.SMS -> sendSms(config.contactNumber, message)
            SendMethod.BOTH -> {
                sendWhatsApp(config.contactNumber, message)
                sendSms(config.contactNumber, message)
            }
        }

        Log.i(TAG, "🚨 Emergency triggered → ${config.contactName} (${config.sendMethod})")
        return message
    }

    /**
     * Check if emergency should auto-trigger based on VisionAssessor.
     */
    fun shouldAutoTrigger(): Boolean {
        val config = loadConfig()
        return config.enabled && config.autoDetectEnabled && config.contactNumber.isNotBlank()
    }

    private fun buildEmergencyMessage(
        lat: Double, lng: Double, address: String, visionContext: String
    ): String {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        val mapsLink = "https://maps.google.com/?q=$lat,$lng"

        return buildString {
            appendLine("🚨 ALERTA DE SEGURIDAD — E.M.M.A. AI")
            appendLine()
            appendLine("📍 Ubicación: ${address.ifBlank { "$lat, $lng" }}")
            appendLine("🕐 Hora: $time")
            if (visionContext.isNotBlank()) {
                appendLine("📷 Contexto: \"${visionContext.take(100)}\"")
            }
            appendLine()
            appendLine("🗺️ Google Maps: $mapsLink")
            appendLine()
            append("Este mensaje fue enviado automáticamente por E.M.M.A. AI.")
        }
    }

    private fun sendWhatsApp(number: String, message: String) {
        try {
            val cleanNumber = number.replace("+", "").replace(" ", "")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp send failed: ${e.message}")
        }
    }

    private fun sendSms(number: String, message: String) {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Background silent send
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }
                smsManager?.sendTextMessage(number, null, message.take(160), null, null)
                Log.i(TAG, "SMS sent silently in background to $number")
            } else {
                // Fallback to explicit intent if no permission
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:$number")
                    putExtra("sms_body", message)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.i(TAG, "SMS intent launched (no SEND_SMS permission)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed: ${e.message}")
        }
    }

    private fun vibrateAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = mgr?.defaultVibrator
                // SOS pattern: short-short-short long-long-long short-short-short
                val pattern = longArrayOf(0, 100, 100, 100, 100, 100, 200, 300, 200, 300, 200, 300, 200, 100, 100, 100, 100, 100)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val pattern = longArrayOf(0, 100, 100, 100, 100, 100, 200, 300, 200, 300, 200, 300, 200, 100, 100, 100, 100, 100)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrate failed: ${e.message}")
        }
    }
}
