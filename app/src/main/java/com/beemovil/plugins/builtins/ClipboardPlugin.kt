package com.beemovil.plugins.builtins

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.plugins.SecurityGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ClipboardPlugin — Project Autonomía Phase S2
 *
 * Bridges the chat world with the rest of the phone via clipboard.
 * Copy = 🟢 GREEN, Read = 🟡 YELLOW (privacy: might contain passwords).
 */
class ClipboardPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "emma_clipboard"
    private val TAG = "ClipboardPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = """Maneja el portapapeles del teléfono. Úsala cuando el usuario diga:
                'copia esto', 'ponlo en el clipboard', 'pégame el texto que copié',
                '¿qué tengo copiado?'. Puedes copiar resultados, links, o códigos.""".trimIndent(),
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("copy").put("read"))
                        put("description", "copy: copiar texto al portapapeles. read: leer lo que hay copiado.")
                    })
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Solo para copy) El texto a copiar al portapapeles.")
                    })
                    put("label", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Solo para copy) Etiqueta descriptiva opcional (ej: 'Token', 'URL').")
                    })
                })
                put("required", JSONArray().put("operation"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val operation = args["operation"] as? String ?: return "Falta 'operation'."

        return when (operation) {
            "copy" -> {
                val text = args["text"] as? String ?: return "Falta el texto a copiar."
                val label = args["label"] as? String ?: "E.M.M.A."
                withContext(Dispatchers.Main) {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                        "Copiado al portapapeles ✅ ($label: ${text.take(50)}${if (text.length > 50) "..." else ""})"
                    } catch (e: Exception) {
                        Log.e(TAG, "Copy failed", e)
                        "Error copiando al portapapeles: ${e.message}"
                    }
                }
            }
            "read" -> {
                // SecurityGate: YELLOW — clipboard might contain sensitive data
                val op = SecurityGate.yellow(id, "read", "Leer contenido del portapapeles")
                if (!SecurityGate.evaluate(op)) {
                    return "Operación cancelada por el usuario."
                }

                withContext(Dispatchers.Main) {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        if (!clipboard.hasPrimaryClip()) {
                            "El portapapeles está vacío."
                        } else {
                            val clip = clipboard.primaryClip
                            val text = clip?.getItemAt(0)?.text?.toString() ?: "Contenido no legible"
                            "CONTENIDO DEL PORTAPAPELES:\n$text"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Read clipboard failed", e)
                        "Error leyendo el portapapeles: ${e.message}"
                    }
                }
            }
            else -> "Operación desconocida: $operation"
        }
    }
}
