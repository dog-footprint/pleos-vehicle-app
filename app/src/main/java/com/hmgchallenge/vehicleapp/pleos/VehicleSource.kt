package com.hmgchallenge.vehicleapp.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.content.Context
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Thin coroutine wrapper around the Pleos Vehicle SDK.
 *
 * The SDK is callback based (`getXxx(onComplete, onFailed)` / `registerXxx(listener)`);
 * each stream here registers on collect and unregisters on cancel.
 * Add more domains (HVAC, Door, Odometer, …) following the same pattern.
 * SDK calls throw [SecurityException] synchronously when a permission is missing,
 * so registration goes through [registerOrFail].
 */
class VehicleSource(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)

    fun initialize() = vehicle.initialize()

    fun release() = vehicle.release()

    /** Odometer distance driven. Unit follows the vehicle's display unit (km on Korean spec). */
    fun distanceDriven(): Flow<Result<Float>> = callbackFlow {
        val odometer = vehicle.getOdometer()
        val listener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                if (value != null) trySend(Result.success(value))
            }

            override fun onFailed(e: Exception) {
                trySend(Result.failure(e))
            }
        }
        val registered = registerOrFail {
            odometer.getCurrentDistanceDriven(
                { if (it != null) trySend(Result.success(it)) },
                { trySend(Result.failure(it)) },
            )
            odometer.registerDistanceDriven(listener)
        }
        if (!registered) return@callbackFlow
        awaitClose { odometer.unregisterDistanceDriven(listener) }
    }

    /** EV battery state of charge, in percent. */
    fun evStateOfCharge(): Flow<Result<Int>> = callbackFlow {
        val battery = vehicle.getEvBattery()
        val listener = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                if (value != null) trySend(Result.success(value))
            }

            override fun onFailed(e: Exception) {
                trySend(Result.failure(e))
            }
        }
        val registered = registerOrFail {
            battery.getEvBatteryStateOfCharge(
                { if (it != null) trySend(Result.success(it)) },
                { trySend(Result.failure(it)) },
            )
            battery.registerEvBatteryStateOfCharge(listener)
        }
        if (!registered) return@callbackFlow
        awaitClose { battery.unregisterEvBatteryStateOfCharge(listener) }
    }

    /** Runs [block]; on a missing-permission throw, emits the failure and closes the flow. */
    private inline fun <T> ProducerScope<Result<T>>.registerOrFail(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: SecurityException) {
            trySend(Result.failure(e))
            close()
            false
        }
}
