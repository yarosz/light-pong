# Settled decisions (running log; newest round last)

Each line gives a ruling's final outcome. A superseded ruling names what replaced it. DESIGN.md is the
resulting spec.

## Round A (rules, feel, computer, sound; 2026-10-10)

- A1 First to 11, no win by two. The player serves first in every Match.
- A2 The serve alternates every Point, player first. The Ball starts at the exact centre. The player's
  serve waits for a click with the Paddle movable; the Computer's launches 0.8 s after its serve state
  begins (1.4 s after the Point). Launch angle uniform in [10°, 30°] with a random sign, seeded.
- A3 Speed is vx. Serve vx 585 px/s, vy = vx·tan(angle). Each Paddle hit multiplies |vx| by 1.06, capped
  at 1170 px/s; reset every Point.
- A4 Outgoing angle = 50° × o, o = (ballCentreY − paddleCentreY) / (96 + 16) clamped to [−1, 1]; an End
  cap hit is ±1; vx flips away from the hitting Paddle every hit.
- A5 Paddle 192 px long and 24 px thick, Ball 32 px square, inset 48 px, no HUD band; the Field is the
  full 1080x1168 with the score drawn inside.
- A6 Computer: reacts after its Reaction delay once the Ball turns toward it, drifts to centre at half
  its max speed otherwise, velocity-capped; target = Intercept + Contact offset + Aim error, re-rolled
  per Approach, seeded. Table as DESIGN.md item 16.
- A7 Normal by default; the last choice persists in filesDir.
- A8 Square-wave tones (paddle 459 Hz 96 ms, wall 226 Hz 16 ms, point 490 Hz 257 ms), 2 ms attack and
  release, generated once at 44,100 Hz into static AudioTrack buffers; sound on by default, mute
  persists, a new tone cuts off the playing one.
- A9 Nothing but the per-hit factor speeds the Ball up.
- A10 The winning Point skips the serve; 0.6 s later play stops with the Ball hidden and paddles and
  final score shown. Keep-screen-on clears. The 0.6 s input lockout of B7 is met by C10.
- A11 Swept collisions, fixed 240 Hz Step, 50 ms frame clamp, the Ball leaves only across a Goal line,
  inputs queued to the next Step, pause when the Activity pauses, separate seeded streams. Kept; C13
  merges the input rules.
- A12 The Point goes to the Side whose goal was not crossed; on the crossing the Ball disappears, the
  score updates and the point tone plays; after 0.6 s the next serve or Match over. During the Point
  pause the Computer drifts to centre and the player's Paddle moves.
- AN1 Vertical Paddles, player left, 317 up and 318 down. Kept, with the direction in WHEEL_UP (B2).
- AN2 24 px per Detent with no smoothing. SUPERSEDED by C1 (40 px Paddle target, 30 ms easing).
- AN3 Large score digits inside the top of the Field, one per half; the Ball passes over them; dashed
  8 px centre line; white on black. Kept, with C5's layout and halo.
- AN4 Resume needs a click; the Ball holds 0.5 s. SUPERSEDED by C2 (0.6 s Hold).
- AN5 Each Match takes a fresh Seed from the clock, logged at debug; tests inject it.

## Round B (controls, screens, lifecycle, copy; 2026-10-10)

- B1 Vertical Paddles at the left and right edges; player left, beside the wheel.
- B2 CCW 317 up, CW 318 down, one constant WHEEL_UP = 317, hand-tested on the LP3 and flipped if wrong.
- B3 Detent = 1/24 of travel with 40 ms easing. SUPERSEDED by C1 (40 px, tau 30 ms); B's easing in the
  pure core, collision on the eased position and "none dropped" are kept.
- B4 No acceleration. Kept; if too slow, raise the step (C1 names 48 px), never a curve.
- B5 Click on key-down with repeatCount 0 only; the in-match rows of the click table. Kept, extended by
  C10 (Point pause ignores clicks) and C2 (a click in the Hold pauses).
