package com.beemovil.plugins.builtins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ExportPdfPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "generate_pdf_document"
    private val TAG = "ExportPdfPlugin"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un Arquitecto de PDFs corporativos. Úsalo SIEMPRE que el usuario te pida que 'generes', 'armes', 'descargues' o le prepares un reporte/ensayo extenso en formato PDF. " +
                    "PUEDES INCLUIR IMÁGENES: para insertar una imagen, coloca la URL en formato [IMG:https://url_de_la_imagen] en cualquier parte del body_text. " +
                    "Si necesitas generar una imagen, primero usa 'generate_ai_image' y luego incluye la URL de Pollinations directamente como [IMG:https://image.pollinations.ai/prompt/...].",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("document_title", JSONObject().apply {
                        put("type", "string")
                        put("description", "El título principal corto del PDF que aparecerá como nombre de archivo.")
                    })
                    put("body_text", JSONObject().apply {
                        put("type", "string")
                        put("description", "Todo el contenido del PDF. Para insertar imágenes usa el marcador [IMG:url]. " +
                                "Ejemplo: 'Introducción al tema\\n[IMG:https://image.pollinations.ai/prompt/sunset]\\nContinuación del texto...'")
                    })
                })
                put("required", JSONArray().put("document_title").put("body_text"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val title = args["document_title"] as? String ?: "Documento_SinTitulo"
        val bodyText = args["body_text"] as? String ?: return "Error: Parameter 'body_text' missing."
        
        Log.i(TAG, "Arrancando Arquitecto de PDFs con $title")

        return withContext(Dispatchers.IO) {
            try {
                val fileName = "${title.replace(' ', '_')}_${System.currentTimeMillis()}.pdf"
                val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (docsDir != null && !docsDir.exists()) docsDir.mkdirs()
                val pdfFile = File(docsDir, fileName)

                val pdfDocument = PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val margin = 50
                val contentWidth = pageWidth - (margin * 2)
                
                val textPaint = TextPaint().apply {
                    isAntiAlias = true
                    textSize = 12f
                    color = Color.BLACK
                }
                val titlePaint = TextPaint().apply {
                    isAntiAlias = true
                    textSize = 18f
                    isFakeBoldText = true
                    color = Color.DKGRAY
                }

                // Parse content into blocks: TEXT or IMG
                val blocks = parseContentBlocks(bodyText)
                
                var pageNumber = 1
                var yOffset = margin.toFloat()
                var currentPage: PdfDocument.Page? = null
                var currentCanvas: Canvas? = null

                fun startNewPage(): Canvas {
                    currentPage?.let { pdfDocument.finishPage(it) }
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    val page = pdfDocument.startPage(pageInfo)
                    currentPage = page
                    pageNumber++
                    yOffset = margin.toFloat()
                    return page.canvas
                }

                currentCanvas = startNewPage()

                // Draw title on first page
                currentCanvas.drawText(title.uppercase(), margin.toFloat(), yOffset + 18f, titlePaint)
                yOffset += 40f

                for (block in blocks) {
                    when (block) {
                        is ContentBlock.Text -> {
                            val layout = StaticLayout.Builder.obtain(block.text, 0, block.text.length, textPaint, contentWidth)
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                .setLineSpacing(1f, 1.2f)
                                .setIncludePad(false)
                                .build()
                            
                            // Check if text fits on current page
                            val textHeight = layout.height.toFloat()
                            if (yOffset + textHeight > pageHeight - margin) {
                                // Paginate: draw what fits, then continue on next page
                                var lineIndex = 0
                                while (lineIndex < layout.lineCount) {
                                    val lineTop = layout.getLineTop(lineIndex).toFloat()
                                    val lineBottom = layout.getLineBottom(lineIndex).toFloat()
                                    val lineHeight = lineBottom - lineTop
                                    
                                    if (yOffset + lineHeight > pageHeight - margin) {
                                        currentCanvas = startNewPage()
                                    }
                                    
                                    val lineStart = layout.getLineStart(lineIndex)
                                    val lineEnd = layout.getLineEnd(lineIndex)
                                    val lineText = block.text.substring(lineStart, lineEnd).trimEnd('\n')
                                    currentCanvas!!.drawText(lineText, margin.toFloat(), yOffset + textPaint.textSize, textPaint)
                                    yOffset += lineHeight
                                    lineIndex++
                                }
                            } else {
                                currentCanvas!!.save()
                                currentCanvas!!.translate(margin.toFloat(), yOffset)
                                layout.draw(currentCanvas!!)
                                currentCanvas!!.restore()
                                yOffset += textHeight
                            }
                            yOffset += 8f // Spacing after text block
                        }
                        is ContentBlock.Image -> {
                            val bitmap = downloadBitmap(block.url)
                            if (bitmap != null) {
                                // Scale to fit content width, max height 300
                                val scale = minOf(
                                    contentWidth.toFloat() / bitmap.width,
                                    300f / bitmap.height,
                                    1f
                                )
                                val drawWidth = (bitmap.width * scale).toInt()
                                val drawHeight = (bitmap.height * scale).toInt()
                                
                                if (yOffset + drawHeight > pageHeight - margin) {
                                    currentCanvas = startNewPage()
                                }
                                
                                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, drawWidth, drawHeight, true)
                                val xCenter = margin + (contentWidth - drawWidth) / 2f
                                currentCanvas!!.drawBitmap(scaledBitmap, xCenter, yOffset, null)
                                yOffset += drawHeight + 12f
                                
                                if (scaledBitmap != bitmap) scaledBitmap.recycle()
                                bitmap.recycle()
                            } else {
                                // Image failed — draw placeholder text
                                currentCanvas!!.drawText("[Imagen no disponible]", margin.toFloat(), yOffset + 14f, textPaint)
                                yOffset += 20f
                            }
                        }
                    }
                }

                // Finish last page
                currentPage?.let { pdfDocument.finishPage(it) }

                FileOutputStream(pdfFile).use { outStream ->
                    pdfDocument.writeTo(outStream)
                }
                pdfDocument.close()
                Log.d(TAG, "PDF Forjado con éxito: ${pdfFile.absolutePath} ($pageNumber páginas)")
                
                com.beemovil.files.PublicFileWriter.copyToPublicDownloads(
                    context, pdfFile, "application/pdf"
                )

                return@withContext "TOOL_CALL::file_generated::${pdfFile.absolutePath}"

            } catch (e: Exception) {
                Log.e(TAG, "Falla forjando PDF", e)
                "Hubo un error de I/O al fabricar el PDF: ${e.message}"
            }
        }
    }

    private sealed class ContentBlock {
        data class Text(val text: String) : ContentBlock()
        data class Image(val url: String) : ContentBlock()
    }

    private fun parseContentBlocks(body: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val imgPattern = Regex("""\[IMG:(https?://[^\]]+)]""")
        var lastEnd = 0

        for (match in imgPattern.findAll(body)) {
            // Text before this image
            if (match.range.first > lastEnd) {
                val text = body.substring(lastEnd, match.range.first).trim()
                if (text.isNotBlank()) blocks.add(ContentBlock.Text(text))
            }
            blocks.add(ContentBlock.Image(match.groupValues[1]))
            lastEnd = match.range.last + 1
        }

        // Remaining text after last image
        if (lastEnd < body.length) {
            val text = body.substring(lastEnd).trim()
            if (text.isNotBlank()) blocks.add(ContentBlock.Text(text))
        }

        // If no blocks parsed (no images), treat whole body as text
        if (blocks.isEmpty()) blocks.add(ContentBlock.Text(body))

        return blocks
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                Log.e(TAG, "Image download failed: ${response.code}")
                response.close()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image download error: ${e.message}")
            null
        }
    }
}
