# Ledger

STATUS: Pong plays end to end on the emulator. The design (rounds A, B and C), the pure core (S1),
its review fixes (S1b), the Tool's screens (S2) and their review fixes (S2b) are done, and the docs
for 0.1.0 (S3) are written. Only the scaffold (#1) is merged to `main`; the rest waits on the stacked
branches `core`, `core-fixes`, `ui`, `ui-fixes` and `release-0.1.0`. Not yet released, not yet in
Light's Tool Library, and the repo is still private. `scripts/domain-drift.sh` reports 0 unresolved.

## Handoff

Start at N1. The stack merges into `main` in order, then the LP3 work (N2 to N4) can run while the
release waits only on N5 (the screenshots the README links). D22's tuning (N3) does not block 0.1.0;
the release notes list it as a known limit. Read `AGENTS.md` first; its rules for the wheel keys and
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

## Next

| # | What | Done when |
|---|---|---|
| N1 | Merge the stack into `main` | `main` contains `release-0.1.0`, merged by merge commits with `build` and `signoff/emulator` green on each head |
| N2 | Wheel direction on the LP3 (DESIGN.md item 19, ADR 0001) | A counter-clockwise turn moves the Paddle and the selection up on the phone, or `WHEEL_UP` is flipped in its own PR; the result is recorded here |
| N3 | Difficulty tuning (decision log D22) | The Detents a second a person sustains is measured on the LP3, and DESIGN.md item 16 is confirmed or retuned with a new decision log entry and a re-recorded golden replay |
| N4 | LP3 screen checks | On the phone: the 40 px step feels right or grows (item 20); every string fits the 16 dp gutter; the panel hint wraps in balanced lines (D29); the three tones play; the screen stays on during play and sleeps on Title, Paused and Match over (item 29) |
| N5 | README screenshots | `docs/screenshots/title.png`, `rally.png` and `paused.png`, taken on the LP3 and checked for personal data, are committed |
| N6 | `signoff/lp3` | `scripts/ci.sh` posts `signoff/lp3` from a round trip on the LP3, under the phone lease |
| N7 | First domain pass tag | `domain-pass/0.1.0` tags the merge commit from N1, with `domain-drift.sh` at 0 unresolved there |
| N8 | Release 0.1.0 (`RELEASING.md`) | Signed tag `v0.1.0` on the merge commit; the GitHub Release is latest, with the notes (no APK) |
| N9 | Go public | The lp3-prepublish scan of a fresh public clone prints `clean`, two privacy reviews find nothing, and `main` requires `build` and `signoff/emulator` (branch protection needs a public repo) |
| N10 | Light's Tool Library (the maintainer) | Light's build of the `v0.1.0` commit succeeds and shows as pending approval |
