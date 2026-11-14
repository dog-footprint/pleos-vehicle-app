package com.dogfootprint.app.care

import com.dogfootprint.app.data.AgeGroup
import com.dogfootprint.app.data.BodyCondition
import com.dogfootprint.app.data.CoatType
import com.dogfootprint.app.data.DogProfile
import com.dogfootprint.app.data.DogSize
import com.dogfootprint.app.data.EventType
import com.dogfootprint.app.data.Tendency
import kotlin.math.max
import kotlin.math.min

/*
 * Pure rules, no Android or SDK types, so they are unit-testable. Every constant traces to a line in
 * docs/CARE_RULES.md; "(ours)" marks interpolations between sourced values.
 */

// Comfort band for the cabin temperature at the dog's seat (°C). Base is ours, inside the NRC 2006
// thermoneutral zone (20–30 °C) and under the USDA 29.5 °C cap, biased cool because heat illness is
// recorded from 16.5 °C (median exertional case, Hall 2020) and from 24.1 °C in most dogs (Bruchim 2006).
const val BAND_BASE_LOWER = 18f
const val BAND_BASE_UPPER = 24f
const val BAND_MIN_WIDTH = 2f
const val BRACHY_MAX_UPPER = 21f           // owners report signs above ~19–21 °C (PMC10601647, Carter 2020)
const val THICK_COAT_OR_LARGE_UPPER = 22f  // thick coat / large body: Hall 2020 (Golden vs Labrador; ≥ 50 kg OR 3.42)
const val SENSITIVE_LOWER = 20f            // short-hair/hairless/toy: USDA 10 °C floor + short-hair LCT ~15 °C (NRC)
const val ABSOLUTE_LOWER = 10f             // USDA: ≥ 50 °F for sensitive dogs
const val ABSOLUTE_UPPER = 29.5f           // USDA: never above 85 °F for more than 4 h
const val HEAVY_DOG_KG = 50f               // Hall 2020: ≥ 50 kg OR 3.42 vs < 10 kg

// Heat alert (spoken, red)
const val HEAT_ALERT_BRACHY_TEMP = 24f     // Bruchim 2006: ≥ 24.1 °C sufficient for HRI in most dogs
const val HEAT_ALERT_ANY_TEMP = 27f        // (ours) well inside the USDA 29.5 °C cap

// Break reminders (minutes of driving). Guidance: stop every 2–3 h; (ours) shorter for the groups
// the sources flag — puppies/seniors (USDA, AAHA), motion-sick dogs (TVN), heat alerts.
const val BREAK_ADULT_MIN = 120
const val BREAK_PUPPY_MIN = 90
const val BREAK_SENIOR_MIN = 90
const val BREAK_MOTION_MILD_PENALTY = 30
const val BREAK_MOTION_SEVERE_PENALTY = 60
const val BREAK_HEAT_ALERT_MAX = 60
const val BREAK_FLOOR_MIN = 45
const val BREAK_PREALERT_MIN = 15
const val REST_AREA_SUGGEST_MAX_KM = 10.0
const val REST_AREA_SUGGEST_MIN_PROGRESS = 0.6
const val REST_AREA_SUPPRESS_REMAIN_SEC = 20 * 60

// Meal timing before travel: ASPCA "light meal 3–4 h before departure", TVN "not within a couple of hours".
const val MEAL_WARN_MIN = 120
const val MEAL_NOTICE_MIN = 240

// Stress / safety thresholds (ours)
const val BRAKE_BURST_WINDOW_MS = 10_000L
const val BRAKE_BURST_THRESHOLD = 4
const val BRAKE_BURST_COOLDOWN_MS = 60_000L
const val WINDOW_OPEN_WARN_FRACTION = 0.30f
const val WINDOW_OPEN_CLEAR_FRACTION = 0.20f
const val TEMP_OUT_OF_BAND_GRACE_MS = 60_000L
const val TEMP_OUT_OF_BAND_COOLDOWN_MS = 10 * 60_000L

// Motion detection
const val MOTION_DELTA_WINDOW_MS = 10_000L
const val MOTION_PARKED_AFTER_MS = 60_000L

// Trip life-cycle
const val BREAK_TAKEN_PARKED_MS = 5 * 60_000L
const val TRIP_END_PROMPT_PARKED_MS = 10 * 60_000L

data class ComfortBand(val lower: Float, val upper: Float) {
    fun contains(t: Float) = t in lower..upper
}

/** True for the traits the sources associate with heat intolerance. */
fun isHeatSensitive(p: DogProfile, stage: AgeGroup = p.lifeStage()): Boolean =
    p.brachycephalic || p.respiratoryOrHeartIssue || p.bodyCondition == BodyCondition.OVERWEIGHT ||
        (p.weightKg ?: 0f) >= HEAVY_DOG_KG || stage != AgeGroup.ADULT

