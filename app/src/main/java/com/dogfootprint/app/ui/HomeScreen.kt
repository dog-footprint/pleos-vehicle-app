package com.dogfootprint.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dogfootprint.app.R
import com.dogfootprint.app.care.BreakPhase
import com.dogfootprint.app.care.AlertLevel
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.care.climateAction
import com.dogfootprint.app.care.ClimateVerdict
import com.dogfootprint.app.care.SafetyFlag
import com.dogfootprint.app.care.bandDelta
import com.dogfootprint.app.data.CalmMode
import com.dogfootprint.app.data.DogSeat
import com.dogfootprint.app.data.EventType
import com.dogfootprint.app.data.Mood
import com.dogfootprint.app.ui.pleos.ButtonSize
import com.dogfootprint.app.ui.pleos.Pleos
import com.dogfootprint.app.ui.pleos.PleosButton
import com.dogfootprint.app.ui.pleos.PleosDims
import com.dogfootprint.app.ui.pleos.PleosDivider
import com.dogfootprint.app.ui.pleos.PleosIcon
import com.dogfootprint.app.ui.pleos.PleosStatusLine
import com.dogfootprint.app.ui.pleos.PleosToggleButton
import com.dogfootprint.app.ui.pleos.PleosType
import kotlin.math.roundToInt

/**
 * "지금" — one screen per state:
 *  - ParkedHome: dog line, seat temperature, pre-departure checklist, "태우기" (the only user action)
 *  - DrivingHome: number · status line · (warning slot above) · ≤ 2 buttons, nothing that ticks
 *  - PreExitHome: parked, dog on board, driver's door open — "are you leaving the dog?" with the one action
 *  - PetAloneHome: the record while the dog was alone and the way out when the driver is back
 *  - TripEndCheckIn: summary line and a one-tap mood
 * The driver sits on the left, so the main button is bottom-left. Temperatures are whole degrees, times are minutes.
 * Colour: accent only for selection / the primary action; negative only for danger. Verdicts differ by icon,
 * size and weight, not by hue.
 */
@Composable
fun HomeScreen(vm: CareViewModel, state: CareState, compact: Boolean, onCreateProfile: () -> Unit, onOpenTrip: (String) -> Unit) {
    val c = Pleos.colors
    if (state.dog == null) {
        Column(Modifier.fillMaxSize().padding(PleosDims.space40), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.home_no_dog_title), style = PleosType.titleLarge, color = c.textPrimary)
            Text(stringResource(R.string.home_no_dog_body), style = PleosType.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(top = PleosDims.space12, bottom = PleosDims.space32))
            PleosButton(stringResource(R.string.home_create_profile), onClick = onCreateProfile, size = ButtonSize.Large)
        }
        return
    }
    when {
        state.petAlone -> PetAloneHome(vm, state)
        state.preExit -> PreExitHome(vm, state)
        state.moving -> DrivingHome(vm, state, compact)
        state.pendingCheckIn != null -> TripEndCheckIn(vm, state, onOpenTrip)
        else -> ParkedHome(vm, state, compact)
    }
}

private val screenPadding = Modifier.padding(start = PleosDims.space40, end = PleosDims.space40, top = PleosDims.space24, bottom = PleosDims.space24)

/** The one primary action fills the driver-side column, capped at half the compact breakpoint (no new constant). */
private val primaryWidth = Modifier.fillMaxWidth().widthIn(max = PleosDims.compactMaxWidth / 2)

// ---------------------------------------------------------------- shared pieces

private data class VerdictLook(val word: Int, val icon: Int, val tint: Color, val style: TextStyle)

