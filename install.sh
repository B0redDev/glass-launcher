#!/usr/bin/env bash
# Build Glass and install it on the TV as a normal app (Home stays as it is).
# Usage: ./install.sh            TV=192.168.0.26:5555 by default
set -euo pipefail
cd "$(dirname "$0")"
TV="${TV:-192.168.0.26:5555}"
ADB="${ADB:-$HOME/platform-tools/adb}"
export JAVA_HOME="${JAVA_HOME:-$(ls -d "$HOME"/opt/jdk-17* | head -1)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleRelease -q --console=plain
APK=app/build/outputs/apk/release/app-release.apk
"$ADB" connect "$TV" >/dev/null
# Android 14 can hold `adb install` waiting for an on-screen confirmation; pm install over the shell doesn't.
"$ADB" -s "$TV" push "$APK" /data/local/tmp/glass.apk >/dev/null
"$ADB" -s "$TV" shell pm install -r /data/local/tmp/glass.apk
"$ADB" -s "$TV" shell rm /data/local/tmp/glass.apk
"$ADB" -s "$TV" shell pm grant dev.matteo.glass android.permission.ACCESS_FINE_LOCATION   # SSID in the Wi-Fi pill
"$ADB" -s "$TV" shell cmd package compile -m speed -f dev.matteo.glass >/dev/null
"$ADB" -s "$TV" shell am start -W -n dev.matteo.glass/.Home | grep -E '^(Status|TotalTime)'
ls -l "$APK" | awk '{print "APK:", $5, "bytes"}'
