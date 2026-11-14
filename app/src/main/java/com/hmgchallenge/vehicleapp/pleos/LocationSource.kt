package com.hmgchallenge.vehicleapp.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Coroutine wrapper around the Pleos FusedLocation SDK.
 *
 * [PERMISSION] is a *runtime* (dangerous) permission on the Pleos image, unlike the
 * Vehicle `CAR_*` ones — request it before collecting [updates], or the SDK throws.
 */
class LocationSource(context: Context) {

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }

    private val fusedLocation = FusedLocation(context.applicationContext)

    fun initialize() = fusedLocation.initialize()

    fun release() = fusedLocation.release()

    fun updates(): Flow<Result<LocationData>> = callbackFlow {
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                if (location != null) trySend(Result.success(location))
            }

            override fun onError(exception: Exception) {
                trySend(Result.failure(exception))
            }
        }
        try {
            fusedLocation.getLastKnownLocation(listener)
            fusedLocation.registerFusedLocationCallback(listener)
        } catch (e: SecurityException) {
            trySend(Result.failure(e))
            close()
            return@callbackFlow
        }
        awaitClose { fusedLocation.unregisterFusedLocationCallback(listener) }
    }
}
