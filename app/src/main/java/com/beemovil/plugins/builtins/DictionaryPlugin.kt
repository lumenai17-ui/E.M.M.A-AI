package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DictionaryPlugin — Word definitions, phonetics, and synonyms via Free Dictionary API.
 *
 * 100% free, no API key, unlimited requests.
 * Supports: English, Spanish, French, German, Italian, Portuguese, Arabic, Turkish.
 *
 * API: https://dictionaryapi.dev/
 */
class DictionaryPlugin(private val context: Context) : EmmaPlugin {

    override val id = "define_word"
    private val TAG = "DictionaryPlugin"

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Busca la definición de una palabra en un diccionario real. " +
                    "Devuelve definiciones, pronunciación fonética, sinónimos y antónimos verificados. " +
                    "Úsalo cuando el usuario pregunte: '¿Qué significa X?', 'Define la palabra Y', " +
                    "'¿Cómo se pronuncia Z?'. Idiomas: en (English), es (Spanish), fr, de, it, pt.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("word", JSONObject().apply {
                        put("type", "string")
                        put("description", "La palabra a definir.")
                    })
                    put("language", JSONObject().apply {
                        put("type", "string")
                        put("description", "Código de idioma (en, es, fr, de, it, pt). Default: en")
                        put("default", "en")
                    })
                })
                put("required", JSONArray().apply {
                    put("word")
                })
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val word = (args["word"] as? String)?.trim()?.lowercase() ?: return "ERROR_TOOL_FAILED: Falta la palabra."
        val lang = (args["language"] as? String)?.trim()?.lowercase() ?: "en"

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.dictionaryapi.dev/api/v2/entries/$lang/$word"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "E.M.M.A. AI/7.2")
                    .build()

                val response = http.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext if (response.code == 404) {
                        "No se encontró la palabra '$word' en el diccionario ($lang). " +
                                "Puede que esté mal escrita o no esté en el diccionario."
                    } else {
                        "ERROR_TOOL_FAILED: Dictionary API respondió con HTTP ${response.code}"
                    }
                }

                val jsonArray = JSONArray(response.body?.string() ?: return@withContext "ERROR_TOOL_FAILED: Respuesta vacía")

                if (jsonArray.length() == 0) {
                    return@withContext "No se encontró la palabra '$word' en el diccionario ($lang)."
                }

                val entry = jsonArray.getJSONObject(0)
                val wordStr = entry.optString("word", word)

                // Phonetics
                val phonetics = entry.optJSONArray("phonetics")
                val phoneticText = if (phonetics != null && phonetics.length() > 0) {
                    val p = phonetics.getJSONObject(0)
                    p.optString("text", "")
                } else ""

                // Meanings
                val meanings = entry.optJSONArray("meanings")
                val allSynonyms = mutableSetOf<String>()
                val allAntonyms = mutableSetOf<String>()

                val definitionsText = buildString {
                    if (meanings != null) {
                        for (i in 0 until minOf(meanings.length(), 4)) {
                            val meaning = meanings.getJSONObject(i)
                            val partOfSpeech = meaning.optString("partOfSpeech", "")
                            val definitions = meaning.optJSONArray("definitions")

                            appendLine("📝 $partOfSpeech:")
                            if (definitions != null) {
                                for (j in 0 until minOf(definitions.length(), 3)) {
                                    val def = definitions.getJSONObject(j)
                                    val definition = def.optString("definition", "")
                                    val example = def.optString("example", "")
                                    appendLine("  ${j + 1}. $definition")
                                    if (example.isNotBlank()) {
                                        appendLine("     💬 \"$example\"")
                                    }
                                }
                            }

                            // Collect synonyms and antonyms
                            meaning.optJSONArray("synonyms")?.let { syns ->
                                for (s in 0 until minOf(syns.length(), 5)) {
                                    allSynonyms.add(syns.optString(s, ""))
                                }
                            }
                            meaning.optJSONArray("antonyms")?.let { ants ->
                                for (a in 0 until minOf(ants.length(), 5)) {
                                    allAntonyms.add(ants.optString(a, ""))
                                }
                            }
                        }
                    }
                }

                buildString {
                    appendLine("📖 **$wordStr** ${if (phoneticText.isNotBlank()) phoneticText else ""}")
                    appendLine()
                    append(definitionsText)
                    if (allSynonyms.isNotEmpty()) {
                        appendLine()
                        appendLine("🔗 Sinónimos: ${allSynonyms.take(6).joinToString(", ")}")
                    }
                    if (allAntonyms.isNotEmpty()) {
                        appendLine("↔️ Antónimos: ${allAntonyms.take(4).joinToString(", ")}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Dictionary lookup error: ${e.message}", e)
                "ERROR_TOOL_FAILED: Error buscando definición: ${e.message}"
            }
        }
    }
}
