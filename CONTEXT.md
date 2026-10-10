# Pong

The language of the Pong tool: a Light Phone III Tool for playing Pong against the computer with the
scroll wheel, built for any LP3 owner who wants a short, quiet game.

## Platform

**Tool**:
An installable capability on LightOS, built with Light's SDK.
_Avoid_: app

**Detent**:
One click of the LP3's scroll wheel as it turns: one 317 or 318 key-down, whatever its repeat count.
In a Match it moves the Paddle target 40 px; in a menu it moves the selection one row.
_Avoid_: tick, notch, scroll step

**Title**:
The screen a Tool launch opens on: the Difficulty rows and the sound row. A Match starts from it and
"Menu" returns to it.
_Avoid_: home, start screen, main menu

## Play

**Match**:
One game against the Computer at one Difficulty, first to 11 Points with no win by two. The player
serves first in every Match.
_Avoid_: game, round, set

**Seed**:
The number a Match's randomness comes from. The serve stream and the Computer's stream are both
derived from it, so a Seed and the same inputs replay a Match exactly. Each Match takes a fresh Seed
from the clock.
_Avoid_: random state

**Step**:
One fixed 1/240 s tick of the simulation. Every timing in a Match is a whole number of Steps, and
nothing happens between them.
_Avoid_: tick, frame (a frame is one redraw; it runs zero or more Steps)

**Side**:
The player or the Computer: who owns a Paddle, a Goal line and a score.
_Avoid_: team

**Player**:
The person turning the wheel. The player's Paddle is on the left, beside the wheel.
_Avoid_: user, human, you

**Point**:
One unit of score, won when the Ball crosses the other Side's Goal line.
_Avoid_: goal (a goal line is where a Point is won), score (the score is the count of Points)

**Phase**:
Where a Match is between its first serve and Match over: the player's serve, the Computer's serve, a
Rally, a Point pause or a Hold. Paused is not a Phase; it freezes the Phase it interrupted.
_Avoid_: state, mode

**Serve**:
Launching the Ball from the field's centre toward the Receiver at 10° to 30° from horizontal. The
player serves with a click; the Computer serves 0.8 s after its serve begins.
_Avoid_: kickoff, launch

**Server**:
The Side that serves the next Point. The player first, then alternating every Point.
_Avoid_: serving side

**Receiver**:
The Side the served Ball travels toward: the Side that is not the Server.
_Avoid_: returner

**Rally**:
The Ball in play, from a Serve until it crosses a Goal line.
_Avoid_: volley, exchange

**Point pause**:
The 0.6 s after a Point: the Ball is hidden, the player's Paddle still moves, the Computer drifts to
centre and clicks are ignored. The next Serve, or Match over, follows.
_Avoid_: goal pause, cooldown

**Hold**:
The 0.6 s after resuming a paused Rally: the Ball is frozen and visible while both Paddles move. A
click during the Hold pauses again.
_Avoid_: resume delay, countdown

**Paused**:
A Match stopped mid-play behind the pause panel, with simulation time frozen. Entered by a click, by
leaving the screen, or by 60 s with the player's serve untouched.
_Avoid_: suspended, frozen

**Match over**:
The end of a Match, 0.6 s after the winning Point: the frozen field without the Ball, and the panel
with "You win" or "Computer wins".
_Avoid_: game over, result screen

## Field

**Field**:
The playing area, 1080x1168 logical px with y downward, black with no HUD band. The renderer scales it
uniformly to the screen.
_Avoid_: board, court, table, arena

**Wall**:
The Field's top (y 0) or bottom (y 1168) edge. The Ball bounces off a Wall; Walls are not drawn.
_Avoid_: border, edge, side

**Goal line**:
The Field's left (x 0, the player's) or right (x 1080, the Computer's) edge. The Ball reaching one
ends the Rally with a Point to the other Side.
_Avoid_: goal, end line, baseline

**Ball**:
The 32x32 white square in play, drawn in a 40x40 black halo. Its speed is its horizontal speed, 585
to 1170 px/s.
_Avoid_: puck

**Paddle**:
A Side's 24x192 white bar, vertical, the player's on the left at x 48 and the Computer's on the right
at x 1008. Its position is its top y, in [0, 976].
_Avoid_: bat, racket, stick

**Face**:
The long side of a Paddle that looks into the Field. A Ball striking it leaves at 50° times its offset
from the Paddle's centre.
_Avoid_: front

**End cap**:
A short end of a Paddle. A Ball striking it leaves at the full 50°, as if at the Paddle's end.
_Avoid_: corner, tip

**Paddle target**:
Where the player's Paddle is heading. Detents and drags move the target at once; the Paddle eases
toward it with a 30 ms time constant and snaps the last half pixel.
_Avoid_: goal position, desired position

## Computer

**Computer**:
The opponent on the right, moving its Paddle at no more than its Difficulty's max speed.
_Avoid_: AI, CPU, bot, opponent

**Difficulty**:
How well the Computer plays: Easy, Normal or Hard. It sets the Reaction delay, max speed, whether the
Computer predicts the Intercept, its Aim error and its Contact offset.
_Avoid_: level, skill, strength

**Approach**:
The Ball travelling toward the Computer, from the moment it turns toward it until it turns away or a
Point ends it. Each Approach re-rolls the Computer's Aim.
_Avoid_: attack, incoming

**Reaction delay**:
How long the Computer waits into an Approach before steering for the Ball: 250, 150 or 80 ms, run as
60, 36 or 19 whole Steps (Hard's is 79 ms).
_Avoid_: lag, latency

**Intercept**:
The y where the Ball's centre will reach the Computer's Face. Easy uses the Ball's current y; Normal
and Hard predict it, folding the path at the Walls.
_Avoid_: landing point, prediction

**Aim**:
Where on its Paddle the Computer means to meet the Ball this Approach: the Contact offset plus the
Aim error.
_Avoid_: target (the Paddle target is the player's)

**Contact offset**:
The part of the Aim the Computer chooses on purpose, uniform within ±k of half the Paddle, to send the
Ball back at an angle.
_Avoid_: spin, english

**Aim error**:
The part of the Aim the Computer gets wrong, uniform within ±e Paddle lengths.
_Avoid_: noise, mistake

## Sound

**Sound event**:
What the simulation reports for the Tool to play: a Paddle hit, a Wall bounce or a Point. The core
reports events; it never plays audio.
_Avoid_: sound effect, beep, tone (a tone is the audio played for an event)
