# Testing — three tiers, and an honest gap

macOS cannot test the thing it exists to do. `README.md` says why: *"Accessibility and Screen
Recording can only be granted to a signed bundle, never to SwiftPM's test binary, so every seam that
touches the operating system is replaced by a fake."* Hence 58 KB of
[`manual-e2e-tests.md`](../../docs/manual-e2e-tests.md).

Android is not stuck that way, and the difference is worth spending.

## Tier 1 — JVM unit tests, no device

Everything in `:domain`, which is pure Kotlin: the **Scenario** model, clamping and repair on load,
the runner's ordering and timing, **Guard** evaluation, **Screen profile** comparison, and template
matching. `TemplateMatcher` and `GrayImage` are arithmetic over pixel arrays — ported to Kotlin they
are testable against fixture bitmaps with no emulator at all, including the two-scale behaviour of
[ADR-0008](../../docs/adr/0008-match-templates-at-two-scales.md).

## Tier 2 — Instrumented tests on an emulator, against a target app

The tier macOS cannot have, and it exists: `app/src/androidTest/kotlin/com/pbh/clickify/tier2/`, run
by `tools/testing/tier2.sh` or `make tier2`. Eight assertions, half a minute on an emulator, driving
the **real** `ClickifyAccessibilityService` — the fake stops at the tier 1 boundary — against a
window that writes down what arrived.

Three pieces:

- **`Tier2`** grants what the app may not grant itself, then waits for the service to bind.
- **`TouchLogActivity`** is the target app `docs/testing.md` asked for, reduced to what is actually
  needed: a full-screen view recording every `MotionEvent` as **screen** pixels, with the time it
  arrived and the device it came from. It lives in `src/debug`, for a reason given below.
- **`StrokeReleaseTest`** and **`SequenceFidelityTest`** hold the assertions.

On an emulator the permissions are scriptable, and this is the order — the suite does it itself, in
`Tier2.grantTheService`:

- `appops set … ACCESS_RESTRICTED_SETTINGS allow` — **first**, or the next line is silently
  reverted. A sideloaded build is behind the restricted-settings wall (`PM-1`…`PM-11`), and the
  platform does not report the refusal: `settings put` appears to succeed and `settings get` returns
  `null`.
- `settings put secure enabled_accessibility_services …` enables the service
- `settings put secure accessibility_enabled 1`
- `appops set … SYSTEM_ALERT_WINDOW allow` grants the **Overlay**

### What it asserts, and what it measured

Measured on `AutoClick_Medium_Phone`, API 37, 1080 × 2400. The run prints every number itself, under
the `Tier2` logcat tag, because a ceiling nobody can see the distance to is a ceiling nobody can
tighten.

| Assertion | Rule | What the machine did |
|---|---|---|
| A hold arrives and is let go | `GX-13`, `SF-1` | a 600ms hold was on the screen for **600ms** |
| Stop lets the stroke in flight finish, and starts no other | [`GX-8`] | Stop asked for 500ms into a 2500ms stroke; the contact ended **2500ms** after it began; **Step 2 never started** |
| Cancelling the run leaves no finger on the screen | `GX-9`, `GX-10` | cancelled 500ms in; the contact still ended by itself, **2500ms** after it began |
| A tap lands on the pixel its **Marker** names | `SM-11`, [ADR-0013] | aimed at `(540, 1200)`, arrived at `(540, 1200)`, from device `-1` |
| Fifteen **Step**s arrive in order, at their own pixels, at speed | `GX-1`, `GX-5`, and A1's acceptance clause | **212ms** end to end; gaps of **12–16ms** between contacts |
| A **Step** repeats in place | `GX-4` | three contacts, same pixel |
| A swipe starts at its **Target** and ends at its destination | `GX-14` | 500ms, 31 events, ending exactly on the destination |
| A **Screen profile** mismatch touches nothing | `GX-2`, `SM-15` | no events at all |

The ceilings come from those numbers rather than from caution: 60ms per gap against 16 measured, two
seconds for the whole sequence against 212ms, 150ms of slack on a duration the platform reports to
the millisecond. Loose enough for a slower machine, tight enough that a **Step** which started
*waiting* would fail.

