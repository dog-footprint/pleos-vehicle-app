package com.dogfootprint.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dogfootprint.app.R
import com.dogfootprint.app.care.BreakPhase
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.care.ClimateVerdict
import com.dogfootprint.app.data.EventType
import com.dogfootprint.app.pleos.ApplyResult
import com.dogfootprint.app.ui.pleos.ButtonSize
import com.dogfootprint.app.ui.pleos.Pleos
import com.dogfootprint.app.ui.pleos.PleosButton
import com.dogfootprint.app.ui.pleos.PleosDims
import com.dogfootprint.app.ui.pleos.PleosDivider
import com.dogfootprint.app.ui.pleos.PleosIcon
import com.dogfootprint.app.ui.pleos.PleosStatusLine
import com.dogfootprint.app.ui.pleos.PleosToggleButton
import com.dogfootprint.app.ui.pleos.PleosType

/**
 * "지금": the one glanceable fact is the temperature at the dog's seat plus a one-word verdict.
 * Driver side (left) carries the value and the 1–2 actions; the right column carries secondary status.
 * Three modes: parked, driving (fewer rows, no editing), pet alone (driver left the dog in the car).
 */
@Composable
fun HomeScreen(vm: CareViewModel, state: CareState, onCreateProfile: () -> Unit) {
    val c = Pleos.colors
    val dog = state.dog
    if (dog == null) {
        Column(Modifier.fillMaxSize().padding(PleosDims.space40), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.home_no_dog_title), style = PleosType.titleLarge, color = c.textPrimary)
            Text(stringResource(R.string.home_no_dog_body), style = PleosType.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(top = PleosDims.space12, bottom = PleosDims.space32))
            PleosButton(stringResource(R.string.home_create_profile), onClick = onCreateProfile, size = ButtonSize.Large)
        }
        return
    }
    val driving = state.moving

    Row(Modifier.fillMaxSize().padding(start = PleosDims.space40, end = PleosDims.space40, top = PleosDims.space32, bottom = PleosDims.space24)) {
        Column(Modifier.weight(1.25f).fillMaxHeight()) {
            DogLine(vm, state, driving)
            Spacer(Modifier.weight(1f))
            if (state.petAlone) PetAloneBlock(vm, state) else TemperatureBlock(vm, state)
            Spacer(Modifier.weight(1f))
            Actions(vm, state, driving)
        }
        Box(Modifier.width(PleosDims.dividerWidth).fillMaxHeight().padding(vertical = PleosDims.space8).background(c.divider))
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(start = PleosDims.space40).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PleosDims.space24),
        ) {
            StatusRows(vm, state, driving)
            PleosDivider()
            SafetyRows(state)
            if (!driving || state.trip != null) {
                PleosDivider()
                TripRows(vm, state, driving)
            }
        }
    }
}

@Composable
private fun DogLine(vm: CareViewModel, state: CareState, driving: Boolean) {
    val c = Pleos.colors
    val dog = state.dog ?: return
    val facts = listOfNotNull(breedName(dog.breedKey), dog.weightKg?.let { stringResource(R.string.weight_fmt, it) }, stringResource(dog.seat.labelRes))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(dog.name, style = PleosType.titleLarge, color = c.textPrimary)
            Text(facts.joinToString(" · "), style = PleosType.bodyMedium, color = c.textTertiary)
        }
        PleosToggleButton(
            stringResource(R.string.home_aboard), on = state.aboard, onToggle = vm::setAboard,
            size = if (driving) ButtonSize.Small else ButtonSize.Medium, icon = R.drawable.ic_pets,
        )
    }
}

