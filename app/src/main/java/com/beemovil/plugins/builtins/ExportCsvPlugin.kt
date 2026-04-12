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

class ExportCsvPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "generate_csv_table"
    private val TAG = "ExportCsvPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un generador de archivos .csv y Excels de tabulación básicos. Úsalo SIEMPRE que el usuario recabe mucha métrica y te ponga 'Descárgame la tabla de inventario' o 'Hazme este archivo con los datos'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("file_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "El nombre base del archivo sin extensión (ej: 'Inventario_Lunes').")
                    })
                    put("csv_content", JSONObject().apply {
                        put("type", "string")
                        put("description", "TODO el contenido crudo en formato CSV (Comas y saltos de línea explícitos).")
                    })
                })
                put("required", JSONArray().put("file_name").put("csv_content"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val fileNameBase = args["file_name"] as? String ?: "Datos"
        val csvContent = args["csv_content"] as? String ?: return "Error: Parameter 'csv_content' missing."
        
        Log.i(TAG, "Armando CSV: $fileNameBase")

        return withContext(Dispatchers.IO) {
            try {
                val fileName = "${fileNameBase.replace(' ', '_')}_${System.currentTimeMillis()}.csv"
                val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (docsDir != null && !docsDir.exists()) docsDir.mkdirs()
                
                val csvFile = File(docsDir, fileName)
                
                FileOutputStream(csvFile).use { fos ->
                    OutputStreamWriter(fos, "UTF-8").use { writer ->
                        writer.write(csvContent)
                    }
                }

                Log.d(TAG, "CSV Fabrícado.")
                launchShareIntent(csvFile, "text/comma-separated-values")

                "He forjado la tabla Excel (.csv) correctamente y te abrí el menú para que la mandes por Whatsapp a tu equipo."
                
            } catch (e: Exception) {
                Log.e(TAG, "Falla exportando CSV", e)
                "Error guardando base de datos: ${e.message}"
            }
        }
    }

    private fun launchShareIntent(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Enviando Tabla")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "Error Envío CSV", e)
        }
    }
}
