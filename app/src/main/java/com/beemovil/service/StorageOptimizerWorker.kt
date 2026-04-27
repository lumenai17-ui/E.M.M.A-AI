package com.beemovil.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * StorageOptimizerWorker — Fase 2.2: Sincronización Física (Limpieza)
 *
 * Se encarga de purgar archivos temporales y cachés multimedia antiguos
 * para evitar que el almacenamiento llegue al 100%.
 *
 * Reglas:
 * - Imágenes generadas (Pollinations) > 48 hrs: Eliminar.
 * - Audios TTS (Deepgram/Pollinations) > 24 hrs: Eliminar.
 * - Adjuntos/Exportaciones temporales > 48 hrs: Eliminar.
 */
class StorageOptimizerWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "StorageOptimizer"
        private const val MAX_AGE_AUDIO_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val MAX_AGE_IMAGE_MS = 48 * 60 * 60 * 1000L // 48 hours
        private const val MAX_AGE_DOC_MS = 48 * 60 * 60 * 1000L   // 48 hours
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Iniciando optimización de almacenamiento...")
            val now = System.currentTimeMillis()
            var deletedFilesCount = 0
            var freedBytes = 0L

            // 1. Limpiar Cache Dir (Audios TTS y temporales)
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    // Audios generados por TTS (speech_*.mp3, etc.)
                    if (file.isFile && (file.name.endsWith(".mp3") || file.name.endsWith(".wav") || file.name.startsWith("speech_"))) {
                        if (now - file.lastModified() > MAX_AGE_AUDIO_MS) {
                            freedBytes += file.length()
                            if (file.delete()) deletedFilesCount++
                        }
                    }
                }
            }

            // 2. Limpiar Imágenes Generadas (Pollinations)
            val generatedImagesDir = File(cacheDir, "generated_images")
            if (generatedImagesDir.exists()) {
                generatedImagesDir.listFiles()?.forEach { file ->
                    if (file.isFile && now - file.lastModified() > MAX_AGE_IMAGE_MS) {
                        freedBytes += file.length()
                        if (file.delete()) deletedFilesCount++
                    }
                }
            }

            // 3. Limpiar Documentos/Adjuntos temporales
            val docsDir = File(context.filesDir, "Documents")
            if (docsDir.exists()) {
                docsDir.listFiles()?.forEach { file ->
                    if (file.isFile && now - file.lastModified() > MAX_AGE_DOC_MS) {
                        freedBytes += file.length()
                        if (file.delete()) deletedFilesCount++
                    }
                }
            }

            // 4. Limpiar Crashes viejos
            val crashesDir = File(context.filesDir, "crashes")
            if (crashesDir.exists()) {
                // Conservar solo los últimos 3
                val crashFiles = crashesDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                if (crashFiles.size > 3) {
                    crashFiles.drop(3).forEach { file ->
                        freedBytes += file.length()
                        if (file.delete()) deletedFilesCount++
                    }
                }
            }

            val freedMB = freedBytes / (1024 * 1024f)
            Log.i(TAG, "Optimización completada. Archivos eliminados: $deletedFilesCount. Espacio liberado: ${"%.2f".format(freedMB)} MB")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizando almacenamiento: ${e.message}", e)
            Result.failure()
        }
    }
}