**And on a GitHub runner, which is the better test of all of that.** API 35, and the AVD
`avdmanager` makes by default is **320 × 640** — a tenth of the pixels this was written against. The
suite passed unchanged, first attempt, because a test asks the target app where its window is instead
of naming a pixel: the tap aimed at `(160, 320)` and arrived at `(160, 320)`. The gaps came out at
14–24ms against the 60ms ceiling, the sequence at 258ms against 2000ms, and every duration was the
same to the millisecond. A suite that had hard-coded this emulator's centre would have failed there
for a reason that has nothing to do with the app.

`GX-8` is the one worth reading the assertion for. A stroke in flight is **completed, not
abandoned**, so what is asserted is that the contact lasts no longer than that stroke's own duration
and that no further **Step** begins. An assertion that the finger lifts *immediately* would fail
against correct behaviour.

### The assertion that matters was checked by breaking the code

A passing test proves nothing until it has been seen to fail for the right reason. `GX-10` forbids
dispatching a stroke with `willContinue`, because a continuing stroke that is never continued is the
stuck finger this whole app is arranged around (`landscape.md`, pain 1) — so that is the mutation: one `true` added to
`StrokeDescription`, and three of the release assertions went red, the first of them with

> `1 contact(s) started and only 0 ended — a finger was left on the screen`

which is `SF-1` caught in the act. The mutation is reverted; what it bought is the knowledge that
these three tests are load-bearing rather than decorative.

It also reproduced the **bug** and not the **freeze**: with the unfinished stroke's window gone,
`dumpsys input` showed no pointer still down. The latched touch a user has to reboot out of is still
a tier 3 item, and nothing here has moved it.

### Four traps, and every one of them is the harness breaking what it measures

1. **Asking for a `UiAutomation` disables the service under test.** This is the expensive one. The
   suite needs shell to enable the accessibility service, and `UiAutomation.executeShellCommand` is
   how instrumentation reaches shell — but a registered `UiAutomation` *is* an accessibility service,
   and the platform gives it accessibility exclusively. The log says it plainly,
   `UiAutomationManager: Registering UiTestAutomationService`, and from that moment
   `dumpsys accessibility` reports `Bound services:{}` while still listing ours under
   `Enabled services`. There is no error: eight tests simply wait twenty seconds each for a service
   the system has been told to hold back. `Instrumentation.getUiAutomation(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)`
   is the whole difference, and the flag belongs to the registration, so it has to be on the **first**
   call.
2. **A grant made before Gradle runs is gone by the time the tests do.** `connectedAndroidTest`
   uninstalls the application before installing the two APKs, and a component that is briefly not
   installed is dropped from `enabled_accessibility_services` by the system. A script that grants and
   then calls Gradle — the obvious shape, and the first one tried — leaves `settings get` answering
   `null`. That is why the grant lives in the suite and the script only prepares the device.
3. **The target app has to be in the application's own process.** `ActivityScenario` refuses an
   Activity that resolves elsewhere — *"Intent in process com.pbh.clickify resolved to different
   process com.pbh.clickify.test"* — and the assertions read the recorded touches out of memory,
   which only works in one process anyway. So `TouchLogActivity` lives in `src/debug`: absent from
   every build that ships, not exported, and with no intent filter, so nothing but the tests can
   start it.
4. **Never drive the interface with `adb shell input` while a gesture is in flight.** It does reach
   the Overlay; this document once claimed it did not, and the claim was a misreading of the symptom.
   `input` and `dispatchGesture` inject from the *same* virtual device — `dumpsys input` shows both as
   `DeviceId(-1)` — so a second injection takes the first one's pointer away. A tap anywhere on the
   screen, not only on the control, ends the stroke in flight: the
   `touchingPointers=[Pointer(id=0, UNKNOWN)]` entry simply disappears. A harness that presses Stop
   this way destroys the very stroke it is trying to prove was released, and then reports a pass.

   The instrument that works is the emulator console. **`adb emu event mouse <x> <y> 0 <1|0>`**
   arrives as `DeviceId(3)`, `Pointer(id=0, FINGER)` — a real touchscreen device, indistinguishable
   from a finger, and it **coexists** with a gesture in flight: with a stroke running and a console
   finger held down, `dumpsys input` lists `DeviceId(-1)` and `DeviceId(3)` at the same moment, and
   the stroke carries on after the finger lifts. That is also the answer to a question worth asking
   out loud — *does a user touching the screen cut the run short?* — and the answer is no.
   Its coordinates are in the panel's own portrait space (`1080 × 2400` on this AVD) whatever the
   display is rotated to, so a landscape test either converts (`px = 1079 - ly`, `py = lx` at
   `ROTATION_90`) or runs in portrait and avoids the conversion. A point outside the portrait range
   silently touches nothing.

