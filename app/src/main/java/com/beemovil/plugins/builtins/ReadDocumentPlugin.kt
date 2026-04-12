package com.beemovil.plugins.builtins

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class ReadDocumentPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "read_document_file"
    private val TAG = "ReadDocumentPlugin"

    init {
        // Inicialización nativa del motor estático PDFBox
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error precargando PDFBox", e)
        }
    }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un extractor profundo de documentos de oficina. Usa esto SI o SI cuando el usuario te mencione que adjuntó o necesita que leas un archivo (Ej: PDF de legajo, Excel contable CSV/XLSX).",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("file_uri", JSONObject().apply {
                        put("type", "string")
                        put("description", "El string del URI físico o path del archivo enviado por el usuario.")
                    })
                })
                put("required", JSONArray().put("file_uri"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val uriStr = args["file_uri"] as? String ?: return "Error: Parameter 'file_uri' missing."
        Log.i(TAG, "Ingestando documento crudo desde: $uriStr")

        return withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            try {
                val uri = Uri.parse(uriStr)
                
                // Tratar de derivar el nombre y extensión
                var fileName = "Documento"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
                
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val isPdf = fileName.lowercase().endsWith(".pdf") || mimeType.contains("pdf")
                val isExcel = fileName.lowercase().endsWith(".xlsx") || mimeType.contains("spreadsheet") || fileName.lowercase().endsWith(".xls")

                inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) return@withContext "Error crítico: El sistema operativo bloqueó la lectura del descriptor (stream null)."

                val extractedText = when {
                    isPdf -> extractFromPdf(inputStream)
                    isExcel -> extractFromExcel(inputStream)
                    else -> {
                        // Respaldo Txt plano
                        inputStream.bufferedReader().use { it.readText() }
                    }
                }

                // Hard Limit Guard: Agentes no pueden digerir 20MB de texto
                var safeText = extractedText
                if (safeText.length > 25000) {
                    safeText = safeText.substring(0, 25000) + "\\n... [TEXTO TRUNCADO POR MEMORIA, LÍMITE DE 25K CARACTERES ALCANZADO]"
                }

                "Archivo leído con éxito ($fileName):\n\n$safeText"

            } catch (e: Exception) {
                Log.e(TAG, "Error destripando Documento", e)
                "Catástrofe de I/O al intentar parsear documento: ${e.message}"
            } finally {
                inputStream?.close()
            }
        }
    }

    private fun extractFromPdf(stream: InputStream): String {
        var document: PDDocument? = null
        return try {
            document = PDDocument.load(stream)
            val stripper = PDFTextStripper()
            stripper.getText(document)
        } finally {
            document?.close()
        }
    }

    private fun extractFromExcel(stream: InputStream): String {
        val sb = StringBuilder()
        try {
            val workbook = WorkbookFactory.create(stream)
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                sb.append("--- HOJA: ${sheet.sheetName} ---\n")
                
                for (row in sheet) {
                    val rowData = mutableListOf<String>()
                    row.forEach { cell -> 
                        rowData.add(cell.toString().replace("\n", " ")) 
                    }
                    if (rowData.isNotEmpty()) {
                        sb.append(rowData.joinToString(" | ")).append("\n")
                    }
                }
                sb.append("\n")
            }
            workbook.close()
        } catch (e: Exception) {
            sb.append("Error POI XLSX: ${e.message}")
        }
        return sb.toString()
    }
}
