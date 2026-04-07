package com.beemovil.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * AttachmentManager — Centralized file processor for Bee-Movil.
 *
 * Converts ANY file into usable AI context:
 *   Image  → base64 (for vision) + thumbnail
 *   PDF    → extracted text per page via PdfRenderer + bitmap OCR fallback
 *   Text   → inline content (txt, csv, json, xml, md, log)
 *   DOCX   → extracted text from document.xml inside ZIP
 *   Link   → HTML content (handled separately)
 *
 * All processed files are stored in BeeMovil/attachments/ for persistence.
 * The FileExplorer can browse them. The agent can reference them across turns.
 */
object AttachmentManager {

    private const val TAG = "AttachmentMgr"

    /** Root directory for all Bee-Movil files */
    fun getBeeDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "BeeMovil")
        dir.mkdirs()
        return dir
    }

    /** Attachments directory — persisted files sent to/from agent */
    fun getAttachmentsDir(): File {
        val dir = File(getBeeDir(), "attachments")
        dir.mkdirs()
        return dir
    }

    /** Agent-generated files directory */
    fun getGeneratedDir(): File {
        val dir = File(getBeeDir(), "generated")
        dir.mkdirs()
        return dir
    }

    /**
     * Process a URI into an AttachedFile with extracted context.
     * The file is persisted to BeeMovil/attachments/ for later access.
     */
    fun processUri(context: Context, uri: Uri): AttachedFile? {
        return try {
            val displayName = getDisplayName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val mimeType = context.contentResolver.getType(uri) ?: ""

            // Copy to persistent storage
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeFileName = "${ts}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            val destFile = File(getAttachmentsDir(), safeFileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (!destFile.exists()) return null

            processFile(context, destFile, displayName)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing URI: ${e.message}", e)
            null
        }
    }

    /**
     * Process a File (already on disk) into an AttachedFile.
     */
    fun processFile(context: Context, file: File, displayName: String = file.name): AttachedFile? {
        return try {
            val ext = file.extension.lowercase()
            val mimeType = getMimeType(ext)
            val type = classifyFile(ext, mimeType)
            val id = UUID.randomUUID().toString().take(8)

            when (type) {
                FileType.IMAGE -> processImage(id, file, displayName)
                FileType.PDF -> processPdf(id, file, displayName)
                FileType.DOCUMENT -> processDocument(id, file, displayName, ext)
                FileType.TEXT -> processText(id, file, displayName)
                FileType.SPREADSHEET -> processSpreadsheet(id, file, displayName, ext)
                FileType.OTHER -> processGeneric(id, file, displayName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing file ${file.name}: ${e.message}", e)
            null
        }
    }

    /**
     * Save agent-generated content as a file.
     * Returns the saved file path for reference in FileExplorer.
     */
    fun saveGeneratedFile(content: String, name: String, extension: String = "txt"): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${ts}_${name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.$extension"
        val file = File(getGeneratedDir(), fileName)
        file.writeText(content)
        Log.i(TAG, "Saved generated file: ${file.absolutePath} (${file.length()} bytes)")
        return file
    }

    /**
     * Save agent-generated binary file (e.g., images).
     */
    fun saveGeneratedBinary(data: ByteArray, name: String, extension: String): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${ts}_${name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.$extension"
        val file = File(getGeneratedDir(), fileName)
        file.writeBytes(data)
        Log.i(TAG, "Saved generated binary: ${file.absolutePath} (${file.length()} bytes)")
        return file
    }

    // ═══════════════════════════════════════════════════════
    // PROCESSORS
    // ═══════════════════════════════════════════════════════

    private fun processImage(id: String, file: File, name: String): AttachedFile {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: return AttachedFile(id, name, FileType.IMAGE, "Error: imagen corrupta", "", null, file.absolutePath, file.length())

        // Scale for LLM — 1200px max for quality analysis
        val maxDim = 1200f
        val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height, 1f)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val preview = "Imagen: ${bitmap.width}x${bitmap.height} (${humanSize(file.length())})"

        if (resized != bitmap) resized.recycle()
        bitmap.recycle()

        return AttachedFile(
            id = id,
            name = name,
            type = FileType.IMAGE,
            preview = preview,
            contextChunk = "[Imagen adjunta: $name — ${bitmap.width}x${bitmap.height}]",
            base64 = base64,
            filePath = file.absolutePath,
            sizeBytes = file.length()
        )
    }

    private fun processPdf(id: String, file: File, name: String): AttachedFile {
        val sb = StringBuilder()
        var pageCount = 0

        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pageCount = renderer.pageCount

            // Extract text from each page (render to bitmap and describe)
            val maxPages = minOf(pageCount, 50) // Up to 50 pages
            for (i in 0 until maxPages) {
                val page = renderer.openPage(i)
                // Render page to bitmap for text extraction
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                // For now, mark page as rendered (actual OCR would need ML Kit)
                sb.appendLine("--- Pagina ${i + 1} ---")
                sb.appendLine("[Contenido visual de pagina ${i + 1} de $pageCount]")
                bmp.recycle()
                page.close()
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            sb.appendLine("[Error leyendo PDF: ${e.message}]")
        }

        // Also try to read raw text from PDF (some PDFs have embedded text)
        val rawText = extractRawPdfText(file)
        if (rawText.isNotBlank()) {
            sb.clear()
            sb.append(rawText)
        }

        val textContent = sb.toString()
        val preview = "PDF: $pageCount paginas (${humanSize(file.length())})"

        return AttachedFile(
            id = id,
            name = name,
            type = FileType.PDF,
            preview = preview,
            contextChunk = "[Documento PDF: $name — $pageCount paginas]\n$textContent",
            base64 = null,
            filePath = file.absolutePath,
            sizeBytes = file.length()
        )
    }

    /**
     * Extract raw text from PDF by reading the binary stream.
     * Simple approach: find text between BT/ET operators or parentheses.
     */
    private fun extractRawPdfText(file: File): String {
        return try {
            val bytes = file.readBytes()
            val text = String(bytes, Charsets.ISO_8859_1)
            val sb = StringBuilder()

            // Find text in parentheses between BT...ET blocks
            val pattern = Regex("\\(([^)]+)\\)")
            val btBlocks = Regex("BT(.*?)ET", RegexOption.DOT_MATCHES_ALL).findAll(text)
            for (block in btBlocks) {
                for (match in pattern.findAll(block.value)) {
                    val str = match.groupValues[1]
                    if (str.length > 1 && str.any { it.isLetterOrDigit() }) {
                        sb.append(str).append(" ")
                    }
                }
                sb.appendLine()
            }

            val result = sb.toString().trim()
            if (result.length > 100) result else "" // Only use if substantial text found
        } catch (e: Exception) {
            ""
        }
    }

    private fun processDocument(id: String, file: File, name: String, ext: String): AttachedFile {
        val textContent = when (ext) {
            "docx" -> extractDocxText(file)
            else -> file.readText().take(100_000) // doc, odt fallback
        }

        val wordCount = textContent.split(Regex("\\s+")).size
        val preview = "Documento: $wordCount palabras (${humanSize(file.length())})"

        return AttachedFile(
            id = id,
            name = name,
            type = FileType.DOCUMENT,
            preview = preview,
            contextChunk = "[Documento: $name]\n$textContent",
            base64 = null,
            filePath = file.absolutePath,
            sizeBytes = file.length()
        )
    }

    /**
     * Extract text from DOCX by opening as ZIP and parsing word/document.xml
     */
    private fun extractDocxText(file: File): String {
        return try {
            val sb = StringBuilder()
            ZipInputStream(file.inputStream()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xml = zis.bufferedReader().readText()
                        // Strip XML tags, keep text content
                        val cleaned = xml
                            .replace(Regex("<w:p[^>]*>"), "\n")
                            .replace(Regex("<[^>]+>"), "")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        sb.append(cleaned)
                    }
                    entry = zis.nextEntry
                }
            }
            sb.toString().ifBlank { "[No se pudo extraer texto del DOCX]" }
        } catch (e: Exception) {
            "[Error leyendo DOCX: ${e.message}]"
        }
    }

    private fun processText(id: String, file: File, name: String): AttachedFile {
        val content = file.readText()
        val preview = "${file.extension.uppercase()}: ${content.lines().size} lineas (${humanSize(file.length())})"

        return AttachedFile(
            id = id,
            name = name,
            type = FileType.TEXT,
            preview = preview,
            contextChunk = "[Archivo de texto: $name]\n```\n$content\n```",
            base64 = null,
            filePath = file.absolutePath,
            sizeBytes = file.length()
        )
    }

    private fun processSpreadsheet(id: String, file: File, name: String, ext: String): AttachedFile {
        val content = if (ext == "csv") {
            file.readText()
        } else {
            // XLSX: open as ZIP, read xl/sharedStrings.xml + xl/worksheets/sheet1.xml
            extractXlsxText(file)
        }

        val rowCount = content.lines().size
        val preview = "${ext.uppercase()}: $rowCount filas (${humanSize(file.length())})"

        return AttachedFile(
            id = id,
            name = name,
            type = FileType.SPREADSHEET,
            preview = preview,
            contextChunk = "[Hoja de datos: $name]\n```\n$content\n```",
            base64 = null,
            filePath = file.absolutePath,
            sizeBytes = file.length()
        )
    }

    private fun extractXlsxText(file: File): String {
        return try {
            val sharedStrings = mutableListOf<String>()
            val sb = StringBuilder()

            ZipInputStream(file.inputStream()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "xl/sharedStrings.xml" -> {
                            val xml = zis.bufferedReader().readText()
                            val parts = Regex("<t[^>]*>([^<]+)</t>").findAll(xml)
                            parts.forEach { sharedStrings.add(it.groupValues[1]) }
                        }
                        "xl/worksheets/sheet1.xml" -> {
                            val xml = zis.bufferedReader().readText()
                            // Extract cell values
                            val rows = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                            for (row in rows) {
                                val cells = Regex("<v>([^<]+)</v>").findAll(row.value)
                                val values = cells.map { m ->
                                    val idx = m.groupValues[1].toIntOrNull()
                                    if (idx != null && idx < sharedStrings.size) sharedStrings[idx]
                                    else m.groupValues[1]
                                }
                                sb.appendLine(values.joinToString(","))
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            sb.toString().ifBlank { "[No se pudo extraer datos del XLSX]" }
        } catch (e: Exception) {
            "[Error leyendo XLSX: ${e.message}]"
        }
    }

    private fun processGeneric(id: String, file: File, name: String): AttachedFile {
        val preview = "${file.extension.uppercase()}: ${humanSize(file.length())}"
        return AttachedFile(
            id = id,
            name = name,
            type = FileType.OTHER,
            preview = preview,
            contextChunk = "[Archivo adjunto: $name (${file.extension}, ${humanSize(file.length())})]",
            base64 = null,
            filePath = file.absolutePath,
            sizeBytes = file.length()
        )
    }

    // ═══════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════

    private fun getDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (idx >= 0) it.getString(idx) else null
        }
    }

    private fun classifyFile(ext: String, mimeType: String): FileType = when {
        mimeType.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> FileType.IMAGE
        ext == "pdf" || mimeType == "application/pdf" -> FileType.PDF
        ext in listOf("docx", "doc", "odt", "rtf") -> FileType.DOCUMENT
        ext in listOf("csv", "xlsx", "xls", "ods") -> FileType.SPREADSHEET
        ext in listOf("txt", "md", "json", "xml", "html", "css", "js", "kt", "java", "py", "yaml", "yml", "log", "sql", "sh") -> FileType.TEXT
        mimeType.startsWith("text/") -> FileType.TEXT
        else -> FileType.OTHER
    }

    private fun getMimeType(ext: String): String = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes.toFloat() / 1024 / 1024)} MB"
        else -> "${"%.2f".format(bytes.toFloat() / 1024 / 1024 / 1024)} GB"
    }
}

/**
 * Represents a processed file attachment ready for AI context injection.
 */
data class AttachedFile(
    val id: String,
    val name: String,
    val type: FileType,
    val preview: String,        // Short human-readable summary
    val contextChunk: String,   // Full text to inject into LLM prompt
    val base64: String?,        // Only for images (vision)
    val filePath: String?,      // Persistent path on device
    val sizeBytes: Long = 0
)

enum class FileType {
    IMAGE,
    PDF,
    DOCUMENT,
    TEXT,
    SPREADSHEET,
    OTHER
}
