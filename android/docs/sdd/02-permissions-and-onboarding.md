# 02 — Permissions and onboarding (Android)

On macOS the app asks for two TCC permissions and the system does the explaining. Android grants
nothing by dialogue here: the two permissions Clickify cannot work without are both **Settings
screens the user has to walk to**, and on a sideloaded build one of them is *hidden behind a
refusal the user has to trigger first*. Onboarding is therefore not a splash screen. It is the
feature that decides whether the app works at all.

## What the app needs, and when

| Permission | Why | Asked at | Without it |
|---|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | the only way to dispatch a **Gesture** | A1, first run | nothing runs |
| `SYSTEM_ALERT_WINDOW` | the **Overlay** and the floating control | A1, first run | no **Marker**s, no Stop |
| `POST_NOTIFICATIONS` | the foreground-service notification (API 33+) | A1, first run | the run has no visible notice |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | matching at speed | A3 | recognition falls back to ~3fps |
| MediaProjection consent | same, and granted **per session** | A3, each run | same |

**PM-1** `[A1]` The app asks for a permission only when the slice that uses it exists. Nothing in
A1 mentions MediaProjection, and no screen offers a toggle for a capability that is not built.

**PM-2** `[A1]` Each request is preceded by one screen saying, in the interface language, what the
permission lets the app do **and what it does not do** — specifically that Clickify reads no
screen content and sends nothing off the device. This is not politeness. The accessibility consent
dialogue Android shows says the service "can observe your actions" and "retrieve window content",
and a user who has read that deserves the rest of the sentence.

**PM-3** `[A1]` The service declares `android:isAccessibilityTool="false"`, and the app never
claims otherwise. Clickify is an automation tool, not an assistive one; only software built to
help people overcome a disability may declare `true`. The cost of the honest declaration is stated
in [Advanced Protection](#advanced-protection-mode) below, and is accepted.

## The Android 13 wall

From Android 13 (API 33) an app installed from anywhere other than a session-based installer — so
any APK the user downloads and opens, which is exactly how Clickify ships — is placed under
**restricted settings**. Its accessibility toggle is greyed out, and tapping it produces
*"Restricted setting — For your security, this setting is currently unavailable."*

The way out is **Settings → Apps → Clickify → ⋮ → Allow restricted settings**. What makes this a
specification problem rather than a FAQ entry is the ordering:

> **The menu entry does not exist until the restriction has been triggered.**

Sending the user to App info first shows them an overflow menu without the item in it. The only
sequence that works is: go to Accessibility, be refused, *then* go to App info. The onboarding has
to walk the user into a wall on purpose.

**PM-4** `[A1]` On API 33+, when the install source is not a session-based installer, onboarding
uses this order and says so before it starts: **(1)** open Accessibility settings, **(2)** return,
**(3)** if the service is still off, open App info with the "Allow restricted settings" instruction
on screen, **(4)** return to Accessibility.

**PM-5** `[A1]` The install source is read with `PackageManager.getInstallSourceInfo()` (API 30,
which `minSdk` guarantees — [ADR-0012]). A null or unknown installing package means sideloaded, and
sideloaded is assumed when the call throws.

**PM-6** `[A1]` Whether the service is enabled is decided by reading
`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and looking for this app's component, **not** by a
flag the service sets in `onServiceConnected`. The flag is lost when the process is killed, which
is the common case between two visits to Settings; the Secure setting is not.

**PM-7** `[A1]` Step (3) is shown only after step (2) has actually failed. A user on a build
installed through a session-based installer, or on a device whose manufacturer does not apply the
restriction, never sees the restricted-settings instruction at all.

**PM-8** `[A1]` The instruction text names the path and nothing else. It does not attempt to
reproduce a manufacturer's wording: One UI, MIUI/HyperOS and ColorOS each move and rename this
screen, and a screenshot of a Pixel is worse than no screenshot on any of them.

## Leaving, and not nagging

**PM-9** `[A1]` Turning the service off in Settings is a supported action, not an error. The app
notices at the next resume, returns to the onboarding screen, and keeps every **Scenario**.

**PM-10** `[A1]` Onboarding is reachable again from the app at any time, and the app asks for a
permission it has already been refused **once per launch at most**. A tool the user reaches for
several times a day cannot also be a tool that interrupts them.

## Advanced Protection Mode

Android's Advanced Protection Mode — opt-in, expected to reach a stable release after this is
written — prevents accessibility permission from being granted to any service that declares
`isAccessibilityTool="false"`, and revokes it where it was already granted. There is no per-app
exception.

**PM-11** `[A1]` If the service is revoked while Advanced Protection is on, the app says which
setting is responsible and stops. It does not retry, and it does not suggest a way around it.

This is the honest end of the road for `PM-3`: a user who turns Advanced Protection on has chosen a
device where Clickify cannot run, and that choice is theirs to make. Declaring `true` to escape
it would be a lie about what this app is for.

## What the tests can and cannot prove

Per [`../testing.md`](../testing.md), tier 2 grants the service on an emulator with
`adb shell settings put secure enabled_accessibility_services …` and the overlay with
`adb shell appops set … SYSTEM_ALERT_WINDOW allow`. Both bypass the Settings UI entirely, so
`PM-4`, `PM-7` and `PM-8` — the whole restricted-settings sequence — are **not provable in CI**.
They belong to the hardware tier, which is currently unverified.

What tier 2 *can* prove is `PM-6` (kill the process, confirm the state still reads correctly),
`PM-9` (revoke mid-session, confirm the app returns to onboarding with Scenarios intact) and
`PM-10` (confirm one prompt per launch).

[ADR-0012]: ../adr/0012-min-sdk-30.md
