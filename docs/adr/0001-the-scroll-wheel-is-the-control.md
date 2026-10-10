---
status: accepted (2026-10-10: domain-expert rounds A, B and C)
---

# The scroll wheel is the control

Pong is played with the LP3's scroll wheel. The Paddles are vertical so that turning the wheel moves
the player's Paddle along the same axis the wheel rolls, and the player's Paddle is on the left, beside
the wheel. Each Detent (one 317 or 318 key-down, whatever its repeat count) moves the Paddle target
40 px, clamped to the Paddle's travel; Detents past an end are consumed and discarded. There is no
acceleration. The click (319) serves, pauses or activates a menu row.

Which way is up lives in one constant, `WHEEL_UP = 317` (counter-clockwise moves up). The builder
hand-tests it on the phone and flips the constant if it is wrong; no other code knows the direction.

Pong consumes every wheel event in every state: 317, 318 and 319 return true from `onKeyDown`
(repeats included), `onKeyUp` and `onKeyMultiple`, even when nothing moves, so the system never acts
on a stray turn. Volume, Back and the other system keys stay with the system. Touch is an optional
fallback: a drag moves the Paddle target 1:1, a tap serves or presses the pause control, and menus take
taps on rows. Everything works by wheel alone.

## Considered Options

- Horizontal Paddles at the top and bottom, the classic arcade layout turned on its side: the wheel's
  roll would then move the Paddle across the axis it turns, which reads backwards in the hand.
- 24 px per Detent with no easing: a full sweep would take about 41 Detents, more than the 20-40 events
  of a short spin. 40 px makes it 24.4.
- Wheel acceleration: a curve makes the Paddle's position depend on spin speed, which is hard to learn
  and hard to replay. If 40 px feels slow on the phone, the step grows (for example to 48 px); it does
  not become a curve.
- Touch as the primary control: the wheel is the LP3's distinctive input and works without covering
  the screen.

## Consequences

- The game's balance depends on how fast a person can turn the wheel. The simulation in
  `WinRateSimulationTest` shows the Computer's strength swings from never scoring to winning almost
  every Match between 30 and 15 Detents a second, so the wheel rate must be measured on the phone.
- One input handler serves every screen, since key consumption is the same everywhere.
- The direction constant is a deliberate single point of change for the hand test.
