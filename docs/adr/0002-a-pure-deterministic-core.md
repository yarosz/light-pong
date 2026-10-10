---
status: accepted (2026-10-10: domain-expert rounds A and C; S1 builds it)
---

# A pure, deterministic core

The whole game runs in `com.yarosz.pong.core`, plain Kotlin with no Android, AndroidX or Light SDK
import (a test enforces it). The core owns the Field geometry, the Ball, both Paddles, Serves, scoring,
Match over, the Computer, pause and resume, and the Sound events. The Tool's screen reads the core's
state each frame, maps keys and touches to its calls, and plays the tones it reports.

The simulation runs in fixed Steps of 1/240 s. `Match.advance` takes the frame's duration in
nanoseconds, clamps it to 50 ms, and runs the whole Steps that fit, carrying the remainder to the next
frame in exact integer arithmetic. Every timer (the Computer's serve, the Point pause, the Hold, the
idle auto-pause, the Reaction delay) counts whole Steps, so 0.8 s is exactly 192 Steps on every phone.

Randomness comes from the Match's Seed. Two streams are derived from it, one for Serves and one for
the Computer, so a change in how often the Computer rolls its Aim cannot shift the Serves. Wheel and
drag input is queued and applied, in order, at the start of the next Step, none dropped. The same Seed
with the same calls in the same order replays a Match bit for bit.

The Ball's collisions are swept: each Step finds the earliest contact with a Wall or a Paddle in the
time left and bounces there, so no speed can carry the Ball through a Paddle. The player's Paddle
eases toward the Paddle target inside the core, with a 30 ms time constant and a snap within 0.5 px,
and collisions use the eased position, which is also the drawn one.

## Considered Options

- Variable time steps driven by the frame clock: simpler, but no two runs agree, tests cannot pin
  timings, and a slow frame can tunnel the Ball.
- Easing in the renderer, with collisions against the Paddle target: what the player sees would not be
  what gets hit.
- Collision by overlap at the end of each Step: at 1170 px/s the Ball moves under 5 px a Step, so it
  would mostly work, but a Paddle eased 100 px in one Step could still skip over it.
- One random stream: any change in the Computer's draws would change every later Serve, so a tuning
  change would change replays in unrelated places.

## Consequences

- The core is tested on the JVM: property tests run hundreds of seeded Matches with random input and
  check the invariants after every Step.
- A frame clock that hiccups costs at most 12 Steps (50 ms) of simulated time, never a burst.
- A Paddle can move onto a Ball that has already passed its Face. The core treats that as an End cap
  hit, or, when a Wall leaves no room beside the cap, sets the Ball back in front of the Face.
- Each Match's Seed comes from the clock and is logged at debug level by the Tool; tests inject it.
