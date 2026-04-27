package com.beemovil.plugins.builtins

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.plugins.SecurityGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * AppLauncherPlugin — Project Autonomía Phase S3
 *
 * List installed apps, launch any app, open system settings.
 * List = 🟢, Launch/open = 🟡.
 */
class AppLauncherPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "emma_app_launcher"
    private val TAG = "AppLauncherPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Lanzador de apps y ajustes. Usa cuando digan 'abre Instagram', 'abre WiFi settings', 'qué apps tengo'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("list_apps").put("open_app").put("open_settings").put("open_app_settings"))
                    })
                    put("app_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "(open_app, open_app_settings) Nombre de la app (ej: 'E.M.M.A.', 'WhatsApp').")
                    })
                    put("settings_page", JSONObject().apply {
                        put("type", "string")
                        put("description", "(open_settings) wifi, bluetooth, display, sound, battery, storage, apps, location, security, notifications, general.")
                    })
                })
                put("required", JSONArray().put("operation"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val operation = args["operation"] as? String ?: return "Falta 'operation'."
        return when (operation) {
            "list_apps" -> listApps()
            "open_app" -> openApp(args)
            "open_settings" -> openSettings(args)
            "open_app_settings" -> openAppSettings(args)
            else -> "Operación desconocida: $operation"
        }
    }

    private fun listApps(): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        return buildString {
            appendLine("═══ APPS INSTALADAS (${apps.size}) ═══")
            apps.forEach { app ->
                val name = pm.getApplicationLabel(app).toString()
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                appendLine("  ${if (isSystem) "⚙️" else "📱"} $name (${app.packageName})")
            }
        }
    }

    private suspend fun openApp(args: Map<String, Any>): String {
        val appName = args["app_name"] as? String ?: return "Falta 'app_name'."
        val pm = context.packageManager
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        val match = allApps.find { pm.getApplicationLabel(it).toString().equals(appName, ignoreCase = true) }
            ?: allApps.find { pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true) }
            ?: allApps.find { it.packageName.contains(appName, ignoreCase = true) }
            ?: return "No encontré '$appName'. Usa list_apps para ver las instaladas."

        val label = pm.getApplicationLabel(match).toString()
        val op = SecurityGate.yellow(id, "open_app", "Abrir: $label")
        if (!SecurityGate.evaluate(op)) return "Cancelado."

        return withContext(Dispatchers.Main) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                "Abriendo $label ✅"
            } else { "No se pudo lanzar $label." }
        }
    }

    private suspend fun openSettings(args: Map<String, Any>): String {
        val page = args["settings_page"] as? String ?: return "Falta 'settings_page'."
        val settingsMap = mapOf(
            "wifi" to Settings.ACTION_WIFI_SETTINGS,
            "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "display" to Settings.ACTION_DISPLAY_SETTINGS,
            "sound" to Settings.ACTION_SOUND_SETTINGS,
            "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
            "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            "apps" to Settings.ACTION_APPLICATION_SETTINGS,
            "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            "security" to Settings.ACTION_SECURITY_SETTINGS,
            "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "notifications" to Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            "general" to Settings.ACTION_SETTINGS
        )
        val action = settingsMap[page.lowercase()]
            ?: return "Página no reconocida. Opciones: ${settingsMap.keys.joinToString()}"

        val op = SecurityGate.yellow(id, "open_settings", "Abrir ajustes: $page")
        if (!SecurityGate.evaluate(op)) return "Cancelado."

        return withContext(Dispatchers.Main) {
            try {
                val intent = Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                if (page.lowercase() == "notifications") intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                context.startActivity(intent)
                "Abriendo ajustes de $page ✅"
            } catch (e: Exception) { "Error: ${e.message}" }
        }
    }

    private suspend fun openAppSettings(args: Map<String, Any>): String {
        val appName = args["app_name"] as? String ?: return "Falta 'app_name'."
        val pm = context.packageManager
        
        // Find package name
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = allApps.find { pm.getApplicationLabel(it).toString().equals(appName, ignoreCase = true) }
            ?: allApps.find { pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true) }
            ?: allApps.find { it.packageName.contains(appName, ignoreCase = true) }
            ?: return "No encontré '$appName'. Usa list_apps para ver las instaladas."

        val label = pm.getApplicationLabel(match).toString()
        val op = SecurityGate.yellow(id, "open_app_settings", "Abrir Información de la App (Ajustes): $label")
        if (!SecurityGate.evaluate(op)) return "Cancelado."

        return withContext(Dispatchers.Main) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${match.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "Abriendo pantalla de Ajustes de la Aplicación para $label ✅. Desde aquí el usuario puede gestionar los permisos."
            } catch (e: Exception) { 
                "Error al abrir ajustes de la app: ${e.message}" 
            }
        }
    }
}
