package com.dogfootprint.app.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.content.Context
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "DogFootprint"
private const val SDK_CALL_TIMEOUT_MS = 3_000L

/**
 * Coroutine wrapper around the Pleos Vehicle SDK.
 *
 * The SDK is callback based (`getXxx(onComplete, onFailed)` / `registerXxx(listener)`); each stream here
 * does one get, then registers, and unregisters in `awaitClose`. SDK calls throw [SecurityException]
 * synchronously when a manifest permission is missing, so registration goes through [registerOrFail].
 * Callback values are platform-nullable; nulls are dropped.
 */
class VehicleSource(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)

    // Domain objects are proxies; resolve them lazily after initialize().
    private val hvac by lazy { vehicle.getHVAC() }
    private val seat by lazy { vehicle.getSeat() }
    private val window by lazy { vehicle.getWindow() }
    private val door by lazy { vehicle.getDoor() }
    private val brake by lazy { vehicle.getBrake() }
    private val drivingMode by lazy { vehicle.getDrivingMode() }
    private val odometer by lazy { vehicle.getOdometer() }
    private val evBattery by lazy { vehicle.getEvBattery() }

    @Suppress("DEPRECATION")
    private val gear by lazy { vehicle.getGear() }

    var initialized: Boolean = false
        private set

    fun initialize(): Boolean {
        initialized = runCatching { vehicle.initialize() }
            .onFailure { Log.w(TAG, "Vehicle SDK initialize failed", it) }
            .isSuccess
        return initialized
    }

    fun release() {
        runCatching { vehicle.release() }
        initialized = false
    }

    // ---- streams -------------------------------------------------------------------------------

    fun distanceDriven(): Flow<Result<Float>> = vehicleFlow(
        get = { ok, err -> odometer.getCurrentDistanceDriven(ok, err) },
        subscribe = { ok, err ->
            val l = object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(value: Float?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            odometer.registerDistanceDriven(l)
            ({ odometer.unregisterDistanceDriven(l) })
        },
    )

    /** Seconds of driving in the current trip, as counted by the vehicle. */
    fun tripDrivingTime(): Flow<Result<Int>> = vehicleFlow(
        get = { ok, err -> odometer.getTripCurrentDrivingTime(ok, err) },
        subscribe = { ok, err ->
            val l = object : DrivingTimeListener {
                override fun onDrivingTimeUpdated(value: Int?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            odometer.registerTripCurrentDrivingTime(l)
            ({ odometer.unregisterTripCurrentDrivingTime(l) })
        },
    )

    /** EV battery state of charge, in percent. */
    fun evStateOfCharge(): Flow<Result<Int>> = vehicleFlow(
        get = { ok, err -> evBattery.getEvBatteryStateOfCharge(ok, err) },
        subscribe = { ok, err ->
            val l = object : EvBatteryStateOfChargeListener {
                override fun onEvBatteryStateOfChargeUpdated(value: Int?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            evBattery.registerEvBatteryStateOfCharge(l)
            ({ evBattery.unregisterEvBatteryStateOfCharge(l) })
        },
    )

    /** Measured cabin temperature (°C) at a seat zone. */
    fun hvacCurrentTemp(area: SeatArea): Flow<Result<Float>> = vehicleFlow(
        get = { ok, err -> hvac.getHvacTemperatureCurrent(area, TemperatureUnit.CELSIUS, ok, err) },
        subscribe = { ok, err ->
            val l = object : HvacTemperatureListener {
                override fun onHvacTemperatureUpdated(value: Float?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            hvac.registerHvacTemperatureCurrent(area, l)
            ({ hvac.unregisterHvacTemperatureCurrent(area, l) })
        },
    )

    /** HVAC set-point (°C) for a seat zone. */
    fun hvacTargetTemp(area: SeatArea): Flow<Result<Float>> = vehicleFlow(
        get = { ok, err -> hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, err) },
        subscribe = { ok, err ->
            val l = object : HvacTemperatureListener {
                override fun onHvacTemperatureUpdated(value: Float?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            hvac.registerHvacTemperature(area, l)
            ({ hvac.unregisterHvacTemperature(area, l) })
        },
    )

    fun hvacPower(area: SeatArea): Flow<Result<Boolean>> = vehicleFlow(
        get = { ok, err -> hvac.getHvacPowerState(area, ok, err) },
        subscribe = { ok, err ->
            val l = object : HvacPowerStateListener {
                override fun onHvacPowerStateUpdated(value: Boolean?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            hvac.registerHvacPowerState(area, l)
            ({ hvac.unregisterHvacPowerState(area, l) })
        },
    )

    fun hvacFanSpeed(area: SeatArea): Flow<Result<Int>> = vehicleFlow(
        get = { ok, err -> hvac.getHvacFanSpeed(area, ok, err) },
        subscribe = { ok, err ->
            val l = object : HvacFanSpeedListener {
                override fun onHvacFanSpeedUpdated(value: Int?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            hvac.registerHvacFanSpeed(area, l)
            ({ hvac.unregisterHvacFanSpeed(area, l) })
        },
    )

    /** No listener exists for fan direction; poll this. */
    suspend fun hvacFanDirection(area: SeatArea): FanDirectionType? =
        getOnce { ok, err -> hvac.getHvacFanDirection(area, ok, err) }

    fun seatOccupancy(area: SeatArea): Flow<Result<Boolean>> = vehicleFlow(
        get = { ok, err -> seat.getCurrentSeatOccupancy(area, ok, err) },
        subscribe = { ok, err ->
            val l = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(value: Boolean?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            seat.registerSeatOccupancy(area, l)
            ({ seat.unregisterSeatOccupancy(area, l) })
        },
    )

    fun seatBeltBuckled(area: SeatArea): Flow<Result<Boolean>> = vehicleFlow(
        get = { ok, err -> seat.getCurrentSeatBeltBuckledState(area, ok, err) },
        subscribe = { ok, err ->
            val l = object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(value: Boolean?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            seat.registerSeatBeltBuckledState(area, l)
            ({ seat.unregisterSeatBeltBuckledState(area, l) })
        },
    )

    fun windowPosition(area: WindowArea): Flow<Result<Int>> = vehicleFlow(
        get = { ok, err -> window.getCurrentWindowPosition(area, ok, err) },
        subscribe = { ok, err ->
            val l = object : WindowPositionListener {
                override fun onWindowPositionUpdated(value: Int?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            window.registerWindowPosition(area, l)
            ({ window.unregisterWindowPosition(area, l) })
        },
    )

    fun doorPosition(area: DoorArea): Flow<Result<Int>> = vehicleFlow(
        get = { ok, err -> door.getCurrentDoorPosition(area, ok, err) },
        subscribe = { ok, err ->
            val l = object : DoorPositionListener {
                override fun onPositionUpdated(value: Int?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            door.registerDoorPosition(area, l)
            ({ door.unregisterDoorPosition(area, l) })
        },
    )

    fun brakePedal(): Flow<Result<Boolean>> = vehicleFlow(
        get = { ok, err -> brake.getCurrentBrakePedalStatus(ok, err) },
        subscribe = { ok, err ->
            val l = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(value: Boolean?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            brake.registerBrakePedalStatus(l)
            ({ brake.unregisterBrakePedalStatus(l) })
        },
    )

    fun drivingMode(): Flow<Result<DrivingModeType>> = vehicleFlow(
        get = { ok, err -> drivingMode.getCurrentDrivingModeState(ok, err) },
        subscribe = { ok, err ->
            val l = object : DrivingModeStateListener {
                override fun onDrivingModeStateUpdated(value: DrivingModeType?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            drivingMode.registerDrivingModeState(l)
            ({ drivingMode.unregisterDrivingModeState(l) })
        },
    )

    /** The Gear API is deprecated in SDK 2.3.2 but still served; it is the only PARK signal we have. */
    @Suppress("DEPRECATION")
    fun gear(): Flow<Result<VehicleGear>> = vehicleFlow(
        get = { ok, err -> gear.getCurrentGearSelectionState(ok, err) },
        subscribe = { ok, err ->
            val l = object : GearSelectionListener {
                override fun onSelected(value: VehicleGear?) = ok(value)
                override fun onFailed(e: Exception) = err(e)
            }
            gear.registerGearSelection(l)
            ({ gear.unregisterGearSelection(l) })
        },
    )

    // ---- capabilities (one-shot) ------------------------------------------------------------------

    suspend fun hvacCurrentTempAreas(): List<SeatArea>? =
        getOnce { ok, err -> hvac.checkHvacTemperatureCurrentCapabilities({ _, areas -> ok(areas) }, err) }

    suspend fun hvacPowerAreas(): List<SeatArea>? =
        getOnce { ok, err -> hvac.checkHvacPowerCapabilities({ areas: List<SeatArea> -> ok(areas) }, err) }

    suspend fun hvacTempBounds(): Map<SeatArea, AreaBounds<Float>>? =
        getOnce { ok, err -> hvac.checkHvacTemperatureCapabilities({ map: Map<SeatArea, AreaBounds<Float>> -> ok(map) }, err) }

    suspend fun hvacFanSpeedBounds(): Map<SeatArea, AreaBounds<Int>>? =
        getOnce { ok, err -> hvac.checkHvacFanSpeedCapabilities({ map -> ok(map) }, err) }

    suspend fun windowBounds(): Map<WindowArea, AreaBounds<Int>>? =
        getOnce { ok, err -> window.checkWindowPositionCapabilities({ map -> ok(map) }, err) }

    // ---- control (experimental; Vehicle Control is not yet approved for our Pleos project) -------

    suspend fun setHvacTemperature(area: SeatArea, celsius: Float): Result<Float> =
        setValue { ok, err -> hvac.setHvacTemperature(area, celsius, TemperatureUnit.CELSIUS, ok, err) }

    suspend fun setHvacFanSpeed(area: SeatArea, speed: Int): Result<Int> =
        setValue { ok, err -> hvac.setHvacFanSpeed(area, speed, ok, err) }

    suspend fun setHvacFanDirection(area: SeatArea, direction: FanDirectionType): Result<FanDirectionType> =
        setValue { ok, err -> hvac.setHvacFanDirection(area, direction, ok, err) }

    suspend fun setHvacPower(area: SeatArea, on: Boolean): Result<Boolean> =
        setValue { ok, err -> hvac.setHvacPower(area, on, ok, err) }

    suspend fun setSeatHeater(area: SeatArea, level: Int): Result<Int> =
        setValue { ok, err -> seat.setHvacSeatTemperature(area, level, ok, err) }

    suspend fun setSeatVentilation(area: SeatArea, level: Int): Result<Int> =
        setValue { ok, err -> seat.setHvacSeatVentilation(area, level, ok, err) }

    suspend fun setWindowPosition(area: WindowArea, position: Int): Result<Int> =
        setValue { ok, err -> window.setWindowPosition(area, position, ok, err) }

    // ---- plumbing ----------------------------------------------------------------------------------

    private fun <T : Any> vehicleFlow(
        get: (onOk: (T?) -> Unit, onErr: (Exception) -> Unit) -> Unit,
        subscribe: (onOk: (T?) -> Unit, onErr: (Exception) -> Unit) -> (() -> Unit),
    ): Flow<Result<T>> = callbackFlow {
        val ok: (T?) -> Unit = { if (it != null) trySend(Result.success(it)) }
        val err: (Exception) -> Unit = { trySend(Result.failure(it)) }
        var unsubscribe: (() -> Unit)? = null
        val registered = registerOrFail {
            get(ok, err)
            unsubscribe = subscribe(ok, err)
        }
        if (!registered) return@callbackFlow
        awaitClose { runCatching { unsubscribe?.invoke() } }
    }

    /** Runs [block]; on a synchronous SDK throw, emits the failure and closes the flow. */
    private inline fun <T> ProducerScope<Result<T>>.registerOrFail(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: Exception) {
            trySend(Result.failure(e))
            close()
            false
        }

    /** One-shot get with a timeout; null on any failure. */
    private suspend fun <T : Any> getOnce(
        block: (onOk: (T?) -> Unit, onErr: (Exception) -> Unit) -> Unit,
    ): T? = withTimeoutOrNull(SDK_CALL_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            try {
                block(
                    { v -> if (cont.isActive) cont.resume(v) },
                    { e -> Log.w(TAG, "Vehicle SDK get failed: ${e.message}"); if (cont.isActive) cont.resume(null) },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Vehicle SDK get threw: ${e.message}")
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private suspend fun <A, V : Any> setValue(
        block: (onOk: (A, V?) -> Unit, onErr: (Exception) -> Unit) -> Unit,
    ): Result<V> = withTimeoutOrNull(SDK_CALL_TIMEOUT_MS) {
        suspendCancellableCoroutine<Result<V>> { cont ->
            try {
                block(
                    { _, v ->
                        if (cont.isActive) cont.resume(v?.let { Result.success(it) } ?: Result.failure(IllegalStateException("null result")))
                    },
                    { e -> if (cont.isActive) cont.resume(Result.failure(e)) },
                )
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Result.failure(e))
            }
        }
    } ?: Result.failure(IllegalStateException("timeout"))
}
