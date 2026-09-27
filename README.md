# Clickify

<p align="center">
  <b>English</b> | <a href="README.vi.md">Tiếng Việt</a> | <a href="README.zh-Hans.md">简体中文</a> | <a href="README.ja.md">日本語</a> | <a href="README.es.md">Español</a>
</p>

<p align="center">
  <b>English</b> | <a href="README.vi.md">Tiếng Việt</a> | <a href="README.zh-Hans.md">简体中文</a> | <a href="README.ja.md">日本語</a> | <a href="README.es.md">Español</a>
</p>

<p align="center">
  <img src="docs/assets/hero_showcase.jpg" alt="Clickify Cross-Platform Showcase" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/phanbaohuy96/clickify/actions"><img src="https://img.shields.io/badge/Platform-macOS%2014%2B%20%7C%20Android%2011%2B-000000?style=for-the-badge&logo=apple&logoColor=white" alt="Platform" /></a>
  <a href="macos/"><img src="https://img.shields.io/badge/macOS-Swift%20%2F%20SwiftUI-F05138?style=for-the-badge&logo=swift&logoColor=white" alt="macOS Swift" /></a>
  <a href="android/"><img src="https://img.shields.io/badge/Android-Kotlin%20%2F%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Android Kotlin" /></a>
  <a href="docs/sdd/08-permissions-and-safety.md"><img src="https://img.shields.io/badge/Touch%20Safety-SF--1%20Enforced-00C853?style=for-the-badge&logo=shield&logoColor=white" alt="SF-1 touch safety" /></a>
  <a href="docs/sdd/09-localisation.md"><img src="https://img.shields.io/badge/Languages-EN%20%7C%20VI%20%7C%20ZH%20%7C%20JA%20%7C%20ES-blue?style=for-the-badge" alt="Languages" /></a>
</p>

Automate repetitive work by emitting synthetic input: an ordered sequence of operations, each one an
**Action** paired with a **Target**, recorded or built by hand and replayed on demand.

**One product, two platforms.** The concepts are shared; almost nothing else is.

| Platform | Interface & status | What it is |
|---|---|---|
| [**`macos/`**](macos/README.md) | **Menu-bar app** (macOS 14+) · *Shipping* | Swift / SwiftUI, driven by ScreenCaptureKit and Apple Vision. Verified by hand on real hardware — [a manual end-to-end suite recorded result by result](docs/manual-e2e-tests.md). |
| [**`android/`**](android/README.md) | **Floating overlay service** (Android 11+) · *All four slices built* | Jetpack Compose overlay over an AccessibilityService. Run on an emulator only — **never yet on a physical device** ([testing](android/docs/testing.md)). |

---

## Why Clickify?

Most auto-clickers are either **dumb-but-buggy toys** (100M+ installs, and a touch-freeze bug that
makes you reboot the phone) or **steep power tools** (a scripting language to press one button).
The gap in the middle is where this stands. The evidence for that claim, app by app and complaint
by complaint, is in [`android/docs/landscape.md`](android/docs/landscape.md).

```
                      ┌──────────────────────────────────────────────┐
                      │                 Clickify                   │
                      │   Clean UI · Image matching · Crash-proof    │
                      │   Zero-freeze safety · Specification-first   │
                      └──────────────────────┬───────────────────────┘
                                             │
               ┌─────────────────────────────┴─────────────────────────────┐
               ▼                                                           ▼
┌──────────────────────────────┐                           ┌──────────────────────────────┐
│  Dumb Clickers (100M+ DLs)   │                           │     Scripting Power Tools    │
│  Stuck-touch freeze bugs     │                           │     Steep learning curve     │
│  No image/text recognition   │                           │     Complex code / tokens    │
│  Predatory weekly paywalls   │                           │     Heavy battery drain      │
└──────────────────────────────┘                           └──────────────────────────────┘
```

### 1. 🛡️ Zero-freeze touch safety (`SF-1` and "Free the touch")

The worst bug in this category is the **stuck-touch freeze**: touch the screen at the moment
synthetic input fires, the last interaction latches, and the phone stops responding to your finger
until it is rebooted. [XDA reports it as reproducible across *every* app tested](android/docs/landscape.md).

- **`SF-1`** — the runner releases every held button and every touch stroke on **any** exit:
  completion, Stop, cancellation, or error ([safety](docs/sdd/08-permissions-and-safety.md)).
- On Android, **Free the touch** is a one-tap recovery in both the run notification and a Quick
  Settings tile, so a latched stroke is unlatched without rebooting.

<p align="center">
  <img src="docs/assets/touch-safety.jpg" alt="A latched touch on the left, released on the right" width="78%" />