/** OK is small and quiet; cold/hot are big and plain; heatstroke and climate-off are big and red. */
@Composable
private fun verdictLook(verdict: ClimateVerdict): VerdictLook {
    val c = Pleos.colors
    return when (verdict) {
        ClimateVerdict.OK -> VerdictLook(R.string.verdict_ok, R.drawable.ic_check_circle, c.textSecondary, PleosType.titleSmall)
        ClimateVerdict.TOO_HOT -> VerdictLook(R.string.verdict_hot, R.drawable.ic_sunny, c.textPrimary, PleosType.titleLarge)
        ClimateVerdict.TOO_COLD -> VerdictLook(R.string.verdict_cold, R.drawable.ic_ac_unit, c.textPrimary, PleosType.titleLarge)
        ClimateVerdict.HEAT_ALERT -> VerdictLook(R.string.verdict_heat, R.drawable.ic_error, c.negative, PleosType.titleLarge)
        ClimateVerdict.HVAC_OFF -> VerdictLook(R.string.verdict_hvac_off, R.drawable.ic_mode_fan, c.negative, PleosType.titleLarge)
        ClimateVerdict.UNKNOWN -> VerdictLook(R.string.verdict_unknown, R.drawable.ic_thermostat, c.textTertiary, PleosType.titleSmall)
    }
}

/** Seat temperature as the one number, the verdict beside it, band + set-point under that. */
@Composable
private fun TempHero(state: CareState, showDelta: Boolean, showBand: Boolean = true) {
    val c = Pleos.colors
    val dog = state.dog ?: return
    val plan = state.climate
    val look = verdictLook(plan?.verdict ?: ClimateVerdict.UNKNOWN)
    Text(
        stringResource(R.string.temp_zone_fmt, stringResource(dog.seat.labelRes)) + if (state.vehicle.zoneTempFallback) " " + stringResource(R.string.zone_fallback_note) else "",
        style = PleosType.labelMedium, color = c.textTertiary,
    )
    Row(verticalAlignment = Alignment.Bottom) {
        Text(tempInt(state.vehicle.zoneTemp), style = PleosType.drivingValue, color = c.textPrimary)
        Spacer(Modifier.width(PleosDims.space32))
        Row(Modifier.padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            PleosIcon(look.icon, look.tint)
            Spacer(Modifier.width(PleosDims.space12))
            Text(stringResource(look.word), style = look.style, color = look.tint)
        }
    }
    val band = state.band ?: return
    if (!showBand) return
    Text(
        stringResource(R.string.tile_temp_band_fmt, band.lower, band.upper) +
            (state.vehicle.targetTemp?.let { "  ·  " + stringResource(R.string.tile_temp_target_fmt, it, state.vehicle.fanSpeed?.toString() ?: "—") } ?: ""),
        style = PleosType.bodyLarge, color = c.textSecondary,
    )
    val temp = state.vehicle.zoneTemp
    if (showDelta && plan != null && plan.needsAction && temp != null) {
        val delta = bandDelta(band, temp)
        val text = when {
            delta < 0f -> stringResource(R.string.temp_delta_cold_fmt, band.lower, -delta)
            delta > 0f -> stringResource(R.string.temp_delta_hot_fmt, band.upper, delta)
            else -> null
        }
        text?.let { Text(it, style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space8)) }
    }
}

/** Climate power as one line; red only when it is off. */
@Composable
private fun HvacLine(state: CareState, style: TextStyle = PleosType.bodyLargeStrong, tint: Color? = null) {
    val c = Pleos.colors
    val off = state.vehicle.hvacPower == false
    PleosStatusLine(
        if (off) R.drawable.ic_warning else R.drawable.ic_mode_fan,
        if (off) stringResource(R.string.pet_alone_hvac_off_short2) else stringResource(R.string.pet_alone_hvac_on_fmt2, state.vehicle.targetTemp ?: 0f),
        tint = tint ?: if (off) c.negative else c.textPrimary, style = style,
    )
}

/** Colour of the cabin condition: danger red, caution plain, fine plain. */
@Composable
private fun levelTint(level: AlertLevel?): Color = if (level == AlertLevel.DANGER) Pleos.colors.negative else Pleos.colors.textPrimary

/** State and action apart: a status line while playing plus "정지"; otherwise the one "진정 사운드" button. */
@Composable
private fun CalmControl(vm: CareViewModel, state: CareState, size: ButtonSize) {
    val c = Pleos.colors
    if (state.calmOn) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
            PleosStatusLine(R.drawable.ic_graphic_eq, stringResource(if (state.data.settings.calmMode == CalmMode.STORY) R.string.calm_status_story else R.string.calm_status_music), tint = c.textSecondary)
            PleosButton(stringResource(R.string.calm_stop), onClick = { vm.toggleCalm() }, size = size, primary = false, icon = R.drawable.ic_stop)
        }
    } else {
        PleosButton(stringResource(R.string.tile_calm_title), onClick = { vm.toggleCalm() }, size = size, primary = false, icon = R.drawable.ic_graphic_eq)
    }
}

