package com.dogfootprint.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "DogFootprint"
private const val SAMPLE_RATE = 44_100
private const val BREATH_HZ = 0.1
private const val BREATH_DEPTH = 0.2
private const val BROWN_SMOOTHING = 0.02
private const val BROWN_GAIN = 3.5

/**
 * Synthesized calming sound: brown noise with a slow "breathing" amplitude envelope. No audio
 * assets, no licensing. Pauses on audio-focus loss.
 */
class CalmingSoundPlayer(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    @Volatile var volume: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f) }

    private var track: AudioTrack? = null
    private var job: Job? = null
    private var focus: AudioFocusRequest? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun start(scope: CoroutineScope) {
        if (_playing.value) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stop()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> volume = volume * 0.3f
                }
            }
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Audio focus denied for calming sound")
        }
        focus = request

        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        _playing.value = true

        job = scope.launch(Dispatchers.Default) {
            val buf = ShortArray(minBuf)
            var brown = 0.0
            var n = 0L
            val rnd = Random(System.nanoTime())
            while (isActive) {
                for (i in buf.indices) {
                    val white = rnd.nextDouble(-1.0, 1.0)
                    brown += BROWN_SMOOTHING * (white - brown)
                    val breath = 1.0 - BREATH_DEPTH + BREATH_DEPTH * sin(2 * PI * BREATH_HZ * n / SAMPLE_RATE)
                    val s = (brown * BROWN_GAIN * breath * volume).coerceIn(-1.0, 1.0)
                    buf[i] = (s * Short.MAX_VALUE).toInt().toShort()
                    n++
                }
                val written = t.write(buf, 0, buf.size)
                if (written < 0) { Log.w(TAG, "AudioTrack write failed: $written"); break }
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        track?.let { runCatching { it.stop(); it.release() } }
        track = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
        _playing.value = false
    }
}
