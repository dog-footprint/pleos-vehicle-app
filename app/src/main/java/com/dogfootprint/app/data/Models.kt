package com.dogfootprint.app.data

import java.util.Calendar
import kotlinx.serialization.Serializable

/** Weight bands follow the AAHA size categories used for life-stage thresholds. */
enum class DogSize { TOY, SMALL, MEDIUM, LARGE, GIANT }

enum class CoatType { SHORT, DOUBLE, LONG_SINGLE, HAIRLESS }

/** AAHA 2019 life stages collapsed to what changes care: puppy (< 9 months), adult, senior (last 25 % of lifespan). */
enum class AgeGroup { PUPPY, ADULT, SENIOR }

enum class Tendency { NONE, MILD, SEVERE }

/** Simplified body-condition score; "overweight" carries Hall 2020's OR 1.42 for heat illness. */
enum class BodyCondition { LEAN, IDEAL, OVERWEIGHT }

/** Restraint in the car (UK Highway Code Rule 57 / 도로교통법 제39조). */
enum class Restraint { HARNESS, CRATE, NONE }

/** Where the dog usually rides. Maps to Pleos seat/window/door areas in `care/Zones.kt`. */
enum class DogSeat { ROW_2_LEFT, ROW_2_RIGHT, ROW_2_CENTER, ROW_1_RIGHT, CARGO }

enum class Mood { HAPPY, OKAY, STRESSED }

enum class EventType {
    TRIP_START, TRIP_END,
    BREAK_SUGGESTED, BREAK_TAKEN,
    BRAKE_BURST, SPORT_MODE, WINDOW_OPEN, BELT_UNBUCKLED, DOOR_OPEN_AT_STOP, TEMP_OUT_OF_BAND,
    CLIMATE_APPLIED, CALM_ON, CALM_OFF, CHECK_IN,
}

/** What "진정 모드" plays — both backed by kennel studies (see docs/CARE_RULES.md §6). */
enum class CalmMode { MUSIC, STORY }

@Serializable
data class DogProfile(
    val id: String,
    val name: String,
    val breedKey: String,
    val size: DogSize,
    val coat: CoatType,
    val brachycephalic: Boolean = false,
    /** Manual life stage; overridden by [birthYear]/[birthMonth] when known. */
    val ageGroup: AgeGroup = AgeGroup.ADULT,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val weightKg: Float? = null,
    val bodyCondition: BodyCondition = BodyCondition.IDEAL,
    /** Respiratory or cardiac disease: greatest odds of environmental heat illness (Hall 2020b). */
    val respiratoryOrHeartIssue: Boolean = false,
    val motionSickness: Tendency = Tendency.NONE,
    val restraint: Restraint = Restraint.HARNESS,
    val seat: DogSeat = DogSeat.ROW_2_LEFT,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Age in months from the birth date, if known. */
    fun ageMonths(nowMs: Long = System.currentTimeMillis()): Int? {
        val y = birthYear ?: return null
        val m = birthMonth ?: 6
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        return ((now.get(Calendar.YEAR) - y) * 12 + (now.get(Calendar.MONTH) + 1 - m)).coerceAtLeast(0)
    }

    /**
     * AAHA 2019: puppy until 6–9 months; senior = last 25 % of expected lifespan, i.e. about
     * 10 y (toy/small), 9 y (medium), 7 y (large), 6 y (giant).
     */
    fun lifeStage(nowMs: Long = System.currentTimeMillis()): AgeGroup {
        val months = ageMonths(nowMs) ?: return ageGroup
        val seniorYears = when (size) {
            DogSize.TOY, DogSize.SMALL -> 10
            DogSize.MEDIUM -> 9
            DogSize.LARGE -> 7
            DogSize.GIANT -> 6
        }
        return when {
            months < 9 -> AgeGroup.PUPPY
            months >= seniorYears * 12 -> AgeGroup.SENIOR
            else -> AgeGroup.ADULT
        }
    }
}

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
    val calmVolume: Float = 0.35f,
    val calmMode: CalmMode = CalmMode.MUSIC,
)

@Serializable
data class AppData(
    val dogs: List<DogProfile> = emptyList(),
    val activeDogId: String? = null,
    /** Dog currently on board (set from the Home toggle); drives trip auto-start. */
    val aboardDogId: String? = null,
    /** When the dog last ate, entered at boarding; drives the motion-sickness notice. */
    val lastMealAt: Long? = null,
    val trips: List<Trip> = emptyList(),
    val settings: Settings = Settings(),
) {
    val activeDog: DogProfile? get() = dogs.firstOrNull { it.id == activeDogId } ?: dogs.firstOrNull()
}
