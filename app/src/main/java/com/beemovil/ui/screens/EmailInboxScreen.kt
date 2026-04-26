package com.beemovil.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.google.GoogleAuthManager
import com.beemovil.google.GoogleGmailService
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailInboxScreen(viewModel: ChatViewModel) {
    val isDark = isDarkTheme()
    val bg = if (isDark) Color(0xFF0F0F16) else LightBackground
    val textPrimary = if (isDark) BeeWhite else TextDark
    val textSecondary = if (isDark) BeeGray else TextGrayDark
    val accent = if (isDark) BeeYellow else BrandBlue
    val cardBg = if (isDark) Color(0xFF1E1E2C) else LightSurface
    val avatarBg = if (isDark) Color(0xFF2A2A3D) else LightCard
    val dividerColor = if (isDark) Color(0xFF1E1E2C) else LightBorder

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuth = remember { GoogleAuthManager(context) }
    val isSignedIn = googleAuth.isSignedIn()
    val accessToken = googleAuth.getAccessToken()

    // IMAP personal email credentials
    val securePrefs = remember { com.beemovil.security.SecurePrefs.get(context) }
    val prefs = remember { context.getSharedPreferences("beemovil", android.content.Context.MODE_PRIVATE) }
    // email_address & email_password → securePrefs (encrypted)
    val imapEmail = securePrefs.getString("email_address", "") ?: ""
    val imapPassword = securePrefs.getString("email_password", "") ?: ""
    // IMAP/SMTP host/port → regular prefs (matching SettingsScreen save logic)
    val imapHost = prefs.getString("email_imap_host", "") ?: ""
    val imapPort = prefs.getInt("email_imap_port", 993)
    val smtpHost = prefs.getString("email_smtp_host", "") ?: ""
    val smtpPort = prefs.getInt("email_smtp_port", 587)
    val hasImapConfig = imapEmail.isNotBlank() && imapPassword.isNotBlank() && imapHost.isNotBlank()

    // Active source: "google" or "personal"
    var activeSource by remember {
        mutableStateOf(if (accessToken != null) "google" else if (hasImapConfig) "personal" else "google")
    }

    // View mode: "list" | "detail" | "compose"
    var viewMode by remember { mutableStateOf("list") }

    var emails by remember { mutableStateOf(viewModel.cachedGmailEmails.value) }
    var imapEmails by remember { mutableStateOf(viewModel.cachedImapEmails.value) }
    var unreadCount by remember { mutableStateOf(
        if (activeSource == "google") viewModel.cachedGmailUnread.value else viewModel.cachedImapUnread.value
    ) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedEmail by remember { mutableStateOf<GoogleGmailService.EmailMessage?>(null) }
    var selectedImapEmail by remember { mutableStateOf<com.beemovil.email.EmailService.EmailMessage?>(null) }

    // Compose state
    var composeTo by remember { mutableStateOf("") }
    var composeSubject by remember { mutableStateOf("") }
    var composeBody by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sendResult by remember { mutableStateOf<String?>(null) }

    // Helper: force network refresh
    fun forceRefresh() {
        scope.launch {
            isLoading = true; errorMsg = null
            withContext(Dispatchers.IO) {
                try {
                    if (activeSource == "google" && accessToken != null) {
                        val service = GoogleGmailService(accessToken)
                        val fetched = service.listInbox(maxResults = 25)
                        val count = service.getUnreadCount()
                        emails = fetched
                        unreadCount = count
                        viewModel.cachedGmailEmails.value = fetched
                        viewModel.cachedGmailUnread.value = count
                    } else if (activeSource == "personal" && hasImapConfig) {
                        val emailService = com.beemovil.email.EmailService(context)
                        val config = com.beemovil.email.EmailService.EmailConfig(imapHost, imapPort, smtpHost, smtpPort)
                        val fetched = emailService.fetchInbox(imapEmail, imapPassword, config, limit = 25)
                        val count = fetched.count { !it.isRead }
                        imapEmails = fetched
                        unreadCount = count
                        viewModel.cachedImapEmails.value = fetched
                        viewModel.cachedImapUnread.value = count
                    }
                    viewModel.emailLastFetchTime.value = System.currentTimeMillis()
                } catch (e: Exception) { errorMsg = e.message }
            }
            isLoading = false
        }
    }

    // Load emails: from cache if fresh, else from network
    LaunchedEffect(accessToken, activeSource) {
        val cacheAge = System.currentTimeMillis() - viewModel.emailLastFetchTime.value
        val hasCachedData = if (activeSource == "google") viewModel.cachedGmailEmails.value.isNotEmpty()
                            else viewModel.cachedImapEmails.value.isNotEmpty()

        if (hasCachedData && cacheAge < 300_000L) {
            // Use cache — no network call
            if (activeSource == "google") {
                emails = viewModel.cachedGmailEmails.value
                unreadCount = viewModel.cachedGmailUnread.value
            } else {
                imapEmails = viewModel.cachedImapEmails.value
                unreadCount = viewModel.cachedImapUnread.value
            }
        } else if ((activeSource == "google" && accessToken != null) || (activeSource == "personal" && hasImapConfig)) {
            forceRefresh()
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (viewMode != "list") {
                        IconButton(onClick = {
                            viewMode = "list"
                            sendResult = null
                        }) {
                            Icon(Icons.Filled.ArrowBack, "Volver", tint = textPrimary)
                        }
                    }
                },
                title = {
                    when (viewMode) {
                        "detail" -> Text(
                            if (activeSource == "google") selectedEmail?.subject ?: "" else selectedImapEmail?.subject ?: "",
                            fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        "compose" -> Text("Redactar", fontWeight = FontWeight.Bold, color = textPrimary)
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Correo", fontWeight = FontWeight.Bold, color = textPrimary)
                            if (unreadCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(color = Color(0xFFF44336), shape = RoundedCornerShape(12.dp)) {
                                    Text("$unreadCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    when (viewMode) {
                        "list" -> {
                            if (accessToken != null || hasImapConfig) {
                                IconButton(onClick = { forceRefresh() }) {
                                    Icon(Icons.Filled.Refresh, "Actualizar", tint = accent)
                                }
                            }
                        }
                        "compose" -> {
                            if (isSending) {
                                CircularProgressIndicator(color = accent, modifier = Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    enabled = composeTo.isNotBlank() && composeSubject.isNotBlank(),
                                    onClick = {
                                        isSending = true; sendResult = null
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    if (activeSource == "google" && accessToken != null) {
                                                        val result = GoogleGmailService(accessToken).sendEmail(composeTo.trim(), composeSubject, composeBody)
                                                        sendResult = if (result != null) "✅ Enviado" else "❌ Error al enviar"
                                                    } else if (hasImapConfig) {
                                                        val emailService = com.beemovil.email.EmailService(context)
                                                        val config = com.beemovil.email.EmailService.EmailConfig(imapHost, imapPort, smtpHost, smtpPort)
                                                        val ok = emailService.sendEmail(imapEmail, imapPassword, config, composeTo.trim(), composeSubject, composeBody)
                                                        sendResult = if (ok) "✅ Enviado" else "❌ Error al enviar"
                                                    }
                                                } catch (e: Exception) { sendResult = "❌ ${e.message}" }
                                            }
                                            isSending = false
                                            if (sendResult?.startsWith("✅") == true) {
                                                // Clear and go back after short delay
                                                kotlinx.coroutines.delay(1000)
                                                composeTo = ""; composeSubject = ""; composeBody = ""
                                                viewMode = "list"
                                            }
                                        }
                                    }
                                ) { Icon(Icons.Filled.Send, "Enviar", tint = if (composeTo.isNotBlank() && composeSubject.isNotBlank()) accent else textSecondary) }
                            }
                        }
                        else -> {}
                    }
                }
            )
        },
        floatingActionButton = {
            if (viewMode == "list" && (accessToken != null || hasImapConfig)) {
                FloatingActionButton(
                    onClick = {
                        composeTo = ""; composeSubject = ""; composeBody = ""; sendResult = null
                        viewMode = "compose"
                    },
                    containerColor = accent,
                    contentColor = if (isDark) BeeBlack else Color.White
                ) {
                    Icon(Icons.Filled.Edit, "Redactar")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when (viewMode) {
                // ═══════════════════════════════════════
                // DETAIL VIEW
                // ═══════════════════════════════════════
                "detail" -> {
                    val detailFrom: String
                    val detailTo: String
                    val detailSubject: String
                    val detailBody: String
                    val detailDate: String
                    val sdf = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())

                    if (activeSource == "google" && selectedEmail != null) {
                        val e = selectedEmail!!
                        detailFrom = e.from
                        detailTo = e.to
                        detailSubject = e.subject
                        detailBody = e.snippet // Gmail snippet is what we have
                        detailDate = sdf.format(Date(e.date))
                    } else if (selectedImapEmail != null) {
                        val e = selectedImapEmail!!
                        detailFrom = e.from
                        detailTo = e.to
                        detailSubject = e.subject
                        detailBody = e.body
                        detailDate = sdf.format(e.date)
                    } else {
                        viewMode = "list"
                        return@Column
                    }

                    androidx.compose.foundation.rememberScrollState().let { scrollState ->
                        Column(
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 20.dp)
                        ) {
                            // Subject
                            Text(detailSubject, color = textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                            Spacer(modifier = Modifier.height(16.dp))

                            // From/To/Date card
                            Surface(color = cardBg, shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = RoundedCornerShape(50), color = avatarBg, modifier = Modifier.size(40.dp)) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Text(detailFrom.take(1).uppercase(), color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(detailFrom, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (detailTo.isNotBlank()) {
                                                Text("Para: $detailTo", color = textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text(detailDate, color = textSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = dividerColor)
                            Spacer(modifier = Modifier.height(16.dp))

                            // Body
                            Text(detailBody, color = textPrimary, fontSize = 15.sp, lineHeight = 22.sp)

                            Spacer(modifier = Modifier.height(24.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        composeTo = detailFrom.substringAfter("<").substringBefore(">").ifBlank {
                                            detailFrom.trim()
                                        }
                                        composeSubject = "Re: ${detailSubject.removePrefix("Re: ")}"
                                        composeBody = "\n\n--- Original ---\nDe: $detailFrom\nFecha: $detailDate\n\n$detailBody"
                                        sendResult = null
                                        viewMode = "compose"
                                    },
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, accent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Reply, "Reply", tint = accent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Responder", color = accent)
                                }
                                OutlinedButton(
                                    onClick = {
                                        composeTo = ""
                                        composeSubject = "Fwd: ${detailSubject.removePrefix("Fwd: ")}"
                                        composeBody = "\n\n--- Forwarded ---\nDe: $detailFrom\nFecha: $detailDate\n\n$detailBody"
                                        sendResult = null
                                        viewMode = "compose"
                                    },
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, dividerColor),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Forward, "Forward", tint = textSecondary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reenviar", color = textSecondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }

                // ═══════════════════════════════════════
                // COMPOSE VIEW
                // ═══════════════════════════════════════
                "compose" -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
                    ) {
                        if (sendResult != null) {
                            Surface(
                                color = if (sendResult!!.startsWith("✅")) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFC62828).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Text(sendResult!!, modifier = Modifier.padding(12.dp), color = textPrimary, fontSize = 13.sp)
                            }
                        }

                        OutlinedTextField(
                            value = composeTo, onValueChange = { composeTo = it },
                            label = { Text("Para") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent, unfocusedBorderColor = dividerColor,
                                focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                                focusedLabelColor = accent, unfocusedLabelColor = textSecondary, cursorColor = accent
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = composeSubject, onValueChange = { composeSubject = it },
                            label = { Text("Asunto") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent, unfocusedBorderColor = dividerColor,
                                focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                                focusedLabelColor = accent, unfocusedLabelColor = textSecondary, cursorColor = accent
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = composeBody, onValueChange = { composeBody = it },
                            label = { Text("Mensaje") },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent, unfocusedBorderColor = dividerColor,
                                focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                                focusedLabelColor = accent, unfocusedLabelColor = textSecondary, cursorColor = accent
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ═══════════════════════════════════════
                // LIST VIEW (inbox)
                // ═══════════════════════════════════════
                else -> {
            // Account filter chips
            val hasGoogle = accessToken != null
            val hasBoth = hasGoogle && hasImapConfig
            if (hasBoth) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeSource == "google",
                        onClick = { activeSource = "google"; errorMsg = null },
                        label = { Text("Gmail", fontSize = 12.sp) },
                        leadingIcon = { Text("G", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.2f), selectedLabelColor = accent)
                    )
                    FilterChip(
                        selected = activeSource == "personal",
                        onClick = { activeSource = "personal"; errorMsg = null },
                        label = { Text(imapEmail.take(20), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Filled.AlternateEmail, "IMAP", modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.2f), selectedLabelColor = accent)
                    )
                }
            }

            val noSourceConfigured = !hasGoogle && !hasImapConfig
            if (noSourceConfigured) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Filled.Email, contentDescription = "Email", tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(72.dp))
                        Text("Configura tu correo", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Conecta Google Workspace o configura tu email personal (IMAP) en Settings.", color = textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 16.dp))
                        Button(onClick = { viewModel.currentScreen.value = "settings" }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (isDark) BeeBlack else Color.White), shape = RoundedCornerShape(24.dp), modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Filled.Settings, "Config", modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Ir a Settings", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(40.dp)); Spacer(modifier = Modifier.height(12.dp))
                        Text("Cargando correos...", color = textSecondary, fontSize = 14.sp)
                    }
                }
            } else if (errorMsg != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Filled.Warning, "Error", tint = Color(0xFFF44336), modifier = Modifier.size(48.dp)); Spacer(modifier = Modifier.height(12.dp))
                        Text("Error al cargar correos", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMsg ?: "", color = textSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)); Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.currentScreen.value = "settings" }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (isDark) BeeBlack else Color.White)) { Text("Verificar conexión") }
                    }
                }
            } else if (activeSource == "google" && emails.isEmpty() || activeSource == "personal" && imapEmails.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 48.sp); Spacer(modifier = Modifier.height(12.dp))
                        Text("Tu bandeja está vacía", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (activeSource == "personal") {
                // IMAP email list
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(imapEmails) { email ->
                        Surface(modifier = Modifier.fillMaxWidth().clickable {
                            selectedImapEmail = email; viewMode = "detail"
                        }, color = Color.Transparent) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Surface(shape = RoundedCornerShape(50), color = avatarBg, modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(email.from.take(1).uppercase(), color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(email.from.take(25), color = if (!email.isRead) textPrimary else textSecondary, fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                        Text(SimpleDateFormat("dd/MM", Locale.getDefault()).format(email.date), color = textSecondary, fontSize = 11.sp)
                                    }
                                    Text(email.subject.take(50), color = textPrimary, fontSize = 13.sp, maxLines = 1)
                                    Text(email.body.take(60).replace("\n", " "), color = textSecondary, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                    }
                }
            } else {
                // Google email list
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(emails, key = { it.id }) { email ->
                        EmailItemCard(
                            email = email, isDark = isDark, textPrimary = textPrimary, textSecondary = textSecondary,
                            accent = accent, avatarBg = avatarBg, dividerColor = dividerColor,
                            onClick = {
                                selectedEmail = email; viewMode = "detail"
                                if (email.isUnread && accessToken != null) {
                                    scope.launch { withContext(Dispatchers.IO) { try { GoogleGmailService(accessToken).markAsRead(email.id) } catch (_: Exception) {} } }
                                }
                            }
                        )
                    }
                }
            }
                }
            }
        }
    }
}

@Composable
private fun EmailItemCard(
    email: GoogleGmailService.EmailMessage,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    avatarBg: Color,
    dividerColor: Color,
    onClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isToday = remember(email.date) {
        val cal1 = Calendar.getInstance().apply { timeInMillis = email.date }
        val cal2 = Calendar.getInstance()
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (email.isUnread) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4285F4))
                )
            } else {
                Spacer(modifier = Modifier.size(8.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))

            val initial = email.from
                .substringBefore("<")
                .trim()
                .firstOrNull()?.uppercase() ?: "?"
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = avatarBg
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(initial, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    email.from.substringBefore("<").trim().ifBlank { email.from },
                    color = if (email.isUnread) textPrimary else textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (email.isUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    email.subject,
                    color = if (email.isUnread) textPrimary.copy(alpha = 0.9f) else textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (email.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    email.snippet,
                    color = textSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isToday) timeFormat.format(Date(email.date)) else sdf.format(Date(email.date)),
                color = if (email.isUnread) accent else textSecondary,
                fontSize = 11.sp
            )
        }
    }
    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
}
