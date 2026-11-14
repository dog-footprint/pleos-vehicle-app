package com.dogfootprint.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dogfootprint.app.BuildConfig
import com.dogfootprint.app.R
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.data.CalmMode
import com.dogfootprint.app.ui.pleos.PleosToggleButton
import androidx.compose.foundation.layout.Row
import com.dogfootprint.app.pleos.SdkStatus
import com.dogfootprint.app.ui.pleos.ButtonSize
import com.dogfootprint.app.ui.pleos.Pleos
import com.dogfootprint.app.ui.pleos.PleosButton
import com.dogfootprint.app.ui.pleos.PleosDims
import com.dogfootprint.app.ui.pleos.PleosListRow
import com.dogfootprint.app.ui.pleos.PleosSwitch
import com.dogfootprint.app.ui.pleos.PleosType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: CareViewModel, state: CareState) {
    val c = Pleos.colors
    val settings = state.data.settings
    val d = state.diagnostics
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PleosDims.space40, vertical = PleosDims.space32)) {
        Text(stringResource(R.string.settings_title), style = PleosType.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(PleosDims.space24))

        PleosListRow(stringResource(R.string.settings_control), subtitle = stringResource(R.string.settings_control_body), icon = R.drawable.ic_mode_fan) {
            PleosSwitch(settings.controlEnabled, { on -> vm.updateSettings { it.copy(controlEnabled = on) } })
        }
        PleosListRow(stringResource(R.string.settings_tts), subtitle = stringResource(R.string.settings_tts_body), icon = R.drawable.ic_volume_up) {
            PleosSwitch(settings.pleosTts, { on -> vm.updateSettings { it.copy(pleosTts = on) } })
        }
        PleosListRow(stringResource(R.string.calm_mode), subtitle = stringResource(R.string.calm_evidence), icon = R.drawable.ic_graphic_eq, divider = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space12)) {
                CalmMode.entries.forEach { m ->
                    PleosToggleButton(
                        stringResource(if (m == CalmMode.MUSIC) R.string.calm_mode_music else R.string.calm_mode_story), on = settings.calmMode == m, size = ButtonSize.Small,
                        onToggle = { vm.updateSettings { it.copy(calmMode = m) }; vm.restartCalmIfPlaying() },
                    )
                }
            }
        }

        run {
            Spacer(Modifier.height(PleosDims.space40))
            Text(stringResource(R.string.settings_developer), style = PleosType.titleSmall, color = c.textPrimary)
            Text(stringResource(R.string.settings_demo_body), style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space4, bottom = PleosDims.space16))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PleosDims.space12), verticalArrangement = Arrangement.spacedBy(PleosDims.space12)) {
                PleosButton(stringResource(R.string.demo_brake), onClick = vm::demoBrakeBurst, size = ButtonSize.Small, primary = false)
                PleosButton(stringResource(R.string.demo_break), onClick = vm::demoBreakDue, size = ButtonSize.Small, primary = false)
                PleosButton(stringResource(R.string.demo_temp_hot), onClick = { vm.demoTemp(27f) }, size = ButtonSize.Small, primary = false)
                PleosButton(stringResource(R.string.demo_temp_cold), onClick = { vm.demoTemp(16f) }, size = ButtonSize.Small, primary = false)
                PleosButton(stringResource(R.string.demo_temp_clear), onClick = { vm.demoTemp(null) }, size = ButtonSize.Small, primary = false, enabled = state.demoTemp != null)
                PleosButton(stringResource(R.string.demo_pet_alone), onClick = vm::demoPetAlone, size = ButtonSize.Small, primary = false)
            }
        }

        Spacer(Modifier.height(PleosDims.space40))
        Text(stringResource(R.string.settings_sdk_status), style = PleosType.titleSmall, color = c.textPrimary, modifier = Modifier.padding(bottom = PleosDims.space8))
        StatusRow(stringResource(R.string.sdk_vehicle), d.vehicle, d.vehicleDetail)
        StatusRow(stringResource(R.string.sdk_navi), d.navi, state.navi.lastError)
        StatusRow(stringResource(R.string.sdk_tts), d.tts, d.ttsDetail)
        StatusRow(stringResource(R.string.sdk_location), d.location, null, divider = false)
        Text(
            stringResource(R.string.settings_about, BuildConfig.PLEOS_VEHICLE_SDK, BuildConfig.PLEOS_NAVI_SDK, BuildConfig.PLEOS_TTS_SDK),
            style = PleosType.labelMedium, color = c.textQuaternary, modifier = Modifier.padding(top = PleosDims.space16),
        )
        Text(stringResource(R.string.voice_hint), style = PleosType.labelMedium, color = c.textQuaternary, modifier = Modifier.padding(top = PleosDims.space8))

    }
}

@Composable
private fun StatusRow(name: String, status: SdkStatus, detail: String?, divider: Boolean = true) {
    val c = Pleos.colors
    val (icon, tint) = when (status) {
        SdkStatus.READY -> R.drawable.ic_check_circle to c.textPrimary
        SdkStatus.CONNECTING -> R.drawable.ic_schedule to c.textTertiary
        SdkStatus.UNAVAILABLE -> R.drawable.ic_cancel to c.negative
        SdkStatus.UNKNOWN -> R.drawable.ic_info to c.textQuaternary
    }
    PleosListRow(
        title = "$name  ·  " + stringResource(status.labelRes),
        subtitle = detail?.takeIf { status == SdkStatus.UNAVAILABLE }?.take(120), icon = icon, iconTint = tint, divider = divider,
    )
}
