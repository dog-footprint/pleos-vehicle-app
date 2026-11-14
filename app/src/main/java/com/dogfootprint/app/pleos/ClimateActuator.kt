package com.dogfootprint.app.pleos

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import com.dogfootprint.app.care.AirDirection
import com.dogfootprint.app.care.ClimatePlan
import com.dogfootprint.app.care.ClimateVerdict
import com.dogfootprint.app.care.DogZone

/** HVAC areas as the vehicle defines them (multi-seat zones); see CareEngine.watchZone. */
data class ZoneAreas(val current: SeatArea, val target: SeatArea, val fan: SeatArea, val power: SeatArea) {
    companion object {
        fun single(seat: SeatValue) = SeatArea(seat).let { ZoneAreas(it, it, it, it) }
    }
}

sealed interface ApplyResult {
    data class Applied(val changes: List<String>) : ApplyResult
    data class Partial(val changes: List<String>, val errors: List<String>) : ApplyResult
    data class NotApplied(val reason: Reason) : ApplyResult

    enum class Reason { CONTROL_NOT_ENABLED, NOTHING_TO_DO, SDK_ERROR }
}

/** Seam for Vehicle Control, which is "Not Available Yet" in our Pleos project. */
interface ClimateActuator {
    suspend fun apply(plan: ClimatePlan, zone: DogZone, areas: ZoneAreas, moving: Boolean, closeWindow: Boolean): ApplyResult
}

/** Production path until Pleos approves control: recommendations only. */
object GuidanceOnlyActuator : ClimateActuator {
    override suspend fun apply(plan: ClimatePlan, zone: DogZone, areas: ZoneAreas, moving: Boolean, closeWindow: Boolean) =
        ApplyResult.NotApplied(ApplyResult.Reason.CONTROL_NOT_ENABLED)
}

/** Experimental path: drives the HVAC/seat/window set APIs (works on the Pleos emulator). */
class PleosHvacActuator(private val vehicle: VehicleSource) : ClimateActuator {

    override suspend fun apply(plan: ClimatePlan, zone: DogZone, areas: ZoneAreas, moving: Boolean, closeWindow: Boolean): ApplyResult {
        if (!plan.needsAction && !closeWindow) return ApplyResult.NotApplied(ApplyResult.Reason.NOTHING_TO_DO)
        val changes = mutableListOf<String>()
        val errors = mutableListOf<String>()

        suspend fun <T> step(label: String, call: suspend () -> Result<T>) {
            call().onSuccess { changes += "$label → $it" }.onFailure { errors += "$label: ${it.message ?: it::class.simpleName}" }
        }

        if (plan.verdict == ClimateVerdict.HVAC_OFF) step("HVAC power") { vehicle.setHvacPower(areas.power, true) }
        plan.targetTemp?.let { t -> step("temperature") { vehicle.setHvacTemperature(areas.target, t) } }
        plan.fanSpeed?.let { f -> step("fan speed") { vehicle.setHvacFanSpeed(areas.fan, f) } }
        plan.direction?.let { d -> step("fan direction") { vehicle.setHvacFanDirection(areas.fan, d.toSdk()) } }
        if (closeWindow && !moving) {
            zone.windows.forEach { w -> step("window ${w.name}") { vehicle.setWindowPosition(WindowArea(w), 0) } }
        }

        return when {
            changes.isEmpty() && errors.isEmpty() -> ApplyResult.NotApplied(ApplyResult.Reason.NOTHING_TO_DO)
            errors.isEmpty() -> ApplyResult.Applied(changes)
            changes.isEmpty() -> ApplyResult.NotApplied(ApplyResult.Reason.SDK_ERROR)
            else -> ApplyResult.Partial(changes, errors)
        }
    }

    private fun AirDirection.toSdk() = when (this) {
        AirDirection.FACE -> FanDirectionType.FACE
        AirDirection.FLOOR -> FanDirectionType.FLOOR
        AirDirection.FACE_AND_FLOOR -> FanDirectionType.FACE_AND_FLOOR
    }
}
