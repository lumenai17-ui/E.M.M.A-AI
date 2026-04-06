package com.beemovil.skills

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * FileManagerSkill — Advanced file operations.
 *
 * Extends beyond the basic FileSkill to support:
 * - Multi-file project creation
 * - Copy, move, rename operations
 * - Access to common directories (Downloads, Documents, DCIM)
 * - File search
 * - Directory tree view
 *
 * This enables the agent to create complete multi-file projects
 * (HTML+CSS+JS, etc.) and organize user files.
 */
class FileManagerSkill(private val context: Context) : BeeSkill {
    override val name = "file_manager"
    override val description = """Advanced file management. Actions:
        - 'create_project': Create a folder with multiple files. Params: project_name, files: [{name, content}]
        - 'copy': Copy a file. Params: source, destination
        - 'move': Move/rename a file. Params: source, destination
        - 'rename': Rename a file. Params: path, new_name
        - 'search': Search files by name pattern. Params: directory, pattern
        - 'tree': Show directory tree. Params: path
        - 'info': Get file details. Params: path
        - 'create_dir': Create a directory. Params: path
        Base directory: /sdcard/BeeMovil/ (use 'base:' prefix for relative paths)
        Common dirs: 'downloads:', 'documents:', 'dcim:', 'pictures:'"""

    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","description":"Operation: create_project, copy, move, rename, search, tree, info, create_dir"},
            "project_name":{"type":"string","description":"Name for new project folder"},
            "files":{"type":"array","items":{"type":"object"},"description":"Array of {name, content} for create_project"},
            "path":{"type":"string","description":"File or directory path"},
            "source":{"type":"string","description":"Source path for copy/move"},
            "destination":{"type":"string","description":"Destination path for copy/move"},
            "new_name":{"type":"string","description":"New name for rename"},
            "directory":{"type":"string","description":"Directory to search in"},
            "pattern":{"type":"string","description":"Search pattern (case insensitive)"}
        },"required":["action"]}
    """.trimIndent())

    companion object {
        private const val TAG = "FileManagerSkill"
    }

    /**
     * Resolve path supporting special prefixes:
     * - base: → /sdcard/BeeMovil/
     * - downloads: → /sdcard/Download/
     * - documents: → /sdcard/Documents/
     * - dcim: → /sdcard/DCIM/
     * - pictures: → /sdcard/Pictures/
     * - /absolute/path → used as-is
     */
    private fun resolvePath(path: String): File {
        val clean = path.replace("..", "").trim()
        return when {
            clean.startsWith("downloads:") -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), clean.removePrefix("downloads:"))
            clean.startsWith("documents:") -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), clean.removePrefix("documents:"))
            clean.startsWith("dcim:") -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), clean.removePrefix("dcim:"))
            clean.startsWith("pictures:") -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), clean.removePrefix("pictures:"))
            clean.startsWith("/") -> File(clean)
            else -> File(Environment.getExternalStorageDirectory(), "BeeMovil/$clean")
        }
    }

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "")
        return try {
            when (action) {
                "create_project" -> createProject(params)
                "copy" -> copyFile(params)
                "move" -> moveFile(params)
                "rename" -> renameFile(params)
                "search" -> searchFiles(params)
                "tree" -> showTree(params)
                "info" -> fileInfo(params)
                "create_dir" -> createDir(params)
                else -> JSONObject().put("error", "Unknown action: $action. Use: create_project, copy, move, rename, search, tree, info, create_dir")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FileManager error: ${e.message}", e)
            JSONObject().put("error", "${e.message}")
        }
    }

    /**
     * Create a multi-file project in a named folder.
     * Example: create_project("mi-sitio", [{name: "index.html", content: "..."}, ...])
     */
    private fun createProject(params: JSONObject): JSONObject {
        val projectName = params.optString("project_name", "")
        if (projectName.isBlank()) return JSONObject().put("error", "project_name required")

        val files = params.optJSONArray("files")
            ?: return JSONObject().put("error", "files array required: [{name, content}, ...]")

        val projectDir = File(Environment.getExternalStorageDirectory(), "BeeMovil/projects/$projectName")
        projectDir.mkdirs()

        val createdFiles = mutableListOf<String>()
        for (i in 0 until files.length()) {
            val fileObj = files.getJSONObject(i)
            val fileName = fileObj.optString("name", "")
            val content = fileObj.optString("content", "")
            if (fileName.isBlank()) continue

            val file = File(projectDir, fileName)
            file.parentFile?.mkdirs()
            file.writeText(content)
            createdFiles.add(fileName)
        }

        Log.i(TAG, "Project created: $projectName with ${createdFiles.size} files")
        return JSONObject()
            .put("success", true)
            .put("project_path", projectDir.absolutePath)
            .put("files_created", JSONArray(createdFiles))
            .put("count", createdFiles.size)
            .put("message", "Proyecto '$projectName' creado con ${createdFiles.size} archivos en ${projectDir.absolutePath}")
    }

    private fun copyFile(params: JSONObject): JSONObject {
        val source = resolvePath(params.optString("source", ""))
        val dest = resolvePath(params.optString("destination", ""))
        if (!source.exists()) return JSONObject().put("error", "Source not found: ${source.absolutePath}")

        dest.parentFile?.mkdirs()
        if (source.isDirectory) {
            source.copyRecursively(dest, overwrite = true)
        } else {
            source.copyTo(dest, overwrite = true)
        }

        return JSONObject()
            .put("success", true)
            .put("source", source.absolutePath)
            .put("destination", dest.absolutePath)
            .put("message", "Copiado: ${source.name} → ${dest.absolutePath}")
    }

    private fun moveFile(params: JSONObject): JSONObject {
        val source = resolvePath(params.optString("source", ""))
        val dest = resolvePath(params.optString("destination", ""))
        if (!source.exists()) return JSONObject().put("error", "Source not found: ${source.absolutePath}")

        dest.parentFile?.mkdirs()
        val moved = source.renameTo(dest)

        return if (moved) {
            JSONObject()
                .put("success", true)
                .put("new_path", dest.absolutePath)
                .put("message", "Movido: ${source.name} → ${dest.absolutePath}")
        } else {
            // Fallback: copy + delete
            source.copyTo(dest, overwrite = true)
            source.delete()
            JSONObject()
                .put("success", true)
                .put("new_path", dest.absolutePath)
                .put("message", "Movido: ${source.name} → ${dest.absolutePath}")
        }
    }

    private fun renameFile(params: JSONObject): JSONObject {
        val file = resolvePath(params.optString("path", ""))
        val newName = params.optString("new_name", "")
        if (!file.exists()) return JSONObject().put("error", "File not found")
        if (newName.isBlank()) return JSONObject().put("error", "new_name required")

        val dest = File(file.parentFile, newName)
        val renamed = file.renameTo(dest)

        return JSONObject()
            .put("success", renamed)
            .put("new_path", dest.absolutePath)
            .put("message", if (renamed) "Renombrado: ${file.name} → $newName" else "Error al renombrar")
    }

    private fun searchFiles(params: JSONObject): JSONObject {
        val dir = resolvePath(params.optString("directory", ""))
        val pattern = params.optString("pattern", "").lowercase()
        if (pattern.isBlank()) return JSONObject().put("error", "pattern required")

        val results = JSONArray()
        var count = 0
        val maxResults = 50

        fun searchRecursive(d: File) {
            d.listFiles()?.forEach { f ->
                if (count >= maxResults) return
                if (f.name.lowercase().contains(pattern)) {
                    results.put(JSONObject()
                        .put("name", f.name)
                        .put("path", f.absolutePath)
                        .put("isDirectory", f.isDirectory)
                        .put("size", if (f.isFile) f.length() else 0))
                    count++
                }
                if (f.isDirectory) searchRecursive(f)
            }
        }

        searchRecursive(if (dir.exists()) dir else File(Environment.getExternalStorageDirectory(), "BeeMovil"))

        return JSONObject()
            .put("results", results)
            .put("count", count)
            .put("message", "$count archivo(s) encontrados con '$pattern'")
    }

    private fun showTree(params: JSONObject): JSONObject {
        val dir = resolvePath(params.optString("path", ""))
        if (!dir.exists()) return JSONObject().put("error", "Directory not found: ${dir.absolutePath}")

        val sb = StringBuilder()
        var fileCount = 0
        var dirCount = 0

        fun buildTree(d: File, indent: String, isLast: Boolean) {
            val prefix = if (indent.isEmpty()) "" else if (isLast) "L── " else "T── "
            val icon = if (d.isDirectory) "[DIR]" else "[DOC]"
            sb.appendLine("$indent$prefix$icon ${d.name}" + if (d.isFile) " (${humanSize(d.length())})" else "")

            if (d.isDirectory) {
                dirCount++
                val children = d.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
                children.forEachIndexed { idx, child ->
                    val newIndent = indent + if (indent.isEmpty()) "" else if (isLast) "    " else "I   "
                    buildTree(child, newIndent, idx == children.lastIndex)
                }
            } else {
                fileCount++
            }
        }

        buildTree(dir, "", true)

        return JSONObject()
            .put("tree", sb.toString())
            .put("files", fileCount)
            .put("directories", dirCount)
            .put("message", "${dir.name}: $fileCount archivos, $dirCount carpetas")
    }

    private fun fileInfo(params: JSONObject): JSONObject {
        val file = resolvePath(params.optString("path", ""))
        if (!file.exists()) return JSONObject().put("error", "Not found: ${file.absolutePath}")

        return JSONObject()
            .put("name", file.name)
            .put("path", file.absolutePath)
            .put("isDirectory", file.isDirectory)
            .put("size", file.length())
            .put("size_human", humanSize(file.length()))
            .put("last_modified", file.lastModified())
            .put("readable", file.canRead())
            .put("writable", file.canWrite())
            .put("extension", file.extension)
    }

    private fun createDir(params: JSONObject): JSONObject {
        val dir = resolvePath(params.optString("path", ""))
        val created = dir.mkdirs()
        return JSONObject()
            .put("success", created || dir.exists())
            .put("path", dir.absolutePath)
            .put("message", "Carpeta creada: ${dir.absolutePath}")
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toFloat() / 1024 / 1024)} MB"
        else -> "${"%.2f".format(bytes.toFloat() / 1024 / 1024 / 1024)} GB"
    }
}
