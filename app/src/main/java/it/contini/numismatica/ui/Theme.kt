package it.contini.numismatica.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Cabinet green and old gold, like a coin tray
private val Light = lightColorScheme(
    primary = Color(0xFF1F5A4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8DF),
    onPrimaryContainer = Color(0xFF062019),
    secondary = Color(0xFF8C6D12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E2A8),
    onSecondaryContainer = Color(0xFF2B2100),
    background = Color(0xFFFAF8F2),
    surface = Color(0xFFFAF8F2),
    surfaceVariant = Color(0xFFE9E5DA),
    onSurfaceVariant = Color(0xFF4A473F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9FD3C3),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF1F5A4C),
    onPrimaryContainer = Color(0xFFCDE8DF),
    secondary = Color(0xFFE2C05A),
    onSecondary = Color(0xFF3A2E00),
    secondaryContainer = Color(0xFF5E4A00),
    onSecondaryContainer = Color(0xFFF4E2A8),
    background = Color(0xFF14130F),
    surface = Color(0xFF14130F),
    surfaceVariant = Color(0xFF2E2C26),
    onSurfaceVariant = Color(0xFFCCC6B8),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    )
}

@Composable
fun NumismaticaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
