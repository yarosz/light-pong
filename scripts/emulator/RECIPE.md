# LightPhone3-pong AVD: recipe

An LP3-shaped emulator for the Pong Tool on port 5560. It runs next to the other Tools' emulators (5554, 5556, 5558) without touching them.

## Why a fresh build and not a copy of the overlay

While `LightPhone3` runs, qemu holds all its `*.qcow2` overlays open read-write (`lsof` shows FD mode `u`), and `userdata-qemu.img.qcow2` is still being written. A copy made then can be inconsistent. So the clone reuses only the static `config.ini` and repeats the base AVD's setup on the same system image (`system-images;android-34;default;arm64-v8a`, test-keys).

You can copy the overlays once `LightPhone3` is shut down. The recipe below does not need that.

## 1. Create the AVD (LightPhone3 files are only read)

```bash
D=~/.android/avd/LightPhone3-pong.avd
mkdir "$D"
sed -e 's/^avd.id=.*/avd.id=LightPhone3-pong/' -e 's/^avd.name=.*/avd.name=LightPhone3-pong/' \
    ~/.android/avd/LightPhone3.avd/config.ini > "$D/config.ini"
printf 'avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/LightPhone3-pong.avd\ntarget=android-34\n' "$D" \
    > ~/.android/avd/LightPhone3-pong.ini
emulator -list-avds   # LightPhone3, LightPhone3-pong
```

(`config.ini` already has `hw.lcd.density=480` and 1080x1240.)

## 2. Boot on 5560 (use these flags on every boot)

```bash
nohup emulator -avd LightPhone3-pong -port 5560 -writable-system -no-snapshot -no-boot-anim \
  -dns-server 1.1.1.1,8.8.8.8 -no-window > emu-5560.log 2>&1 &
bash wait-boot.sh          # polls adb -s emulator-5560 for sys.boot_completed
```

Leave out `-no-window` to get a window.

## 3. One-time setup. Every adb call uses `-s emulator-5560`

The LightOS emulator app shares uid 1000 with the system, so it must be signed with the AOSP platform test key (`light-sdk/docs/system_app`). The build looks for it at `light-sdk/sdk/emulator/keys/platform.jks` (ignored by the SDK's git). Without it the build only warns, and the next boot loops on "Signature mismatch on system package".

```bash
(cd ../.. && ./gradlew :sdk:emulator:assembleDebug)   # with keys/platform.jks in place
A="adb -s emulator-5560"
$A root && $A wait-for-device && $A remount     # first time: "Overlayfs enabled ... reboot"
bash wait-boot.sh reboot
$A root && $A wait-for-device && $A remount
$A shell mkdir -p /system/priv-app/LightOSEmulator
$A push ../../light-sdk/sdk/emulator/build/outputs/apk/debug/emulator-debug.apk \
        /system/priv-app/LightOSEmulator/LightOSEmulator.apk
$A shell chmod 644 /system/priv-app/LightOSEmulator/LightOSEmulator.apk
bash wait-boot.sh reboot                        # PackageManager picks it up as system, uid 1000
$A shell settings put global window_animation_scale 0
$A shell settings put global transition_animation_scale 0
$A shell settings put global animator_duration_scale 0
$A shell wm density 480
$A shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
bash set-home.sh    # root, set-home-activity com.thelightphone.sdk.emulator/.MainActivity, wait until persisted
```

Then, in LightOS on the emulator, open Settings, Allowed Tools and pick "All Tools" (`mise run ui key home`, dismiss the "Viewing full screen" dialog with GOT IT, tap the circle, `tap Settings`, the Allowed Tools row, then All Tools). The v0.2.0 emulator defaults to "Community Tools", which hides a dev-signed Tool and refuses its service token.

Gotcha: `set-home-activity` is written to `package-restrictions.xml` lazily. The first time, `emu kill` ran a few seconds after setting it and the setting was lost; Launcher3 was home on the next boot. `set-home.sh` waits until the file names the package before it returns.

## 4. Verify

```bash
bash verify.sh
```

Expected: avd `LightPhone3-pong`, density 480, size 1080x1240, `app=1080x1168`, `[x] ...navbar.gestural` and `navigation_mode` 2, `/system/priv-app/LightOSEmulator/LightOSEmulator.apk` with `uid=1000`, HOME resolves to `com.thelightphone.sdk.emulator/.MainActivity`, animation scales 0 0 0, `ping github.com` resolves and gets a reply. The emulator's user-mode network garbles ICMP timings, so the ping only proves DNS and reachability.

## 5. Shut down

```bash
adb -s emulator-5560 emu kill
adb devices     # emulator-5554 still listed, unchanged
```

Helper scripts `wait-boot.sh`, `set-home.sh` and `verify.sh` are in this directory. They only ever call `adb -s emulator-5560`.