@Composable
private fun ApplyButton(vm: CareViewModel, state: CareState, size: ButtonSize, primary: Boolean, modifier: Modifier = Modifier) {
    if (state.climate?.needsAction == true) {
        PleosButton(stringResource(R.string.tile_temp_apply), onClick = vm::applyClimate, size = size, primary = primary, icon = R.drawable.ic_mode_fan, enabled = !state.applying, modifier = modifier)
    }
}

// ---------------------------------------------------------------- A. parked

@Composable
private fun ParkedHome(vm: CareViewModel, state: CareState, compact: Boolean) {
    val c = Pleos.colors
    val dog = state.dog ?: return
    val beltToDo = state.aboard && SafetyFlag.BELT_UNBUCKLED in state.safety
    val header: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PleosIcon(R.drawable.ic_pets, c.textPrimary)
            Spacer(Modifier.width(PleosDims.space16))
            Column(Modifier.weight(1f)) {
                Text(dog.name, style = PleosType.titleLarge, color = c.textPrimary)
                Text(breedName(dog.breedKey) + " · " + stringResource(dog.seat.labelRes), style = PleosType.bodyMedium, color = c.textTertiary)
            }
            // Boarding state next to the name, as a title, never as small text at the bottom (3rd review D3).
            if (state.aboard) PleosStatusLine(R.drawable.ic_check_circle, stringResource(R.string.home_aboard_fmt, dog.name), tint = c.textPrimary, style = PleosType.titleSmall)
        }
    }
    val actions: @Composable () -> Unit = {
        if (!state.aboard) {
            PleosButton(stringResource(R.string.home_board_fmt, dog.name), onClick = { showMealDialog.value = true }, size = ButtonSize.Large, icon = R.drawable.ic_pets, modifier = primaryWidth)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space16), verticalAlignment = Alignment.CenterVertically) {
                ApplyButton(vm, state, ButtonSize.Medium, primary = true)
                CalmControl(vm, state, ButtonSize.Medium)
                PleosButton(stringResource(R.string.home_unboard), onClick = vm::unboard, size = ButtonSize.Medium, primary = false)
            }
        }
        if (state.climate?.needsAction == true && !state.data.settings.controlEnabled) {
            Text(stringResource(R.string.tile_temp_apply_disabled), style = PleosType.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space8))
        }
    }
    val side: @Composable () -> Unit = {
        Checklist(state)
        PleosDivider(Modifier.padding(vertical = PleosDims.space24))
        FootprintsLine(state)
    }
    val body: @Composable () -> Unit = {
        TempHero(state, showDelta = true)
        // After boarding, the harness is the one thing to do before leaving: say it in the body too (3rd review D2).
        if (beltToDo) PleosStatusLine(R.drawable.ic_warning, stringResource(R.string.harness_before_departure), tint = c.textPrimary, style = PleosType.bodyLargeStrong, modifier = Modifier.padding(top = PleosDims.space24))
    }

    if (compact) {
        Column(Modifier.fillMaxSize().then(screenPadding).verticalScroll(rememberScrollState())) {
            header(); Spacer(Modifier.height(PleosDims.space24))
            body(); Spacer(Modifier.height(PleosDims.space32))
            side(); Spacer(Modifier.height(PleosDims.space32))
            actions()
        }
    } else {
        Row(Modifier.fillMaxSize().then(screenPadding)) {
            Column(Modifier.weight(1.2f).fillMaxHeight()) {
                header(); Spacer(Modifier.height(PleosDims.space32))
                body()
                Spacer(Modifier.weight(1f))
                actions()
            }
            Spacer(Modifier.width(PleosDims.space40))
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { side() }
        }
    }
}

