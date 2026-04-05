package com.beemovil.ui.screens

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.ui.theme.*
import org.eclipse.jgit.api.Git
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class RepoInfo(
    val name: String,
    val path: String,
    val branch: String,
    val lastCommit: String,
    val lastDate: String,
    val dirty: Boolean
)

data class CommitInfo(
    val id: String,
    val message: String,
    val author: String,
    val date: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val reposDir = File(Environment.getExternalStorageDirectory(), "BeeMovil/repos")
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }

    var repos by remember { mutableStateOf(loadRepos(reposDir, dateFormatter)) }
    var selectedRepo by remember { mutableStateOf<RepoInfo?>(null) }
    var commits by remember { mutableStateOf<List<CommitInfo>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
    ) {
        // ══════ Top Bar ══════
        TopAppBar(
            title = {
                Column {
                    Text(
                        if (selectedRepo != null) selectedRepo!!.name else "Git Repos",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    if (selectedRepo != null) {
                        Text("Branch: ${selectedRepo!!.branch}", fontSize = 11.sp, color = BeeYellow)
                    } else {
                        Text("${repos.size} repositorios", fontSize = 11.sp, color = BeeGray)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (selectedRepo != null) {
                        selectedRepo = null
                        commits = emptyList()
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow)
                }
            },
            actions = {
                IconButton(onClick = {
                    repos = loadRepos(reposDir, dateFormatter)
                }) {
                    Icon(Icons.Filled.Refresh, "Refresh", tint = BeeGray)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BeeBlackLight,
                titleContentColor = BeeWhite
            )
        )

        if (selectedRepo != null) {
            // ══════ Repo Detail — Commits ══════
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp)
            ) {
                // Status card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        color = BeeBlackLight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (selectedRepo!!.dirty) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                    "Status",
                                    tint = if (selectedRepo!!.dirty) BeeYellow else Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (selectedRepo!!.dirty) "Cambios sin guardar" else "Repositorio limpio",
                                    color = BeeWhite, fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Branch: ${selectedRepo!!.branch}", fontSize = 12.sp, color = BeeGray)
                            Text("Path: ${selectedRepo!!.path}", fontSize = 11.sp, color = BeeGray)
                        }
                    }
                }

                item {
                    Text(
                        "COMMITS", fontSize = 11.sp, color = BeeYellow,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }

                items(commits) { commit ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        color = BeeBlackLight,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            // Commit dot
                            Surface(
                                modifier = Modifier.size(8.dp).offset(y = 6.dp),
                                color = BeeYellow,
                                shape = RoundedCornerShape(4.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    commit.message, color = BeeWhite, fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(commit.id, fontSize = 11.sp, color = BeeYellow, fontWeight = FontWeight.Medium)
                                    Text(commit.author, fontSize = 11.sp, color = BeeGray)
                                    Text(commit.date, fontSize = 11.sp, color = BeeGray)
                                }
                            }
                        }
                    }
                }

                if (commits.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hay commits", color = BeeGray)
                        }
                    }
                }
            }
        } else {
            // ══════ Repos List ══════
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(repos) { repo ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedRepo = repo
                                commits = loadCommits(File(repo.path), dateFormatter)
                            },
                        color = BeeBlackLight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Repo icon
                            Surface(
                                color = BeeYellow.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Filled.Storage, "Repo", tint = BeeYellow, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(repo.name, color = BeeWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(repo.lastCommit, fontSize = 12.sp, color = BeeGray,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        color = BeeYellow.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            repo.branch, fontSize = 10.sp, color = BeeYellow,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(repo.lastDate, fontSize = 10.sp, color = BeeGray)
                                }
                            }

                            // Status indicator
                            if (repo.dirty) {
                                Surface(
                                    color = BeeYellow.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "Modified", fontSize = 10.sp, color = BeeYellow,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Icon(Icons.Filled.ChevronRight, "Open", tint = BeeGray, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (repos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Storage, "No repos", tint = BeeGray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No hay repositorios", color = BeeGray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Usa el chat: \"Clona mi repo de GitHub\"", fontSize = 12.sp, color = BeeYellow)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadRepos(dir: File, dateFormatter: SimpleDateFormat): List<RepoInfo> {
    if (!dir.exists()) {
        dir.mkdirs()
        return emptyList()
    }
    return (dir.listFiles() ?: emptyArray())
        .filter { it.isDirectory }
        .mapNotNull { repoDir ->
            try {
                val git = Git.open(repoDir)
                val status = git.status().call()
                val head = git.log().setMaxCount(1).call().firstOrNull()
                val info = RepoInfo(
                    name = repoDir.name,
                    path = repoDir.absolutePath,
                    branch = git.repository.branch,
                    lastCommit = head?.shortMessage ?: "No commits",
                    lastDate = if (head != null) dateFormatter.format(Date(head.commitTime * 1000L)) else "",
                    dirty = !status.isClean
                )
                git.close()
                info
            } catch (e: Exception) {
                null
            }
        }
        .sortedByDescending { it.lastDate }
}

private fun loadCommits(repoDir: File, dateFormatter: SimpleDateFormat): List<CommitInfo> {
    return try {
        val git = Git.open(repoDir)
        val commits = git.log().setMaxCount(30).call().map { commit ->
            CommitInfo(
                id = commit.name.take(7),
                message = commit.shortMessage,
                author = commit.authorIdent.name,
                date = dateFormatter.format(Date(commit.commitTime * 1000L))
            )
        }
        git.close()
        commits
    } catch (e: Exception) {
        emptyList()
    }
}