fun comfortBand(p: DogProfile, stage: AgeGroup = p.lifeStage()): ComfortBand {
    var lower = BAND_BASE_LOWER
    var upper = BAND_BASE_UPPER
    val thickCoat = p.coat == CoatType.DOUBLE || p.coat == CoatType.LONG_SINGLE
    val big = p.size == DogSize.LARGE || p.size == DogSize.GIANT
    if (thickCoat || big) upper = min(upper, THICK_COAT_OR_LARGE_UPPER)
    if (p.brachycephalic) upper = min(upper, BRACHY_MAX_UPPER)
    if (p.bodyCondition == BodyCondition.OVERWEIGHT) upper -= 1f
    if ((p.weightKg ?: 0f) >= HEAVY_DOG_KG) upper -= 1f
    if (p.respiratoryOrHeartIssue) upper -= 1f
    val coldSensitive = p.coat == CoatType.SHORT || p.coat == CoatType.HAIRLESS || p.size == DogSize.TOY
    if (coldSensitive) lower = max(lower, SENSITIVE_LOWER)
    if (stage != AgeGroup.ADULT) { lower += 1f; upper -= 1f }   // elderly and < 6 months are sensitive both ways (USDA)
    lower = max(lower, ABSOLUTE_LOWER)
    upper = min(upper, ABSOLUTE_UPPER)
    if (upper - lower < BAND_MIN_WIDTH) lower = upper - BAND_MIN_WIDTH
    return ComfortBand(lower, upper)
}

fun isHeatAlert(p: DogProfile, zoneTemp: Float?): Boolean {
    if (zoneTemp == null) return false
    val threshold = if (p.brachycephalic || p.respiratoryOrHeartIssue) HEAT_ALERT_BRACHY_TEMP else HEAT_ALERT_ANY_TEMP
    return zoneTemp >= threshold
}

enum class ClimateVerdict { UNKNOWN, OK, TOO_HOT, TOO_COLD, HEAT_ALERT, HVAC_OFF }

/** Airflow we would like; mapped to the SDK's FanDirectionType by the actuator. */
enum class AirDirection { FACE, FLOOR, FACE_AND_FLOOR }

data class ClimatePlan(
    val verdict: ClimateVerdict,
    val targetTemp: Float? = null,
    val fanSpeed: Int? = null,
    val direction: AirDirection? = null,
) {
    val needsAction: Boolean get() = verdict != ClimateVerdict.OK && verdict != ClimateVerdict.UNKNOWN
}

fun climatePlan(
    p: DogProfile,
    band: ComfortBand,
    zoneTemp: Float?,
    hvacPower: Boolean?,
    fanSpeed: Int?,
    fanMax: Int?,
    moving: Boolean,
): ClimatePlan {
    if (zoneTemp == null) return ClimatePlan(ClimateVerdict.UNKNOWN)
    val heat = isHeatAlert(p, zoneTemp)
    val tooHot = zoneTemp > band.upper
    val tooCold = zoneTemp < band.lower
    if (hvacPower == false && moving && (tooHot || tooCold || heat)) {
        return ClimatePlan(ClimateVerdict.HVAC_OFF, targetTemp = if (tooCold) band.lower + 1f else band.upper - 1f)
    }
    val row2Dir = { hot: Boolean -> if (hot) AirDirection.FACE_AND_FLOOR else AirDirection.FLOOR }
    val boostedFan = fanSpeed?.let { f -> fanMax?.let { min(f + 1, it) } ?: (f + 1) }
    return when {
        heat -> ClimatePlan(ClimateVerdict.HEAT_ALERT, band.upper - 1f, boostedFan ?: 3, row2Dir(true))
        tooHot -> ClimatePlan(ClimateVerdict.TOO_HOT, band.upper - 1f, boostedFan, row2Dir(true))
        tooCold -> ClimatePlan(ClimateVerdict.TOO_COLD, band.lower + 1f, null, row2Dir(false))
        else -> ClimatePlan(ClimateVerdict.OK)
    }
}

fun breakIntervalMin(p: DogProfile, heatAlert: Boolean, stage: AgeGroup = p.lifeStage()): Int {
    var m = when (stage) {
        AgeGroup.PUPPY -> BREAK_PUPPY_MIN
        AgeGroup.SENIOR -> BREAK_SENIOR_MIN
        AgeGroup.ADULT -> BREAK_ADULT_MIN
    }
    m -= when (p.motionSickness) {
        Tendency.NONE -> 0
        Tendency.MILD -> BREAK_MOTION_MILD_PENALTY
        Tendency.SEVERE -> BREAK_MOTION_SEVERE_PENALTY
    }
    if (heatAlert) m = min(m, BREAK_HEAT_ALERT_MAX)
    return max(m, BREAK_FLOOR_MIN)
}

enum class BreakPhase { NONE, PRE_ALERT, DUE }

