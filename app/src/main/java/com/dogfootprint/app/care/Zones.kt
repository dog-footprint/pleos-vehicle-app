package com.dogfootprint.app.care

import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import com.dogfootprint.app.data.DogSeat

/** The Pleos areas that matter for the dog's riding position. */
data class DogZone(
    /** Seat zone used for HVAC current/target temperature and fan. */
    val tempSeat: SeatValue,
    /** Seat used for occupancy and belt state; null when the dog rides in the cargo area. */
    val occupancySeat: SeatValue?,
    val windows: List<WindowValue>,
    val doors: List<DoorValue>,
    val inRow2: Boolean,
)

fun DogSeat.zone(): DogZone = when (this) {
    DogSeat.ROW_2_LEFT -> DogZone(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_LEFT, listOf(WindowValue.ROW_2_LEFT), listOf(DoorValue.ROW_2_LEFT), inRow2 = true)
    DogSeat.ROW_2_RIGHT -> DogZone(SeatValue.ROW_2_RIGHT, SeatValue.ROW_2_RIGHT, listOf(WindowValue.ROW_2_RIGHT), listOf(DoorValue.ROW_2_RIGHT), inRow2 = true)
    DogSeat.ROW_2_CENTER -> DogZone(
        SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER,
        listOf(WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT), listOf(DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT), inRow2 = true,
    )
    DogSeat.ROW_1_RIGHT -> DogZone(SeatValue.ROW_1_RIGHT, SeatValue.ROW_1_RIGHT, listOf(WindowValue.ROW_1_RIGHT), listOf(DoorValue.ROW_1_RIGHT), inRow2 = false)
    DogSeat.CARGO -> DogZone(
        SeatValue.ROW_2_LEFT, null,
        listOf(WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT), listOf(DoorValue.REAR), inRow2 = true,
    )
}

/** Zone the emulator/vehicle always reports; used when the dog's seat zone has no HVAC sensor. */
val FALLBACK_TEMP_SEAT: SeatValue = SeatValue.ROW_1_LEFT
