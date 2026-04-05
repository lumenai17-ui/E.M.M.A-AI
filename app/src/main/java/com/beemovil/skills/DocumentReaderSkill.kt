package com.beemovil.skills

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * DocumentReaderSkill — Read text content from PDF, DOCX, XLSX, TXT, CSV files.
 * Extracts text that can be sent to the LLM for analysis, summary, translation, etc.
 *
 * Supported formats:
 * - PDF: Native Android PdfRenderer + raw text extraction
 * - DOCX: Apache POI XWPFDocument (paragraphs + tables)
 * - XLSX: Apache POI XSSFWorkbook (all sheets, rows, cells)
 * - TXT/CSV/JSON/XML/MD/HTML: Plain text reading
 */
class DocumentReaderSkill(private val context: Context) : BeeSkill {
    override val name = "read_document"
    override val description = """Read and extract text from a document file. Supports:
        - PDF, DOCX, XLSX, TXT, CSV, JSON, XML, MD, HTML
        Provide 'file_path' with the absolute path to the file.
        Returns the text content for analysis."""
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "file_path":{"type":"string","description":"Absolute path to the document file"}
        },"required":["file_path"]}
    """.trimIndent())

    companion object {
        private const val TAG = "DocReaderSkill"
        private const val MAX_TEXT_LENGTH = 8000  // Limit to avoid token overflow
    }

    override fun execute(params: JSONObject): JSONObject {
        val filePath = params.optString("file_path", "")
        if (filePath.isBlank()) {
            return JSONObject().put("error", "No file path provided")
        }

        val file = File(filePath)
        if (!file.exists()) {
            val docsFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "BeeMovil/$filePath"
            )
            if (!docsFile.exists()) {
                return JSONObject().put("error", "File not found: $filePath")
            }
            return readFile(docsFile)
        }

        return readFile(file)
    }

    private fun readFile(file: File): JSONObject {
        val ext = file.extension.lowercase()
        return try {
            val content = when (ext) {
                "pdf" -> readPdf(file)
                "docx" -> readDocx(file)
                "xlsx" -> readXlsx(file)
                "doc" -> "⚠️ Formato DOC (antiguo) detectado. Convierte a DOCX para leerlo."
                "xls" -> "⚠️ Formato XLS (antiguo) detectado. Convierte a XLSX para leerlo."
                "txt", "csv", "json", "xml", "md", "html", "htm", "log" -> readPlainText(file)
                else -> return JSONObject().put("error", "Unsupported format: .$ext. Supported: PDF, DOCX, XLSX, TXT, CSV, JSON, XML")
            }

            val truncated = if (content.length > MAX_TEXT_LENGTH) {
                content.take(MAX_TEXT_LENGTH) + "\n\n... [Truncado: ${content.length} caracteres totales, mostrando primeros $MAX_TEXT_LENGTH]"
            } else content

            Log.i(TAG, "Read ${file.name}: ${content.length} chars, ext=$ext")
            JSONObject()
                .put("success", true)
                .put("content", truncated)
                .put("filename", file.name)
                .put("format", ext.uppercase())
                .put("size_bytes", file.length())
                .put("total_chars", content.length)
                .put("message", "Leido: ${file.name} (${ext.uppercase()}, ${content.length} caracteres)")
        } catch (e: Exception) {
            Log.e(TAG, "Read error: ${e.message}", e)
            JSONObject().put("error", "Error reading ${file.name}: ${e.message}")
        }
    }

    /**
     * Read PDF using raw text extraction from PDF byte streams.
     * Works for text-based PDFs. Scanned PDFs need Vision AI.
     */
    private fun readPdf(file: File): String {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val pageCount = renderer.pageCount
        renderer.close()
        fd.close()

        val sb = StringBuilder()
        sb.appendLine("PDF: ${file.name} — $pageCount pagina(s)")
        sb.appendLine("-".repeat(40))

        val rawText = extractTextFromPdfRaw(file)
        if (rawText.isNotBlank() && rawText.length > 50) {
            sb.appendLine(rawText)
        } else {
            sb.appendLine("Este PDF parece ser escaneado (imagenes, no texto).")
            sb.appendLine("Paginas: $pageCount")
            sb.appendLine("Sugerencia: Usa Vision AI para analizar las paginas como imagenes.")
        }

        return sb.toString()
    }

    /**
     * Basic text extraction from PDF raw bytes.
     * Finds text strings in parenthesized PDF operators.
     */
    private fun extractTextFromPdfRaw(file: File): String {
        val bytes = file.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        val sb = StringBuilder()

        val textPattern = Regex("""\(([^)]{2,500})\)""")
        for (match in textPattern.findAll(text)) {
            val t = match.groupValues[1]
                .replace("\\n", "\n")
                .replace("\\r", "")
                .replace("\\(", "(")
                .replace("\\)", ")")
            if (t.any { it.isLetterOrDigit() }) {
                sb.append(t).append(" ")
            }
        }

        return sb.toString().trim()
    }

    /**
     * Read DOCX using Apache POI — extracts paragraphs and tables.
     */
    private fun readDocx(file: File): String {
        val sb = StringBuilder()
        FileInputStream(file).use { fis ->
            val doc = XWPFDocument(fis)
            sb.appendLine("DOCX: ${file.name}")
            sb.appendLine("-".repeat(40))

            doc.paragraphs.forEach { para ->
                val t = para.text.trim()
                if (t.isNotBlank()) sb.appendLine(t)
            }

            doc.tables.forEach { table ->
                sb.appendLine("\n[Tabla]")
                table.rows.forEach { row ->
                    val cells = row.tableCells.joinToString(" | ") { it.text.trim() }
                    sb.appendLine("| $cells |")
                }
            }

            doc.close()
        }
        return sb.toString()
    }

    /**
     * Read XLSX using Apache POI — all sheets, rows, cells as tab-separated.
     */
    private fun readXlsx(file: File): String {
        val sb = StringBuilder()
        FileInputStream(file).use { fis ->
            val workbook = XSSFWorkbook(fis)
            sb.appendLine("XLSX: ${file.name} — ${workbook.numberOfSheets} hoja(s)")
            sb.appendLine("-".repeat(40))

            for (i in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(i)
                sb.appendLine("\nHoja: ${sheet.sheetName}")
                for (row in sheet) {
                    val lastCell = row.lastCellNum.toInt()
                    if (lastCell <= 0) continue
                    val cells = (0 until lastCell).map { idx ->
                        row.getCell(idx)?.toString() ?: ""
                    }.joinToString("\t")
                    sb.appendLine(cells)
                }
            }

            workbook.close()
        }
        return sb.toString()
    }

    /**
     * Read plain text files.
     */
    private fun readPlainText(file: File): String {
        val ext = file.extension.uppercase()
        return "$ext: ${file.name}\n${"-".repeat(40)}\n${file.readText()}"
    }
}
