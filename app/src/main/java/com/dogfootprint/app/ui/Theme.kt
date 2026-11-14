package com.dogfootprint.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.dogfootprint.app.ui.pleos.PleosDark
import com.dogfootprint.app.ui.pleos.PleosLight
import com.dogfootprint.app.ui.pleos.PleosTheme
import com.dogfootprint.app.ui.pleos.PleosType

/**
 * Pleos tokens drive everything; MaterialTheme is only bridged so the few stock controls we still use
 * (Switch, TextField, Dialog scrim) pick up the same colours and type scale.
 */
@Composable
fun DogFootprintTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) PleosDark else PleosLight
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onButtonFilled, background = c.background, onBackground = c.textPrimary,
            surface = c.surface, onSurface = c.textPrimary, surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textTertiary,
            outline = c.divider, error = c.negative,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onButtonFilled, background = c.background, onBackground = c.textPrimary,
            surface = c.surface, onSurface = c.textPrimary, surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textTertiary,
            outline = c.divider, error = c.negative,
        )
    }
    val typography = Typography(
        displayLarge = PleosType.drivingValue, headlineLarge = PleosType.headlineLarge, headlineMedium = PleosType.headlineMedium,
        headlineSmall = PleosType.headlineSmall, titleLarge = PleosType.titleLarge, titleMedium = PleosType.titleMedium,
        titleSmall = PleosType.titleSmall, bodyLarge = PleosType.bodyLarge, bodyMedium = PleosType.bodyMedium,
        bodySmall = PleosType.bodySmall, labelLarge = PleosType.labelMedium, labelMedium = PleosType.labelMedium, labelSmall = PleosType.labelSmall,
    )
    PleosTheme(dark) { MaterialTheme(colorScheme = scheme, typography = typography, content = content) }
}