/** Window · harness · door — neutral before boarding: done = check, pending = empty circle (3rd review D1). */
@Composable
private fun Checklist(state: CareState) {
    val c = Pleos.colors
    val v = state.vehicle
    Text(stringResource(R.string.check_title), style = PleosType.bodyMediumStrong, color = c.textTertiary, modifier = Modifier.padding(bottom = PleosDims.space12))
    @Composable
    fun line(label: Int, ok: Boolean?, okText: Int, badText: Int) {
        val (icon, stateRes) = when (ok) {
            true -> R.drawable.ic_check_circle to okText
            false -> R.drawable.ic_radio_button_unchecked to badText
            null -> R.drawable.ic_info to R.string.check_unknown
        }
        PleosStatusLine(icon, stringResource(label) + " · " + stringResource(stateRes), tint = if (ok == null) c.textTertiary else c.textSecondary, modifier = Modifier.padding(vertical = PleosDims.space8))
    }
    line(R.string.check_window, v.windowPosition?.let { it == 0 }, R.string.check_closed, R.string.check_open)
    line(R.string.check_harness, v.beltBuckled, R.string.check_worn, R.string.check_not_worn)
    line(R.string.check_door, v.doorOpen?.let { !it }, R.string.check_closed, R.string.check_open)
}

@Composable
private fun FootprintsLine(state: CareState) {
    val c = Pleos.colors
    val last = state.dogTrips.maxByOrNull { it.startedAt }
    val text = if (last != null) stringResource(R.string.home_footprints_last_fmt, state.totalKm, state.dogTrips.size, shortDateText(last.startedAt))
    else stringResource(R.string.home_footprints_fmt, state.totalKm, state.dogTrips.size)
    PleosStatusLine(R.drawable.ic_footprint, text, tint = c.textSecondary)
}

// ---------------------------------------------------------------- B. driving

@Composable
private fun DrivingHome(vm: CareViewModel, state: CareState, compact: Boolean) {
    val c = Pleos.colors
    val trip = state.trip
    val interval = state.breakIntervalMin
    val status = when {
        !state.aboard -> stringResource(R.string.home_not_aboard_driving)
        trip == null || interval == null -> stringResource(R.string.tile_break_no_trip)
        state.breakPhase == BreakPhase.DUE -> stringResource(R.string.break_now)
        else -> {
            val remaining = (interval * 60 - state.drivenSinceBreakSec).coerceAtLeast(0)
            if (state.breakPhase == BreakPhase.PRE_ALERT) stringResource(R.string.break_soon_fmt, (remaining + 59) / 60)
            else stringResource(R.string.break_next_fmt, minutesText(remaining))
        }
    }
    Column(Modifier.fillMaxSize().then(screenPadding)) {
        Spacer(Modifier.weight(1f))
        // Number · verdict · band · next break read as one group; the seat map beside it says which seat (wide window only).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {   // intrinsic width, so the seat map sits right beside the block (space24), not at the far edge
                TempHero(state, showDelta = false)
                PleosStatusLine(
                    if (state.breakPhase == BreakPhase.DUE) R.drawable.ic_local_cafe else R.drawable.ic_schedule, status,
                    tint = c.textPrimary, modifier = Modifier.padding(top = PleosDims.space8),
                )
            }
            if (!compact) { Spacer(Modifier.width(PleosDims.space24)); SeatMap(state) }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(PleosDims.space40))
        Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space16), verticalAlignment = Alignment.CenterVertically) {
            ApplyButton(vm, state, ButtonSize.Large, primary = true)
            if (state.breakPhase == BreakPhase.DUE) {
                PleosButton(stringResource(R.string.tile_break_done), onClick = vm::markBreakTaken, size = ButtonSize.Large, primary = false, icon = R.drawable.ic_local_cafe)
            } else {
                CalmControl(vm, state, ButtonSize.Large)
            }
        }
    }
}

/**
 * Top-view seats inside a thin car outline, no numbers: shows which seat the reading belongs to. The dog's
 * seat is an outline in textSecondary (never the brightest thing on screen); filled negative only when the
 * verdict is a danger. A front-seat sensor fallback marks the front-left seat instead and says so.
 */
