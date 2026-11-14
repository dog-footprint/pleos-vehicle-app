package com.dogfootprint.app.care

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
}
