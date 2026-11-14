package com.hmgchallenge.vehicleapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hmgchallenge.vehicleapp.pleos.LocationSource
import com.hmgchallenge.vehicleapp.pleos.VehicleSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardState(
    val odometer: String = "—",
    val stateOfCharge: String = "—",
    val location: String = "—",
    val errors: List<String> = emptyList(),
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val vehicle = VehicleSource(application)
    private val location = LocationSource(application)

    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()

    init {
        vehicle.initialize()
        location.initialize()

        viewModelScope.launch {
            vehicle.distanceDriven().collect { r ->
                r.onSuccess { d -> _state.update { it.copy(odometer = "%.1f km".format(d)) } }
                    .onFailure(::reportError)
            }
        }
        viewModelScope.launch {
            vehicle.evStateOfCharge().collect { r ->
                r.onSuccess { soc -> _state.update { it.copy(stateOfCharge = "$soc %") } }
                    .onFailure(::reportError)
            }
        }
    }

    private var locationStarted = false

    /** Call once [LocationSource.PERMISSION] has been granted. */
    fun startLocation() {
        if (locationStarted) return
        locationStarted = true
        viewModelScope.launch {
            location.updates().collect { r ->
                r.onSuccess { l ->
                    _state.update { it.copy(location = "%.5f, %.5f".format(l.latitude, l.longitude)) }
                }.onFailure(::reportError)
            }
        }
    }

    private fun reportError(t: Throwable) {
        _state.update { it.copy(errors = (it.errors + (t.message ?: t.toString())).takeLast(5)) }
    }

    override fun onCleared() {
        vehicle.release()
        location.release()
    }
}
