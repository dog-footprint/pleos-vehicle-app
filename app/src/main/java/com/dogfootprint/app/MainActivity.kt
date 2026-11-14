package com.dogfootprint.app

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.dogfootprint.app.ui.CareViewModel
import com.dogfootprint.app.ui.DogFootprintApp
import com.dogfootprint.app.ui.DogFootprintTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        applySeed(intent)
        setContent {
            DogFootprintTheme {
                Surface { DogFootprintApp(viewModel(factory = CareViewModel.factory(app))) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applySeed(intent)
    }

    /**
     * Debug-only demo hook: `scripts/demo-seed.sh <json>` replaces the stored data, so a presentation
     * can start from a known profile without typing on the head unit.
     */
    private fun applySeed(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val b64 = intent?.getStringExtra(EXTRA_SEED_B64) ?: return
        val app = application as App
        lifecycleScope.launch {
            runCatching { app.repository.importJson(String(Base64.decode(b64, Base64.DEFAULT))) }
                .onFailure { Log.w("DogFootprint", "seed import failed", it) }
        }
    }

    private companion object {
        const val EXTRA_SEED_B64 = "seed_b64"
    }
}
