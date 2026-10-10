package com.yarosz.pong.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BurstLogTest {

    @Test
    fun `a burst ends after 150 ms with no Detent and logs its count, duration and peak rate`() {
        val burst = BurstLog()
        for ((at, rate) in listOf(1_000L to 0.0, 1_040L to 25.0, 1_080L to 25.0, 1_120L to 25.0)) assertNull(burst.detent(at, rate))
        assertNull(burst.quiet(1_269))
        assertEquals("spin detents=4 ms=120 peak_rate=25", burst.quiet(1_270))
        assertNull(burst.quiet(5_000))
    }

    @Test
    fun `a Detent after the quiet gap ends the last burst and starts the next`() {
        val burst = BurstLog()
        burst.detent(0, 0.0)
        burst.detent(100, 10.0)
        assertEquals("spin detents=2 ms=100 peak_rate=10", burst.detent(250, 0.0))
        assertEquals("spin detents=1 ms=0 peak_rate=0", burst.end())
        assertNull(burst.end())
    }

    @Test
    fun `the peak is the fastest rate the Match applied in the burst, rounded`() {
        val burst = BurstLog()
        for (rate in listOf(0.0, 11.1, 33.3, 33.4, 20.0)) burst.detent(0, rate)
        assertEquals("spin detents=5 ms=0 peak_rate=33", burst.end())
    }
}
