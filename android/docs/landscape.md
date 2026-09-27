# Landscape — what Android auto-clickers already do, and where they hurt

Surveyed 2026-09-21, before writing the Android specification. This is **evidence**, not a plan.
Nothing here is a requirement until it appears in `android/docs/sdd/`.

The point of the survey is not to copy a feature list. It is that this category is ~15 years old,
has apps with 100M+ installs, and **still has a bug that forces users to reboot the phone**. The
open ground is not features. It is reliability and comprehensibility.

## Who is in the market

| App | Scale | Shape | Detection |
|---|---|---|---|
| **Auto Clicker — Automatic tap** (True Developers Studio) | 4.5★, ~831K reviews, 100M+ installs | Floating panel, single- and multi-target, swipe and long press | None |
| **Macrorify** (kok_emm) | The power tool of the category | **Three tiers**: Basic (drag actions) → Abstract (visual `if`/loops/variables) → EMScript (a real scripting language) | Image template matching + OCR text, dynamic scaling, grayscale, score tuning |
| **Klick'r / Smart AutoClicker** (open source, Kotlin, on F-Droid) | The open-source reference | Scenarios of events; triggers from images, timers, counters, broadcast receivers; Android Intents as actions | Image detection, counters, flow control |
| **Auto Clicker — Image Detect**, **Detect Clicker**, **Image Clicker** | Niche | Single/multi target with image gating | Image only |

