package com.beemovil.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.memory.NotificationLogDB
import com.beemovil.service.BeeNotificationService

// Premium palette
private val Bg = Color(0xFF08080A)
private val CardBg = Color(0xFF111118)
private val Gold = Color(0xFFF5A623)
private val Txt = Color(0xFFF2F2F7)
private val TxtSub = Color(0xFF8E8E9A)
private val TxtMuted = Color(0xFF555566)
private val Green = Color(0xFF34C759)
private val Red = Color(0xFFFF3B30)
private val Orange = Color(0xFFFF9500)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationConfigScreen(
    notifDB: NotificationLogDB,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isEnabled = remember { BeeNotificationService.isServiceEnabled(context) }
    var captureEnabled by remember { mutableStateOf(BeeNotificationService.isCaptureEnabled(context)) }
    var excludedApps by remember { mutableStateOf(BeeNotificationService.getExcludedApps(context)) }
    val trackedApps = remember { notifDB.getTrackedApps() }
    val appStats = remember { notifDB.getAppStats(50) }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // Top bar
        Surface(color = CardBg) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Txt)
                }
                Text("Configuracion de captura", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Txt)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // System permission status
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEnabled) Green.copy(alpha = 0.08f) else Orange.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isEnabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            "Status",
                            tint = if (isEnabled) Green else Orange,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isEnabled) "Acceso activado" else "Acceso desactivado",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (isEnabled) Green else Orange
                            )
                            Text(
                                if (isEnabled) "Bee-Movil puede capturar notificaciones"
                                else "Activa el permiso en configuracion del sistema",
                                fontSize = 13.sp, color = TxtSub
                            )
                        }
                        if (!isEnabled) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Gold, contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Activar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Global toggle
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.NotificationsActive, "Capture", tint = Gold, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Captura activa", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Txt)
                            Text("Loguear notificaciones automaticamente", fontSize = 12.sp, color = TxtSub)
                        }
                        Switch(
                            checked = captureEnabled,
                            onCheckedChange = {
                                captureEnabled = it
                                BeeNotificationService.setCaptureEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Gold,
                                uncheckedTrackColor = Color(0xFF333344)
                            )
                        )
                    }
                }
            }

            // Privacy note
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Shield, "Privacy", tint = Green, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Todos los datos se almacenan localmente en tu dispositivo. Nada sale del telefono.",
                            fontSize = 12.sp, color = Green.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // App list header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Apps monitoreadas", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Txt)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${trackedApps.size - excludedApps.size} activas", fontSize = 13.sp, color = TxtSub)
                }
            }

            // Per-app toggles
            if (appStats.isNotEmpty()) {
                items(appStats) { stat ->
                    val isExcluded = stat.packageName in excludedApps
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // App color dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isExcluded) TxtMuted else Gold)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stat.appName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Txt)
                                Text("${stat.count} notificaciones", fontSize = 12.sp, color = TxtSub)
                            }
                            Switch(
                                checked = !isExcluded,
                                onCheckedChange = { enabled ->
                                    val newSet = excludedApps.toMutableSet()
                                    if (enabled) newSet.remove(stat.packageName)
                                    else newSet.add(stat.packageName)
                                    excludedApps = newSet
                                    BeeNotificationService.setExcludedApps(context, newSet)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = Gold,
                                    uncheckedTrackColor = Color(0xFF333344)
                                )
                            )
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.NotificationsOff, "Empty", tint = TxtMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Sin apps detectadas aun", fontSize = 14.sp, color = TxtSub)
                        Text("Las apps apareceran aqui conforme lleguen notificaciones", fontSize = 12.sp, color = TxtMuted)
                    }
                }
            }

            // Clear data
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.DeleteForever, "Clear", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Borrar todo el historial", fontWeight = FontWeight.SemiBold)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = CardBg,
            title = { Text("Borrar historial", color = Txt, fontWeight = FontWeight.Bold) },
            text = { Text("Esto eliminara todas las notificaciones capturadas. Esta accion no se puede deshacer.", color = TxtSub) },
            confirmButton = {
                Button(
                    onClick = { notifDB.clearAll(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)
                ) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar", color = TxtSub) }
            }
        )
    }
}
