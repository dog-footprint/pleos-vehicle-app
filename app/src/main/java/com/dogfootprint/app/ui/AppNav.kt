package com.dogfootprint.app.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dogfootprint.app.R
import com.dogfootprint.app.care.AlertLevel
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.pleos.GuidePriority
import com.dogfootprint.app.pleos.LocationSource
import com.dogfootprint.app.ui.pleos.Pleos
import com.dogfootprint.app.ui.pleos.PleosDims
import com.dogfootprint.app.ui.pleos.PleosIcon
import com.dogfootprint.app.ui.pleos.PleosIconButton
import com.dogfootprint.app.ui.pleos.PleosStatusLine
import com.dogfootprint.app.ui.pleos.PleosType
import kotlinx.coroutines.delay

/** Information-level notices (ivi_toast_popup pattern) go away on their own. */
private const val NOTICE_MS = 4_000L

private enum class Route(val path: String, val labelRes: Int, val icon: Int) {
    NOW("now", R.string.nav_now, R.drawable.ic_health_and_safety),
    FOOTPRINTS("footprints", R.string.nav_footprints, R.drawable.ic_footprint),
    PROFILE("profile", R.string.nav_profile, R.drawable.ic_badge),   // outlined like the other three (ic_pets is a filled glyph)
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_settings),
}

/**
 * Vertical GNB on the driver's side (ivi_gnb/vertical 140 wide, gnb_button/standard 124), then a column with a
 * fixed-height warning slot above the screens, so a warning never pushes the content around. Screens cut
 * between each other (no cross-fade), as the review asked.
 */
@Composable
fun DogFootprintApp(vm: CareViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val context = LocalContext.current
    val c = Pleos.colors

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startLocation()
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, LocationSource.PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.startLocation() else permissionLauncher.launch(LocationSource.PERMISSION)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val compact = maxWidth < PleosDims.compactMaxWidth
        Row(Modifier.fillMaxSize()) {
            val backStack by nav.currentBackStackEntryAsState()
            val current = backStack?.destination
            // Routes with arguments ("footprints?trip={trip}") must still light their tab.
            NavRail(selected = { route -> current?.hierarchy?.any { it.route?.substringBefore('?') == route.path } == true }) { route ->
                nav.navigate(route.path) {
                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                AlertSlot(vm, state)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    NavHost(
                        nav, startDestination = Route.NOW.path,
                        enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None },
                    ) {
                        composable(Route.NOW.path) {
                            HomeScreen(vm, state, compact, onCreateProfile = { nav.navigate(Route.PROFILE.path) }, onOpenTrip = { id -> nav.navigate(Route.FOOTPRINTS.path + "?trip=" + id) })
                        }
                        composable(Route.FOOTPRINTS.path + "?trip={trip}", arguments = listOf(navArgument("trip") { nullable = true; defaultValue = null })) { entry ->
                            FootprintsScreen(state, initialTripId = entry.arguments?.getString("trip"))
                        }
                        composable(Route.PROFILE.path) { ProfileScreen(vm, state) }
                        composable(Route.SETTINGS.path) { SettingsScreen(vm, state) }
                    }
                    TripDialogs(vm, state)
                }
            }
        }
    }
}

@Composable
private fun NavRail(selected: (Route) -> Boolean, onSelect: (Route) -> Unit) {
    val c = Pleos.colors
    Column(
        Modifier.width(PleosDims.gnbVerticalWidth).fillMaxHeight().background(c.surface).padding(top = PleosDims.space24),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PleosDims.space8),
    ) {
        Route.entries.forEach { route ->
            val on = selected(route)
            val fg = if (on) c.onButtonFilled else c.textPrimary
            Column(
                Modifier
                    .size(PleosDims.gnbButton)
                    .clip(PleosDims.radiusMedium)
                    .background(if (on) c.buttonFilled else c.surface)
                    .clickable { onSelect(route) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                PleosIcon(route.icon, fg)
                Spacer(Modifier.height(PleosDims.space4))
                Text(stringResource(route.labelRes), style = PleosType.labelSmall, color = fg)
            }
        }
    }
}

