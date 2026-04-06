package com.beemovil.llm.local

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.beemovil.network.BeeHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Manages local LLM model files — download, storage, and lifecycle.
 *
 * Models are stored in: app external files dir / models/
 * (e.g. /storage/emulated/0/Android/data/com.beemovil/files/models/)
 * This does NOT require MANAGE_EXTERNAL_STORAGE permission.
 */
object LocalModelManager {

    private const val TAG = "LocalModelManager"
    private const val MODELS_SUBDIR = "models"

    // Main-thread handler for safe Compose state updates from background threads
    private val mainHandler = Handler(Looper.getMainLooper())

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

    // Application context — set from BeeMovilApp.onCreate() or SettingsScreen
    var appContext: Context? = null

    /** Get the models directory using app-private external storage (no special permission needed). */
    fun getModelsDir(): File {
        val ctx = appContext
        val dir = if (ctx != null) {
            File(ctx.getExternalFilesDir(null), MODELS_SUBDIR)
        } else {
            // Fallback — should not happen if appContext is set
            Log.w(TAG, "appContext not set, using legacy path")
            File(android.os.Environment.getExternalStorageDirectory(), "BeeMovil/models")
        }
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (!created) Log.e(TAG, "Failed to create models dir: ${dir.absolutePath}")
        }
        return dir
    }

    /** Check if a model is downloaded. */
    fun isModelDownloaded(modelId: String): Boolean {
        return try {
            val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
            val file = File(getModelsDir(), model.fileName)
            file.exists() && file.length() > 100_000_000L // At least 100MB
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model: ${e.message}")
            false
        }
    }

    /** Get the path to a downloaded model. */
    fun getModelPath(modelId: String): String? {
        return try {
            val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
            val file = File(getModelsDir(), model.fileName)
            if (file.exists()) file.absolutePath else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting model path: ${e.message}")
            null
        }
    }

    /** Get all downloaded models. */
    fun getDownloadedModels(): List<LocalModel> {
        return AVAILABLE_MODELS.filter { isModelDownloaded(it.id) }
    }

    /** Delete a downloaded model. */
    fun deleteModel(modelId: String): Boolean {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        return try {
            val file = File(getModelsDir(), model.fileName)
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model: ${e.message}")
            false
        }
    }

    /** Get available storage in GB. */
    fun getAvailableStorageGB(): Double {
        return try {
            val stat = android.os.StatFs(getModelsDir().absolutePath)
            stat.availableBytes / 1_073_741_824.0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage: ${e.message}")
            0.0
        }
    }

    /**
     * Download a model with progress callback.
     *
     * IMPORTANT: onProgress and onComplete are dispatched to the MAIN THREAD
     * so they are safe to use for updating Compose mutableState.
     *
     * @param onProgress Called on main thread with (bytesDownloaded, totalBytes).
     * @param onComplete Called on main thread when done.
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

        val modelsDir = try {
            getModelsDir()
        } catch (e: Exception) {
            onComplete(false, "Error accediendo al almacenamiento: ${e.message}")
            return
        }

        val targetFile = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, "${model.fileName}.tmp")

        // Check available storage
        val availableGB = getAvailableStorageGB()
        val requiredGB = model.sizeBytes / 1_073_741_824.0
        if (availableGB < requiredGB + 0.5) {
            onComplete(false, "Espacio insuficiente: necesitas ${String.format("%.1f", requiredGB)} GB, tienes ${String.format("%.1f", availableGB)} GB")
            return
        }

        Thread {
            try {
                Log.i(TAG, "Downloading ${model.name} from ${model.downloadUrl}")
                Log.i(TAG, "Target dir: ${modelsDir.absolutePath}")

                val request = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "BeeMovil/4.2.5")
                    .build()

                // Use the download client (10 min timeout, follows redirects)
                val response = BeeHttpClient.download.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    mainHandler.post { onComplete(false, "Error HTTP $code") }
                    return@Thread
                }

                val body = response.body
                if (body == null) {
                    response.close()
                    mainHandler.post { onComplete(false, "Respuesta vacía del servidor") }
                    return@Thread
                }

                val totalBytes = body.contentLength().let {
                    if (it > 0) it else model.sizeBytes
                }

                var downloaded = 0L
                val buffer = ByteArray(32768) // 32KB buffer for faster downloads

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            // Report progress every ~2MB (on main thread)
                            if (downloaded % (2 * 1024 * 1024) < 32768) {
                                val dl = downloaded
                                val total = totalBytes
                                mainHandler.post { onProgress(dl, total) }
                            }
                        }
                    }
                }
                response.close()

                // Rename temp to final
                if (tempFile.exists()) {
                    // Delete existing target if any
                    if (targetFile.exists()) targetFile.delete()
                    val renamed = tempFile.renameTo(targetFile)
                    if (renamed) {
                        Log.i(TAG, "Download complete: ${targetFile.absolutePath} (${downloaded / 1_048_576} MB)")
                        mainHandler.post {
                            onComplete(true, "✅ ${model.name} descargado (${downloaded / 1_048_576} MB)")
                        }
                    } else {
                        Log.e(TAG, "Failed to rename temp file")
                        mainHandler.post { onComplete(false, "Error: no se pudo mover el archivo descargado") }
                    }
                } else {
                    mainHandler.post { onComplete(false, "Archivo temporal no encontrado") }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                try { tempFile.delete() } catch (_: Exception) {}

                val errorMsg = when {
                    e.message?.contains("timeout", true) == true ->
                        "⏱️ Timeout — conexión muy lenta. Intenta con WiFi."
                    e.message?.contains("Unable to resolve", true) == true ->
                        "📡 Sin conexión a internet"
                    e.message?.contains("Permission", true) == true ->
                        "🔒 Sin permiso de almacenamiento"
                    else -> "Error: ${e.message?.take(100)}"
                }
                mainHandler.post { onComplete(false, errorMsg) }
            }
        }.start()
    }
}
