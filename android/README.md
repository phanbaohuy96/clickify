# Clickify for Android

The Android member of Clickify. See the [repository README](../README.md) for the product, and
[`../CONTEXT.md`](../CONTEXT.md) for the shared language.

**Status: all four slices are built.** The app sets itself up, lists **Scenario**s, steps out of the way
so you can author over the application you actually want to automate, drags its **Marker**s without
taking the screen away from you, edits a **Step** and a **Scenario** in the panel, records a
session of real touches, finds a cropped **Template** on the screen before it presses, and runs
through the accessibility service in five languages.

It has been run on two emulators (Pixel 10 Pro XL and a 1080 × 2400 Medium Phone, both API 37)
and **never on a physical device**;
[`docs/testing.md`](docs/testing.md) records exactly what that has and has not shown.

The assertions that matter most no longer depend on somebody remembering to look: `make tier2` runs
eight instrumented tests against the **real** accessibility service on an emulator — Stop mid-stroke,
cancellation, fifteen **Step**s in order, the pixel a **Marker** names — in half a minute. They were
checked by breaking the code they guard. They are not in CI — the
[pull-request template](../.github/pull_request_template.md) asks for their output instead, and
`android-tier2.yml` is there for a runner when a change deserves one.

| Built | Not built, and why |
|---|---|
| **Scenario / Step / Action × Target**, limits, validation ([03](docs/sdd/03-scenario-model.md)) | Recording a `multiTouch` — two fingers through a layer that swallows and re-emits would not resemble what was done ([08](docs/sdd/08-recording.md)) |
| Recording, with pass-through **and** a silent mode, keeping real timing ([08](docs/sdd/08-recording.md)) | Finding by **text** — the Android equivalent of Vision is ML Kit, and this app is not distributed through Play ([10](docs/sdd/10-recognition.md)) |
| One directory per **Scenario** on disk, and a `DataStore` for what is not one ([04](docs/sdd/04-storage.md)) | Matching a **Template** at a second scale — [ADR-0013] already refuses another screen, so there is no second scale to match ([10](docs/sdd/10-recognition.md)) |
| The runner, and every exit path releasing ([05](docs/sdd/05-gesture-execution.md)) | Importing a **Template** from the gallery — worth having, and it needs a photo picker ([10](docs/sdd/10-recognition.md)) |
| The **Overlay**, **Marker**s, the movable control, both faces of the panel ([06](docs/sdd/06-overlay-and-markers.md)) | More than one **Guard** per **Step** — a conjunction is most of the way to a branch ([ADR-0011]) |
| Onboarding, including the Android 13 wall ([02](docs/sdd/02-permissions-and-onboarding.md)) | A blurred control — the platform blur blocks every other window's touches ([07](docs/sdd/07-design-system.md)) |
| Aiming a **Step** at the screen instead of dropping it in the middle ([09](docs/sdd/09-picking.md)) | **Anything a console finger cannot stand in for** — real touch latency, vendor power optimisers, a latched touch ([testing](docs/testing.md)) |
| The panel as a bottom sheet: drag to expand, then scroll ([06](docs/sdd/06-overlay-and-markers.md)) | **Anything at all on physical hardware** ([testing](docs/testing.md)) |
| A way back from a **Step** to its **Scenario**, and one question before either exit ([06](docs/sdd/06-overlay-and-markers.md)) | |
| Tier 2: eight instrumented assertions on the real service, and a target app that records what arrived ([testing](docs/testing.md)) | |
| Landscape: the panel as a side sheet, and the windows put back after a screen change ([06](docs/sdd/06-overlay-and-markers.md)) | |
| A **Scenario**'s orientation, auto-detected, and rebuilding it for another screen ([03](docs/sdd/03-scenario-model.md)) | |
| **Recognition**: crop a **Template**, aim a **Step** at it, guard a **Step** on it ([10](docs/sdd/10-recognition.md)) | |
| Trying one **Step** on its own, and renaming or duplicating a **Scenario** | |
| **Five interface languages**, swapped live in both surfaces ([11](docs/sdd/11-localisation.md)) | |