/**
 * Fixed 112 px slot (table row height). Priority: DANGER (no close) > CAUTION (closable) > transient notice (4 s)
 * > a closed caution shown as a plain status line > empty. Several at once: the top one plus "외 n건", which opens
 * the full list while parked and is plain text while driving.
 * Shapes, not just colour (3rd review B1): danger = negative border + negative-filled icon badge; caution = a
 * textPrimary bar on the left + triangle icon; notice = nothing but the icon.
 */
@Composable
private fun AlertSlot(vm: CareViewModel, state: CareState) {
    val c = Pleos.colors
    val top = state.topAlert
    val visible = state.alerts.filter { it.level == AlertLevel.DANGER || it.kind !in state.dismissedCautions }
    val dismissed = state.alerts.firstOrNull { it.level == AlertLevel.CAUTION && it.kind in state.dismissedCautions }
    val notice = state.banner
    Box(Modifier.fillMaxWidth().height(PleosDims.listRow).padding(horizontal = PleosDims.space40, vertical = PleosDims.space16), contentAlignment = Alignment.CenterStart) {
        when {
            top != null -> {
                val danger = top.level == AlertLevel.DANGER
                Strip(
                    shape = if (danger) StripShape.DANGER else StripShape.CAUTION,
                    icon = top.kind.iconRes, text = alertText(top, state),
                    extra = if (visible.size > 1) stringResource(R.string.alert_more_fmt, visible.size - 1) else null,
                    onExtra = if (!state.moving) { { showAlertSheet.value = true } } else null,
                    onClose = if (danger) null else { { vm.dismissAlert(top.kind) } },
                )
            }
            notice != null -> {
                LaunchedEffect(notice.at) { delay(NOTICE_MS); vm.dismissBanner() }
                val icon = when (notice.priority) {
                    GuidePriority.INFO -> R.drawable.ic_info
                    GuidePriority.WARN -> R.drawable.ic_warning
                    GuidePriority.ALERT -> R.drawable.ic_error
                }
                Strip(shape = StripShape.NOTICE, icon = icon, text = notice.text, extra = null, onExtra = null, onClose = vm::dismissBanner)
            }
            dismissed != null -> PleosStatusLine(dismissed.kind.iconRes, alertText(dismissed, state), tint = c.textSecondary)
        }
    }
}

private enum class StripShape { DANGER, CAUTION, NOTICE }

@Composable
private fun Strip(shape: StripShape, icon: Int, text: String, extra: String?, onExtra: (() -> Unit)?, onClose: (() -> Unit)?) {
    val c = Pleos.colors
    val tint = if (shape == StripShape.DANGER) c.negative else c.textPrimary
    Row(
        Modifier
            .fillMaxWidth().fillMaxHeight()
            .clip(PleosDims.radiusSmall)
            .background(if (c.isDark) c.surfaceHigh else c.surface)
            .then(if (shape == StripShape.DANGER) Modifier.border(PleosDims.space4, c.negative, PleosDims.radiusSmall) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (shape == StripShape.CAUTION) Box(Modifier.width(PleosDims.space4).fillMaxHeight().background(c.textPrimary))
        Spacer(Modifier.width(if (shape == StripShape.CAUTION) PleosDims.space20 else PleosDims.space24))
        if (shape == StripShape.DANGER) {
            Box(Modifier.size(PleosDims.icon).clip(CircleShape).background(c.negative), contentAlignment = Alignment.Center) {
                PleosIcon(icon, c.onButtonFilled, size = PleosDims.iconSmall)
            }
        } else {
            PleosIcon(icon, tint)
        }
        Spacer(Modifier.width(PleosDims.space16))
        Text(text, style = PleosType.bodyLargeStrong, color = tint, modifier = Modifier.weight(1f), maxLines = 1)
        extra?.let {
            Text(
                it, style = PleosType.labelMedium, color = if (onExtra != null) c.textPrimary else c.textTertiary,
                modifier = Modifier.padding(horizontal = PleosDims.space16).then(if (onExtra != null) Modifier.clickable(onClick = onExtra) else Modifier),
            )
        }
        if (onClose != null) PleosIconButton(R.drawable.ic_close, onClick = onClose, small = true) else Spacer(Modifier.width(PleosDims.space8))
    }
}