</p>
<p align="center"><em>Left: the latched touch this whole category ships. Right: released — <code>SF-1</code> lets go on every exit, and Free the touch rescues one that is already stuck.</em></p>

### 2. 👁️ Finding the target instead of trusting a coordinate

A fixed point fails the moment a window moves, a banner shifts, or the layout changes.

- **Template matching** — crop a picture straight off the screen and aim a **Step** at it. macOS
  matches at two scales so a **Template** survives the move between a Retina display and an
  external monitor ([ADR-0008](docs/adr/0008-match-templates-at-two-scales.md)). Android matches at
  one scale, on purpose: a **Scenario** there is bound to the screen it was built on
  ([ADR-0013](android/docs/adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md)), so
  there is no second scale to match.
- **Text (OCR)** — macOS aims at a word (*"Save"*, *"Claim"*, *"Submit"*) through Apple Vision.
  **Not built on Android**, and [`10-recognition.md`](android/docs/sdd/10-recognition.md) says why:
  the equivalent is ML Kit, and this app is not distributed through Play.
- **A visible fallback (`DM-16`)** — every search has a timeout you set, and you choose what happens
  when it expires: skip that **Step**, or stop the **Scenario**. Never a silent stall.

<p align="center">
  <img src="docs/assets/android-crop.png" alt="Cropping a Template straight off the screen on Android" width="31%" />
  &nbsp;
  <img src="docs/assets/android-step-find.png" alt="The Step that resulted, with its Template, threshold, wait and timeout choice" width="31%" />
</p>
<p align="center"><em>Android, on an emulator: crop what the Step looks for, then the Step that came out of it.</em></p>

### 3. 🧩 An orthogonal Action × Target model

A **Step** pairs exactly one **Action** with exactly one **Target** ([ADR-0002](docs/adr/0002-step-is-action-times-target.md)):

- **Actions** — macOS: click (single, double, triple, hold), scroll, move, drag, type a string,
  key shortcut. Android: tap, swipe, multi-touch, a global action, set text.
- **Targets** — macOS: the cursor, an absolute point, an offset from a window's nearest corner, an
  image **Template**, a piece of text. Android: a point, optionally moved by a **Template** search.
- **No branches** — a **Step** decides its own fate on timeout and never another **Step**'s
  ([ADR-0011](docs/adr/0011-a-scenario-has-no-branches.md)). There is no `if`/`else` to debug.

<p align="center">
  <img src="docs/assets/action-times-target.jpg" alt="Any Action paired with any Target" width="78%" />
</p>
<p align="center"><em>Action on the left, Target on the right — a Step is one of each, chosen independently.</em></p>

### 4. 📱 The Android overlay

- A movable floating control that gets out of the way of the app you are automating.
- Numbered **Marker**s you drag onto the points you mean, over the real application.
- Swipes with a duration you choose, and multi-touch gestures built by hand.
- Five interface languages that swap **live**, in the Activity and in the overlay at once, with no
  restart — because restarting would take the overlay off the screen mid-run
  ([11-localisation.md](android/docs/sdd/11-localisation.md)).

<p align="center">
  <img src="docs/assets/android-languages.png" alt="The list behind and the overlay panel in front, both in Vietnamese" width="31%" />
</p>
<p align="center"><em>One choice, both surfaces, no restart — the list behind and the overlay panel in front are Vietnamese at the same moment.</em></p>

### 5. 🎥 Recording what you actually did

- **macOS** — `⌥⌘R` starts and ends a recording, and it keeps your real timing rather than
  flattening it to a constant ([ADR-0004](docs/adr/0004-recordings-keep-real-timing.md)). It infers
  window-relative coordinates when a session stays inside one application.
- **Android** — a recording layer takes each touch, records it, and hands it back to the app
  underneath, so you record by using the app normally ([08-recording.md](android/docs/sdd/08-recording.md)).
- **Mouse and touch only, never the keyboard.** Deliberate: a recorder that watched the keyboard
  would be a keylogger ([ADR-0003](docs/adr/0003-no-keyboard-capture-when-recording.md)).

---

## Market comparison

Sourced from the survey in [`android/docs/landscape.md`](android/docs/landscape.md), which was done
before the Android specification was written.

| Criterion | Traditional auto-clickers *(True Developers, etc.)* | Macrorify | Klick'r / Smart AutoClicker | **Clickify** |
|---|---|---|---|---|
| **Touch-freeze recovery** | ❌ Category-wide bug; the fix is a reboot | ⚠️ Partial | ⚠️ Not addressed as such | ✅ **`SF-1`, plus one-tap "Free the touch"** |
| **Image detection** | ❌ None | ✅ Template + OCR | ✅ Image triggers | ✅ **Template on both platforms; OCR on macOS** |
| **Learning curve** | Low, because it does little | High — visual logic, or a scripting language | High; named as its main criticism | **Low to medium, visual** |
| **Cross-platform** | ❌ Android only | ❌ Android only | ❌ Android only | ✅ **macOS and Android, both native** |
| **Specification** | — | — | — | ✅ **Every behaviour is a numbered requirement cited in the code** |

