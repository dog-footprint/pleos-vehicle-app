package com.dogfootprint.app.care

import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.content.Context
import android.util.Log
import com.dogfootprint.app.R
import com.dogfootprint.app.audio.CalmingPlayer
import com.dogfootprint.app.data.AgeGroup
import com.dogfootprint.app.data.AppData
import com.dogfootprint.app.data.CalmMode
import com.dogfootprint.app.data.AppRepository
import com.dogfootprint.app.data.DogProfile
import com.dogfootprint.app.data.DogSeat
import com.dogfootprint.app.data.EventType
import com.dogfootprint.app.data.Mood
import com.dogfootprint.app.data.Trip
import com.dogfootprint.app.data.TripEvent
import com.dogfootprint.app.pleos.ApplyResult
import com.dogfootprint.app.pleos.ClimateActuator
import com.dogfootprint.app.pleos.GuidePriority
import com.dogfootprint.app.pleos.LocationSource
import com.dogfootprint.app.pleos.NaviSource
import com.dogfootprint.app.pleos.NaviState
import com.dogfootprint.app.pleos.SdkStatus
import com.dogfootprint.app.pleos.VehicleSource
import com.dogfootprint.app.pleos.VoiceGuide
import com.dogfootprint.app.pleos.ZoneAreas
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "DogFootprint"
private const val TICK_MS = 1_000L
private const val PERSIST_EVERY_MS = 30_000L
private const val FAN_DIRECTION_POLL_MS = 30_000L
private const val PET_ALONE_GUIDE_COOLDOWN_MS = 5 * 60_000L

/** Demo override of what the vehicle reports, so a scripted scenario is self-consistent (measured, set-point, power). */
data class DemoClimate(val zoneTemp: Float, val targetTemp: Float? = null, val hvacPower: Boolean? = null)

data class VehicleSnapshot(
    val odometerKm: Float? = null,
    val vehicleTripSec: Int? = null,
    val soc: Int? = null,
    val zoneTemp: Float? = null,
    /** True when the dog's seat zone has no temperature sensor and ROW_1_LEFT is shown instead. */
    val zoneTempFallback: Boolean = false,
    val targetTemp: Float? = null,
    val hvacPower: Boolean? = null,
    val fanSpeed: Int? = null,
    val fanMax: Int? = null,
    val fanDirection: FanDirectionType? = null,
    val occupancy: Boolean? = null,
    val beltBuckled: Boolean? = null,
    val windowPositions: Map<WindowValue, Int> = emptyMap(),
    val windowMax: Int? = null,
    val doorPositions: Map<DoorValue, Int> = emptyMap(),
    /** Driver's door (ROW_1_LEFT) — used to notice the driver leaving the dog in a parked car. */
    val driverDoorOpen: Boolean? = null,
    /** Front passenger seat — someone staying in the car means the dog is not alone (3rd review item 7). */
    val passengerOccupied: Boolean? = null,
    /** Power-down warning telltale; the SDK has no ignition state. */
    val powerDownWarning: Boolean? = null,
    val brakePressed: Boolean? = null,
    val drivingMode: DrivingModeType? = null,
    val gearPark: Boolean? = null,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val windowPosition: Int? get() = windowPositions.values.maxOrNull()
    val doorOpen: Boolean? get() = if (doorPositions.isEmpty()) null else doorPositions.values.any { it > 0 }
}

data class Guidance(val text: String, val priority: GuidePriority, val at: Long)

enum class SafetyFlag { WINDOW_OPEN, BELT_UNBUCKLED, DOOR_OPEN, SPORT_MODE, HVAC_OFF, NOT_ON_SEAT }

data class Diagnostics(
    val vehicle: SdkStatus = SdkStatus.UNKNOWN,
    val vehicleDetail: String? = null,
    val navi: SdkStatus = SdkStatus.UNKNOWN,
    val tts: SdkStatus = SdkStatus.UNKNOWN,
    val ttsDetail: String? = null,
    val location: SdkStatus = SdkStatus.UNKNOWN,
)

