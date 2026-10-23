#!/usr/bin/env bash
# Build Glass and install it on the TV as a normal app (Home stays as it is).
# Usage: TV=<tv-ip>:5555 ./install.sh
set -euo pipefail
cd "$(dirname "$0")"
TV="${TV:?set TV to the TV address, e.g. TV=192.168.1.50:5555 $0}"
ADB="${ADB:-$HOME/platform-tools/adb}"
export JAVA_HOME="${JAVA_HOME:-$(ls -d "$HOME"/opt/jdk-17* | head -1)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleRelease -q --console=plain
APK=app/build/outputs/apk/release/app-release.apk
"$ADB" connect "$TV" >/dev/null
LAYOUT=/sdcard/Android/data/io.github.b0reddev.glass/files/layout.txt
BACKUP="backups/${TV%%:*}-layout.txt"
mkdir -p backups
# Glass keeps a copy of its layout on the TV; keep one on the PC too, so a reinstall can put it back.
has_layout() { [ "$("$ADB" -s "$TV" shell "[ -f $LAYOUT ] && echo y" | tr -d '\r')" = y ]; }
if has_layout; then
  "$ADB" -s "$TV" pull "$LAYOUT" "$BACKUP" >/dev/null
  echo "Layout: saved to $BACKUP"
fi
# Android 14 can hold `adb install` waiting for an on-screen confirmation; pm install over the shell doesn't.
"$ADB" -s "$TV" push "$APK" /data/local/tmp/glass.apk >/dev/null
"$ADB" -s "$TV" shell pm install -r /data/local/tmp/glass.apk
# Glass imports the file only when its own prefs are empty, so a normal update never overwrites the live layout.
if ! has_layout && [ -f "$BACKUP" ]; then
  "$ADB" -s "$TV" shell mkdir -p "$(dirname "$LAYOUT")"
  "$ADB" -s "$TV" push "$BACKUP" "$LAYOUT" >/dev/null
  echo "Layout: restored from $BACKUP"
fi
"$ADB" -s "$TV" shell rm /data/local/tmp/glass.apk
"$ADB" -s "$TV" shell cmd package compile -m speed -f io.github.b0reddev.glass >/dev/null
HOME_PKG=$("$ADB" -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
  | tail -n 1 | tr -d '\r' | cut -d/ -f1)
if [ "$HOME_PKG" = io.github.b0reddev.glass ]; then
  # Glass is the Home app: open it through Home. Starting it by name from the shell makes an ordinary second
  # copy next to the Home one, which then keeps running (twice the memory, twice the network callbacks).
  "$ADB" -s "$TV" shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME | grep -E '^(Status|TotalTime)'
else
  "$ADB" -s "$TV" shell am start -W -n io.github.b0reddev.glass/.Home | grep -E '^(Status|TotalTime)'
fi
ls -l "$APK" | awk '{print "APK:", $5, "bytes"}'
