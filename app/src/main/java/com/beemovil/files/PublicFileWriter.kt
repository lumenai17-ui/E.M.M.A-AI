package com.beemovil.files

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * U-04 fix: Utility to save generated files to the PUBLIC Downloads/EMMA/ folder
 * so the user can find them in the file explorer.
 *
 * On Android 10+ uses MediaStore API (Scoped Storage compliant).
 * On older versions, writes directly to Downloads.
 */
object PublicFileWriter {

    private const val TAG = "PublicFileWriter"
    private const val EMMA_FOLDER = "EMMA"

    /**
     * Copies a file from the app's private directory to Downloads/EMMA/.
     * Returns the public path for display, or the original path if copy fails.
     */
    fun copyToPublicDownloads(context: Context, privateFile: File, mimeType: String = "application/octet-stream"): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, privateFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$EMMA_FOLDER")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        privateFile.inputStream().use { input -> input.copyTo(output) }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    val publicPath = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/$EMMA_FOLDER/${privateFile.name}"
                    Log.i(TAG, "Archivo copiado a Downloads/EMMA/: ${privateFile.name}")
                    publicPath
                } else {
                    Log.w(TAG, "MediaStore insert devolvió null, usando path privado")
                    privateFile.absolutePath
                }
            } else {
                // Android 9 and below — direct file write
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    EMMA_FOLDER
                )
                if (!publicDir.exists()) publicDir.mkdirs()

                val publicFile = File(publicDir, privateFile.name)
                privateFile.copyTo(publicFile, overwrite = true)
                Log.i(TAG, "Archivo copiado a Downloads/EMMA/: ${publicFile.absolutePath}")
                publicFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copiando a Downloads: ${e.message}")
            privateFile.absolutePath // Fallback al path privado
        }
    }
}
