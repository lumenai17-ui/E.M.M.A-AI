package com.beemovil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.email.EmailService
import com.beemovil.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailInboxScreen(
    onEmailClick: (Long) -> Unit,
    onCompose: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("beemovil", android.content.Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    val emailAddr = prefs.getString("email_address", "") ?: ""
    val emailPass = prefs.getString("email_password", "") ?: ""
    val imapHost = prefs.getString("email_imap_host", "imap.gmail.com") ?: "imap.gmail.com"
    val imapPort = prefs.getInt("email_imap_port", 993)
    val smtpHost = prefs.getString("email_smtp_host", "smtp.gmail.com") ?: "smtp.gmail.com"
    val smtpPort = prefs.getInt("email_smtp_port", 587)

    val isConfigured = emailAddr.isNotBlank() && emailPass.isNotBlank()
    val emails = remember { mutableStateListOf<EmailService.EmailMessage>() }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Auto-fetch on open
    LaunchedEffect(isConfigured) {
        if (isConfigured) {
            isLoading = true
            error = null
            try {
                val config = EmailService.EmailConfig(imapHost, imapPort, smtpHost, smtpPort)
                val service = EmailService(context)
                val fetched = withContext(Dispatchers.IO) {
                    service.fetchInbox(emailAddr, emailPass, config, limit = 30)
                }
                emails.clear()
                emails.addAll(fetched)
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BeeBlack)
    ) {
        // Header
        Surface(
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(Color(0xFF1565C0).copy(alpha = 0.15f), BeeBlack))
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Mi Correo", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = BeeWhite)
                    if (isConfigured) {
                        Text(emailAddr, fontSize = 12.sp, color = BeeGray)
                    }
                }
                Row {
                    if (isConfigured) {
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                try {
                                    val config = EmailService.EmailConfig(imapHost, imapPort, smtpHost, smtpPort)
                                    val service = EmailService(context)
                                    val fetched = withContext(Dispatchers.IO) {
                                        service.fetchInbox(emailAddr, emailPass, config, limit = 30)
                                    }
                                    emails.clear()
                                    emails.addAll(fetched)
                                } catch (e: Exception) {
                                    error = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, "Refresh", tint = BeeYellow)
                        }
                    }
                    FloatingActionButton(
                        onClick = onCompose,
                        containerColor = BeeYellow,
                        contentColor = BeeBlack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Edit, "Compose", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Loading
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = BeeYellow,
                trackColor = Color(0xFF1A1A2E)
            )
        }

        // Error
        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("⚠️ $error", color = Color(0xFFF44336), fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp))
            }
        }

        // Not configured
        if (!isConfigured) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📧", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Configura tu correo", fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, color = BeeWhite)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Ve a Config → configura tu email\npara ver tu bandeja de entrada aquí",
                            fontSize = 13.sp, color = BeeGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            return
        }

        // Email list
        if (emails.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("📭 Bandeja vacía", fontSize = 16.sp, color = BeeGray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                // Group by date
                val today = Calendar.getInstance()
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

                val grouped = emails.groupBy { email ->
                    val cal = Calendar.getInstance().apply { time = email.date }
                    when {
                        isSameDay(cal, today) -> "Hoy"
                        isSameDay(cal, yesterday) -> "Ayer"
                        else -> SimpleDateFormat("dd MMM", Locale("es")).format(email.date)
                    }
                }

                grouped.forEach { (dateLabel, groupEmails) ->
                    item {
                        Text(
                            dateLabel.uppercase(),
                            fontSize = 11.sp, color = BeeYellow,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    groupEmails.forEach { email ->
                        item {
                            EmailRow(
                                email = email,
                                onClick = { onEmailClick(email.uid) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailRow(email: EmailService.EmailMessage, onClick: () -> Unit) {
    val initial = email.from.firstOrNull()?.uppercase() ?: "?"
    val bgColor = getAvatarColor(email.fromEmail)

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            Surface(
                color = bgColor,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(initial, fontSize = 18.sp, color = BeeWhite, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        email.from,
                        fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        color = if (!email.isRead) BeeWhite else Color(0xFFB0B0B0),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(email.date),
                        fontSize = 11.sp, color = BeeGray
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    email.subject,
                    fontWeight = if (!email.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                    color = if (!email.isRead) Color(0xFFE0E0E0) else Color(0xFF999999),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        email.body.replace(Regex("<[^>]*>"), "").take(80).trim(),
                        fontSize = 12.sp, color = BeeGray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (email.hasAttachments) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("📎", fontSize = 12.sp)
                    }
                    if (email.isStarred) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("⭐", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun getAvatarColor(email: String): Color {
    val colors = listOf(
        Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFC62828),
        Color(0xFF6A1B9A), Color(0xFFEF6C00), Color(0xFF00838F),
        Color(0xFF4527A0), Color(0xFFAD1457)
    )
    val hash = email.hashCode().let { if (it < 0) -it else it }
    return colors[hash % colors.size]
}
