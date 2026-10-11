# Ledger

STATUS: 0.1.2 in progress: the tones moved off the main thread, so a paddle hit with sound on no
longer freezes the picture (S6, D32); it awaits review and the LP3 A/B (N12). 0.1.1 is on `main`:
the frame judder fix (D30), wheel acceleration and the Wheel row (D31), and their review fixes
(S5b). 0.1.0, the first release, is on `main`: the design (rounds A, B and
C), the pure core (S1), the Tool's screens (S2), both review rounds (S1b, S2b) and the docs (S3),
with a release build played on a Light Phone III (L1). Not yet in Light's Tool Library. `scripts/domain-drift.sh`
reports 0 unresolved.

## Handoff

Start at N2. The open items are hand tests on the phone (N2 to N4) and the `signoff/lp3` round trip
(N6). D22's tuning (N3) did not block 0.1.0; the release notes list it as a known limit. Read `AGENTS.md` first; its rules for the wheel keys and
the pure core are the ones reviews keep checking.

## Done

| # | What | Evidence |
|---|---|---|
| S0 | Scaffold from the light-chess template (#1) | light-sdk v0.2.0 submodule, Gradle build, `ToolMetadataTest`, the `build` Actions check, `scripts/ci.sh` posting `signoff/emulator`, `scripts/light-build.sh`, the `LightPhone3-pong` AVD recipe on emulator-5560 |
| A-C | Design rounds | `DESIGN.md` items 1-32, `CONTEXT.md`, ADRs 0001-0003, decision log rounds A, B and C |
| S1 | Pure game core (15ace87) | `core/`: fixed 240 Hz Steps, swept collisions, seeded Serve and Computer streams, the Computer at three Difficulties; property tests over 400 seeded Matches; `PureCoreTest`; decision log D1-D22 |
| S1b | Core review fixes (0438de6) | One hit per Paddle per Approach (D23), no save from behind the Face (D24), Phases without data and one `phaseSteps` counter (D25), `StrictMath` and a golden replay (D26) |
| S2 | Playable Tool (97e4344) | Title, play, Paused and Match over screens, the wheel keys (`decideKey`), the tones, `settings.txt`, keep-screen-on, re-entry through `PongOwner` |
| S2b | UI review fixes (4e11ac7) | Resume preselected on re-entry (D27), audio failure plays silent (D28), balanced panel hint (D29), tests for the three key hooks |
| S3 | Docs for 0.1.0 (`release-0.1.0`) | `README.md`, `AGENTS.md`, `RELEASING.md`, `SECURITY.md`, `CONTRIBUTING.md`, `docs/privacy.md`, `docs/release-notes/0.1.0.md`; `docs/domain-ignore.txt` resolves the 17 UI and plumbing names; `scripts/domain-drift.sh` 0 unresolved |
| L1 | Release build on a Light Phone III (2026-10-10) | Non-debuggable 0.1.0 installed and profile-compiled; injected `input keyevent 317/318/319` started a Match, served, moved the Paddle 40 px per Detent (317 up) and played Rallies with Player hits (`PongEvent`); frame interval P50, P90 and P99 16.6 ms over 1,079 frames (`PongPerf`, `p90.py`); thermal status 0; brightness unchanged by the wheel; Akkurat fits the Title hint on one line and breaks the panel hint in two balanced lines |
| L2 | Frame judder fix on a Light Phone III (D30; was N11) | `mise run bench` on the phone: `steps 4:1727 3:13 of 1740 frames at the 1170 px/s cap, frame_ms P50/P90/P99/max 16.6, draw_ms P50 0.24, P90 0.5, P99 0.97`. The by-eye check of a Rally at the cap stays with the owner |
| N5 | README screenshots | `docs/screenshots/title.png`, `rally.png` and `paused.png`, LP3 screencaps checked for personal data |

## Next

| # | What | Done when |
|---|---|---|
| N2 | Wheel direction on the LP3 (DESIGN.md item 19, ADR 0001) | A counter-clockwise turn moves the Paddle and the selection up on the phone, or `WHEEL_UP` is flipped in its own PR; the result is recorded here |
| N3 | Difficulty tuning (decision log D22) | The Detents a second a person sustains is measured on the LP3, and DESIGN.md item 16 is confirmed or retuned with a new decision log entry and a re-recorded golden replay |
| N4 | LP3 screen checks | On the phone: the 40 px step feels right or grows (item 20); every string fits the 16 dp gutter; the panel hint wraps in balanced lines (D29); the three tones play; the screen stays on during play and sleeps on Title, Paused and Match over (item 29) |
| N6 | `signoff/lp3` | `scripts/ci.sh` posts `signoff/lp3` from a round trip on the LP3, under the phone lease |
| N7 | First domain pass tag | `domain-pass/0.1.0` tags the 0.1.0 merge commit, with `domain-drift.sh` at 0 unresolved there |
| N8 | Release 0.1.0 (`RELEASING.md`) | Signed tag `v0.1.0` on the merge commit; the GitHub Release is latest, with the notes (no APK) |
| N9 | Go public | The lp3-prepublish scan of a fresh public clone prints `clean`, two privacy reviews find nothing, and `main` requires `build` and `signoff/emulator` (branch protection needs a public repo) |
| N10 | Light's Tool Library (the maintainer) | Light's build of the `v0.1.0` commit succeeds and shows as pending approval |
| N12 | Paddle-hit hitch on the LP3 (D32) | `mise run bench` on the phone with sound on prints `sound: on` and passes its hitch line (0.1.1 ran 35 frames of 100 ms in 26 s). Run it with `PERFORMANCE_MODE` in `Tones.kt` at `PERFORMANCE_MODE_NONE` (0.1.2) and again at `PERFORMANCE_MODE_LOW_LATENCY`; keep LOW_LATENCY only if NONE hitches or the tones lag the hit by ear. Both results are recorded here |