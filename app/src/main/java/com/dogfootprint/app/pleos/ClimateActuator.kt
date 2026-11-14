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

/** One thing the actuator changed, typed so the UI can word it for the driver (ui/Format.changeText). */
sealed interface Change {
    data class Temperature(val celsius: Float) : Change
    data class FanSpeed(val level: Int) : Change
    data class Direction(val direction: AirDirection) : Change
    data object Power : Change
    data class Window(val name: String) : Change
}

sealed interface ApplyResult {
    data class Applied(val changes: List<Change>) : ApplyResult
    /** `errors` are SDK messages for logs and the debug diagnostics, not for the driver. */
    data class Partial(val changes: List<Change>, val errors: List<String>) : ApplyResult
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
        val changes = mutableListOf<Change>()
        val errors = mutableListOf<String>()

        suspend fun <T> step(change: Change, call: suspend () -> Result<T>) {
            call().onSuccess { changes += change }.onFailure { errors += "${change::class.simpleName}: ${it.message ?: it::class.simpleName}" }
        }

        if (plan.verdict == ClimateVerdict.HVAC_OFF) step(Change.Power) { vehicle.setHvacPower(areas.power, true) }
        plan.targetTemp?.let { t -> step(Change.Temperature(t)) { vehicle.setHvacTemperature(areas.target, t) } }
        plan.fanSpeed?.let { f -> step(Change.FanSpeed(f)) { vehicle.setHvacFanSpeed(areas.fan, f) } }
        plan.direction?.let { d -> step(Change.Direction(d)) { vehicle.setHvacFanDirection(areas.fan, d.toSdk()) } }
        if (closeWindow && !moving) {
            zone.windows.forEach { w -> step(Change.Window(w.name)) { vehicle.setWindowPosition(WindowArea(w), 0) } }
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