[ADR-0011]: ../docs/adr/0011-a-scenario-has-no-branches.md
[ADR-0013]: docs/adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md

## Where to read what

| Document | Answers |
|---|---|
| [`CONTEXT.md`](CONTEXT.md) | The terms that exist only on Android — **Overlay**, **Marker**, **Gesture**, **Screen profile** |
| [`docs/sdd/`](docs/sdd/README.md) | The specification, and the requirement-identifier convention |
| [`docs/sdd/01-scope.md`](docs/sdd/01-scope.md) | The four slices, and what is deliberately out of scope |
| [`docs/sdd/02-permissions-and-onboarding.md`](docs/sdd/02-permissions-and-onboarding.md) | Why onboarding has to walk the user into a wall on purpose |
| [`docs/sdd/03-scenario-model.md`](docs/sdd/03-scenario-model.md) | The five **Action**s, the one **Target**, and which limits Android owns |
| [`docs/sdd/04-storage.md`](docs/sdd/04-storage.md) | What `scenario.json` looks like, and what happens when it is wrong |
| [`docs/sdd/05-gesture-execution.md`](docs/sdd/05-gesture-execution.md) | What happens when the user presses Stop, and why that is most of the code |
| [`docs/sdd/06-overlay-and-markers.md`](docs/sdd/06-overlay-and-markers.md) | Why a Marker is a window of its own, and why Stop is re-attached last |
| [`docs/sdd/07-design-system.md`](docs/sdd/07-design-system.md) | Why the Overlay is dark at every hour of the day, and why it has no blur |
| [`docs/sdd/09-picking.md`](docs/sdd/09-picking.md) | Why aiming discards the timing that recording keeps |
| [`docs/sdd/08-recording.md`](docs/sdd/08-recording.md) | Why recording has to swallow a touch and then hand it back |
| [`docs/sdd/10-recognition.md`](docs/sdd/10-recognition.md) | Why a **Template** moves the whole **Step**, and why there is no second scale |
| [`docs/sdd/11-localisation.md`](docs/sdd/11-localisation.md) | Why the language changes with no restart, and what that costs |
| [`docs/adr/`](docs/adr/) | Android decisions, numbered from 0012 in the product-wide sequence |
| [`docs/landscape.md`](docs/landscape.md) | What the competing apps do, what users punish them for, and our answers |
| [`docs/testing.md`](docs/testing.md) | Three tiers, and what is honestly not verified yet |

## Commands

Every target is in the [`Makefile`](Makefile), and `make check` is exactly what CI runs.

| Command | What it does |
|---|---|
| `make check` | Tier 1 in one invocation: assemble, JVM tests, detekt, Spotless, coverage |
| `make test` | The JVM tests alone |
| `make lint` / `make format` | detekt and Spotless, checking or fixing |
| `make tier2` | Tier 2 on a connected emulator or device — see [`docs/testing.md`](docs/testing.md) |

## What this project is built on, and what it dropped

Started from `android-base-structure`: Kotlin, Compose, Material 3, MVVM with unidirectional data
flow, Hilt, Navigation Compose, and the Spotless / ktlint / detekt / Kover toolchain.

Removed on purpose, so the absences are not read as oversights:

- **Room** — a **Scenario** is a directory of files, [ADR-0014](docs/adr/0014-no-database.md).
- **Retrofit, OkHttp, the `INTERNET` permission and the network security config** — Clickify talks
  to no server at all.
- **The `dev`/`staging`/`prod` flavours** — they existed to point at three base URLs.
- **The reference login and item features**, and the network- and auth-shaped `DomainError`
  taxonomy that came with them. The failure cases this app really has are defined with slice A1.
- **`BaseScreen` for the main surface.** It assumes an Activity, and most of this app's interface
  lives in the **Overlay**, which is not one — [ADR-0015](docs/adr/0015-compose-in-the-overlay.md).
  `BaseScreen` stays for the Activity screens; `BaseOverlayScreen` joins it in `:core` at A1.
