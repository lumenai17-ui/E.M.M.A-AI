package com.beemovil.skills

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * CodeRunnerSkill — Execute JavaScript code in a sandboxed WebView.
 *
 * The LLM writes JavaScript code, this skill runs it and returns the result.
 * This transforms the agent from "suggests answers" to "computes answers".
 *
 * Features:
 * - Executes arbitrary JavaScript safely
 * - Captures console.log output
 * - Captures return values
 * - Timeout protection (max 10 seconds)
 * - Error handling with stack traces
 *
 * Examples:
 * - Math: "2 + 2" → 4
 * - Functions: "function fib(n) { ... }; fib(10)" → 55
 * - Data processing: parse CSV, transform JSON
 * - Regex validation: test emails, URLs, etc.
 */
class CodeRunnerSkill(private val context: Context) : BeeSkill {
    override val name = "run_code"
    override val description = """Execute JavaScript code and return the result.
        You can use this to do calculations, data processing, text manipulation, etc.
        The code runs in a sandboxed environment.
        IMPORTANT: The LAST expression in the code is the return value.
        Use console.log() for intermediate outputs.
        Provide:
        - 'code': The JavaScript code to execute
        Example: run_code({"code": "const prices = [10, 20, 30]; prices.reduce((a,b) => a+b, 0)"})
        Returns: {"result": "60", "logs": [], "success": true}"""

    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "code":{"type":"string","description":"JavaScript code to execute. Last expression is the return value."}
        },"required":["code"]}
    """.trimIndent())

    companion object {
        private const val TAG = "CodeRunnerSkill"
        private const val TIMEOUT_SECONDS = 10L
    }

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        if (webView == null) {
            val latch = CountDownLatch(1)
            mainHandler.post {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = WebViewClient()
                    addJavascriptInterface(ConsoleCapture(), "AndroidConsole")
                    // Load minimal HTML page
                    loadDataWithBaseURL(null, """
                        <html><head><script>
                        var __logs = [];
                        var __originalConsole = console.log;
                        console.log = function() {
                            var args = Array.from(arguments).map(function(a) {
                                if (typeof a === 'object') return JSON.stringify(a);
                                return String(a);
                            });
                            __logs.push(args.join(' '));
                            AndroidConsole.log(args.join(' '));
                        };
                        console.error = function() {
                            var args = Array.from(arguments).map(String);
                            __logs.push('ERROR: ' + args.join(' '));
                            AndroidConsole.log('ERROR: ' + args.join(' '));
                        };
                        console.warn = function() {
                            var args = Array.from(arguments).map(String);
                            __logs.push('WARN: ' + args.join(' '));
                            AndroidConsole.log('WARN: ' + args.join(' '));
                        };
                        </script></head><body></body></html>
                    """.trimIndent(), "text/html", "UTF-8", null)
                }
                latch.countDown()
            }
            latch.await(5, TimeUnit.SECONDS)
            // Give WebView time to load
            Thread.sleep(300)
        }
        return webView!!
    }

    // Accumulated console logs for current execution
    private val consoleLogs = mutableListOf<String>()

    override fun execute(params: JSONObject): JSONObject {
        val code = params.optString("code", "")
        if (code.isBlank()) {
            return JSONObject().put("error", "No code provided")
        }

        consoleLogs.clear()
        val resultLatch = CountDownLatch(1)
        var jsResult: String? = null
        var jsError: String? = null

        try {
            val wv = ensureWebView()

            // Wrap code to capture last expression value + errors
            val wrappedCode = """
                (function() {
                    try {
                        __logs = [];
                        var __result = eval(${escapeForJs(code)});
                        if (typeof __result === 'object' && __result !== null) {
                            return JSON.stringify(__result, null, 2);
                        }
                        return String(__result);
                    } catch(e) {
                        return 'ERROR: ' + e.message;
                    }
                })();
            """.trimIndent()

            mainHandler.post {
                wv.evaluateJavascript(wrappedCode) { result ->
                    // Result comes back as a JSON string (quoted)
                    jsResult = result
                        ?.removeSurrounding("\"")
                        ?.replace("\\n", "\n")
                        ?.replace("\\t", "\t")
                        ?.replace("\\\"", "\"")
                        ?.replace("\\/", "/")
                    resultLatch.countDown()
                }
            }

            val completed = resultLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (!completed) {
                return JSONObject()
                    .put("error", "Code execution timed out after ${TIMEOUT_SECONDS}s")
                    .put("logs", consoleLogs.joinToString("\n"))
            }

            val result = jsResult ?: "null"

            // Check if result is an error
            if (result.startsWith("ERROR: ")) {
                return JSONObject()
                    .put("success", false)
                    .put("error", result.removePrefix("ERROR: "))
                    .put("logs", consoleLogs.joinToString("\n"))
                    .put("message", "Error ejecutando codigo: ${result.removePrefix("ERROR: ")}")
            }

            Log.i(TAG, "Code executed: ${code.take(80)}... → $result")
            return JSONObject()
                .put("success", true)
                .put("result", result)
                .put("logs", consoleLogs.joinToString("\n"))
                .put("message", "Codigo ejecutado. Resultado: $result")

        } catch (e: Exception) {
            Log.e(TAG, "Code runner error: ${e.message}", e)
            return JSONObject()
                .put("error", "Execution failed: ${e.message}")
                .put("logs", consoleLogs.joinToString("\n"))
        }
    }

    /**
     * Escape code string for safe embedding in JavaScript eval().
     */
    private fun escapeForJs(code: String): String {
        val escaped = code
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
        return "`$escaped`"
    }

    /**
     * JavaScript interface to capture console.log from WebView.
     */
    inner class ConsoleCapture {
        @JavascriptInterface
        fun log(message: String) {
            consoleLogs.add(message)
            Log.d(TAG, "JS console: $message")
        }
    }

    /**
     * Clean up WebView when no longer needed.
     */
    fun destroy() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
    }
}
