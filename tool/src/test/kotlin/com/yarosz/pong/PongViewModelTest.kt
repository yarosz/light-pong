package com.yarosz.pong

import android.view.KeyEvent
import com.yarosz.pong.core.Phase
import com.yarosz.pong.screen.KEY_WHEEL_CLICK
import com.yarosz.pong.screen.MenuRow
import com.yarosz.pong.screen.Mode
import com.yarosz.pong.screen.SettingsFile
import com.yarosz.pong.screen.stateIn
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PongViewModelTest {

    private val event = KeyEvent(KeyEvent.ACTION_DOWN, 0)

    private fun viewModel(mode: Mode) =
        PongViewModel(stateIn(mode), SettingsFile(Files.createTempDirectory("pong-vm").toFile().apply { deleteOnExit() }))

    @Test
    fun `all three key hooks return true for the wheel and false for 24`() {
        for (mode in Mode.entries) {
            val vm = viewModel(mode)
            for (key in listOf(317, 318, 319)) {
                assertEquals(true, vm.onKeyDown(key, event), "$mode down $key")
                assertEquals(true, vm.onKeyUp(key, event), "$mode up $key")
                assertEquals(true, vm.onKeyMultiple(key, 3, event), "$mode multiple $key")
            }
            assertEquals(false, vm.onKeyDown(24, event))
            assertEquals(false, vm.onKeyUp(24, event))
            assertEquals(false, vm.onKeyMultiple(24, 3, event))
        }
    }

    @Test
    fun `a click acts on key-down, not key-up`() {
        val vm = viewModel(Mode.PLAY)
        vm.onKeyUp(KEY_WHEEL_CLICK, event)
        vm.onKeyMultiple(KEY_WHEEL_CLICK, 0, event)
        assertIs<Phase.PlayerServe>(vm.match?.phase)
        vm.onKeyDown(KEY_WHEEL_CLICK, event)
        assertIs<Phase.Rally>(vm.match?.phase)
    }

    @Test
    fun `hiding the screen while Paused selects Resume and refreshes the shell`() {
        val vm = viewModel(Mode.PAUSED)
        vm.onKeyDown(318, event)
        assertEquals(MenuRow.MENU, vm.shell.menuRow)
        vm.onAppPause()
        assertEquals(MenuRow.FIRST, vm.shell.menuRow)
    }
}
