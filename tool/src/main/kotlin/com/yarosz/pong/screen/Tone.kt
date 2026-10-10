package com.yarosz.pong.screen

import com.yarosz.pong.core.SoundEvent
import kotlin.math.min
import kotlin.math.roundToInt

const val SAMPLE_RATE = 44_100
private const val RAMP_SECONDS = 0.002
private const val AMPLITUDE = 0.25 * Short.MAX_VALUE

/** DESIGN.md item 18. */
fun SoundEvent.frequencyHz() = when (this) {
    SoundEvent.PADDLE -> 459.0
    SoundEvent.WALL -> 226.0
    SoundEvent.POINT -> 490.0
}

fun SoundEvent.durationSeconds() = when (this) {
    SoundEvent.PADDLE -> 0.096
    SoundEvent.WALL -> 0.016
    SoundEvent.POINT -> 0.257
}

fun SoundEvent.samples() = squareTone(frequencyHz(), durationSeconds())

/** A mono 16-bit square wave at [SAMPLE_RATE] with a linear attack and release of 2 ms each. */
fun squareTone(frequencyHz: Double, seconds: Double): ShortArray {
    val count = (seconds * SAMPLE_RATE).roundToInt()
    val ramp = (RAMP_SECONDS * SAMPLE_RATE).roundToInt()
    return ShortArray(count) { i ->
        val cycle = (i * frequencyHz / SAMPLE_RATE) % 1.0
        val level = if (cycle < 0.5) 1.0 else -1.0
        val envelope = min(1.0, min(i, count - 1 - i).toDouble() / ramp)
        (level * envelope * AMPLITUDE).roundToInt().toShort()
    }
}
