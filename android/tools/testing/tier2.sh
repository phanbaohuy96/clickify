#!/usr/bin/env bash
#
# Tier 2 — the instrumented tests, on an emulator or a device (see ../../docs/testing.md).
#
# The accessibility service and the Overlay are granted by the suite itself, in `Tier2.awaitService`,
# through `UiAutomation.executeShellCommand`. They are deliberately **not** granted here: AGP
# uninstalls the application before it installs the two APKs, and a component that is briefly not
# installed is dropped from `enabled_accessibility_services` by the system — so anything granted
# before Gradle runs is gone by the time the first test does.
#
# What is left for a script is the state of the device itself, which no test can fix from inside.
#
# Usage: tools/testing/tier2.sh [extra gradle arguments]
#        ANDROID_SERIAL=emulator-5554 tools/testing/tier2.sh
set -euo pipefail

PACKAGE="com.pbh.clickify"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

find_adb() {
  if command -v adb >/dev/null 2>&1; then command -v adb; return; fi
  for root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -n "$root" ] && [ -x "$root/platform-tools/adb" ] && { echo "$root/platform-tools/adb"; return; }
  done
  echo "adb not found: put it on PATH or set ANDROID_HOME" >&2
  exit 1
}

ADB="$(find_adb)"
say() { printf '\n== %s\n' "$1"; }

say "device"
"$ADB" wait-for-device
DEVICE="$("$ADB" shell getprop ro.product.model | tr -d '\r') API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "$DEVICE"

# A screen that is off or locked delivers no touches, and the failure looks like a bug in the app.
say "screen"
"$ADB" shell input keyevent KEYCODE_WAKEUP || true
"$ADB" shell wm dismiss-keyguard || true

# Nothing of ours may be in front of the target app. An Overlay left running from a previous session
# would be, and a force-stop is safe here because no gesture is in flight yet.
say "a clear screen"
"$ADB" shell am force-stop "$PACKAGE" || true

say "tests"
# Cleared first so the measurements below belong to this run and no other.
"$ADB" logcat -c || true
set +e
(cd "$HERE" && ./gradlew --console=plain :app:connectedDebugAndroidTest "$@")
GRADLE_STATUS=$?
set -e

say "what the tests measured"
"$ADB" logcat -d -s Tier2:I | sed -e 's/^.*Tier2: //' | grep -v '^---' || true

say "what the run left behind"
echo "enabled_accessibility_services: $("$ADB" shell settings get secure enabled_accessibility_services | tr -d '\r')"
echo "(AGP uninstalls both APKs afterwards, so 'null' here is expected rather than a failure.)"

exit $GRADLE_STATUS
