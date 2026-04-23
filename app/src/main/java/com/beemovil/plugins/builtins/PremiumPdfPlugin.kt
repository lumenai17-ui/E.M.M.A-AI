package com.beemovil.plugins.builtins

import android.content.Context
import android.os.Environment
import android.print.PrintAttributes
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import android.print.PdfPrinter
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * PremiumPdfPlugin — Generates beautiful, visually rich PDFs using HTML/CSS rendering.
 *
 * Architecture:
 *   1. The LLM generates FULL HTML+CSS (like a landing page)
 *   2. A headless WebView renders it pixel-perfect
 *   3. PdfPrinter (Java) converts the WebView to PDF
 *   4. Result: a stunning PDF that looks exactly like the HTML
 */
class PremiumPdfPlugin(private val context: Context) : EmmaPlugin {

    override val id: String = "generate_premium_pdf"
    private val TAG = "PremiumPdfPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Genera un PDF profesional y visualmente impactante. Funciona como un diseñador gráfico: " +
                    "tú generas TODO el HTML+CSS del documento como si fuera una landing page premium (con gradientes, " +
                    "secciones coloridas, tipografía elegante, layout moderno), y el sistema lo renderiza a PDF preservando " +
                    "el diseño visual exacto. ÚSALO SIEMPRE que el usuario pida un PDF bonito, una propuesta, reporte, " +
                    "presentación, curriculum, catálogo, o cualquier documento que necesite verse profesional. " +
                    "REGLAS del HTML: " +
                    "1) Usa estilos CSS inline o en <style> tags (NO links externos a CSS). " +
                    "2) Usa fuentes web seguras (Arial, Georgia, 'Segoe UI', sans-serif). " +
                    "3) Para imágenes usa URLs de Pollinations: <img src='https://image.pollinations.ai/prompt/DESCRIPCION_EN_INGLES?width=800&height=400&nologo=true'>. " +
                    "4) Diseña para tamaño carta (8.5x11in). Usa page-break-before/after CSS para controlar páginas. " +
                    "5) Incluye colores, gradientes, iconos emoji, bordes redondeados — hazlo PREMIUM. " +
                    "6) El HTML debe ser un documento completo desde <html> hasta </html>.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("document_title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Título corto para el nombre del archivo PDF. Ej: 'Propuesta_Comercial_2024'")
                    })
                    put("html_content", JSONObject().apply {
                        put("type", "string")
                        put("description", "El HTML COMPLETO del documento, desde <html> hasta </html>. " +
                                "Diseña como si fuera una landing page premium con CSS moderno. " +
                                "El sistema lo renderizará a PDF preservando el diseño exacto.")
                    })
                })
                put("required", JSONArray().put("document_title").put("html_content"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val title = args["document_title"] as? String ?: "Documento_Premium"
        val htmlContent = args["html_content"] as? String
            ?: return "❌ Falta el contenido HTML del documento."

        Log.i(TAG, "Generando PDF Premium: $title (${htmlContent.length} chars de HTML)")

        return withContext(Dispatchers.Main) {
            try {
                val fileName = "${title.replace(' ', '_').replace(Regex("[^a-zA-Z0-9_-]"), "")}_${System.currentTimeMillis()}.pdf"
                val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (docsDir != null && !docsDir.exists()) docsDir.mkdirs()
                val pdfFile = File(docsDir, fileName)

                val printReadyHtml = injectPrintStyles(htmlContent)
                val success = renderHtmlToPdf(printReadyHtml, pdfFile)

                if (success && pdfFile.exists() && pdfFile.length() > 100) {
                    Log.i(TAG, "PDF Premium generado: ${pdfFile.absolutePath} (${pdfFile.length() / 1024}KB)")

                    com.beemovil.files.PublicFileWriter.copyToPublicDownloads(
                        context, pdfFile, "application/pdf"
                    )

                    "TOOL_CALL::file_generated::${pdfFile.absolutePath}"
                } else {
                    Log.e(TAG, "PDF generation failed or file too small")
                    "❌ No se pudo generar el PDF. El renderizado falló."
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error generando PDF Premium", e)
                "❌ Error generando PDF: ${e.message}"
            }
        }
    }

    private fun injectPrintStyles(html: String): String {
        val printCss = """
            <style>
                @page {
                    size: letter;
                    margin: 0.5in;
                }
                @media print {
                    body {
                        -webkit-print-color-adjust: exact !important;
                        print-color-adjust: exact !important;
                    }
                }
            </style>
        """.trimIndent()

        return when {
            html.contains("</head>", ignoreCase = true) ->
                html.replace("</head>", "$printCss\n</head>")
            html.contains("<html", ignoreCase = true) ->
                html.replace(Regex("<html[^>]*>", RegexOption.IGNORE_CASE)) {
                    "${it.value}\n<head>$printCss</head>"
                }
            else -> "<html><head>$printCss</head><body>$html</body></html>"
        }
    }

    /**
     * Renders HTML to PDF via headless WebView + PdfPrinter (Java helper).
     * Must run on Main thread.
     */
    private suspend fun renderHtmlToPdf(html: String, outputFile: File): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val webView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        loadsImagesAutomatically = true
                        blockNetworkImage = false
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d(TAG, "WebView page loaded, waiting for images to finish...")

                        // Use JavaScript to wait for all images to complete loading,
                        // then add extra buffer for Pollinations AI-generated images (they take 5-15s)
                        val checkImagesJs = """
                            (function() {
                                var imgs = document.querySelectorAll('img');
                                var allDone = true;
                                for (var i = 0; i < imgs.length; i++) {
                                    if (!imgs[i].complete || imgs[i].naturalWidth === 0) {
                                        allDone = false;
                                        break;
                                    }
                                }
                                return allDone;
                            })();
                        """.trimIndent()

                        fun attemptPrint(retriesLeft: Int) {
                            view?.evaluateJavascript(checkImagesJs) { result ->
                                if (result == "true" || retriesLeft <= 0) {
                                    // Images loaded (or timeout), give 1s extra buffer then print
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        try {
                                            printWebViewToPdf(webView, outputFile) { success ->
                                                webView.destroy()
                                                if (continuation.isActive) {
                                                    continuation.resumeWith(Result.success(success))
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error in PDF generation callback", e)
                                            webView.destroy()
                                            if (continuation.isActive) {
                                                continuation.resumeWith(Result.success(false))
                                            }
                                        }
                                    }, 1500)
                                } else {
                                    // Images still loading, retry in 2 seconds
                                    Log.d(TAG, "Images not ready yet, retrying in 2s ($retriesLeft retries left)")
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        attemptPrint(retriesLeft - 1)
                                    }, 2000)
                                }
                            }
                        }

                        // Start checking after initial 3s delay, retry up to 6 times (total max ~15s)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            attemptPrint(6)
                        }, 3000)
                    }

                    @Suppress("DEPRECATION")
                    override fun onReceivedError(
                        view: WebView?, errorCode: Int,
                        description: String?, failingUrl: String?
                    ) {
                        Log.e(TAG, "WebView error: $errorCode $description")
                    }
                }

                // Absolute timeout
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (continuation.isActive) {
                        Log.w(TAG, "WebView timeout — generating PDF with current state")
                        try {
                            printWebViewToPdf(webView, outputFile) { success ->
                                webView.destroy()
                                if (continuation.isActive) {
                                    continuation.resumeWith(Result.success(success))
                                }
                            }
                        } catch (e: Exception) {
                            webView.destroy()
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(false))
                            }
                        }
                    }
                }, 30_000)

                webView.loadDataWithBaseURL(
                    "https://image.pollinations.ai",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )

            } catch (e: Exception) {
                Log.e(TAG, "WebView setup failed", e)
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(false))
                }
            }
        }
    }

    /**
     * Uses the Java PdfPrinter helper to convert WebView to PDF.
     */
    private fun printWebViewToPdf(
        webView: WebView,
        outputFile: File,
        onComplete: (Boolean) -> Unit
    ) {
        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val jobName = "EMMA_PDF_${System.currentTimeMillis()}"
        val printAdapter = webView.createPrintDocumentAdapter(jobName)

        val pdfPrinter = PdfPrinter(printAttributes)
        pdfPrinter.print(printAdapter, outputFile) { success ->
            onComplete(success)
        }
    }
}
