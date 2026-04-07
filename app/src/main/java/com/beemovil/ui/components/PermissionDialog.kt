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

// ── Palette
private val DialogBg = Color(0xFF111118)
private val Gold = Color(0xFFF5A623)
private val Txt = Color(0xFFF2F2F7)
private val TxtSub = Color(0xFF8E8E9A)
private val TxtMuted = Color(0xFF555566)
private val Red = Color(0xFFFF3B30)

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
        title = "Archivos",
        description = "Necesario para adjuntar fotos, PDFs, documentos y guardar archivos generados.",
        reason = "Permite al agente leer y guardar archivos, adjuntar documentos al chat y generar reportes.",
        color = Color(0xFF5AC8FA)
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
 * Shows a beautiful dialog explaining why the permission is needed,
 * with Approve/Deny buttons.
 */
@Composable
fun PermissionDialog(
    permission: BeePermission,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted() else onDenied()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DialogBg,
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
                    color = Txt,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Text(
                    permission.description,
                    fontSize = 14.sp,
                    color = TxtSub,
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
                            color = TxtSub,
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtMuted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TxtMuted.copy(alpha = 0.3f))
                    ) {
                        Text("Denegar", fontWeight = FontWeight.Medium)
                    }

                    // Approve
                    Button(
                        onClick = {
                            launcher.launch(permission.androidPermission)
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
