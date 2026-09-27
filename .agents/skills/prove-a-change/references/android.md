# Android rig — emulator, tier 2, and screenshots

`android/docs/testing.md` is the source: tiers, the four harness traps, what the emulator has and has
not proved. Read its *Four traps* section before writing any instrumented test. This file adds the
procedure for driving the **shipped** interface by hand, and what that costs.

## Grants

- Order matters: `appops set com.pbh.clickify ACCESS_RESTRICTED_SETTINGS allow` **first**, then
  `settings put secure enabled_accessibility_services …`, `settings put secure accessibility_enabled 1`,
  `appops set com.pbh.clickify SYSTEM_ALERT_WINDOW allow`. Otherwise the enable is silently reverted.
- After anything that wipes the grant (`testing.md`, *Two facts about the device*; `am start -S`
  force-stops too), the app shows onboarding, which looks like the grant failed. Re-grant, then bring
  the Activity forward with a plain `am start` (no `-S`).
- `dumpsys accessibility | grep -i clickify` says whether the service is really **bound**, not just
  enabled.

## Finding and pressing the Overlay

- The **Overlay** is drawn by the accessibility service, so `uiautomator dump` does not contain it —
  the dump shows whatever is behind (usually the launcher). Locate controls in the **pixels**.
- Press anything while a gesture is in flight the way `testing.md` trap 4 says, never with
  `adb shell input`. Edge swipes: its *Platform behaviour worth knowing about*. Swipes under
  ~900ms may be too few events for Compose to see a drag.
- Coordinates that matter are read from `dumpsys window windows`, not from a screenshot.
- Over Settings the Overlay is force-hidden by the platform (anti-tapjacking). Not a bug.

## Language

`cmd locale set-app-locales com.pbh.clickify --locales vi` reaches the Activity but **not** the
service, so the list is Vietnamese and the Overlay English. Change language through the app's own
picker, which sets both (`IL-5`).

## Screenshots for `docs/assets/`

Every trap above produces an image that looks finished. Before committing one, check in the image
itself: the right language on **both** the Activity and the Overlay, the control actually in the
state claimed, no onboarding. `sips --cropOffset` did not crop where asked; a few lines of Swift
with `CGImage.cropping(to:)` did.
