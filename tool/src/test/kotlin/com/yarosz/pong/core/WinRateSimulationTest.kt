package com.yarosz.pong.core

import org.junit.Assume.assumeTrue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A sanity check on the difficulty table, printed rather than asserted. The perfect player is limited
 * only by speed, 30 detents a second (66.7 px each by D31, 2,000 px/s). The human-like players also
 * react 0.2 s late and misjudge the intercept by up to a quarter of a paddle; the slow one turns 15
 * detents a second (46.7 px each, 700 px/s). It plays 1,800 Matches, so it runs only with -Dpong.winrate=true.
 */
class WinRateSimulationTest {

    private val matches = 200

    private fun winRates(name: String, newBot: (seed: Long) -> PlayerBot): String {
        val lines = Difficulty.entries.map { difficulty ->
            var wins = 0
            var unfinished = 0
            var pointsFor = 0
            var pointsAgainst = 0
            for (i in 0 until matches) {
                val seed = 10_000L * (difficulty.ordinal + 1) + i
                val match = Match(seed, difficulty)
                playOut(match, newBot(seed))
                when ((match.phase as? Phase.MatchOver)?.winner) {
                    Side.PLAYER -> wins++
                    Side.COMPUTER -> Unit
                    null -> unfinished++
                }
                pointsFor += match.playerScore
                pointsAgainst += match.computerScore
            }
            assertEquals(0, unfinished, "$difficulty matches that never finished")
            "  %-6s player wins %3d/%d (%5.1f%%), mean score %5.2f-%5.2f".format(
                difficulty, wins, matches, 100.0 * wins / matches,
                pointsFor.toDouble() / matches, pointsAgainst.toDouble() / matches,
            )
        }
        return lines.joinToString("\n", prefix = "$name:\n")
    }

    @Test
    fun `win rates against each difficulty`() {
        assumeTrue(System.getProperty("pong.winrate") == "true")
        val perfect = winRates("perfect, 30 detents/s") { PlayerBot(Random(it)) }
        val human = winRates("human-like, 30 detents/s, 0.2 s late, misjudges up to 48 px") {
            PlayerBot(Random(it), reactionSteps = 48, misjudgement = Paddle.LENGTH / 4)
        }
        val slow = winRates("human-like at 15 detents/s (700 px/s)") {
            PlayerBot(Random(it), detentsPerSecond = 15, reactionSteps = 48, misjudgement = Paddle.LENGTH / 4)
        }
        println("$perfect\n$human\n$slow")
    }
}
