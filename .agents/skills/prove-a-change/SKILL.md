---
name: prove-a-change
description: Decide what evidence a Clickify change needs, get it, and write it down honestly — tier 1 checks, the Android tier 2 emulator suite, macOS manual e2e sessions, and the traps in each rig that produce a plausible pass for the wrong reason. Use before saying a change works, before filling in a pull request, and whenever driving the real app on an emulator or a screen.
---

# Prove a change

Law 2 in `AGENTS.md`: a claim is measured or it is not made. This skill picks the measurement.

## 1. Pick the tier from what the change touches

| The change touches | Required | Where the procedure lives |
|---|---|---|
| anything under `android/` | `cd android && make check` | `android/Makefile` |
| the accessibility service, `ScenarioRunner`, gesture dispatch, the **Overlay**'s windows, a **Screen profile** | also `cd android && make tier2`, output pasted into the PR | `android/docs/testing.md`, then `references/android.md` |
| anything under `macos/` | `cd macos && swift build && swift test --no-parallel` | `.github/workflows/macos.yml` |
| macOS behaviour that needs TCC, a real screen or real input | the matching case in `docs/manual-e2e-tests.md`, run by hand | that file, then `references/macos.md` |
| a screenshot for `docs/assets/` | the rig for that platform | `references/android.md` or `references/macos.md` |

Only open the reference file for the platform you are actually driving.

## 2. Run it, and read the numbers

- Tier 2 prints its own measurements under the `Tier2` logcat tag and in the Gradle output. Paste
  them; a ceiling nobody can see the distance to is a ceiling nobody can tighten.
- A new assertion is not evidence until it has been seen to **fail** for the right reason. Break the
  code once (the way `GX-10` was checked: one `true` added to `StrokeDescription` turned three assertions red), watch it go red, revert.
- If a result looks like a product defect, reproduce it offline first (`swift test`, a JVM test, the
  matcher on the saved images). More than one "failure" on this project was the rig, not the app.

## 3. Write the evidence down

- PR: fill every section of `.github/pull_request_template.md`. *Tier 2* either has pasted output or
  a reason it cannot affect a dispatched gesture.
- *What this does not claim* is never empty. No physical Android device is in use on this project,
  so a latched touch, vendor power optimisers and real touch latency are always unverified.
- A manual e2e case gets its result written into its row in `docs/manual-e2e-tests.md`, with the
  measurement, not "works". "Invalid, not a failure" is a legitimate result when the rig was wrong.
- `[done]` in an SDD file means code and tests exist. Seeing it on a device is recorded in the
  testing documents, not by changing the label.

## Red flags — stop and re-check

- The screenshot or dump shows the launcher when you expected the Overlay.
- The app shows onboarding in the middle of a session.
- A click landed "somewhere" and the scenario "failed" — was the Start button ever pressed?
- Every test passed in a run that should have taken a minute and took two seconds.
