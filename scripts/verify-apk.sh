#!/usr/bin/env bash
# Checks a built APK has everything Android Auto needs to list Depth in its launcher.
# Usage: scripts/verify-apk.sh path/to/app.apk
set -euo pipefail
apk="${1:?usage: verify-apk.sh app.apk}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
aapt2="$(ls -d "$sdk"/build-tools/*/aapt2 | sort -V | awk 'END{print}')"
manifest="$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")"
fail() { echo "FAIL: $1"; exit 1; }
grep -q 'android.media.browse.MediaBrowserService' <<<"$manifest" || fail "no MediaBrowserService intent filter"
grep -q 'com.google.android.gms.car.application' <<<"$manifest" || fail "no car application meta-data"
grep -q 'android.hardware.type.automotive' <<<"$manifest" && fail "declares Automotive OS hardware (hides it from phone Android Auto)"
desc="$("$aapt2" dump xmltree --file res/xml/automotive_app_desc.xml "$apk" 2>/dev/null || true)"
if [[ -z "$desc" ]]; then
  # Release builds shrink resource names; find the descriptor by content instead.
  for f in $(unzip -Z1 "$apk" 'res/*.xml'); do
    d="$("$aapt2" dump xmltree --file "$f" "$apk" 2>/dev/null || true)"
    grep -q 'automotiveApp' <<<"$d" && desc="$d" && break
  done
fi
grep -q '"media"' <<<"$desc" || fail "automotive_app_desc does not declare <uses name=\"media\"/>"
echo "OK: $apk is discoverable by Android Auto as a media app"
"$aapt2" dump badging "$apk" | awk "NR==1"