Two facts about the device that are not traps, just useful:

- **`am force-stop` clears `enabled_accessibility_services`; `adb install -r` does not.** After a
  force-stop the key reads back `null` and `accessibility_enabled` becomes `0`. A reinstall was
  claimed here to do the same and, measured again on API 37, does not: the key survives
  `install -r` untouched. What *does* clear it is an uninstall, which is trap 2.
- **A stroke in flight is observable without a target app.** `dumpsys input` names every window a
  pointer is touching, so *is a finger down* is one shell command and a `grep -c`. Useful while
  driving the shipped interface by hand, where there is no `TouchLogActivity` in front.

### What tier 2 still does not cover

- **The shipped interface.** The suite drives `ScenarioRunner` and the service directly; nobody
  presses Play. The floating control, the panel, the run notification and the **Quick Settings tile**
  are still only ever driven by hand — and when they are, trap 4 says which instrument to use.
- **Recording and recognition.** Nothing in tier 2 records a touch or crops a **Template**; `RD-*`
  and the crop-and-find half of `TP-*` are still hand-driven, with what was seen recorded below.
- **CI, on purpose.** `.github/workflows/android-tier2.yml` works — it was run on a GitHub runner
  and passed, and the paragraph above is the evidence — and it is **dispatch only** anyway. Six
  minutes of emulator boot on every pull request touching `android/` is the price, and the `on:`
  block that charges it is commented out rather than deleted, because that trade can change.

  What carries the obligation instead is `.github/pull_request_template.md`, which asks for the
  output of `make tier2` from the machine the change was written on, for anything touching the
  service, the runner, gesture dispatch, the **Overlay**'s windows or a **Screen profile**. Weaker
  than a green check, much stronger than nothing, and it is the reason the harness prints its own
  measurements.


## Tier 3 — By hand, on real hardware

Reserved for what an emulator misrepresents. Declared rather than skipped, because a gap that is
written down can be planned around and a gap that is not gets mistaken for coverage.

### What the emulator has actually shown (Pixel 10 Pro XL, API 37)

Run by hand rather than by a harness, so it is a record and not yet a regression test. Where a
claim is about a coordinate it was read from `dumpsys window windows` rather than from a screenshot,
because the bug in `OV-31` is exactly the size of a status bar and the eye does not measure that.

- The **Overlay** draws over another application, and the application underneath keeps working.
- A `tap` **Step** with a 20-second hold makes the launcher open its **long-press menu** — a real,
  sustained touch delivered to somebody else's window.
- The countdown, the run, and `FinishReason` reaching the control (`OV-16`, `OV-17`).
- **Marker**s are not drawn while running (`OV-11`).
- Stop from the run notification works **mid-stroke** (`OV-15`).
- The **Step** panel takes focus and opens a keyboard for `setText`, and drops focus again
  (`OV-20`).
- `SM-14`'s "forgets its profile" clause, end to end: changing the last **Step** to `setText`
  leaves `"screenProfile": null` in `scenario.json`.
- `OV-26`: creating a **Scenario** leaves the Activity and drops the control on the launcher.
- `OV-27`, the one that could not have been reasoned about: with a **Marker** on screen and
  draggable, a tap next to it **opened Chrome from the launcher**. Touch reaches the application
  underneath and the handle at the same time, with no mode in between.
- `OV-14`: the control drags by its handle, snaps to the nearer side, survives a restart, and
  re-hugs its side when a run changes its width.
- `OV-31`, by `dumpsys` rather than by eye: a **Marker** handle's window frame is
  `[606,1430][738,1562]`, whose centre is exactly the **Step**'s point `(672, 1496)`. Before the
  fix it was `[606,1589][738,1721]` — centred on `(672, 1655)`, 159 pixels below the point the
  **Gesture** actually lands on. **This was wrong before this work and had not been noticed**: the
  full-screen **Marker** layer was laid out inside the system bars too.
