package com.beemovil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
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
fun LiveVisionScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Live Vision (Offline)", fontWeight = FontWeight.Bold, color = BeeWhite) },
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
                    color = Color(0xFF00C853).copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Videocam, "WIP", tint = Color(0xFF00C853), modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Motor de Visión Suspendido",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BeeWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Se desactivó Live Vision temporalmente mientras se instala Koog VLM. La lógica de bucle manual del modelo antiguo fue purgada correctamente.",
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
