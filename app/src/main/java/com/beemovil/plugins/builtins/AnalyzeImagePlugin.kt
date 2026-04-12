package com.beemovil.plugins.builtins

import android.content.Context
import android.net.Uri
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.tasks.await

class AnalyzeImagePlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "read_image_text_ocr"
    private val TAG = "AnalyzeImagePlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un escáner OCR visual de grado militar. Úsalo si el usuario quiere que analices o leas el contenido literario de una FOTO / IMAGEN proporcionada. Escaneará las coordenadas de la foto y te devolverá todo el texto inyectado en ella.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("file_uri", JSONObject().apply {
                        put("type", "string")
                        put("description", "El URI o path absoluto de la imagen proporcionado por el usuario en sistema Android (ej: content://media/external/...).")
                    })
                })
                put("required", JSONArray().put("file_uri"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val uriStr = args["file_uri"] as? String ?: return "Error: Parameter 'file_uri' missing."
        
        Log.i(TAG, "Arrancando Motor de Reconocimiento Textual para: $uriStr")
        
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriStr)
                val image = InputImage.fromFilePath(context, uri)
                
                // Instanciar el lector de caracteres nativo (offline mode)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                
                // Forzamos await() en de Task asíncrona de ML Kit a Coroutine
                val visionText = recognizer.process(image).await()
                
                val resultText = visionText.text
                if (resultText.isBlank()) {
                    return@withContext "El análisis de ML Kit concluyó sin encontrar ningún carácter legible en la placa."
                }
                
                Log.d(TAG, "OCR completado. Longitud: ${resultText.length}")
                "He extraído el siguiente texto textualmente de la imagen:\n\n$resultText"
                
            } catch (e: Exception) {
                Log.e(TAG, "Excepción en el OCR visual", e)
                "Fallo crítico en sub-módulo OCR: ${e.message}"
            }
        }
    }
}
