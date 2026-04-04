package com.beemovil.skills

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * AppLauncherSkill — open other apps on the phone.
 */
class AppLauncherSkill(private val context: Context) : BeeSkill {
    override val name = "app_launcher"
    override val description = "Open apps on the phone. Actions: 'open' (requires 'package' like com.whatsapp or 'app_name' like WhatsApp), 'list' (list installed apps), 'search' (search by name)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["open","list","search"]},
            "package":{"type":"string","description":"Package name like com.whatsapp"},
            "app_name":{"type":"string","description":"App name to search/open like WhatsApp"}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "list")

        return when (action) {
            "open" -> {
                val pkg = params.optString("package", "")
                val appName = params.optString("app_name", "")

                val targetPkg = if (pkg.isNotBlank()) pkg else findPackageByName(appName)

                if (targetPkg.isNullOrBlank()) {
                    return JSONObject().put("error", "App '$appName' no encontrada")
                }

                try {
                    val intent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        JSONObject().put("success", true).put("message", "📱 Abriendo $targetPkg")
                    } else {
                        JSONObject().put("error", "No se puede abrir $targetPkg")
                    }
                } catch (e: Exception) {
                    JSONObject().put("error", "Error: ${e.message}")
                }
            }
            "list" -> {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .sortedBy { pm.getApplicationLabel(it).toString() }

                val arr = JSONArray()
                apps.take(30).forEach { app ->
                    arr.put(JSONObject().apply {
                        put("name", pm.getApplicationLabel(app).toString())
                        put("package", app.packageName)
                    })
                }
                JSONObject().put("apps", arr).put("total", apps.size)
            }
            "search" -> {
                val query = params.optString("app_name", "").lowercase()
                if (query.isBlank()) return JSONObject().put("error", "No search query")

                val pm = context.packageManager
                val matches = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter {
                        pm.getLaunchIntentForPackage(it.packageName) != null &&
                        (pm.getApplicationLabel(it).toString().lowercase().contains(query) ||
                         it.packageName.lowercase().contains(query))
                    }

                val arr = JSONArray()
                matches.forEach { app ->
                    arr.put(JSONObject().apply {
                        put("name", pm.getApplicationLabel(app).toString())
                        put("package", app.packageName)
                    })
                }
                JSONObject().put("results", arr).put("count", matches.size)
            }
            else -> JSONObject().put("error", "Action not supported")
        }
    }

    private fun findPackageByName(name: String): String? {
        if (name.isBlank()) return null
        val pm = context.packageManager
        val query = name.lowercase()

        // Common app shortcuts
        val shortcuts = mapOf(
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "youtube" to "com.google.android.youtube",
            "spotify" to "com.spotify.music",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "telegram" to "org.telegram.messenger",
            "tiktok" to "com.zhiliaoapp.musically",
            "camera" to "com.android.camera",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "phone" to "com.google.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "photos" to "com.google.android.apps.photos",
            "files" to "com.google.android.documentsui"
        )

        shortcuts[query]?.let { return it }

        // Search installed apps
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .firstOrNull {
                pm.getApplicationLabel(it).toString().lowercase().contains(query)
            }?.packageName
    }
}