Two clusters, and almost nothing between them: **dumb-but-easy** (True Developers) and
**powerful-but-steep** (Macrorify, Klick'r). The gap in the middle is the interesting place to stand.

## What users reward

- **No root.** Universal. Every serious app is Accessibility-Service based and says so in the first line.
- **A floating control that starts and stops without leaving the app.** Non-negotiable; every app has one.
- **Multi-target.** Several points in one run, not one point repeated.
- **Gestures, not just taps** — swipe and long press are expected, not advanced.
- **One-time purchase.** Klick'r is praised specifically for this, against a category full of subscriptions.
- **Image detection** is what separates a toy from a tool, in users' own framing.

## What users punish

Ranked by how badly it breaks trust, not by how often it is mentioned.

1. **The stuck-touch freeze.** If the user touches the screen at the same moment the clicker fires, the
   service latches the last interaction and **stops being able to touch the screen at all** until the
   phone is rebooted. Reported on XDA as reproducible across *every* app tested — Auto Clicker, Auto
   Clicker Pro, GC Auto Clicker, AG Auto Clicker, Click Assistant. A whole category ships this.
2. **Freezing when a notification arrives** mid-run; the clicker has to be restarted.
3. **Keeping the device awake even when idle**, draining the battery while doing nothing.
4. **Dying silently** after a vendor power optimiser (Samsung One UI sleep) decides the service is idle.
5. **Detection that quietly stops working** and is fixed by nudging an unrelated setting — i.e. the
   user has no idea what the detector is doing and no way to see it.
6. **Image matching that fails in dark mode** — contrast too low against a template cropped in light mode.
7. **Controls that sit on top of what you are automating** and cannot be made small or moved away.
8. **The permanent "accessibility service is running" notification**, back after every reboot.
9. **Steep learning curve and thin documentation** — named as the *main* feedback on Klick'r, and the
   one caveat in otherwise glowing Macrorify reviews.
10. **Ads and paywalls on the core function**, and pro purchases that fail to unlock.
11. **Being blocked by the automated app.** Klick'r's own README answers this with "try the obfuscated
    version", which says plainly that this is an arms race it is losing.

## Where Clickify already stands ahead

Not aspiration — these exist and are specified today on macOS.

- **`SF-1`: release every held button on stop, for any reason — completion, Stop, error, cancellation.**
  This is, exactly, the principle that pain point 1 is the absence of. The category's worst bug is a
  requirement we already wrote down on the other platform.
- **`DM-16` `onTimeout`: `skipStep` or `stopScenario`, chosen per Step.** Most apps have one hidden
  behaviour for "not found". Ours is a visible decision, and it is what turns detection from a locator
  into a guard.
- **`RG-23`: the default search region hugs where the template was cropped.** Faster and far less prone
  to matching the wrong one of five identical item slots.
- **[ADR-0008] two-scale matching** — a template survives a change of display scale.
- **Specification-first with numbered requirements**, and five interface languages already shipped.
  Against pain point 9, this is the largest structural advantage in the list.
- **A domain that deliberately refuses control flow**, which is the other half of pain point 9.

## Where Clickify is behind, or absent

- No Android build at all.
- No swipe with a configurable duration (macOS `drag` is close but is a mouse drag).
- No multi-touch; Android supports up to 10 simultaneous strokes and games use them.
- No awareness of a device going to sleep, of a vendor power optimiser, or of an incoming notification.
- No dark-mode or theme-change strategy for template matching.
- Nothing to say about being detected and blocked by the automated application.

## Answers already settled against these findings

Kept here, next to the evidence, until the Android specification exists to number them.

- **Pain 1 — the stuck-touch freeze.** A stroke with `willContinue` is a finger held down; it lifts
  only when the terminating stroke is sent. Clickify **must** send it on every exit — completion,
  Stop, error, cancellation, service disconnect. That is `SF-1` rewritten in Android's vocabulary,
  and it is the prevention half.
  The recovery half: a **"free the touch"** action, always reachable from the persistent notification
  and a Quick Settings tile, which force-terminates every stroke and cycles the accessibility
  service. The category's worst failure becomes one tap instead of a reboot.
  **Unverified**: that cycling the service actually clears a latched touch. This must be run on real
  hardware and written into the manual tests before it is promised to anyone. If it does not clear,
  the action degrades to a notification explaining the reboot, and most of the value is gone.
- **Rejected: blocking touches during a run.** A full-screen **Overlay** swallowing the user's
  touches while a **Scenario** runs would make the collision structurally impossible. Rejected as a
  default because it takes the device away from the user for the length of the run, and because a
  **hung** process leaves that **Overlay** in place swallowing everything — building the very bug it
  set out to prevent. A dead process is self-healing, since its windows die with it; a hung one is
  not. It stays available as a per-Scenario option once a watchdog exists that has proven itself.

## Tensions with the current specification

Recorded because they are decisions, not oversights.

- **Control flow.** `01-scope.md` forbids `if`, branches and conditional loops inside a **Scenario**.
  Macrorify's power is exactly those things, and Klick'r has them too. Either the refusal is a
  differentiator (simplicity, in a category whose main complaint is complexity) or it is a ceiling.
  It cannot be both, and the answer decides how far the Android app can go.
- **Recording.** Possible on Android only by swallowing each touch in a full-screen **Overlay** and
  re-emitting it as a **Gesture**. Macrorify does this, marked by a green border. It means the
  application underneath is driven by synthetic touches *while being recorded*.
- **`recognitionRetryFloorMilliseconds = 150`** exists to save CPU (`EX-8`). Klick'r solves the same
  problem with a user-facing **execution rate limiter**, which makes the trade-off the user's instead
  of the specification's.

## Sources

- <https://play.google.com/store/apps/details?id=com.kok_emm.mobile> · <https://www.kok-emm.com/docs>
- <https://github.com/Nain57/Smart-AutoClicker> · <https://f-droid.org/en/packages/com.buzbuz.smartautoclicker/>
- <https://play.google.com/store/apps/details?id=com.truedevelopersstudio.automatictap.autoclicker>
- <https://xdaforums.com/t/issues-with-accessibility-apps.4718020/>
- <https://appgrooves.com/android/com.truedevelopersstudio.automatictap.autoclicker/auto-clicker-automatic-tap/true-developers-studio>