---

## Platform capabilities

| Capability | macOS (`macos/`) | Android (`android/`) |
|---|:---:|:---:|
| **Runtime** | Menu-bar popover + ScreenCaptureKit | Foreground service + AccessibilityService overlay |
| **Actions** | Click, scroll, move, drag, type, key shortcut | Tap, swipe, multi-touch, global action, set text |
| **Target: cursor / fixed point** | ✅ Both | ✅ Fixed point, placed with **Marker**s |
| **Target: window-relative offset** | ✅ Nearest-corner tracking | N/A — no windows to anchor to |
| **Target: image Template** | ✅ Two-scale pyramid matcher | ✅ Single scale ([ADR-0013](android/docs/adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md)) |
| **Target: OCR text** | ✅ Apple Vision | ❌ Not built — ML Kit, and no Play distribution |
| **Recording** | ✅ Mouse events, real timing | ✅ Touch pass-through; multi-touch not recorded |
| **Interface languages** | ✅ 5, swapped live | ✅ 5, swapped live in both surfaces |
| **Verified on real hardware** | ✅ [manual e2e suite](docs/manual-e2e-tests.md) | ❌ Emulator only ([testing](android/docs/testing.md)) |

---

## Where this is going

**None of this is built yet.** It is the intended direction, recorded here so the shape of the
project is legible; nothing below exists in this repository today.

- **The core stays free.** Unlimited clicking, multi-target gestures, recording, and `SF-1` touch
  safety — the things the category charges for, and the things it breaks.
- **A one-off upgrade rather than a subscription**, which is the single thing users of this category
  praise most loudly when they get it ([landscape](android/docs/landscape.md)).
- **Candidate paid features**: anti-detection jitter, Bézier swipe paths, a wall-clock scheduler, a
  colour guard, and unlimited **Scenario** slots.

---

## Quick start

### macOS (14 Sonoma or later)

Requires the Xcode command-line tools.

```bash
git clone https://github.com/phanbaohuy96/clickify.git
cd clickify/macos

swift test              # unit and specification suites
./scripts/install.sh    # build and install into /Applications
```

> **First run**: grant **Accessibility** in `System Settings → Privacy & Security → Accessibility`,
> and **Screen Recording** as well if you use a **Template** or OCR.
> **Emergency stop**: `⌥⌘S` halts everything and releases every held button, at any time.

### Android (11+)

```bash
cd clickify/android

./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
```

> **First run**: onboarding walks you through the overlay permission and then into Android's own
> accessibility settings. On Android 13+ the service is restricted until you allow it explicitly —
> [`02-permissions-and-onboarding.md`](android/docs/sdd/02-permissions-and-onboarding.md) explains
> why that wall is walked into on purpose rather than around.

---

## Documentation

The specification comes **before** the code on both platforms. Every observable behaviour is a
numbered requirement in an `sdd/` document, cited from the source that implements it.

| Document | Answers |
|---|---|
| [`CONTEXT-MAP.md`](CONTEXT-MAP.md) | Which glossary covers which platform |
| [`CONTEXT.md`](CONTEXT.md) | The shared language — what the concepts are called and what they mean |
| [`docs/adr/`](docs/adr/) | Why this option and not that one — `0001`–`0011` shared and macOS, `0012`+ Android |
| [`docs/sdd/`](docs/sdd/) | The purpose the product answers to, and the macOS specification |
| [`android/docs/`](android/docs/) | The Android glossary, specification, decisions, market survey and test plan |
| [`docs/manual-e2e-tests.md`](docs/manual-e2e-tests.md) | What was tried by hand on real hardware, and what it showed |

**Paths.** Commands in [`macos/README.md`](macos/README.md) run from `macos/`. Paths named inside
`docs/sdd/` and `docs/adr/` (`Sources/`, `Resources/`, `Package.swift`, `scripts/`) are relative to
`macos/` too; they were written when that was the repository root.

---

## Interface languages

Five, on both platforms, switched live with no relaunch:

🇬🇧 **English** · 🇻🇳 **Tiếng Việt** · 🇨🇳 **中文（简体）** · 🇯🇵 **日本語** · 🇪🇸 **Español**

English and Vietnamese have been read by a person. The other three are machine translations, and
anything missing falls back to English.

---

## License

Released under the [MIT licence](LICENSE).
