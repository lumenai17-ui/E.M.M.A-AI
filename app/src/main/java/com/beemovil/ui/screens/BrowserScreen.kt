package com.beemovil.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.beemovil.skills.BrowserSkill
import com.beemovil.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    browserSkill: BrowserSkill?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var urlInput by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("Browser") }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
    ) {
        // ══════ Top Bar ══════
        TopAppBar(
            title = {
                Column {
                    Text("Browser", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (pageTitle.isNotBlank() && pageTitle != "Browser") {
                        Text(pageTitle, fontSize = 11.sp, color = BeeGray, maxLines = 1)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BeeBlackLight,
                titleContentColor = BeeWhite
            )
        )

        // ══════ URL Bar ══════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BeeBlackLight)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text("URL o buscar...", color = BeeGray, fontSize = 13.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = BeeWhite),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BeeYellow,
                    unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                    cursorColor = BeeYellow,
                    focusedContainerColor = BeeBlack,
                    unfocusedContainerColor = BeeBlack
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val url = urlInput.trim()
                    currentUrl = when {
                        url.startsWith("http") -> url
                        url.contains(".") && !url.contains(" ") -> "https://$url"
                        else -> "https://www.google.com/search?q=${url.replace(" ", "+")}"
                    }
                })
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = {
                val url = urlInput.trim()
                currentUrl = when {
                    url.startsWith("http") -> url
                    url.contains(".") && !url.contains(" ") -> "https://$url"
                    else -> "https://www.google.com/search?q=${url.replace(" ", "+")}"
                }
            }) {
                Icon(Icons.Filled.Search, "Go", tint = BeeYellow)
            }
        }

        // ══════ Loading Bar ══════
        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = BeeYellow,
                trackColor = BeeBlackLight
            )
        }

        // ══════ WebView ══════
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.mediaPlaybackRequiresUserGesture = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            pageTitle = view?.title ?: ""
                            urlInput = url ?: ""
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            if (newProgress >= 100) isLoading = false
                        }
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            pageTitle = title ?: ""
                        }
                    }

                    // Register with BrowserSkill
                    browserSkill?.initWebView(this)
                    browserSkill?.onNavigate = { url -> currentUrl = url }

                    loadUrl(currentUrl)
                }
            },
            update = { webView ->
                val current = webView.url ?: ""
                if (currentUrl != current && currentUrl.isNotBlank()) {
                    webView.loadUrl(currentUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
        )
    }
}
