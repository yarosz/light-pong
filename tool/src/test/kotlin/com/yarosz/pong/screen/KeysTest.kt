package com.yarosz.pong.screen

import com.yarosz.pong.core.Phase
import kotlin.test.Test
import kotlin.test.assertEquals

class KeysTest {

    private val wheel = listOf(KEY_WHEEL_CCW, KEY_WHEEL_CW, KEY_WHEEL_CLICK)
    private val others = listOf(24, 25, 80, 27, 0, 4, 3, 19, 20, 23, 66)
    private val repeats = listOf(0, 1, 7)

    @Test
    fun `wheel up is 317 and down is the other turn`() {
        assertEquals(317, WHEEL_UP)
        assertEquals(318, WHEEL_DOWN)
    }

    @Test
    fun `each hook reports its own action and repeat count`() {
        assertEquals(KeyPress(KEY_WHEEL_CLICK, KeyAction.DOWN, 1), KeyPress.down(KEY_WHEEL_CLICK, 1))
        assertEquals(KeyPress(KEY_WHEEL_CLICK, KeyAction.UP, 2), KeyPress.up(KEY_WHEEL_CLICK, 2))
        assertEquals(KeyPress(KEY_WHEEL_CW, KeyAction.MULTIPLE, 5), KeyPress.multiple(KEY_WHEEL_CW, 5))
    }

    @Test
    fun `all three hooks consume the wheel keys and leave 24 to the system`() {
        val hooks = listOf<(Int) -> KeyPress>({ KeyPress.down(it, 0) }, { KeyPress.up(it, 0) }, { KeyPress.multiple(it, 2) })
        for (hook in hooks) {
            for (key in wheel) assertEquals(true, decideKey(hook(key)).consumed, "$key")
            assertEquals(false, decideKey(hook(24)).consumed)
        }
    }

    @Test
    fun `the decision table over every wheel key, action and repeat count`() {
        for (key in wheel) for (action in KeyAction.entries) for (repeat in repeats) {
            val expected = when {
                action != KeyAction.DOWN -> null
                key == WHEEL_UP -> Command.UP
                key == WHEEL_DOWN -> Command.DOWN
                repeat == 0 -> Command.CLICK
                else -> null
            }
            assertEquals(KeyDecision(consumed = true, command = expected), decideKey(KeyPress(key, action, repeat)), "$key $action $repeat")
        }
    }

    @Test
    fun `every other key is left to the system and does nothing`() {
        for (key in others) for (action in KeyAction.entries) for (repeat in repeats) {
            assertEquals(KeyDecision(consumed = false, command = null), decideKey(KeyPress(key, action, repeat)), "$key $action $repeat")
        }
    }

    @Test
    fun `consumption is the same in every mode`() {
        for (mode in Mode.entries) for (key in wheel + others) for (action in KeyAction.entries) for (repeat in repeats) {
            val state = stateIn(mode)
            assertEquals(key in wheel, state.key(KeyPress(key, action, repeat)), "$mode $key $action $repeat")
        }
    }

    @Test
    fun `only a first click key-down acts`() {
        for (action in KeyAction.entries) for (repeat in repeats) {
            val state = stateIn(Mode.PLAY)
            state.key(KeyPress(KEY_WHEEL_CLICK, action, repeat))
            val served = state.match?.phase !is Phase.PlayerServe
            assertEquals(action == KeyAction.DOWN && repeat == 0, served, "$action $repeat")
        }
    }

    @Test
    fun `every turn key-down moves the paddle target one detent, repeats included`() {
        val state = stateIn(Mode.PLAY)
        val start = state.match!!.playerTarget
        for (repeat in repeats) state.key(KeyPress(WHEEL_DOWN, KeyAction.DOWN, repeat))
        state.key(KeyPress(WHEEL_DOWN, KeyAction.UP, 0))
        state.key(KeyPress(WHEEL_DOWN, KeyAction.MULTIPLE, 3))
        state.advance(STEP_NANOS)
        assertEquals(start + 3 * 40.0, state.match!!.playerTarget)
    }
}
