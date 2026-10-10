package com.yarosz.pong.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ComputerTest {

    private val incoming = Ball(540.0, 100.0, Ball.SERVE_SPEED, 300.0)

    @Test
    fun `reaction delays are whole steps, rounded to nearest`() {
        assertEquals(listOf(60, 36, 19), Difficulty.entries.map { it.reactionSteps })
    }

    @Test
    fun `Easy tracks the ball's current y`() {
        val computer = Computer(Difficulty.EASY, Random(1))
        assertEquals(incoming.cy, computer.intercept(incoming))
    }

    @Test
    fun `Normal and Hard predict the intercept with wall bounces`() {
        for (difficulty in listOf(Difficulty.NORMAL, Difficulty.HARD)) {
            val computer = Computer(difficulty, Random(1))
            val expected = interceptY(incoming, Paddle.face(Side.COMPUTER) - Ball.HALF)
            assertEquals(expected, computer.intercept(incoming))
            assertNotEquals(incoming.cy, expected)
        }
    }

    @Test
    fun `the computer waits out its reaction delay, then moves at its max speed`() {
        for (difficulty in Difficulty.entries) {
            val computer = Computer(difficulty, Random(7))
            val tops = (1..difficulty.reactionSteps + 3).map {
                computer.step(incoming)
                computer.top
            }
            val waiting = tops.take(difficulty.reactionSteps)
            assertEquals(List(waiting.size) { Paddle.CENTRE_TOP }, waiting, "$difficulty waits at centre")
            val moving = tops.drop(difficulty.reactionSteps - 1).zipWithNext { a, b -> b - a }
            moving.forEach { assertEquals(-difficulty.maxSpeed / STEPS_PER_SECOND, it, 1e-9, "$difficulty speed") }
        }
    }

    @Test
    fun `away from the ball the computer drifts to centre at half its max speed`() {
        val computer = Computer(Difficulty.HARD, Random(3))
        repeat(400) { computer.step(incoming) }
        val start = computer.top
        computer.step(incoming.copy(vx = -Ball.SERVE_SPEED))
        assertEquals(Difficulty.HARD.maxSpeed / 2 / STEPS_PER_SECOND, computer.top - start, 1e-9)
        repeat(2_000) { computer.step(null) }
        assertEquals(Paddle.CENTRE_TOP, computer.top)
    }

    @Test
    fun `each approach re-rolls the aim`() {
        val computer = Computer(Difficulty.EASY, Random(11))
        val settled = (1..2).map {
            repeat(2_000) { computer.step(incoming.copy(cy = 600.0)) }
            computer.top.also { computer.step(null) }
        }
        assertNotEquals(settled[0], settled[1])
    }
}
