package com.beemovil.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.email.EmailService
import com.beemovil.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailComposeScreen(
    replyTo: String? = null,
    replySubject: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("beemovil", android.content.Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    val securePrefs = com.beemovil.security.SecurePrefs.get(context)
    val emailAddr = securePrefs.getString("email_address", "") ?: ""
    val emailPass = securePrefs.getString("email_password", "") ?: ""
    val config = EmailService.EmailConfig(
        prefs.getString("email_imap_host", "imap.gmail.com") ?: "imap.gmail.com",
        prefs.getInt("email_imap_port", 993),
        prefs.getString("email_smtp_host", "smtp.gmail.com") ?: "smtp.gmail.com",
        prefs.getInt("email_smtp_port", 587)
    )

    var to by remember { mutableStateOf(replyTo ?: "") }
    var subject by remember { mutableStateOf(
        if (replySubject != null && !replySubject.startsWith("Re:")) "Re: $replySubject"
        else replySubject ?: ""
    ) }
    var body by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val attachedFiles = remember { mutableStateListOf<String>() }

    // Saved attachments (from previous downloads)
    val savedAttachments = remember {
        val dir = File(context.filesDir, "email_attachments")
        dir.listFiles()?.map { it.absolutePath } ?: emptyList()
    }
    var showAttachPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(BeeBlack)
    ) {
        // Top bar
        TopAppBar(
            title = { Text("Nuevo Correo", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, "Close", tint = BeeYellow)
                }
            },
            actions = {
                // Send button
                Button(
                    onClick = {
                        if (to.isBlank()) {
                            Toast.makeText(context, "⚠️ Escribe un destinatario", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSending = true
                        scope.launch {
                            try {
                                val service = EmailService(context)
                                val sent = withContext(Dispatchers.IO) {
                                    service.sendEmail(
                                        emailAddr, emailPass, config,
                                        to = to.trim(),
                                        subject = subject,
                                        body = body,
                                        attachmentPaths = attachedFiles
                                    )
                                }
                                if (sent) {
                                    Toast.makeText(context, "✅ Correo enviado", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, "❌ Error al enviar", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    enabled = !isSending && to.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BeeBlack, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Send, "Send", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enviar", fontWeight = FontWeight.Bold)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1A1A2E),
                titleContentColor = BeeWhite
            )
        )

        if (isSending) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BeeYellow)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // From
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("De:", fontSize = 13.sp, color = BeeGray, modifier = Modifier.width(60.dp))
                Text(emailAddr, fontSize = 14.sp, color = Color(0xFFE0E0E0))
            }

            HorizontalDivider(color = Color(0xFF333355))

            // To
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("Para") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = composeFieldColors()
            )

            // Subject
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Asunto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = composeFieldColors()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Body
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Mensaje") },
                modifier = Modifier.fillMaxWidth().height(250.dp),
                maxLines = 20,
                colors = composeFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Attachments
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ADJUNTOS",
                    fontSize = 11.sp, color = BeeYellow,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showAttachPicker = !showAttachPicker }) {
                    Icon(Icons.Filled.AttachFile, "Attach", tint = BeeYellow)
                }
            }

            // Attached files list
            attachedFiles.forEach { path ->
                val name = File(path).name
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📎", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(name, fontSize = 13.sp, color = Color(0xFFE0E0E0), modifier = Modifier.weight(1f))
                        IconButton(onClick = { attachedFiles.remove(path) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, "Remove", tint = Color(0xFFF44336), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Available saved files
            if (showAttachPicker && savedAttachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Archivos guardados:", fontSize = 12.sp, color = BeeGray)
                savedAttachments.forEach { path ->
                    val name = File(path).name
                    val isAttached = path in attachedFiles
                    Surface(
                        onClick = {
                            if (!isAttached) attachedFiles.add(path)
                            else attachedFiles.remove(path)
                        },
                        color = if (isAttached) BeeYellow.copy(alpha = 0.1f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isAttached) "✅" else "📄", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, fontSize = 13.sp, color = Color(0xFFE0E0E0))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun composeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BeeYellow,
    unfocusedBorderColor = Color(0xFF333355),
    focusedTextColor = BeeWhite,
    unfocusedTextColor = BeeWhite,
    focusedLabelColor = BeeYellow,
    unfocusedLabelColor = BeeGrayLight,
    cursorColor = BeeYellow
)
