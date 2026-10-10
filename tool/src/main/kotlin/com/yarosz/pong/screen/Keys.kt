package com.yarosz.pong.screen

const val KEY_WHEEL_CCW = 317
const val KEY_WHEEL_CW = 318
const val KEY_WHEEL_CLICK = 319

/** The wheel key that moves the Paddle and the selection up (DESIGN.md item 19). Flip it here if the LP3 hand test says so. */
const val WHEEL_UP = KEY_WHEEL_CCW
val WHEEL_DOWN = if (WHEEL_UP == KEY_WHEEL_CCW) KEY_WHEEL_CW else KEY_WHEEL_CCW

/** Which of LightActivity's three key hooks asked. */
enum class KeyAction { DOWN, UP, MULTIPLE }

enum class Command { UP, DOWN, CLICK }

/** [consumed] is what the hook returns; [command] is what, if anything, the key does. */
data class KeyDecision(val consumed: Boolean, val command: Command?)

/**
 * DESIGN.md items 19-22. The wheel's three keys are consumed on every hook in every state, so LightOS
 * never sees a turn (brightness) or a click (flashlight). Every other key, the volume keys and the
 * shutter among them, stays with the system. A turn acts on every key-down, repeats included; a click
 * only on its first key-down.
 */
fun decideKey(keyCode: Int, action: KeyAction, repeatCount: Int): KeyDecision {
    val command = when (keyCode) {
        WHEEL_UP -> Command.UP
        WHEEL_DOWN -> Command.DOWN
        KEY_WHEEL_CLICK -> Command.CLICK
        else -> return KeyDecision(consumed = false, command = null)
    }
    val acts = action == KeyAction.DOWN && (command != Command.CLICK || repeatCount == 0)
    return KeyDecision(consumed = true, command = command.takeIf { acts })
}
