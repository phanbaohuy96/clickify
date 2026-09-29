# 01 — Scope and slices (Android)

## The problem

Clickify exists on macOS. Its purpose, and the three pressures that decide its design, are stated
once for the whole product in [`../../../docs/sdd/01-scope.md`](../../../docs/sdd/01-scope.md) and
are not repeated here.

What is different here is the machine. Android has no cursor, no window the user moves, and no way
for an ordinary application to send a keystroke to another one. The only sanctioned way to touch
another application is `AccessibilityService.dispatchGesture()`. Every decision below follows from
that one sentence.

The **Scenario / Step / Action × Target** model is unchanged ([ADR-0002]). What changes is the
alphabet: five **Action**s instead of six, two **Target** forms instead of five, and one new
**Step** property, the **Guard**.

## Slices

Every slice leaves a runnable, installable app, as on macOS.

### A1 — Gestures and Markers  ✅

The surface the whole app is built on, and enough to be useful on its own.

- The accessibility service, the **Overlay**, and the floating control
- **Marker**s: drag to place, tap to configure, numbered by **Step** order
- **Action**s: `tap`, `swipe`, `multiTouch`, `globalAction`, `setText`
- **Target**: a fixed point only
- Per-**Step** repeat and delay; per-**Scenario** repeat; a countdown before the first **Step**
- Raw pixels bound to a **Screen profile**; a mismatch blocks ([ADR-0013])
- Every stroke terminated on every exit, and a one-tap **free the touch** recovery
- Onboarding through the accessibility and overlay permissions, including the Android 13
  restricted-settings wall ([02](./02-permissions-and-onboarding.md))

Done when a 15-step sequence of fixed taps runs in the right order, at speed, and Stop always works.

All three clauses are now assertions rather than intentions, in tier 2 against the real accessibility
service: fifteen taps arrive in order at their own pixels 12–16ms apart, and Stop lets the stroke in
flight finish and starts no other. See [`../testing.md`](../testing.md).

### A2 — Recording  ✅

Placing ten to fifteen **Marker**s by hand, **every time the Scenario is edited**, is the difference
between a tool that works and one that gets used. Recording is therefore ahead of recognition here,
the reverse of the macOS order.

- A full-screen **Overlay** swallows each touch and records it
- **Pass-through** re-emits each touch as a **Gesture** so the application underneath still reacts,
  which is what a sequence spanning several screens needs, and it is therefore the mode the floating
  control offers. *Silent* — swallow and do **not** re-emit, for marking several points on one
  screen without setting any of them off — is the same thing minus that one step, and is built as
  well; `RD-9` in [08](./08-recording.md) is how the two are told apart on screen.
- Real timing is kept, as on macOS ([ADR-0004])
- The recorded sequence becomes ordinary **Marker**s, editable like any others

Two things the interface must be honest about rather than hide:

- While recording in *pass-through*, the application underneath is driven by synthetic touches, not
  by a finger. One that rejects synthetic input behaves differently while being recorded.
- `dispatchGesture` costs tens of milliseconds, so **taps record faithfully and swipes do not**: a
  continuous drag has to be re-emitted segment by segment while the finger is still moving, and
  arrives late and jerky. Swipes are better adjusted as **Marker**s afterwards. This is the
  platform's limit, not a shortcut.

### A3 — Recognition  ✅

Specified in [10](./10-recognition.md), where the prefix is `TP` — macOS already spends `RG` on
recognition and `RC` on recording.

- A still frame via `takeScreenshot()`, for cropping **and** for matching. **MediaProjection is
  not used**: it asks the user for the screen every session, which would put a system dialogue in
  front of Start, and the speed it buys is not speed this app can use — [ADR-0016].
- **Target** by **Template**, as a modifier on the point rather than a second kind of **Target**;
  **Guard** by presence *or* absence
- Threshold, search region, timeout, and what to do when it expires ([`DM-16`])
- Ported `TemplateMatcher.swift` and `GrayImage.swift`. **Two-scale matching was not ported**, and
  `TP-18` says at length why: [ADR-0008] answers a problem a Mac has — two display scales inside
  one coordinate space — and [ADR-0013] has already refused to run a **Scenario** against a screen
  that is not the one it was built on.
- Finding by **text** is *not* in this slice. macOS has it because Vision is in the SDK; the
  Android equivalent is ML Kit, and this app is deliberately not distributed through Play.

