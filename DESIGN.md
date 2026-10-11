# Pong design

The reference spec for the Pong Tool, settled in three domain-expert rounds on 2026-10-10. Item
numbers are stable: code, tests and docs/design/decision-log.md cite them. Units are logical px of
the 1080x1168 Field (density 3, so 1 dp = 3 px), y downward, unless a line says dp or sp. Where
this spec is silent, the build decisions D1 onward in the decision log settle the gap. CONTEXT.md
defines the terms.

## Field and geometry

#### 1. Field

The field is the full app area, 1080x1168 logical px (y down), black, with no HUD band. The walls are y=0 and y=1168, undrawn. The goal lines are x=0 (player's) and x=1080 (computer's). The renderer scales uniformly to the Canvas size.

#### 2. Paddles

Both are vertical, 24 px thick and 192 px long, all in white.

- Player on the left: x 48-72.
- Computer on the right: x 1008-1032.
- Faces are 936 px apart.
- Top y ranges over [0, 976]. Both start centred (top y 488) at match start, and are not reset per point.

#### 3. Ball

32x32 white square, drawn inside a 40x40 black halo.

#### 4. Centre line

Dashed, 8 px wide, centred at x 540, from y 168 to 1168 (dashes 32 px, gaps 32 px).

#### 5. Score digits

Built from rect segments, not a font. Each digit is 72x120 px with a 16 px stroke and a 24 px gap between digits. Top at y 48. The player's score is centred at x 270, the computer's at x 810.

#### 6. Pause control

Two 16x64 bars with a 16 px gap at x 540, y 56-120. Touch target 144x144 centred at (540, 88). Semantics "Pause". Shown in in-match states only (not Paused, not Match over).

#### 7. Draw order

Digits, centre line, pause bars, paddles, ball.

## Rules and physics

#### 8. Match

First to 11, no win-by-two. The player serves first in every match, then the serve alternates every point.

#### 9. Serve

The ball starts at the exact field centre and launches toward the receiver. Launch angle magnitude is uniform in [10°, 30°] from horizontal, with a random sign, from the seeded serve RNG.

- Player serve: waits for a click (or a tap). The paddle is movable.
- Computer serve: launches 0.8 s after the serve state begins. During the wait the player can move and can pause.

#### 10. Speed

- vx at serve is 585 px/s, and vy = vx·tan(angle).
- Each paddle hit multiplies |vx| by 1.06, capped at 1170 px/s (2.0x).
- Speed resets every point.
- Nothing else speeds the ball up.

#### 11. Paddle bounce

Outgoing angle = 50° × o, where o = (ballCentreY − paddleCentreY)/(96 + 16), clamped to [−1, 1]. An end-cap hit counts as ±1. The incoming angle is ignored. vx always flips to point away from the hitting paddle.

#### 12. Wall bounce

vy flips at the walls. The ball can leave only across a goal line.

#### 13. Simulation

- Fixed 240 Hz step, frame time clamped to 50 ms. The carried remainder starts half a step from a step boundary, so vsync jitter cannot flip 60 Hz frames between 3 and 5 steps (D30). A pause keeps the remainder, so the paused picture does not move.
- The ball and both paddles are drawn blended between the last two steps by the remainder's fraction of a step, one step (4.2 ms) behind the clock. The blend never feeds back into the simulation (D30).
- Swept collisions, so the ball never tunnels through a paddle.
- Deterministic given the match seed plus timestamped inputs.
- Separate RNG streams for serves and for the computer, derived from the match seed.
- Each match gets a fresh seed from the clock, logged at debug level. Tests inject the seed.

#### 14. Scoring

The point goes to the side whose goal was not crossed. The point is scored when the ball's leading edge reaches the goal line (D6). On the crossing the ball disappears, the score updates and the point tone plays. A 0.6 s point pause follows, during which the player paddle moves, the computer drifts to centre, and clicks are consumed and ignored. Then the game enters the next serve state, or Match over at 11 (the winning point skips the serve).

## Computer

#### 15. Reaction and movement

Once the ball turns toward it, the computer reacts after its reaction delay. When the ball moves away (and during serve waits and point pauses) it drifts to centre at half its max speed. Movement is velocity-capped.