fun breakPhase(drivenSinceBreakSec: Int, intervalMin: Int): BreakPhase = when {
    drivenSinceBreakSec >= intervalMin * 60 -> BreakPhase.DUE
    drivenSinceBreakSec >= (intervalMin - BREAK_PREALERT_MIN) * 60 -> BreakPhase.PRE_ALERT
    else -> BreakPhase.NONE
}

enum class MealNotice { NONE, NOTICE, WARN }

/**
 * Motion-sickness notice from the last meal: within 2 h → warn (any dog); within 4 h → notice for dogs
 * with a motion-sickness history or puppies (the most affected group).
 */
fun mealNotice(lastMealAt: Long?, nowMs: Long, p: DogProfile, stage: AgeGroup = p.lifeStage()): MealNotice {
    if (lastMealAt == null) return MealNotice.NONE
    val minutes = (nowMs - lastMealAt) / 60_000
    val prone = p.motionSickness != Tendency.NONE || stage == AgeGroup.PUPPY
    return when {
        minutes < MEAL_WARN_MIN -> MealNotice.WARN
        minutes < MEAL_NOTICE_MIN && prone -> MealNotice.NOTICE
        else -> MealNotice.NONE
    }
}

/** "Rest area ahead, take the break there" rule. */
fun suggestRestArea(
    restAreaAheadKm: Double?,
    drivenSinceBreakSec: Int,
    intervalMin: Int,
    destinationRemainSec: Int?,
): Boolean {
    if (restAreaAheadKm == null || restAreaAheadKm > REST_AREA_SUGGEST_MAX_KM) return false
    if (destinationRemainSec != null && destinationRemainSec <= REST_AREA_SUPPRESS_REMAIN_SEC) return false
    return drivenSinceBreakSec >= intervalMin * 60 * REST_AREA_SUGGEST_MIN_PROGRESS
}

fun comfortScore(counts: Map<EventType, Int>): Int {
    val c = { t: EventType -> counts[t] ?: 0 }
    val score = 100 -
        10 * c(EventType.BRAKE_BURST) -
        15 * c(EventType.SPORT_MODE) -
        10 * c(EventType.WINDOW_OPEN) -
        10 * c(EventType.BELT_UNBUCKLED) -
        5 * c(EventType.TEMP_OUT_OF_BAND)
    return score.coerceIn(0, 100)
}

fun windowOpenFraction(position: Int?, max: Int?): Float? =
    if (position == null || max == null || max <= 0) null else (position.toFloat() / max).coerceIn(0f, 1f)

/** Counts brake-pedal presses (false→true) in a sliding window; fires once per burst. */
class BrakeBurstDetector(
    private val windowMs: Long = BRAKE_BURST_WINDOW_MS,
    private val threshold: Int = BRAKE_BURST_THRESHOLD,
    private val cooldownMs: Long = BRAKE_BURST_COOLDOWN_MS,
) {
    private val presses = ArrayDeque<Long>()
    private var lastPressed = false
    private var lastFiredAt: Long? = null

    /** @return true when a burst is detected at this press. */
    fun onPedal(pressed: Boolean, nowMs: Long, moving: Boolean): Boolean {
        val rising = pressed && !lastPressed
        lastPressed = pressed
        if (!rising || !moving) return false
        presses.addLast(nowMs)
        while (presses.isNotEmpty() && nowMs - presses.first() > windowMs) presses.removeFirst()
        val cooledDown = lastFiredAt?.let { nowMs - it >= cooldownMs } ?: true
        if (presses.size >= threshold && cooledDown) {
            lastFiredAt = nowMs
            presses.clear()
            return true
        }
        return false
    }
}

/**
 * Moving vs parked without a speed API: gear says PARK → parked; otherwise moving while the odometer
 * keeps increasing, parked after [MOTION_PARKED_AFTER_MS] with no distance change.
 */
class MotionDetector {
    private var lastKm: Float? = null
    private var lastChangeAt: Long? = null
    var moving: Boolean = false
        private set

    fun update(gearPark: Boolean?, odometerKm: Float?, nowMs: Long): Boolean {
        if (odometerKm != null) {
            val prev = lastKm
            when {
                prev == null -> lastKm = odometerKm
                odometerKm < prev -> { lastKm = odometerKm; lastChangeAt = null }   // reset (emulator): new baseline, no movement
                odometerKm > prev -> { lastChangeAt = nowMs; lastKm = odometerKm }
            }
        }
        val recentlyMoved = lastChangeAt?.let { nowMs - it <= MOTION_PARKED_AFTER_MS } ?: false
        moving = when (gearPark) {
            true -> false
            false -> recentlyMoved || (lastChangeAt?.let { nowMs - it <= MOTION_DELTA_WINDOW_MS } ?: false)
            null -> recentlyMoved
        }
        return moving
    }

    /** Test/demo hook. */
    fun reset() { lastKm = null; lastChangeAt = null; moving = false }
}