@Composable
private fun SeatMap(state: CareState) {
    val c = Pleos.colors
    val dog = state.dog ?: return
    val danger = state.climate?.verdict == ClimateVerdict.HEAT_ALERT || state.climate?.verdict == ClimateVerdict.HVAC_OFF
    val fallback = state.vehicle.zoneTempFallback
    val highlighted: Set<DogSeat?> = if (fallback) setOf(null) else setOf(dog.seat)   // null = driver/front-left sensor
    val cell = PleosDims.iconButtonMedium   // 80
    @Composable
    fun seat(seat: DogSeat?, width: androidx.compose.ui.unit.Dp = cell) {
        val on = seat in highlighted
        Box(
            Modifier
                .size(width = width, height = cell)
                .clip(PleosDims.radiusSmall)
                .then(
                    when {
                        on && danger -> Modifier.background(c.negative)
                        on -> Modifier.border(PleosDims.space4, c.textSecondary, PleosDims.radiusSmall)
                        else -> Modifier.border(PleosDims.dividerWidth, c.divider, PleosDims.radiusSmall)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (on) PleosIcon(if (fallback) R.drawable.ic_thermostat else R.drawable.ic_pets, if (danger) c.onButtonFilled else c.textSecondary, size = PleosDims.iconSmall)
        }
    }
    // One car-shaped outline (narrow front, full-width body; GenericShape with the large radius token) around the
    // three rows, all centred on the same axis. Front inset = space32, the same amount the front row is narrower.
    val rearWidth = cell * 3 + PleosDims.space12 * 2
    val inset = PleosDims.space32
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.width(rearWidth + PleosDims.space24 * 2).border(PleosDims.dividerWidth, c.textSecondary, CarOutline(inset)).padding(PleosDims.space24),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PleosDims.space12),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space12 + inset)) { seat(null); seat(DogSeat.ROW_1_RIGHT) }
            Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space12)) { seat(DogSeat.ROW_2_LEFT); seat(DogSeat.ROW_2_CENTER); seat(DogSeat.ROW_2_RIGHT) }
            seat(DogSeat.CARGO, width = rearWidth)
        }
        if (fallback) Text(stringResource(R.string.seat_map_sensor_note), style = PleosType.labelMedium, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space8))
    }
}

/** Top view of a car: the front (top) edge is inset on both sides and rounded; the body is a rounded rectangle. */
private class CarOutline(private val frontInset: androidx.compose.ui.unit.Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 32.dp.toPx() }          // radius level9 (PleosDims.radiusLarge)
        val inset = with(density) { frontInset.toPx() }
        val front = size.height * 0.34f                 // where the body reaches full width (just behind the front row)
        val path = Path().apply {
            moveTo(inset + r, 0f)
            lineTo(size.width - inset - r, 0f)
            quadraticTo(size.width - inset, 0f, size.width - inset, r)
            quadraticTo(size.width - inset, front, size.width, front + r)
            lineTo(size.width, size.height - r)
            quadraticTo(size.width, size.height, size.width - r, size.height)
            lineTo(r, size.height)
            quadraticTo(0f, size.height, 0f, size.height - r)
            lineTo(0f, front + r)
            quadraticTo(inset, front, inset, r)
            quadraticTo(inset, 0f, inset + r, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

// ---------------------------------------------------------------- C1. leaving: driver's door open

/**
 * One line for range + climate, then the one action for the cause (ComfortRules.climateAction) — or, without
 * Vehicle Control, what the driver should do. Danger (outside the band / heading out) is red, a caution
 * (climate off but inside the band) is plain with the triangle.
 */
@Composable
private fun PreExitHome(vm: CareViewModel, state: CareState) {
    val c = Pleos.colors
    val dog = state.dog ?: return
    val v = state.vehicle
    val level = state.cabinLevel
    val action = climateAction(v.hvacPower, state.climate?.verdict, state.band)
    val tint = levelTint(level)
    val canAct = action != null && state.data.settings.controlEnabled
    Column(Modifier.fillMaxSize().then(screenPadding)) {
        Text(stringResource(R.string.pre_exit_title_fmt, dog.name), style = PleosType.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.weight(1f))
        TempHero(state, showDelta = false, showBand = false)
        state.band?.let { band ->
            if (v.hvacPower == false) {
                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.pre_exit_line_off_prefix_fmt, band.lower, band.upper))
                        withStyle(SpanStyle(color = tint, fontWeight = FontWeight.Bold)) { append(stringResource(R.string.pet_alone_hvac_off_short2)) }
                    },
                    style = PleosType.bodyLarge, color = c.textSecondary,
                )
            } else {
                Text(stringResource(R.string.pre_exit_line_fmt, band.lower, band.upper, v.targetTemp ?: 0f, v.fanSpeed?.toString() ?: "—"), style = PleosType.bodyLarge, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(PleosDims.space24))
        when {
            action == null -> PleosStatusLine(R.drawable.ic_check_circle, stringResource(R.string.pre_exit_ok), tint = c.textPrimary, style = PleosType.bodyLargeStrong)
            !canAct -> PleosStatusLine(if (level == AlertLevel.DANGER) R.drawable.ic_error else R.drawable.ic_warning, climateActionGuide(action), tint = tint, style = PleosType.bodyLargeStrong)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space16), verticalAlignment = Alignment.CenterVertically) {
            if (canAct) {
                PleosButton(climateActionLabel(action!!), onClick = vm::keepClimate, size = ButtonSize.Large, icon = R.drawable.ic_mode_fan, enabled = !state.applying, modifier = primaryWidth.then(Modifier.weight(1f, fill = false)))
            }
            PleosButton(stringResource(R.string.pre_exit_together_fmt, dog.name), onClick = vm::unboard, size = ButtonSize.Medium, primary = false)
        }
        if (canAct) Text(stringResource(R.string.pre_exit_keep_hint), style = PleosType.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = PleosDims.space8))
        Text(stringResource(R.string.pre_exit_limit), style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space16))
    }
}

