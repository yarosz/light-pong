package com.yarosz.pong.screen

import com.yarosz.pong.core.Difficulty
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** The two choices that outlive a process (DESIGN.md items 17-18). */
data class Settings(val difficulty: Difficulty = Difficulty.DEFAULT, val soundOn: Boolean = true) {

    fun encode() = "difficulty=${difficulty.name.lowercase()}\nsound=${if (soundOn) "on" else "off"}\n"

    companion object {
        /**
         * Reads the `difficulty` and `sound` lines [encode] writes, in any order, ignoring blank and
         * unknown lines. Null when either is missing or holds a value [encode] never writes.
         */
        fun decode(text: String): Settings? {
            val fields = text.lines().filter { it.isNotBlank() }.associate { it.substringBefore('=') to it.substringAfter('=', "") }
            val difficulty = Difficulty.entries.firstOrNull { it.name.lowercase() == fields["difficulty"] } ?: return null
            val soundOn = when (fields["sound"]) {
                "on" -> true
                "off" -> false
                else -> return null
            }
            return Settings(difficulty, soundOn)
        }
    }
}

/**
 * `settings.txt` in the Tool's filesDir. A save writes a temp file, syncs it and renames it over the old
 * one, so a kill at any moment leaves the old file or the new one. A missing or unreadable file loads
 * as the defaults.
 */
class SettingsFile(dir: File) {
    private val main = File(dir, "settings.txt")
    private val temp = File(dir, "settings.txt.tmp")

    fun load(): Settings = runCatching { main.takeIf { it.exists() }?.readText(Charsets.UTF_8) }.getOrNull()?.let(Settings::decode) ?: Settings()

    fun save(settings: Settings) {
        FileOutputStream(temp).use { out ->
            out.write(settings.encode().toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (!temp.renameTo(main)) throw IOException("couldn't rename ${temp.name} to ${main.name}")
    }
}
