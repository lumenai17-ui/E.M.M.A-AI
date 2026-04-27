package com.beemovil.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.beemovil.ui.theme.*

/**
 * Permission types used across BeeMovil screens
 */
enum class BeePermission(
    val androidPermission: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val reason: String,
    val color: Color
) {
    MICROPHONE(
        androidPermission = Manifest.permission.RECORD_AUDIO,
        icon = Icons.Filled.Mic,
        title = "Microfono",
        description = "Necesario para modo voz, preguntas por voz y transcripcion en tiempo real.",
        reason = "BeeMovil usa el microfono para convertir tu voz en texto y permite conversaciones en modo manos libres.",
        color = Color(0xFF0A84FF)
    ),
    CAMERA(
        androidPermission = Manifest.permission.CAMERA,
        icon = Icons.Filled.CameraAlt,
        title = "Camara",
        description = "Necesario para Vision Pro, analisis de imagenes, dashcam y escaneo de QR.",
        reason = "La camara permite al AI analizar tu entorno, tomar fotos inteligentes y modo dashcam.",
        color = Color(0xFFBF5AF2)
    ),
    LOCATION(
        androidPermission = Manifest.permission.ACCESS_FINE_LOCATION,
        icon = Icons.Filled.LocationOn,
        title = "Ubicacion",
        description = "Necesario para GPS overlay, guia turista, navegacion y contexto local.",
        reason = "Tu ubicacion permite al AI dar direcciones, info de la zona y navegar como guia turistico.",
        color = Color(0xFF34C759)
    ),
    NOTIFICATIONS(
        androidPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS else Manifest.permission.ACCESS_NOTIFICATION_POLICY,
        icon = Icons.Filled.Notifications,
        title = "Notificaciones",
        description = "Necesario para alertas del agente, recordatorios y resumen inteligente.",
        reason = "Permite que el agente te notifique sobre tareas, eventos y alertas importantes.",
        color = Color(0xFFFF9500)
    ),
    STORAGE(
        androidPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE,
        icon = Icons.Filled.Folder,
        title = "Archivos (Básico)",
        description = "Necesario para adjuntar fotos y documentos.",
        reason = "Permite al agente leer y adjuntar medios al chat.",
        color = Color(0xFF5AC8FA)
    ),
    STORAGE_ALL_FILES(
        androidPermission = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        icon = Icons.Filled.FolderSpecial,
        title = "Control Total de Archivos",
        description = "Necesario para explorar directorios completos, ver tamaños de carpetas y buscar archivos en todo el dispositivo.",
        reason = "Debido a restricciones de Android 11+, E.M.M.A. necesita el permiso 'All Files Access' para actuar como tu gestor de archivos personal.",
        color = Color(0xFF007AFF)
    ),
    CONTACTS(
        androidPermission = Manifest.permission.READ_CONTACTS,
        icon = Icons.Filled.Contacts,
        title = "Contactos",
        description = "Necesario para que Emma pueda buscar números en tu agenda y enviar mensajes.",
        reason = "Permite que el agente encuentre automáticamente a tus contactos al usar WhatsApp o Email.",
        color = Color(0xFFFF2D55)
    ),
    CALL_LOGS(
        androidPermission = Manifest.permission.READ_CALL_LOG,
        icon = Icons.Filled.RecentActors,
        title = "Registro de Llamadas",
        description = "Necesario para revisar llamadas perdidas y recientes.",
        reason = "Permite a E.M.M.A. informarte quién te ha llamado mientras estabas ocupado.",
        color = Color(0xFF5856D6)
    ),
    CALENDAR(
        androidPermission = Manifest.permission.READ_CALENDAR,
        icon = Icons.Filled.CalendarMonth,
        title = "Calendario",
        description = "Necesario para agendar eventos y revisar tu disponibilidad.",
        reason = "Permite que el agente lea y escriba en tu calendario de Android.",
        color = Color(0xFF00C7BE)
    ),
    SYSTEM_SETTINGS(
        androidPermission = Manifest.permission.WRITE_SETTINGS,
        icon = Icons.Filled.SettingsSystemDaydream,
        title = "Ajustes del Sistema",
        description = "Necesario para controlar el brillo, volumen y conexiones de red directamente.",
        reason = "Otorga a E.M.M.A. la capacidad de actuar físicamente sobre los ajustes de tu teléfono sin que tengas que abrir menús.",
        color = Color(0xFFFF9500)
    ),
    EXACT_ALARMS(
        androidPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.SCHEDULE_EXACT_ALARM else "android.permission.SET_ALARM",
        icon = Icons.Filled.AccessAlarms,
        title = "Alarmas Precisas",
        description = "Necesario para ejecutar cronómetros y recordatorios en el milisegundo exacto.",
        reason = "Desde Android 12, Google restringe alarmas exactas para ahorrar batería. E.M.M.A. necesita esta excepción para no llegar tarde a tus recordatorios.",
        color = Color(0xFFFF3B30)
    ),
    DISPLAY_OVER_APPS(
        androidPermission = Manifest.permission.SYSTEM_ALERT_WINDOW,
        icon = Icons.Filled.PictureInPicture,
        title = "Aparecer Encima",
        description = "Necesario para crear la Burbuja Flotante de E.M.M.A.",
        reason = "Permite que E.M.M.A. se muestre como un asistente flotante sobre otras aplicaciones como Chrome o YouTube.",
        color = Color(0xFF00C7BE)
    )
}

