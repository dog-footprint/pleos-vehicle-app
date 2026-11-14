package com.dogfootprint.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Warm, readable palette for a 2560x1440 head unit viewed at arm's length.
private val Light = lightColorScheme(
    primary = Color(0xFF8C5A2B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFF3C6E71),
    secondaryContainer = Color(0xFFCCEBEC),
    tertiary = Color(0xFFB7791F),
    tertiaryContainer = Color(0xFFFFE8B8),
    background = Color(0xFFFBF7F2),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1E9DF),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE3B88A),
    onPrimary = Color(0xFF3F2A12),
    primaryContainer = Color(0xFF5C3F1F),
    secondary = Color(0xFF8FC1C4),
    secondaryContainer = Color(0xFF244F52),
    tertiary = Color(0xFFF2C56B),
    tertiaryContainer = Color(0xFF5B4312),
    background = Color(0xFF1B1A18),
    surface = Color(0xFF242220),
    surfaceVariant = Color(0xFF34302B),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
)

private val CarTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontSize = 52.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Typography().headlineMedium.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = Typography().headlineSmall.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 20.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 18.sp),
    labelLarge = Typography().labelLarge.copy(fontSize = 18.sp),
)

@Composable
fun DogFootprintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = CarTypography,
        content = content,
    )
}

/** Semantic status colours shared by tiles and chips. */
object StatusColors {
    val good = Color(0xFF2E7D32)
    val warn = Color(0xFFEF8F00)
    val bad = Color(0xFFC62828)
}
