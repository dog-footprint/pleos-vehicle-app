package com.dogfootprint.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dogfootprint.app.BuildConfig
import com.dogfootprint.app.R
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.pleos.SdkStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: CareViewModel, state: CareState) {
    val settings = state.data.settings
    val d = state.diagnostics
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ToggleRow(stringResource(R.string.settings_control), stringResource(R.string.settings_control_body), settings.controlEnabled) { on ->
                    vm.updateSettings { it.copy(controlEnabled = on) }
                }
                ToggleRow(stringResource(R.string.settings_tts), stringResource(R.string.settings_tts_body), settings.pleosTts) { on ->
                    vm.updateSettings { it.copy(pleosTts = on) }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_sdk_status), style = MaterialTheme.typography.titleMedium)
                StatusRow(stringResource(R.string.sdk_vehicle), d.vehicle, d.vehicleDetail)
                StatusRow(stringResource(R.string.sdk_navi), d.navi, state.navi.lastError)
                StatusRow(stringResource(R.string.sdk_tts), d.tts, d.ttsDetail)
                StatusRow(stringResource(R.string.sdk_location), d.location, null)
                Text(
                    stringResource(R.string.settings_about, BuildConfig.PLEOS_VEHICLE_SDK, BuildConfig.PLEOS_NAVI_SDK, BuildConfig.PLEOS_TTS_SDK),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_demo), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_demo_body), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::demoBrakeBurst) { Text(stringResource(R.string.demo_brake)) }
                    OutlinedButton(onClick = vm::demoBreakDue) { Text(stringResource(R.string.demo_break)) }
                    OutlinedButton(onClick = { vm.demoTemp(27f) }) { Text(stringResource(R.string.demo_temp_hot)) }
                    OutlinedButton(onClick = { vm.demoTemp(16f) }) { Text(stringResource(R.string.demo_temp_cold)) }
                    OutlinedButton(onClick = { vm.demoTemp(null) }, enabled = state.demoTemp != null) { Text(stringResource(R.string.demo_temp_clear)) }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun StatusRow(name: String, status: SdkStatus, detail: String?) {
    val color = when (status) {
        SdkStatus.READY -> StatusColors.good
        SdkStatus.CONNECTING -> StatusColors.warn
        SdkStatus.UNAVAILABLE -> StatusColors.bad
        SdkStatus.UNKNOWN -> Color.Gray
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 16.dp))
        Text(stringResource(status.labelRes), style = MaterialTheme.typography.bodyLarge, color = color)
        detail?.let { Text("  · $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
