package com.beemovil.ui.components

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.beemovil.ui.theme.*

/**
 * BrowserChatPanel — embedded WebView for navigating LLM-generated URLs.
 *
 * C-05 hardening:
 *  - JavaScript is OFF by default. The user can enable it per-session (toggle).
 *  - URL is validated: only http/https, no `javascript:`, no `file:`, no `content:`.
 *  - Hosts on the private/loopback ranges are rejected (defense against the LLM
 *    being tricked into pointing the user at the home router admin page).
 *  - If the URL fails the safety checks, the panel shows an error instead of loading it.
 */
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
    val rejectionReason = remember(url) { rejectIfUnsafe(url) }
    var jsEnabled by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = handleColor) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
        ) {
            // Top bar: URL + JS toggle.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = url,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (rejectionReason == null) {
                    Switch(
                        checked = jsEnabled,
                        onCheckedChange = { jsEnabled = it }
                    )
                    Text(
                        text = "JS",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (rejectionReason != null) {
                // Refuse to load — show explanation.
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "URL rechazada",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = rejectionReason,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = jsEnabled
                            // Defense in depth: never let JS get a path to native code.
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            webViewClient = WebViewClient()
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        // Keep the JS toggle in sync if the user flips it.
                        if (webView.settings.javaScriptEnabled != jsEnabled) {
                            webView.settings.javaScriptEnabled = jsEnabled
                            webView.reload()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Returns null if the URL is safe to navigate. Otherwise returns a human-readable reason.
 *
 * Rules:
 *  - Scheme must be http or https.
 *  - Host must be present and not a literal IP in the private/loopback ranges.
 *  - No `javascript:`, `file:`, `content:`, `data:` schemes (XSS / file disclosure risk).
 */
private fun rejectIfUnsafe(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return "URL vacía."
    val uri = try { Uri.parse(trimmed) } catch (_: Throwable) { null }
        ?: return "No se pudo parsear la URL."
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        return "Esquema no permitido: $scheme. Solo http/https."
    }
    val host = uri.host?.lowercase() ?: return "URL sin host."
    if (host == "localhost" || host == "ip6-localhost") return "Host bloqueado: localhost."
    if (host.endsWith(".localhost")) return "Host bloqueado: subdominio de localhost."
    // Block IP literals in private/loopback ranges.
    if (isPrivateOrLoopbackIp(host)) {
        return "IP privada/loopback bloqueada: $host."
    }
    return null
}

private fun isPrivateOrLoopbackIp(host: String): Boolean {
    // IPv4
    val ipv4 = host.split(".")
    if (ipv4.size == 4 && ipv4.all { it.toIntOrNull() in 0..255 }) {
        val a = ipv4[0].toInt(); val b = ipv4[1].toInt()
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 127) ||
            (a == 169 && b == 254) ||
            (a == 0)
    }
    // IPv6 loopback / link-local / ULA
    return host == "::1" ||
        host.startsWith("fe80:") ||
        host.startsWith("fc") ||
        host.startsWith("fd")
}
