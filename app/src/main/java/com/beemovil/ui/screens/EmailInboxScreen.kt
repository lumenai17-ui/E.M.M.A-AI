package com.beemovil.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

    var emails by remember { mutableStateOf<List<GoogleGmailService.EmailMessage>>(emptyList()) }
    var unreadCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedEmail by remember { mutableStateOf<GoogleGmailService.EmailMessage?>(null) }

    LaunchedEffect(accessToken) {
        if (accessToken != null) {
            isLoading = true
            errorMsg = null
            withContext(Dispatchers.IO) {
                try {
                    val service = GoogleGmailService(accessToken)
                    emails = service.listInbox(maxResults = 25)
                    unreadCount = service.getUnreadCount()
                } catch (e: Exception) {
                    errorMsg = e.message
                }
            }
            isLoading = false
        }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Correo", fontWeight = FontWeight.Bold, color = textPrimary)
                        if (unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFFF44336),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "$unreadCount",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (accessToken != null) {
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                withContext(Dispatchers.IO) {
                                    try {
                                        val service = GoogleGmailService(accessToken)
                                        emails = service.listInbox(maxResults = 25)
                                        unreadCount = service.getUnreadCount()
                                    } catch (e: Exception) {
                                        errorMsg = e.message
                                    }
                                }
                                isLoading = false
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, "Actualizar", tint = accent)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isSignedIn || accessToken == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Email,
                            contentDescription = "Email",
                            tint = accent.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            "Conecta tu cuenta de Google",
                            color = textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Para ver tu bandeja de correo, ve a Settings → Google Workspace y conecta tu cuenta.",
                            color = textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Button(
                            onClick = { viewModel.currentScreen.value = "settings" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F1F1F)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("G", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ir a Settings", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Cargando correos...", color = textSecondary, fontSize = 14.sp)
                    }
                }
            } else if (errorMsg != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(Icons.Filled.Warning, "Error", tint = Color(0xFFF44336), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Error al cargar correos", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            errorMsg ?: "",
                            color = textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.currentScreen.value = "settings" },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (isDark) BeeBlack else Color.White)
                        ) { Text("Verificar conexión Google") }
                    }
                }
            } else if (emails.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Tu bandeja está vacía", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                if (selectedEmail != null) {
                    AlertDialog(
                        onDismissRequest = { selectedEmail = null },
                        containerColor = cardBg,
                        title = {
                            Text(
                                selectedEmail!!.subject,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        },
                        text = {
                            Column {
                                Text("De: ${selectedEmail!!.from}", color = accent, fontSize = 12.sp)
                                if (selectedEmail!!.to.isNotBlank()) {
                                    Text("Para: ${selectedEmail!!.to}", color = textSecondary, fontSize = 11.sp)
                                }
                                val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                                Text(sdf.format(Date(selectedEmail!!.date)), color = textSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = dividerColor)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    selectedEmail!!.snippet,
                                    color = textPrimary.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { selectedEmail = null }) {
                                Text("Cerrar", color = accent)
                            }
                        }
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(emails, key = { it.id }) { email ->
                        EmailItemCard(
                            email = email,
                            isDark = isDark,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accent = accent,
                            avatarBg = avatarBg,
                            dividerColor = dividerColor,
                            onClick = {
                                selectedEmail = email
                                if (email.isUnread && accessToken != null) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                GoogleGmailService(accessToken).markAsRead(email.id)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                        )
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
