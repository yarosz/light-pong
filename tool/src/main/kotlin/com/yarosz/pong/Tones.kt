package com.yarosz.pong

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.yarosz.pong.core.SoundEvent
import com.yarosz.pong.screen.SAMPLE_RATE
import com.yarosz.pong.screen.samples
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * The three tones, each in its own track (DESIGN.md item 18). A new tone cuts off the playing one.
 *
 * Tones never block a frame (decision log D32): every track call, from building to releasing, runs on one
 * worker thread, because an AudioTrack call can wait on audioserver for 100 ms. [play] and [stop] only
 * enqueue, and only the newest request runs: one superseded before the worker reaches it is dropped.
 * [release] runs last and ends the worker; calls after it are ignored.
 *
 * Audio never stops the game (D28): if any track fails to build, the Tool runs silent, and a failed play
 * or stop is logged and skipped.
 */
class Tones internal constructor(buildTrack: (SoundEvent) -> ToneTrack) {

    constructor() : this({ AudioToneTrack(it.samples()) })

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "PongTones").apply { isDaemon = true } }
    private val latestRequest = AtomicLong()

    @Volatile
    private var released = false

    private var tracks: Map<SoundEvent, ToneTrack> = emptyMap()
    private var playing: ToneTrack? = null

    init {
        worker.execute { tracks = buildAllOrNone(SoundEvent.entries, buildTrack) { runCatching { it.release() } } }
    }

    fun play(event: SoundEvent) = latestOnly {
        stopPlaying()
        val track = tracks[event] ?: return@latestOnly
        runCatching { if (track.restart()) playing = track }.onFailure { Log.w(PongViewModel.TAG, "tone not played", it) }
    }

    fun stop() = latestOnly { stopPlaying() }

    fun release() {
        if (released) return
        released = true
        latestRequest.incrementAndGet()
        worker.execute {
            playing = null
            tracks.values.forEach { runCatching { it.release() } }
            tracks = emptyMap()
        }
        worker.shutdown()
    }

    private fun latestOnly(request: () -> Unit) {
        if (released) return
        val id = latestRequest.incrementAndGet()
        worker.execute { if (latestRequest.get() == id) request() }
    }

    private fun stopPlaying() {
        val track = playing ?: return
        playing = null
        runCatching { track.pause() }.onFailure { Log.w(PongViewModel.TAG, "tone not stopped", it) }
    }
}

/** One tone's track, called on the [Tones] worker only. */
internal interface ToneTrack {
    /** Plays the tone from its start; false if it could not be rewound. */
    fun restart(): Boolean

    fun pause()

    fun release()
}

/**
 * The one place that picks the tracks' performance mode (decision log D32). Static tracks of 16 to 257 ms
 * need no fast mixer, and D28's LOW_LATENCY is not kept.
 */
private const val PERFORMANCE_MODE = AudioTrack.PERFORMANCE_MODE_NONE

private class AudioToneTrack(samples: ShortArray) : ToneTrack {
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setPerformanceMode(PERFORMANCE_MODE)
        .setBufferSizeInBytes(samples.size * 2)
        .build()

    init {
        try {
            val written = track.write(samples, 0, samples.size)
            check(written == samples.size) { "wrote $written of ${samples.size} samples" }
            check(track.state == AudioTrack.STATE_INITIALIZED) { "track state ${track.state}" }
        } catch (e: RuntimeException) {
            track.release()
            throw e
        }
    }

    override fun restart(): Boolean {
        track.pause()
        if (track.setPlaybackHeadPosition(0) != AudioTrack.SUCCESS) return false
        track.play()
        return true
    }

    override fun pause() = track.pause()

    override fun release() = track.release()
}

/**
 * Builds one value per key, or none: if a build throws, the values already built are released and the
 * result is empty.
 */
internal fun <K, V> buildAllOrNone(keys: List<K>, build: (K) -> V, release: (V) -> Unit): Map<K, V> {
    val built = LinkedHashMap<K, V>()
    return try {
        for (key in keys) built[key] = build(key)
        built
    } catch (e: RuntimeException) {
        Log.w(PongViewModel.TAG, "audio unavailable, playing silent", e)
        built.values.forEach(release)
        emptyMap()
    }
}
