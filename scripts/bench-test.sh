#!/usr/bin/env bash
# Checks scripts/bench.sh without a device or Gradle, against the lp3 plugin's fake adb (LP3_FAKE_ADB,
# default: the newest lp3 plugin in the Claude plugin cache). A thin wrapper answers the calls that fake
# lacks (install, logcat) and passes the rest through, so the fake still refuses any unknown shell
# command. bench.sh must leave the warm-up line out of the report, print P50/P90 for every PongPerf key
# and the Steps histogram, refuse an emulator that is not the Pong AVD, and fail when no "done" comes.
#
#   scripts/bench-test.sh        prints "bench-test: OK" or the first failed check
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
fake=${LP3_FAKE_ADB:-$(ls -d "$HOME"/.claude/plugins/cache/lightphone-dev/lp3/*/tests/fake-adb/adb 2>/dev/null | sort -V | tail -1)}
[ -x "$fake" ] || { echo "bench-test: FAIL no lp3 fake adb (set LP3_FAKE_ADB)" >&2; exit 1; }
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
printf 'mWakefulness=Awake\n' >"$state/dev/FAKE123/power"

warmup="D PongPerf: frame_ms=33.3 steps=8 draw_ms=9.99 speed=1170"
cat >"$work/lines" <<EOF
--------- beginning of main
$warmup
D PongPerf: frame_ms=16.6 steps=4 draw_ms=0.40 frame_ms=16.7 steps=4 draw_ms=0.50 speed=1170
D PongPerf: frame_ms=16.6 steps=3 draw_ms=0.60 frame_ms=16.6 steps=5 draw_ms=0.45 speed=1170
D PongPerf: done
EOF

cat >"$work/adb" <<'EOF'
#!/usr/bin/env bash
echo "adb $*" >>"$FAKE_ADB_STATE/log"
case "${3:-}" in
  install) exit 0 ;;
  logcat)
    [ "$*" = "-s $2 logcat -c" ] && exit 0
    [ "$*" = "-s $2 logcat -d -s PongPerf:D" ] || { echo "wrapper: unexpected logcat: $*" >&2; exit 97; }
    [ -f "$FAKE_ADB_STATE/nodone" ] && { grep -v done "$FAKE_LINES"; exit 0; }
    cat "$FAKE_LINES"; exit 0 ;;
esac
exec "$FAKE_ADB" "$@"
EOF
chmod +x "$work/adb"
touch "$work/apk"

run() { # <serial> <expected exit status>
  set +e
  FAKE_ADB_STATE=$state FAKE_ADB=$fake FAKE_LINES=$work/lines BENCH_ADB=$work/adb BENCH_APK=$work/apk \
    scripts/bench.sh "$1" >"$work/out" 2>&1
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
grep -q 'am start -W -n com.yarosz.pong/' "$state/log" || fail "the Tool was not started"
run emulator-5560 0
run emulator-5554 1
grep -q 'not the Pong AVD' "$work/out" || fail "another AVD's emulator is not refused"
touch "$state/nodone"
BENCH_DEADLINE_S=1 run FAKE123 1
grep -q "no 'done' line" "$work/out" || fail "a missing done line is not reported"

echo "bench-test: OK"