#### 16. Target

Target = intercept + intended contact offset (uniform ±k·96 px) + aim error (uniform ±e·192 px). Both are re-rolled per approach from the computer RNG.

| Level | Reaction | Max speed | Tracks | e | k |
|---|---|---|---|---|---|
| Easy | 250 ms | 322 px/s | current ball y | 0.35 | 0.3 |
| Normal | 150 ms | 468 px/s | predicted intercept, with wall bounces | 0.2 | 0.6 |
| Hard | 80 ms | 614 px/s | predicted intercept | 0.08 | 0.85 |

Reaction delays run in whole simulation steps, rounded to the nearest: 60, 36 and 19 steps, so Hard's 80 ms is 79 ms (D14).

#### 17. Default difficulty

Normal by default. The last choice persists in filesDir.

## Sound

#### 18. Tones

Square waves with a 2 ms linear attack and release, generated once at 44,100 Hz into static AudioTrack buffers (16-bit, USAGE_GAME).

| Event | Frequency | Duration |
|---|---|---|
| Paddle hit | 459 Hz | 96 ms |
| Wall bounce | 226 Hz | 16 ms |
| Point | 490 Hz | 257 ms |

A new tone cuts off the one playing. Tones never block a frame: every track call runs off the main thread, and a tone superseded before it starts is dropped (decision log D32). Sound is on by default and the mute choice persists. Pause, background and Menu stop any playing tone. Menus, selection moves and pausing make no sound. No haptics.

## Input

#### 19. Wheel direction

CCW 317 moves the paddle (or the selection) up, CW 318 moves it down. This lives in one constant, `WHEEL_UP = 317`, which the builder hand-tests on the LP3 and flips if wrong.

#### 20. Paddle by wheel

Every 317/318 key-down (any repeatCount) is one Detent, and it moves the target by step(rate) × the Sensitivity's factor, clamped to [0, 976] (D31). Detents past an end are consumed and discarded. Key-ups are consumed and ignored.

- Rate: the Detent rate in Detents a second, measured from the keys' event times (`KeyEvent.eventTime`, uptime ms) over the Detents of the last 100 ms, this one included: n Detents spanning t ms read (n − 1)/t. A lone Detent reads 0.
- Step: 40 px at a rate of 10 a second or less, rising linearly to 80 px at 40 a second or more, capped there. Never below 40 px at Normal. No exponential curve. 10 a second is the slowest rate the 100 ms window reads above 0, and 40 a second sits above a typical spin, so the ramp covers real spins. Both are to be confirmed from the `spin` lines (D31).
- Sensitivity: Normal multiplies the step by 1, Fast by 1.5 (60 to 120 px). Normal by default; the choice persists (item 25).

The paddle eases to the target in the core with tau 30 ms (`alpha = 1 − exp(−(1/240)/0.030)`), snapping within 0.5 px. Collision uses the eased position; the renderer draws it blended one Step behind the clock (item 13). Inputs are queued with their event times and applied at the start of the next physics step, and none are dropped. During play, each spin (Detents with no gap of 150 ms) logs one `spin` PongEvent line when it ends (AGENTS.md, "Measuring frames and spins").

#### 21. Click (319)

Acts on key-down with repeatCount == 0 only. Repeats and key-up are consumed and do nothing.

| State | Click does |
|---|---|
| Title | Activates the selected row |
| Player serve pending | Serves |
| Rally, resume hold, computer serve pending | Pauses |
| Point pause | Ignored |
| Paused | Activates the selected row |
| Match over | Activates the selected row |

There is no click lockout after a serve.

#### 22. Key consumption

317, 318 and 319 return true from onKeyDown (including repeats), onKeyUp and onKeyMultiple in every state, even when nothing moves. 24, 25, 80 and 27 return false everywhere. One handler serves all states.

#### 23. Menus

One row per detent, clamped, no wrap. Extra turns are consumed. No sound.

#### 24. Touch (optional fallback)

- During play, a drag anywhere moves the paddle target 1:1 with the drag's vertical delta, relative and clamped. Collision still uses the eased position.
- A touch under slop counts as a tap. A tap in the pause target pauses. A tap elsewhere serves when the player's serve is pending and otherwise does nothing.
- Menus take taps on rows.
- Everything works by wheel alone.

