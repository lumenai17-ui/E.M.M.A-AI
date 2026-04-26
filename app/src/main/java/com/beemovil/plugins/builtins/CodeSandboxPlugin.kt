package com.beemovil.plugins.builtins

import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import kotlin.time.Duration.Companion.seconds

class CodeSandboxPlugin : EmmaPlugin {
    override val id: String = "execute_js_script"
    private val TAG = "CodeSandbox"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un sandbox interno para ejecutar código JavaScript de forma rápida y determinista. Úsalo SI o SI para cálculos matemáticos, filtrado de datos crudos (JSON/Regex), o cuando necesites precisión lógica de programación. No ejecutes bucles infinitos. Siempre usa `print()` si quieres mandar log a output, el último valor de la expresión evaluada también es retornado.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("code", JSONObject().apply {
                        put("type", "string")
                        put("description", "El código puro de JavaScript a ejecutar (ES5 / JS clásico compatible, sin requires de Node.js). Puedes usar for, while, regex, Math.")
                    })
                })
                put("required", JSONArray().put("code"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val script = args["code"] as? String ?: return "Error: Parameter 'code' missing or invalid."

        // L-01: previously "\$script" — Kotlin escape made it a literal "$script" in logs.
        Log.d(TAG, "Iniciando Rhino Sandbox Task:\n$script")

        // 3 segundos máximos de límite de ejecución
        val result = withContext(Dispatchers.Default) {
            withTimeoutOrNull(3000L) {
                runInRhinoContext(script)
            }
        }

        return if (result == null) {
            Log.w(TAG, "Ejecución abortada: Watchdog Timeout.")
            "Error: Watchdog Timeout alcanzado (3 segundos). El script tardaba demasiado o tenía un bucle infinito."
        } else {
            Log.d(TAG, "Resultado Sandbox: $result")
            result
        }
    }

    private fun runInRhinoContext(script: String): String {
        return try {
            val rhino = Context.enter()
            rhino.optimizationLevel = -1 // Interpret mode to ensure broad stability
            
            val scope: ScriptableObject = rhino.initSafeStandardObjects()
            
            // Inyectamos un print() custom
            val printFunction = """
                var stdout = '';
                function print(obj) {
                    stdout += String(obj) + '\\n';
                }
            """.trimIndent()
            
            rhino.evaluateString(scope, printFunction, "setup", 1, null)
            
            // Evaluamos el script recibido
            val evaluation = rhino.evaluateString(scope, script, "sandbox", 1, null)
            
            // Recogemos el stdout
            val stdoutObj = scope.get("stdout", scope)
            val stdoutStr = if (stdoutObj !== ScriptableObject.NOT_FOUND) Context.toString(stdoutObj) else ""
            
            val evalStr = if (evaluation != null && evaluation !is org.mozilla.javascript.Undefined) {
                Context.toString(evaluation)
            } else ""

            val finalOutput = buildString {
                if (stdoutStr.isNotBlank()) append("Salida Console:\n$stdoutStr\n")
                if (evalStr.isNotBlank()) append("Retorno Evaluado: $evalStr")
                if (isEmpty()) append("Ejecución sin errores pero sin un output impreso ni devuelto.")
            }.trim()
            
            finalOutput
        } catch (e: Exception) {
            "Excepción en Sandbox: ${e.message}"
        } finally {
            Context.exit()
        }
    }
}
