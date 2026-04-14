package com.beemovil.plugins.builtins

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WhatsAppAutomatorPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "send_whatsapp_message"
    private val TAG = "WhatsAppAutomatorPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "El arquitecto que intercepta comandos de envíos por WhatsApp y construye el enlace directo. Úsalo cuando el usuario pida explícitamente responderle a un contacto vía WhatsApp.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("phone_number", JSONObject().apply {
                        put("type", "string")
                        put("description", "Número SIN espacios pero DEBE incluir el código de país (Ej: 52155000000). Si el usuario omitió el país, deduce o infiere el código correcto o pónselo base a su localidad.")
                    })
                    put("generated_message", JSONObject().apply {
                        put("type", "string")
                        put("description", "El mensaje completo armado por ti que el humano enviará.")
                    })
                    put("file_path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Ruta opcional del archivo a adjuntar. Si el usuario pidió 'envíale el PDF/archivo', debes incluir la ruta del archivo que se mencionó en la conversación.")
                    })
                })
                put("required", JSONArray().put("phone_number").put("generated_message"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val numberRaw = args["phone_number"] as? String ?: return "Falta 'phone_number'."
        val message = args["generated_message"] as? String ?: "Saludos."
        val filePath = args["file_path"] as? String
        
        // Limpiamos los "+" o " " manuales
        val cleanNumber = numberRaw.replace("+", "").replace(" ", "")

        Log.i(TAG, "Construyendo URI WhatsApp para el numero: $cleanNumber")

        return withContext(Dispatchers.Main) {
            try {
                // U-05 fix: Si hay archivo adjunto, usar ACTION_SEND con EXTRA_STREAM
                if (filePath != null && File(filePath).exists()) {
                    val fileUri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", File(filePath)
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        setPackage("com.whatsapp")
                        type = "*/*"
                        putExtra(Intent.EXTRA_TEXT, message)
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        putExtra("jid", "$cleanNumber@s.whatsapp.net")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "El mensaje con archivo adjunto para '+${cleanNumber}' está listo en WhatsApp. Solo envíalo."
                } else {
                    // Sin adjunto: link directo clásico
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    "El mensaje final de WhatsApp hacia '+${cleanNumber}' está precargado en la pantalla de chat. Tíralo cuando quieras."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error invadiendo WA", e)
                "Crash abriendo WhatsApp directo: ${e.message}"
            }
        }
    }
}
