package com.yarosz.pong.screen

/**
 * The process's one [PongState] (ADR 0003). LightOS relaunches the activity in the same process without
 * clearing the old view model, so a new view model re-attaches here; an in-match state comes back Paused.
 */
object PongOwner {
    private var state: PongState? = null

    fun attach(load: () -> Settings): PongState {
        val existing = state
        if (existing != null) return existing.also { it.pause() }
        return PongState(load()) { System.nanoTime() xor System.currentTimeMillis() }.also { state = it }
    }
}
