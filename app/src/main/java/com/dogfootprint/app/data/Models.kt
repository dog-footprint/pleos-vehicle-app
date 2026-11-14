package com.dogfootprint.app.data

import kotlinx.serialization.Serializable

enum class DogSize { TOY, SMALL, MEDIUM, LARGE }

enum class CoatType { SHORT, DOUBLE, LONG_SINGLE, HAIRLESS }

enum class AgeGroup { PUPPY, ADULT, SENIOR }

enum class Tendency { NONE, MILD, SEVERE }

/** Where the dog usually rides. Maps to Pleos seat/window/door areas in `care/Zones.kt`. */
enum class DogSeat { ROW_2_LEFT, ROW_2_RIGHT, ROW_2_CENTER, ROW_1_RIGHT, CARGO }

enum class Mood { HAPPY, OKAY, STRESSED }

enum class EventType {
    TRIP_START, TRIP_END,
    BREAK_SUGGESTED, BREAK_TAKEN,
    BRAKE_BURST, SPORT_MODE, WINDOW_OPEN, BELT_UNBUCKLED, DOOR_OPEN_AT_STOP, TEMP_OUT_OF_BAND,
    CLIMATE_APPLIED, CALM_ON, CALM_OFF, CHECK_IN,
}

@Serializable
data class DogProfile(
    val id: String,
    val name: String,
    val breedKey: String,
    val size: DogSize,
    val coat: CoatType,
    val brachycephalic: Boolean = false,
    val ageGroup: AgeGroup = AgeGroup.ADULT,
    val weightKg: Float? = null,
    val motionSickness: Tendency = Tendency.NONE,
    val seat: DogSeat = DogSeat.ROW_2_LEFT,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class TripEvent(val at: Long, val type: EventType, val detail: String? = null)

@Serializable
data class Trip(
    val id: String,
    val dogId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val startKm: Float,
    val endKm: Float? = null,
    val drivingSec: Int = 0,
    val breaks: Int = 0,
    val startSoc: Int? = null,
    val endSoc: Int? = null,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val destinationName: String? = null,
    val comfortScore: Int = 100,
    val events: List<TripEvent> = emptyList(),
    val mood: Mood? = null,
    val note: String? = null,
) {
    val distanceKm: Float get() = ((endKm ?: startKm) - startKm).coerceAtLeast(0f)
    val isActive: Boolean get() = endedAt == null
}

@Serializable
data class Settings(
    /** Experimental: drive Vehicle Control APIs (not yet available in our Pleos project). */
    val controlEnabled: Boolean = false,
    val pleosTts: Boolean = true,
    val calmVolume: Float = 0.5f,
)

@Serializable
data class AppData(
    val dogs: List<DogProfile> = emptyList(),
    val activeDogId: String? = null,
    /** Dog currently on board (set from the Home toggle); drives trip auto-start. */
    val aboardDogId: String? = null,
    val trips: List<Trip> = emptyList(),
    val settings: Settings = Settings(),
) {
    val activeDog: DogProfile? get() = dogs.firstOrNull { it.id == activeDogId } ?: dogs.firstOrNull()
}