@Composable
private fun TemperatureBlock(vm: CareViewModel, state: CareState) {
    val c = Pleos.colors
    val dog = state.dog ?: return
    val plan = state.climate
    val verdict = plan?.verdict ?: ClimateVerdict.UNKNOWN
    val (word, tint, icon) = when (verdict) {
        ClimateVerdict.OK -> Triple(R.string.verdict_ok, c.accent, R.drawable.ic_check_circle)
        ClimateVerdict.TOO_HOT -> Triple(R.string.verdict_hot, c.negative, R.drawable.ic_sunny)
        ClimateVerdict.TOO_COLD -> Triple(R.string.verdict_cold, c.negative, R.drawable.ic_ac_unit)
        ClimateVerdict.HEAT_ALERT -> Triple(R.string.verdict_heat, c.negative, R.drawable.ic_error)
        ClimateVerdict.HVAC_OFF -> Triple(R.string.verdict_hvac_off, c.negative, R.drawable.ic_mode_fan)
        ClimateVerdict.UNKNOWN -> Triple(R.string.verdict_unknown, c.textTertiary, R.drawable.ic_thermostat)
    }
    Text(stringResource(R.string.temp_zone_fmt, stringResource(dog.seat.labelRes)) + if (state.vehicle.zoneTempFallback) " " + stringResource(R.string.zone_fallback_note) else "", style = PleosType.labelMedium, color = c.textTertiary)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(state.vehicle.zoneTemp?.let { "%.1f°".format(it) } ?: "—", style = PleosType.drivingValue, color = c.textPrimary)
        Spacer(Modifier.width(PleosDims.space32))
        Row(Modifier.padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            PleosIcon(icon, tint)
            Spacer(Modifier.width(PleosDims.space12))
            Text(stringResource(word), style = PleosType.titleLarge, color = tint)
        }
    }
    state.band?.let { band ->
        Text(
            stringResource(R.string.tile_temp_band_fmt, band.lower, band.upper) +
                (state.vehicle.targetTemp?.let { "  ·  " + stringResource(R.string.tile_temp_target_fmt, it, state.vehicle.fanSpeed?.toString() ?: "—") } ?: ""),
            style = PleosType.bodyMedium, color = c.textTertiary,
        )
    }
    if (plan != null && plan.needsAction) {
        Text(vm.engine.climateText(dog, plan), style = PleosType.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(top = PleosDims.space16))
    }
}

@Composable
private fun PetAloneBlock(vm: CareViewModel, state: CareState) {
    val c = Pleos.colors
    val dog = state.dog ?: return
    val v = state.vehicle
    val elapsedSec = state.petAloneSince?.let { ((System.currentTimeMillis() - it) / 1000).toInt() } ?: 0
    Text(stringResource(R.string.pet_alone_title_fmt, dog.name), style = PleosType.titleLarge, color = c.textPrimary)
    Text(stringResource(R.string.pet_alone_elapsed_fmt, durationText(elapsedSec)), style = PleosType.bodyMedium, color = c.textTertiary)
    Spacer(Modifier.height(PleosDims.space24))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(v.zoneTemp?.let { "%.1f°".format(it) } ?: "—", style = PleosType.drivingValue, color = c.textPrimary)
        Spacer(Modifier.width(PleosDims.space32))
        Text(stringResource(R.string.temp_zone_fmt, stringResource(dog.seat.labelRes)), style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.padding(bottom = 30.dp))
    }
    val hvacOff = v.hvacPower == false
    PleosStatusLine(
        icon = if (hvacOff) R.drawable.ic_warning else R.drawable.ic_mode_fan,
        text = if (hvacOff) stringResource(R.string.pet_alone_hvac_off) else stringResource(R.string.pet_alone_hvac_on_fmt, v.targetTemp ?: 0f),
        tint = if (hvacOff) c.negative else c.accent, style = PleosType.bodyLargeStrong,
        modifier = Modifier.padding(top = PleosDims.space16),
    )
    Text(stringResource(R.string.pet_alone_hint), style = PleosType.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space8))
}

@Composable
private fun Actions(vm: CareViewModel, state: CareState, driving: Boolean) {
    val c = Pleos.colors
    val plan = state.climate
    val size = if (driving) ButtonSize.Large else ButtonSize.Medium
    Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space16), verticalAlignment = Alignment.CenterVertically) {
        if (state.petAlone && state.vehicle.hvacPower == false && state.data.settings.controlEnabled) {
            PleosButton(stringResource(R.string.pet_alone_turn_on), onClick = vm::applyClimate, size = size, icon = R.drawable.ic_mode_fan, enabled = !state.applying)
        } else if (plan?.needsAction == true) {
            PleosButton(stringResource(R.string.tile_temp_apply), onClick = vm::applyClimate, size = size, icon = R.drawable.ic_mode_fan, enabled = !state.applying)
        }
        if (state.breakPhase == BreakPhase.DUE) {
            PleosButton(stringResource(R.string.tile_break_done), onClick = vm::markBreakTaken, size = size, primary = false, icon = R.drawable.ic_local_cafe)
        } else {
            PleosToggleButton(stringResource(R.string.tile_calm_title), on = state.calmOn, onToggle = { vm.toggleCalm() }, size = size, icon = R.drawable.ic_graphic_eq)
        }
    }
    val apply = state.lastApply
    if (!driving && apply != null) {
        val text = when (apply) {
            is ApplyResult.Applied -> stringResource(R.string.apply_result_applied, apply.changes.joinToString())
            is ApplyResult.Partial -> stringResource(R.string.apply_result_partial, apply.changes.joinToString(), apply.errors.joinToString())
            is ApplyResult.NotApplied -> when (apply.reason) {
                ApplyResult.Reason.CONTROL_NOT_ENABLED -> stringResource(R.string.apply_result_not_enabled)
                ApplyResult.Reason.NOTHING_TO_DO -> stringResource(R.string.apply_result_nothing)
                ApplyResult.Reason.SDK_ERROR -> stringResource(R.string.apply_result_error)
            }
        }
        Text(text, style = PleosType.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space12))
    } else if (plan?.needsAction == true && !state.data.settings.controlEnabled && !driving) {
        Text(stringResource(R.string.tile_temp_apply_disabled), style = PleosType.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space12))
    }
}

