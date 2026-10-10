package com.yarosz.pong.screen

/**
 * The process's one [PongState] (ADR 0003). The view model does not survive leaving the Tool: Back runs
 * LightActivity.goBack, which destroys the screen and clears its ViewModelStore, and a relaunch in the
 * same process builds a new screen and a new view model. That new view model re-attaches here, so the
 * state outlives it; an in-match state comes back Paused.
 */
object PongOwner {
    private var state: PongState? = null

    fun attach(load: () -> Settings): PongState {
        val existing = state
        if (existing != null) return existing.also { it.pause() }
        return PongState(load()) { System.nanoTime() xor System.currentTimeMillis() }.also { state = it }
    }

    internal fun resetForTest() {
        state = null
    }
}