- `OV-13`: with the panel open, the control's frame ends at `y=1846` and the panel's begins at
  `y=1846`. They no longer overlap, and the control is re-attached last so nothing is drawn over
  Stop.
- `OV-25`, the bug that started this round: the run notification no longer offers Stop when there
  is nothing to stop, and the state refuses the transition regardless.
- **Recording, end to end** (`RD-2` to `RD-5`, `RD-8`). Two taps four seconds apart, in Gmail,
  produced exactly two Steps at exactly the coordinates touched, with
  `"delayMillisecondsAfter": 4045` on the first — the measured pause, not a rounded one. Gmail
  **advanced to its next screen while being recorded**, which is `RD-5` working. A 400ms swipe
  recorded as a `swipe` of `407` milliseconds, which is what it actually took.
- `PK-1` to `PK-3`, end to end: *add a step* took the editor off the screen, a 700-pixel drag
  became a `swipe` between exactly its two ends, and the **Step** carried the default 200ms travel
  rather than the 400ms the drag took — which is `PK-2`'s whole point.
- `OV-32`, by `dumpsys`: the panel's frame is now `[0,1616][1344,2992]`. It was `[0,1582][1344,2920]`
  — seventy-two pixels short of the display, with the application underneath showing through below
  the sheet. Adding the flags alone did **not** fix it; the frame stayed at 2920 until the negative
  offset went in too.
- `OV-33`: the control window measured 840×228 and now measures 792×144, with six buttons instead
  of five. *Done* took the Overlay from fourteen windows to one.
- `OV-34`, both phases. With twelve **Step**s the sheet opened at its peek of 1376 pixels, a drag
  on the grabber took it to 2753 — its full extent — and a further swipe in the body left the
  window at 2753 while the content moved. With five **Step**s the same drag settled at 1791,
  because that is all the content there was: the sheet is bounded, not sized.
- `DS-7`: the grey wedge at the control's bottom corners is gone. Where the old build measured 159
  and 204 against a white application, the same points now read 240 and 241 — the wallpaper — and
  the darkening above and below the window differs by six levels rather than being offset downward.

### What the emulator disproved

`DS-6` is here rather than above because the emulator **refuted** a design decision rather than
confirming one.

- **Cross-window blur is unusable in this application.** `supports_background_blur` is 1 and
  `isCrossWindowBlurEnabled` returns true, so the control's `FLAG_BLUR_BEHIND` was accepted. Every
  other Overlay window then stopped answering touches: `InputDispatcher` logged
  `Untrusted touch due to occlusion by /1000` and named `Dim Layer for - Display 0 …
  mode=BLOCK_UNTRUSTED`. Asking for a blur creates that layer, and Android's untrusted-touch rules
  drop touches to any untrusted window beneath it. Removing the blur restored input immediately,
  which is how it was proved rather than guessed.
- **The blur was not happening anyway.** Before that was found, a row of Gmail's body text sampled
  through the control gave two values, 92 and 43, in the same proportion as the sharp text beside
  it — unblurred text at the surface alpha, not a frost. Whether a device that really does blur
  would also block the touches is untested and now moot.

- `RD-5`'s failure mode, before it was fixed: one tap produced `recorded a touch of 0ms` and
  `recorded a touch of 1ms`, 41ms apart — the layer recording its own re-emission. Two guards
  rather than one, and the same test then gave two touches for two taps.

### A second AVD, and the rotation this document said was impossible

A second AVD was added — a plain **Medium Phone, API 37, 1080 × 2400 at 420 dpi** — because a
second **Screen profile** is the only way to exercise [ADR-0013] end to end, and because one
device profile is a poor sample of a platform.

**The emulator rotates.** This document previously recorded that the AVD "refused to turn" and
fell back to `wm size`. That conclusion was wrong, and the cause is worth knowing: every attempt
had been made with the **launcher** in front, and Nexus Launcher is locked to portrait, so the
display honours no rotation request while it is the top activity. `dumpsys window` says so plainly
— `mUserRotationMode=USER_ROTATION_LOCKED mUserRotation=ROTATION_90` alongside `mRotation=0` — and
the moment a rotatable activity is in front, `settings put system user_rotation 1` turns the
display for real: `cur=2400x1080`, `mCurrentRotation=ROTATION_90`, configuration `land`.