/**
 * Check if a permission is granted
 */
fun isPermissionGranted(context: android.content.Context, permission: BeePermission): Boolean {
    return ContextCompat.checkSelfPermission(
        context, permission.androidPermission
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * Premium permission request dialog.
 * Theme-aware: uses dark bg on dark theme, light bg on light theme.
 */
@Composable
fun PermissionDialog(
    permission: BeePermission,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isDarkTheme()
    val dialogBg = if (isDark) Color(0xFF111118) else LightSurface
    val txt = if (isDark) Color(0xFFF2F2F7) else TextDark
    val txtSub = if (isDark) Color(0xFF8E8E9A) else TextGrayDark
    val txtMuted = if (isDark) Color(0xFF555566) else TextGrayDarker

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted() else onDenied()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = dialogBg,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(permission.color.copy(alpha = 0.3f), Color.Transparent)
                            ),
                            CircleShape
                        )
                ) {
                    Surface(
                        color = permission.color.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                permission.icon,
                                permission.title,
                                tint = permission.color,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Text(
                    "Permiso: ${permission.title}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = txt,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Text(
                    permission.description,
                    fontSize = 14.sp,
                    color = txtSub,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Reason card
                Surface(
                    color = permission.color.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            "Info",
                            tint = permission.color,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            permission.reason,
                            fontSize = 12.sp,
                            color = txtSub,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Deny
                    OutlinedButton(
                        onClick = {
                            onDenied()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = txtMuted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, txtMuted.copy(alpha = 0.3f))
                    ) {
                        Text("Denegar", fontWeight = FontWeight.Medium)
                    }

                    // Approve
                    Button(
                        onClick = {
                            if (permission == BeePermission.STORAGE_ALL_FILES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                                onGranted()
                            } else if (permission == BeePermission.SYSTEM_SETTINGS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                                onGranted()
                            } else if (permission == BeePermission.EXACT_ALARMS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                                onGranted()
                            } else if (permission == BeePermission.DISPLAY_OVER_APPS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                                onGranted()
                            } else {
                                launcher.launch(permission.androidPermission)
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = permission.color,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Aprobar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Permission gate — shows dialog if permission not granted, then executes action.
 * Use: val gate = rememberPermissionGate(BeePermission.CAMERA)
 *      gate.checkAndRun { /* camera code */ }
 */
@Composable
fun rememberPermissionGate(permission: BeePermission): PermissionGateState {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isGranted by remember {
        mutableStateOf(isPermissionGranted(context, permission))
    }

    if (showDialog && !isGranted) {
        PermissionDialog(
            permission = permission,
            onGranted = {
                isGranted = true
                showDialog = false
                pendingAction?.invoke()
                pendingAction = null
            },
            onDenied = {
                showDialog = false
                pendingAction = null
            },
            onDismiss = { showDialog = false }
        )
    }

    return remember(isGranted) {
        PermissionGateState(
            isGranted = isGranted,
            checkAndRun = { action ->
                if (isGranted) {
                    action()
                } else {
                    pendingAction = action
                    showDialog = true
                }
            }
        )
    }
}

data class PermissionGateState(
    val isGranted: Boolean,
    val checkAndRun: (() -> Unit) -> Unit
)
