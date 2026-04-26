package com.beemovil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beemovil.plugins.SecurityGate

/**
 * ConfirmationDialog — C-03 fix
 *
 * Shows a confirmation prompt for SecurityGate YELLOW and RED operations.
 * YELLOW: amber accent, dismissable by tapping outside.
 * RED:    red accent, NOT dismissable by tapping outside (must tap a button).
 */
@Composable
fun ConfirmationDialog(
    operation: SecurityGate.SecureOperation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val isRed = operation.level == SecurityGate.Level.RED
    val accent = if (isRed) Color(0xFFE53935) else Color(0xFFFFA000)
    val title = if (isRed) "Acción destructiva" else "Confirmar acción"
    val confirmLabel = if (isRed) "Sí, ejecutar" else "Confirmar"

    Dialog(
        onDismissRequest = { if (!isRed) onCancel() }
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = accent
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = operation.description.ifBlank { operation.operation },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Plugin: ${operation.pluginId} · ${operation.operation}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text(confirmLabel, color = Color.White)
                    }
                }
            }
        }
    }
}