So `OV-37` is now proved through the sensor path rather than around it, with the **Overlay** up
over another application:

- the panel window is rebuilt as `(0,0)(1050x1080) gr=TOP END`, frame `[1350,0][2400,1080]` — the
  side sheet, pinned to the end edge and full height;
- the **Marker** layer is resized to `2400×1080`, at `alpha=0.8`, which is the platform forcing
  the full-screen untouchable window to four-fifths as recorded below;
- the floating control is clamped back inside the new bounds at `[656,381][1350,507]`, ending at
  exactly the `x` the panel begins at. They abut and do not overlap, which is `OV-13`'s claim in
  landscape;
- `Display.rotation` is `ROTATION_90` throughout, which is precisely what `wm size` could never
  show.

**`SM-18` end to end, under a real rotation.** A **Scenario** measured at `1080 × 2400 Portrait`,
rotated: the banner reads *"Built on a different screen — Measured on 1080 × 2400 Portrait. This
screen is 2400 × 1080 Landscape, so the scenario will not run here"*, and *Rebuild for this screen*
asks first. Confirmed, `scenario.json` becomes `2400 × 1080 LANDSCAPE_LEFT` and the one **Step**'s
point is pulled from `(540, 1200)` to `(540, 1079)` — inside the screen, at its last row. The
**Marker** window then sits at `[482,1021][598,1137]`, whose centre is exactly that point, the same
invariant `OV-31` fixed. Worth noting rather than filing: a handle centred on the last row is half
off-screen, so it is there to be seen and not to be dragged.

**Coordinates, on a profile nothing had been built against.** Three **Step**s aimed at
`(600, 300)`, `(1200, 540)` and `(1800, 800)` produced three **Marker** windows centred on exactly
those pixels, and a **Step** placed by tapping wrote the tapped pixel into `scenario.json`
unchanged.

**`OV-13` and [`GX-8`], measured at last.** With a twelve-second hold in flight and Stop pressed by
a console finger on the floating control: the press is received immediately — the control changes
to **"Stopping…"** while the synthetic pointer is still down — and the pointer is released
`11.6 s` later, at the stroke's own end, after which the editor returns and the control reads
*Stopped*. That is `GX-8` exactly as written: *honoured during a stroke, the stroke completed
rather than abandoned, and the interface saying "stopping" for however long that takes.* The claim
this document listed as unverified is now verified, and it is the specified behaviour rather than
the instant stop a reader might assume. The cost is stated in `GX-8` itself and bounded by
`GestureDescription.getMaxGestureDuration()`.

**The Overlay is invisible over Settings.** With Settings in front the control still exists but
`dumpsys` reports `mPolicyVisibility=false`, `isVisible=false` and
`mIsForceHiddenNonSystemOverlayWindow=true` — the platform's anti-tapjacking measure, which hides
every non-system overlay while certain system screens are up. Nothing to fix; worth knowing before
someone reports the control "disappearing", and worth remembering when writing onboarding copy
that sends the user into Settings and back.

### What the emulator could not be made to do

**Currently unverified — no physical Android device is in use on this project.** Everything below is
untested until one is:

- The **latched-touch freeze** itself, and whether cycling the accessibility service clears it. The
  whole value of the one-tap *free the touch* recovery rests on this, and it is a promise that must
  not be made in the interface until it has been seen to work on a phone.
- Vendor power optimisers — Samsung One UI sleep, MIUI, and their habit of stopping a service that
  looks idle. Reported as a cause of silent death across the category.
- Skin-specific **Overlay** restrictions.
- **Real touch hardware.** Rotation and Stop-mid-stroke have both moved out of this list — see the
  second-AVD section above — but they moved on the strength of a console finger, which is a real
  input device and still not a fingertip on glass.
- **The run notification opens collapsed**, in the *Silent* section, so Stop and *free the touch*
  need one expand before they can be pressed. `OV-15` wants them reachable in a panic; the
  **Quick Settings tile** added for `GX-12` is the answer to that, and the tile itself has not been
  pressed on hardware either.
