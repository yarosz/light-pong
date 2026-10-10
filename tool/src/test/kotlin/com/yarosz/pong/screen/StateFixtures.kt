package com.yarosz.pong.screen

import com.yarosz.pong.core.Phase
import java.util.concurrent.atomic.AtomicLong

const val STEP_NANOS = 4_166_667L
const val FRAME_NANOS = 16_666_667L

fun newState(settings: Settings = Settings(), seed: Long = 42) = PongState(settings) { seed }

private val wheelClock = AtomicLong()

/** Sends [command] at the next tick of a clock that runs a second per call, so every Detent reads as lone. */
fun PongState.wheel(command: Command) = command(command, wheelClock.addAndGet(1_000))

/** Serves whenever the player's serve is pending and lets the computer win every point it can. */
fun PongState.playUntil(limit: Int = 100_000, done: PongState.() -> Boolean) {
    var frames = 0
    while (!done()) {
        check(frames++ < limit) { "no progress: $shell" }
        if (match?.phase is Phase.PlayerServe) wheel(Command.CLICK) else advance(FRAME_NANOS)
    }
}

fun stateIn(mode: Mode): PongState {
    val state = newState()
    if (mode == Mode.TITLE) return state
    state.wheel(Command.CLICK)
    when (mode) {
        Mode.PAUSED -> state.pause()
        Mode.OVER -> state.playUntil { this.mode == Mode.OVER }
        else -> Unit
    }
    check(state.mode == mode)
    return state
}
