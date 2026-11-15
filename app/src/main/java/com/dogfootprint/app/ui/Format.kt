package com.dogfootprint.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dogfootprint.app.R
import com.dogfootprint.app.care.AirDirection
import com.dogfootprint.app.care.Alert
import com.dogfootprint.app.care.AlertKind
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.care.ClimateAction
import com.dogfootprint.app.care.alertMessageRes
import com.dogfootprint.app.care.wholeMinutes
import com.dogfootprint.app.care.BreedPresets
import com.dogfootprint.app.care.HEAVY_DOG_KG
import com.dogfootprint.app.care.SafetyFlag
import com.dogfootprint.app.data.AgeGroup
import com.dogfootprint.app.data.BodyCondition
import com.dogfootprint.app.data.CoatType
import com.dogfootprint.app.data.DogProfile
import com.dogfootprint.app.data.DogSeat
import com.dogfootprint.app.data.DogSize
import com.dogfootprint.app.data.EventType
import com.dogfootprint.app.data.Mood
import com.dogfootprint.app.data.Restraint
import com.dogfootprint.app.data.Tendency
import com.dogfootprint.app.pleos.Change
import com.dogfootprint.app.pleos.SdkStatus
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun isKorean(): Boolean = Locale.getDefault().language == "ko"

fun breedName(key: String): String = if (key.isBlank()) "—" else BreedPresets.byKey(key).let { if (isKorean()) it.nameKo else it.nameEn }

/**
 * Breed line-art for the dog's picture (SVG Repo outline set, CC0; sources and look-alikes in
 * scripts/breed-icons/sources.txt). Unknown or blank keys get the generic dog, like [BreedPresets.byKey].
 */
@DrawableRes
fun breedIconRes(key: String): Int = when (BreedPresets.byKey(key).key) {
    "maltese" -> R.drawable.breed_maltese
    "poodle" -> R.drawable.breed_poodle
    "pomeranian" -> R.drawable.breed_pomeranian
    "chihuahua" -> R.drawable.breed_chihuahua
    "bichon" -> R.drawable.breed_bichon
    "shih_tzu" -> R.drawable.breed_shih_tzu
    "pug" -> R.drawable.breed_pug
    "french_bulldog" -> R.drawable.breed_french_bulldog
    "boston_terrier" -> R.drawable.breed_boston_terrier
    "dachshund" -> R.drawable.breed_dachshund
    "yorkshire" -> R.drawable.breed_yorkshire
    "spitz" -> R.drawable.breed_spitz
    "corgi" -> R.drawable.breed_corgi
    "shiba" -> R.drawable.breed_shiba
    "jindo" -> R.drawable.breed_jindo
    "border_collie" -> R.drawable.breed_border_collie
    "beagle" -> R.drawable.breed_beagle
    "cocker" -> R.drawable.breed_cocker
    "golden" -> R.drawable.breed_golden
    "labrador" -> R.drawable.breed_labrador
    "husky" -> R.drawable.breed_husky
    "samoyed" -> R.drawable.breed_samoyed
    "malamute" -> R.drawable.breed_malamute
    "shepherd" -> R.drawable.breed_shepherd
    "greyhound" -> R.drawable.breed_greyhound
    else -> R.drawable.breed_other
}

/** Every duration the driver reads ("1시간 18분", "3분") — same rounding everywhere (ComfortRules.wholeMinutes). */
@Composable
fun durationText(sec: Int): String = minutesText(sec)

fun clockText(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun dateText(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(epochMs))

fun timeText(epochMs: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epochMs))

/** "11월 12일" / "Nov 12" — for one-line summaries. */
fun shortDateText(epochMs: Long): String = SimpleDateFormat(if (isKorean()) "M월 d일" else "MMM d", Locale.getDefault()).format(Date(epochMs))

/** Button label for the one climate action (ComfortRules.climateAction). */
@Composable
fun climateActionLabel(action: ClimateAction): String = when (action) {
    ClimateAction.TurnOn -> stringResource(R.string.climate_turn_on)
    is ClimateAction.CoolTo -> stringResource(R.string.climate_cool_to_fmt, action.target)
    is ClimateAction.HeatTo -> stringResource(R.string.climate_heat_to_fmt, action.target)
}

/** Release builds (no Vehicle Control): what the driver should do instead. */
@Composable
fun climateActionGuide(action: ClimateAction): String = when (action) {
    ClimateAction.TurnOn -> stringResource(R.string.climate_guide_turn_on)
    is ClimateAction.CoolTo -> stringResource(R.string.climate_guide_cool_fmt, action.target)
    is ClimateAction.HeatTo -> stringResource(R.string.climate_guide_heat_fmt, action.target)
}

/** Integer degrees for Home and pet-alone ("19°"); decimals only in the Footprints detail and debug. */
fun tempInt(t: Float?): String = t?.let { "${it.roundToInt()}°" } ?: "—"

