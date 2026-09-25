# Clickify for macOS

The macOS member of Clickify. Every command below runs from this directory (`macos/`); the
shared glossary and decision records live one level up. See the [repository README](../README.md).

A native menu-bar app. Two interface surfaces, but only **one** runner underneath:

- **Simple** — one repeated operation, exactly as in earlier versions.
- **Scenario** — an ordered sequence of operations, each step with its own action, target,
  repeat count and delay.

## Scenarios

A **Step** is an **Action** × **Target** pair, two independent axes:

| Action | Target |
|---|---|
| Click (left/right/middle, n times, hold N ms) | At the cursor |
| Scroll (horizontal, vertical) | A fixed point on screen |
| Move | An offset from the nearest corner of the app's window |
| Drag | |
| Type a string | |
| Press a key combination | The centre of a template found on screen |
| | The centre of a piece of text found on screen |

Splitting the two axes is what keeps "double-click on a template" or "scroll where it says OK"
from being new kinds of step — they already exist, out of 6 actions × 5 targets.

Window anchoring attaches to the **corner nearest the point**, not always the top-left: that is
what keeps a button in the bottom-right corner correct when you enlarge the window.

Scenarios live in `~/Library/Application Support/Clickify/Scenarios/<id>/scenario.json`,
one directory per scenario. Deleting a scenario deletes the whole directory.

## Image and text recognition

A step can aim at a **template** or a **piece of text** instead of fixed coordinates.

Drag out any region of the screen to crop a template. No locked application is needed, and **the
target does not have to be on screen**: the button you want to press usually only appears after
the page loads, so open an old screenshot in Preview and crop from that instead.

Every template step has a **timeout** and a **what to do when it expires**:

```
Step 2  click @ template "Save button"   wait up to 10s  → on timeout: stop the scenario
Step 5  click @ template "close advert"  wait up to 0s   → on timeout: skip the step
```

Step 2 is "wait for the button to appear, then press it". Step 5 is "close it if it is there,
otherwise carry on". Both are just two numbers — a scenario has no `if` and no branches.

**Finding by text** survives light/dark switches and system font-size changes better than a
template does, but it can only aim at things that have text. Templates can aim at icons and
graphics.

This feature needs the extra **Screen Recording** permission, and only asks when you actually
use it.

## Languages

The interface is available in **English** (the default), **Tiếng Việt**, **中文（简体）**,
**日本語** and **Español**. Pick one in the menu-bar popover, next to *Launch at login* — it
changes immediately, with no restart. Left alone it follows the system language.

> Only English and Vietnamese have been checked by a person. Chinese, Japanese and Spanish are
> machine-translated, and corrections are very welcome — a translation does not have to be complete
> to be useful, because anything missing falls back to English.

**Adding a language is adding one directory.** Copy `Resources/en.lproj/` to
`Resources/<code>.lproj/`, translate the values, and add the code to `Localization.supportedCodes`
and its name to `Localization.nativeName(of:)`. No other Swift changes, no build-script changes.

## Recording

Press `⌥⌘R` to start and end a recording session. Clickify watches your mouse and builds a
scenario from it: it recognises double clicks, long presses and drags, and folds a whole
trackpad scroll burst into a single step.

Two things are deliberate:

- **No keyboard capture.** Recording keys would force Clickify to request Input Monitoring and
  would turn it into a system-wide keylogger. Typing steps are added by hand after recording.
- **No cap on idle time.** If you wait 8 seconds, the recording waits 8 seconds. The recorder
  cannot tell "waiting for a page to load" from "gone to make coffee", so a cap would break
  exactly when it matters most.

If a whole recording session stays inside one application, Clickify locks onto that
application and anchors every step to its window — the recording still works after the window
has moved. Spanning several applications keeps absolute coordinates and says so plainly.

## Other features

