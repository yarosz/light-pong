package com.yarosz.pong.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The core runs on the JVM alone: no Android, AndroidX or Light SDK import may reach it. */
class PureCoreTest {

    private val core = File("src/main/kotlin/com/yarosz/pong/core")

    @Test
    fun `no file under core imports android, androidx or the Light SDK`() {
        val files = core.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "no sources under $core")
        val platform = Regex("""^\s*import\s+(android|androidx|com\.thelightphone)\.""")
        val imports = files.flatMap { file ->
            file.readLines().filter { platform.containsMatchIn(it) }.map { "${file.relativeTo(core)}: ${it.trim()}" }
        }
        assertEquals(emptyList(), imports)
    }
}
