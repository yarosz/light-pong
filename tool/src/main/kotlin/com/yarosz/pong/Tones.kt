package com.yarosz.pong

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.yarosz.pong.core.SoundEvent
import com.yarosz.pong.screen.SAMPLE_RATE
import com.yarosz.pong.screen.samples

/** The three tones, each in its own static AudioTrack (DESIGN.md item 18). A new tone cuts off the playing one. */
class Tones {
    private val tracks = SoundEvent.entries.associateWith { track(it.samples()) }
    private var playing: AudioTrack? = null

    fun play(event: SoundEvent) {
        stop()
        val track = tracks.getValue(event)
        track.pause()
        track.setPlaybackHeadPosition(0)
        track.play()
        playing = track
    }

    fun stop() {
        playing?.pause()
        playing = null
    }

    fun release() {
        playing = null
        tracks.values.forEach { it.release() }
    }

    private fun track(samples: ShortArray): AudioTrack =
        AudioTrack.Builder()
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
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .also { it.write(samples, 0, samples.size) }
}
