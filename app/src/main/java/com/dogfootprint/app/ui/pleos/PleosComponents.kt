package com.dogfootprint.app.ui.pleos

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * The handful of Pleos components the app needs, built to the measurements in the design assets
 * (ivi_basic_button, ivi_toggle_button, ivi_icon_button, table rows, switch, popup).
 */

enum class ButtonSize(val height: Dp, val minWidth: Dp, val padding: Dp, val shape: Shape, val text: TextStyle, val gap: Dp) {
    Large(PleosDims.buttonLarge, PleosDims.buttonLargeMinWidth, PleosDims.buttonLargePadding, PleosDims.radiusLarge, PleosType.titleSmall, 16.dp),
    Medium(PleosDims.buttonMedium, PleosDims.buttonMediumMinWidth, PleosDims.buttonMediumPadding, PleosDims.radiusMedium, PleosType.bodyLargeStrong, 14.dp),
    Small(PleosDims.buttonSmall, PleosDims.buttonSmallMinWidth, PleosDims.buttonSmallPadding, PleosDims.radiusSmall, PleosType.bodySmallStrong, 12.dp),
}

@Composable
fun PleosIcon(@DrawableRes res: Int, tint: Color, size: Dp = PleosDims.icon, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(painterResource(res), contentDescription, modifier.size(size), tint = tint)
}

/** ivi_basic_button — Primary (filled) or Secondary (#44464e). */
@Composable
fun PleosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Medium,
    primary: Boolean = true,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val c = Pleos.colors
    val bg = if (primary) c.buttonFilled else c.buttonSecondary
    val fg = if (primary) c.onButtonFilled else c.onButtonSecondary
    ButtonShell(onClick, modifier, size, bg.copy(alpha = if (enabled) 1f else 0.32f), enabled) {
        icon?.let { PleosIcon(it, fg, size = if (size == ButtonSize.Small) 32.dp else 40.dp); Spacer(Modifier.width(size.gap)) }
        Text(text, style = size.text, color = fg, maxLines = 1)
    }
}

/** ivi_toggle_button — on: filled; off: surface with a divider border (as in the Pleos climate panel). */
@Composable
fun PleosToggleButton(
    text: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val c = Pleos.colors
    val bg = if (on) c.buttonFilled else c.surface
    val fg = if (on) c.onButtonFilled else c.textPrimary
    ButtonShell(
        onClick = { onToggle(!on) }, modifier = modifier, size = size, background = bg, enabled = enabled,
        border = if (on) null else c.divider,
    ) {
        icon?.let { PleosIcon(it, fg, size = if (size == ButtonSize.Small) 32.dp else 40.dp); Spacer(Modifier.width(size.gap)) }
        Text(text, style = size.text, color = fg, maxLines = 1)
    }
}

@Composable
private fun ButtonShell(
    onClick: () -> Unit,
    modifier: Modifier,
    size: ButtonSize,
    background: Color,
    enabled: Boolean,
    border: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .defaultMinSize(minWidth = size.minWidth, minHeight = size.height)
            .height(size.height)
            .clip(size.shape)
            .background(background)
            .then(if (border != null) Modifier.border(PleosDims.dividerWidth, border, size.shape) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = size.padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

/** ivi_icon_button — Plain (no fill) or Basic (filled), 80 / 64. */
@Composable
fun PleosIconButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    small: Boolean = false,
    contentDescription: String? = null,
) {
    val c = Pleos.colors
    val side = if (small) PleosDims.iconButtonSmall else PleosDims.iconButtonMedium
    val shape = if (small) PleosDims.radiusSmall else PleosDims.radiusIcon
    Box(
        modifier.size(side).clip(shape).background(if (filled) c.buttonFilled else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PleosIcon(icon, if (filled) c.onButtonFilled else c.textPrimary, size = if (small) 40.dp else PleosDims.icon, contentDescription = contentDescription)
    }
}

@Composable
fun PleosDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(PleosDims.dividerWidth).background(Pleos.colors.divider))
}

/** table "Contents=Body" row: 112 px, icon · title / subtitle · trailing. */
@Composable
fun PleosListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = Pleos.colors.textPrimary,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Pleos.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = PleosDims.listRow)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = PleosDims.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { PleosIcon(it, iconTint); Spacer(Modifier.width(PleosDims.space24)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = PleosType.bodyLargeStrong, color = c.textPrimary)
                subtitle?.let { Text(it, style = PleosType.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = 4.dp)) }
            }
            trailing?.let { Spacer(Modifier.width(PleosDims.space16)); it() }
        }
        if (divider) PleosDivider()
    }
}

/** Pleos switch: green "active" track when on, divider-grey when off. */
@Composable
fun PleosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Pleos.colors
    Switch(
        checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.active, checkedThumbColor = Color.White,
            uncheckedTrackColor = c.divider, uncheckedThumbColor = c.surface, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Status line: icon + text, never colour alone. */
@Composable
fun PleosStatusLine(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier, tint: Color = Pleos.colors.textPrimary, style: TextStyle = PleosType.bodyLarge) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PleosIcon(icon, tint, size = PleosDims.iconSmall)
        Spacer(Modifier.width(PleosDims.space16))
        Text(text, style = style, color = tint)
    }
}

/** Popup: surface, radius 32, title + body + actions; dismissable only through its buttons while driving. */
@Composable
fun PleosPopup(
    title: String,
    onDismiss: () -> Unit,
    dismissOnOutside: Boolean = true,
    body: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val c = Pleos.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = dismissOnOutside, usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(900.dp).clip(PleosDims.radiusLarge).background(if (c.isDark) c.surfaceHigh else c.surface).padding(PleosDims.space40),
        ) {
            Text(title, style = PleosType.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(PleosDims.space24))
            body()
            Spacer(Modifier.height(PleosDims.space40))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosDims.space16, Alignment.End), content = actions)
        }
    }
}
