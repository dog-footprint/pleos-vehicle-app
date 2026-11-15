package com.dogfootprint.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.dogfootprint.app.R
import com.dogfootprint.app.data.CalmMode
import com.dogfootprint.app.pleos.GuidePriority
import com.dogfootprint.app.pleos.VoiceGuide
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "DogFootprint"

/**
 * "진정 모드". Two options with kennel-study evidence (docs/CARE_RULES.md §6):
 *  - MUSIC: calm classical at low volume — the Open Goldberg Variations (Kimiko Ishizaka, CC0: "free of
 *    copyright, all uses allowed"), five slow movements rotated so the same one is not repeated every trip
 *    (classical calms dogs but habituates within days of the same music; variety held the effect — Bowman 2015/2017;
 *    evidence and selection criteria in docs/CALMING_MUSIC.md).
 *  - STORY: a calm narration read by the vehicle's voice (audiobooks beat music and silence for resting
 *    and barking in Brayley & Montrose 2016; TTS is an approximation of a human reader).
 * Low-frequency "brown noise" was dropped: no canine evidence, and it sits mostly below dogs' ~67 Hz hearing floor.
 */
class CalmingPlayer(private val context: Context, private val voice: VoiceGuide) {

    // Slow movements only (Aria, Var. 13, 15, 21, 25); the fast variations would not fit the evidence (docs/CALMING_MUSIC.md).
    private val tracks = listOf(R.raw.calm_aria, R.raw.calm_var13, R.raw.calm_var15, R.raw.calm_var21, R.raw.calm_var25)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    @Volatile var volume: Float = 0.35f
        set(value) { field = value.coerceIn(0f, 1f); player?.setVolume(field, field) }

    private var player: MediaPlayer? = null
    private var storyJob: Job? = null
    private var focus: AudioFocusRequest? = null
    private var nextTrack = Random.nextInt(tracks.size)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun start(scope: CoroutineScope, mode: CalmMode, storyLines: List<String>) {
        if (_playing.value) return
        requestFocus()
        _playing.value = true
        when (mode) {
            CalmMode.MUSIC -> playNextTrack()
            CalmMode.STORY -> storyJob = scope.launch {
                var i = 0
                while (isActive && storyLines.isNotEmpty()) {
                    val line = storyLines[i % storyLines.size]
                    voice.say(line, GuidePriority.INFO)
                    delay(1_500L + line.length * 180L)   // no completion callback from the TTS: pace by length
                    i++
                }
            }
        }
    }

    fun stop() {
        storyJob?.cancel(); storyJob = null
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
        _playing.value = false
    }

    private fun playNextTrack() {
        val res = tracks[nextTrack % tracks.size]
        nextTrack++
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            context.resources.openRawResourceFd(res).use { afd -> mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
            mp.setVolume(volume, volume)
            mp.setOnCompletionListener { if (_playing.value) { it.release(); playNextTrack() } }
            mp.setOnErrorListener { _, what, extra -> Log.w(TAG, "calm track error $what/$extra"); stop(); true }
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            Log.w(TAG, "calm track failed", e)
            mp.release()
            stop()
        }
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stop()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(volume * 0.3f, volume * 0.3f)
                    AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(volume, volume)
                }
            }
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) Log.w(TAG, "audio focus denied")
        focus = request
    }
}
