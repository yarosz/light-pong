#!/usr/bin/env bash
# Checks scripts/bench.sh without a device or Gradle, against scripts/bench-fake-adb.sh, which answers
# only the calls bench.sh makes and refuses any other. bench.sh must leave the warm-up line out of the
# report, print P50/P90 for every PongPerf key, the Steps histogram, the sound choice and the hitch
# count, fail when hitches (frame_ms > 33.4) are more than 0.5% of the frames, refuse an emulator that
# is not the Pong AVD, and fail when no "done" comes. scripts/ci.sh runs it with the unit tests.
#
#   scripts/bench-test.sh        prints "bench-test: OK" or the first failed check
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
fail() { echo "bench-test: FAIL $1" >&2; [ -f "$work/out" ] && sed 's/^/  | /' "$work/out" >&2; exit 1; }

state=$work/state
for serial in FAKE123 emulator-5560 emulator-5554; do
  mkdir -p "$state/dev/$serial"
  echo TLP301 >"$state/dev/$serial/model"
done
echo LightPhone3-pong >"$state/dev/emulator-5560/avd"
echo LightPhone3-other >"$state/dev/emulator-5554/avd"

warmup="D PongPerf: frame_ms=33.3 steps=8 draw_ms=9.99 speed=1170"
cat >"$state/lines" <<EOF
--------- beginning of main
D PongEvent: match seed=12 difficulty=normal sensitivity=normal sound=on
$warmup
D PongPerf: frame_ms=16.6 steps=4 draw_ms=0.40 frame_ms=16.7 steps=4 draw_ms=0.50 speed=1170
D PongPerf: frame_ms=16.6 steps=3 draw_ms=0.60 frame_ms=16.6 steps=5 draw_ms=0.45 speed=1170
D PongPerf: done
EOF
touch "$work/apk"

run() { # <serial> <expected exit status>
  set +e
  FAKE_ADB_STATE=$state BENCH_ADB=scripts/bench-fake-adb.sh BENCH_APK=$work/apk scripts/bench.sh "$1" >"$work/out" 2>&1
  status=$?
  set -e
  [ "$status" = "$2" ] || fail "$1: exit $status, expected $2"
}

run FAKE123 0
grep -q '^frame_ms n=4 p50=16.6 p90=16.7 p99=16.7 max=16.7$' "$work/out" || fail "frame_ms percentiles (warm-up left out)"
grep -q '^steps n=4 p50=4 p90=5' "$work/out" || fail "steps percentiles"
grep -q '^draw_ms n=4 p50=0.45 p90=0.6' "$work/out" || fail "draw_ms percentiles"
grep -q '^speed n=2 p50=1170' "$work/out" || fail "speed percentiles"
grep -q '^frames by steps: 3:1 4:2 5:1$' "$work/out" || fail "steps histogram"
grep -q '^sound: on$' "$work/out" || fail "sound choice"
grep -q '^hitches (frame_ms > 33.4): 0 of 4$' "$work/out" || fail "hitch count"
grep -q 'am start -W -n com.yarosz.pong/' "$state/log" || fail "the Tool was not started"
! grep -q 'fake adb: unknown call' "$work/out" || fail "bench.sh made a call the fake does not know"
run emulator-5560 0
run emulator-5554 1
grep -q 'not the Pong AVD' "$work/out" || fail "another AVD's emulator is not refused"
cp "$state/lines" "$work/passing"

frames() { # <count> <frame_ms>: one PongPerf line of that many frames
  local line="D PongPerf:" i
  for ((i = 0; i < $1; i++)); do line+=" frame_ms=$2 steps=4 draw_ms=0.40"; done
  echo "$line speed=1170"
}
hitch_fixture() { # <frames at 100 ms> among 200 frames
  { echo "D PongEvent: match seed=12 difficulty=normal sensitivity=normal sound=off"
    echo "$warmup"
    frames $(( 200 - $1 )) 16.7
    frames "$1" 100.0
    echo "D PongPerf: done"; } >"$state/lines"
}
hitch_fixture 1
run FAKE123 0
grep -q '^hitches (frame_ms > 33.4): 1 of 200$' "$work/out" || fail "one hitch in 200 frames (0.5%) passes"
grep -q '^sound: off$' "$work/out" || fail "sound off"
hitch_fixture 2
run FAKE123 1
grep -q '^hitches (frame_ms > 33.4): 2 of 200$' "$work/out" || fail "hitch count with two hitches"
grep -q 'hitches are more than 0.5% of the frames' "$work/out" || fail "two hitches in 200 frames do not fail the bench"
grep -q '^frame_ms n=200 ' "$work/out" || fail "a failing bench still prints its report"
cp "$work/passing" "$state/lines"

touch "$state/nodone"
BENCH_DEADLINE_S=1 run FAKE123 1
grep -q "no 'done' line" "$work/out" || fail "a missing done line is not reported"

echo "bench-test: OK"
