#!/usr/bin/env bash
# The frame benchmark on a device (decision log D30). Builds and installs the benchmark Tool (the
# `benchmark` build type: release, R8 on, not debuggable, dev-signed, same applicationId), which opens
# straight into an endless Rally at the 1170 px/s cap and logs PongPerf lines for 30 s. Prints P50/P90
# of every PongPerf key (frame_ms, steps, draw_ms, speed) with lp3-measure's p90.py, and how many
# frames ran each number of Steps.
#
#   scripts/bench.sh <adb serial>        (mise run bench <serial>)
#
# The serial is required: the benchmark never picks a device by itself. An emulator serial must run the
# Pong AVD (PONG_AVD, default LightPhone3-pong) and gets the emulator build (scripts/emulator-build.sh);
# only the phone's numbers mean anything. On a shared Light Phone III, hold the phone lease for the run.
# The installed benchmark build replaces the Tool on the device: reinstall the release or debug build
# afterwards. The first PongPerf line (60 frames) is a warm-up and is left out.
#
# LP3_P90 points at p90.py (default: the newest lp3 plugin in the Claude plugin cache). For
# scripts/bench-test.sh only: BENCH_ADB replaces adb, BENCH_APK a prebuilt APK (no Gradle), and
# BENCH_DEADLINE_S the 90 s wait for the "done" line.
set -euo pipefail

[ $# -eq 1 ] && [ -n "$1" ] || { echo "usage: scripts/bench.sh <adb serial>" >&2; exit 2; }
serial=$1
die() { echo "bench: $1" >&2; exit 1; }

cd "$(git rev-parse --show-toplevel)"
adb="${BENCH_ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
p90=${LP3_P90:-$(ls -d "$HOME"/.claude/plugins/cache/lightphone-dev/lp3/*/skills/lp3-measure/scripts/p90.py 2>/dev/null | sort -V | tail -1)}
[ -f "$p90" ] || die "no p90.py (set LP3_P90)"
pkg=com.yarosz.pong
a() { "$adb" -s "$serial" "$@"; }

model=$(a shell getprop ro.product.model 2>/dev/null | tr -d '\r') || die "device $serial is not attached"
case "$serial" in
  emulator-*)
    avd=$(a emu avd name | tr -d '\r' | sed -n 1p)
    [ "$avd" = "${PONG_AVD:-LightPhone3-pong}" ] || die "$serial runs AVD '$avd', not the Pong AVD"
    ;;
esac

apk=${BENCH_APK:-tool/build/outputs/apk/benchmark/tool-benchmark.apk}
if [ -z "${BENCH_APK:-}" ]; then
  build=(mise exec -- ./gradlew -q --console=plain :tool:assembleBenchmark)
  case "$serial" in
    emulator-*) scripts/emulator-build.sh "${build[@]}" ;;
    *) "${build[@]}" ;;
  esac
fi
[ -f "$apk" ] || die "no APK at $apk"

# The LP3 drops off USB while asleep: wake it, wait up to 30 s for adb, clear a PIN-less lock screen.
for _ in $(seq 1 15); do
  if a shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1; then break; fi
  sleep 2
done
a shell wm dismiss-keyguard >/dev/null 2>&1 || die "device not reachable over adb"

a install -r "$apk" >/dev/null
a shell am force-stop $pkg
a logcat -c
a shell am start -W -n $pkg/com.thelightphone.sdk.LightActivity >/dev/null
echo "bench: 30 s Rally at the speed cap on $model"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
deadline=$(( $(date +%s) + ${BENCH_DEADLINE_S:-90} ))
until a logcat -d -s PongPerf:D >"$work/lines" && grep -q 'PongPerf: done' "$work/lines"; do
  [ "$(date +%s)" -lt "$deadline" ] || die "no 'done' line within ${BENCH_DEADLINE_S:-90} s ($(grep -c 'frame_ms=' "$work/lines" || true) lines logged)"
  sleep 2
done

grep 'frame_ms=' "$work/lines" | tail -n +2 >"$work/measured" || true
[ -s "$work/measured" ] || die "no PongPerf lines after the warm-up"
echo
echo "PongPerf on $model ($(wc -l <"$work/measured" | tr -d ' ') lines after a warm-up line)"
python3 "$p90" "$work/measured"
echo "frames by steps: $(grep -o 'steps=[0-9]*' "$work/measured" | sort | uniq -c | awk '{printf "%s%s:%s", sep, substr($2, 7), $1; sep=" "}')"
