package com.dogfootprint.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dogfootprint.app.R
import com.dogfootprint.app.care.BreedPresets
import com.dogfootprint.app.data.AgeGroup
import com.dogfootprint.app.data.CoatType
import com.dogfootprint.app.data.DogSeat
import com.dogfootprint.app.data.DogSize
import com.dogfootprint.app.data.EventType
import com.dogfootprint.app.data.Mood
import com.dogfootprint.app.data.Tendency
import com.dogfootprint.app.pleos.SdkStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun isKorean(): Boolean = Locale.getDefault().language == "ko"

fun breedName(key: String): String = BreedPresets.byKey(key).let { if (isKorean()) it.nameKo else it.nameEn }

@Composable
fun durationText(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) stringResource(R.string.duration_h_m, h, m) else stringResource(R.string.duration_m, m)
}

fun clockText(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun dateText(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(epochMs))

fun timeText(epochMs: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epochMs))

val DogSize.labelRes: Int
    get() = when (this) {
        DogSize.TOY -> R.string.size_toy
        DogSize.SMALL -> R.string.size_small
        DogSize.MEDIUM -> R.string.size_medium
        DogSize.LARGE -> R.string.size_large
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
