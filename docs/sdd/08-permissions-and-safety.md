# 08 — Permissions and safety

## Releasing the mouse button

- **SF-1** `[Slice 1]` `[done]` The runner **must** release every held mouse button when it
  finishes for any reason: completion, the user stopping it, an error, or task cancellation.

  This is not theoretical. `holdMs` has existed since Slice 1 and drag since Slice 2 — both leave
  a `mouseDown` with no `mouseUp` if cut off part-way. The operating system then believes the
  button is held down, and the user **loses the ability to interact** until they click once
  themselves. Since `⌥⌘S` is the only escape hatch while a scenario is running, it must never be
  the thing that puts the machine into that state.

- **SF-2** `[Slice 1]` `[done]` Releasing must work even after the task has been cancelled — which
  means it cannot sit behind any `await` that can throw `CancellationError`.

## System permissions

- **SF-3** `[Slice 1]` `[done]` **Accessibility** — needed to emit events and to ask which process
  owns a point. Requested when Start is first pressed.
- **SF-5** `[Slice 4]` `[done]` **Screen Recording** — needed for **Template**s and for finding by
  text. Only requested when the user actually uses it, never at launch.
- **SF-6** `[done]` Clickify **never** requests **Input Monitoring**, and the recorder **never**
  listens to the keyboard. See [ADR-0003](../adr/0003-no-keyboard-capture-when-recording.md).

  Three layers of verification, of which the first is the only one that **automatically stops a
  future maintainer**:

  1. `ScenarioRecorder.recordedEventTypes` contains mouse and scroll only, and has a test guarding
     it — adding `.keyDown` turns the test red immediately. The mask used to live inside
     `startSession`, where only a real `CGEventTap` ever reached it, so the project's strongest
     safety constraint **had no test holding it**.
  2. Injecting a `keyDown` carrying a string straight into `ScenarioRecorder.handle` produces no
     Step — so even if the mask were widened, the decoding path still would not turn keys into
     data.
  3. There is no `IOHIDRequestAccess`/`IOHIDCheckAccess` call anywhere in the source, and
     `Info.plist` has no key requesting this permission.

  The two remaining uses of `keyDown` are `NSEvent.addLocalMonitorForEvents` in the two
  region-drawing overlays: a **local** monitor only sees keys delivered to the app's own windows,
  needs no permission at all, and is there purely to catch Esc.
- **SF-10** `[done]` The message for a missing **Accessibility** permission must name **both**
  possibilities: never granted, and granted but invalidated by an app update. Installing over an
  existing copy changes the signature, macOS invalidates the old grant **but still shows the
  toggle as on**; the user opens System Settings, sees Clickify enabled, and concludes the app is
  broken. The fix is to toggle it off and on. No API can read the real state to tell the two
  apart, so both have to be stated — the same approach as `SF-7`. Found while running session A of
  the [manual tests](../manual-e2e-tests.md), not while writing the spec: only installing over a
  copy that already had the permission exposes it.

  **The cause, and how to end it on a development machine:** ad-hoc signing (`codesign --sign -`)
  produces a designated requirement of `cdhash H"…"`, and the cdhash changes with **every build**.
  Signing with a stable certificate produces `identifier "com.local.Clickify" and certificate leaf
  = H"…"`, which does not depend on the build — grant the permission once and you are done.
  `scripts/create-local-signing-identity.sh` creates such a self-signed certificate, and
  `build-app.sh` uses it automatically when present.

  This does **not** make `SF-10` unnecessary: real users still get ad-hoc builds.

  **Measured again, and one claim to withdraw:** after the first install with a stable
  certificate, **Accessibility** did indeed survive — pressing `⌥⌘R` brought the recording panel
  straight up, with nothing to grant again. But **Screen Recording did not**: the first time
  recognition was used after that install, `CGPreflightScreenCaptureAccess()` returned `false` and
  macOS raised the permission dialog again, **while System Settings still showed Clickify as
  enabled** — exactly the trap `SF-10` describes, just on the other permission. So the stable
  certificate helps Accessibility and has proved nothing for Screen Recording. `SF-7` remains the
  only thing that rescues the user in that situation, and it did its job (see `C14` of the
  [manual tests](../manual-e2e-tests.md)).
- **SF-7** `[Slice 4]` `[done]` The error message for a failed screen capture must name **both**
  possibilities: the permission was never granted, and it was granted but the app needs to be
  quit and reopened.

  The original spec demanded *distinguishing* the two. Not possible:
  `CGPreflightScreenCaptureAccess()` returns `false` for both, and no API can read the real TCC
  state. An ad-hoc signed build is treated by macOS as a different application after every update,
  so it falls into the second case very often, which makes naming both more useful than saying
  nothing.

## Typing and focus

- **SF-4** `[Slice 2]` `[done]` Before every typing **Step**, if there is a **Locked application**
  and it is not the frontmost one, call `activate` and wait up to 500 ms for it to come forward.
  If it still does not, **stop the Scenario**.

  The reason: keyboard events carry no coordinates, so `EX-10` cannot protect them. Without this
  check, a notification stealing focus part-way through would make the scenario type into the
  wrong application.

## Limits

- **SF-8** `[Slice 1]` `[done]` The runner always yields at least 10 ms between consecutive events,
  even when the **Step**'s delay is 0, so that a misconfigured **Scenario** cannot freeze the
  system interface. This lower bound existed in 1.2.0 as an input constraint and has moved to being
  a run-time one.
- **SF-9** `[Slice 1]` `[done]` An unlimited **Scenario** must still check for cancellation at
  every **Step**, so that `⌥⌘S` takes effect within one **Step**.
