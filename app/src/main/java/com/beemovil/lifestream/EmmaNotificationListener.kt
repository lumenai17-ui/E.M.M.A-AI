package com.beemovil.lifestream

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * EmmaNotificationListener — LifeStream Notification Capture
 *
 * Android NotificationListenerService that intercepts ALL system notifications
 * and logs relevant ones to the LifeStream database.
 *
 * Security:
 * - Banking apps are NEVER captured (hardcoded blacklist)
 * - OTP/2FA messages are filtered out by content pattern
 * - Only notification text preview is stored (not full message content)
 * - User can disable at any time via Settings
 *
 * Permission: Requires user to manually enable in
 * Settings → Apps → Special Access → Notification Access → EMMA
 */
class EmmaNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "EmmaNotifListener"

        // ═══════════════════════════════════════
        // SECURITY: Packages that are NEVER captured
        // ═══════════════════════════════════════
        private val BLOCKED_PACKAGES = setOf(
            // Banking & Finance
            "com.bancolombia.app",
            "com.nequi",
            "com.davivienda.movilesb",
            "com.bbva.bbvacontigo",
            "com.banistmo.app",
            "com.bfrapp",
            "com.paypal.android.p2pmobile",
            "com.venmo",
            "com.squareup.cash",
            "com.zellepay.zelle",
            // Authentication
            "com.google.android.apps.authenticator2",
            "com.authy.authy",
            "com.microsoft.msa.authenticator",
            "com.twofasapp",
            // Password Managers
            "com.lastpass.lpandroid",
            "com.x8bit.bitwarden",
            "com.onepassword.android",
            // Own app (avoid feedback loops)
            "com.beemovil"
        )

        // ═══════════════════════════════════════
        // SOURCE MAPPING: Package → friendly name
        // ═══════════════════════════════════════
        private val SOURCE_MAP = mapOf(
            "com.whatsapp" to "whatsapp",
            "com.whatsapp.w4b" to "whatsapp_business",
            "org.telegram.messenger" to "telegram",
            "com.instagram.android" to "instagram",
            "com.facebook.orca" to "messenger",
            "com.facebook.katana" to "facebook",
            "com.google.android.gm" to "gmail",
            "com.google.android.calendar" to "calendar",
            "com.google.android.apps.maps" to "maps",
            "com.google.android.dialer" to "phone",
            "com.google.android.apps.messaging" to "sms",
            "com.samsung.android.messaging" to "sms",
            "com.twitter.android" to "twitter",
            "com.linkedin.android" to "linkedin",
            "com.spotify.music" to "spotify",
            "com.slack" to "slack",
            "com.microsoft.teams" to "teams",
            "com.Slack" to "slack"
        )

        // ═══════════════════════════════════════
        // IMPORTANCE MAPPING: Source → importance level
        // ═══════════════════════════════════════
        private val IMPORTANCE_MAP = mapOf(
            "whatsapp" to 2,
            "whatsapp_business" to 2,
            "telegram" to 2,
            "messenger" to 2,
            "sms" to 2,
            "phone" to 3,
            "gmail" to 1,
            "calendar" to 3,
            "maps" to 1,
            "instagram" to 1,
            "facebook" to 1,
            "slack" to 2,
            "teams" to 2,
            "linkedin" to 1
        )

        // OTP/2FA patterns to filter from content
        private val OTP_PATTERNS = listOf(
            Regex("\\b\\d{4,8}\\b.*(?:code|código|verificación|verification|OTP)", RegexOption.IGNORE_CASE),
            Regex("(?:code|código|verificación|verification|OTP).*\\b\\d{4,8}\\b", RegexOption.IGNORE_CASE),
            Regex("G-\\d{5,}", RegexOption.IGNORE_CASE), // Google verification codes
            Regex("\\b\\d{6}\\b\\s+is your", RegexOption.IGNORE_CASE)
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName ?: return

        // 1. Check if LifeStream is enabled
        if (!LifeStreamManager.isEnabled(applicationContext)) return

        // 2. Security: Block banking/auth packages
        if (pkg in BLOCKED_PACKAGES) {
            Log.d(TAG, "BLOCKED: $pkg (security filter)")
            return
        }

        // 3. Extract notification data
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()

        // Skip empty notifications
        if (title.isBlank() && text.isBlank()) return

        // Skip ongoing/persistent notifications (music players, etc.)
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            Log.d(TAG, "SKIP: $pkg (ongoing notification)")
            return
        }

        // 4. Security: Filter OTP/verification codes
        val contentPreview = (bigText ?: text).take(200)
        if (isOtpMessage(contentPreview) || isOtpMessage(title)) {
            Log.d(TAG, "BLOCKED: OTP/verification code detected from $pkg")
            return
        }

        // 5. Map to source name
        val source = SOURCE_MAP[pkg] ?: pkg.substringAfterLast(".").lowercase()
        val importance = IMPORTANCE_MAP[source] ?: 0

        // 6. Build metadata JSON
        val metadata = buildString {
            append("{")
            append("\"package\":\"$pkg\"")
            sbn.tag?.let { append(",\"tag\":\"$it\"") }
            append(",\"category\":\"${notification.category ?: "unknown"}\"")
            append(",\"when\":${notification.`when`}")
            append("}")
        }

        // 7. Create and save entry
        val entry = LifeStreamManager.notificationEntry(
            source = source,
            title = title.take(100),
            content = contentPreview.take(200),
            importance = importance,
            metadata = metadata
        )

        // Use sync insert (NotificationListenerService runs on its own thread)
        LifeStreamManager.logSync(applicationContext, entry)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Could track dismissed notifications, but for now we just ignore
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "✅ NotificationListener CONNECTED — capturing notifications")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "⚠️ NotificationListener DISCONNECTED")
        // Request rebind (Android 7+)
        try {
            requestRebind(android.content.ComponentName(this, EmmaNotificationListener::class.java))
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    private fun isOtpMessage(text: String): Boolean {
        if (text.isBlank()) return false
        return OTP_PATTERNS.any { it.containsMatchIn(text) }
    }
}