- Choose the interval between clicks (10–3,600,000 ms).
- Choose the repeat count (1–1,000,000), per step and per scenario.
- Run a scenario until you press Stop.
- Click at the cursor position or at a saved fixed point.
- Pick a point directly on any display; press `Esc` to cancel.
- Lock clicking to one running application and verify which app owns the click point.
- Stop automatically if the destination app quits, so clicks never land on another app.
- A 3-second countdown to get the cursor into place.
- Quick stop from the menu-bar icon.
- A floating live activity while running, with a **Stop** button always visible.
- Global `⌥⌘S` shortcut to stop even while using another application.
- Remembers its configuration and can start at macOS login.
- Always releases the mouse button on stop, even mid long-press or mid-drag.
- Brings the locked application to the front before every typing step; stops if it cannot.
- Types a plain-ASCII string **key by key**, the way a person does, so games and
  filter-as-you-type fields see it correctly; strings with Vietnamese or emoji are sent as text
  instead. The step's panel says which of the two a given string will use.
- Finds a template even when it was cropped on a display of a different scale, so a template made
  on the built-in screen still works on an external monitor.

## Building and installing

Requires macOS 14 or later and the Xcode Command Line Tools.

```bash
./scripts/install.sh
# or:
sh ./scripts/install.sh
```

The script builds a release, quits the old version, installs into `/Applications`, checks the
signature and relaunches the app. To install a pre-built bundle, or not to launch the app:

```bash
./scripts/install.sh --no-build
./scripts/install.sh --no-launch
```

To build the bundle without installing it:

```bash
./scripts/build-app.sh
```

The first time you press **Start**, macOS asks for the Accessibility permission. Open:

`System Settings → Privacy & Security → Accessibility`

and enable it for **Clickify**. Scenarios that use image or text recognition need the extra
**Screen Recording** permission on the same screen — two separate permissions, and granting one
does not grant the other. Run the app from `/Applications` before enabling "Start with MacBook"
so macOS registers the right location.

A local build is ad-hoc signed by default, so macOS may ask you to re-enable the Accessibility
permission after an update. With a stable macOS certificate you can name it when building or
installing:

```bash
AUTO_CLICK_SIGNING_IDENTITY="Developer ID Application: Your Name (TEAMID)" ./scripts/install.sh
```

To restrict clicking, turn on **Only click in this application** and pick a running app. The
refresh button beside the list picks up apps opened since. Clickify brings that app to the
front, checks that the UI at the coordinates belongs to the chosen PID, and only then emits a
system-wide click. It stops on its own if the app quits or the click point falls outside it.

In **At the cursor** mode every click uses the cursor position at that moment. In **Fixed point**
mode the app always clicks the saved coordinates. After picking a new point the configuration
window reappears on its own.

## Documentation

| Document | Answers |
|---|---|
| [`../CONTEXT.md`](../CONTEXT.md) | What the shared concepts are called and what they mean |
| [`CONTEXT.md`](CONTEXT.md) | The terms that exist only on macOS — **Simple mode**, **Locked application**, **Anchor window**, **Input source** |
| [`../docs/adr/`](../docs/adr/) | Why this option was chosen over that one |
| [`../docs/sdd/`](../docs/sdd/) | What the system must do, precise enough to check |
| [`../docs/manual-e2e-tests.md`](../docs/manual-e2e-tests.md) | What can only be checked by hand, and how |

Every observable behaviour has a requirement identifier in `../docs/sdd/` (`DM-`, `EX-`, `ST-`,
`UI-`, `SF-`, `RC-`, `RG-`). The code cites those identifiers wherever the behaviour is not
obvious. Change a behaviour and you change the spec first.

## Development

```bash
swift test
swift run Clickify
```

Under `swift run`, clicking may require granting the permission to Terminal. The `.app` in
`/Applications` is the recommended way to run it.

`swift test` proves the logic; it does **not** prove the app clicks the right place on a real
screen. Accessibility and Screen Recording can only be granted to a signed bundle, never to
SwiftPM's test binary, so every seam that touches the operating system is replaced by a fake in
`TestSupport.swift`. The rest lives in [`../docs/manual-e2e-tests.md`](../docs/manual-e2e-tests.md) and
has to be run by hand.
