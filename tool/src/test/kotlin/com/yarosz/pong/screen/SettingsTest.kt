package com.yarosz.pong.screen

import com.yarosz.pong.core.Difficulty
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SettingsTest {

    private val dir: File = Files.createTempDirectory("pong-settings").toFile().apply { deleteOnExit() }

    @Test
    fun `a missing file loads as Normal with sound on`() {
        assertEquals(Settings(Difficulty.NORMAL, soundOn = true), SettingsFile(dir).load())
    }

    @Test
    fun `every settings value round trips through the file`() {
        val file = SettingsFile(dir)
        for (difficulty in Difficulty.entries) for (soundOn in listOf(true, false)) {
            val settings = Settings(difficulty, soundOn)
            file.save(settings)
            assertEquals(settings, SettingsFile(dir).load())
        }
        assertFalse(File(dir, "settings.txt.tmp").exists())
    }

    @Test
    fun `a corrupt file loads as the defaults and is replaced by the next save`() {
        val main = File(dir, "settings.txt")
        for (text in listOf("", "garbage", "difficulty=extreme\nsound=on", "difficulty=hard\nsound=maybe", "difficulty=hard")) {
            main.writeText(text)
            assertEquals(Settings(), SettingsFile(dir).load(), text)
        }
        SettingsFile(dir).save(Settings(Difficulty.EASY, soundOn = false))
        assertEquals(Settings(Difficulty.EASY, soundOn = false), SettingsFile(dir).load())
    }

    @Test
    fun `decode accepts exactly what encode writes`() {
        assertEquals(Settings(Difficulty.HARD, false), Settings.decode(Settings(Difficulty.HARD, false).encode()))
        assertNull(Settings.decode("difficulty=HARD\nsound=on"))
    }
}
