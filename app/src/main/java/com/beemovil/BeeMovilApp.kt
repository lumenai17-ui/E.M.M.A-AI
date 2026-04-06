package com.beemovil

import android.app.Application
import android.util.Log

class BeeMovilApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Set context for on-device LLM (MediaPipe needs it)
        com.beemovil.llm.local.LocalGemmaProvider.appContext = applicationContext
        // Set context for model download manager (storage access)
        com.beemovil.llm.local.LocalModelManager.appContext = applicationContext

        // Global crash handler — catch ANY unhandled exception
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("BeeMovil", "FATAL CRASH: ${throwable.message}", throwable)
            // Save crash log with rotation (keep last 5)
            try {
                val crashDir = java.io.File(filesDir, "crashes").also { it.mkdirs() }
                // Rotate: keep only last 4 crash files
                val existing = crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                existing.drop(4).forEach { it.delete() }
                // Write new crash
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val crashFile = java.io.File(crashDir, "crash_$timestamp.log")
                crashFile.writeText(buildString {
                    appendLine("Bee-Movil Crash Report")
                    appendLine("Time: ${java.util.Date()}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("App: v4.2.5")
                    appendLine("---")
                    appendLine(throwable.stackTraceToString())
                })
                // Also keep latest as crash.log for quick access
                val latestFile = java.io.File(filesDir, "crash.log")
                crashFile.copyTo(latestFile, overwrite = true)
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Read the last crash log (used by Settings/debug). */
    fun getLastCrashLog(): String? {
        return try {
            val file = java.io.File(filesDir, "crash.log")
            if (file.exists()) file.readText() else null
        } catch (_: Exception) { null }
    }

    /** Get number of crash logs saved. */
    fun getCrashCount(): Int {
        return try {
            java.io.File(filesDir, "crashes").listFiles()?.size ?: 0
        } catch (_: Exception) { 0 }
    }
}
