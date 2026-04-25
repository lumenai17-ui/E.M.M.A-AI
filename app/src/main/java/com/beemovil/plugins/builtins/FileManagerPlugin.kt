package com.beemovil.plugins.builtins

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.plugins.SecurityGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FileManagerPlugin — Project Autonomía Phase S3
 *
 * E.M.M.A. can browse, search, organize, and manage files on the device.
 * Read ops = 🟢 GREEN, Move/rename/organize = 🟡 YELLOW, Delete = 🔴 RED.
 */
class FileManagerPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "emma_file_manager"
    private val TAG = "FileManagerPlugin"
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = """Gestor de archivos del dispositivo. Úsala cuando el usuario diga:
                'qué hay en Downloads', 'organiza mis archivos', 'busca un PDF',
                'cuánto espacio tengo', 'borra este archivo', 'mueve esto a otra carpeta',
                'encuentra archivos grandes', 'qué archivos tengo'.
                NUNCA inventes archivos — SIEMPRE consulta el sistema real.""".trimIndent(),
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray()
                            .put("list_directory")
                            .put("search_files")
                            .put("file_info")
                            .put("disk_usage")
                            .put("find_large_files")
                            .put("move_file")
                            .put("rename_file")
                            .put("organize_folder")
                            .put("delete_file")
                        )
                        put("description", """Operaciones:
                            🟢 list_directory, search_files, file_info, disk_usage, find_large_files
                            🟡 move_file, rename_file, organize_folder
                            🔴 delete_file""".trimIndent())
                    })
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Carpeta o archivo. Usar nombres cortos: 'Downloads', 'DCIM', 'Documents', 'Pictures', 'Music', o ruta absoluta.")
                    })
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para search_files) Nombre parcial o extensión a buscar (ej: '.pdf', 'factura', '.jpg').")
                    })
                    put("destination", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para move_file) Carpeta destino.")
                    })
                    put("new_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para rename_file) Nuevo nombre del archivo.")
                    })
                })
                put("required", JSONArray().put("operation"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val operation = args["operation"] as? String ?: return "Falta 'operation'."

        return withContext(Dispatchers.IO) {
            try {
                when (operation) {
                    "list_directory" -> listDirectory(args)
                    "search_files" -> searchFiles(args)
                    "file_info" -> fileInfo(args)
                    "disk_usage" -> diskUsage()
                    "find_large_files" -> findLargeFiles()
                    "move_file" -> moveFile(args)
                    "rename_file" -> renameFile(args)
                    "organize_folder" -> organizeFolder(args)
                    "delete_file" -> deleteFile(args)
                    else -> "Operación desconocida: $operation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "FileManager error", e)
                "Error en gestión de archivos: ${e.message}"
            }
        }
    }

    private fun resolveDir(name: String?): File {
        val n = name?.lowercase()?.trim() ?: "downloads"
        return when {
            n == "downloads" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            n == "dcim" || n == "cámara" || n == "camara" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            n == "documents" || n == "documentos" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            n == "pictures" || n == "imágenes" || n == "imagenes" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            n == "music" || n == "música" || n == "musica" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            n == "movies" || n == "videos" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            n.startsWith("/") -> File(n)
            else -> Environment.getExternalStoragePublicDirectory(n)
        }
    }

    private fun listDirectory(args: Map<String, Any>): String {
        val dir = resolveDir(args["path"] as? String)
        if (!dir.exists()) return "La carpeta '${dir.name}' no existe."

        val files = dir.listFiles() ?: return "No se pudo leer '${dir.name}' (sin permisos)."
        if (files.isEmpty()) return "La carpeta '${dir.name}' está vacía."

        val sorted = files.sortedByDescending { it.lastModified() }
        val byType = sorted.groupBy { it.extension.lowercase().ifBlank { "otro" } }

        return buildString {
            appendLine("📂 ${dir.name} (${files.size} archivos)")
            appendLine()
            byType.entries.sortedByDescending { it.value.size }.take(8).forEach { (ext, group) ->
                appendLine("  .$ext (${group.size}):")
                group.take(5).forEach { f ->
                    appendLine("    ${f.name} — ${formatSize(f.length())} — ${dateFormat.format(Date(f.lastModified()))}")
                }
                if (group.size > 5) appendLine("    ... y ${group.size - 5} más")
            }
        }
    }

    private fun searchFiles(args: Map<String, Any>): String {
        val query = args["query"] as? String ?: return "Falta 'query' para buscar."
        val dir = resolveDir(args["path"] as? String ?: "downloads")
        if (!dir.exists()) return "La carpeta '${dir.name}' no existe."

        val results = dir.walkTopDown()
            .filter { it.isFile && it.name.contains(query, ignoreCase = true) }
            .take(20)
            .toList()

        if (results.isEmpty()) return "No encontré archivos con '$query' en ${dir.name}."

        return buildString {
            appendLine("🔍 Resultados para '$query' en ${dir.name} (${results.size} encontrados):")
            results.forEach { f ->
                appendLine("  📄 ${f.name} — ${formatSize(f.length())} — ${dateFormat.format(Date(f.lastModified()))}")
                appendLine("     Ruta: ${f.absolutePath}")
            }
        }
    }

    private fun fileInfo(args: Map<String, Any>): String {
        val path = args["path"] as? String ?: return "Falta 'path'."
        val file = File(path).let { if (it.exists()) it else resolveDir(path) }
        if (!file.exists()) return "Archivo no encontrado: $path"

        return buildString {
            appendLine("📄 INFO: ${file.name}")
            appendLine("  Tipo: ${file.extension.uppercase().ifBlank { "Desconocido" }}")
            appendLine("  Tamaño: ${formatSize(file.length())}")
            appendLine("  Modificado: ${dateFormat.format(Date(file.lastModified()))}")
            appendLine("  Ruta: ${file.absolutePath}")
            appendLine("  Es directorio: ${file.isDirectory}")
            if (file.isDirectory) {
                appendLine("  Contenido: ${file.listFiles()?.size ?: 0} archivos")
            }
        }
    }

    private fun diskUsage(): String {
        val path = Environment.getExternalStorageDirectory()
        val stats = StatFs(path.path)
        val total = stats.totalBytes
        val free = stats.availableBytes
        val used = total - free
        val pct = if (total > 0) (used * 100 / total) else 0

        val folders = listOf("Downloads", "DCIM", "Documents", "Pictures", "Music", "Movies")

        return buildString {
            appendLine("═══ USO DE DISCO ═══")
            appendLine("  Total: ${formatSize(total)}")
            appendLine("  Usado: ${formatSize(used)} ($pct%)")
            appendLine("  Libre: ${formatSize(free)}")
            appendLine()
            appendLine("📂 POR CARPETA:")
            folders.forEach { name ->
                val dir = Environment.getExternalStoragePublicDirectory(name)
                if (dir.exists()) {
                    val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    val count = dir.walkTopDown().filter { it.isFile }.count()
                    appendLine("  $name: ${formatSize(size)} ($count archivos)")
                }
            }
        }
    }

    private fun findLargeFiles(): String {
        val root = Environment.getExternalStorageDirectory()
        val large = root.walkTopDown()
            .filter { it.isFile }
            .sortedByDescending { it.length() }
            .take(15)
            .toList()

        if (large.isEmpty()) return "No se encontraron archivos."

        return buildString {
            appendLine("═══ ARCHIVOS MÁS GRANDES ═══")
            large.forEachIndexed { i, f ->
                appendLine("  ${i + 1}. ${f.name} — ${formatSize(f.length())}")
                appendLine("     📂 ${f.parentFile?.name ?: "?"}")
            }
        }
    }

    private suspend fun moveFile(args: Map<String, Any>): String {
        val srcPath = args["path"] as? String ?: return "Falta 'path' del archivo a mover."
        val destPath = args["destination"] as? String ?: return "Falta 'destination'."

        val src = File(srcPath)
        if (!src.exists()) return "Archivo no encontrado: $srcPath"
        val destDir = resolveDir(destPath)
        destDir.mkdirs()
        val dest = File(destDir, src.name)

        val op = SecurityGate.yellow(id, "move_file", "Mover '${src.name}' → ${destDir.name}/")
        if (!SecurityGate.evaluate(op)) return "Movimiento cancelado."

        return if (src.renameTo(dest)) {
            "Archivo movido ✅: ${src.name} → ${destDir.name}/"
        } else {
            // Fallback: copy + delete
            src.copyTo(dest, overwrite = true)
            src.delete()
            "Archivo movido ✅: ${src.name} → ${destDir.name}/"
        }
    }

    private suspend fun renameFile(args: Map<String, Any>): String {
        val srcPath = args["path"] as? String ?: return "Falta 'path' del archivo."
        val newName = args["new_name"] as? String ?: return "Falta 'new_name'."

        val src = File(srcPath)
        if (!src.exists()) return "Archivo no encontrado: $srcPath"
        val dest = File(src.parentFile, newName)

        val op = SecurityGate.yellow(id, "rename_file", "Renombrar '${src.name}' → '$newName'")
        if (!SecurityGate.evaluate(op)) return "Renombrado cancelado."

        return if (src.renameTo(dest)) {
            "Archivo renombrado ✅: ${src.name} → $newName"
        } else {
            "Error al renombrar. Verifica permisos."
        }
    }

    private suspend fun organizeFolder(args: Map<String, Any>): String {
        val dir = resolveDir(args["path"] as? String)
        if (!dir.exists()) return "Carpeta no encontrada."

        val files = dir.listFiles()?.filter { it.isFile } ?: return "No hay archivos para organizar."
        if (files.isEmpty()) return "La carpeta ya está vacía."

        val op = SecurityGate.yellow(id, "organize_folder", "Organizar ${files.size} archivos en ${dir.name} en subcarpetas por tipo")
        if (!SecurityGate.evaluate(op)) return "Organización cancelada."

        val categories = mapOf(
            "Documentos" to listOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx"),
            "Imágenes" to listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic"),
            "Videos" to listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm"),
            "Audio" to listOf("mp3", "wav", "flac", "aac", "ogg", "m4a"),
            "Instaladores" to listOf("apk", "xapk"),
            "Comprimidos" to listOf("zip", "rar", "7z", "tar", "gz")
        )

        var moved = 0
        val summary = mutableMapOf<String, Int>()

        files.forEach { file ->
            val ext = file.extension.lowercase()
            val category = categories.entries.find { ext in it.value }?.key ?: "Otros"
            val subDir = File(dir, category)
            subDir.mkdirs()
            val dest = File(subDir, file.name)
            if (!dest.exists()) {
                file.renameTo(dest)
                moved++
                summary[category] = (summary[category] ?: 0) + 1
            }
        }

        return buildString {
            appendLine("Carpeta organizada ✅ ($moved archivos movidos)")
            summary.forEach { (cat, count) -> appendLine("  📁 $cat: $count archivos") }
        }
    }

    private suspend fun deleteFile(args: Map<String, Any>): String {
        val path = args["path"] as? String ?: return "Falta 'path' del archivo a eliminar."
        val file = File(path)
        if (!file.exists()) return "Archivo no encontrado: $path"

        val op = SecurityGate.red(id, "delete_file", "ELIMINAR permanentemente: ${file.name} (${formatSize(file.length())})")
        if (!SecurityGate.evaluate(op)) return "Eliminación cancelada."

        return if (file.delete()) {
            Log.i(TAG, "File deleted: $path")
            "Archivo eliminado ✅: ${file.name}"
        } else {
            "Error al eliminar. Verifica permisos."
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)} GB"
        bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