// ---------------------------------------------------------------- C2. pet alone: the record, and the way out

@Composable
private fun PetAloneHome(vm: CareViewModel, state: CareState) {
    val c = Pleos.colors
    val dog = state.dog ?: return
    val v = state.vehicle
    val level = state.cabinLevel
    val danger = level == AlertLevel.DANGER
    val hvacOff = v.hvacPower == false
    val action = climateAction(v.hvacPower, state.climate?.verdict, state.band)
    val canAct = action != null && state.data.settings.controlEnabled
    val aloneMin = state.petAloneSince?.let { ((System.currentTimeMillis() - it) / 60_000).toInt() } ?: 0
    val temp = v.zoneTemp?.roundToInt() ?: 0
    val frame = if (danger) Modifier.padding(PleosDims.space8).border(PleosDims.space4, c.negative, PleosDims.radiusLarge) else Modifier

    Column(Modifier.fillMaxSize().then(frame).then(screenPadding)) {
        Text(stringResource(R.string.pet_alone_title_fmt, dog.name), style = PleosType.headlineSmall, color = c.textPrimary)
        // The record first, large, right under the title — what the returning driver wants to know.
        Text(
            if (aloneMin < 1) stringResource(R.string.pet_alone_summary_now)
            else stringResource(R.string.pet_alone_summary_fmt, aloneMin, state.petAloneMaxTemp?.roundToInt() ?: temp),
            style = PleosType.titleMedium, color = c.textPrimary, modifier = Modifier.padding(top = PleosDims.space8),
        )
        Spacer(Modifier.height(PleosDims.space32))
        Text(stringResource(R.string.temp_zone_fmt, stringResource(dog.seat.labelRes)), style = PleosType.labelMedium, color = c.textTertiary)
        Text(tempInt(v.zoneTemp), style = PleosType.drivingValue, color = if (danger && !hvacOff) c.negative else c.textPrimary)
        HvacLine(state, PleosType.titleSmall, tint = if (hvacOff) levelTint(level) else null)
        if (level != null) {
            // Parked wording only: what to do on return, never the driving line about a break.
            PleosStatusLine(
                if (danger) R.drawable.ic_error else R.drawable.ic_warning,
                when {
                    hvacOff && danger -> stringResource(R.string.pet_alone_risk_hvac_fmt, temp)
                    hvacOff -> stringResource(R.string.pet_alone_hvac_caution_fmt, temp)
                    else -> stringResource(R.string.pet_alone_risk_temp_fmt, temp)
                },
                tint = levelTint(level), style = PleosType.bodyLargeStrong, modifier = Modifier.padding(top = PleosDims.space24),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space16), verticalAlignment = Alignment.CenterVertically) {
            if (state.driverBack) {
                PleosButton(stringResource(R.string.home_unboard), onClick = vm::unboard, size = ButtonSize.Large, modifier = primaryWidth.then(Modifier.weight(1f, fill = false)))
            }
            if (canAct) {
                PleosButton(climateActionLabel(action!!), onClick = vm::keepClimate, size = if (state.driverBack) ButtonSize.Medium else ButtonSize.Large, primary = !state.driverBack, icon = R.drawable.ic_mode_fan, enabled = !state.applying)
            } else if (action != null) {
                PleosStatusLine(R.drawable.ic_info, stringResource(R.string.tile_temp_apply_disabled), tint = c.textTertiary)
            }
            CalmControl(vm, state, ButtonSize.Small)
        }
        Text(stringResource(R.string.pet_alone_back_hint), style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space16))
    }
}

