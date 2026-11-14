package com.dogfootprint.app

import android.app.Application
import com.dogfootprint.app.audio.CalmingPlayer
import com.dogfootprint.app.care.CareEngine
import com.dogfootprint.app.data.AppRepository
import com.dogfootprint.app.data.appDataStore
import com.dogfootprint.app.pleos.ChimeVoiceGuide
import com.dogfootprint.app.pleos.CompositeVoiceGuide
import com.dogfootprint.app.pleos.GuidanceOnlyActuator
import com.dogfootprint.app.pleos.LocationSource
import com.dogfootprint.app.pleos.NaviSource
import com.dogfootprint.app.pleos.PleosHvacActuator
import com.dogfootprint.app.pleos.PleosTtsVoiceGuide
import com.dogfootprint.app.pleos.VehicleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Composition root. Everything the care engine needs lives for the process lifetime. */
class App : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository by lazy { AppRepository(appDataStore) }
    val vehicle by lazy { VehicleSource(this) }
    val navi by lazy { NaviSource(this) }
    val location by lazy { LocationSource(this) }
    val pleosTts by lazy { PleosTtsVoiceGuide(this, BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET) }
    private val chime by lazy { ChimeVoiceGuide() }
    private val voice by lazy { CompositeVoiceGuide(pleosTts, chime) { engine.state.value.data.settings.pleosTts } }
    val calm by lazy { CalmingPlayer(this, voice) }

    val engine: CareEngine by lazy {
        CareEngine(
            context = this,
            repo = repository,
            vehicle = vehicle,
            navi = navi,
            location = location,
            voice = voice,
            calm = calm,
            actuatorFor = { enabled -> if (enabled) PleosHvacActuator(vehicle) else GuidanceOnlyActuator },
            scope = scope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        engine.start()
        pleosTts.connect()
    }
}
