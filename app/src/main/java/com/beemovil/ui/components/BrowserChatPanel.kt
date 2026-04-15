package com.beemovil.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.beemovil.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserChatPanel(
    url: String,
    onDismiss: () -> Unit
) {
    val isDark = isDarkTheme()
    val sheetBg = if (isDark) Color(0xFF161622) else LightSurface
    val handleColor = if (isDark) Color.Gray else LightBorder

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = handleColor) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl(url)
                    }
                },
                update = { webView ->
                    webView.loadUrl(url)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
