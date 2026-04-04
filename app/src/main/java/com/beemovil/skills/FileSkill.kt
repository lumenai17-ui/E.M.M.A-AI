package com.beemovil.skills

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FileSkill(private val context: Context) : BeeSkill {
    override val name = "file"
    override val description = "Read, write, list, or delete files on the phone storage. Actions: 'read', 'write', 'list', 'delete', 'exists'. Path is relative to /sdcard/BeeMovil/"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["read","write","list","delete","exists"],"description":"File operation"},
            "path":{"type":"string","description":"File path relative to /sdcard/BeeMovil/"},
            "content":{"type":"string","description":"Content to write (only for write action)"}
        },"required":["action","path"]}
    """.trimIndent())

    companion object {
        private const val TAG = "FileSkill"
    }

    private val baseDir: File by lazy {
        val dir = File(Environment.getExternalStorageDirectory(), "BeeMovil")
        dir.mkdirs()
        dir
    }

    private fun resolvePath(path: String): File {
        // Prevent path traversal attacks
        val clean = path.replace("..", "").removePrefix("/")
        return File(baseDir, clean)
    }

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "")
        val path = params.optString("path", "")

        if (path.isBlank() && action != "list") {
            return JSONObject().put("error", "Path is required")
        }

        return try {
            when (action) {
                "read" -> readFile(path)
                "write" -> writeFile(path, params.optString("content", ""))
                "list" -> listDir(path)
                "delete" -> deleteFile(path)
                "exists" -> JSONObject().put("exists", resolvePath(path).exists()).put("path", resolvePath(path).absolutePath)
                else -> JSONObject().put("error", "Action '$action' not supported")
            }
        } catch (e: Exception) {
            Log.e(TAG, "File operation failed: ${e.message}")
            JSONObject().put("error", e.message ?: "Unknown error")
        }
    }

    private fun readFile(path: String): JSONObject {
        val file = resolvePath(path)
        if (!file.exists()) return JSONObject().put("error", "File not found: ${file.absolutePath}")
        if (file.length() > 1_000_000) return JSONObject().put("error", "File too large (>1MB)")

        return JSONObject()
            .put("content", file.readText())
            .put("size", file.length())
            .put("path", file.absolutePath)
    }

    private fun writeFile(path: String, content: String): JSONObject {
        val file = resolvePath(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        Log.i(TAG, "Written ${content.length} chars to ${file.absolutePath}")

        return JSONObject()
            .put("success", true)
            .put("path", file.absolutePath)
            .put("size", file.length())
            .put("message", "Archivo guardado: ${file.name}")
    }

    private fun listDir(path: String): JSONObject {
        val dir = if (path.isBlank()) baseDir else resolvePath(path)
        if (!dir.exists()) {
            dir.mkdirs()
            return JSONObject().put("files", JSONArray()).put("path", dir.absolutePath)
        }

        val files = dir.listFiles() ?: return JSONObject().put("files", JSONArray()).put("path", dir.absolutePath)
        val arr = JSONArray()
        files.sortedBy { it.name }.forEach { f ->
            arr.put(JSONObject().apply {
                put("name", f.name)
                put("isDirectory", f.isDirectory)
                put("size", if (f.isFile) f.length() else 0)
            })
        }

        return JSONObject().put("files", arr).put("count", files.size).put("path", dir.absolutePath)
    }

    private fun deleteFile(path: String): JSONObject {
        val file = resolvePath(path)
        if (!file.exists()) return JSONObject().put("error", "File not found")

        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        return JSONObject()
            .put("success", deleted)
            .put("message", if (deleted) "Archivo eliminado" else "No se pudo eliminar")
    }
}
