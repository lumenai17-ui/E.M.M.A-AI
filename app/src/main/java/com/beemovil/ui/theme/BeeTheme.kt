package com.beemovil.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val BeeColorScheme = darkColorScheme(
    primary = BeeYellow,
    onPrimary = BeeBlack,
    primaryContainer = BeeYellowDark,
    secondary = BeeGrayLight,
    onSecondary = BeeWhite,
    background = BeeBlack,
    surface = BeeBlackLight,
    surfaceVariant = BeeGray,
    onBackground = BeeWhite,
    onSurface = BeeWhite,
    error = BeeRed
)

@Composable
fun BeeMovilTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BeeBlack.toArgb()
            window.navigationBarColor = BeeBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = BeeColorScheme,
        typography = Typography(
            titleLarge = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp
            ),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            )
        ),
        content = content
    )
}
