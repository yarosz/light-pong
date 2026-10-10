package com.yarosz.pong.screen

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class PongOwnerTest {

    @BeforeTest
    @AfterTest
    fun reset() = PongOwner.resetForTest()

    @Test
    fun `re-attaching shows any in-match state as Paused`() {
        val first = PongOwner.attach { Settings() }
        first.tapTitle(TitleRow.NORMAL)
        first.command(Command.CLICK)
        assertEquals(Mode.PLAY, first.mode)
        val again = PongOwner.attach { error("settings are loaded once per process") }
        assertSame(first, again)
        assertEquals(Mode.PAUSED, again.mode)
        again.tapMenu(MenuRow.MENU)
        assertEquals(Mode.TITLE, PongOwner.attach { Settings() }.mode)
    }

    @Test
    fun `re-attaching while Paused with Menu selected selects Resume`() {
        val state = PongOwner.attach { Settings() }
        state.tapTitle(TitleRow.NORMAL)
        state.pause()
        state.command(Command.DOWN)
        assertEquals(MenuRow.MENU, state.menuRow)
        PongOwner.attach { Settings() }
        assertEquals(Mode.PAUSED, state.mode)
        assertEquals(MenuRow.FIRST, state.menuRow)
    }

    @Test
    fun `re-attaching keeps Match over's selection`() {
        val state = PongOwner.attach { Settings() }
        state.tapTitle(TitleRow.NORMAL)
        state.playUntil { mode == Mode.OVER }
        state.command(Command.DOWN)
        PongOwner.attach { Settings() }
        assertEquals(Mode.OVER, state.mode)
        assertEquals(MenuRow.MENU, state.menuRow)
    }

    @Test
    fun `a reset owner starts a new state`() {
        val first = PongOwner.attach { Settings() }
        PongOwner.resetForTest()
        assertNotSame(first, PongOwner.attach { Settings() })
    }
}