- **The whole restricted-settings sequence** (`PM-4`, `PM-7`, `PM-8`). Tier 2 grants the service
  with `appops` and `settings put`, which skips the wall entirely, so the one part of onboarding
  that exists *because* the platform fights the user is the part nothing has exercised.
- **`PM-11`** — Advanced Protection. The API is read and the screen is written, and no device here
  has the mode to turn on.
- Real touch latency and how a game reacts to a synthetic touch arriving beside a real one.
- Whether an application detects the accessibility service and refuses to run.
- Whether one window per **Marker** (`OV-27`) stays smooth at fifteen **Step**s. The emulator was
  fine at one and two; nothing has drawn a full-sized **Scenario** yet.
- **Recording with a real finger.** Everything above went in through `adb shell input`, which
  produces a clean synthetic touch. A finger is messier: it wanders inside the slop, it arrives and
  leaves at speeds `input` does not reproduce, and `RD-3`'s tap-versus-swipe decision is made on
  exactly that. The 24ms window in which `RD-5` drops a touch has also only ever been met by taps
  arriving seconds apart.
- **The sheet under a real finger.** `adb shell input swipe` with a 400ms duration produced too
  few motion events for Compose to recognise a drag at all, and the sheet did not move; the same
  gesture over 900ms worked. A finger produces far more events than either, so this is an artefact
  of the harness rather than a defect — but it means `OV-34`'s drag has only ever been driven by a
  synthetic gesture slow enough to be seen.
- **Whether a re-emitted swipe is good enough to use.** `RD-5` says plainly that it will arrive
  late; nobody has yet recorded a drag in an application that cares.

### What the emulator did prove about recognition and languages

A3 and A4 were both walked end to end on the emulator (Pixel 10 Pro XL, API 37, 1344 × 2992).

- **A Template was cropped, found and pressed.** Crop the **Photos** icon out of the home screen,
  save, run: the search found it, the **Step** moved onto the match, the tap landed, and
  `topResumedActivity` became `com.google.android.apps.photos/.home.HomeActivity`. The
  `scenario.json` written was schema 2 with the point at the crop's centre `835, 2105` and a
  **Search region** of `596, 1866 → 1074, 2344` — which is `TP-9`'s padding of 48 dp × 3 arithmetic
  exactly.
- **And the other half.** Run the same **Scenario** from inside Photos, where the icon is not on
  screen: after the five-second wait the run ended and the control said *Step 1 never found its
  picture*. `TP-21`'s two outcomes are both real.
- **The language changed under the Overlay.** Choosing Tiếng Việt, and then 日本語, re-lettered the
  Activity **and** the floating control **and** the open panel with no restart and nothing
  recreated — `IL-5`, which is the whole reason A4 was a slice rather than four resource
  directories. Choosing *follow the system* removed the key from the preferences rather than
  writing a language, as `IL-1` requires.

### What the emulator cost, and it was worth it

- **`TP-7` needs `removeViewImmediate`.** `removeView` is a request: the window survives until the
  window manager next runs. A frame taken 160 ms after the Overlay was dismissed still contained
  the panel and the floating control, which would have been baked into the **Template**. The fix
  is a synchronous removal on the crop path only — `OV-13` re-attaches the control on every state
  change, and removing it synchronously there makes Stop blink.
- **`adb shell am force-stop` switches the accessibility service off**, which is the same fact tier 2
  states above and was first paid for here: a script that force-stops has to put the grant back.
  Re-launching with `am start` does not.

### Platform behaviour worth knowing about

- **A full-screen `FLAG_NOT_TOUCHABLE` overlay is forced to 80% alpha.** The system logs
  `setting alpha to 0.80 to let touches pass through` and does it whether asked or not. It applies
  to the **Marker** line layer, which is the only window that is both full-screen and untouchable,
  so the lines are drawn at four-fifths opacity and there is nothing to be done about it.
- **`adb shell input swipe` starting at a screen edge is taken as a back gesture** before any
  window sees it, which makes edge-anchored drags awkward to script. Start the swipe a little way
  in.

## Not automated on purpose

Driving the system permission dialogs with UiAutomator. The dialogs change wording and layout
between Android versions, so those tests break when Google edits a label rather than when this code
is wrong — the most brittle coverage available, bought at the highest maintenance price.

[`GX-8`]: ./sdd/05-gesture-execution.md
[ADR-0013]: ./adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md