data class CareState(
    val data: AppData = AppData(),
    val dog: DogProfile? = null,
    val aboard: Boolean = false,
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val moving: Boolean = false,
    val band: ComfortBand? = null,
    val climate: ClimatePlan? = null,
    val heatAlert: Boolean = false,
    val lastApply: ApplyResult? = null,
    val applying: Boolean = false,
    val trip: Trip? = null,
    val breakIntervalMin: Int? = null,
    val drivenSinceBreakSec: Int = 0,
    val breakPhase: BreakPhase = BreakPhase.NONE,
    val restAreaSuggested: Boolean = false,
    val navi: NaviState = NaviState(),
    val safety: Set<SafetyFlag> = emptySet(),
    val calmOn: Boolean = false,
    val banner: Guidance? = null,
    val askTripEnd: Boolean = false,
    /** Parked, dog on board, driver's door opened and closed → the dog is alone in the car. */
    val petAlone: Boolean = false,
    val petAloneSince: Long? = null,
    /** AAHA life stage from the birth date (or the manual value). */
    val lifeStage: AgeGroup? = null,
    val mealNotice: MealNotice = MealNotice.NONE,
    val diagnostics: Diagnostics = Diagnostics(),
    val demoClimate: DemoClimate? = null,
    /** Pet alone and the cabin is a DANGER (ComfortRules.hvacOffLevel / out of band) — the voice condition, shared with the UI. */
    val petAloneRisky: Boolean = false,
    /** How bad the cabin is for a dog left in it: null = fine, CAUTION = climate off but inside the band, DANGER = outside or heading out. */
    val cabinLevel: AlertLevel? = null,
    /** Persistent warnings, highest level first (ComfortRules.activeAlerts). */
    val alerts: List<Alert> = emptyList(),
    /** CAUTION alerts the driver closed; they stay as a status line until the condition ends. */
    val dismissedCautions: Set<AlertKind> = emptySet(),
    /** A trip that just ended and still needs its mood check-in on the Home screen. */
    val pendingCheckIn: Trip? = null,
    /** Leaving, stage 2: parked, dog on board, driver's door open (ComfortRules.isPreExit). */
    val preExit: Boolean = false,
    /** Leaving, stage 1: parked with a risky cabin — reminder to leave the climate on. */
    val leavePrep: Boolean = false,
    /** Pet alone and the driver's door opened again: show the summary and the way out. */
    val driverBack: Boolean = false,
    val petAloneMaxTemp: Float? = null,
) {
    val dogTrips: List<Trip> get() = dog?.let { d -> data.trips.filter { it.dogId == d.id && !it.isActive } } ?: emptyList()
    val totalKm: Float get() = dogTrips.sumOf { it.distanceKm.toDouble() }.toFloat()
    /** The one alert the slot shows; dismissed cautions fall through to the next. */
    val topAlert: Alert? get() = alerts.firstOrNull { it.level == AlertLevel.DANGER || it.kind !in dismissedCautions }
}

/**
 * Everything that only means something during a trip goes back to idle: break timer, meal notice, apply result,
 * banner, boarding and the trip itself. Pure so the reset is unit-tested (review §2-D).
 */
fun CareState.afterTripEnd(): CareState = copy(
    trip = null, askTripEnd = false, drivenSinceBreakSec = 0, breakPhase = BreakPhase.NONE, lastApply = null, banner = null,
    mealNotice = MealNotice.NONE, heatAlert = false, alerts = emptyList(), dismissedCautions = emptySet(), aboard = false,
    petAlone = false, petAloneSince = null, petAloneRisky = false, cabinLevel = null, preExit = false, leavePrep = false, driverBack = false, petAloneMaxTemp = null,
    data = data.copy(aboardDogId = null, lastMealAt = null),
)

/**
 * Application-scoped brain: combines the dog profile with live vehicle / navigation signals into
 * [CareState], records trips ("footprints"), and issues guidance. Rules live in ComfortRules.kt.
 */
