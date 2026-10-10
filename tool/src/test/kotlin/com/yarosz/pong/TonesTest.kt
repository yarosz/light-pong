package com.yarosz.pong

import com.yarosz.pong.core.SoundEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TonesTest {

    @Test
    fun `a failed build releases the tracks already built and leaves none`() {
        val released = mutableListOf<SoundEvent>()
        val tracks = buildAllOrNone(SoundEvent.entries, { if (it == SoundEvent.POINT) error("no track") else it }, { released += it })
        assertTrue(tracks.isEmpty())
        assertEquals(listOf(SoundEvent.PADDLE, SoundEvent.WALL), released)
    }

    @Test
    fun `a full build keeps every track`() {
        val tracks = buildAllOrNone(SoundEvent.entries, { it.name }, { error("nothing to release") })
        assertEquals(SoundEvent.entries.associateWith { it.name }, tracks)
    }

    @Test
    fun `without audio the tones are silent and never throw`() {
        val tones = Tones()
        for (event in SoundEvent.entries) tones.play(event)
        tones.stop()
        tones.release()
    }
}
