package com.beemovil.google

import android.util.Log
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/**
 * GoogleDriveService — CRUD operations for Google Drive via REST API v3.
 *
 * All methods are SYNCHRONOUS and must be called from a background thread.
 * Access token is provided by GoogleAuthManager.
 */
class GoogleDriveService(private val accessToken: String) {

    companion object {
        private const val TAG = "GoogleDrive"
        const val SCOPE = DriveScopes.DRIVE_FILE // Only files created by this app
        const val SCOPE_FULL = DriveScopes.DRIVE // Full access (for browsing all files)

        // MIME types
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }

    data class DriveFile(
        val id: String,
        val name: String,
        val mimeType: String,
        val size: Long,
        val modifiedTime: Long,
        val isFolder: Boolean,
        val iconLink: String?,
        val webViewLink: String?
    )

    private val driveService: Drive by lazy {
        val credentials = GoogleCredentials.create(AccessToken(accessToken, Date(System.currentTimeMillis() + 3600_000)))
        val httpTransport = com.google.api.client.http.javanet.NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()

        Drive.Builder(httpTransport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName("BeeMovil")
            .build()
    }

    // ═══════════════════════════════════════
    // LIST FILES
    // ═══════════════════════════════════════

    /**
     * List files in a folder. Pass null for root.
     */
    fun listFiles(folderId: String? = null, maxResults: Int = 50): List<DriveFile> {
        return try {
            val query = buildString {
                if (folderId != null) {
                    append("'$folderId' in parents")
                } else {
                    append("'root' in parents")
                }
                append(" and trashed = false")
            }

            val result = driveService.files().list()
                .setQ(query)
                .setPageSize(maxResults)
                .setFields("files(id, name, mimeType, size, modifiedTime, iconLink, webViewLink)")
                .setOrderBy("folder, name")
                .execute()

            result.files?.map { file ->
                DriveFile(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType ?: "",
                    size = file.getSize()?.toLong() ?: 0L,
                    modifiedTime = file.modifiedTime?.value ?: 0L,
                    isFolder = file.mimeType == FOLDER_MIME,
                    iconLink = file.iconLink,
                    webViewLink = file.webViewLink
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "List files error: ${e.message}", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════

    fun searchFiles(query: String, maxResults: Int = 20): List<DriveFile> {
        return try {
            val driveQuery = "name contains '$query' and trashed = false"
            val result = driveService.files().list()
                .setQ(driveQuery)
                .setPageSize(maxResults)
                .setFields("files(id, name, mimeType, size, modifiedTime, iconLink, webViewLink)")
                .execute()

            result.files?.map { file ->
                DriveFile(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType ?: "",
                    size = file.getSize()?.toLong() ?: 0L,
                    modifiedTime = file.modifiedTime?.value ?: 0L,
                    isFolder = file.mimeType == FOLDER_MIME,
                    iconLink = file.iconLink,
                    webViewLink = file.webViewLink
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════
    // DOWNLOAD
    // ═══════════════════════════════════════

    /**
     * Download a file from Drive to local storage.
     * Returns the local file path or null on error.
     */
    fun downloadFile(fileId: String, localDir: File, fileName: String? = null): File? {
        return try {
            // Get file metadata first
            val driveFile = driveService.files().get(fileId)
                .setFields("name, mimeType, size")
                .execute()

            val name = fileName ?: driveFile.name
            val outFile = File(localDir, name)
            localDir.mkdirs()

            FileOutputStream(outFile).use { outputStream ->
                driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            }

            Log.i(TAG, "Downloaded: ${driveFile.name} → ${outFile.absolutePath}")
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════
    // UPLOAD
    // ═══════════════════════════════════════

    /**
     * Upload a local file to Drive.
     * Returns the Drive file ID or null on error.
     */
    fun uploadFile(localFile: File, parentFolderId: String? = null): String? {
        return try {
            val mimeType = guessMimeType(localFile.extension)
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = localFile.name
                if (parentFolderId != null) {
                    parents = listOf(parentFolderId)
                }
            }

            val mediaContent = FileContent(mimeType, localFile)
            val uploaded = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, name, webViewLink")
                .execute()

            Log.i(TAG, "Uploaded: ${localFile.name} → Drive ID: ${uploaded.id}")
            uploaded.id
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════
    // CREATE FOLDER
    // ═══════════════════════════════════════

    fun createFolder(name: String, parentId: String? = null): String? {
        return try {
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                this.name = name
                this.mimeType = FOLDER_MIME
                if (parentId != null) parents = listOf(parentId)
            }
            val folder = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute()
            Log.i(TAG, "Created folder: $name → ${folder.id}")
            folder.id
        } catch (e: Exception) {
            Log.e(TAG, "Create folder error: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════

    fun deleteFile(fileId: String): Boolean {
        return try {
            driveService.files().delete(fileId).execute()
            Log.i(TAG, "Deleted: $fileId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Delete error: ${e.message}", e)
            false
        }
    }

    // ═══════════════════════════════════════
    // STORAGE INFO
    // ═══════════════════════════════════════

    data class StorageInfo(val totalBytes: Long, val usedBytes: Long)

    fun getStorageInfo(): StorageInfo? {
        return try {
            val about = driveService.about().get()
                .setFields("storageQuota")
                .execute()
            val quota = about.storageQuota
            StorageInfo(
                totalBytes = quota.limit ?: 0L,
                usedBytes = quota.usage ?: 0L
            )
        } catch (e: Exception) {
            Log.e(TAG, "Storage info error: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════
    // UTILS
    // ═══════════════════════════════════════

    private fun guessMimeType(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "application/javascript"
        "json" -> "application/json"
        "txt", "md" -> "text/plain"
        "csv" -> "text/csv"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }
}
