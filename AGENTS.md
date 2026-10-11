# Pong

A LightOS Tool for the Light Phone III: Pong against the computer, played with the scroll wheel, for
any LP3 owner who wants a short, quiet game.

- **The spec:** `DESIGN.md`. Item numbers are stable; code, tests and docs cite them ("DESIGN.md item
  20"). Change the spec in the same PR as the behaviour.
- **Architecture decisions:** `docs/adr/`. Read the relevant ADR before changing the input model
  (0001), the core's purity or determinism (0002), or where the Match lives (0003).
- **Settled rulings:** `docs/design/decision-log.md` holds every ruling, from the design rounds (A, B,
  C) and the build (D1 onward). Read it before reopening a decision, and cite its ids in PRs. A ruling
  the build makes where the spec is silent gets the next D number.
- **State and next work:** `LEDGER.md`. Read it before starting, and update it before ending a
  session.

## Domain language

`CONTEXT.md` is the glossary: use its terms (Match, Point, Rally, Serve, Phase, Paddle target,
Detent, Approach, Intercept, Aim, ...) in code, UI copy and docs. Its _Avoid_ words (game, round,
AI, bot, tick, ...) stay out of type names.

- **In every PR:** a new domain concept enters `CONTEXT.md` in the same PR, or goes in
  `docs/domain-ignore.txt` as implementation or UI vocabulary, with a comment saying why. UI copy
  belongs in `DESIGN.md`'s copy list.
- **Domain pass, at two points in every milestone:** before its first PR and after its last PR
  merges, and whenever `scripts/domain-drift.sh` lists more than a handful of candidates. A pass:
  1. Run `scripts/domain-drift.sh` (`mise run domain-drift`).
  2. Run the `grilling` and `domain-modeling` skills with an answering agent grounded in
     `CONTEXT.md`, the ADRs, `DESIGN.md`, the decision log and the code.
  3. Resolve every candidate as a glossary term, a rename, UI vocabulary or an ignore entry. Offer an
     ADR only when a decision is hard to reverse, surprising, and a real trade-off.
  4. Done when `domain-drift.sh` reports 0 unresolved. Tag the merged commit
     `domain-pass/<milestone>` for the pass before a milestone, and `domain-pass/<milestone>-close`
     for the one after it.

## Layout

- `light-sdk/`: Light's SDK as a pristine submodule, pinned to a release tag. Never edit it.
- `tool/`: the Tool. Code in `tool/src/main/kotlin/com/yarosz/pong/`, tests in `tool/src/test/`.
  - `core/`: the game (ADR 0002). `Match` runs fixed 240 Hz Steps from a Seed and the calls made to
    it; `Rally.kt` sweeps the Ball's collisions in one Step; `Computer` steers the Computer's Paddle;
    `Field.kt` holds the geometry, `Difficulty` the table of DESIGN.md item 16, and `Wheel.kt` the
    Detent rate and the Sensitivity's step curve (item 20). The core reports
    Sound events and never plays audio.
  - `screen/`: the screen's state, still plain Kotlin. `PongState` takes every input and holds the
    Title selection, the Match and the panel selection; `PongOwner` keeps the process's one
    `PongState` (ADR 0003); `Keys.kt` decides what each key does; `Settings.kt` reads and writes
    `settings.txt`; `BurstLog.kt` makes the wheel's `spin` lines; `Tone.kt` generates the tones' samples.
  - The package root: the Android side. `PongScreen.kt` (the `LightScreen`, its view model and the
    `withFrameNanos` frame loop), `FieldRenderer.kt`, `Menus.kt` and `Tones.kt` (the `AudioTrack`s).
- `scripts/`: `ci.sh`, `light-build.sh`, `emulator-build.sh`, `pong-emu.sh`, `domain-drift.sh`, and
  `bench.sh` with `p90.py` and its check `bench-test.sh`, which runs against the fake adb in
  `scripts/fake-adb/` (no device, no plugin; `ci.sh` runs it with the unit tests); `scripts/emulator/`
  builds and checks the Pong AVD.
- `mise.toml`, `.mise/tasks/ui`: toolchain and commands; run `mise tasks`.

## Rules this repo keeps

- **The core is pure.** Nothing under `core/` or `screen/` imports `android`, `androidx` or
  `com.thelightphone`; `PureCoreTest` fails the build if one does. Platform code stays in the
  package root, which reads the state each frame and passes inputs in. The core is deterministic:
  timers count whole Steps, `tan` and `exp` come from `StrictMath`, and `GoldenReplayTest` pins one
  recorded Match. A change that alters replays re-records the golden on purpose and says so in the
  PR.
- **Pong consumes every wheel key.** 317, 318 and 319 return true from `onKeyDown` (repeats
  included), `onKeyUp` and `onKeyMultiple` in every state, even when nothing moves, so LightOS never
  sees a turn or a click. Every other key returns false and stays with the system. One function,
  `decideKey` in `screen/Keys.kt`, decides this for every screen (DESIGN.md item 22). A turn acts on
  every key-down; a click acts only on a key-down with repeat count 0.
- **One constant knows the wheel's direction.** `WHEEL_UP` in `screen/Keys.kt` (317, counter-clockwise
  moves up) is still to be hand-tested on the LP3 (`LEDGER.md` N2). Flip it there and nowhere else (ADR 0001).
- **Nothing is written mid-match.** Only the Difficulty, the sound choice and the Sensitivity persist,
  in `settings.txt`, written when they change (DESIGN.md item 31).

## Platform rules (enforced by Light's build plugin)

