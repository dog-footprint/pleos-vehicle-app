package com.dogfootprint.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dogfootprint.app.App
import com.dogfootprint.app.care.AlertKind
import com.dogfootprint.app.care.CareEngine
import com.dogfootprint.app.care.DemoClimate
import com.dogfootprint.app.care.CareState
import com.dogfootprint.app.data.DogProfile
import com.dogfootprint.app.data.Mood
import com.dogfootprint.app.data.Settings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Thin adapter over the application-scoped [CareEngine] and repository. */
class CareViewModel(private val app: App) : ViewModel() {

    val state: StateFlow<CareState> = app.engine.state
    val engine: CareEngine get() = app.engine

    fun setAboard(on: Boolean) = engine.setAboard(on)
    fun board(lastMealAt: Long?) = engine.board(lastMealAt)
    fun restartCalmIfPlaying() = engine.restartCalmIfPlaying()
    fun startTrip() = engine.startTripNow()
    fun endTrip(mood: Mood?, note: String?) = engine.endTrip(mood, note)
    fun unboard() = engine.unboard()
    fun checkIn(mood: Mood) = engine.checkIn(mood)
    fun addNote(note: String) = engine.addNote(note)
    fun dismissCheckIn() = engine.dismissCheckIn()
    fun dismissAlert(kind: AlertKind) = engine.dismissAlert(kind)
    fun markBreakTaken() = engine.markBreakTaken()
    fun applyClimate() = engine.applyClimate()
    fun keepClimate() = engine.keepClimate()
    fun toggleCalm() = engine.toggleCalm()
    fun setCalmVolume(v: Float) = engine.setCalmVolume(v)
    fun dismissBanner() = engine.dismissBanner()
    fun dismissTripEndPrompt() = engine.dismissTripEndPrompt()
    fun startLocation() = engine.startLocation()

    fun saveDog(dog: DogProfile) = viewModelScope.launch { app.repository.saveDog(dog) }
    fun deleteDog(id: String) = viewModelScope.launch { app.repository.deleteDog(id) }
    fun selectDog(id: String) = viewModelScope.launch { app.repository.setActiveDog(id) }
    fun updateSettings(transform: (Settings) -> Settings) = viewModelScope.launch { app.repository.updateSettings(transform) }

    fun demoBrakeBurst() = engine.demoBrakeBurst()
    fun demoBreakDue() = engine.demoBreakDue()
    fun demoClimate(d: DemoClimate?) = engine.setDemoClimate(d)
    fun demoPetAloneRisk() = engine.demoPetAloneRisk()
    fun demoPetAlone() = engine.demoPetAlone()

    companion object {
        fun factory(app: App): ViewModelProvider.Factory = viewModelFactory { initializer { CareViewModel(app) } }
    }
}
