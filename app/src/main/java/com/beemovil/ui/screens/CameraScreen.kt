package com.beemovil.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.beemovil.llm.*
import com.beemovil.ui.ChatViewModel
import com.beemovil.vision.BarcodeScanner
import com.beemovil.vision.GpsModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * CameraScreen — E.M.M.A. Vision Phase V1: Los Ojos
 *
 * Static image analysis with 7 quick prompts and contextual GPS enrichment.
 * Supports: take photo (CameraX), pick from gallery, auto-select best vision provider.
 * Theme-aware: uses MaterialTheme.colorScheme for Light/Dark.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CameraScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("beemovil", 0) }
    val colorScheme = MaterialTheme.colorScheme

    // ── State ──
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageBase64 by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var selectedVisionModel by remember {
        mutableStateOf(prefs.getString("vision_model", "") ?: "")
    }
    var showModelPicker by remember { mutableStateOf(false) }

    // ── GPS for context enrichment ──
    val gpsModule = remember { GpsModule(context) }
    var gpsAddress by remember { mutableStateOf("") }
    var gpsCoords by remember { mutableStateOf("") }
    var gpsLat by remember { mutableStateOf(0.0) }
    var gpsLng by remember { mutableStateOf(0.0) }

    // ── Ambient data (v7.2 — zero-cost APIs) ──
    var ambientWeather by remember { mutableStateOf("") }
    var ambientHoliday by remember { mutableStateOf("") }
    var ambientCurrency by remember { mutableStateOf("") }
    var barcodeInfo by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (gpsModule.hasPermission) {
            gpsModule.onLocationUpdate = { data ->
                gpsAddress = data.address
                gpsCoords = data.coordsShort
                gpsLat = data.latitude
                gpsLng = data.longitude
            }
            gpsModule.start()
        }
    }
    DisposableEffect(Unit) { onDispose { gpsModule.stop() } }

    // ── Fetch ambient data once on screen load ──
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val http = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                // Currency
                try {
                    val req = okhttp3.Request.Builder()
                        .url("https://api.frankfurter.app/latest?from=USD&to=MXN,EUR,COP")
                        .header("User-Agent", "E.M.M.A. AI/7.2").build()
                    val resp = http.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val json = org.json.JSONObject(resp.body?.string() ?: "")
                        val rates = json.optJSONObject("rates")
                        if (rates != null) {
                            val parts = mutableListOf<String>()
                            val iter = rates.keys()
                            while (iter.hasNext()) { val c = iter.next(); parts.add("$c=${"%,.2f".format(rates.optDouble(c))}") }
                            ambientCurrency = "USD→${parts.joinToString(", ")}"
                        }
                    }
                    resp.close()
                } catch (_: Exception) {}
                // Holiday
                try {
                    val country = java.util.Locale.getDefault().country.ifBlank { "US" }
                    val today = java.time.LocalDate.now()
                    val tomorrow = today.plusDays(1)
                    val req = okhttp3.Request.Builder()
                        .url("https://date.nager.at/api/v3/publicholidays/${today.year}/$country")
                        .header("User-Agent", "E.M.M.A. AI/7.2").build()
                    val resp = http.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val holidays = org.json.JSONArray(resp.body?.string() ?: "[]")
                        for (i in 0 until holidays.length()) {
                            val h = holidays.getJSONObject(i)
                            val d = java.time.LocalDate.parse(h.optString("date"))
                            if (d.isEqual(today)) { ambientHoliday = "Hoy es feriado: ${h.optString("localName")}"; break }
                            if (d.isEqual(tomorrow)) { ambientHoliday = "Mañana es feriado: ${h.optString("localName")}" }
                        }
                    }
                    resp.close()
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    // ── Fetch weather when GPS arrives ──
    LaunchedEffect(gpsLat, gpsLng) {
        if (gpsLat != 0.0 && gpsLng != 0.0 && ambientWeather.isBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val http = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()
                    val req = okhttp3.Request.Builder()
                        .url("https://api.open-meteo.com/v1/forecast?latitude=$gpsLat&longitude=$gpsLng&current=temperature_2m,weather_code,wind_speed_10m&timezone=auto")
                        .header("User-Agent", "E.M.M.A. AI/7.2").build()
                    val resp = http.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val json = org.json.JSONObject(resp.body?.string() ?: "")
                        val cur = json.optJSONObject("current")
                        if (cur != null) {
                            val temp = cur.optDouble("temperature_2m", 0.0)
                            val code = cur.optInt("weather_code", 0)
                            val wind = cur.optDouble("wind_speed_10m", 0.0)
                            val cond = when (code) {
                                0 -> "despejado"; 1 -> "mayormente despejado"; 2 -> "parcialmente nublado"
                                3 -> "nublado"; in 45..48 -> "niebla"; in 51..57 -> "llovizna"
                                in 61..67 -> "lluvia"; in 71..77 -> "nieve"; in 80..82 -> "chubascos"
                                95, 96, 99 -> "tormenta"; else -> "variable"
                            }
                            ambientWeather = "${temp.toInt()}°C, $cond, viento ${wind.toInt()}km/h"
                        }
                    }
                    resp.close()
                } catch (_: Exception) {}
            }
        }
    }

    // ── Auto-select best vision model ──
    LaunchedEffect(Unit) {
        if (selectedVisionModel.isBlank()) {
            selectedVisionModel = autoSelectVisionModel(context, viewModel)
            prefs.edit().putString("vision_model", selectedVisionModel).apply()
        }
    }

    // ── Camera capture ──
    val photoFile = remember { File(context.cacheDir, "emma_camera_${System.currentTimeMillis()}.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile.exists()) {
            processImageFile(photoFile)?.let { (bmp, b64) ->
                capturedBitmap = bmp; imageBase64 = b64; analysisResult = ""
            } ?: Toast.makeText(context, "Error procesando imagen", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Gallery picker ──
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processImageUri(context, it)?.let { (bmp, b64) ->
                capturedBitmap = bmp; imageBase64 = b64; analysisResult = ""
            } ?: Toast.makeText(context, "Error procesando imagen", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Quick prompts (8) — Prompt DNA v7.2 ──
    val quickPrompts = listOf(
        Triple(Icons.Filled.Description, "Describir", """
            Analiza esta imagen con ojo experto. Identifica:
            1. SUJETO PRINCIPAL: Qué es exactamente (nombre específico, no genérico)
            2. CONTEXTO: Dónde parece estar, época, estilo, ambiente
            3. DETALLES CLAVE: Marca, modelo, material, estado, colores dominantes
            4. DATOS VERIFICABLES: Si reconoces el objeto/lugar, comparte 1 dato real
            5. Si ves texto en otro idioma, tradúcelo automáticamente
            Formato: Respuesta directa y específica, máximo 4 oraciones. NO digas "veo una imagen de".
        """.trimIndent()),
        Triple(Icons.Filled.TextFields, "OCR", """
            Extrae TODO el texto visible en esta imagen con precisión de OCR profesional.
            1. IDIOMA: Detecta el idioma del texto. Si no es español, tradúcelo debajo
            2. ESTRUCTURA: Mantén el formato original (listas, columnas, párrafos)
            3. TIPO DE DOCUMENTO: Identifica qué tipo de documento es (carta, menú, cartel, receta, código)
            4. DATOS CLAVE: Resalta fechas, montos, nombres, direcciones, teléfonos
            5. Si hay texto parcialmente legible, indica [ilegible] en esa parte
            Formato: Primero el texto extraído literal, luego un resumen de 1 línea.
        """.trimIndent()),
        Triple(Icons.Filled.Receipt, "Factura", """
            Analiza esta factura, recibo o ticket con precisión contable.
            EXTRAE con formato estructurado:
            📋 EMISOR: Nombre del negocio, RUC/NIT si visible
            📅 FECHA: Fecha y hora de la transacción
            💰 MONTO: Subtotal, impuestos, total (identifica la moneda)
            🛒 ITEMS: Lista de productos/servicios con precio individual
            💳 MÉTODO DE PAGO: Efectivo, tarjeta, transferencia
            Si la moneda NO es la local del usuario, convierte al tipo de cambio actual.
            Si detectas errores de cálculo en la factura, señálalos.
        """.trimIndent()),
        Triple(Icons.Filled.BarChart, "Datos", """
            Analiza los datos, tablas o gráficos de esta imagen como un analista de datos.
            1. TIPO: Identifica (tabla, gráfico de barras, líneas, pie chart, dashboard, infografía)
            2. DATOS: Extrae los valores numéricos clave con precisión
            3. TENDENCIA: Identifica la tendencia principal (subida, bajada, estable, pico)
            4. ANOMALÍAS: Señala valores atípicos o inconsistencias
            5. CONCLUSIÓN: Resume el insight principal en 1 oración
            Si hay cifras monetarias, indica la moneda. Si hay porcentajes, valida que sumen correctamente.
        """.trimIndent()),
        Triple(Icons.Filled.Sell, "Producto", """
            Identifica este producto con precisión comercial:
            📦 PRODUCTO: Nombre exacto, marca, modelo, línea, generación/versión
            📏 PRESENTACIÓN: Tamaño, peso, cantidad, color
            💰 PRECIO: Si visible, repórtalo exacto con moneda
            ⭐ VALORACIÓN: Si conoces el producto, rating general del mercado
            🔄 ALTERNATIVAS: Sugiere 1-2 alternativas comparables (mejor precio o calidad)
            🏷️ Si es alimento: Nutri-Score estimado (A=excelente a E=evitar)
            🔧 Si es electrónica: Especificaciones clave, generación, ¿vale la pena en 2026?
            Sé el aliado del comprador: datos reales, no marketing.
        """.trimIndent()),
        Triple(Icons.Filled.Park, "Natural", """
            Identifica este elemento natural con precisión de biólogo:
            🌿 IDENTIFICACIÓN: Nombre común + nombre científico (Género especie)
            📍 ORIGEN: Región nativa, hábitat típico
            🔬 CARACTERÍSTICAS: Rasgos distintivos que confirman la identificación
            ⚠️ SEGURIDAD: ¿Es comestible, venenoso, alérgeno, o peligroso?
            💊 USOS: Usos medicinales, culinarios o industriales conocidos
            🌍 CONSERVACIÓN: Estado de conservación si aplica (peligro, vulnerable, etc.)
            Si no estás seguro de la especie exacta, da las 2-3 opciones más probables.
        """.trimIndent()),
        Triple(Icons.Filled.Place, "¿Qué lugar?", buildPlacePrompt(gpsAddress, gpsCoords)),
        Triple(Icons.Filled.QrCodeScanner, "Barcode", """
            Analiza los códigos de barras o QR visibles en esta imagen.
            1. Lee el código numérico/texto del barcode o QR
            2. Si es un producto: identifica nombre, marca, precio estimado
            3. Si es un QR con URL: indica a dónde lleva
            4. Si es un QR de contacto/WiFi: extrae los datos
            5. Si conoces el producto por el código, incluye reviews y alternativas
            Formato: 📊 [Código] → [Información del producto]
        """.trimIndent())
    )

    // ── Analysis function (v7.2: ambient context + barcode pre-scan) ──
    fun analyzeImage(question: String) {
        if (imageBase64.isBlank()) return
        isAnalyzing = true; analysisResult = ""; barcodeInfo = ""

        scope.launch {
            try {
                // Pre-scan for barcodes (async, before LLM call)
                var bcContext = ""
                capturedBitmap?.let { bmp ->
                    try {
                        val scans = withContext(Dispatchers.IO) { BarcodeScanner.scan(bmp) }
                        if (scans.isNotEmpty()) {
                            bcContext = scans.joinToString("\n") { BarcodeScanner.buildProductContext(it) }
                            barcodeInfo = scans.joinToString(", ") { "${it.format}: ${it.rawValue}" }
                        }
                    } catch (_: Exception) {}
                }

                // Build enriched prompt with all ambient data
                val enrichedPrompt = enrichWithContext(
                    question = question,
                    address = gpsAddress, coords = gpsCoords,
                    weather = ambientWeather,
                    holiday = ambientHoliday,
                    currency = ambientCurrency,
                    barcodeContext = bcContext
                )

                withContext(Dispatchers.IO) {
                    val modelEntry = ModelRegistry.findModel(selectedVisionModel)
                    val providerType = modelEntry?.provider ?: viewModel.currentProvider.value
                    val apiKey = getApiKeyForProvider(context, providerType)

                    if (apiKey.isBlank() && providerType != "local") {
                        analysisResult = "Configura tu API key de $providerType en Settings"
                        isAnalyzing = false; return@withContext
                    }

                    val provider = LlmFactory.createProvider(providerType, apiKey, selectedVisionModel)
                    val messages = listOf(
                        ChatMessage(role = "user", content = enrichedPrompt, images = listOf(imageBase64))
                    )
                    val response = provider.complete(messages, emptyList())
                    analysisResult = response.text ?: "Sin respuesta del modelo"
                }
            } catch (e: Exception) {
                analysisResult = "Error: ${e.message?.take(150)}"
            }
            isAnalyzing = false
        }
    }

    // ── UI ──
    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        // Header
        TopAppBar(
            title = {
                Column {
                    Text("Visión AI", fontWeight = FontWeight.Bold)
                    val modelName = ModelRegistry.findModel(selectedVisionModel)?.name ?: selectedVisionModel
                    Text(modelName, fontSize = 11.sp, color = colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = colorScheme.primary)
                }
            },
            actions = {
                IconButton(onClick = { showModelPicker = !showModelPicker }) {
                    Icon(Icons.Filled.Tune, "Model", tint = colorScheme.primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surface,
                titleContentColor = colorScheme.onSurface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Model Picker (collapsible) ──
            if (showModelPicker) {
                VisionModelPicker(
                    selected = selectedVisionModel,
                    onSelect = { id ->
                        selectedVisionModel = id
                        prefs.edit().putString("vision_model", id).apply()
                        showModelPicker = false
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Context indicators (v7.2 — ambient data strip) ──
            if (gpsAddress.isNotBlank() || ambientWeather.isNotBlank() || ambientCurrency.isNotBlank()) {
                Surface(
                    color = colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (gpsAddress.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MyLocation, "GPS", tint = colorScheme.secondary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(gpsAddress, fontSize = 11.sp, color = colorScheme.onSecondaryContainer,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val ambientParts = mutableListOf<String>()
                        if (ambientWeather.isNotBlank()) ambientParts.add("🌡️ $ambientWeather")
                        if (ambientHoliday.isNotBlank()) ambientParts.add("🎉 $ambientHoliday")
                        if (ambientCurrency.isNotBlank()) ambientParts.add("💱 $ambientCurrency")
                        if (ambientParts.isNotEmpty()) {
                            if (gpsAddress.isNotBlank()) Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                ambientParts.joinToString("  •  "),
                                fontSize = 10.sp, color = colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (barcodeInfo.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("📊 Barcode: $barcodeInfo", fontSize = 10.sp,
                                color = colorScheme.primary, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Image preview or empty state ──
            if (capturedBitmap != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box {
                        capturedBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Captured",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        IconButton(
                            onClick = { capturedBitmap = null; imageBase64 = ""; analysisResult = "" },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                .background(colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, "Clear", tint = colorScheme.onSurface)
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.CameraAlt, "Camera", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Captura o selecciona una imagen", fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = colorScheme.onSurface)
                        Text("E.M.M.A. analizará lo que vea", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Capture buttons ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { cameraLauncher.launch(photoUri) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, "Camera", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Foto", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, "Gallery", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Galería", fontSize = 13.sp)
                }
                Button(
                    onClick = { viewModel.currentScreen.value = "live_vision" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.FiberManualRecord, "Live", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Live", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // ── Quick prompts (only when image loaded) ──
            if (capturedBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("ANÁLISIS RÁPIDO", fontSize = 11.sp, color = colorScheme.primary,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPrompts.forEach { (icon, label, p) ->
                        AssistChip(
                            onClick = { prompt = p; analyzeImage(p) },
                            label = { Text(label, fontSize = 12.sp) },
                            leadingIcon = { Icon(icon, label, modifier = Modifier.size(16.dp)) },
                            enabled = !isAnalyzing,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Custom prompt
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Pregunta personalizada") },
                    placeholder = { Text("¿Qué quieres saber de esta imagen?") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface,
                        cursorColor = colorScheme.primary,
                        focusedLabelColor = colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (prompt.isBlank()) {
                            Toast.makeText(context, "Escribe una pregunta", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        analyzeImage(prompt)
                    },
                    enabled = prompt.isNotBlank() && !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizando...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, "Analyze", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analizar imagen", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Analysis loading indicator ──
            if (isAnalyzing) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant
                )
            }

            // ── Result ──
            if (analysisResult.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, "AI", tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RESULTADO", fontSize = 11.sp, color = colorScheme.primary,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            val modelName = ModelRegistry.findModel(selectedVisionModel)?.name ?: selectedVisionModel
                            Text("via $modelName", fontSize = 9.sp, color = colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(analysisResult, fontSize = 14.sp, color = colorScheme.onSurface, lineHeight = 22.sp)

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.currentScreen.value = "chat"
                                    viewModel.sendMessage("Resultado de análisis de imagen: $analysisResult\n\n¿Puedes elaborar más?")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.primary),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Al Chat", fontSize = 12.sp) }

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("result", analysisResult))
                                    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurfaceVariant),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Copiar", fontSize = 12.sp) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ═══════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════

/** Auto-select the best available vision model — BUG-4 FIX: Uses dynamic cache */
internal fun autoSelectVisionModel(context: android.content.Context, viewModel: ChatViewModel): String {
    val currentProvider = viewModel.currentProvider.value
    val currentModel = viewModel.currentModel.value

    // 1. If current model has vision capability, use it directly
    val cachedVision = DynamicModelFetcher.getCachedVisionModels(context, currentProvider)
    if (cachedVision.any { it.id == currentModel }) return currentModel

    // 2. First vision model from the user's active provider (from dynamic cache)
    val bestDynamic = cachedVision.firstOrNull()
    if (bestDynamic != null) return bestDynamic.id

    // 3. Fallback to static registry for the provider
    val staticVision = ModelRegistry.getModelsForProvider(currentProvider)
        .firstOrNull { it.hasVision }
    if (staticVision != null) return staticVision.id

    // 4. Local model with vision (downloaded)?
    val localVision = ModelRegistry.LOCAL.firstOrNull {
        it.hasVision && com.beemovil.llm.local.LocalModelManager.isModelDownloaded(it.id)
    }
    if (localVision != null) return localVision.id

    // 5. Last resort: use current model as-is
    return currentModel
}

/** Get API key for a provider */
internal fun getApiKeyForProvider(context: android.content.Context, provider: String): String {
    val secPrefs = com.beemovil.security.SecurePrefs.get(context)
    return when (provider) {
        "openrouter" -> secPrefs.getString("openrouter_api_key", "") ?: ""
        "ollama" -> secPrefs.getString("ollama_api_key", "") ?: ""
        "local" -> ""
        else -> ""
    }
}

/** v7.2: Enrich a prompt with full ambient context (GPS + Weather + Holiday + Currency) */
private fun enrichWithContext(
    question: String, address: String, coords: String,
    weather: String = "", holiday: String = "", currency: String = "",
    barcodeContext: String = ""
): String {
    val ctx = buildString {
        appendLine("[CONTEXTO AMBIENTAL — E.M.M.A. v7.2]")
        if (address.isNotBlank() || coords.isNotBlank()) {
            append("📍 Ubicación: ")
            if (address.isNotBlank()) append(address)
            if (coords.isNotBlank()) append(" ($coords)")
            appendLine()
        }
        if (weather.isNotBlank()) appendLine("🌡️ Clima: $weather")
        val now = java.util.Calendar.getInstance()
        val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale("es")).format(now.time)
        val dateFmt = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("es")).format(now.time)
        appendLine("📅 Hoy: $dayName $dateFmt")
        if (holiday.isNotBlank()) appendLine("🎉 $holiday")
        if (currency.isNotBlank()) appendLine("💱 Cambio: $currency")
        if (barcodeContext.isNotBlank()) {
            appendLine()
            append(barcodeContext)
        }
        appendLine("[Usa estos datos para enriquecer tu análisis cuando sea relevante.]")
    }
    return "$ctx\n\n$question"
}

/** Build the "What place is this?" prompt with GPS data — v7.2 enhanced */
private fun buildPlacePrompt(address: String, coords: String): String {
    val base = """
        Identifica este lugar, monumento, edificio o punto de interés:
        🏛️ NOMBRE: Nombre oficial y nombre popular si difiere
        📅 HISTORIA: Año de construcción/fundación + dato histórico clave
        🎨 ESTILO: Estilo arquitectónico o artístico
        🕐 HORARIO: Horario de visita y precio de entrada si es atracción
        📍 ZONA: Qué más hay interesante cerca (a pie)
        💡 DATO CURIOSO: Algo que la mayoría no sabe sobre este lugar
        Si no reconoces el lugar exacto, usa el GPS para investigar qué hay en esa ubicación.
    """.trimIndent()
    if (address.isBlank() && coords.isBlank()) return base
    return "$base\n[GPS: ${address.ifBlank { coords }}. Usa esta ubicación para precisar la identificación.]"
}

/** Process a camera photo file into Bitmap + Base64 */
private fun processImageFile(file: File): Pair<Bitmap, String>? {
    return try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        resizeAndEncode(bitmap)
    } catch (_: Exception) { null }
}

/** Process a gallery URI into Bitmap + Base64 */
private fun processImageUri(context: android.content.Context, uri: Uri): Pair<Bitmap, String>? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(input)
        input.close()
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
        resizeAndEncode(bitmap)
    } catch (_: Exception) { null }
}

/** Resize to max 800px and encode to base64 JPEG */
private fun resizeAndEncode(bitmap: Bitmap): Pair<Bitmap, String> {
    val scale = minOf(800f / bitmap.width, 800f / bitmap.height, 1f)
    val resized = Bitmap.createScaledBitmap(
        bitmap,
        maxOf(1, (bitmap.width * scale).toInt()),
        maxOf(1, (bitmap.height * scale).toInt()),
        true
    )
    if (resized !== bitmap) bitmap.recycle() // Recycle original if a new one was created
    val baos = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, 85, baos)
    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    return Pair(resized, b64)
}

// ═══════════════════════════════════════════════════
// VISION MODEL PICKER
// ═══════════════════════════════════════════════════

@Composable
private fun VisionModelPicker(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val currentProvider = remember {
        context.getSharedPreferences("beemovil", 0).getString("selected_provider", "openrouter") ?: "openrouter"
    }

    // BUG-4 FIX: Load from dynamic cache first, then refresh from API
    var visionModels by remember {
        mutableStateOf(DynamicModelFetcher.getCachedVisionModels(context, currentProvider))
    }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Also include static fallback if dynamic cache is empty
    val displayModels = if (visionModels.isEmpty()) {
        remember { ModelRegistry.getVisionModels() }
    } else {
        visionModels
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MODELO DE VISIÓN", fontSize = 10.sp, color = colorScheme.primary,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("${displayModels.size} modelos con visión ($currentProvider)",
                        fontSize = 9.sp, color = colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = {
                        isRefreshing = true
                        scope.launch {
                            val fresh = DynamicModelFetcher.getVisionModels(context, currentProvider)
                            if (fresh.isNotEmpty()) visionModels = fresh
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            displayModels.forEach { model ->
                val isSelected = selected == model.id
                Surface(
                    onClick = { onSelect(model.id) },
                    color = if (isSelected) colorScheme.primaryContainer else colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Visibility, "Vision", tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, fontSize = 13.sp,
                                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            Text("${model.provider} · ${model.sizeLabel}", fontSize = 10.sp,
                                color = colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) Icon(Icons.Filled.CheckCircle, "Selected", tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