/** "1시간 30분" / "15분" — whole minutes, never a ticking clock (driving rule). */
@Composable
fun minutesText(sec: Int): String {
    val m = wholeMinutes(sec)
    val h = m / 60
    val mm = m % 60
    return when {
        h > 0 && mm > 0 -> stringResource(R.string.hours_minutes_fmt, h, mm)
        h > 0 -> stringResource(R.string.hours_fmt, h)
        else -> stringResource(R.string.minutes_fmt, mm)
    }
}

/** Banner line: what to do, worded for the screen it is read on (care/AlertTexts). */
@Composable
fun alertText(alert: Alert, state: CareState): String {
    val res = alertMessageRes(alert.kind, alert.context)
    return if (alert.kind == AlertKind.MEAL) stringResource(res, state.data.lastMealAt?.let { ((System.currentTimeMillis() - it) / 60_000).toInt() } ?: 0)
    else stringResource(res)
}

val AlertKind.iconRes: Int
    get() = when (this) {
        AlertKind.HEAT -> R.drawable.ic_error
        AlertKind.HVAC_OFF, AlertKind.LEAVE_PREP -> R.drawable.ic_mode_fan
        AlertKind.WINDOW_OPEN -> R.drawable.ic_sensor_window
        AlertKind.DOOR_OPEN -> R.drawable.ic_door_open
        AlertKind.BELT -> R.drawable.ic_airline_seat_recline_normal
        AlertKind.MEAL -> R.drawable.ic_schedule
        AlertKind.BREAK_DUE -> R.drawable.ic_local_cafe
    }

/** Timeline icon per event; danger events get the warning colour in the Footprints detail. */
val EventType.iconRes: Int
    get() = when (this) {
        EventType.TRIP_START -> R.drawable.ic_route
        EventType.TRIP_END -> R.drawable.ic_local_parking
        EventType.BREAK_SUGGESTED, EventType.BREAK_TAKEN -> R.drawable.ic_local_cafe
        EventType.BRAKE_BURST -> R.drawable.ic_error
        EventType.SPORT_MODE -> R.drawable.ic_speed
        EventType.WINDOW_OPEN -> R.drawable.ic_sensor_window
        EventType.BELT_UNBUCKLED -> R.drawable.ic_airline_seat_recline_normal
        EventType.DOOR_OPEN_AT_STOP -> R.drawable.ic_door_open
        EventType.TEMP_OUT_OF_BAND -> R.drawable.ic_thermostat
        EventType.CLIMATE_APPLIED -> R.drawable.ic_mode_fan
        EventType.CALM_ON, EventType.CALM_OFF -> R.drawable.ic_graphic_eq
        EventType.CHECK_IN -> R.drawable.ic_sentiment_satisfied
    }

val EventType.isDanger: Boolean
    get() = this == EventType.BRAKE_BURST || this == EventType.WINDOW_OPEN || this == EventType.TEMP_OUT_OF_BAND ||
        this == EventType.DOOR_OPEN_AT_STOP || this == EventType.SPORT_MODE

/** "성견 · 3년 2개월" — life stage plus age when the birth date is known. */
@Composable
fun lifeStageText(dog: DogProfile): String {
    val stage = stringResource(dog.lifeStage().labelRes)
    val months = dog.ageMonths() ?: return stage
    return stringResource(R.string.life_stage_fmt, stage, stringResource(R.string.age_years_months_fmt, months / 12, months % 12))
}

/** Heat-sensitive traits the sources flag (docs/CARE_RULES.md §1), for the profile and Home lines. */
@Composable
fun heatRiskLabels(dog: DogProfile): List<String> = buildList {
    if (dog.brachycephalic) add(stringResource(R.string.risk_brachy))
    if (dog.respiratoryOrHeartIssue) add(stringResource(R.string.risk_resp))
    if (dog.bodyCondition == BodyCondition.OVERWEIGHT) add(stringResource(R.string.risk_overweight))
    if ((dog.weightKg ?: 0f) >= HEAVY_DOG_KG) add(stringResource(R.string.risk_heavy))
    if (dog.coat == CoatType.DOUBLE || dog.coat == CoatType.LONG_SINGLE) add(stringResource(R.string.risk_thick_coat))
    when (dog.lifeStage()) {
        AgeGroup.PUPPY -> add(stringResource(R.string.risk_puppy))
        AgeGroup.SENIOR -> add(stringResource(R.string.risk_senior))
        AgeGroup.ADULT -> Unit
    }
}

/** User wording for one applied climate change ("19°C", "바닥 송풍"), never the SDK property names. */
@Composable
fun changeText(change: Change): String = when (change) {
    is Change.Temperature -> stringResource(R.string.change_temp_fmt, change.celsius)
    is Change.FanSpeed -> stringResource(R.string.change_fan_fmt, change.level)
    is Change.Direction -> stringResource(
        when (change.direction) {
            AirDirection.FACE -> R.string.change_dir_face
            AirDirection.FLOOR -> R.string.change_dir_floor
            AirDirection.FACE_AND_FLOOR -> R.string.change_dir_both
        },
    )
    Change.Power -> stringResource(R.string.change_power)
    is Change.Window -> stringResource(R.string.change_window)
}

