package com.beemovil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationConfigScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Configuración Ops", fontWeight = FontWeight.Bold, color = BeeWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = BeeYellow.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Settings, "WIP", tint = BeeYellow, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Configuración Suspendida",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BeeWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Esta pantalla usaba clases de la memoria antigua que ya han sido purgadas.",
                    fontSize = 14.sp,
                    color = BeeGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack)
                ) {
                    Text("Regresar al Dashboard")
                }
            }
        }
    }
}
