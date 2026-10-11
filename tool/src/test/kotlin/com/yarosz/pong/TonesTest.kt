package com.yarosz.pong

import com.yarosz.pong.core.SoundEvent
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @Test
    fun `play returns at once while the track blocks, and the tone still plays on the worker`() {
        val calls = Calls()
        val tones = Tones { event -> FakeTrack(event, calls, restartMillis = 200) }
        val started = System.nanoTime()
        tones.play(SoundEvent.PADDLE)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMillis < 10, "play blocked the caller for $elapsedMillis ms")
        tones.release()
        calls.awaitReleased()
        assertEquals("restart PADDLE", calls.log.first { it.startsWith("restart") })
        assertTrue(calls.threads.none { it == Thread.currentThread().name }, "a track was touched on the caller's thread")
    }

    @Test
    fun `building the tracks never blocks the caller`() {
        val calls = Calls()
        val started = System.nanoTime()
        val tones = Tones { event -> Thread.sleep(100).let { FakeTrack(event, calls) } }
        tones.play(SoundEvent.WALL)
        tones.stop()
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMillis < 50, "construction or play blocked the caller for $elapsedMillis ms")
        tones.release()
        calls.awaitReleased()
    }

    @Test
    fun `a play superseded before it runs is dropped`() {
        val calls = Calls()
        val gate = CountDownLatch(1)
        val tones = Tones { event -> FakeTrack(event, calls, gate = gate.takeIf { event == SoundEvent.PADDLE }) }
        tones.play(SoundEvent.PADDLE)
        calls.awaitRestartEntered()
        tones.play(SoundEvent.WALL)
        tones.play(SoundEvent.POINT)
        gate.countDown()
        calls.awaitLogged("restart POINT")
        tones.release()
        calls.awaitReleased()
        assertEquals(listOf("restart PADDLE", "restart POINT"), calls.log.filter { it.startsWith("restart") })
    }

    @Test
    fun `a new tone cuts off the playing one`() {
        val calls = Calls()
        val tones = Tones { event -> FakeTrack(event, calls) }
        tones.play(SoundEvent.PADDLE)
        calls.awaitLogged("restart PADDLE")
        tones.play(SoundEvent.WALL)
        calls.awaitLogged("restart WALL")
        tones.release()
        calls.awaitReleased()
        val log = calls.log.filterNot { it.startsWith("release") }
        assertEquals(listOf("restart PADDLE", "pause PADDLE", "restart WALL"), log)
    }

    @Test
    fun `a stop after a play is kept in order and the play before it is dropped`() {
        val calls = Calls()
        val gate = CountDownLatch(1)
        val tones = Tones { event -> FakeTrack(event, calls, gate = gate.takeIf { event == SoundEvent.PADDLE }) }
        tones.play(SoundEvent.PADDLE)
        calls.awaitRestartEntered()
        tones.play(SoundEvent.WALL)
        tones.stop()
        gate.countDown()
        calls.awaitLogged("pause PADDLE")
        tones.release()
        calls.awaitReleased()
        val log = calls.log.filterNot { it.startsWith("release") }
        assertEquals(listOf("restart PADDLE", "pause PADDLE"), log)
    }

    @Test
    fun `release after play is safe, releases every track last, and later calls are ignored`() {
        val calls = Calls()
        val tones = Tones { event -> FakeTrack(event, calls) }
        tones.play(SoundEvent.POINT)
        tones.release()
        tones.play(SoundEvent.PADDLE)
        tones.stop()
        tones.release()
        calls.awaitReleased()
        Thread.sleep(50)
        val log = calls.log.toList()
        assertEquals(SoundEvent.entries.map { "release $it" }, log.takeLast(SoundEvent.entries.size))
        assertTrue("restart PADDLE" !in log, log.toString())
        assertEquals(SoundEvent.entries.size, log.count { it.startsWith("release") })
    }

    @Test
    fun `a track that throws never reaches the caller`() {
        val calls = Calls()
        val tones = Tones { event -> FakeTrack(event, calls, fail = true) }
        tones.play(SoundEvent.PADDLE)
        tones.stop()
        tones.play(SoundEvent.WALL)
        tones.release()
        calls.awaitReleased()
    }

    private class Calls {
        val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val threads: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val released = CountDownLatch(SoundEvent.entries.size)
        private val restartEntered = CountDownLatch(1)

        fun record(entry: String) {
            threads += Thread.currentThread().name
            log += entry
            if (entry.startsWith("release")) released.countDown()
        }

        fun enteredRestart() = restartEntered.countDown()

        fun awaitReleased() = assertTrue(released.await(5, TimeUnit.SECONDS), "tracks not released: $log")

        fun awaitRestartEntered() = assertTrue(restartEntered.await(5, TimeUnit.SECONDS), "no restart began")

        fun awaitLogged(entry: String) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (entry !in log) {
                assertTrue(System.nanoTime() < deadline, "no '$entry' in $log")
                Thread.sleep(1)
            }
        }
    }

    private class FakeTrack(
        private val event: SoundEvent,
        private val calls: Calls,
        private val restartMillis: Long = 0,
        private val gate: CountDownLatch? = null,
        private val fail: Boolean = false,
    ) : ToneTrack {
        override fun restart(): Boolean {
            calls.enteredRestart()
            gate?.await(5, TimeUnit.SECONDS)
            if (restartMillis > 0) Thread.sleep(restartMillis)
            calls.record("restart $event")
            if (fail) error("restart failed")
            return true
        }

        override fun pause() {
            calls.record("pause $event")
            if (fail) error("pause failed")
        }

        override fun release() {
            calls.record("release $event")
            if (fail) error("release failed")
        }
    }
}