### A4 — Interface languages  ✅

`en`, `vi`, `zh-Hans`, `ja`, `es`, as on macOS. Last, as on macOS. Specified in
[11](./11-localisation.md), where the prefix is `IL` — macOS spends `LC` on localisation.

The wrinkle was solved rather than inherited: the **Overlay** is not an Activity, so it does not
receive a configuration change when the language changes, and an Overlay window is never recreated
for one. The answer is [ADR-0010]'s in Android's vocabulary — one process-wide value, swapped
inside the single theme both surfaces pass through, with no restart and nothing recreated.

`LocaleManager.setApplicationLocales` is not used: it arrives at API 33 while [ADR-0012] settled
on 30, and it restarts the process to apply — which would take the **Overlay** off the screen of
whatever the user was in the middle of automating.

### A5 — The Scenario map

Specified in [12](./12-scenario-map.md), where the prefix is `MP`.

- A read-only, fullscreen Activity screen: a phone frame shaped like the **Scenario**'s **Screen
  profile**, with each **Step** at the pixel it acts on, numbered and joined in order by arrows
- **Playback**: the map plays the **Scenario** through by itself, **Step** by **Step**, on the path
  where every **Guard** holds and every **Template** is found. It never loops and never waits a real
  delay, and it is not evidence that the **Scenario** works
- **Template** pictures where they were cropped, and a tooltip and a card that say what a **Step**
  looks for and what happens when it gives up
- Controls for play/pause, previous and next **Step**, replay and speed; pinch and pan inside the
  frame; open the **Scenario** in the **Overlay**
- One path and no branch: a search that gives up ends the path or steps around its own **Step**,
  never elsewhere ([ADR-0011])
- Nothing is edited from it, and nothing new is stored

## Out of scope

Stated explicitly so it does not get proposed again.

- **Control flow.** No `if`, no jumps, no loops over a range of **Step**s. Permanent, and a product
  position rather than a postponement — [ADR-0011].
- **Root, ADB and Shizuku.** The app works on an unmodified phone or it does not ship.
- **Google Play.** Play does not permit this use of the Accessibility API. Distribution is by APK.
- **Node targets** — finding a control in the `AccessibilityNodeInfo` tree by id or text. Exact,
  cheap and resolution-independent, and deliberately absent: it is empty in OpenGL games, which is
  where it would be wanted most. Reconsider only when a real use case needs it.
- **A cursor **Target** and a window-relative **Target**.** Neither has anything to attach to.
- **Arbitrary key presses.** Android permits only the fixed set of **Global action**s.
- **Key-by-key typing.** [ADR-0009] does not apply here; `setText` writes the whole string at once.
  Restoring key-by-key means shipping an input method, and that gets its own ADR on the day.
- **Running a Scenario made on another screen or another phone** — [ADR-0013].
- **Sharing `scenario.json` with the macOS app.** Shared glossary, separate schema.
- **Sharing a Scenario with another person.** [ADR-0013] makes a downloaded **Scenario** refuse to
  run on a different screen, so what travels is only its shape — the **Step** order, the **Action**s
  and the **Template**s, with every **Marker** to be placed again. Out of scope now; the
  "export is a zip of a directory" property is kept so it stays cheap to add ([ADR-0014]).
- **Wall-clock scheduling**, as on macOS. A countdown of X seconds is not a schedule: the user reads
  the countdown the automated application is already showing, which is the server's clock, and the
  whole problem of synchronising time disappears.

[ADR-0002]: ../../../docs/adr/0002-step-is-action-times-target.md
[ADR-0004]: ../../../docs/adr/0004-recordings-keep-real-timing.md
[ADR-0008]: ../../../docs/adr/0008-match-templates-at-two-scales.md
[ADR-0009]: ../../../docs/adr/0009-type-ascii-key-by-key.md
[ADR-0010]: ../../../docs/adr/0010-strings-files-and-a-live-bundle-swap.md
[ADR-0011]: ../../../docs/adr/0011-a-scenario-has-no-branches.md
[ADR-0013]: ../adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md
[ADR-0012]: ../adr/0012-min-sdk-30.md
[ADR-0014]: ../adr/0014-no-database.md
[ADR-0016]: ../adr/0016-screenshots-come-from-the-accessibility-service.md
[`DM-16`]: ../../../docs/sdd/02-data-model.md
