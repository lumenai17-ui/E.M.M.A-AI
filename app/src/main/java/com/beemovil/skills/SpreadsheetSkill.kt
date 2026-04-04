package com.beemovil.skills

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * SpreadsheetSkill — Create CSV spreadsheets that open in Excel/Google Sheets.
 * The agent can generate data tables, reports, and structured data exports.
 */
class SpreadsheetSkill(private val context: Context) : BeeSkill {
    override val name = "generate_spreadsheet"
    override val description = """Generate a CSV spreadsheet file (opens in Excel/Google Sheets).
        Provide:
        - 'filename': Output filename (without extension)
        - 'headers': JSON array of column headers ["Col1", "Col2", ...]
        - 'rows': JSON array of arrays [["val1","val2"], ["val3","val4"]]
        Optional:
        - 'format': 'csv' (default) or 'tsv'"""
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "filename":{"type":"string","description":"Output filename (without extension)"},
            "headers":{"type":"array","items":{"type":"string"},"description":"Column headers"},
            "rows":{"type":"array","items":{"type":"array"},"description":"Data rows (array of arrays)"},
            "format":{"type":"string","enum":["csv","tsv"],"description":"Output format"}
        },"required":["filename","headers","rows"]}
    """.trimIndent())

    companion object {
        private const val TAG = "SpreadsheetSkill"
    }

    override fun execute(params: JSONObject): JSONObject {
        val filename = params.optString("filename", "bee_data").replace(" ", "_")
        val headers = params.optJSONArray("headers")
        val rows = params.optJSONArray("rows")
        val format = params.optString("format", "csv")

        if (headers == null || rows == null) {
            return JSONObject().put("error", "Both 'headers' and 'rows' are required")
        }

        return try {
            val separator = if (format == "tsv") "\t" else ","
            val ext = if (format == "tsv") "tsv" else "csv"

            val sb = StringBuilder()

            // BOM for Excel UTF-8 compatibility
            sb.append("\uFEFF")

            // Headers
            val headerRow = mutableListOf<String>()
            for (i in 0 until headers.length()) {
                headerRow.add(escapeCsvField(headers.getString(i), separator))
            }
            sb.appendLine(headerRow.joinToString(separator))

            // Data rows
            var rowCount = 0
            for (i in 0 until rows.length()) {
                val row = rows.optJSONArray(i) ?: continue
                val fields = mutableListOf<String>()
                for (j in 0 until row.length()) {
                    fields.add(escapeCsvField(row.optString(j, ""), separator))
                }
                sb.appendLine(fields.joinToString(separator))
                rowCount++
            }

            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BeeMovil")
            dir.mkdirs()
            val file = File(dir, "${filename}.${ext}")
            file.writeText(sb.toString())

            Log.i(TAG, "Spreadsheet created: ${file.absolutePath}")
            JSONObject()
                .put("success", true)
                .put("path", file.absolutePath)
                .put("filename", file.name)
                .put("columns", headers.length())
                .put("rows", rowCount)
                .put("message", "📊 Hoja de cálculo generada: ${file.name} (${headers.length()} columnas × $rowCount filas)")
        } catch (e: Exception) {
            Log.e(TAG, "Spreadsheet error: ${e.message}")
            JSONObject().put("error", "Spreadsheet generation failed: ${e.message}")
        }
    }

    private fun escapeCsvField(field: String, separator: String): String {
        return if (field.contains(separator) || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
