package com.dogfootprint.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dogfootprint.app.R
import com.dogfootprint.app.care.BreakPhase
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.care.ClimateVerdict
import com.dogfootprint.app.care.SafetyFlag
import com.dogfootprint.app.pleos.ApplyResult

@Composable
fun HomeScreen(vm: CareViewModel, state: CareState, onCreateProfile: () -> Unit) {
    val dog = state.dog
    if (dog == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyDogCard(onCreateProfile) }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp)) {
        DogHeader(vm, state)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TempTile(vm, state)
                SafetyTile(state)
                CalmTile(vm, state)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BreakTile(vm, state)
                TripTile(vm, state)
                ScoreTile(state)
            }
        }
    }
}

@Composable
private fun DogHeader(vm: CareViewModel, state: CareState) {
    val dog = state.dog ?: return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🐕", style = MaterialTheme.typography.displaySmall)
            Column(Modifier.weight(1f).padding(start = 20.dp)) {
                Text(dog.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${breedName(dog.breedKey)} · ${stringResource(R.string.home_seat_fmt, stringResource(dog.seat.labelRes))}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(stringResource(R.string.home_total_km_fmt, state.totalKm, state.dogTrips.size), style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (state.moving) StatusColors.good else Color.Gray)
                    Text(
                        stringResource(if (state.moving) R.string.moving else R.string.parked),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp),
                    )
                    state.vehicle.soc?.let { Text(" · " + stringResource(R.string.soc_fmt, it), style = MaterialTheme.typography.bodyMedium) }
                }
                // Whole row toggles: a bigger target for a moving car (and for scripted demos).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp).clickable { vm.setAboard(!state.aboard) },
                ) {
                    Text(stringResource(if (state.aboard) R.string.home_aboard else R.string.home_not_aboard), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = state.aboard, onCheckedChange = vm::setAboard, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun Tile(title: String, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                accent?.let { StatusDot(it); Spacer(Modifier.size(10.dp)) }
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StatusDot(color: Color) = Box(Modifier.size(14.dp).background(color, CircleShape))

@Composable
private fun TempTile(vm: CareViewModel, state: CareState) {
    val dog = state.dog ?: return
    val plan = state.climate
    val accent = when (plan?.verdict) {
        ClimateVerdict.OK -> StatusColors.good
        ClimateVerdict.TOO_HOT, ClimateVerdict.TOO_COLD, ClimateVerdict.HVAC_OFF -> StatusColors.warn
        ClimateVerdict.HEAT_ALERT -> StatusColors.bad
        else -> Color.Gray
    }
    Tile(stringResource(R.string.tile_temp_title) + " · " + stringResource(dog.seat.labelRes), accent) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.vehicle.zoneTemp?.let { "%.1f°C".format(it) } ?: "—", style = MaterialTheme.typography.displaySmall, color = accent)
            Column(Modifier.padding(start = 20.dp, bottom = 8.dp)) {
                state.band?.let { Text(stringResource(R.string.tile_temp_band_fmt, it.lower, it.upper), style = MaterialTheme.typography.bodyLarge) }
                val v = state.vehicle
                if (v.targetTemp != null) {
                    Text(stringResource(R.string.tile_temp_target_fmt, v.targetTemp, v.fanSpeed?.toString() ?: "—"), style = MaterialTheme.typography.bodyMedium)
                }
                if (v.zoneTempFallback) Text(stringResource(R.string.zone_fallback_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (plan != null) {
            Text(vm.engine.climateText(dog, plan), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
        }
        if (plan?.needsAction == true) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Button(onClick = vm::applyClimate, enabled = !state.applying) { Text(stringResource(R.string.tile_temp_apply)) }
                if (!state.data.settings.controlEnabled) {
                    Text(stringResource(R.string.tile_temp_apply_disabled), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
        state.lastApply?.let { r ->
            val text = when (r) {
                is ApplyResult.Applied -> stringResource(R.string.apply_result_applied, r.changes.joinToString())
                is ApplyResult.Partial -> stringResource(R.string.apply_result_partial, r.changes.joinToString(), r.errors.joinToString())
                is ApplyResult.NotApplied -> when (r.reason) {
                    ApplyResult.Reason.CONTROL_NOT_ENABLED -> stringResource(R.string.apply_result_not_enabled)
                    ApplyResult.Reason.NOTHING_TO_DO -> stringResource(R.string.apply_result_nothing)
                    ApplyResult.Reason.SDK_ERROR -> stringResource(R.string.apply_result_error)
                }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun BreakTile(vm: CareViewModel, state: CareState) {
    val accent = when (state.breakPhase) {
        BreakPhase.NONE -> if (state.trip != null) StatusColors.good else Color.Gray
        BreakPhase.PRE_ALERT -> StatusColors.warn
        BreakPhase.DUE -> StatusColors.bad
    }
    Tile(stringResource(R.string.tile_break_title), accent) {
        val interval = state.breakIntervalMin
        if (state.trip == null || interval == null) {
            Text(stringResource(R.string.tile_break_no_trip), style = MaterialTheme.typography.bodyLarge)
        } else {
            val remaining = (interval * 60 - state.drivenSinceBreakSec).coerceAtLeast(0)
            Text(
                if (state.breakPhase == BreakPhase.DUE) stringResource(R.string.tile_break_due) else stringResource(R.string.tile_break_countdown_fmt, clockText(remaining)),
                style = MaterialTheme.typography.headlineMedium, color = accent,
            )
            LinearProgressIndicator(
                progress = { (state.drivenSinceBreakSec.toFloat() / (interval * 60)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Text(stringResource(R.string.tile_break_interval_fmt, interval), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = vm::markBreakTaken, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.tile_break_done)) }
        }
        val n = state.navi
        Text(
            if (n.destinationName != null && n.remainTimeSec != null) {
                stringResource(R.string.tile_navi_fmt, n.destinationName, durationText(n.remainTimeSec), (n.remainDistanceM ?: 0) / 1000f)
            } else {
                stringResource(R.string.tile_navi_none)
            },
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp),
        )
        n.restAreaAheadKm?.let {
            AssistChip(
                onClick = {}, label = { Text(stringResource(R.string.tile_navi_rest_area_fmt, it)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = if (state.restAreaSuggested) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun SafetyTile(state: CareState) {
    val flags = state.safety
    Tile(stringResource(R.string.tile_safety_title), if (flags.isEmpty()) StatusColors.good else StatusColors.warn) {
        if (flags.isEmpty()) {
            Text(stringResource(R.string.safety_ok), style = MaterialTheme.typography.bodyLarge)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                flags.forEach { f ->
                    val res = when (f) {
                        SafetyFlag.WINDOW_OPEN -> R.string.safety_window
                        SafetyFlag.BELT_UNBUCKLED -> R.string.safety_belt
                        SafetyFlag.DOOR_OPEN -> R.string.safety_door
                        SafetyFlag.SPORT_MODE -> R.string.safety_sport
                        SafetyFlag.HVAC_OFF -> R.string.safety_hvac_off
                        SafetyFlag.NOT_ON_SEAT -> R.string.safety_not_on_seat
                    }
                    AssistChip(
                        onClick = {}, label = { Text(stringResource(res)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    )
                }
            }
        }
        val v = state.vehicle
        val details = listOfNotNull(
            v.occupancy?.let { if (it) "🪑 ✓" else "🪑 ✗" },
            v.beltBuckled?.let { if (it) "🦺 ✓" else "🦺 ✗" },
            v.windowPosition?.let { "🪟 $it" },
            v.drivingMode?.let { "🚗 ${it.name}" },
        )
        if (details.isNotEmpty()) Text(details.joinToString("   "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ScoreTile(state: CareState) {
    val score = state.trip?.comfortScore ?: 100
    val accent = when { score >= 80 -> StatusColors.good; score >= 50 -> StatusColors.warn; else -> StatusColors.bad }
    Tile(stringResource(R.string.tile_score_title), accent) {
        Text("$score", style = MaterialTheme.typography.displaySmall, color = accent)
        LinearProgressIndicator(progress = { score / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = accent)
    }
}

@Composable
private fun CalmTile(vm: CareViewModel, state: CareState) {
    Tile(stringResource(R.string.tile_calm_title), if (state.calmOn) StatusColors.good else Color.Gray) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.toggleCalm() }) {
            Text(stringResource(R.string.tile_calm_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.calmOn, onCheckedChange = { vm.toggleCalm() })
        }
        Slider(
            value = state.data.settings.calmVolume, onValueChange = vm::setCalmVolume,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun TripTile(vm: CareViewModel, state: CareState) {
    val trip = state.trip
    Tile(stringResource(R.string.tile_trip_title), if (trip != null) StatusColors.good else Color.Gray) {
        if (trip == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tile_trip_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = vm::startTrip) { Text(stringResource(R.string.tile_trip_start)) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(clockText(trip.drivingSec), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Button(onClick = { vm.engine.dismissTripEndPrompt(); showMoodDialog.value = true }) { Text(stringResource(R.string.tile_trip_end)) }
            }
            Text(stringResource(R.string.tile_trip_stats_fmt, trip.distanceKm, durationText(trip.drivingSec), trip.breaks), style = MaterialTheme.typography.bodyMedium)
            trip.destinationName?.let { Text(stringResource(R.string.footprint_destination_fmt, it), style = MaterialTheme.typography.bodyMedium) }
            trip.events.takeLast(4).reversed().forEach { e ->
                Text("${timeText(e.at)}  ${stringResource(e.type.labelRes)}${e.detail?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
