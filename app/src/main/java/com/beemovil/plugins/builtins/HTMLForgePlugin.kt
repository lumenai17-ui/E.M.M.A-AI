package com.beemovil.plugins.builtins

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

class HTMLForgePlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "generate_html_landing"
    private val TAG = "HTMLForgePlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un creador de sitios web funcionales físicos. Si el usuario te exige que armes una landing page y le entregues la web funcional, usa esta herramienta generando todo el HTML+Tailwind+JS en crudo para entregar.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("file_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "Nombre corto que quieras ponerle a la Landing Page (ejs: Promocion_Nike, Website_Restaurante).")
                    })
                    put("html_markup", JSONObject().apply {
                        put("type", "string")
                        put("description", "Absolutamente TODO el código del sitio. Desde <html> hasta </html>. Todo condensado.")
                    })
                })
                put("required", JSONArray().put("file_name").put("html_markup"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val fileNameBase = args["file_name"] as? String ?: "SitioWeb"
        val markup = args["html_markup"] as? String ?: return "Error: Parameter 'html_markup' missing."
        
        Log.i(TAG, "Forjando Web HTML local: $fileNameBase")

        return withContext(Dispatchers.IO) {
            try {
                val fileName = "${fileNameBase.replace(' ', '_')}_${System.currentTimeMillis()}.html"
                val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (docsDir != null && !docsDir.exists()) docsDir.mkdirs()
                
                val webFile = File(docsDir, fileName)
                
                FileOutputStream(webFile).use { fos ->
                    OutputStreamWriter(fos, "UTF-8").use { writer ->
                        writer.write(markup)
                    }
                }

                Log.d(TAG, "Website local completado.")
                launchViewIntent(webFile)
                
                // U-04 fix: Copiar a Downloads/EMMA/ para visibilidad
                com.beemovil.files.PublicFileWriter.copyToPublicDownloads(
                    context, webFile, "text/html"
                )

                return@withContext "TOOL_CALL::file_generated::${webFile.absolutePath}"
                
            } catch (e: Exception) {
                Log.e(TAG, "Falla exportando HTML", e)
                "Catástrofe de compilación Web: ${e.message}"
            }
        }
    }

    private fun launchViewIntent(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            // ACTION_VIEW en lugar de SEND, para que el usuario navegue lo HTML directamente (WebKit)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/html")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error View Intent", e)
        }
    }
}
