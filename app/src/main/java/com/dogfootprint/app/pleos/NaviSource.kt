package com.dogfootprint.app.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "DogFootprint"
private const val POLL_MS = 30_000L

enum class SdkStatus { UNKNOWN, CONNECTING, READY, UNAVAILABLE }

data class NaviState(
    val status: SdkStatus = SdkStatus.UNKNOWN,
    val navigation: NavigationStatus? = null,
    val destinationName: String? = null,
    val remainTimeSec: Int? = null,
    val remainDistanceM: Int? = null,
    /** Distance to the next rest area / service area on the route, if the next turn is one. */
    val restAreaAheadKm: Double? = null,
    val speedLimit: Int? = null,
    val lastError: String? = null,
) {
    val routing: Boolean get() = navigation == NavigationStatus.ROUTE_DRIVE
}

/**
 * Wrapper around the Pleos NaviHelper SDK. Info callbacks are request-driven, so destination/TBT
 * are polled while a route is active. Never blocks the app: init failure → [SdkStatus.UNAVAILABLE].
 */
class NaviSource(context: Context) {

    private val navi = NaviHelper(context.applicationContext)
    private val _state = MutableStateFlow(NaviState())
    val state: StateFlow<NaviState> = _state
    private var pollJob: Job? = null

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            _state.update { it.copy(navigation = status) }
            if (status != NavigationStatus.ROUTE_DRIVE) {
                _state.update { it.copy(destinationName = null, remainTimeSec = null, remainDistanceM = null, restAreaAheadKm = null) }
            }
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            _state.update {
                it.copy(
                    navigation = it.navigation ?: NavigationStatus.ROUTE_DRIVE,
                    destinationName = info.name.ifBlank { null },
                    remainTimeSec = info.remainTime,
                    remainDistanceM = info.remainDistance,
                )
            }
        }

        override fun onTBTInfo(tbt: List<TBTInfo>) {
            val rest = tbt.firstOrNull { it.type == TurnType.TURN_RESTAREA || it.type == TurnType.TURN_ENTER_SERVICEAREA }
            _state.update { it.copy(restAreaAheadKm = rest?.let { r -> r.distance / 1000.0 }) }
        }

        override fun onRouteStarted(info: RouteStartInfo) {
            _state.update { it.copy(navigation = NavigationStatus.ROUTE_DRIVE, destinationName = info.name.ifBlank { null }, remainTimeSec = info.duration, remainDistanceM = info.distance) }
            requestInfo()
        }

        override fun onRouteEnded() {
            _state.update { it.copy(navigation = NavigationStatus.FREE_DRIVE, destinationName = null, remainTimeSec = null, remainDistanceM = null, restAreaAheadKm = null) }
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) = onRouteEnded()

        override fun onCurrentRoadSpeedLimit(limit: Int) {
            _state.update { it.copy(speedLimit = limit.takeIf { l -> l > 0 }) }
        }

        override fun onError(code: NaviErrorCode) {
            _state.update { it.copy(lastError = code.name) }
        }
    }

    fun start(scope: CoroutineScope) {
        if (_state.value.status == SdkStatus.READY || _state.value.status == SdkStatus.CONNECTING) return
        _state.update { it.copy(status = SdkStatus.CONNECTING) }
        try {
            navi.initialize(
                {
                    navi.addListener(listener)
                    _state.update { it.copy(status = SdkStatus.READY) }
                    requestInfo()
                    pollJob?.cancel()
                    pollJob = scope.launch {
                        while (isActive) {
                            delay(POLL_MS)
                            if (_state.value.routing) requestInfo()
                        }
                    }
                },
                { t ->
                    Log.w(TAG, "NaviHelper unavailable: ${t.message}")
                    _state.update { it.copy(status = SdkStatus.UNAVAILABLE, lastError = t.message) }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "NaviHelper initialize threw", e)
            _state.update { it.copy(status = SdkStatus.UNAVAILABLE, lastError = e.message) }
        }
    }

    fun requestInfo() {
        runCatching {
            navi.getRouteStateInfo()
            navi.getDestinationInfo()
            navi.getTBTInfo()
        }.onFailure { Log.w(TAG, "NaviHelper request failed: ${it.message}") }
    }

    fun release() {
        pollJob?.cancel()
        runCatching { navi.removeListener(listener); navi.release() }
        _state.update { NaviState() }
    }
}
