package com.beemovil.skills

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * CameraSkill — launches camera to take a photo.
 * Uses Android Intent-based capture (no CameraX dependency needed).
 *
 * Photos saved to /sdcard/BeeMovil/photos/
 */
class CameraSkill(private val context: Context) : BeeSkill {
    override val name = "camera"
    override val description = "Take a photo using the phone camera. Action 'capture' launches camera, 'list' shows recent photos"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["capture","list"],"description":"capture to take photo, list to see photos"}
        },"required":["action"]}
    """.trimIndent())

    companion object {
        private const val TAG = "CameraSkill"
        const val REQUEST_CODE = 2001
        var pendingPhotoPath: String? = null
    }

    private val photosDir: File by lazy {
        val dir = File(Environment.getExternalStorageDirectory(), "BeeMovil/photos")
        dir.mkdirs()
        dir
    }

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "capture")

        return when (action) {
            "capture" -> {
                try {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val photoFile = File(photosDir, "IMG_$timestamp.jpg")
                    pendingPhotoPath = photoFile.absolutePath

                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    // Try to launch camera
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        JSONObject()
                            .put("success", true)
                            .put("message", "[SNAP] Cámara abierta. La foto se guardará en: ${photoFile.name}")
                            .put("path", photoFile.absolutePath)
                    } else {
                        JSONObject().put("error", "No se encontró app de cámara")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Camera error: ${e.message}")
                    JSONObject().put("error", "Error al abrir cámara: ${e.message}")
                }
            }
            "list" -> {
                val photos = photosDir.listFiles()?.filter { it.extension in listOf("jpg", "png", "jpeg") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.take(10)
                    ?: emptyList()

                val arr = org.json.JSONArray()
                photos.forEach { f ->
                    arr.put(JSONObject().apply {
                        put("name", f.name)
                        put("size", f.length())
                        put("path", f.absolutePath)
                        put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(f.lastModified())))
                    })
                }
                JSONObject().put("photos", arr).put("count", photos.size).put("directory", photosDir.absolutePath)
            }
            else -> JSONObject().put("error", "Action '$action' not supported")
        }
    }
}
