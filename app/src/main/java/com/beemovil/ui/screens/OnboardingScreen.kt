package com.beemovil.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.R
import com.beemovil.ui.theme.*
import com.beemovil.llm.local.LocalModelManager
import com.beemovil.llm.DynamicModelFetcher
import com.beemovil.llm.ModelRegistry
import com.beemovil.security.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OnboardingScreen — Mandatory first-time setup wizard.
 *
 * 3-step flow:
 *   1. Welcome + Name input
 *   2. Choose AI provider (Local / OpenRouter / Ollama)
 *   3. Provider-specific configuration
 *
 * Cannot be skipped. User must complete valid configuration to proceed.
 *
 * @param onComplete Called when onboarding is done. MainActivity should navigate to Dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── State ──
    var currentStep by remember { mutableIntStateOf(0) }  // 0=Welcome, 1=ChooseProvider, 2=Config
    var userName by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf("") }  // "local", "openrouter", "ollama"

    // Theme state — user chooses during onboarding
    val isDark = isDarkTheme()

    // Animation entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    // Theme-aware colors
    val bgGradient = if (isDark) {
        listOf(Color(0xFF0A0A12), Color(0xFF0D0D1A), Color(0xFF12101F))
    } else {
        listOf(Color(0xFFF5F0E8), Color(0xFFEDE8E0), Color(0xFFE5E0D8))
    }
    val glowColor1 = if (isDark) HoneyGold.copy(alpha = 0.08f) else BrandBlue.copy(alpha = 0.08f)
    val glowColor2 = if (isDark) AccentViolet.copy(alpha = 0.06f) else BrandGreen.copy(alpha = 0.08f)
    val textPrimary = if (isDark) TextWhite else Color(0xFF1A2030)
    val textSecondary = if (isDark) TextGrayLight else Color(0xFF4A5568)
    val textMuted = if (isDark) TextGrayMuted else Color(0xFF718096)
    val accentColor = if (isDark) HoneyGold else BrandBlue
    val cardBg = if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.7f)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.1f)
    val inputBg = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White
    val inputBorder = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f)
    val progressInactive = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.12f)
    val btnDisabledBg = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
    val btnDisabledText = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = bgGradient))
    ) {
        // ── Ambient glow background ──
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-50).dp, y = (-50).dp)
                .blur(120.dp)
                .background(Brush.radialGradient(colors = listOf(glowColor1, Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .blur(100.dp)
                .background(Brush.radialGradient(colors = listOf(glowColor2, Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Progress indicator ──
            AnimatedVisibility(visible = visible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(3) { index ->
                        val isActive = index == currentStep
                        val isPast = index < currentStep
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        isActive -> accentColor
                                        isPast -> accentColor.copy(alpha = 0.5f)
                                        else -> progressInactive
                                    }
                                )
                        )
                    }
                }
            }

            // ── Main content area ──
            AnimatedContent(
                targetState = currentStep,
                label = "onboarding_step",
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it / 2 } + fadeIn() togetherWith
                            slideOutHorizontally { -it / 2 } + fadeOut()
                    } else {
                        slideInHorizontally { -it / 2 } + fadeIn() togetherWith
                            slideOutHorizontally { it / 2 } + fadeOut()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { step ->
                when (step) {
                    0 -> WelcomePage(
                        userName = userName,
                        onNameChanged = { userName = it },
                        isDark = isDark,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        textMuted = textMuted,
                        accentColor = accentColor,
                        cardBg = cardBg,
                        inputBg = inputBg,
                        inputBorder = inputBorder,
                        btnDisabledBg = btnDisabledBg,
                        btnDisabledText = btnDisabledText,
                        onNext = {
                            if (userName.isNotBlank()) {
                                // Save name immediately
                                val memDb = try {
                                    com.beemovil.memory.BeeMemoryDB(context)
                                } catch (_: Exception) { null }
                                memDb?.setSoul("name", userName.trim())
                                currentStep = 1
                            }
                        }
                    )
                    1 -> ChooseProviderPage(
                        selected = selectedProvider,
                        onSelect = { selectedProvider = it },
                        isDark = isDark,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = accentColor,
                        cardBorder = cardBorder,
                        btnDisabledBg = btnDisabledBg,
                        btnDisabledText = btnDisabledText,
                        onNext = { currentStep = 2 },
                        onBack = { currentStep = 0 }
                    )
                    2 -> ConfigProviderPage(
                        provider = selectedProvider,
                        userName = userName,
                        onBack = { currentStep = 1 },
                        onComplete = {
                            // Save theme preference
                            val themeValue = if (isDark) "dark" else "light"
                            context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("onboarding_completed", true)
                                .putString("app_theme", themeValue)
                                .apply()
                            onComplete()
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// PAGE 1: WELCOME + NAME
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WelcomePage(
    userName: String,
    onNameChanged: (String) -> Unit,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    accentColor: Color,
    cardBg: Color,
    inputBg: Color,
    inputBorder: Color,
    btnDisabledBg: Color,
    btnDisabledText: Color,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Logo animation
    var logoVisible by remember { mutableStateOf(false) }
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "logo_scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(800),
        label = "logo_alpha"
    )

    // Staggered features
    var featuresVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        logoVisible = true
        delay(600)
        featuresVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // ── Theme toggle ──
        AnimatedVisibility(
            visible = logoVisible,
            enter = fadeIn(tween(600, delayMillis = 400))
        ) {
            Surface(
                onClick = {
                    val newDark = !isDark
                    BeeThemeState.forceDark.value = newDark
                },
                color = cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        "Theme",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (isDark) "Modo Oscuro" else "Modo Claro",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "· Toca para cambiar",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Logo ──
        Image(
            painter = painterResource(R.drawable.emma_logo),
            contentDescription = "E.M.M.A. AI",
            modifier = Modifier
                .size(110.dp)
                .scale(logoScale)
                .alpha(logoAlpha)
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "E.M.M.A. AI",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            modifier = Modifier.alpha(logoAlpha)
        )
        Text(
            "Tu asistente de IA privado",
            fontSize = 16.sp,
            color = textSecondary,
            modifier = Modifier.alpha(logoAlpha)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Features ──
        AnimatedVisibility(
            visible = featuresVisible,
            enter = fadeIn(tween(600)) + slideInVertically { 40 }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureItem(
                    icon = Icons.Filled.Psychology,
                    text = "Procesa documentos, imágenes y voz",
                    isDark = isDark,
                    accentColor = accentColor,
                    textColor = textPrimary,
                    cardBg = cardBg,
                    delay = 0
                )
                FeatureItem(
                    icon = Icons.Filled.Lock,
                    text = "Tu data nunca sale del teléfono",
                    isDark = isDark,
                    accentColor = accentColor,
                    textColor = textPrimary,
                    cardBg = cardBg,
                    delay = 150
                )
                FeatureItem(
                    icon = Icons.Filled.AutoAwesome,
                    text = "18 herramientas nativas integradas",
                    isDark = isDark,
                    accentColor = accentColor,
                    textColor = textPrimary,
                    cardBg = cardBg,
                    delay = 300
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Name input ──
        AnimatedVisibility(
            visible = featuresVisible,
            enter = fadeIn(tween(800, delayMillis = 500)) + slideInVertically { 30 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "¿Cómo te llamas?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = userName,
                    onValueChange = onNameChanged,
                    placeholder = {
                        Text("Tu nombre", color = textMuted)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (userName.isNotBlank()) onNext()
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = inputBorder,
                        cursorColor = accentColor,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedContainerColor = inputBg,
                        unfocusedContainerColor = inputBg.copy(alpha = if (isDark) 0.03f else 1f)
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onNext,
                    enabled = userName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = if (isDark) Color.Black else Color.White,
                        disabledContainerColor = btnDisabledBg,
                        disabledContentColor = btnDisabledText
                    )
                ) {
                    Text(
                        "Comenzar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.ArrowForward, "Next", modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    text: String,
    isDark: Boolean,
    accentColor: Color,
    textColor: Color,
    cardBg: Color,
    delay: Int
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInHorizontally { -30 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, text,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text,
                fontSize = 14.sp,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// PAGE 2: CHOOSE PROVIDER
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ChooseProviderPage(
    selected: String,
    onSelect: (String) -> Unit,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    cardBorder: Color,
    btnDisabledBg: Color,
    btnDisabledText: Color,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Back button
        TextButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = textSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Atrás", color = textSecondary, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Elige cómo quieres usar tu IA",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        Text(
            "Puedes cambiar esto después en Configuración",
            fontSize = 14.sp,
            color = textSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Local/Gemma ──
        ProviderCard(
            isSelected = selected == "local",
            isRecommended = true,
            icon = Icons.Filled.PhoneAndroid,
            title = "IA en tu Teléfono",
            subtitle = "100% Offline · Sin costo · Privado",
            description = "Gemma 4 corre directo en tu dispositivo.\nNo necesitas internet ni cuentas externas.",
            accentColor = AccentGreen,
            tag = "⭐ RECOMENDADO",
            onClick = { onSelect("local") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── OpenRouter ──
        ProviderCard(
            isSelected = selected == "openrouter",
            icon = Icons.Filled.Cloud,
            title = "IA en la Nube",
            subtitle = "OpenRouter · GPT-4o · Claude · Gemini",
            description = "Accede a los modelos más potentes del mundo.\nTiene modelos gratuitos. Requiere API Key.",
            accentColor = AccentBlue,
            tag = "POTENCIA MÁXIMA",
            onClick = { onSelect("openrouter") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Ollama ──
        ProviderCard(
            isSelected = selected == "ollama",
            icon = Icons.Filled.Cloud,
            title = "IA en la Nube",
            subtitle = "Ollama Cloud · Gemma 4 · DeepSeek · Qwen",
            description = "Modelos open-source potentes en la nube.\nSolo necesitas una API key. Incluye\nmodelos con visión, código y razonamiento.",
            accentColor = AccentViolet,
            tag = "OPEN SOURCE",
            onClick = { onSelect("ollama") }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Continue button ──
        Button(
            onClick = onNext,
            enabled = selected.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = if (isDark) Color.Black else Color.White,
                disabledContainerColor = btnDisabledBg,
                disabledContentColor = btnDisabledText
            )
        ) {
            Text("Continuar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, "Next", modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProviderCard(
    isSelected: Boolean,
    isRecommended: Boolean = false,
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    accentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    val isDark = isDarkTheme()
    val textPri = if (isDark) TextWhite else Color(0xFF1A2030)
    val textSec = if (isDark) TextGrayLight else Color(0xFF4A5568)
    val unselectedBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.1f)
    val tagBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else unselectedBorder,
        animationSpec = tween(300),
        label = "border"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isSelected) {
            if (isDark) 0.12f else 0.15f
        } else {
            if (isDark) 0.04f else 0.06f
        },
        animationSpec = tween(300),
        label = "bg"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = bgAlpha)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, title, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPri)
                    Text(subtitle, fontSize = 12.sp, color = textSec)
                }
                // Tag
                Surface(
                    color = if (isRecommended) AccentGreen.copy(alpha = 0.2f) else tagBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        tag,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecommended) AccentGreen else textSec,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(description, fontSize = 13.sp, color = textSec, lineHeight = 18.sp)

            // Selection indicator
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle, "Selected",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Seleccionado", fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════
// PAGE 3: PROVIDER-SPECIFIC CONFIGURATION
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ConfigProviderPage(
    provider: String,
    userName: String,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    when (provider) {
        "local" -> LocalSetupPage(userName = userName, onBack = onBack, onComplete = onComplete)
        "openrouter" -> OpenRouterSetupPage(onBack = onBack, onComplete = onComplete)
        "ollama" -> OllamaSetupPage(onBack = onBack, onComplete = onComplete)
    }
}

// ───────────────────────────────────────────────────
// 3A: LOCAL GEMMA SETUP
// ───────────────────────────────────────────────────

@Composable
private fun LocalSetupPage(
    userName: String,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isDarkTheme()
    val textPri = if (isDark) TextWhite else Color(0xFF1A2030)
    val textSec = if (isDark) TextGrayLight else Color(0xFF4A5568)
    val accentClr = if (isDark) HoneyGold else BrandBlue

    // Model states
    val models = LocalModelManager.AVAILABLE_MODELS
    var selectedModels by remember { mutableStateOf(setOf<String>()) }

    // Download states
    var isDownloading by remember { mutableStateOf(false) }
    var downloadingModelId by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    var downloadStatus by remember { mutableStateOf("") }
    var completedModels by remember { mutableStateOf(setOf<String>()) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    // Check already downloaded
    LaunchedEffect(Unit) {
        LocalModelManager.appContext = context
        val alreadyDownloaded = models
            .filter { LocalModelManager.isModelDownloaded(it.id) }
            .map { it.id }
            .toSet()
        completedModels = alreadyDownloaded
    }

    val storageGB = remember { LocalModelManager.getAvailableStorageGB() }
    val canComplete = completedModels.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = onBack, enabled = !isDownloading) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = textSec, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Atrás", color = textSec, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Descarga tu IA local",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textPri
        )
        Text(
            "Elige uno o ambos modelos de Gemma 4",
            fontSize = 14.sp,
            color = textSec
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Storage info
        Surface(
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Storage, "Storage", tint = textSec, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Espacio disponible: ${String.format("%.1f", storageGB)} GB",
                    fontSize = 13.sp,
                    color = textSec
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Model cards ──
        models.forEach { model ->
            val isCompleted = completedModels.contains(model.id)
            val isSelected = selectedModels.contains(model.id)
            val isCurrentlyDownloading = isDownloading && downloadingModelId == model.id

            ModelDownloadCard(
                name = model.name,
                description = model.description,
                sizeDisplay = model.sizeDisplay,
                isSelected = isSelected,
                isCompleted = isCompleted,
                isDownloading = isCurrentlyDownloading,
                downloadProgress = if (isCurrentlyDownloading) downloadProgress else 0f,
                downloadedMB = if (isCurrentlyDownloading) (downloadedBytes / 1_048_576) else 0L,
                totalMB = if (isCurrentlyDownloading) (totalBytes / 1_048_576) else 0L,
                enabled = !isDownloading,
                onToggle = {
                    if (!isCompleted && !isDownloading) {
                        selectedModels = if (isSelected) {
                            selectedModels - model.id
                        } else {
                            selectedModels + model.id
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Download error ──
        if (downloadError != null) {
            Surface(
                color = AccentRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("❌ Error de descarga", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentRed)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(downloadError!!, fontSize = 12.sp, color = AccentRed.copy(alpha = 0.8f), lineHeight = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Download button ──
        if (!canComplete || selectedModels.isNotEmpty()) {
            Button(
                onClick = {
                    downloadError = null
                    val modelsToDownload = selectedModels.filter { !completedModels.contains(it) }
                    if (modelsToDownload.isEmpty()) return@Button

                    // Inject bootstrap HF token if not set
                    val securePrefs = SecurePrefs.get(context)
                    val existingToken = securePrefs.getString("huggingface_token", null)?.trim()
                    if (existingToken.isNullOrBlank()) {
                        securePrefs.edit()
                            .putString("huggingface_token", LocalModelManager.BOOTSTRAP_HF_TOKEN)
                            .apply()
                    }

                    val modelId = modelsToDownload.first()
                    isDownloading = true
                    downloadingModelId = modelId
                    downloadProgress = 0f
                    downloadedBytes = 0L
                    downloadStatus = "Conectando..."

                    LocalModelManager.downloadModel(
                        modelId = modelId,
                        onProgress = { dl, total ->
                            downloadedBytes = dl
                            totalBytes = total
                            downloadProgress = if (total > 0) dl.toFloat() / total else 0f
                            downloadStatus = "${dl / 1_048_576} MB / ${total / 1_048_576} MB"
                        },
                        onComplete = { success, message ->
                            isDownloading = false
                            if (success) {
                                completedModels = completedModels + modelId
                                selectedModels = selectedModels - modelId
                                downloadStatus = "✅ Completado"

                                // If more models selected, start next
                                val remaining = selectedModels.filter { !completedModels.contains(it) }
                                if (remaining.isNotEmpty()) {
                                    // Auto-start next model download
                                    val nextId = remaining.first()
                                    isDownloading = true
                                    downloadingModelId = nextId
                                    downloadProgress = 0f
                                    downloadedBytes = 0L
                                    downloadStatus = "Conectando siguiente modelo..."

                                    LocalModelManager.downloadModel(
                                        modelId = nextId,
                                        onProgress = { dl, total ->
                                            downloadedBytes = dl
                                            totalBytes = total
                                            downloadProgress = if (total > 0) dl.toFloat() / total else 0f
                                        },
                                        onComplete = { s2, m2 ->
                                            isDownloading = false
                                            if (s2) {
                                                completedModels = completedModels + nextId
                                                selectedModels = selectedModels - nextId
                                            } else {
                                                downloadError = m2
                                            }
                                        }
                                    )
                                }
                            } else {
                                downloadError = message
                            }
                        }
                    )
                },
                enabled = selectedModels.any { !completedModels.contains(it) } && !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = Color.White,
                    disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                    disabledContentColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
                )
            ) {
                Icon(Icons.Filled.Download, "Download", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isDownloading) "Descargando..." else "Descargar seleccionados",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Complete button ──
        Button(
            onClick = {
                // Save provider config
                val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                val firstCompleted = completedModels.first()
                prefs.edit()
                    .putString("selected_provider", "local")
                    .putString("selected_model", firstCompleted)
                    .apply()
                onComplete()
            },
            enabled = canComplete && !isDownloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDark) HoneyGold else BrandBlue,
                        contentColor = if (isDark) Color.Black else Color.White,
                disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                disabledContentColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.25f)
            )
        ) {
            Text(
                if (canComplete) "¡Listo! Entrar a E.M.M.A." else "Descarga al menos un modelo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (canComplete) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.RocketLaunch, "Go", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModelDownloadCard(
    name: String,
    description: String,
    sizeDisplay: String,
    isSelected: Boolean,
    isCompleted: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadedMB: Long,
    totalMB: Long,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val isDark = isDarkTheme()
    val textPri = if (isDark) TextWhite else Color(0xFF1A2030)
    val textSec = if (isDark) TextGrayLight else Color(0xFF4A5568)
    val unselBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val unselBg = if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.03f)
    val tagBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val trackBg = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
    val borderColor = when {
        isCompleted -> AccentGreen
        isSelected -> HoneyGold
        else -> Color.White.copy(alpha = 0.08f)
    }

    Surface(
        onClick = onToggle,
        enabled = enabled && !isCompleted,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected || isCompleted) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = when {
            isCompleted -> AccentGreen.copy(alpha = 0.08f)
            isSelected -> HoneyGold.copy(alpha = 0.08f)
            else -> unselBg
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Checkbox/Status
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> AccentGreen.copy(alpha = 0.2f)
                                isSelected -> HoneyGold.copy(alpha = 0.2f)
                                else -> unselBorder
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isCompleted -> Icon(Icons.Filled.Check, "Done", tint = AccentGreen, modifier = Modifier.size(18.dp))
                        isSelected -> Icon(Icons.Filled.Check, "Selected", tint = HoneyGold, modifier = Modifier.size(18.dp))
                        else -> {}
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPri)
                    Text(description, fontSize = 12.sp, color = textSec)
                }
                Surface(
                    color = tagBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        if (isCompleted) "✅ LISTO" else sizeDisplay,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) AccentGreen else textSec,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Progress bar ──
            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = HoneyGold,
                        trackColor = trackBg
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${(downloadProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HoneyGold
                        )
                        Text(
                            "${downloadedMB} MB / ${totalMB} MB",
                            fontSize = 11.sp,
                            color = textSec
                        )
                    }
                }
            }
        }
    }
}


// ───────────────────────────────────────────────────
// 3B: OPENROUTER SETUP
// ───────────────────────────────────────────────────

@Composable
private fun OpenRouterSetupPage(
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isDarkTheme()
    val textPri = if (isDark) TextWhite else Color(0xFF1A2030)
    val textSec = if (isDark) TextGrayLight else Color(0xFF4A5568)

    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<String?>(null) }
    var isValid by remember { mutableStateOf(false) }

    // Model selection state
    var availableModels by remember { mutableStateOf<List<ModelRegistry.ModelEntry>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    var showModelDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = textSec, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Atrás", color = textSec, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Configura OpenRouter",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textPri
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Step 1: Create account ──
        SetupStep(
            number = "1",
            title = "Crea una cuenta gratis",
            description = "Ve a openrouter.ai y regístrate"
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Open browser button
        Button(
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://openrouter.ai/keys")
                )
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue.copy(alpha = 0.15f),
                contentColor = AccentBlue
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.OpenInBrowser, "Open", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abrir openrouter.ai/keys", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        SetupStep(
            number = "2",
            title = "Copia tu API Key",
            description = "Genera una key y pégala aquí abajo"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── API Key input ──
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                isValid = false
                validationResult = null
                availableModels = emptyList()
                selectedModelId = null
            },
            label = { Text("API Key de OpenRouter") },
            placeholder = { Text("sk-or-v1-...", color = textSec) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        "Toggle",
                        tint = textSec
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                cursorColor = AccentBlue,
                focusedTextColor = textPri,
                unfocusedTextColor = textPri,
                focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White,
                unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.9f),
                focusedLabelColor = AccentBlue,
                unfocusedLabelColor = textSec
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Validate button ──
        Button(
            onClick = {
                isValidating = true
                validationResult = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val request = okhttp3.Request.Builder()
                                .url("https://openrouter.ai/api/v1/models")
                                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                                .build()
                            val response = okhttp3.OkHttpClient().newCall(request).execute()
                            val code = response.code
                            response.close()
                            code
                        }
                        when (result) {
                            200 -> {
                                isValid = true
                                validationResult = "✅ API Key válida — cargando modelos..."
                                // Auto-fetch models after validation
                                isLoadingModels = true
                                try {
                                    val models = DynamicModelFetcher.fetchOpenRouterModels(context, apiKey.trim())
                                    availableModels = models
                                    // Auto-select first free model
                                    selectedModelId = models.firstOrNull { it.free }?.id ?: models.firstOrNull()?.id
                                    validationResult = "✅ Key válida — ${models.size} modelos disponibles"
                                } catch (e: Exception) {
                                    // Fallback to static list
                                    availableModels = ModelRegistry.OPENROUTER
                                    selectedModelId = availableModels.firstOrNull { it.free }?.id
                                    validationResult = "✅ Key válida (modelos cargados de caché)"
                                }
                                isLoadingModels = false
                            }
                            401, 403 -> {
                                validationResult = "❌ Key inválida o expirada"
                            }
                            else -> {
                                validationResult = "⚠️ Error HTTP $result"
                            }
                        }
                    } catch (e: Exception) {
                        validationResult = "❌ Error de conexión: ${e.message?.take(60)}"
                    }
                    isValidating = false
                }
            },
            enabled = apiKey.isNotBlank() && !isValidating,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                contentColor = Color.White,
                disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
            )
        ) {
            if (isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Validando...")
            } else {
                Icon(Icons.Filled.Verified, "Validate", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Validar Key", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Validation result
        if (validationResult != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                validationResult!!,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isValid) AccentGreen else AccentRed
            )
        }

        // ── Step 3: Model Selection (appears after validation) ──
        if (isValid && availableModels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))

            SetupStep(
                number = "3",
                title = "Escoge tu modelo",
                description = "Selecciona el modelo de IA que usará E.M.M.A."
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingModels) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cargando modelos...", fontSize = 13.sp, color = textSec)
                }
            } else {
                // Group models: Free first, then paid. Show top picks per category
                val freeModels = availableModels.filter { it.free }.take(15)
                val paidModels = availableModels.filter { !it.free }.take(10)

                // Free models section
                if (freeModels.isNotEmpty()) {
                    Text("✨ Modelos Gratuitos", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentGreen)
                    Spacer(modifier = Modifier.height(6.dp))
                    freeModels.forEach { model ->
                        val isSelected = selectedModelId == model.id
                        Surface(
                            onClick = { selectedModelId = model.id },
                            color = if (isSelected) AccentBlue.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    when (model.category) {
                                        ModelRegistry.Category.VISION -> Icons.Filled.Visibility
                                        ModelRegistry.Category.CODE -> Icons.Filled.Code
                                        ModelRegistry.Category.REASONING -> Icons.Filled.Psychology
                                        else -> Icons.Filled.SmartToy
                                    },
                                    model.name,
                                    tint = if (isSelected) AccentBlue else textSec,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        model.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentBlue else textPri,
                                        maxLines = 1
                                    )
                                    Text(
                                        model.id.split("/").first() + if (model.hasVision) " • 👁 Visión" else "",
                                        fontSize = 11.sp, color = textSec, maxLines = 1
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.CheckCircle, "Selected", tint = AccentBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // Paid models section
                if (paidModels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("💎 Modelos Premium", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.height(6.dp))
                    paidModels.forEach { model ->
                        val isSelected = selectedModelId == model.id
                        Surface(
                            onClick = { selectedModelId = model.id },
                            color = if (isSelected) AccentBlue.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Diamond,
                                    model.name,
                                    tint = if (isSelected) AccentBlue else Color(0xFFFF9800),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        model.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentBlue else textPri,
                                        maxLines = 1
                                    )
                                    Text(
                                        model.sizeLabel + if (model.hasVision) " • 👁 Visión" else "",
                                        fontSize = 11.sp, color = textSec, maxLines = 1
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.CheckCircle, "Selected", tint = AccentBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Complete button ──
        Button(
            onClick = {
                // Save provider config
                val securePrefs = SecurePrefs.get(context)
                securePrefs.edit().putString("openrouter_api_key", apiKey.trim()).apply()

                val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("selected_provider", "openrouter")
                    .putString("selected_model", selectedModelId ?: "qwen/qwen3.6-plus:free")
                    .apply()

                onComplete()
            },
            enabled = isValid && selectedModelId != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDark) HoneyGold else BrandBlue,
                        contentColor = if (isDark) Color.Black else Color.White,
                disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                disabledContentColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.25f)
            )
        ) {
            Text(
                if (selectedModelId != null) "¡Listo! Entrar a E.M.M.A." else "Selecciona un modelo",
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            if (isValid && selectedModelId != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.RocketLaunch, "Go", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}



// ───────────────────────────────────────────────────
// 3C: OLLAMA CLOUD SETUP
// ───────────────────────────────────────────────────

@Composable
private fun OllamaSetupPage(
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isDarkTheme()
    val textPri = if (isDark) TextWhite else Color(0xFF1A2030)
    val textSec = if (isDark) TextGrayLight else Color(0xFF4A5568)

    var ollamaKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<String?>(null) }
    var isValid by remember { mutableStateOf(false) }

    // Model selection state
    var availableModels by remember { mutableStateOf<List<ModelRegistry.ModelEntry>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var selectedModelId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = textSec, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Atrás", color = textSec, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Configura Ollama Cloud",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textPri
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Step 1 ──
        SetupStep(
            number = "1",
            title = "Crea una cuenta en Ollama",
            description = "Ve a ollama.com y regístrate gratis"
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Open browser button
        Button(
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://ollama.com/settings/keys")
                )
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentViolet.copy(alpha = 0.15f),
                contentColor = AccentViolet
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.OpenInBrowser, "Open", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abrir ollama.com/settings/keys", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        SetupStep(
            number = "2",
            title = "Copia tu API Key",
            description = "Genera una key y pégala aquí abajo"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── API Key input ──
        OutlinedTextField(
            value = ollamaKey,
            onValueChange = {
                ollamaKey = it
                isValid = false
                validationResult = null
                availableModels = emptyList()
                selectedModelId = null
            },
            label = { Text("API Key de Ollama") },
            placeholder = { Text("Tu API key de ollama.com", color = textSec) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        "Toggle", tint = textSec
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentViolet,
                unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                cursorColor = AccentViolet,
                focusedTextColor = textPri,
                unfocusedTextColor = textPri,
                focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White,
                unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.9f),
                focusedLabelColor = AccentViolet,
                unfocusedLabelColor = textSec
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Validate button ──
        Button(
            onClick = {
                isValidating = true
                validationResult = null
                scope.launch {
                    try {
                        val code = withContext(Dispatchers.IO) {
                            val request = okhttp3.Request.Builder()
                                .url("https://ollama.com/api/tags")
                                .addHeader("Authorization", "Bearer ${ollamaKey.trim()}")
                                .build()
                            val response = okhttp3.OkHttpClient.Builder()
                                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                                .newCall(request).execute()
                            val result = response.code
                            response.close()
                            result
                        }
                        when (code) {
                            200 -> {
                                isValid = true
                                validationResult = "✅ API Key válida — cargando modelos..."
                                // Auto-fetch models
                                isLoadingModels = true
                                try {
                                    val models = DynamicModelFetcher.fetchOllamaModels(context, "https://ollama.com")
                                    availableModels = models
                                    selectedModelId = models.firstOrNull()?.id
                                    validationResult = "✅ Key válida — ${models.size} modelos disponibles"
                                } catch (e: Exception) {
                                    availableModels = ModelRegistry.OLLAMA_CLOUD
                                    selectedModelId = availableModels.firstOrNull()?.id
                                    validationResult = "✅ Key válida (modelos cargados de caché)"
                                }
                                isLoadingModels = false
                            }
                            401, 403 -> validationResult = "❌ Key inválida o expirada"
                            else -> validationResult = "⚠️ Error HTTP $code"
                        }
                    } catch (e: Exception) {
                        validationResult = "❌ Error de conexión: ${e.message?.take(60)}"
                    }
                    isValidating = false
                }
            },
            enabled = ollamaKey.isNotBlank() && !isValidating,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentViolet,
                contentColor = Color.White,
                disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
            )
        ) {
            if (isValidating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Validando...")
            } else {
                Icon(Icons.Filled.Verified, "Validate", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Validar Key", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Validation result
        if (validationResult != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                validationResult!!,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isValid) AccentGreen else AccentRed
            )
        }

        // ── Step 3: Model Selection (appears after validation) ──
        if (isValid && availableModels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))

            SetupStep(
                number = "3",
                title = "Escoge tu modelo",
                description = "Selecciona el modelo de IA que usará E.M.M.A."
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingModels) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentViolet)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cargando modelos...", fontSize = 13.sp, color = textSec)
                }
            } else {
                availableModels.forEach { model ->
                    val isSelected = selectedModelId == model.id
                    Surface(
                        onClick = { selectedModelId = model.id },
                        color = if (isSelected) AccentViolet.copy(alpha = 0.12f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (model.category) {
                                    ModelRegistry.Category.VISION -> Icons.Filled.Visibility
                                    ModelRegistry.Category.CODE -> Icons.Filled.Code
                                    ModelRegistry.Category.REASONING -> Icons.Filled.Psychology
                                    else -> Icons.Filled.SmartToy
                                },
                                model.name,
                                tint = if (isSelected) AccentViolet else textSec,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentViolet else textPri,
                                    maxLines = 1
                                )
                                Text(
                                    model.sizeLabel + if (model.hasVision) " • 👁 Visión" else "",
                                    fontSize = 11.sp, color = textSec, maxLines = 1
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.CheckCircle, "Selected", tint = AccentViolet, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Complete button ──
        Button(
            onClick = {
                // Save config
                val securePrefs = SecurePrefs.get(context)
                securePrefs.edit().putString("ollama_api_key", ollamaKey.trim()).apply()

                val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("selected_provider", "ollama")
                    .putString("selected_model", selectedModelId ?: "gemma4:cloud")
                    .apply()

                onComplete()
            },
            enabled = isValid && selectedModelId != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDark) HoneyGold else BrandBlue,
                        contentColor = if (isDark) Color.Black else Color.White,
                disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                disabledContentColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.25f)
            )
        ) {
            Text(
                if (selectedModelId != null) "¡Listo! Entrar a E.M.M.A." else "Selecciona un modelo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (isValid && selectedModelId != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.RocketLaunch, "Go", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}


// ───────────────────────────────────────────────────
// SHARED COMPONENTS
// ───────────────────────────────────────────────────

@Composable
private fun SetupStep(number: String, title: String, description: String) {
    val isDark = isDarkTheme()
    val titleColor = if (isDark) TextWhite else Color(0xFF1A2030)
    val descColor = if (isDark) TextGrayLight else Color(0xFF4A5568)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AccentBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = titleColor)
            Text(description, fontSize = 13.sp, color = descColor)
        }
    }
}
