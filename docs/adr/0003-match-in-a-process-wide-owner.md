---
status: accepted (2026-10-10: domain-expert rounds B and C)
---

# The Match lives in a process-wide owner

The Match in progress is held by a process-wide owner object that holds state only: the current
`Match`, the screen state around it (Title selection, Paused, Match over) and nothing that runs on its
own. The frame loop is `withFrameNanos` in composition, so the game advances only while the screen is
composed.

The owner exists because the view model does not outlive the screen. Back runs the SDK's
`LightActivity.goBack`, which calls `popped.destroy()` on the screen, and `LightScreen.destroy` clears
the screen's `viewModelStore`; Back on the only screen then finishes the activity. Reopening the Tool
in the same process builds a new screen and a new view model, which re-attaches to the owner:

- Title restores to Title with the same selection.
- Match over restores to Match over with the same selection.
- Any in-match Phase restores to Paused with Resume selected, also when it was already Paused
  (decision log D27).

Nothing is written to disk mid-match. Difficulty and mute persist in `filesDir` and are written when
they change. Back always closes the Tool (system behaviour). Leaving the screen, by `onAppPause` or
`onScreenHide`, pauses the Match and stops any playing tone, so a Match is never running unseen.

## Considered Options

- Saving the Match to disk so it survives process death: a Match lasts minutes, the Tool's state
  would grow a format to version, and a restored Match would have to resume mid-Rally after an
  arbitrary gap. Losing a Match to process death is acceptable.
- A coroutine game loop in the owner: it would keep running while the screen is gone and need its own
  lifecycle; tying the loop to composition makes "not composed" mean "not running".
- Intercepting Back to pause or confirm: Back closing the Tool is what every LP3 Tool does.

## Consequences

- The owner is plain state, so the core's tests cover what it holds; the Tool's tests cover only the
  re-attach mapping.
- A process restart starts at Title with the persisted Difficulty and mute.
- Pause on hide relies on the core's resume rules (a Rally resumes through the Hold), so returning to
  the Tool never drops the player into a moving Ball.
