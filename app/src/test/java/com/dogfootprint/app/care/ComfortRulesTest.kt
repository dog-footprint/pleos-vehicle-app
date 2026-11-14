package com.dogfootprint.app.care

import com.dogfootprint.app.R
import com.dogfootprint.app.data.AgeGroup
import com.dogfootprint.app.data.BodyCondition
import com.dogfootprint.app.data.CoatType
import com.dogfootprint.app.data.DogProfile
import com.dogfootprint.app.data.DogSize
import com.dogfootprint.app.data.EventType
import com.dogfootprint.app.data.Tendency
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfortRulesTest {

    private fun dog(
        size: DogSize = DogSize.MEDIUM, coat: CoatType = CoatType.SHORT, brachy: Boolean = false,
        age: AgeGroup = AgeGroup.ADULT, motion: Tendency = Tendency.NONE, weight: Float? = null,
        body: BodyCondition = BodyCondition.IDEAL, resp: Boolean = false,
    ) = DogProfile(
        id = "d", name = "코코", breedKey = "other", size = size, coat = coat, brachycephalic = brachy,
        ageGroup = age, motionSickness = motion, weightKg = weight, bodyCondition = body, respiratoryOrHeartIssue = resp,
    )

    @Test
    fun `husky band caps at 22 (thick coat, large)`() {
        val band = comfortBand(dog(size = DogSize.LARGE, coat = CoatType.DOUBLE))
        assertEquals(18f, band.lower); assertEquals(22f, band.upper)
    }

    @Test
    fun `toy short-hair dog gets the 20 degree floor`() {
        val band = comfortBand(dog(size = DogSize.TOY, coat = CoatType.SHORT))
        assertEquals(20f, band.lower); assertEquals(24f, band.upper)
    }

    @Test
    fun `brachycephalic cap is 21 and senior shrinks both sides`() {
        val p = dog(size = DogSize.SMALL, coat = CoatType.SHORT, brachy = true, age = AgeGroup.SENIOR)
        val band = comfortBand(p)
        assertEquals(20f, band.upper)   // 21 − 1 (senior)
        assertTrue(band.upper - band.lower >= BAND_MIN_WIDTH)
        assertTrue(isHeatAlert(p, 24f))
        assertFalse(isHeatAlert(dog(), 26f))
        assertTrue(isHeatAlert(dog(), 27f))
    }

    @Test
    fun `overweight and heavy dogs lose a degree each`() {
        assertEquals(23f, comfortBand(dog(body = BodyCondition.OVERWEIGHT)).upper)
        assertEquals(21f, comfortBand(dog(size = DogSize.GIANT, weight = 55f)).upper)   // 22 (large/giant) − 1 (≥ 50 kg)
    }

    @Test
    fun `life stage from birth date follows AAHA size thresholds`() {
        val now = Calendar.getInstance()
        val y = now.get(Calendar.YEAR)
        val m = now.get(Calendar.MONTH) + 1
        assertEquals(AgeGroup.PUPPY, dog().copy(birthYear = y, birthMonth = m).lifeStage())
        assertEquals(AgeGroup.ADULT, dog(size = DogSize.SMALL).copy(birthYear = y - 9, birthMonth = m).lifeStage())
        assertEquals(AgeGroup.SENIOR, dog(size = DogSize.SMALL).copy(birthYear = y - 10, birthMonth = m).lifeStage())
        assertEquals(AgeGroup.SENIOR, dog(size = DogSize.LARGE).copy(birthYear = y - 7, birthMonth = m).lifeStage())
        assertEquals(AgeGroup.ADULT, dog(size = DogSize.LARGE).copy(birthYear = y - 6, birthMonth = m).lifeStage())
        assertEquals(AgeGroup.SENIOR, dog(age = AgeGroup.SENIOR).lifeStage())   // no birth date → manual value
    }

    @Test
    fun `climate plan picks target below band when hot`() {
        val p = dog(size = DogSize.LARGE, coat = CoatType.DOUBLE)
        val plan = climatePlan(p, comfortBand(p), zoneTemp = 26f, hvacPower = true, fanSpeed = 2, fanMax = 5, moving = true)
        assertEquals(ClimateVerdict.TOO_HOT, plan.verdict)
        assertEquals(21f, plan.targetTemp); assertEquals(3, plan.fanSpeed); assertEquals(AirDirection.FACE_AND_FLOOR, plan.direction)
    }

    @Test
    fun `hvac off while moving and out of band is flagged`() {
        val p = dog()
        val plan = climatePlan(p, comfortBand(p), zoneTemp = 28f, hvacPower = false, fanSpeed = null, fanMax = null, moving = true)
        assertEquals(ClimateVerdict.HVAC_OFF, plan.verdict)
    }

    @Test
    fun `break interval honours age motion sickness and floor`() {
        assertEquals(120, breakIntervalMin(dog(), heatAlert = false))
        assertEquals(60, breakIntervalMin(dog(age = AgeGroup.PUPPY, motion = Tendency.MILD), heatAlert = false))
        assertEquals(45, breakIntervalMin(dog(age = AgeGroup.SENIOR, motion = Tendency.SEVERE), heatAlert = false))
        assertEquals(60, breakIntervalMin(dog(), heatAlert = true))
        assertEquals(BreakPhase.PRE_ALERT, breakPhase(106 * 60, 120))
        assertEquals(BreakPhase.DUE, breakPhase(120 * 60, 120))
    }

    @Test
    fun `meal notice warns within two hours, notices prone dogs within four`() {
        val now = 10_000_000L
        val h = 60 * 60_000L
        assertEquals(MealNotice.WARN, mealNotice(now - 1 * h, now, dog()))
        assertEquals(MealNotice.NONE, mealNotice(now - 3 * h, now, dog()))
        assertEquals(MealNotice.NOTICE, mealNotice(now - 3 * h, now, dog(motion = Tendency.MILD)))
        assertEquals(MealNotice.NOTICE, mealNotice(now - 3 * h, now, dog(age = AgeGroup.PUPPY)))
        assertEquals(MealNotice.NONE, mealNotice(null, now, dog()))
    }

    @Test
    fun `rest area suggestion needs progress and distance`() {
        assertTrue(suggestRestArea(restAreaAheadKm = 5.0, drivenSinceBreakSec = 80 * 60, intervalMin = 120, destinationRemainSec = null))
        assertFalse(suggestRestArea(5.0, 30 * 60, 120, null))
        assertFalse(suggestRestArea(15.0, 80 * 60, 120, null))
        assertFalse(suggestRestArea(5.0, 80 * 60, 120, destinationRemainSec = 10 * 60))
    }

    @Test
    fun `brake burst fires once per window and respects cooldown`() {
        val d = BrakeBurstDetector()
        var fired = 0
        var t = 0L
        repeat(4) { fired += if (d.onPedal(true, t, true)) 1 else 0; d.onPedal(false, t + 500, true); t += 2_000 }
        assertEquals(1, fired)
        repeat(4) { fired += if (d.onPedal(true, t, true)) 1 else 0; d.onPedal(false, t + 500, true); t += 2_000 }
        assertEquals(1, fired)
        t += BRAKE_BURST_COOLDOWN_MS
        repeat(4) { fired += if (d.onPedal(true, t, true)) 1 else 0; d.onPedal(false, t + 500, true); t += 2_000 }
        assertEquals(2, fired)
        assertFalse(d.onPedal(true, t, moving = false))
    }

    @Test
    fun `motion detector uses gear and odometer delta`() {
        val m = MotionDetector()
        assertFalse(m.update(gearPark = true, odometerKm = 100f, nowMs = 0))
        assertFalse(m.update(gearPark = false, odometerKm = 100f, nowMs = 1_000))
        assertTrue(m.update(gearPark = false, odometerKm = 100.1f, nowMs = 2_000))
        assertTrue(m.update(gearPark = false, odometerKm = 100.1f, nowMs = 30_000))
        assertFalse(m.update(gearPark = false, odometerKm = 100.1f, nowMs = 70_000))
        assertFalse(m.update(gearPark = true, odometerKm = 100.2f, nowMs = 71_000))
        assertFalse(m.update(gearPark = false, odometerKm = 50f, nowMs = 72_000))
        assertTrue(m.update(gearPark = false, odometerKm = 50.2f, nowMs = 73_000))
    }

    @Test
    fun `comfort score clamps`() {
        assertEquals(100, comfortScore(emptyMap()))
        assertEquals(65, comfortScore(mapOf(EventType.BRAKE_BURST to 2, EventType.SPORT_MODE to 1)))
        assertEquals(0, comfortScore(mapOf(EventType.BRAKE_BURST to 20)))
    }

    @Test
    fun `band delta is negative below, zero inside, positive above`() {
        val band = ComfortBand(18f, 22f)
        assertEquals(-2f, bandDelta(band, 16f))
        assertEquals(0f, bandDelta(band, 18f))
        assertEquals(0f, bandDelta(band, 20f))
        assertEquals(0f, bandDelta(band, 22f))
        assertEquals(5f, bandDelta(band, 27f))
    }

    @Test
    fun `alerts depend on the screen and drop what its body already shows`() {
        val drive = activeAlerts(AlertContext.DRIVING, ClimateVerdict.HEAT_ALERT, setOf(SafetyFlag.BELT_UNBUCKLED, SafetyFlag.WINDOW_OPEN), MealNotice.WARN, BreakPhase.DUE, aboard = true, leavePrep = false)
        assertEquals(listOf(AlertKind.HEAT, AlertKind.WINDOW_OPEN, AlertKind.BELT, AlertKind.MEAL, AlertKind.BREAK_DUE), drive.map { it.kind })
        assertEquals(AlertLevel.DANGER, drive.first().level); assertEquals(AlertContext.DRIVING, drive.first().context)
        // Leaving with the door open: the body shows heat/climate; belt, meal and break mean nothing now. Window/door stay.
        val preExit = activeAlerts(AlertContext.PRE_EXIT, ClimateVerdict.HEAT_ALERT, setOf(SafetyFlag.WINDOW_OPEN, SafetyFlag.BELT_UNBUCKLED, SafetyFlag.DOOR_OPEN), MealNotice.WARN, BreakPhase.DUE, aboard = true, leavePrep = true)
        assertEquals(listOf(AlertKind.WINDOW_OPEN, AlertKind.DOOR_OPEN), preExit.map { it.kind })
        val alone = activeAlerts(AlertContext.PET_ALONE, ClimateVerdict.HVAC_OFF, setOf(SafetyFlag.DOOR_OPEN), MealNotice.WARN, BreakPhase.NONE, aboard = true, leavePrep = false)
        assertEquals(listOf(AlertKind.DOOR_OPEN), alone.map { it.kind })
        // Parked after boarding: the body already says "buckle the harness", so the slot shows the leave reminder, then the meal.
        val parked = activeAlerts(AlertContext.PARKED, ClimateVerdict.OK, setOf(SafetyFlag.BELT_UNBUCKLED), MealNotice.WARN, BreakPhase.DUE, aboard = true, leavePrep = true)
        assertEquals(listOf(AlertKind.LEAVE_PREP, AlertKind.MEAL), parked.map { it.kind })
        // Leaving with the door open: the screen itself is the leave reminder.
        val leaving = activeAlerts(AlertContext.PRE_EXIT, ClimateVerdict.OK, emptySet(), MealNotice.NONE, BreakPhase.NONE, aboard = true, leavePrep = true)
        assertTrue(leaving.isEmpty())
        // Climate off: caution inside the band, danger outside — and dangers always come first in the list.
        val off = activeAlerts(AlertContext.PARKED, ClimateVerdict.OK, emptySet(), MealNotice.WARN, BreakPhase.NONE, aboard = true, leavePrep = false, hvacOff = true, hvacOffLevel = AlertLevel.CAUTION)
        assertEquals(listOf(AlertKind.HVAC_OFF, AlertKind.MEAL), off.map { it.kind }); assertEquals(AlertLevel.CAUTION, off.first().level)
        val offHot = activeAlerts(AlertContext.DRIVING, ClimateVerdict.TOO_HOT, setOf(SafetyFlag.BELT_UNBUCKLED), MealNotice.NONE, BreakPhase.NONE, aboard = true, leavePrep = false, hvacOff = true, hvacOffLevel = AlertLevel.DANGER)
        assertEquals(listOf(AlertKind.HVAC_OFF, AlertKind.BELT), offHot.map { it.kind }); assertEquals(AlertLevel.DANGER, offHot.first().level)
        // Nothing about a dog that is not on board.
        assertTrue(activeAlerts(AlertContext.PARKED, ClimateVerdict.HEAT_ALERT, setOf(SafetyFlag.BELT_UNBUCKLED, SafetyFlag.DOOR_OPEN), MealNotice.NONE, BreakPhase.NONE, aboard = false, leavePrep = false).isEmpty())
    }

    @Test
    fun `alert wording follows the screen it is read on`() {
        assertEquals(R.string.alert_heat_driving, alertMessageRes(AlertKind.HEAT, AlertContext.DRIVING))
        assertEquals(R.string.alert_heat_parked, alertMessageRes(AlertKind.HEAT, AlertContext.PARKED))
        assertEquals(R.string.alert_heat_pre_exit, alertMessageRes(AlertKind.HEAT, AlertContext.PRE_EXIT))
        assertEquals(R.string.alert_heat_pet_alone, alertMessageRes(AlertKind.HEAT, AlertContext.PET_ALONE))
        assertEquals(R.string.alert_belt_parked, alertMessageRes(AlertKind.BELT, AlertContext.PARKED))
        assertEquals(R.string.alert_belt_driving, alertMessageRes(AlertKind.BELT, AlertContext.DRIVING))
    }

    @Test
    fun `pre-exit needs dog aboard, parked, driver door open and not yet alone`() {
        assertTrue(isPreExit(aboard = true, moving = false, gearPark = true, driverDoorOpen = true, petAlone = false))
        assertTrue(isPreExit(aboard = true, moving = false, gearPark = null, driverDoorOpen = true, petAlone = false))
        assertFalse(isPreExit(aboard = true, moving = false, gearPark = true, driverDoorOpen = true, petAlone = true))
        assertFalse(isPreExit(aboard = true, moving = true, gearPark = false, driverDoorOpen = true, petAlone = false))
        assertFalse(isPreExit(aboard = false, moving = false, gearPark = true, driverDoorOpen = true, petAlone = false))
        assertFalse(isPreExit(aboard = true, moving = false, gearPark = true, driverDoorOpen = false, petAlone = false))
        assertEquals(AlertContext.PET_ALONE, alertContext(moving = false, preExit = true, petAlone = true))
        assertEquals(AlertContext.PRE_EXIT, alertContext(moving = false, preExit = true, petAlone = false))
    }

    @Test
    fun `trip end resets break, meal, boarding and alert state`() {
        val s = CareState(
            aboard = true, drivenSinceBreakSec = 5400, breakPhase = BreakPhase.DUE, mealNotice = MealNotice.WARN, heatAlert = true,
            alerts = listOf(Alert(AlertKind.BELT, AlertLevel.CAUTION, AlertContext.DRIVING)), dismissedCautions = setOf(AlertKind.BELT),
            data = com.dogfootprint.app.data.AppData(aboardDogId = "d", lastMealAt = 1L),
        ).afterTripEnd()
        assertEquals(0, s.drivenSinceBreakSec); assertEquals(BreakPhase.NONE, s.breakPhase)
        assertEquals(MealNotice.NONE, s.mealNotice); assertFalse(s.heatAlert); assertFalse(s.aboard)
        assertTrue(s.alerts.isEmpty()); assertTrue(s.dismissedCautions.isEmpty())
        assertEquals(null, s.data.lastMealAt); assertEquals(null, s.data.aboardDogId)
        assertEquals(null, s.trip)
    }

    @Test
    fun `whole minutes round to the nearest minute`() {
        assertEquals(90, wholeMinutes(5400)); assertEquals(15, wholeMinutes(899)); assertEquals(0, wholeMinutes(20))
    }

    @Test
    fun `climate action follows the cause and aims at the band centre`() {
        val band = ComfortBand(18f, 22f)
        assertEquals(ClimateAction.TurnOn, climateAction(hvacPower = false, verdict = ClimateVerdict.OK, band = band))
        assertEquals(ClimateAction.TurnOn, climateAction(hvacPower = false, verdict = ClimateVerdict.HEAT_ALERT, band = band))
        assertEquals(ClimateAction.CoolTo(20), climateAction(hvacPower = true, verdict = ClimateVerdict.HEAT_ALERT, band = band))
        assertEquals(ClimateAction.CoolTo(20), climateAction(hvacPower = true, verdict = ClimateVerdict.TOO_HOT, band = band))
        assertEquals(ClimateAction.HeatTo(20), climateAction(hvacPower = true, verdict = ClimateVerdict.TOO_COLD, band = band))
        assertEquals(null, climateAction(hvacPower = true, verdict = ClimateVerdict.OK, band = band))
    }

    @Test
    fun `climate off is a caution inside the band and a danger outside or trending out`() {
        val band = ComfortBand(18f, 22f)
        assertEquals(AlertLevel.CAUTION, hvacOffLevel(20f, band, ratePerMin = 0f))
        assertEquals(AlertLevel.CAUTION, hvacOffLevel(20f, band, ratePerMin = null))
        assertEquals(AlertLevel.DANGER, hvacOffLevel(27f, band, ratePerMin = 0f))
        assertEquals(AlertLevel.DANGER, hvacOffLevel(21f, band, ratePerMin = 0.3f))   // +3 °C in 10 min → 24 > 22
        assertEquals(AlertLevel.CAUTION, hvacOffLevel(21f, band, ratePerMin = 0.05f))
        assertEquals(AlertLevel.CAUTION, hvacOffLevel(null, band, ratePerMin = null))
    }

    @Test
    fun `durations round to the nearest minute everywhere`() {
        assertEquals(1, wholeMinutes(45))      // the check-in and the list must agree: 45 s is "1분" in both
        assertEquals(0, wholeMinutes(29)); assertEquals(1, wholeMinutes(30)); assertEquals(78, wholeMinutes(78 * 60 + 20))
    }

    @Test
    fun `parking-lot moves are not footprints`() {
        assertFalse(isTripWorthRecording(distanceKm = 0.4f, drivingSec = 120))
        assertTrue(isTripWorthRecording(distanceKm = 1.0f, drivingSec = 60))
        assertTrue(isTripWorthRecording(distanceKm = 0.2f, drivingSec = 180))
        assertFalse(isTripWorthRecording(distanceKm = 0.99f, drivingSec = 179))
    }
}
