#!/usr/bin/env bash
# One command to see Depth in Android Auto on your computer, using your real phone and
# Google's Desktop Head Unit (the same car-display emulator Google uses for testing).
#
# One-time phone setup:
#   1. Android Auto app → Settings → tap "Version" 10× to enable developer mode.
#   2. ⋮ menu → Developer settings → enable "Unknown sources".
#   3. ⋮ menu → "Start head unit server".
#   4. Plug the phone into this computer with USB debugging on.
#
# Usage: scripts/dhu.sh [path/to/Depth.apk]   (default: newest APK in dist/ or the build)
set -euo pipefail
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB="$SDK/platform-tools/adb"; command -v adb >/dev/null && ADB="$(command -v adb)"
apk="${1:-$(ls -t dist/*.apk app/build/outputs/apk/release/*.apk 2>/dev/null | head -1 || true)}"

"$ADB" get-state >/dev/null 2>&1 || { echo "No phone found over adb. Plug it in with USB debugging enabled."; exit 1; }
if [[ -n "$apk" ]]; then
  echo "Installing $apk"
  "$ADB" install -r "$apk"
fi

DHU="$SDK/extras/google/auto/desktop-head-unit"
if [[ ! -x "$DHU" ]]; then
  echo "Installing the Desktop Head Unit (one time)…"
  "$SDK/cmdline-tools/latest/bin/sdkmanager" "extras;google;auto"
fi

"$ADB" forward tcp:5277 tcp:5277
echo "Launching the Desktop Head Unit — open the app grid and choose Depth."
cd "$(dirname "$DHU")" && exec "$DHU"
