package com.beemovil.llm.local

import android.content.Context
import android.util.Log
import com.beemovil.network.BeeHttpClient
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Manages local LLM model files — download, storage, and lifecycle.
 *
 * Models are stored in: /sdcard/BeeMovil/models/
 * This allows them to persist across app reinstalls and be shared.
 */
object LocalModelManager {

    private const val TAG = "LocalModelManager"
    private const val MODELS_DIR = "BeeMovil/models"

    /** Available models for download. */
    data class LocalModel(
        val id: String,
        val name: String,
        val fileName: String,
        val downloadUrl: String,
        val sizeBytes: Long,    // Approximate file size
        val sizeDisplay: String,
        val description: String
    )

    val AVAILABLE_MODELS = listOf(
        LocalModel(
            id = "gemma4-e2b",
            name = "Gemma 4 E2B (Rápido)",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            sizeBytes = 2_580_000_000L,
            sizeDisplay = "~2.6 GB",
            description = "Optimizado para velocidad. Ideal para chat general."
        ),
        LocalModel(
            id = "gemma4-e4b",
            name = "Gemma 4 E4B (Inteligente)",
            fileName = "gemma-4-E4B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            sizeBytes = 3_650_000_000L,
            sizeDisplay = "~3.7 GB",
            description = "Mayor capacidad de razonamiento. Requiere más RAM."
        )
    )

    /** Get the models directory, creating it if needed. */
    fun getModelsDir(): File {
        val dir = File(android.os.Environment.getExternalStorageDirectory(), MODELS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Check if a model is downloaded. */
    fun isModelDownloaded(modelId: String): Boolean {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val file = File(getModelsDir(), model.fileName)
        return file.exists() && file.length() > 100_000_000L // At least 100MB
    }

    /** Get the path to a downloaded model. */
    fun getModelPath(modelId: String): String? {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        val file = File(getModelsDir(), model.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    /** Get all downloaded models. */
    fun getDownloadedModels(): List<LocalModel> {
        return AVAILABLE_MODELS.filter { isModelDownloaded(it.id) }
    }

    /** Delete a downloaded model. */
    fun deleteModel(modelId: String): Boolean {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val file = File(getModelsDir(), model.fileName)
        return file.delete()
    }

    /** Get available storage in GB. */
    fun getAvailableStorageGB(): Double {
        val stat = android.os.StatFs(getModelsDir().absolutePath)
        return stat.availableBytes / 1_073_741_824.0
    }

    /**
     * Download a model with progress callback.
     * @param onProgress Called with (bytesDownloaded, totalBytes) — both in bytes.
     * @param onComplete Called when done (success = true) or failed (success = false, error message).
     */
    fun downloadModel(
        modelId: String,
        onProgress: (Long, Long) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val model = AVAILABLE_MODELS.find { it.id == modelId }
        if (model == null) {
            onComplete(false, "Modelo no encontrado: $modelId")
            return
        }

        val targetFile = File(getModelsDir(), model.fileName)
        val tempFile = File(getModelsDir(), "${model.fileName}.tmp")

        // Check available storage
        val availableGB = getAvailableStorageGB()
        val requiredGB = model.sizeBytes / 1_073_741_824.0
        if (availableGB < requiredGB + 0.5) { // 0.5 GB safety margin
            onComplete(false, "Espacio insuficiente: necesitas ${String.format("%.1f", requiredGB)} GB, tienes ${String.format("%.1f", availableGB)} GB")
            return
        }

        Thread {
            try {
                Log.i(TAG, "Downloading ${model.name} from ${model.downloadUrl}")

                val request = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "BeeMovil/4.2.4")
                    .build()

                val response = BeeHttpClient.default.newCall(request).execute()
                if (!response.isSuccessful) {
                    onComplete(false, "Error HTTP ${response.code}")
                    response.close()
                    return@Thread
                }

                val body = response.body ?: run {
                    onComplete(false, "Respuesta vacía")
                    response.close()
                    return@Thread
                }

                val totalBytes = body.contentLength().let {
                    if (it > 0) it else model.sizeBytes
                }

                var downloaded = 0L
                val buffer = ByteArray(8192)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            // Report progress every ~1MB
                            if (downloaded % (1024 * 1024) < 8192) {
                                onProgress(downloaded, totalBytes)
                            }
                        }
                    }
                }
                response.close()

                // Rename temp to final
                if (tempFile.exists()) {
                    tempFile.renameTo(targetFile)
                    Log.i(TAG, "Download complete: ${targetFile.absolutePath} (${downloaded / 1_048_576} MB)")
                    onComplete(true, "✅ ${model.name} descargado (${downloaded / 1_048_576} MB)")
                } else {
                    onComplete(false, "Archivo temporal no encontrado")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                tempFile.delete() // Clean up partial download
                onComplete(false, "Error: ${e.message}")
            }
        }.start()
    }
}
