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

# Ned til «Alle apper» og to til høyre
key DPAD_DOWN
key DPAD_RIGHT
key DPAD_RIGHT
shot 02-fokus 1

# Hold inne OK → app-meny → «Legg til i favoritter»
key --longpress DPAD_CENTER
shot 03-app-meny 2
key DPAD_CENTER
shot 04-ny-favoritt 2

# Opp til favoritter, opp igjen til toppfeltet
key DPAD_UP
shot 05-opp-til-favoritt 1
key DPAD_UP
shot 06-toppfelt 1

# Kilder
key DPAD_RIGHT
key DPAD_CENTER
shot 07-kilder 2

# Tilpass
key DPAD_RIGHT
key DPAD_RIGHT
key DPAD_CENTER
shot 08-tilpass 2
key BACK

ls -la "$OUT"