- B6 Title layout, rows, marker, hints and no About screen. Kept; C4 sizes the marker (32 px square).
- B7 Match over panel; input ignored for 600 ms after the winning Point. The lockout is SUPERSEDED by
  C10: Match over appears 0.6 s after the Point and takes input at once.
- B8 Pause entry, panel, rows and hint; a 48 dp pause target; a 600 ms hold on resume. Target sized by
  C6 (144 px = 48 dp); the hold settled by C2 (0.6 s).
- B9 Match in a process-wide owner, nothing on disk mid-match, difficulty and mute persisted, frame loop
  in composition, one LightScreen. Kept (ADR 0003); C17 defines re-entry per state.
- B10 Touch drag relative 1:1 on the target, tap under slop, menus take taps, wheel alone suffices.
  Kept; C6 settles a tap in the pause target.
- B11 Segment digits, centre line under the pause control, serve prompt only during play, difficulty
  name on the pause and over panels only. Kept (C14 confirms).
- B12 Screen on during the Rally and any pending serve, off on Title, Paused and Match over; 60 s idle
  player serve auto-pauses. Kept via C8; DESIGN.md item 29 adds the Point pause and the Hold.
- B13 317/318/319 return true on down (repeats), up and multiple in every state; 24, 25, 80 and 27
  return false; one handler.
- B14 The copy strings. Kept as DESIGN.md's complete list.
- N1 Every 317/318 key-down moves one step whatever its repeatCount; key-ups consumed and ignored.
- N2 Computer speed in the player's units, easing in the core. Units SUPERSEDED by C7 (px/s of the
  logical Field are already shared); easing in the core kept.
- N3 During the Computer's serve the player moves and a click pauses; the Computer serves after about
  1 s. The delay is SUPERSEDED by C3 (0.8 s).
- N4 Menus: one row per Detent, clamped, no wrap, no sound.
- N5 No haptics.
- N6 No click lockout after a serve.

## Round C (contradictions; 2026-10-10)

- C1 Each Detent moves the Paddle target 40 px, clamped to [0, 976], extra Detents discarded; the
  Paddle eases at 240 Hz with tau 30 ms and snaps within 0.5 px; collision on the eased position.
- C2 One 0.6 s constant for the Point pause and the Hold; during the Hold the Ball is frozen and visible,
  both Paddles move, and a click pauses again.
- C3 The Computer serves 0.8 s after its serve state begins.
- C4 The selection marker is a 32x32 white square 24 px left of the label, centred on it.
- C5 Digits and Paddles do not overlap; draw order digits, centre line, pause bars, Paddles, Ball; the
  Ball has a 40x40 black halo.
- C6 Pause bars 16x64 with a 16 px gap at x 540, y 56-120; a 144x144 target at (540, 88); a touch that
  stays under slop there pauses, one that exceeds slop is a drag.
- C7 All speeds in px/s of the logical Field: Easy 322, Normal 468, Hard 614; drift at half.
- C8 60 s with the player's serve pending and no wheel or touch input pauses the Match.
- C9 The Ball is hidden in the Point pause, still at the centre while a serve is pending, frozen in
  place during the Hold.
- C10 Clicks in any Point pause are consumed and ignored; the wheel still moves the Paddle; Match over
  takes input as soon as it appears.
- C11 Resume: a Rally resumes into the Hold; the player's serve waits again with the idle timer
  restarted; the Computer's serve restarts its full 0.8 s; a Point pause goes straight to the next
  serve or Match over; no simulation time passes while Paused.
- C12 Walls undrawn at y 0 and 1168; both Paddles start centred (top 488) and are not reset per Point;
  the Computer drifts to centre during the Point pause and the player's serve; the serve alternates and
  launches toward the Receiver.
- C13 Every 317/318 key-down and every drag delta is queued and applied at the start of the next Step;
  replay is exact.
- C14 The difficulty name appears on the pause and Match over panels only.
- C15 Match over shows the frozen Field (Paddles, digits, centre line; no Ball, no pause bars) under the
  same panel as Paused.
- C16 Pause, background and Menu stop any playing tone; menus make no sound; mute is set only on Title.
- C17 Re-entry: Title to Title with the same selection, Match over to Match over, any in-match Phase to
  Paused.
