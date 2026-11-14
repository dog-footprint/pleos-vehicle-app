package com.dogfootprint.app.pleos

import ai.pleos.playground.tts.TextToSpeech
import android.media.AudioAttributes
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "DogFootprint"

enum class GuidePriority { INFO, WARN, ALERT }

/** Spoken or audible guidance. The on-screen banner is handled by the engine, not here. */
interface VoiceGuide {
    val status: StateFlow<SdkStatus>
    val detail: StateFlow<String?>

    /** @return true if something was played. */
    suspend fun say(text: String, priority: GuidePriority = GuidePriority.INFO): Boolean

    fun release() {}

    /**
     * Recording mode (debug demos): route speech as USAGE_MEDIA so the emulator's screen capture, which only
     * records the media bus, picks it up. Production keeps USAGE_ASSISTANCE_NAVIGATION_GUIDANCE (ducks media).
     */
    fun setMediaUsage(on: Boolean) {}
}

/** Always-available fallback: the emulator ships no Android TTS engine, so a chime it is. */
class ChimeVoiceGuide : VoiceGuide {
    override val status = MutableStateFlow(SdkStatus.READY)
    override val detail = MutableStateFlow<String?>(null)
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }

    override suspend fun say(text: String, priority: GuidePriority): Boolean {
        val t = tone ?: return false
        val (type, ms) = when (priority) {
            GuidePriority.INFO -> ToneGenerator.TONE_PROP_BEEP2 to 250
            GuidePriority.WARN -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 400
            GuidePriority.ALERT -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK to 600
        }
        return runCatching { t.startTone(type, ms) }.getOrDefault(false)
    }

    override fun release() { tone?.release() }
}

/**
 * Pleos TextToSpeech. "Not Available Yet" in our Playground project, so this registers lazily and
 * reports [SdkStatus.UNAVAILABLE] (with the error) when the service rejects us.
 */
class PleosTtsVoiceGuide(
    private val context: Context,
    private val clientId: String,
    private val clientSecret: String,
) : VoiceGuide {
    override val status = MutableStateFlow(SdkStatus.UNKNOWN)
    override val detail = MutableStateFlow<String?>(null)
    private var tts: TextToSpeech? = null
    @Volatile private var mediaUsage = false

    /** The service reads the attributes at initialize time, so switching means re-registering (≈1–2 s). */
    override fun setMediaUsage(on: Boolean) {
        if (mediaUsage == on) return
        mediaUsage = on
        release(); connect()
    }

    private fun attributes() = AudioAttributes.Builder()
        .setUsage(if (mediaUsage) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    fun connect() {
        if (status.value == SdkStatus.READY || status.value == SdkStatus.CONNECTING) return
        if (clientId.isBlank() || clientSecret.isBlank()) {
            status.value = SdkStatus.UNAVAILABLE; detail.value = "CLIENT_ID / CLIENT_SECRET not set in .env"; return
        }
        status.value = SdkStatus.CONNECTING
        try {
            val engine = TextToSpeech(context.applicationContext, Mode.HYBRID)
            tts = engine
            runCatching { engine.setAudioAttributes(attributes()) }
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    engine.registerApp(clientId, clientSecret, object : OnServerConnectionListener {
                        override fun onConnected() {
                            runCatching { engine.setLocale(Locale.KOREAN) }
                            status.value = SdkStatus.READY; detail.value = null
                        }

                        override fun onFailed(errorType: ErrorType) = fail("registerApp: $errorType")
                    })
                }

                override fun onFailure(errorType: ErrorType) = fail("initialize: $errorType")
            })
        } catch (e: Exception) {
            fail("threw: ${e.message}")
        }
    }

    private fun fail(reason: String) {
        Log.w(TAG, "Pleos TTS unavailable ($reason)")
        status.value = SdkStatus.UNAVAILABLE
        detail.value = reason
    }

    override suspend fun say(text: String, priority: GuidePriority): Boolean {
        val engine = tts ?: return false
        if (status.value != SdkStatus.READY) return false
        runCatching { engine.setAudioAttributes(attributes()) }
        return runCatching { engine.speak(text, true); true }
            .onFailure { fail("speak: ${it.message}") }
            .getOrDefault(false)
    }

    override fun release() {
        runCatching { tts?.release() }
        tts = null
        status.value = SdkStatus.UNKNOWN
    }
}

/** Speaks through [primary] when it is ready and enabled, otherwise chimes. */
class CompositeVoiceGuide(
    private val primary: VoiceGuide,
    private val fallback: VoiceGuide,
    private val usePrimary: () -> Boolean,
) : VoiceGuide {
    override fun setMediaUsage(on: Boolean) { primary.setMediaUsage(on); fallback.setMediaUsage(on) }
    override val status get() = primary.status
    override val detail get() = primary.detail

    override suspend fun say(text: String, priority: GuidePriority): Boolean {
        if (usePrimary() && primary.status.value == SdkStatus.READY && primary.say(text, priority)) return true
        return fallback.say(text, priority)
    }

    override fun release() { primary.release(); fallback.release() }
}