## Screens and lifecycle

#### 25. Title

No top bar or field.

- "Pong", then the rows Easy / Normal / Hard, a gap, "Sound on" or "Sound off", and "Wheel normal" or "Wheel fast".
- The persisted difficulty is preselected (Normal on first launch). The selected row shows the 32 px square marker 24 px left of its label.
- Click or tap on a difficulty row starts a match at that difficulty. Click or tap on the sound row toggles sound, and on the wheel row toggles the Sensitivity between Normal and Fast; both save immediately.
- Hint at the bottom: on a difficulty row "Turn the wheel to choose. Press it to start." On the sound row, "Press the wheel to turn sound off." or "Press the wheel to turn sound on.", whichever applies. On the wheel row, "Press the wheel to make it fast." or "Press the wheel to make it normal.", whichever applies.
- The rows centre in the space above the hint: 16 dp after "Pong" and 16 dp before the sound row.
- No About screen.

#### 26. Play

The field, as in items 1-7. The only text is "Press the wheel to serve", small at bottom centre, while the player's serve is pending.

#### 27. Paused

- Entered by click, onAppPause, onScreenHide, or 60 s with no input while the player's serve is pending.
- Shows the frozen field (ball visible if it was in play, no pause bars) behind a centred panel: black with a 4 px white border.
- Panel contents: "Paused", the difficulty name small, the rows "Resume" (preselected every time) and "Menu", and the hint "Turn the wheel to choose. Press it to select."
- Simulation time stops while Paused.
- Resume returns to:
  - a rally: the 0.6 s hold, with the ball frozen and visible, the player paddle movable and the computer active. A click during the hold pauses again.
  - player serve pending: waiting again, and the 60 s timer restarts.
  - computer serve pending: the full 0.8 s delay restarts.
  - a point pause: straight to the next serve state, or to Match over.
- "Menu" ends the match without confirmation and goes to Title with the match's difficulty preselected.

#### 28. Match over

Appears 0.6 s after the winning point.

- The frozen field shows the paddles, final digits and centre line, with no ball and no pause bars. The same panel as Paused sits over it.
- Panel contents: "You win" or "Computer wins", the difficulty name small, the rows "Play again" (preselected) and "Menu", and the same hint as Paused.
- Input is live as soon as it appears.
- "Play again" starts a new match at the same difficulty with a fresh seed; the player serves.

#### 29. Keep-screen-on

On during the rally, both serve-pending states, the point pause and the resume hold. Off on Title, Paused and Match over. Implemented as a View with keepScreenOn inside AndroidView.

#### 30. Text sizes

"Pong" 48 sp, rows 28 sp, the result line ("You win") and "Paused" 32 sp, hints, the serve prompt and the difficulty name 16 sp. Sentence case, no exclamation marks, system font. Row pitch 48 dp, on Title and the panels, so Title's six rows fit the app area (D31). Keep everything within a 16 dp side gutter.

#### 31. State and persistence

- The match lives in a process-wide owner object that holds state only.
- The frame loop is `withFrameNanos` in composition.
- On re-entry the view model re-attaches to the owner: Title restores to Title with the same selection, Match over to Match over, any in-match state to Paused.
- Nothing is written to disk mid-match. Difficulty, mute and the Sensitivity persist in filesDir and are written on change.
- One LightScreen with internal states: title, play (player serve, computer serve, rally, point pause, hold), paused, over.

#### 32. System keys

Back always closes the Tool (system behaviour); HOME never reaches it. Volume keys stay with the system.

## Copy strings (complete)

- "Pong"
- "Easy", "Normal", "Hard"
- "Sound on", "Sound off"
- "Wheel normal", "Wheel fast"
- "Turn the wheel to choose. Press it to start."
- "Press the wheel to turn sound off.", "Press the wheel to turn sound on."
- "Press the wheel to make it fast.", "Press the wheel to make it normal."
- "Press the wheel to serve"
- "Paused", "Resume", "Menu"
- "Turn the wheel to choose. Press it to select."
- "You win", "Computer wins", "Play again"
- Content description "Pause" for the pause control.
