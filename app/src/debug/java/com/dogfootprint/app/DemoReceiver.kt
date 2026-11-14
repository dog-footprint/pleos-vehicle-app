package com.dogfootprint.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dogfootprint.app.care.DemoClimate

/**
 * Debug-only demo triggers for recordings and live demos, so the Settings screen never has to be opened:
 *   adb shell am broadcast --user 10 -a com.dogfootprint.app.DEMO -n com.dogfootprint.app/.DemoReceiver --es action <name>
 * Scenario values match the Settings developer buttons (ComfortRules thresholds): cold 16/18 °C, hot 27/24 °C.
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = (context.applicationContext as App).engine
        val action = intent.getStringExtra("action")
        Log.i("DogFootprint", "demo action=$action")
        when (action) {
            "cold" -> engine.setDemoClimate(DemoClimate(zoneTemp = 16f, targetTemp = 18f, hvacPower = true))
            "hot" -> engine.setDemoClimate(DemoClimate(zoneTemp = 27f, targetTemp = 24f, hvacPower = true))
            "hvac_off" -> engine.setDemoClimate(DemoClimate(zoneTemp = 27f, hvacPower = false))
            "clear" -> engine.setDemoClimate(null)
            "break" -> engine.demoBreakDue()
            "brake" -> engine.demoBrakeBurst()
            "belt_on" -> engine.setDemoBelt(true)
            "belt_off" -> engine.setDemoBelt(false)
            "belt_clear" -> engine.setDemoBelt(null)
            "pet_alone" -> engine.demoPetAlone()
            "pet_alone_risk" -> engine.demoPetAloneRisk()
            "record_on" -> engine.setRecordMode(true)
            "record_off" -> engine.setRecordMode(false)
            "calm" -> engine.toggleCalm()
            else -> Log.w("DogFootprint", "unknown demo action: $action")
        }
    }
}