- Write Kotlin, Compose UI, inside `LightScreen`/`LightViewModel`. Java source files fail the build.
- Use only allowlisted dependencies, at exact versions, and allowlisted permissions. Reach files
  through the screen's `filesDir`. The allowlists are under `light-sdk/plugin/src/main/`.
- Reflection is blocked, including `.javaClass`. Native code (NDK/JNI) is disallowed by Light policy.
- The plugin generates the manifest from `tool/lighttool.toml`. Pong declares no permission and no
  capability; `ToolMetadataTest` guards both.
- `tool/build.gradle.kts` excludes ML Kit, Play services, Firebase, Data Transport and CameraX, which
  `sdk:ui` brings only for `LightQrCodeScanner`. Never call the scanner: it would crash.
- `serverPackage` in `tool/lighttool.toml` stays `"com.lightos"` (LightOS on the phone): Light builds
  releases from the committed file. Emulator builds swap it at build time through
  `scripts/emulator-build.sh` (`mise run tool` and `mise run ci` already do). `ToolMetadataTest` and
  `light-build.sh` enforce this.

## Devices

- The Pong emulator is the AVD `LightPhone3-pong` on emulator-5560 (`scripts/emulator/RECIPE.md`).
  Scripts find it by AVD name through `scripts/pong-emu.sh` (override with `PONG_AVD`) and never fall
  back to another emulator. Never touch other emulators on the machine.
  Every raw adb call names its device with `-s`; never run `adb kill-server`.
- When several sessions share one Light Phone III, take turns through a lease kept outside this repo (the lp3 plugin's `lp3-lease.sh`).

## Measuring frames and spins

During a Rally every build logs one `PongPerf` line a second (debug level), 60 frames to a line, for
`scripts/p90.py` (vendored from the lp3 plugin's lp3-measure). The key names are frozen; scripts parse them:

- `frame_ms`: the frame's interval, to 0.1 ms.
- `steps`: the Steps that frame ran (4 at 60 Hz; D30).
- `draw_ms`: the field's last draw, timed around `drawField` on the main thread, to 0.01 ms. It is the
  draw recorded into the display list, not the GPU's work.
- `speed`: the Ball's |vx| in px/s, once a line, at its last frame.

Each wheel spin during play logs one `PongEvent` line when it ends, after 150 ms with no Detent or when
play stops mid-spin (DESIGN.md item 20, decision log D31), for tuning the step curve. Its key names are
frozen too:

- `spin detents`: the Detents in the spin.
- `ms`: from its first Detent to its last, in event-time ms.
- `peak_rate`: the fastest Detent rate the Match applied among them (the rate `Match.turn` returns), in
  Detents a second, rounded.

Each Match starts with one `PongEvent` line, `match seed=<n> difficulty=<easy|normal|hard>
sensitivity=<normal|fast> sound=<on|off>`, so a `spin` line can be read against the curve it ran on
and a benchmark against its sound choice. Its keys (`match seed`, `difficulty`, `sensitivity`,
`sound`) are frozen too.

`mise run bench <serial>` builds the `benchmark` build type (release with `BuildConfig.BENCHMARK_RALLY`:
not debuggable, dev-signed, same applicationId), installs it over the Tool, and opens straight into an
endless Rally at the 1170 px/s cap for 30 s, then prints P50/P90 of each key, the frames by Steps,
`sound: on|off` (from the `match` line; the benchmark plays tones when sound is on) and
`hitches (frame_ms > 33.4): N of M`. It exits 1 when the hitches are more than 0.5% of the frames: a
hitch is a frame that missed at least one vsync, as each paddle hit's blocking tone did in 0.1.1
(decision log D32). Reinstall the normal build afterwards. Only the LP3's numbers mean anything; hold the phone lease.

## How changes land

Every change arrives as a pull request into `main`. Merge only when the GitHub Actions `build` check
(unit tests and the Light-builder simulation on a clean Linux machine) and `signoff/emulator` are
green. Only `mise run ci` posts `signoff/*` statuses (through the GitHub API, described as local CI);
never post them by hand.

Every PR also needs review before merge, by an agent other than its author: a product review for
changes to behaviour, copy or docs, and a code review for code changes. A PR can need both. Findings
are fixed in the same PR, and a fix that settles a question the spec left open becomes a decision log
entry (D23 to D29 came from these reviews). Re-request review after changes. PRs that change what
the screen shows attach one or two `mise run ui shot` screenshots.

Merge with a merge commit, not a squash; the PR title is the merge commit's subject. Commits are
signed.

Public evidence (PR comments, statuses) carries generic facts only: no serials, hostnames or local
paths. Nothing personal goes in the repo.

## Verification loop

A change is done when all of these are _green_:

1. `./gradlew :tool:testDebugUnitTest`: the core's property tests play 400 seeded Matches with random
   input and check the invariants after every Step. `-Dpong.winrate=true` adds the win-rate
   simulation (decision log D22).
2. Emulator: `mise run emu` (boot), `mise run tool` (build, install and launch), then drive and read
   the screen with `mise run ui` (`tap <label>`, `key <name>`, `shot`; `mise run ui help`). Check that
   `dumpsys window` `mCurrentFocus` names `com.yarosz.pong` before any injected key or tap. Look at a
   screenshot for anything visual: tests cannot see rendering bugs.
3. `mise run light-build` on a committed HEAD (Light's builder, rehearsed offline).
4. Light's plugin checks pass (they run in every build).
5. `mise run domain-drift` reports 0 unresolved.