val DogSize.labelRes: Int
    get() = when (this) {
        DogSize.TOY -> R.string.size_toy
        DogSize.SMALL -> R.string.size_small
        DogSize.MEDIUM -> R.string.size_medium
        DogSize.LARGE -> R.string.size_large
        DogSize.GIANT -> R.string.size_giant
    }

val CoatType.labelRes: Int
    get() = when (this) {
        CoatType.SHORT -> R.string.coat_short
        CoatType.DOUBLE -> R.string.coat_double
        CoatType.LONG_SINGLE -> R.string.coat_long_single
        CoatType.HAIRLESS -> R.string.coat_hairless
    }

val AgeGroup.labelRes: Int
    get() = when (this) {
        AgeGroup.PUPPY -> R.string.age_puppy
        AgeGroup.ADULT -> R.string.age_adult
        AgeGroup.SENIOR -> R.string.age_senior
    }

val Tendency.labelRes: Int
    get() = when (this) {
        Tendency.NONE -> R.string.motion_none
        Tendency.MILD -> R.string.motion_mild
        Tendency.SEVERE -> R.string.motion_severe
    }

val BodyCondition.labelRes: Int
    get() = when (this) {
        BodyCondition.LEAN -> R.string.body_lean
        BodyCondition.IDEAL -> R.string.body_ideal
        BodyCondition.OVERWEIGHT -> R.string.body_overweight
    }

val Restraint.labelRes: Int
    get() = when (this) {
        Restraint.HARNESS -> R.string.restraint_harness
        Restraint.CRATE -> R.string.restraint_crate
        Restraint.NONE -> R.string.restraint_none
    }

val DogSeat.labelRes: Int
    get() = when (this) {
        DogSeat.ROW_2_LEFT -> R.string.zone_row2_left
        DogSeat.ROW_2_RIGHT -> R.string.zone_row2_right
        DogSeat.ROW_2_CENTER -> R.string.zone_row2_center
        DogSeat.ROW_1_RIGHT -> R.string.zone_row1_right
        DogSeat.CARGO -> R.string.zone_cargo
    }

val Mood.labelRes: Int
    get() = when (this) {
        Mood.HAPPY -> R.string.mood_happy
        Mood.OKAY -> R.string.mood_okay
        Mood.STRESSED -> R.string.mood_stressed
    }

val Mood.iconRes: Int
    get() = when (this) {
        Mood.HAPPY -> R.drawable.ic_sentiment_satisfied
        Mood.OKAY -> R.drawable.ic_sentiment_neutral
        Mood.STRESSED -> R.drawable.ic_sentiment_dissatisfied
    }

val SafetyFlag.labelRes: Int
    get() = when (this) {
        SafetyFlag.WINDOW_OPEN -> R.string.safety_window
        SafetyFlag.BELT_UNBUCKLED -> R.string.safety_belt
        SafetyFlag.DOOR_OPEN -> R.string.safety_door
        SafetyFlag.SPORT_MODE -> R.string.safety_sport
        SafetyFlag.HVAC_OFF -> R.string.safety_hvac_off
        SafetyFlag.NOT_ON_SEAT -> R.string.safety_not_on_seat
    }

val SafetyFlag.iconRes: Int
    get() = when (this) {
        SafetyFlag.WINDOW_OPEN -> R.drawable.ic_sensor_window
        SafetyFlag.BELT_UNBUCKLED -> R.drawable.ic_airline_seat_recline_normal
        SafetyFlag.DOOR_OPEN -> R.drawable.ic_door_open
        SafetyFlag.SPORT_MODE -> R.drawable.ic_speed
        SafetyFlag.HVAC_OFF -> R.drawable.ic_mode_fan
        SafetyFlag.NOT_ON_SEAT -> R.drawable.ic_pets
    }

val EventType.labelRes: Int
    get() = when (this) {
        EventType.TRIP_START -> R.string.event_trip_start
        EventType.TRIP_END -> R.string.event_trip_end
        EventType.BREAK_SUGGESTED -> R.string.event_break_suggested
        EventType.BREAK_TAKEN -> R.string.event_break_taken
        EventType.BRAKE_BURST -> R.string.event_brake_burst
        EventType.SPORT_MODE -> R.string.event_sport_mode
        EventType.WINDOW_OPEN -> R.string.event_window_open
        EventType.BELT_UNBUCKLED -> R.string.event_belt_unbuckled
        EventType.DOOR_OPEN_AT_STOP -> R.string.event_door_open_at_stop
        EventType.TEMP_OUT_OF_BAND -> R.string.event_temp_out_of_band
        EventType.CLIMATE_APPLIED -> R.string.event_climate_applied
        EventType.CALM_ON -> R.string.event_calm_on
        EventType.CALM_OFF -> R.string.event_calm_off
        EventType.CHECK_IN -> R.string.event_check_in
    }

val SdkStatus.labelRes: Int
    get() = when (this) {
        SdkStatus.UNKNOWN -> R.string.status_unknown
        SdkStatus.CONNECTING -> R.string.status_connecting
        SdkStatus.READY -> R.string.status_ready
        SdkStatus.UNAVAILABLE -> R.string.status_unavailable
    }
