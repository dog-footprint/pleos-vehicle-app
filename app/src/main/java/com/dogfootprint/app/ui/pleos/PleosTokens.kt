package com.dogfootprint.app.ui.pleos

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Design tokens read from the official Pleos Connect Figma files
 * (Pleos_Connect_design_assets_215.fig / Pleos_Connect_display_guide_215.fig, Hyundai theme, connect-s).
 * Values are px on the 2560x1440 / 160 dpi display, i.e. dp == px here.
 * Anything not in the files is marked "ours".
 */

@Immutable
data class PleosColors(
    val isDark: Boolean,
    /** color/basic/50 → background/primary */
    val background: Color,
    /** color/basic/00 → surface/basic (light) ; basic/50 in dark */
    val surface: Color,
    /** color/basic/100 → surface/high, popup background (dark) */
    val surfaceHigh: Color,
    /** text/dark/primary = static 100 */
    val textPrimary: Color,
    /** static 200 = 84 % */
    val textSecondary: Color,
    /** static 300 = 64 % */
    val textTertiary: Color,
    /** static 400 = 32 % */
    val textQuaternary: Color,
    /** divider/secondary = alpha 20 % */
    val divider: Color,
    /** alpha/dark/50 → button/basic/enabled */
    val pressOverlay: Color,
    /** button/filled/enabled (Primary button): basic 600 in light, inverted in dark */
    val buttonFilled: Color,
    val onButtonFilled: Color,
    /** Secondary basic button fill (#44464e in the component) */
    val buttonSecondary: Color,
    val onButtonSecondary: Color,
    /** informative/positive — the single accent: state + action */
    val accent: Color,
    /** informative/negative — the single warning colour */
    val negative: Color,
    /** informative/active — switch "on" */
    val active: Color,
)

val PleosLight = PleosColors(
    isDark = false,
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEDEEF2),
    textPrimary = Color(0xFF131417),
    textSecondary = Color(0xD6131417),
    textTertiary = Color(0xA3131417),
    textQuaternary = Color(0x52131417),
    divider = Color(0x33131417),
    pressOverlay = Color(0x0D131417),
    buttonFilled = Color(0xFF131417),
    onButtonFilled = Color(0xFFFFFFFF),
    buttonSecondary = Color(0xFF44464E),
    onButtonSecondary = Color(0xFFFFFFFF),
    accent = Color(0xFF0064FF),
    negative = Color(0xFFFE3D16),
    active = Color(0xFF02C265),
)

val PleosDark = PleosColors(
    isDark = true,
    background = Color(0xFF1D1E21),
    surface = Color(0xFF131417),
    surfaceHigh = Color(0xFF313236),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xD6FFFFFF),
    textTertiary = Color(0xA3FFFFFF),
    textQuaternary = Color(0x52FFFFFF),
    divider = Color(0x33FFFFFF),
    pressOverlay = Color(0x1AFFFFFF),
    buttonFilled = Color(0xFFFFFFFF),
    onButtonFilled = Color(0xFF131417),
    buttonSecondary = Color(0xFF44464E),
    onButtonSecondary = Color(0xFFFFFFFF),
    accent = Color(0xFF0082FF),
    negative = Color(0xFFFF4C28),
    active = Color(0xFF00E074),
)

/** Pleos type scale (connect-s). Family is the system UI font (HyundaiSansUI on the vehicle). */
object PleosType {
    private fun style(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    /** Driving/Speed_kmh 112/134 — the one glanceable number */
    val drivingValue = style(112, 134, FontWeight.Bold)
    val headlineLarge = style(60, 80, FontWeight.ExtraBold)
    val headlineMedium = style(56, 72, FontWeight.ExtraBold)
    val headlineSmall = style(48, 64, FontWeight.Bold)
    val titleLarge = style(40, 52, FontWeight.Bold)
    val titleMedium = style(36, 44, FontWeight.Bold)
    val titleSmall = style(32, 40, FontWeight.Bold)
    val bodyLargeStrong = style(30, 38, FontWeight.Bold)
    val bodyLarge = style(30, 38, FontWeight.Normal)
    val bodyMediumStrong = style(28, 36, FontWeight.Bold)
    val bodyMedium = style(28, 36, FontWeight.Normal)
    val bodySmallStrong = style(26, 34, FontWeight.Bold)
    val bodySmall = style(26, 34, FontWeight.Normal)
    val labelMedium = style(24, 28, FontWeight.Normal)
    val labelSmall = style(20, 24, FontWeight.Normal)
}

/** Sizes from form_factor (connect-s) and Number sets. */
object PleosDims {
    val space4 = 4.dp; val space8 = 8.dp; val space12 = 12.dp; val space16 = 16.dp
    val space20 = 20.dp; val space24 = 24.dp; val space32 = 32.dp; val space40 = 40.dp; val space48 = 48.dp

    val buttonLarge = 100.dp; val buttonMedium = 80.dp; val buttonSmall = 64.dp
    val buttonLargeMinWidth = 140.dp; val buttonMediumMinWidth = 120.dp; val buttonSmallMinWidth = 100.dp
    val buttonLargePadding = 40.dp; val buttonMediumPadding = 32.dp; val buttonSmallPadding = 24.dp
    val iconButtonMedium = 80.dp; val iconButtonSmall = 64.dp
    val icon = 48.dp; val iconSmall = 40.dp
    /** table Contents=Body row = 112 px; Title row = 100 px */
    val listRow = 112.dp
    val windowPadding = 20.dp
    val dividerWidth = 1.dp

    val radiusLarge = RoundedCornerShape(32.dp)   // radius level9 — large button / utility panels
    val radiusMedium = RoundedCornerShape(24.dp)  // radius/l — medium button
    val radiusSmall = RoundedCornerShape(16.dp)   // radius/m — small button, icon button small
    val radiusIcon = RoundedCornerShape(20.dp)    // icon button medium
}

val LocalPleosColors = staticCompositionLocalOf { PleosLight }

object Pleos {
    val colors: PleosColors @Composable get() = LocalPleosColors.current
}

@Composable
fun PleosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPleosColors provides if (darkTheme) PleosDark else PleosLight, content = content)
}