@Composable
private fun StatusRows(vm: CareViewModel, state: CareState, driving: Boolean) {
    val c = Pleos.colors
    val trip = state.trip
    Column(verticalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
        PleosStatusLine(
            icon = if (state.moving) R.drawable.ic_directions_car else R.drawable.ic_local_parking,
            text = if (state.moving) stringResource(R.string.status_driving_fmt, clockText(trip?.drivingSec ?: 0)) else stringResource(R.string.status_parked),
        )
        state.vehicle.soc?.let { PleosStatusLine(R.drawable.ic_battery_charging_full, stringResource(R.string.soc_fmt, it), tint = c.textSecondary) }
        if (state.petAlone) return@Column
        val interval = state.breakIntervalMin
        when {
            trip == null || interval == null -> PleosStatusLine(R.drawable.ic_schedule, stringResource(R.string.tile_break_no_trip), tint = c.textTertiary)
            state.breakPhase == BreakPhase.DUE -> PleosStatusLine(R.drawable.ic_local_cafe, stringResource(R.string.tile_break_due), tint = c.negative, style = PleosType.bodyLargeStrong)
            else -> {
                val remaining = (interval * 60 - state.drivenSinceBreakSec).coerceAtLeast(0)
                PleosStatusLine(R.drawable.ic_schedule, stringResource(R.string.break_in_fmt, clockText(remaining)), tint = if (state.breakPhase == BreakPhase.PRE_ALERT) c.negative else c.textPrimary)
            }
        }
        val n = state.navi
        if (n.destinationName != null && n.remainTimeSec != null) {
            PleosStatusLine(R.drawable.ic_route, stringResource(R.string.tile_navi_fmt, n.destinationName, durationText(n.remainTimeSec), (n.remainDistanceM ?: 0) / 1000f), tint = c.textSecondary)
        }
        n.restAreaAheadKm?.let { PleosStatusLine(R.drawable.ic_local_cafe, stringResource(R.string.tile_navi_rest_area_fmt, it), tint = if (state.restAreaSuggested) c.accent else c.textSecondary) }
    }
}

@Composable
private fun SafetyRows(state: CareState) {
    val c = Pleos.colors
    Column(verticalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
        if (state.safety.isEmpty()) {
            PleosStatusLine(R.drawable.ic_health_and_safety, stringResource(R.string.safety_all_ok), tint = c.textSecondary)
        } else {
            state.safety.forEach { f -> PleosStatusLine(f.iconRes, stringResource(f.labelRes), tint = c.negative, style = PleosType.bodyLargeStrong) }
        }
    }
}

@Composable
private fun TripRows(vm: CareViewModel, state: CareState, driving: Boolean) {
    val c = Pleos.colors
    val trip = state.trip
    Column(verticalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
        if (trip != null) {
            val counts = trip.events.groupingBy { it.type }.eachCount()
            PleosStatusLine(R.drawable.ic_footprint, stringResource(R.string.trip_line_fmt, trip.distanceKm, durationText(trip.drivingSec), trip.comfortScore))
            Text(
                stringResource(R.string.trip_reason_fmt, counts[EventType.BRAKE_BURST] ?: 0, counts[EventType.WINDOW_OPEN] ?: 0, counts[EventType.BELT_UNBUCKLED] ?: 0),
                style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.padding(start = 56.dp),
            )
            if (!driving) {
                Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
                    PleosButton(stringResource(R.string.tile_trip_end), onClick = { vm.dismissTripEndPrompt(); showMoodDialog.value = true }, size = ButtonSize.Small, primary = false)
                }
            }
        } else {
            val last = state.dogTrips.maxByOrNull { it.startedAt }
            PleosStatusLine(R.drawable.ic_footprint, stringResource(R.string.home_total_km_fmt, state.totalKm, state.dogTrips.size), tint = c.textSecondary)
            Text(
                last?.let { stringResource(R.string.last_trip_fmt, dateText(it.startedAt)) } ?: stringResource(R.string.no_trips_yet),
                style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.padding(start = 56.dp),
            )
            if (!driving) PleosButton(stringResource(R.string.tile_trip_start), onClick = vm::startTrip, size = ButtonSize.Small, primary = false)
        }
    }
}