class CareEngine(
    private val context: Context,
    private val repo: AppRepository,
    private val vehicle: VehicleSource,
    private val navi: NaviSource,
    private val location: LocationSource,
    private val voice: VoiceGuide,
    private val calm: CalmingPlayer,
    private val actuatorFor: (controlEnabled: Boolean) -> ClimateActuator,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(CareState())
    val state: StateFlow<CareState> = _state

    private val snap = MutableStateFlow(VehicleSnapshot())
    private val motion = MotionDetector()
    private val brakeDetector = BrakeBurstDetector()
    private var zoneJob: Job? = null
    private var zoneSeat: DogSeat? = null
    /** HVAC areas resolved from the vehicle's capability lists for the current dog seat. */
    @Volatile private var zoneAreas: ZoneAreas? = null
    private var locationJob: Job? = null
    private var started = false

    // Trip-local bookkeeping (reset in clearTripState)
    private var parkedSince: Long? = null
    private var doorOpenedWhileParked = false
    private var outOfBandSince: Long? = null
    private var lastTempEventAt: Long? = null
    private var lastPersistAt = 0L
    private var sportFlagged = false
    private var beltFlagged = false
    private var windowFlagged = false
    private var lastBreakPhase = BreakPhase.NONE
    private var restAreaSuggestedAtKm: Double? = null
    private var demoClimate: DemoClimate? = null
    private var demoBelt: Boolean? = null
    private var doorClosedAt: Long? = null
    private var petAloneMax: Float? = null
    private var prevGearPark: Boolean? = null
    private var leavePrep = false
    private var leavePrepSpoken = false
    private var driverBack = false
    /** (time, seat temperature) over the last two minutes — the trend behind hvacOffLevel. */
    private val tempHistory = ArrayDeque<Pair<Long, Float>>()
    private var driverDoorCycled = false
    private var petAloneSince: Long? = null
    private var lastPetAloneGuideAt: Long? = null

    fun start() {
        if (started) return
        started = true

        val ok = vehicle.initialize()
        diag { it.copy(vehicle = if (ok) SdkStatus.CONNECTING else SdkStatus.UNAVAILABLE) }

        scope.launch {
            repo.data.collect { data ->
                val dog = data.activeDog
                calm.volume = data.settings.calmVolume
                _state.update { it.copy(data = data, dog = dog, aboard = dog != null && data.aboardDogId == dog.id) }
                val seat = dog?.seat
                if (seat != zoneSeat) { zoneSeat = seat; if (seat != null) watchZone(seat) else zoneJob?.cancel() }
            }
        }

        watch("distanceDriven", vehicle.distanceDriven()) { v -> snap.update { it.copy(odometerKm = v) } }
        watch("tripDrivingTime", vehicle.tripDrivingTime()) { v -> snap.update { it.copy(vehicleTripSec = v) } }
        watch("evStateOfCharge", vehicle.evStateOfCharge()) { v -> snap.update { it.copy(soc = v) } }
        watch("drivingMode", vehicle.drivingMode()) { v -> snap.update { it.copy(drivingMode = v) } }
        watch("gear", vehicle.gear()) { v -> snap.update { it.copy(gearPark = v == VehicleGear.GEAR_PARK) } }
        watch("driverDoor", vehicle.doorPosition(DoorArea(DoorValue.ROW_1_LEFT))) { v -> snap.update { it.copy(driverDoorOpen = v > 0) } }
        watch("passengerOccupancy", vehicle.seatOccupancy(SeatArea(SeatValue.ROW_1_RIGHT))) { v -> snap.update { it.copy(passengerOccupied = v) } }
        watch("powerDownWarning", vehicle.powerDownWarning()) { v -> snap.update { it.copy(powerDownWarning = v) } }
        watch("brakePedal", vehicle.brakePedal()) { pressed ->
            snap.update { it.copy(brakePressed = pressed) }
            if (brakeDetector.onPedal(pressed, clock(), motion.moving)) onBrakeBurst()
        }

        navi.start(scope)
        scope.launch { navi.state.collect { n -> _state.update { it.copy(navi = n) }; diag { it.copy(navi = n.status) } } }
        scope.launch { voice.status.collect { s -> diag { it.copy(tts = s) } } }
        scope.launch { voice.detail.collect { d -> diag { it.copy(ttsDetail = d) } } }
        scope.launch { calm.playing.collect { p -> _state.update { it.copy(calmOn = p) } } }

        scope.launch {
            while (isActive) {
                runCatching { tick() }.onFailure { Log.e(TAG, "tick failed", it) }
                delay(TICK_MS)
            }
        }
    }

    /** Call once the FUSED_LOCATION runtime permission is granted. */
    fun startLocation() {
        if (locationJob != null) return
        if (!location.initialize()) { diag { it.copy(location = SdkStatus.UNAVAILABLE) }; return }
        diag { it.copy(location = SdkStatus.CONNECTING) }
        locationJob = scope.launch {
            location.updates().collect { r ->
                r.onSuccess { l -> snap.update { it.copy(lat = l.latitude, lon = l.longitude) }; diag { it.copy(location = SdkStatus.READY) } }
                    .onFailure { diag { it.copy(location = SdkStatus.UNAVAILABLE) } }
            }
        }
    }

    // ---- actions -------------------------------------------------------------------------------

    fun setAboard(on: Boolean) {
        val dog = _state.value.dog ?: return
        scope.launch { repo.setAboard(if (on) dog.id else null) }
    }

    /** Boarding with the last meal time (null = unknown). Warns once if the meal was under two hours ago. */
    fun board(lastMealAt: Long?) {
        val dog = _state.value.dog ?: return
        scope.launch { repo.setLastMeal(lastMealAt); repo.setAboard(dog.id) }
        if (mealNotice(lastMealAt, clock(), dog) == MealNotice.WARN) guide(context.getString(R.string.guide_meal_warn), GuidePriority.WARN)
    }

    fun startTripNow() {
        val s = _state.value
        val dog = s.dog ?: return
        if (s.trip != null) return
        scope.launch { if (!s.aboard) repo.setAboard(dog.id) }
        _state.update { it.copy(trip = newTrip(dog, snap.value, clock())) }
    }

    fun endTrip(mood: Mood?, note: String?) {
        val trip = _state.value.trip ?: return
        val now = clock()
        val done = trip.withEvent(EventType.TRIP_END, now).copy(
            endedAt = now, mood = mood, note = note?.takeIf { it.isNotBlank() },
            endKm = snap.value.odometerKm ?: trip.endKm ?: trip.startKm, endSoc = snap.value.soc ?: trip.endSoc,
            endLat = snap.value.lat ?: trip.endLat, endLon = snap.value.lon ?: trip.endLon,
        )
        clearTripState()
        driverDoorCycled = false; petAloneSince = null
        _state.update { it.afterTripEnd().copy(pendingCheckIn = if (mood == null) done else null) }
        scope.launch { repo.upsertTrip(done); repo.setAboard(null); repo.setLastMeal(null) }
    }

    /** Mood chosen on the Home check-in card (one tap saves). */
    fun checkIn(mood: Mood) {
        val trip = _state.value.pendingCheckIn ?: return
        val saved = trip.copy(mood = mood)
        _state.update { it.copy(pendingCheckIn = saved) }
        scope.launch { repo.upsertTrip(saved) }
    }

    fun addNote(note: String) {
        val trip = _state.value.pendingCheckIn ?: return
        val saved = trip.copy(note = note.takeIf { it.isNotBlank() })
        _state.update { it.copy(pendingCheckIn = saved) }
        scope.launch { repo.upsertTrip(saved) }
    }

    fun dismissCheckIn() = _state.update { it.copy(pendingCheckIn = null) }

    /** "내리기": ends the trip if one is running, otherwise just clears boarding. */
    fun unboard() {
        if (_state.value.trip != null) endTrip(null, null) else { setAboard(false); _state.update { it.copy(mealNotice = MealNotice.NONE) }; scope.launch { repo.setLastMeal(null) } }
    }

    fun dismissAlert(kind: AlertKind) = _state.update { it.copy(dismissedCautions = it.dismissedCautions + kind) }

    fun markBreakTaken() {
        val trip = _state.value.trip ?: return
        _state.update { it.copy(trip = trip.withEvent(EventType.BREAK_TAKEN, clock()).copy(breaks = trip.breaks + 1), drivenSinceBreakSec = 0, breakPhase = BreakPhase.NONE) }
        lastBreakPhase = BreakPhase.NONE
        doorOpenedWhileParked = false
        guide(context.getString(R.string.guide_break_taken), GuidePriority.INFO)
    }

    fun applyClimate() {
        val plan = _state.value.climate ?: return
        applyPlan(plan, R.string.guide_climate_applied)
    }

    /** The one climate button (leaving / pet alone): the shared cause → action mapping, aimed at the band centre. */
    fun keepClimate() {
        val s = _state.value
        val band = s.band ?: return
        val plan = when (val action = climateAction(s.vehicle.hvacPower, s.climate?.verdict, band)) {
            ClimateAction.TurnOn -> ClimatePlan(ClimateVerdict.HVAC_OFF, targetTemp = bandCentre(band).toFloat())
            is ClimateAction.CoolTo -> ClimatePlan(ClimateVerdict.TOO_HOT, targetTemp = action.target.toFloat(), fanSpeed = s.climate?.fanSpeed, direction = s.climate?.direction)
            is ClimateAction.HeatTo -> ClimatePlan(ClimateVerdict.TOO_COLD, targetTemp = action.target.toFloat(), direction = s.climate?.direction)
            null -> return
        }
        applyPlan(plan, if (plan.verdict == ClimateVerdict.HVAC_OFF) R.string.guide_climate_kept else R.string.guide_climate_applied)
    }

    private fun applyPlan(plan: ClimatePlan, doneRes: Int) {
        val s = _state.value
        val dog = s.dog ?: return
        if (s.applying) return
        _state.update { it.copy(applying = true) }
        scope.launch {
            val actuator = actuatorFor(s.data.settings.controlEnabled)
            val areas = zoneAreas ?: ZoneAreas.single(dog.seat.zone().tempSeat)
            val result = runCatching { actuator.apply(plan, dog.seat.zone(), areas, s.moving, closeWindow = windowFlagged) }
                .getOrElse { ApplyResult.NotApplied(ApplyResult.Reason.SDK_ERROR) }
            _state.update { st ->
                val trip = if (result is ApplyResult.Applied || result is ApplyResult.Partial) st.trip?.withEvent(EventType.CLIMATE_APPLIED, clock()) else st.trip
                st.copy(applying = false, lastApply = result, trip = trip)
            }
            if (result is ApplyResult.Applied || result is ApplyResult.Partial) {
                // Keep a demo scenario consistent with what was just set, so the Home line does not show the old set-point.
                demoClimate?.let { d -> demoClimate = d.copy(targetTemp = plan.targetTemp ?: d.targetTemp, hvacPower = true) }
                guide(context.getString(doneRes, zoneLabel(dog.seat)), GuidePriority.INFO)
            }
        }
    }

    fun setDemoBelt(buckled: Boolean?) { demoBelt = buckled }
    fun setRecordMode(on: Boolean) = voice.setMediaUsage(on)

    fun toggleCalm() {
        if (calm.playing.value) {
            calm.stop(); addEvent(EventType.CALM_OFF)
        } else {
            val s = _state.value
            val name = s.dog?.name ?: ""
            val lines = context.resources.getStringArray(R.array.calm_story).map { it.format(name) }
            // The story needs a real voice; with Pleos TTS off or unavailable it would be a chime every few seconds.
            val ttsUsable = s.data.settings.pleosTts && s.diagnostics.tts == SdkStatus.READY
            val mode = if (s.data.settings.calmMode == CalmMode.STORY && !ttsUsable) CalmMode.MUSIC else s.data.settings.calmMode
            calm.start(scope, mode, lines); addEvent(EventType.CALM_ON)
        }
    }

    /** Restart playback when the mode changes while playing. */
    fun restartCalmIfPlaying() { if (calm.playing.value) { calm.stop(); toggleCalm() } }

    fun setCalmVolume(v: Float) {
        calm.volume = v
        scope.launch { repo.updateSettings { it.copy(calmVolume = v) } }
    }

    fun dismissBanner() = _state.update { it.copy(banner = null) }
    fun dismissTripEndPrompt() { parkedSince = clock(); _state.update { it.copy(askTripEnd = false) } }

    // Demo triggers (Settings → 진단), so a presentation does not depend on emulator timing.
    fun demoBrakeBurst() = onBrakeBurst()
    fun demoBreakDue() {
        val s = _state.value
        if (s.trip == null) startTripNow()
        val interval = s.breakIntervalMin ?: s.dog?.let { breakIntervalMin(it, s.heatAlert, it.lifeStage()) } ?: BREAK_ADULT_MIN
        _state.update { it.copy(drivenSinceBreakSec = interval * 60) }
    }
    /** Demo: pretend the driver got out while the dog stayed (parked + 탑승 required). */
    fun demoPetAlone() {
        val s = _state.value
        if (s.dog == null) return
        if (!s.aboard) setAboard(true)
        driverDoorCycled = true
        petAloneSince = clock()
        doorClosedAt = petAloneSince; petAloneMax = null; driverBack = false
        lastPetAloneGuideAt = null
    }

    fun setDemoClimate(d: DemoClimate?) {
        demoClimate = d
        _state.update { it.copy(lastApply = null) }   // a new scenario should not keep the previous apply line
        // Skip the out-of-band grace/cooldown so the guidance fires on the next tick.
        outOfBandSince = if (d != null) clock() - TEMP_OUT_OF_BAND_GRACE_MS else null
        lastTempEventAt = null
    }
    /** Demo: the driver left, the cabin is hot and the climate is off — the worst pet-alone case. */
    fun demoPetAloneRisk() {
        demoPetAlone()
        setDemoClimate(DemoClimate(zoneTemp = 27f, hvacPower = false))
    }

    // ---- internals -------------------------------------------------------------------------------

    private fun <T> watch(label: String, flow: Flow<Result<T>>, onValue: (T) -> Unit) = scope.launch {
        flow.collect { r ->
            r.onSuccess { v -> onValue(v); diag { if (it.vehicle != SdkStatus.READY) it.copy(vehicle = SdkStatus.READY) else it } }
                .onFailure { e -> sdkError(label, e) }
        }
    }

    private fun sdkError(label: String, e: Throwable) {
        Log.w(TAG, "Vehicle SDK [$label]: ${e.message}")
        diag { it.copy(vehicleDetail = e.message ?: e::class.simpleName) }
        if (e is SecurityException) diag { it.copy(vehicle = SdkStatus.UNAVAILABLE) }
    }

    private fun diag(transform: (Diagnostics) -> Diagnostics) = _state.update { it.copy(diagnostics = transform(it.diagnostics)) }

    private fun watchZone(seat: DogSeat) {
        zoneJob?.cancel()
        snap.update { it.copy(zoneTemp = null, targetTemp = null, hvacPower = null, fanSpeed = null, fanDirection = null, occupancy = null, beltBuckled = null, windowPositions = emptyMap(), doorPositions = emptyMap()) }
        zoneJob = scope.launch {
            val zone = seat.zone()
            // The vehicle's HVAC zones are multi-seat areas (e.g. "rear" = ROW_2_LEFT+ROW_2_RIGHT), so use the
            // SeatArea instances the capability APIs hand back rather than constructing single-seat ones.
            val sensedAreas = vehicle.hvacCurrentTempAreas()
            val tempBounds = vehicle.hvacTempBounds()
            val fanBounds = vehicle.hvacFanSpeedBounds()
            val powerAreas = vehicle.hvacPowerAreas()
            Log.i(TAG, "HVAC zones: current=${sensedAreas?.map { it.values.toList() }} target=${tempBounds?.keys?.map { it.values.toList() }} fan=${fanBounds?.keys?.map { it.values.toList() }} power=${powerAreas?.map { it.values.toList() }}")
            fun pick(areas: Collection<SeatArea>?): SeatArea? =
                areas?.firstOrNull { it.values.contains(zone.tempSeat) }
                    ?: areas?.firstOrNull { it.values.contains(FALLBACK_TEMP_SEAT) }
                    ?: areas?.firstOrNull()
            val currentArea = pick(sensedAreas) ?: SeatArea(zone.tempSeat)
            val targetArea = pick(tempBounds?.keys) ?: currentArea
            val fanArea = pick(fanBounds?.keys) ?: targetArea
            val powerArea = pick(powerAreas) ?: fanArea
            val fanMax = fanBounds?.get(fanArea)?.maxValue
            val winBounds = vehicle.windowBounds()
            val winMax = winBounds?.entries?.firstOrNull { e -> zone.windows.any { e.key.values.contains(it) } }?.value?.maxValue
                ?: winBounds?.values?.firstOrNull()?.maxValue
            snap.update { it.copy(zoneTempFallback = !currentArea.values.contains(zone.tempSeat), fanMax = fanMax, windowMax = winMax) }

            zoneAreas = ZoneAreas(currentArea, targetArea, fanArea, powerArea)
            watch("hvacCurrentTemp", vehicle.hvacCurrentTemp(currentArea)) { v -> snap.update { it.copy(zoneTemp = v) } }
            watch("hvacTargetTemp", vehicle.hvacTargetTemp(targetArea)) { v -> snap.update { it.copy(targetTemp = v) } }
            watch("hvacPower", vehicle.hvacPower(powerArea)) { v -> snap.update { it.copy(hvacPower = v) } }
            watch("hvacFanSpeed", vehicle.hvacFanSpeed(fanArea)) { v -> snap.update { it.copy(fanSpeed = v) } }
            launch {
                while (isActive) {
                    vehicle.hvacFanDirection(fanArea)?.let { d -> snap.update { it.copy(fanDirection = d) } }
                    delay(FAN_DIRECTION_POLL_MS)
                }
            }
            zone.occupancySeat?.let { s ->
                val seatArea = SeatArea(s)
                watch("seatOccupancy", vehicle.seatOccupancy(seatArea)) { v -> snap.update { it.copy(occupancy = v) } }
                watch("seatBeltBuckled", vehicle.seatBeltBuckled(seatArea)) { v -> snap.update { it.copy(beltBuckled = v) } }
            }
            zone.windows.forEach { w ->
                watch("windowPosition", vehicle.windowPosition(WindowArea(w))) { v -> snap.update { it.copy(windowPositions = it.windowPositions + (w to v)) } }
            }
            zone.doors.forEach { d ->
                watch("doorPosition", vehicle.doorPosition(DoorArea(d))) { v -> snap.update { it.copy(doorPositions = it.doorPositions + (d to v)) } }
            }
        }
    }

    private fun tick() {
        val now = clock()
        val s = _state.value
        val v = snap.value
        val moving = motion.update(v.gearPark, v.odometerKm, now)
        val dog = s.dog
        val stage = dog?.lifeStage(now)
        val demo = demoClimate
        val zoneTemp = demo?.zoneTemp ?: v.zoneTemp
        val hvacPower = demo?.hvacPower ?: v.hvacPower
        val beltBuckled = demoBelt ?: v.beltBuckled
        val targetTemp = demo?.targetTemp ?: v.targetTemp
        val band = dog?.let { comfortBand(it, stage!!) }
        val heat = dog != null && isHeatAlert(dog, zoneTemp)
        val meal = if (dog != null) mealNotice(s.data.lastMealAt, now, dog, stage!!) else MealNotice.NONE
        val plan = if (dog != null && band != null) climatePlan(dog, band, zoneTemp, hvacPower, v.fanSpeed, v.fanMax, moving) else null

        var trip = s.trip
        var drivenSinceBreak = s.drivenSinceBreakSec
        var askEnd = s.askTripEnd

        if (dog != null && s.aboard && moving && trip == null) {
            trip = newTrip(dog, v, now)
            guide(context.getString(R.string.guide_trip_started, dog.name), GuidePriority.INFO)
        }

        if (trip != null) {
            if (moving) {
                trip = trip.copy(drivingSec = trip.drivingSec + 1)
                drivenSinceBreak += 1
                if (parkedSince != null) { parkedSince = null; doorOpenedWhileParked = false }
            } else {
                val since = parkedSince ?: now.also { parkedSince = it }
                if (v.doorOpen == true) doorOpenedWhileParked = true
                val parkedFor = now - since
                if (parkedFor >= BREAK_TAKEN_PARKED_MS && doorOpenedWhileParked && drivenSinceBreak > 0) {
                    trip = trip.withEvent(EventType.BREAK_TAKEN, now).copy(breaks = trip.breaks + 1)
                    drivenSinceBreak = 0
                    doorOpenedWhileParked = false
                    lastBreakPhase = BreakPhase.NONE
                    guide(context.getString(R.string.guide_break_taken), GuidePriority.INFO)
                }
                if (parkedFor >= TRIP_END_PROMPT_PARKED_MS && !askEnd) askEnd = true
            }
            trip = trip.copy(
                endKm = v.odometerKm ?: trip.endKm, endSoc = v.soc ?: trip.endSoc,
                endLat = v.lat ?: trip.endLat, endLon = v.lon ?: trip.endLon,
                destinationName = s.navi.destinationName ?: trip.destinationName,
            )
        }

        val interval = dog?.let { breakIntervalMin(it, heat, stage!!) }
        val phase = if (trip != null && interval != null) breakPhase(drivenSinceBreak, interval) else BreakPhase.NONE
        if (phase != lastBreakPhase) {
            when (phase) {
                BreakPhase.PRE_ALERT -> guide(context.getString(R.string.guide_break_pre, BREAK_PREALERT_MIN, dog?.name ?: ""), GuidePriority.INFO)
                BreakPhase.DUE -> {
                    trip = trip?.withEvent(EventType.BREAK_SUGGESTED, now)
                    guide(context.getString(R.string.guide_break_due, dog?.name ?: ""), GuidePriority.ALERT)
                }
                BreakPhase.NONE -> Unit
            }
            lastBreakPhase = phase
        }

        val restKm = s.navi.restAreaAheadKm
        val suggestRest = trip != null && interval != null && suggestRestArea(restKm, drivenSinceBreak, interval, s.navi.remainTimeSec)
        if (suggestRest && restAreaSuggestedAtKm == null && restKm != null) {
            restAreaSuggestedAtKm = restKm
            guide(context.getString(R.string.guide_rest_area, restKm, dog?.name ?: ""), GuidePriority.WARN)
        }
        if (restKm == null) restAreaSuggestedAtKm = null

        val flags = mutableSetOf<SafetyFlag>()
        val winFrac = windowOpenFraction(v.windowPosition, v.windowMax)
        if (winFrac != null) {
            if (!windowFlagged && moving && winFrac > WINDOW_OPEN_WARN_FRACTION) {
                windowFlagged = true
                trip = trip?.withEvent(EventType.WINDOW_OPEN, now, "${(winFrac * 100).toInt()}%")
                guide(context.getString(R.string.guide_window_open, dog?.let { zoneLabel(it.seat) } ?: ""), GuidePriority.WARN)
            } else if (windowFlagged && winFrac < WINDOW_OPEN_CLEAR_FRACTION) {
                windowFlagged = false
            }
        }
        if (windowFlagged) flags += SafetyFlag.WINDOW_OPEN

        val dogPresent = v.occupancy == true || (v.occupancy == null && s.aboard)
        if (moving && dogPresent && beltBuckled == false) {
            flags += SafetyFlag.BELT_UNBUCKLED
            if (!beltFlagged) {
                beltFlagged = true
                trip = trip?.withEvent(EventType.BELT_UNBUCKLED, now)
                guide(context.getString(R.string.guide_belt, dog?.name ?: ""), GuidePriority.WARN)
            }
        } else if (!moving && s.aboard && dogPresent && beltBuckled == false) {
            flags += SafetyFlag.BELT_UNBUCKLED   // parked after boarding: the one thing to do before leaving (3rd review D2)
        } else if (beltBuckled == true) {
            beltFlagged = false
        }
        if (v.doorOpen == true) flags += SafetyFlag.DOOR_OPEN
        if (v.drivingMode == DrivingModeType.SPORT) {
            flags += SafetyFlag.SPORT_MODE
            if (!sportFlagged && trip != null) {
                sportFlagged = true
                trip = trip.withEvent(EventType.SPORT_MODE, now)
                guide(context.getString(R.string.guide_sport_mode, dog?.name ?: ""), GuidePriority.INFO)
            }
        }
        if (plan?.verdict == ClimateVerdict.HVAC_OFF) flags += SafetyFlag.HVAC_OFF
        if (s.aboard && moving && v.occupancy == false) flags += SafetyFlag.NOT_ON_SEAT

        if (plan != null && plan.verdict in setOf(ClimateVerdict.TOO_HOT, ClimateVerdict.TOO_COLD, ClimateVerdict.HEAT_ALERT, ClimateVerdict.HVAC_OFF)) {
            val since = outOfBandSince ?: now.also { outOfBandSince = it }
            val cooledDown = lastTempEventAt?.let { now - it >= TEMP_OUT_OF_BAND_COOLDOWN_MS } ?: true
            if (now - since >= TEMP_OUT_OF_BAND_GRACE_MS && cooledDown && dog != null) {
                lastTempEventAt = now
                trip = trip?.withEvent(EventType.TEMP_OUT_OF_BAND, now, zoneTemp?.let { "%.1f°C".format(it) })
                // The leaving and pet-alone screens have their own (parked) wording; the driving sentence stays off there.
                if (!s.preExit && !s.petAlone) guide(climateText(dog, plan), if (plan.verdict == ClimateVerdict.HEAT_ALERT) GuidePriority.ALERT else GuidePriority.WARN)
            }
        } else {
            outOfBandSince = null
        }

        // Leaving the car. Stage 1: the P shift (or a power-down warning) with a risky cabin reminds the driver to
        // leave the climate on. Stage 2: the driver's door opens → PreExitHome. Door closed → 30 s grace (driving off
        // or shifting cancels), then the dog counts as alone unless someone is still in the passenger seat.
        zoneTemp?.let { t -> tempHistory.addLast(now to t); while (tempHistory.isNotEmpty() && now - tempHistory.first().first > 120_000L) tempHistory.removeFirst() }
        val ratePerMin = tempHistory.takeIf { it.size >= 2 && it.last().first - it.first().first >= 60_000L }
            ?.let { (it.last().second - it.first().second) / ((it.last().first - it.first().first) / 60_000f) }
        val hvacLevel = hvacOffLevel(zoneTemp, band, ratePerMin)
        val cabinLevel: AlertLevel? = when {
            hvacPower == false -> hvacLevel
            plan != null && plan.needsAction -> AlertLevel.DANGER
            else -> null
        }
        val cabinRisky = cabinLevel != null
        val parkedNow = v.gearPark ?: !moving
        if (moving || !s.aboard || dog == null || v.gearPark == false) {
            driverDoorCycled = false; petAloneSince = null; doorClosedAt = null; petAloneMax = null
            leavePrep = false; leavePrepSpoken = false; driverBack = false
        } else {
            val justParked = parkedNow && prevGearPark == false
            if ((justParked || v.powerDownWarning == true) && cabinRisky && petAloneSince == null && v.driverDoorOpen != true) leavePrep = true
            if (!cabinRisky) leavePrep = false
            if (v.driverDoorOpen == true) {
                driverDoorCycled = true; doorClosedAt = null; leavePrep = false
                if (petAloneSince != null) driverBack = true
                else if (cabinRisky && !leavePrepSpoken) { leavePrepSpoken = true; guide(context.getString(R.string.guide_leave_prep, dog.name), GuidePriority.WARN) }
            } else if (driverDoorCycled && v.driverDoorOpen == false && petAloneSince == null) {
                val closedAt = doorClosedAt ?: now.also { doorClosedAt = it }
                if (now - closedAt >= PET_ALONE_CONFIRM_MS && v.passengerOccupied != true) petAloneSince = closedAt
            }
            if (leavePrep && !leavePrepSpoken) { leavePrepSpoken = true; guide(context.getString(R.string.guide_leave_prep, dog.name), GuidePriority.WARN) }
        }
        prevGearPark = parkedNow
        val petAlone = petAloneSince != null
        val preExit = isPreExit(s.aboard, moving, v.gearPark, v.driverDoorOpen, petAlone)
        if (petAlone) zoneTemp?.let { t -> petAloneMax = maxOf(petAloneMax ?: t, t) }
        val risky = petAlone && cabinLevel == AlertLevel.DANGER
        if (petAlone && dog != null) {
            val cooled = lastPetAloneGuideAt?.let { now - it >= PET_ALONE_GUIDE_COOLDOWN_MS } ?: true
            if (risky && cooled) {
                lastPetAloneGuideAt = now
                val text = if (hvacPower == false) context.getString(R.string.guide_pet_alone_hvac_off, dog.name)
                else context.getString(R.string.guide_pet_alone_temp, dog.name, zoneTemp ?: 0f)
                guide(text, GuidePriority.ALERT)
            }
        }

        trip = trip?.let { t -> t.copy(comfortScore = comfortScore(t.events.groupingBy { e -> e.type }.eachCount())) }
        if (trip != null && now - lastPersistAt >= PERSIST_EVERY_MS) {
            lastPersistAt = now
            val toSave = trip
            scope.launch { repo.upsertTrip(toSave) }
        }

        _state.update {
            it.copy(
                vehicle = v.copy(zoneTemp = zoneTemp, targetTemp = targetTemp, hvacPower = hvacPower, beltBuckled = beltBuckled), moving = moving, band = band, climate = plan, heatAlert = heat,
                trip = trip, breakIntervalMin = interval, drivenSinceBreakSec = drivenSinceBreak, breakPhase = phase,
                restAreaSuggested = suggestRest, safety = flags, askTripEnd = askEnd, demoClimate = demo,
                petAlone = petAlone, petAloneSince = petAloneSince, lifeStage = stage, mealNotice = meal,
                petAloneRisky = risky, cabinLevel = cabinLevel, preExit = preExit, leavePrep = leavePrep, driverBack = driverBack, petAloneMaxTemp = petAloneMax,
                alerts = activeAlerts(alertContext(moving, preExit, petAlone), plan?.verdict, flags, meal, phase, s.aboard, leavePrep, hvacOff = hvacPower == false, hvacOffLevel = hvacLevel),
                pendingCheckIn = if (moving) null else it.pendingCheckIn,   // driving off ends the check-in moment
            ).let { st -> st.copy(dismissedCautions = st.dismissedCautions.filter { k -> st.alerts.any { a -> a.kind == k } }.toSet()) }
        }
    }

    private fun onBrakeBurst() {
        val dog = _state.value.dog
        addEvent(EventType.BRAKE_BURST)
        guide(context.getString(R.string.guide_brake_burst, dog?.name ?: ""), GuidePriority.WARN)
    }

    private fun addEvent(type: EventType, detail: String? = null) =
        _state.update { s -> s.copy(trip = s.trip?.withEvent(type, clock(), detail)) }

    private fun guide(text: String, priority: GuidePriority) {
        _state.update { it.copy(banner = Guidance(text, priority, clock())) }
        scope.launch { runCatching { voice.say(text, priority) }.onFailure { Log.w(TAG, "voice failed", it) } }
    }

    fun climateText(dog: DogProfile, plan: ClimatePlan): String {
        val zone = zoneLabel(dog.seat)
        val target = plan.targetTemp?.toInt() ?: 0
        return when (plan.verdict) {
            ClimateVerdict.TOO_HOT -> context.getString(R.string.guide_too_hot, dog.name, zone, target)
            ClimateVerdict.TOO_COLD -> context.getString(R.string.guide_too_cold, dog.name, zone, target)
            ClimateVerdict.HEAT_ALERT -> context.getString(R.string.guide_heat_alert, dog.name)
            ClimateVerdict.HVAC_OFF -> context.getString(R.string.guide_hvac_off, zone, dog.name)
            ClimateVerdict.OK -> context.getString(R.string.climate_ok, dog.name)
            ClimateVerdict.UNKNOWN -> context.getString(R.string.climate_unknown)
        }
    }

    fun zoneLabel(seat: DogSeat): String = context.getString(
        when (seat) {
            DogSeat.ROW_2_LEFT -> R.string.zone_row2_left
            DogSeat.ROW_2_RIGHT -> R.string.zone_row2_right
            DogSeat.ROW_2_CENTER -> R.string.zone_row2_center
            DogSeat.ROW_1_RIGHT -> R.string.zone_row1_right
            DogSeat.CARGO -> R.string.zone_cargo
        },
    )

    private fun newTrip(dog: DogProfile, v: VehicleSnapshot, now: Long): Trip {
        clearTripState()
        return Trip(
            id = UUID.randomUUID().toString(), dogId = dog.id, startedAt = now,
            startKm = v.odometerKm ?: 0f, startSoc = v.soc, startLat = v.lat, startLon = v.lon,
            events = listOf(TripEvent(now, EventType.TRIP_START)),
        )
    }

    private fun clearTripState() {
        parkedSince = null; doorOpenedWhileParked = false; outOfBandSince = null; lastTempEventAt = null
        lastPersistAt = 0L; sportFlagged = false; beltFlagged = false; windowFlagged = false
        lastBreakPhase = BreakPhase.NONE; restAreaSuggestedAtKm = null
    }

    private fun Trip.withEvent(type: EventType, at: Long, detail: String? = null) = copy(events = events + TripEvent(at, type, detail))
}
