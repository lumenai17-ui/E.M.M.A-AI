package com.beemovil.skills

import android.content.Context
import android.os.Environment
import com.beemovil.google.GoogleAuthManager
import com.beemovil.google.GoogleDriveService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * GoogleDriveSkill — Agent can browse, search, upload, and download Google Drive files.
 * Requires Google Sign-In with Drive scope.
 * Skill #40.
 */
class GoogleDriveSkill(private val context: Context) : BeeSkill {
    override val name = "google_drive"
    override val description = """Manage Google Drive files. Actions:
        'list' (optional 'folder_id' to browse a folder, default=root),
        'search' (requires 'query'),
        'upload' (requires 'file_path' local path),
        'download' (requires 'file_id', optional 'file_name'),
        'create_folder' (requires 'name'),
        'storage' (check Drive storage usage)""".trimIndent()

    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["list","search","upload","download","create_folder","storage"]},
            "folder_id":{"type":"string","description":"Drive folder ID to list"},
            "query":{"type":"string","description":"Search query"},
            "file_path":{"type":"string","description":"Local file path to upload"},
            "file_id":{"type":"string","description":"Drive file ID to download"},
            "file_name":{"type":"string","description":"Name for downloaded file"},
            "name":{"type":"string","description":"Folder name to create"}
        },"required":["action"]}
    """.trimIndent())

    private val authManager by lazy { GoogleAuthManager(context) }
    private val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BeeMovil/drive")

    private fun getDriveService(): GoogleDriveService? {
        val token = authManager.getAccessToken()
        if (token == null || !authManager.hasDriveScope()) {
            return null
        }
        return GoogleDriveService(token)
    }

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "list")

        val drive = getDriveService()
            ?: return JSONObject().put("error", "Google Drive no conectado. Ve a Settings → Google Workspace para conectar tu cuenta.")

        return when (action) {
            "list" -> {
                val folderId = params.optString("folder_id", "").ifBlank { null }
                val files = drive.listFiles(folderId)
                val arr = JSONArray()
                files.forEach { f ->
                    arr.put(JSONObject().apply {
                        put("id", f.id)
                        put("name", f.name)
                        put("type", if (f.isFolder) "folder" else f.mimeType)
                        put("size", f.size)
                        put("is_folder", f.isFolder)
                    })
                }
                JSONObject()
                    .put("files", arr)
                    .put("count", files.size)
                    .put("message", "${files.size} archivos en ${folderId ?: "raiz"}")
            }

            "search" -> {
                val query = params.optString("query", "")
                if (query.isBlank()) return JSONObject().put("error", "Query requerida")
                val files = drive.searchFiles(query)
                val arr = JSONArray()
                files.forEach { f ->
                    arr.put(JSONObject().apply {
                        put("id", f.id)
                        put("name", f.name)
                        put("type", if (f.isFolder) "folder" else f.mimeType)
                        put("size", f.size)
                    })
                }
                JSONObject()
                    .put("results", arr)
                    .put("count", files.size)
                    .put("message", "${files.size} resultados para '$query'")
            }

            "upload" -> {
                val path = params.optString("file_path", "")
                if (path.isBlank()) return JSONObject().put("error", "file_path requerido")
                val localFile = File(path)
                if (!localFile.exists()) return JSONObject().put("error", "Archivo no encontrado: $path")
                val folderId = params.optString("folder_id", "").ifBlank { null }
                val driveId = drive.uploadFile(localFile, folderId)
                if (driveId != null) {
                    JSONObject()
                        .put("success", true)
                        .put("drive_id", driveId)
                        .put("message", "Subido: ${localFile.name} → Google Drive (ID: $driveId)")
                } else {
                    JSONObject().put("error", "Error al subir archivo")
                }
            }

            "download" -> {
                val fileId = params.optString("file_id", "")
                if (fileId.isBlank()) return JSONObject().put("error", "file_id requerido")
                val fileName = params.optString("file_name", "").ifBlank { null }
                val localFile = drive.downloadFile(fileId, downloadDir, fileName)
                if (localFile != null) {
                    JSONObject()
                        .put("success", true)
                        .put("local_path", localFile.absolutePath)
                        .put("message", "Descargado: ${localFile.name} → ${localFile.absolutePath}")
                } else {
                    JSONObject().put("error", "Error al descargar archivo")
                }
            }

            "create_folder" -> {
                val folderName = params.optString("name", "")
                if (folderName.isBlank()) return JSONObject().put("error", "name requerido")
                val parentId = params.optString("folder_id", "").ifBlank { null }
                val folderId = drive.createFolder(folderName, parentId)
                if (folderId != null) {
                    JSONObject()
                        .put("success", true)
                        .put("folder_id", folderId)
                        .put("message", "Carpeta creada: $folderName (ID: $folderId)")
                } else {
                    JSONObject().put("error", "Error al crear carpeta")
                }
            }

            "storage" -> {
                val info = drive.getStorageInfo()
                if (info != null) {
                    val usedGB = "%.2f".format(info.usedBytes.toFloat() / 1024 / 1024 / 1024)
                    val totalGB = "%.0f".format(info.totalBytes.toFloat() / 1024 / 1024 / 1024)
                    JSONObject()
                        .put("used_bytes", info.usedBytes)
                        .put("total_bytes", info.totalBytes)
                        .put("used_gb", usedGB)
                        .put("total_gb", totalGB)
                        .put("message", "Drive: $usedGB GB de $totalGB GB usados")
                } else {
                    JSONObject().put("error", "Error al obtener info de almacenamiento")
                }
            }

            else -> JSONObject().put("error", "Acción no soportada: $action")
        }
    }
}
