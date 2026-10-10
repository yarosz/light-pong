package com.yarosz.pong.screen

import com.yarosz.pong.core.Difficulty
import com.yarosz.pong.core.Sensitivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** The three choices that outlive a process (DESIGN.md items 17, 18 and 20). */
data class Settings(
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val soundOn: Boolean = true,
    val sensitivity: Sensitivity = Sensitivity.DEFAULT,
) {

    fun encode() = "difficulty=${difficulty.name.lowercase()}\nsound=${if (soundOn) "on" else "off"}\nsensitivity=${sensitivity.name.lowercase()}\n"

    companion object {
        /**
         * Reads the `difficulty`, `sound` and `sensitivity` lines [encode] writes, in any order, ignoring
         * blank and unknown lines. A file without `sensitivity` (0.1.0 wrote none) reads as Normal. Null
         * when `difficulty` or `sound` is missing, or any of the three holds a value [encode] never writes.
         */
        fun decode(text: String): Settings? {
            val fields = text.lines().filter { it.isNotBlank() }.associate { it.substringBefore('=') to it.substringAfter('=', "") }
            val difficulty = Difficulty.entries.firstOrNull { it.name.lowercase() == fields["difficulty"] } ?: return null
            val soundOn = when (fields["sound"]) {
                "on" -> true
                "off" -> false
                else -> return null
            }
            val sensitivity = fields["sensitivity"]?.let { value -> Sensitivity.entries.firstOrNull { it.name.lowercase() == value } ?: return null }
                ?: Sensitivity.DEFAULT
            return Settings(difficulty, soundOn, sensitivity)
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