// ---------------------------------------------------------------- D. trip just ended

/** Same numbers as ComfortRules.comfortScore, so the reasons line matches the score. */
private val penalties = listOf(
    EventType.BRAKE_BURST to 10, EventType.SPORT_MODE to 15, EventType.WINDOW_OPEN to 10, EventType.BELT_UNBUCKLED to 10, EventType.TEMP_OUT_OF_BAND to 5,
)

@Composable
private fun TripEndCheckIn(vm: CareViewModel, state: CareState, onOpenTrip: (String) -> Unit) {
    val c = Pleos.colors
    val dog = state.dog ?: return
    val trip = state.pendingCheckIn ?: return
    val counts = trip.events.groupingBy { it.type }.eachCount()
    val reasons = penalties.mapNotNull { (t, p) -> counts[t]?.takeIf { it > 0 }?.let { n -> stringResource(t.labelRes) + " −" + n * p } }
    Column(Modifier.fillMaxSize().then(screenPadding), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.trip_done_title_fmt, dog.name), style = PleosType.titleLarge, color = c.textPrimary)
        Text(
            stringResource(R.string.fp_summary_fmt, trip.distanceKm, minutesText(trip.drivingSec), trip.breaks),
            style = PleosType.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(top = PleosDims.space8),
        )
        Text(
            stringResource(R.string.fp_score_fmt, trip.comfortScore) + (reasons.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = " · ") ?: " · " + stringResource(R.string.fp_score_perfect)),
            style = PleosType.bodyMedium, color = c.textTertiary, modifier = Modifier.padding(top = PleosDims.space4),
        )
        Spacer(Modifier.height(PleosDims.space40))
        Text(
            if (trip.mood == null) stringResource(R.string.trip_done_mood_prompt_fmt, dog.name) else stringResource(R.string.trip_done_saved_fmt, stringResource(trip.mood.labelRes)),
            style = PleosType.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(bottom = PleosDims.space16),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
            Mood.entries.forEach { m ->
                PleosToggleButton(stringResource(m.labelRes), on = trip.mood == m, onToggle = { vm.checkIn(m) }, size = ButtonSize.Large, icon = m.iconRes)
            }
        }
        Spacer(Modifier.height(PleosDims.space40))
        Row(horizontalArrangement = Arrangement.spacedBy(PleosDims.space16)) {
            if (trip.mood != null) {
                PleosButton(stringResource(R.string.trip_done), onClick = vm::dismissCheckIn, size = ButtonSize.Medium)
                PleosButton(stringResource(R.string.trip_add_note), onClick = { showNoteDialog.value = true }, size = ButtonSize.Medium, primary = false)
            } else {
                PleosButton(stringResource(R.string.trip_skip), onClick = vm::dismissCheckIn, size = ButtonSize.Medium, primary = false)
            }
            PleosButton(stringResource(R.string.trip_view_detail), onClick = { vm.dismissCheckIn(); onOpenTrip(trip.id) }, size = ButtonSize.Medium, primary = false, icon = R.drawable.ic_footprint)
        }
    }
}
