package com.beemovil

import android.app.Application
import android.util.Log

class BeeMovilApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Global crash handler — catch ANY unhandled exception
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("BeeMovil", "FATAL CRASH: ${throwable.message}", throwable)
            // Save crash log for next launch
            try {
                val crashFile = java.io.File(filesDir, "crash.log")
                crashFile.writeText("${java.util.Date()}\n${throwable.stackTraceToString()}")
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
