package com.dogfootprint.app.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay

private const val BANNER_MS = 8_000L

private enum class Route(val path: String, val labelRes: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    FOOTPRINTS("footprints", R.string.nav_footprints, Icons.Filled.Favorite),
    PROFILE("profile", R.string.nav_profile, Icons.Filled.Person),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun DogFootprintApp(vm: CareViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startLocation()
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, LocationSource.PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.startLocation() else permissionLauncher.launch(LocationSource.PERMISSION)
    }

    Row(Modifier.fillMaxSize()) {
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination
        NavigationRail(Modifier.fillMaxHeight()) {
            Text("🐾", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 20.dp))
            Route.entries.forEach { route ->
                NavigationRailItem(
                    selected = current?.hierarchy?.any { it.route == route.path } == true,
                    onClick = {
                        nav.navigate(route.path) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(route.icon, contentDescription = null) },
                    label = { Text(stringResource(route.labelRes)) },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            NavHost(nav, startDestination = Route.HOME.path) {
                composable(Route.HOME.path) { HomeScreen(vm, state, onCreateProfile = { nav.navigate(Route.PROFILE.path) }) }
                composable(Route.FOOTPRINTS.path) { FootprintsScreen(state) }
                composable(Route.PROFILE.path) { ProfileScreen(vm, state) }
                composable(Route.SETTINGS.path) { SettingsScreen(vm, state) }
            }

            state.banner?.let { banner ->
                LaunchedEffect(banner.at) { delay(BANNER_MS); vm.dismissBanner() }
                val container = when (banner.priority) {
                    GuidePriority.INFO -> MaterialTheme.colorScheme.secondaryContainer
                    GuidePriority.WARN -> MaterialTheme.colorScheme.tertiaryContainer
                    GuidePriority.ALERT -> MaterialTheme.colorScheme.errorContainer
                }
                Card(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp, start = 32.dp, end = 32.dp).fillMaxWidth(0.8f),
                    colors = CardDefaults.cardColors(containerColor = container),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🐶", style = MaterialTheme.typography.headlineSmall)
                        Text(banner.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                        TextButton(onClick = vm::dismissBanner) { Text("✕") }
                    }
                }
            }

            TripDialogs(vm, state)
        }
    }
}

@Composable
fun EmptyDogCard(onCreateProfile: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(32.dp)) {
        Column(Modifier.padding(32.dp)) {
            Text(stringResource(R.string.home_no_dog_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.home_no_dog_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            Button(onClick = onCreateProfile) { Text(stringResource(R.string.home_create_profile)) }
        }
    }
}
