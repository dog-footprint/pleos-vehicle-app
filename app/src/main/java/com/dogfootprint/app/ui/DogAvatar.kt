package com.dogfootprint.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.dogfootprint.app.ui.pleos.Pleos
import com.dogfootprint.app.ui.pleos.PleosDims
import com.dogfootprint.app.ui.pleos.PleosIcon

/**
 * The dog's picture: breed line-art in a circle, the same proportions as a Pleos icon button (80 dp shell, 48 dp
 * icon; or 64 / 40 for the small size). The platform cannot import a photo (no phone→vehicle channel, no USB
 * import on the emulator), so the breed icon stands in for one everywhere the dog is named.
 */
@Composable
fun DogAvatar(
    breedKey: String,
    modifier: Modifier = Modifier,
    size: Dp = PleosDims.iconButtonMedium,
    icon: Dp = PleosDims.icon,
    tint: Color = Pleos.colors.textPrimary,
) {
    Box(modifier.size(size).clip(CircleShape).background(Pleos.colors.surfaceHigh), contentAlignment = Alignment.Center) {
        PleosIcon(breedIconRes(breedKey), tint, size = icon)
    }
}
