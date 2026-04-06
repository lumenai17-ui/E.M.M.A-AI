package com.beemovil.skills

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * PdfGeneratorSkill — Create PDF documents programmatically.
 * The agent can generate reports, summaries, invoices, etc.
 */
class PdfGeneratorSkill(private val context: Context) : BeeSkill {
    override val name = "generate_pdf"
    override val description = """Generate a PDF document. Provide:
        - 'title': Document title
        - 'content': Main text content (supports \n for newlines)
        - 'filename': Output filename (without .pdf extension)
        Optional:
        - 'sections': JSON array of {"heading":"...", "body":"..."} for structured documents
        - 'footer': Footer text"""
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "title":{"type":"string","description":"Document title"},
            "content":{"type":"string","description":"Main text content"},
            "filename":{"type":"string","description":"Output filename (without .pdf)"},
            "sections":{"type":"array","items":{"type":"object"},"description":"Array of {heading, body} sections"},
            "footer":{"type":"string","description":"Footer text"}
        },"required":["title","filename"]}
    """.trimIndent())

    companion object {
        private const val TAG = "PdfGenSkill"
        private const val PAGE_WIDTH = 595  // A4
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 50f
        private const val LINE_HEIGHT = 18f
        private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN.toInt()
    }

    override fun execute(params: JSONObject): JSONObject {
        val title = params.optString("title", "Documento")
        val content = params.optString("content", "")
        val filename = params.optString("filename", "bee_document").replace(" ", "_")
        val sections = params.optJSONArray("sections")
        val footer = params.optString("footer", "Generado por Bee-Movil")

        return try {
            val dir = try {
                val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BeeMovil/PDFs")
                d.mkdirs()
                if (d.canWrite()) d else File(context.getExternalFilesDir(null), "PDFs").also { it.mkdirs() }
            } catch (_: Exception) {
                File(context.getExternalFilesDir(null), "PDFs").also { it.mkdirs() }
            }
            val file = File(dir, "${filename}.pdf")

            val document = PdfDocument()
            var pageNumber = 1
            var currentY = MARGIN

            // Paint objects
            val titlePaint = Paint().apply {
                color = Color.parseColor("#1A1A2E"); textSize = 24f; isFakeBoldText = true; isAntiAlias = true
            }
            val headingPaint = Paint().apply {
                color = Color.parseColor("#FFC107"); textSize = 18f; isFakeBoldText = true; isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#333333"); textSize = 12f; isAntiAlias = true
            }
            val footerPaint = Paint().apply {
                color = Color.parseColor("#999999"); textSize = 9f; isAntiAlias = true
            }
            val linePaint = Paint().apply {
                color = Color.parseColor("#FFC107"); strokeWidth = 2f
            }

            fun newPage(): PdfDocument.Page {
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                pageNumber++
                currentY = MARGIN
                return document.startPage(info)
            }

            fun drawFooter(canvas: Canvas) {
                canvas.drawText("$footer · Pág ${pageNumber - 1}", MARGIN, PAGE_HEIGHT - 30f, footerPaint)
            }

            fun needNewPage(): Boolean = currentY > PAGE_HEIGHT - 80f

            // Wrap text to fit page width
            fun wrapText(text: String, paint: Paint): List<String> {
                val lines = mutableListOf<String>()
                text.split("\n").forEach { paragraph ->
                    if (paragraph.isBlank()) {
                        lines.add("")
                        return@forEach
                    }
                    val words = paragraph.split(" ")
                    var currentLine = ""
                    words.forEach { word ->
                        val test = if (currentLine.isEmpty()) word else "$currentLine $word"
                        if (paint.measureText(test) > CONTENT_WIDTH) {
                            if (currentLine.isNotEmpty()) lines.add(currentLine)
                            currentLine = word
                        } else {
                            currentLine = test
                        }
                    }
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                }
                return lines
            }

            var page = newPage()
            var canvas = page.canvas

            // Title
            canvas.drawText(title, MARGIN, currentY + 24f, titlePaint)
            currentY += 36f
            canvas.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, linePaint)
            currentY += 20f

            // Content or sections
            if (sections != null && sections.length() > 0) {
                for (i in 0 until sections.length()) {
                    val section = sections.getJSONObject(i)
                    val heading = section.optString("heading", "")
                    val body = section.optString("body", "")

                    if (needNewPage()) {
                        drawFooter(canvas); document.finishPage(page)
                        page = newPage(); canvas = page.canvas
                    }

                    // Section heading
                    if (heading.isNotBlank()) {
                        currentY += 8f
                        canvas.drawText(heading, MARGIN, currentY + 18f, headingPaint)
                        currentY += 28f
                    }

                    // Section body
                    val lines = wrapText(body, bodyPaint)
                    lines.forEach { line ->
                        if (needNewPage()) {
                            drawFooter(canvas); document.finishPage(page)
                            page = newPage(); canvas = page.canvas
                        }
                        canvas.drawText(line, MARGIN, currentY + 12f, bodyPaint)
                        currentY += LINE_HEIGHT
                    }
                }
            } else if (content.isNotBlank()) {
                val lines = wrapText(content, bodyPaint)
                lines.forEach { line ->
                    if (needNewPage()) {
                        drawFooter(canvas); document.finishPage(page)
                        page = newPage(); canvas = page.canvas
                    }
                    canvas.drawText(line, MARGIN, currentY + 12f, bodyPaint)
                    currentY += LINE_HEIGHT
                }
            }

            drawFooter(canvas)
            document.finishPage(page)

            FileOutputStream(file).use { document.writeTo(it) }
            document.close()

            Log.i(TAG, "PDF created: ${file.absolutePath}")
            JSONObject()
                .put("success", true)
                .put("path", file.absolutePath)
                .put("filename", file.name)
                .put("pages", pageNumber - 1)
                .put("message", "📄 PDF generado: ${file.name} (${pageNumber - 1} páginas)")
        } catch (e: Exception) {
            Log.e(TAG, "PDF error: ${e.message}")
            JSONObject().put("error", "PDF generation failed: ${e.message}")
        }
    }
}
