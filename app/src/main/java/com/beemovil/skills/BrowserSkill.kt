package com.beemovil.skills

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * BrowserSkill — Agent-controlled built-in browser.
 *
 * Enables the agent to navigate web pages, read content, fill forms,
 * click buttons, extract data, and take screenshots.
 *
 * The same WebView is used for:
 * - Web navigation (URLs)
 * - Local file preview (file:// for HTML projects, PDFs)
 * - Form automation
 * - Data extraction / web scraping
 */
class BrowserSkill(private val context: Context) : BeeSkill {
    override val name = "browser_agent"
    override val description = """Control the built-in browser. Actions:
        - 'navigate': Go to a URL. Params: url
        - 'read_page': Get the current page text content + title + URL
        - 'click': Click an element. Params: selector (CSS selector or text content)
        - 'type': Type text into a field. Params: selector, text
        - 'fill_form': Fill multiple fields at once. Params: fields [{selector, value}]
        - 'extract_links': Get all links on the page
        - 'extract_tables': Get table data from the page
        - 'run_js': Execute JavaScript on the page. Params: code
        - 'screenshot': Take a screenshot (returns base64)
        - 'get_elements': Get form fields/buttons on the page. Params: selector (optional)
        - 'scroll_to': Scroll to an element. Params: selector
        - 'wait_for': Wait for element to appear (max 10s). Params: selector
        - 'highlight': Highlight an element with yellow border. Params: selector
        - 'back': Go back
        - 'forward': Go forward
        - 'current': Get current URL and title
        - 'save_to_file': Save text/markdown/csv to a file in Downloads. Params: filename, content
        - 'search_web': Search the internet natively to get quick URLs. Params: query"""

    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","description":"Browser action"},
            "url":{"type":"string","description":"URL to navigate to"},
            "selector":{"type":"string","description":"CSS selector or text to find element"},
            "text":{"type":"string","description":"Text to type"},
            "code":{"type":"string","description":"JavaScript to execute"},
            "filename":{"type":"string","description":"Filename for save_to_file"},
            "content":{"type":"string","description":"Content for save_to_file"},
            "query":{"type":"string","description":"Search query for search_web"},
            "fields":{"type":"array","items":{"type":"object"},"description":"Array of {selector, value} for fill_form"}
        },"required":["action"]}
    """.trimIndent())

    companion object {
        private const val TAG = "BrowserSkill"
        private const val TIMEOUT = 15L
    }

    // Shared WebView — will be set by BrowserScreen
    var webView: WebView? = null
        private set
    private val mainHandler = Handler(Looper.getMainLooper())

    // Callback for notifying BrowserScreen to show/update
    var onNavigate: ((String) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun initWebView(wv: WebView) {
        webView = wv
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = true
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        wv.settings.useWideViewPort = true
        wv.settings.loadWithOverviewMode = true
        wv.settings.setSupportZoom(true)
        wv.addJavascriptInterface(BrowserBridge(), "BeeBridge")
    }

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "")

        if (webView == null) {
            return JSONObject()
                .put("error", "Browser not initialized. Open the Browser screen first.")
                .put("suggestion", "Tell the user to open the Browser tab")
        }

        return try {
            when (action) {
                "navigate" -> navigate(params.optString("url", ""))
                "read_page" -> readPage()
                "click" -> clickElement(params.optString("selector", ""))
                "type" -> typeText(params.optString("selector", ""), params.optString("text", ""))
                "fill_form" -> fillForm(params.optJSONArray("fields"))
                "extract_links" -> extractLinks()
                "extract_tables" -> extractTables()
                "run_js" -> runJs(params.optString("code", ""))
                "screenshot" -> takeScreenshot()
                "get_elements" -> getElements(params.optString("selector", ""))
                "scroll_to" -> scrollTo(params.optString("selector", ""))
                "wait_for" -> waitFor(params.optString("selector", ""))
                "highlight" -> highlightElement(params.optString("selector", ""))
                "back" -> { mainHandler.post { webView?.goBack() }; JSONObject().put("success", true).put("message", "Navegando atras") }
                "forward" -> { mainHandler.post { webView?.goForward() }; JSONObject().put("success", true).put("message", "Navegando adelante") }
                "current" -> getCurrentInfo()
                "search_web" -> WebSearchSkill().execute(params)
                "save_to_file" -> {
                    val filename = params.optString("filename", "export.txt")
                    val content = params.optString("content", "")
                    try {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val file = java.io.File(downloadsDir, filename)
                        file.writeText(content)
                        JSONObject().put("message", "Archivo guardado exitosamente en Descargas: ${file.name}")
                    } catch (e: Exception) {
                        JSONObject().put("error", "Error guardando archivo: ${e.message}")
                    }
                }
                else -> JSONObject().put("error", "Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Browser error: ${e.message}", e)
            JSONObject().put("error", "${e.message}")
        }
    }

    private fun navigate(url: String): JSONObject {
        if (url.isBlank()) return JSONObject().put("error", "URL required")

        val fullUrl = when {
            url.startsWith("http") -> url
            url.startsWith("file://") -> url
            url.startsWith("/") -> "file://$url"
            url.contains(".") && !url.contains(" ") -> "https://$url"
            else -> "https://www.google.com/search?q=${url.replace(" ", "+")}"
        }

        val latch = CountDownLatch(1)
        var pageTitle = ""

        mainHandler.post {
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    pageTitle = view?.title ?: ""
                    latch.countDown()
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            }
            webView?.loadUrl(fullUrl)
            onNavigate?.invoke(fullUrl)
        }

        latch.await(TIMEOUT, TimeUnit.SECONDS)

        return JSONObject()
            .put("success", true)
            .put("url", fullUrl)
            .put("title", pageTitle)
            .put("message", "Navegando a: $fullUrl")
    }

    private fun readPage(): JSONObject {
        val js = """
            (function() {
                var title = document.title;
                var url = window.location.href;
                var text = document.body.innerText.substring(0, 5000);
                var meta = document.querySelector('meta[name="description"]');
                var desc = meta ? meta.content : '';
                return JSON.stringify({title: title, url: url, text: text, description: desc});
            })();
        """.trimIndent()

        val result = evalJs(js) ?: return JSONObject().put("error", "Could not read page")

        return try {
            val data = JSONObject(result)
            data.put("success", true)
            data.put("message", "Pagina: " + data.optString("title"))
            data
        } catch (e: Exception) {
            JSONObject().put("content", result).put("success", true)
        }
    }

    private fun clickElement(selector: String): JSONObject {
        if (selector.isBlank()) return JSONObject().put("error", "selector required")

        val safeSel = selector.replace("'", "\\'")
        val js = """
            (function() {
                var el = document.querySelector('$safeSel');
                if (!el) {
                    var all = document.querySelectorAll('a, button, input[type="submit"], [onclick]');
                    for (var i = 0; i < all.length; i++) {
                        if (all[i].textContent.trim().toLowerCase().includes('${safeSel.lowercase()}')) {
                            el = all[i]; break;
                        }
                    }
                }
                if (el) { el.click(); return 'clicked'; }
                return 'not_found';
            })();
        """.trimIndent()

        val result = evalJs(js)
        return if (result == "clicked") {
            JSONObject().put("success", true).put("message", "Click en '$selector'")
        } else {
            JSONObject().put("error", "Element not found: $selector")
        }
    }

    private fun typeText(selector: String, text: String): JSONObject {
        if (selector.isBlank()) return JSONObject().put("error", "selector required")

        val safeSel = selector.replace("'", "\\'")
        val safeText = text.replace("'", "\\'").replace("\n", "\\n")
        val js = """
            (function() {
                var el = document.querySelector('$safeSel');
                if (!el) {
                    var inputs = document.querySelectorAll('input, textarea');
                    for (var i = 0; i < inputs.length; i++) {
                        var ph = inputs[i].placeholder || inputs[i].name || inputs[i].id || '';
                        if (ph.toLowerCase().includes('${safeSel.lowercase()}')) {
                            el = inputs[i]; break;
                        }
                    }
                }
                if (el) {
                    el.value = '$safeText';
                    el.dispatchEvent(new Event('input', {bubbles: true}));
                    el.dispatchEvent(new Event('change', {bubbles: true}));
                    return 'typed';
                }
                return 'not_found';
            })();
        """.trimIndent()

        val result = evalJs(js)
        return if (result == "typed") {
            JSONObject().put("success", true).put("message", "Escrito en '$selector'")
        } else {
            JSONObject().put("error", "Input not found: $selector")
        }
    }

    private fun fillForm(fields: JSONArray?): JSONObject {
        if (fields == null || fields.length() == 0) return JSONObject().put("error", "fields array required")

        val filled = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            val sel = field.optString("selector", "")
            val value = field.optString("value", "")
            val result = typeText(sel, value)
            if (result.optBoolean("success")) filled.add(sel) else failed.add(sel)
        }

        return JSONObject()
            .put("success", failed.isEmpty())
            .put("filled", JSONArray(filled))
            .put("failed", JSONArray(failed))
            .put("message", "${filled.size} campos llenados" + if (failed.isNotEmpty()) ", ${failed.size} fallaron" else "")
    }

    private fun extractLinks(): JSONObject {
        val js = """
            (function() {
                var links = document.querySelectorAll('a[href]');
                var result = [];
                for (var i = 0; i < Math.min(links.length, 50); i++) {
                    result.push({text: links[i].textContent.trim().substring(0, 100), href: links[i].href});
                }
                return JSON.stringify(result);
            })();
        """.trimIndent()

        val result = evalJs(js) ?: return JSONObject().put("error", "Could not extract links")
        return JSONObject()
            .put("links", JSONArray(result))
            .put("success", true)
    }

    private fun extractTables(): JSONObject {
        val js = """
            (function() {
                var tables = document.querySelectorAll('table');
                var result = [];
                for (var t = 0; t < Math.min(tables.length, 5); t++) {
                    var rows = tables[t].querySelectorAll('tr');
                    var data = [];
                    for (var r = 0; r < rows.length; r++) {
                        var cells = rows[r].querySelectorAll('td, th');
                        var row = [];
                        for (var c = 0; c < cells.length; c++) {
                            row.push(cells[c].textContent.trim());
                        }
                        data.push(row);
                    }
                    result.push({rows: data.length, data: data});
                }
                return JSON.stringify(result);
            })();
        """.trimIndent()

        val result = evalJs(js) ?: return JSONObject().put("error", "Could not extract tables")
        return JSONObject()
            .put("tables", JSONArray(result))
            .put("success", true)
    }

    private fun getElements(selector: String): JSONObject {
        val sel = if (selector.isBlank()) "input, textarea, select, button, a" else selector
        val js = """
            (function() {
                var els = document.querySelectorAll('$sel');
                var result = [];
                for (var i = 0; i < Math.min(els.length, 30); i++) {
                    var el = els[i];
                    result.push({
                        tag: el.tagName.toLowerCase(),
                        type: el.type || '',
                        name: el.name || '',
                        id: el.id || '',
                        placeholder: el.placeholder || '',
                        value: el.value || '',
                        text: el.textContent.trim().substring(0, 80),
                        selector: el.id ? '#' + el.id : (el.name ? '[name="' + el.name + '"]' : '')
                    });
                }
                return JSON.stringify(result);
            })();
        """.trimIndent()

        val result = evalJs(js) ?: return JSONObject().put("error", "Could not get elements")
        return JSONObject()
            .put("elements", JSONArray(result))
            .put("success", true)
    }

    private fun runJs(code: String): JSONObject {
        if (code.isBlank()) return JSONObject().put("error", "code required")
        val safeCode = code.replace("`", "\\`")
        val result = evalJs("(function() { try { return String(eval(`$safeCode`)); } catch(e) { return 'ERROR: ' + e.message; } })();")
        return JSONObject()
            .put("success", true)
            .put("result", result ?: "null")
    }

    private fun takeScreenshot(): JSONObject {
        val latch = CountDownLatch(1)
        var base64 = ""

        mainHandler.post {
            webView?.let { wv ->
                if (wv.width <= 0 || wv.height <= 0) {
                    latch.countDown()
                    return@let
                }
                val bitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                wv.draw(canvas)

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                bitmap.recycle()
            }
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)

        return JSONObject()
            .put("success", true)
            .put("screenshot_base64", base64.take(100) + "...")
            .put("size", base64.length)
            .put("message", "Screenshot tomado (${base64.length / 1024} KB)")
    }

    private fun getCurrentInfo(): JSONObject {
        val js = "(function() { return JSON.stringify({url: window.location.href, title: document.title}); })();"
        val result = evalJs(js) ?: return JSONObject().put("url", "about:blank")
        return try {
            JSONObject(result).put("success", true)
        } catch (e: Exception) {
            JSONObject().put("url", "unknown").put("success", true)
        }
    }

    private fun evalJs(code: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null

        mainHandler.post {
            val wv = webView
            if (wv == null) {
                latch.countDown()
                return@post
            }
            wv.evaluateJavascript(code) { value ->
                result = value
                    ?.removeSurrounding("\"")
                    ?.replace("\\n", "\n")
                    ?.replace("\\t", "\t")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\/", "/")
                latch.countDown()
            }
        }

        val completed = latch.await(TIMEOUT, TimeUnit.SECONDS)
        if (!completed) Log.w(TAG, "evalJs timed out")
        return result
    }

    inner class BrowserBridge {
        @JavascriptInterface
        fun log(msg: String) { Log.d(TAG, "Browser: $msg") }
    }

    // ═══════════════════════════════════════════
    // New actions for Phase 26
    // ═══════════════════════════════════════════

    private fun scrollTo(selector: String): JSONObject {
        if (selector.isBlank()) return JSONObject().put("error", "selector required")
        val safeSel = selector.replace("'", "\\'")
        val js = """
            (function() {
                var el = document.querySelector('$safeSel');
                if (!el) {
                    var all = document.querySelectorAll('*');
                    for (var i = 0; i < all.length; i++) {
                        if (all[i].textContent.trim().toLowerCase().includes('${safeSel.lowercase()}')) {
                            el = all[i]; break;
                        }
                    }
                }
                if (el) { el.scrollIntoView({behavior: 'smooth', block: 'center'}); return 'scrolled'; }
                return 'not_found';
            })();
        """.trimIndent()
        val result = evalJs(js)
        return if (result == "scrolled") {
            JSONObject().put("success", true).put("message", "Scroll a '$selector'")
        } else {
            JSONObject().put("error", "Element not found: $selector")
        }
    }

    private fun waitFor(selector: String): JSONObject {
        if (selector.isBlank()) return JSONObject().put("error", "selector required")
        val safeSel = selector.replace("'", "\\'")
        // Poll every 500ms for up to 10 seconds
        for (attempt in 1..20) {
            val js = "(function() { return document.querySelector('$safeSel') ? 'found' : 'waiting'; })();"
            val result = evalJs(js)
            if (result == "found") {
                return JSONObject().put("success", true)
                    .put("message", "Elemento '$selector' encontrado (${attempt * 500}ms)")
            }
            Thread.sleep(500)
        }
        return JSONObject().put("error", "Timeout: '$selector' not found after 10s")
    }

    private fun highlightElement(selector: String): JSONObject {
        if (selector.isBlank()) return JSONObject().put("error", "selector required")
        val safeSel = selector.replace("'", "\\'")
        val js = """
            (function() {
                var el = document.querySelector('$safeSel');
                if (!el) {
                    var all = document.querySelectorAll('a, button, input, textarea, select');
                    for (var i = 0; i < all.length; i++) {
                        if (all[i].textContent.trim().toLowerCase().includes('${safeSel.lowercase()}')) {
                            el = all[i]; break;
                        }
                    }
                }
                if (el) {
                    el.scrollIntoView({behavior: 'smooth', block: 'center'});
                    var orig = el.style.cssText;
                    el.style.outline = '3px solid #D4A843';
                    el.style.outlineOffset = '2px';
                    el.style.boxShadow = '0 0 10px rgba(212,168,67,0.5)';
                    setTimeout(function() { el.style.cssText = orig; }, 2500);
                    return 'highlighted';
                }
                return 'not_found';
            })();
        """.trimIndent()
        val result = evalJs(js)
        return if (result == "highlighted") {
            JSONObject().put("success", true).put("message", "Elemento resaltado: '$selector'")
        } else {
            JSONObject().put("error", "Element not found: $selector")
        }
    }
}
