package com.beemovil.skills

import org.json.JSONObject
import kotlin.math.*

/**
 * CalculatorSkill — perform math calculations.
 * Supports basic operations and common functions.
 */
class CalculatorSkill : BeeSkill {
    override val name = "calculator"
    override val description = "Perform math calculations. Provide 'expression' for evaluation or use structured: 'a', 'b', 'operation' (+, -, *, /, %, sqrt, pow, sin, cos, tan, log, abs, round, currency_convert)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "expression":{"type":"string","description":"Math expression like '2+2' or 'sqrt(144)'"},
            "operation":{"type":"string","enum":["+","-","*","/","%","sqrt","pow","sin","cos","tan","log","abs","round","floor","ceil"]},
            "a":{"type":"number","description":"First number"},
            "b":{"type":"number","description":"Second number (for binary ops)"}
        }}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        // Try expression first
        val expr = params.optString("expression", "")
        if (expr.isNotBlank()) {
            return evaluateExpression(expr)
        }

        val op = params.optString("operation", "+")
        val a = params.optDouble("a", 0.0)
        val b = params.optDouble("b", 0.0)

        val result = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else return JSONObject().put("error", "Division by zero")
            "%" -> a % b
            "sqrt" -> sqrt(a)
            "pow" -> a.pow(b)
            "sin" -> sin(Math.toRadians(a))
            "cos" -> cos(Math.toRadians(a))
            "tan" -> tan(Math.toRadians(a))
            "log" -> if (a > 0) ln(a) else return JSONObject().put("error", "Log of non-positive")
            "abs" -> abs(a)
            "round" -> round(a)
            "floor" -> floor(a)
            "ceil" -> ceil(a)
            else -> return JSONObject().put("error", "Unknown operation: $op")
        }

        return JSONObject()
            .put("result", result)
            .put("operation", "$op($a${if (op in listOf("+", "-", "*", "/", "%", "pow")) ", $b" else ""})")
            .put("formatted", if (result == floor(result)) "${result.toLong()}" else "%.6f".format(result).trimEnd('0'))
    }

    private fun evaluateExpression(expr: String): JSONObject {
        return try {
            // Simple expression evaluator for basic math
            val cleaned = expr.replace(" ", "")
                .replace("×", "*").replace("÷", "/")
                .replace("^", "**")

            // Handle sqrt, sin, cos, etc.
            val result = when {
                cleaned.startsWith("sqrt(") -> sqrt(cleaned.removeSurrounding("sqrt(", ")").toDouble())
                cleaned.startsWith("sin(") -> sin(Math.toRadians(cleaned.removeSurrounding("sin(", ")").toDouble()))
                cleaned.startsWith("cos(") -> cos(Math.toRadians(cleaned.removeSurrounding("cos(", ")").toDouble()))
                cleaned.startsWith("tan(") -> tan(Math.toRadians(cleaned.removeSurrounding("tan(", ")").toDouble()))
                cleaned.startsWith("log(") -> ln(cleaned.removeSurrounding("log(", ")").toDouble())
                cleaned.startsWith("abs(") -> abs(cleaned.removeSurrounding("abs(", ")").toDouble())
                else -> {
                    // Basic arithmetic: handle +, -, *, /
                    evaluateBasic(cleaned)
                }
            }

            JSONObject()
                .put("expression", expr)
                .put("result", result)
                .put("formatted", if (result == floor(result)) "${result.toLong()}" else "%.6f".format(result).trimEnd('0'))
        } catch (e: Exception) {
            JSONObject().put("error", "Cannot evaluate '$expr': ${e.message}")
        }
    }

    private fun evaluateBasic(expr: String): Double {
        // Handle addition/subtraction (lowest precedence)
        var depth = 0
        for (i in expr.length - 1 downTo 0) {
            when (expr[i]) {
                ')' -> depth++
                '(' -> depth--
                '+' -> if (depth == 0 && i > 0) return evaluateBasic(expr.substring(0, i)) + evaluateBasic(expr.substring(i + 1))
                '-' -> if (depth == 0 && i > 0) return evaluateBasic(expr.substring(0, i)) - evaluateBasic(expr.substring(i + 1))
            }
        }
        // Handle multiplication/division
        depth = 0
        for (i in expr.length - 1 downTo 0) {
            when (expr[i]) {
                ')' -> depth++
                '(' -> depth--
                '*' -> if (depth == 0) return evaluateBasic(expr.substring(0, i)) * evaluateBasic(expr.substring(i + 1))
                '/' -> if (depth == 0) return evaluateBasic(expr.substring(0, i)) / evaluateBasic(expr.substring(i + 1))
            }
        }
        // Handle parentheses
        if (expr.startsWith("(") && expr.endsWith(")")) {
            return evaluateBasic(expr.substring(1, expr.length - 1))
        }
        return expr.toDouble()
    }
}