- C18 Text sizes per DESIGN.md item 30; system font; digits from rect segments.

## Build decisions (S1, the pure core; 2026-10-10)

- D1 Timers count whole Steps, not seconds: 0.8 s is 192 Steps, 0.6 s is 144, 60 s is 14,400.
  `Match.advance` takes nanoseconds (what `withFrameNanos` gives) and keeps its remainder in exact
  integer units, so no rounding drift can add or lose a Step.
- D2 Paused is a flag on the Match, not a Phase, because resume depends on the Phase it froze (C11).
- D3 The click's in-match rows (DESIGN.md item 21) live in the core as `Match.click()`. The Paused and
  Match over rows activate menu rows and belong to the Tool's screen, which calls `click()` only in play.
- D4 While Paused or at Match over, `turn`, `drag`, `serve`, `click` and `pause` do nothing and queue
  nothing: the wheel drives the menus then.
- D5 `serve()` launches at once rather than at the next Step; the Ball first moves on the next Step. The
  core has no clock, so "timestamped inputs" means the same calls in the same order between the same
  `advance` durations; that replays exactly.
- D6 A Point is scored when the Ball's leading edge reaches a Goal line (its left edge at x ≤ 0, or its
  right edge at x ≥ 1080). The Ball in play therefore never leaves the Field, even in part.
- D7 A Paddle can move onto a Ball that has passed its Face. That counts as an End cap hit on the nearer
  cap (o = ±1, with the hit's speed-up and sound). If a Wall leaves no room beside that cap, the Ball is
  set back in front of the Face and bounces with its Face offset.
- D8 End cap hits speed the Ball up like any Paddle hit (A3 says every hit).
- D9 A Ball wedged between a Wall and an End cap can bounce many times in one Step. After 16 contacts it
  waits out the rest of that Step instead of being moved through either.
- D10 During the Hold the frozen Ball is not solid, so a Paddle may be drawn over it; the first Rally
  Step resolves the overlap by D7.
- D11 Pausing during the Hold and resuming restarts the full Hold.
- D12 During its Reaction delay the Computer keeps drifting to centre at half speed.
- D13 An Approach starts on the first Step the Ball, in a Rally or Hold, moves toward the Computer, and
  ends when it moves away or no Ball is in play. The Aim (Contact offset, then Aim error) is rolled once
  per Approach from the Computer's stream.
- D14 Reaction delays round up to whole Steps: Easy 60 (250 ms), Normal 36 (150 ms), Hard 20 (83 ms).
- D15 The Computer steers its Paddle's centre to Intercept + Aim, clamped to its travel. Normal and Hard
  predict the Intercept the same way, folding at the Walls; the table's "predicted intercept" for Hard
  is read as the same prediction as Normal's.
- D16 A launch (the Computer's serve, or the Hold ending into a Rally) happens on the Step its timer
  completes; the Ball moves from the next Step.
- D17 Any wheel or drag input applied during the player's serve restarts the idle timer, including
  Detents discarded at an end.
- D18 The serve and Computer streams are `Random(seed).nextLong()` drawn twice, serve first.
- D19 One `advance` returns the Sound events of all its Steps in order; the Tool plays the last one,
  since a new tone cuts off the playing one.
- D20 The end-of-Match Phase is named `MatchOver` to match the glossary term Match over.
- D21 `turn(n)` queues one move of n × 40 px, clamped once. The Tool sends one call per key-down, so a
  multi-Detent call appears only in tests.
- D22 Balance finding, not a change. Over 200 seeded Matches per Difficulty
  (`WinRateSimulationTest`), a bot playing perfectly at 30 Detents a second never loses a Point. One
  that reacts 0.2 s late and misjudges by up to 48 px still wins every Match (Hard scores 0.73 a
  Match). Slowed to 15 Detents a second, it beats Easy 200/200 and Normal 198/200, and Hard 2/200.
  The Computer's strength rests on the wheel rate a person sustains, which must be measured on the
  phone before the table is tuned.
