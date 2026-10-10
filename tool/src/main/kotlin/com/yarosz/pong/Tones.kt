package com.yarosz.pong

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.yarosz.pong.core.SoundEvent
import com.yarosz.pong.screen.SAMPLE_RATE
import com.yarosz.pong.screen.samples

/**
 * The three tones, each in its own static AudioTrack (DESIGN.md item 18). A new tone cuts off the playing
 * one. Audio never stops the game: if any track fails to build, the Tool runs silent, and a failed play
 * or stop is logged and skipped.
 */
class Tones {
    private val tracks = buildAllOrNone(SoundEvent.entries, { track(it.samples()) }, { runCatching { it.release() } })
    private var playing: AudioTrack? = null

    fun play(event: SoundEvent) {
        stop()
        val track = tracks[event] ?: return
        runCatching {
            track.pause()
            if (track.setPlaybackHeadPosition(0) == AudioTrack.SUCCESS) {
                track.play()
                playing = track
            }
        }.onFailure { Log.w(PongViewModel.TAG, "tone not played", it) }
    }

    fun stop() {
        val track = playing ?: return
        playing = null
        runCatching { track.pause() }.onFailure { Log.w(PongViewModel.TAG, "tone not stopped", it) }
    }

    fun release() {
        playing = null
        tracks.values.forEach { runCatching { it.release() } }
    }

    private fun track(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
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
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        try {
            val written = track.write(samples, 0, samples.size)
            check(written == samples.size) { "wrote $written of ${samples.size} samples" }
            check(track.state == AudioTrack.STATE_INITIALIZED) { "track state ${track.state}" }
        } catch (e: RuntimeException) {
            track.release()
            throw e
        }
        return track
    }
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
