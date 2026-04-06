package com.beemovil.skills

import android.content.Context
import android.os.Environment
import android.util.Log
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GitSkill — Git operations from the phone using JGit.
 *
 * Enables the agent to:
 * - Clone repositories from GitHub/GitLab
 * - Create commits and push changes
 * - Pull updates
 * - View commit history and diffs
 * - Check repository status
 *
 * Repos are stored in /sdcard/BeeMovil/repos/
 */
class GitSkill(private val context: Context) : BeeSkill {
    override val name = "git"
    override val description = """Git operations. Actions:
        - 'clone': Clone a repository. Params: url, name (optional), token (optional for private repos)
        - 'status': Check repo status. Params: repo
        - 'add': Stage files. Params: repo, files (optional, default: all)
        - 'commit': Commit staged changes. Params: repo, message
        - 'push': Push to remote. Params: repo, token
        - 'pull': Pull from remote. Params: repo, token (optional)
        - 'log': Show commit history. Params: repo, count (optional, default: 10)
        - 'diff': Show changes. Params: repo
        - 'list_repos': List cloned repositories
        'repo' is the folder name inside /sdcard/BeeMovil/repos/"""

    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","description":"git operation: clone, status, add, commit, push, pull, log, diff, list_repos"},
            "url":{"type":"string","description":"Repository URL for clone"},
            "repo":{"type":"string","description":"Repository folder name"},
            "name":{"type":"string","description":"Custom folder name for clone"},
            "token":{"type":"string","description":"GitHub/GitLab personal access token"},
            "message":{"type":"string","description":"Commit message"},
            "files":{"type":"string","description":"Files to add (comma-separated, or 'all')"},
            "count":{"type":"integer","description":"Number of commits for log"}
        },"required":["action"]}
    """.trimIndent())

    companion object {
        private const val TAG = "GitSkill"
    }

    private val reposDir: File by lazy {
        val dir = File(Environment.getExternalStorageDirectory(), "BeeMovil/repos")
        dir.mkdirs()
        dir
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private fun getRepoDir(name: String): File = File(reposDir, name)

    private fun getCredentials(params: JSONObject): UsernamePasswordCredentialsProvider? {
        val token = params.optString("token", "")
        if (token.isBlank()) {
            // Try saved token from prefs
            val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
            val saved = com.beemovil.security.SecurePrefs.get(context).getString("github_token", "") ?: ""
            return if (saved.isNotBlank()) UsernamePasswordCredentialsProvider("token", saved) else null
        }
        return UsernamePasswordCredentialsProvider("token", token)
    }

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "")
        return try {
            when (action) {
                "clone" -> cloneRepo(params)
                "status" -> withRepo(params) { git -> repoStatus(git) }
                "add" -> withRepo(params) { git -> addFiles(git, params) }
                "commit" -> withRepo(params) { git -> commitChanges(git, params) }
                "push" -> withRepo(params) { git -> pushChanges(git, params) }
                "pull" -> withRepo(params) { git -> pullChanges(git, params) }
                "log" -> withRepo(params) { git -> commitLog(git, params) }
                "diff" -> withRepo(params) { git -> showDiff(git) }
                "list_repos" -> listRepos()
                else -> JSONObject().put("error", "Unknown action: $action")
            }
        } catch (e: GitAPIException) {
            Log.e(TAG, "Git error: ${e.message}", e)
            JSONObject().put("error", "Git error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            JSONObject().put("error", "${e.message}")
        }
    }

    /** Safely opens and closes a Git repo for an operation. */
    private fun withRepo(params: JSONObject, block: (Git) -> JSONObject): JSONObject {
        val repoDir = getRepoDir(params.optString("repo", ""))
        if (!repoDir.exists()) return JSONObject().put("error", "Repo not found")
        val git = Git.open(repoDir)
        return try {
            block(git)
        } finally {
            git.close()
        }
    }

    private fun cloneRepo(params: JSONObject): JSONObject {
        val url = params.optString("url", "")
        if (url.isBlank()) return JSONObject().put("error", "url required")

        // Extract repo name from URL or use provided name
        val repoName = params.optString("name", "").ifBlank {
            url.substringAfterLast("/").removeSuffix(".git")
        }
        val repoDir = getRepoDir(repoName)

        if (repoDir.exists() && repoDir.listFiles()?.isNotEmpty() == true) {
            return JSONObject().put("error", "Repository '$repoName' already exists. Use pull to update.")
        }

        repoDir.mkdirs()
        val cloneCmd = Git.cloneRepository()
            .setURI(url)
            .setDirectory(repoDir)

        getCredentials(params)?.let { cloneCmd.setCredentialsProvider(it) }

        val git = cloneCmd.call()
        val headCommit = git.log().setMaxCount(1).call().firstOrNull()

        git.close()

        Log.i(TAG, "Cloned: $url → $repoDir")
        return JSONObject()
            .put("success", true)
            .put("repo", repoName)
            .put("path", repoDir.absolutePath)
            .put("last_commit", headCommit?.shortMessage ?: "N/A")
            .put("message", "Repo clonado: $repoName en ${repoDir.absolutePath}")
    }

    private fun repoStatus(git: Git): JSONObject {
        val status = git.status().call()

        val result = JSONObject()
            .put("branch", git.repository.branch)
            .put("clean", status.isClean)
            .put("added", JSONArray(status.added))
            .put("modified", JSONArray(status.modified))
            .put("removed", JSONArray(status.removed))
            .put("untracked", JSONArray(status.untracked))
            .put("changed", JSONArray(status.changed))

        val totalChanges = status.added.size + status.modified.size + status.removed.size + status.untracked.size
        result.put("message", if (status.isClean) "Repo limpio, sin cambios" else "$totalChanges archivo(s) con cambios")

        return result
    }

    private fun addFiles(git: Git, params: JSONObject): JSONObject {
        val files = params.optString("files", "all")

        if (files == "all" || files.isBlank()) {
            git.add().addFilepattern(".").call()
        } else {
            val addCmd = git.add()
            files.split(",").forEach { addCmd.addFilepattern(it.trim()) }
            addCmd.call()
        }

        return JSONObject()
            .put("success", true)
            .put("message", "Archivos agregados al staging")
    }

    private fun commitChanges(git: Git, params: JSONObject): JSONObject {
        val message = params.optString("message", "Update from Bee-Movil")

        // Auto-add all changes
        git.add().addFilepattern(".").call()

        val commit = git.commit()
            .setMessage(message)
            .setAuthor(PersonIdent("Bee-Movil", "bee@beemovil.app"))
            .call()

        return JSONObject()
            .put("success", true)
            .put("commit_id", commit.name.take(7))
            .put("message_used", message)
            .put("message", "Commit: ${commit.name.take(7)} - $message")
    }

    private fun pushChanges(git: Git, params: JSONObject): JSONObject {
        val pushCmd = git.push()

        getCredentials(params)?.let { pushCmd.setCredentialsProvider(it) }
            ?: return JSONObject().put("error", "Token required for push. Provide 'token' or save in Settings.")

        pushCmd.call()

        return JSONObject()
            .put("success", true)
            .put("message", "Push completado exitosamente")
    }

    private fun pullChanges(git: Git, params: JSONObject): JSONObject {
        val pullCmd = git.pull()
        getCredentials(params)?.let { pullCmd.setCredentialsProvider(it) }

        val result = pullCmd.call()

        return JSONObject()
            .put("success", result.isSuccessful)
            .put("message", if (result.isSuccessful) "Pull completado" else "Pull fallido: ${result.mergeResult?.mergeStatus}")
    }

    private fun commitLog(git: Git, params: JSONObject): JSONObject {
        val count = params.optInt("count", 10)
        val log = git.log().setMaxCount(count).call()

        val commits = JSONArray()
        log.forEach { commit ->
            commits.put(JSONObject()
                .put("id", commit.name.take(7))
                .put("message", commit.shortMessage)
                .put("author", commit.authorIdent.name)
                .put("date", dateFormat.format(Date(commit.commitTime * 1000L)))
            )
        }

        return JSONObject()
            .put("commits", commits)
            .put("count", commits.length())
            .put("message", "${commits.length()} commits")
    }

    private fun showDiff(git: Git): JSONObject {
        val status = git.status().call()

        val sb = StringBuilder()
        status.modified.forEach { file ->
            sb.appendLine("M  $file")
        }
        status.added.forEach { file ->
            sb.appendLine("A  $file")
        }
        status.removed.forEach { file ->
            sb.appendLine("D  $file")
        }
        status.untracked.forEach { file ->
            sb.appendLine("?  $file")
        }

        git.close()

        return JSONObject()
            .put("diff", sb.toString().ifBlank { "No changes" })
            .put("modified", status.modified.size)
            .put("added", status.added.size)
            .put("removed", status.removed.size)
            .put("untracked", status.untracked.size)
    }

    private fun listRepos(): JSONObject {
        val repos = reposDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val arr = JSONArray()

        repos.forEach { dir ->
            try {
                val git = Git.open(dir)
                val branch = git.repository.branch
                val lastCommit = git.log().setMaxCount(1).call().firstOrNull()
                arr.put(JSONObject()
                    .put("name", dir.name)
                    .put("path", dir.absolutePath)
                    .put("branch", branch)
                    .put("last_commit", lastCommit?.shortMessage ?: "N/A")
                )
                git.close()
            } catch (e: Exception) {
                arr.put(JSONObject()
                    .put("name", dir.name)
                    .put("path", dir.absolutePath)
                    .put("error", "Not a git repo")
                )
            }
        }

        return JSONObject()
            .put("repos", arr)
            .put("count", arr.length())
            .put("base_path", reposDir.absolutePath)
            .put("message", "${arr.length()} repositorio(s) en ${reposDir.absolutePath}")
    }
}
