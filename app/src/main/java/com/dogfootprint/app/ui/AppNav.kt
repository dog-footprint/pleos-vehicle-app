package com.dogfootprint.app.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dogfootprint.app.R
import com.dogfootprint.app.pleos.GuidePriority
import com.dogfootprint.app.pleos.LocationSource
import com.dogfootprint.app.ui.pleos.Pleos
import com.dogfootprint.app.ui.pleos.PleosDims
import com.dogfootprint.app.ui.pleos.PleosIcon
import com.dogfootprint.app.ui.pleos.PleosIconButton
import com.dogfootprint.app.ui.pleos.PleosType
import kotlinx.coroutines.delay

private const val BANNER_MS = 8_000L

private enum class Route(val path: String, val labelRes: Int, val icon: Int) {
    NOW("now", R.string.nav_now, R.drawable.ic_thermostat),
    FOOTPRINTS("footprints", R.string.nav_footprints, R.drawable.ic_footprint),
    PROFILE("profile", R.string.nav_profile, R.drawable.ic_pets),
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_settings),
}

/**
 * Layout follows the Pleos "빠른 설정" pattern: a menu column of icon+label rows (selected row filled)
 * and a content area. The menu sits on the driver's side of the app window.
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

    Row(Modifier.fillMaxSize().background(c.background)) {
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination
        Column(Modifier.width(360.dp).fillMaxHeight().background(c.surface)) {
            Text(
                stringResource(R.string.app_name), style = PleosType.labelMedium, color = c.textTertiary,
                modifier = Modifier.padding(start = PleosDims.space32, top = PleosDims.space24, bottom = PleosDims.space8),
            )
            Route.entries.forEach { route ->
                val selected = current?.hierarchy?.any { it.route == route.path } == true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(if (selected) c.buttonFilled else c.surface)
                        .clickable {
                            nav.navigate(route.path) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(horizontal = PleosDims.space32),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fg = if (selected) c.onButtonFilled else c.textPrimary
                    PleosIcon(route.icon, fg)
                    Spacer(Modifier.width(PleosDims.space24))
                    Text(stringResource(route.labelRes), style = PleosType.bodyLargeStrong, color = fg)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            NavHost(nav, startDestination = Route.NOW.path) {
                composable(Route.NOW.path) { HomeScreen(vm, state, onCreateProfile = { nav.navigate(Route.PROFILE.path) }) }
                composable(Route.FOOTPRINTS.path) { FootprintsScreen(state) }
                composable(Route.PROFILE.path) { ProfileScreen(vm, state) }
                composable(Route.SETTINGS.path) { SettingsScreen(vm, state) }
            }

            state.banner?.let { banner ->
                LaunchedEffect(banner.at) { delay(BANNER_MS); vm.dismissBanner() }
                val (icon, tint) = when (banner.priority) {
                    GuidePriority.INFO -> R.drawable.ic_info to c.textPrimary
                    GuidePriority.WARN -> R.drawable.ic_warning to c.negative
                    GuidePriority.ALERT -> R.drawable.ic_error to c.negative
                }
                // System-notification style strip: fixed position (top of the app window), icon + text.
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = PleosDims.space40, vertical = PleosDims.space24)
                        .fillMaxWidth()
                        .clip(PleosDims.radiusSmall)
                        .background(if (c.isDark) c.surfaceHigh else c.surface)
                        .padding(start = PleosDims.space32, end = PleosDims.space16, top = PleosDims.space20, bottom = PleosDims.space20),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PleosIcon(icon, tint)
                    Spacer(Modifier.width(PleosDims.space24))
                    Text(banner.text, style = PleosType.bodyLargeStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
                    PleosIconButton(R.drawable.ic_close, onClick = vm::dismissBanner, small = true)
                }
            }

            TripDialogs(vm, state)
        }
    }
}
