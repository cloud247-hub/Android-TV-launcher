#!/usr/bin/env bash
# Tar skjermbilder av launcheren i en Android TV-emulator (brukes av CI).
set -u
OUT=screenshots
PKG=no.cloud247.tvlauncher
mkdir -p "$OUT"

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "saved $1"; }
key() { adb shell input keyevent "$@"; sleep 0.8; }

adb install -r out/cloud247-tv-launcher.apk
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

adb shell am start -W -n "$PKG/.LauncherActivity"
shot 01-hjem 6

key DPAD_RIGHT
key DPAD_RIGHT
shot 02-fokus 1

key DPAD_DOWN
shot 03-alle-apper 1

key --longpress DPAD_CENTER
shot 04-app-meny 2
key BACK

# Opp til toppfeltet og åpne «Kilder»
for _ in 1 2 3 4 5; do key DPAD_UP; done
key DPAD_LEFT; key DPAD_LEFT; key DPAD_LEFT
key DPAD_RIGHT
shot 05-toppfelt 1
key DPAD_CENTER
shot 06-kilder 2
key BACK

key DPAD_RIGHT; key DPAD_RIGHT
key DPAD_CENTER
shot 07-tilpass 2
key BACK

ls -la "$OUT"
