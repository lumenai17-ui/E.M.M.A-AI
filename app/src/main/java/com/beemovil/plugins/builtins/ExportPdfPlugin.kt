package com.beemovil.plugins.builtins

import android.content.Context
import android.content.Intent
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ExportPdfPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "generate_pdf_document"
    private val TAG = "ExportPdfPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un Arquitecto de PDFs corporativos. Úsalo SIEMPRE que el usuario te pida que 'generes', 'armes', 'descargues' o le prepares un reporte/ensayo extenso en formato PDF.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("document_title", JSONObject().apply {
                        put("type", "string")
                        put("description", "El título principal corto del PDF que aparecerá como nombre de archivo.")
                    })
                    put("body_text", JSONObject().apply {
                        put("type", "string")
                        put("description", "Todo el contenido gigante, ensayos, listas y texto estructurado que quieres que contenga el PDF.")
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
                // Configurar Archivo destino
                val fileName = "${title.replace(' ', '_')}_${System.currentTimeMillis()}.pdf"
                val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (docsDir != null && !docsDir.exists()) {
                    docsDir.mkdirs()
                }
                val pdfFile = File(docsDir, fileName)

                // Crear Documento PDF Nativo (A4 = 595 x 842 points)
                val pdfDocument = PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                
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
                
                val margin = 50
                val contentWidth = pageWidth - (margin * 2)
                
                // Formateador Automático de bloque (Static Layout API level fallback)
                val staticLayout = StaticLayout.Builder.obtain(bodyText, 0, bodyText.length, textPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(1f, 1f)
                    .setIncludePad(false)
                    .build()
                
                var currentLayoutLine = 0
                var pageNumber = 1
                
                // Loop de Paginación Inteligente
                while (currentLayoutLine < staticLayout.lineCount) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas: Canvas = page.canvas
                    
                    var yOffset = margin.toFloat()
                    
                    // Si es página 1, dibujar Título grande
                    if (pageNumber == 1) {
                        canvas.drawText(title.uppercase(), margin.toFloat(), yOffset, titlePaint)
                        yOffset += 40f
                    }
                    
                    val maxLinesPerPage = ((pageHeight - yOffset - margin) / textPaint.descent() - textPaint.ascent()).toInt()
                    
                    val endLine = Math.min(currentLayoutLine + maxLinesPerPage, staticLayout.lineCount)
                    val startOffset = staticLayout.getLineStart(currentLayoutLine)
                    val endOffset = staticLayout.getLineEnd(endLine - 1)
                    
                    val pageText = bodyText.substring(startOffset, endOffset)
                    
                    val pageLayout = StaticLayout.Builder.obtain(pageText, 0, pageText.length, textPaint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(1f, 1f)
                        .setIncludePad(false)
                        .build()
                    
                    canvas.save()
                    canvas.translate(margin.toFloat(), yOffset)
                    pageLayout.draw(canvas)
                    canvas.restore()
                    
                    pdfDocument.finishPage(page)
                    currentLayoutLine = endLine
                    pageNumber++
                }

                // Guardado I/O
                FileOutputStream(pdfFile).use { outStream ->
                    pdfDocument.writeTo(outStream)
                }
                pdfDocument.close()
                Log.d(TAG, "PDF Forjado con éxito: ${pdfFile.absolutePath}")

                // Lanzar Share Intent
                launchShareIntent(pdfFile, "application/pdf")

                "He generado tu documento físico y te he abierto la ventana para que puedas enviarlo o descargarlo directamente."

            } catch (e: Exception) {
                Log.e(TAG, "Falla forjando PDF", e)
                "Hubo un error de I/O al fabricar el PDF: ${e.message}"
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
            context.startActivity(Intent.createChooser(shareIntent, "Compartir E.M.M.A. Export")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "Error Share Intent", e)
        }
    }
}
