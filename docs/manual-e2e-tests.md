# Manual end-to-end tests

The automated suite stops at the operating-system boundary: every real seam is replaced by a fake
(`TestSupport.swift`). It proves the logic; it does **not** prove that the real app clicks the
right place on a real screen.

This document is the remainder. Each case states what to do, what to expect, and **which
requirement** in the [SDD](sdd/) it proves. The last column is yours to fill in.

Quoted strings show the app's own interface text. Cases written before Slice 6 quote the
Vietnamese wording the interface had at the time; the same labels now come from `en.lproj` and
`vi.lproj` (`LC-1`).

## Why these cases cannot be automated

| Obstacle | Consequence |
|---|---|
| Accessibility and Screen Recording are granted through TCC to a **signed bundle**, never to SwiftPM's test binary | No events can be emitted and no screen captured inside `swift test` |
| Real screen coordinates, Retina scaling, multiple displays | A fake only hands back the number the test put in, so an axis flip or a forgotten scale division still passes |
| SwiftUI has no test seam in this project | 2,182 lines of interface only a human presser can judge |

## Preparation

```bash
./scripts/build-app.sh && ./scripts/install.sh
```

Open **System Settings → Privacy & Security** and grant Clickify: **Accessibility** (required,
`SF-3`) and **Screen Recording** (only needed for session C, `SF-5`).

> After granting, you **must quit and reopen** Clickify. macOS does not hand a permission to an
> already-running process. This is exactly where `SF-7` cannot tell "never granted" from "granted
> but not restarted".

Have a harmless destination application to fire at — TextEdit with an empty document is enough.
**Never use Terminal, a logged-in browser, or any window with a delete button as the target.**

Always remember the way out: **`⌥⌘S` stops everything** (`UI-15`). Press it once before you start.

---

## Session A — The baseline (no Screen Recording needed)

**Run on 11/09/2026 — 11 of 11 passed.** How it was run is at the [end](#how-session-a-was-run).

| # | Do | Expect | Proves | Result |
|---|---|---|---|---|
| A1 | Simple mode, at the cursor, 5 times, 500 ms apart | A 3-second countdown then exactly 5 clicks at the cursor | `UI-1` `UI-6` `EX-3` | **Pass** — 5 down/up pairs at `(500,550)`, 530/508/527/524 ms apart, first click 3.0 s after pressing |
| A2 | As A1 but cancel during the countdown | **No** click emitted | `EX-3` | **Pass** — 0 events |
| A3 | Check `Scenarios/` after A1 | No new directory | `UI-7` | **Pass** — `Clickify/` had not even been created |
| A4 | A 1-step scenario, 20 repetitions, 200 ms apart | Clicks the right place, the floating panel counts up | `UI-2` `UI-13` `EX-4` | **Pass** — exactly 20 pairs, 3,993 ms total |
| A5 | Press `⌥⌘S` while A4 is running | Stops at once, status says the user stopped it | `EX-13` `EX-15` `SF-9` | **Pass** — 10 of 40 events then stopped; status *"Đã dừng"* |
| A6 | One `click` Step with `count = 3` | Three consecutive pairs, understood by the OS as a triple click | `EX-16` `EX-17` | **Pass** — `clickState` exactly 1, 2, 3; 30 and 32 ms apart |
| A7 | `holdMs = 1500`, 3 repetitions | The button holds about 1.5 s then releases | `EX-18` | **Pass** — held 1566/1589/1548 ms; 532/529 ms between repetitions |
| A8 | Press `⌥⌘S` **while A7 is holding** | The button **is released**; the mouse still works | `SF-1` `SF-2` `EX-14` | **Pass** — `leftDown` 4390 → `leftUp` 5424 (cut to 1034 of 1500 ms); moving the mouse afterwards produced no `leftDrag` |
| A9 | Unlimited repetitions, run then `⌥⌘S` | The panel counts repetitions with no total; it can be stopped | `UI-14` `SF-9` | **Pass** — ran continuously at ~316 ms/click; silent for 4 s after `⌥⌘S`; popover said *"lặp đến khi dừng"* |
| A10 | Rename a scenario | The new name persists, with no Save button to press | `UI-11` `ST-11` | **Pass** — `scenario.json` renamed as soon as typing stopped |
| A11 | Run, then try to edit | Editing is impossible while running | `UI-12` | **Pass** — shows the lock *"Đang chạy — không sửa được"*, the delete button disappears, controls dim |

### Findings outside the checklist

Installing a new build over one that already had the permission **invalidated Accessibility while
System Settings still showed the toggle as on**. The old message — *"Hãy cấp quyền Accessibility
rồi thử lại"* — sent the user to exactly the screen that says the permission is already enabled.
Fixed; see `SF-10`.

Only exposed by installing over a copy that already had the permission. Writing the spec could not
have shown it.

## Session B — Locked application, window anchoring, drag, keyboard

> **B2 was changed.** The original — *move the window then run again* — **cannot fail**: `EX-6`
> requires the Target to be resolved before **every repetition**, and `ScenarioRunner` re-reads the
> window frame in `applyPointer`, so any new run reads the new frame. Only moving it **while
> running** distinguishes "re-read every repetition" from "sampled once and cached". A green test
> that cannot go red is not evidence.

| # | Do | Expect | Proves | Result |
|---|---|---|---|---|
| B1 | Set **Locked application** = TextEdit, a `click`/`windowRelative` Step, run | Clicks the right relative position inside the TextEdit window | `EX-7` | **Pass** — 3 clicks at exactly `(500,450)` = corner `(200,200)` + offset `(300,250)`, pid = Clickify |
| B2 | 10 repetitions 800 ms apart; **drag the TextEdit window elsewhere around repetition 3–4, while running** | Clicks **follow the window from the very next repetition** — two coordinate clusters, the second matching the new position | `EX-6` `DM-9` | **Pass** — 4 clicks at `(500,450)`; window dragged to `(400,350)` at t+6.2 s; **the very next click was already at `(700,600)`**, as were the remaining 6 |
| B2b | Stop fully, move the window, **run again** | Still hits the same point in the interface | smoke test, **not** evidence for `EX-6` | **Pass** — window at `(400,350)`, rerun produced `(700,600)` |
| B3 | Drag another window (Finder) over that point, then run | The scenario does **not** click into Finder | `EX-10` | **Pass** — 4 clicks then Finder brought forward: **complete silence**, 0 clicks into Finder; status *"Điểm thao tác không nằm trong ứng dụng…"* |
| B4 | Quit TextEdit while the scenario is running | Stops, status says the locked application quit | `EX-11` | **Pass** — 6 clicks then `quit` TextEdit: stopped immediately; status *"TextEdit hiện không chạy"* + *"Ứng dụng đích đã đóng"*, Start disabled |
| B5 | Minimise TextEdit into the Dock then run a `windowRelative` Step | Stops with a message, emits **no** junk coordinates | `EX-7` `EX-25` | **Conditional pass, awaiting a re-run.** 0 clicks, it did stop and did report — but it **named the wrong cause**: *"điểm nằm ngoài ứng dụng khoá"* when the window was in fact minimised. `EX-25` was written and fixed **after** this was recorded, so the result above predates the fix. Re-run and check the message now names the minimised window |
| B6 | A `drag` Step from point A to point B in TextEdit (selecting text) | Text is selected continuously, with no jumps | `EX-20` | **Pass** — 1 `leftDown`, **24** `leftDrag`, 1 `leftUp`; TextEdit selected 40 continuous characters `"AAA BBBB … HHHH I"` |
| B7 | Press `⌥⌘S` **while B6 is dragging** | The mouse is released, not stuck mid-drag | `SF-1` `EX-23` | **Pass** — 7 `leftDown` / **165** `leftDrag` / 7 `leftUp`. A complete run would be 168, so the 7th drag was cut at step 21 of 24 and released at `x=464` rather than the destination `x=500`. Moving the mouse afterwards: 0 `leftDrag` |
| B8 | A `typeText` Step with `Xin chào 123 — ăn` | Exactly that text, with Vietnamese diacritics and the em dash | `EX-21` | **Pass after a fix** — the old version produced `"Aa chào 123 — ăn"` with no error. After switching to chunked sending (`EX-24`, [ADR-0007]): **8 of 8 correct** through the app itself |
| B9 | A `pressKey` `⌘A` Step then a `pressKey` `⌫` Step | Select all then delete all | `EX-22` | **Pass** — the document went from `"Xin chào 123 — ăn"` to empty |
| B10 | Run a two-typing-Step scenario, **stealing focus to Finder between the steps** | TextEdit **is brought forward** before typing; no text leaks into Finder | `SF-4` `EX-12` | **Broken, left open** — `SF-4` did run (Finder stole focus at t+4 s, TextEdit returned at t+7.3 s), but the text came out `"â"` instead of `"[B10b]"`. Cause not found. **The project owner ranks typing strings a low priority**, so this stops here rather than digging further |
| B11 | With **EVKey on**, a `typeText` Step with `[B10b]` into TextEdit | Exactly `[B10b]`. This is `B10` again, now that the string takes the key-by-key route | `EX-26` `EX-27` | **Pass** — two-sided. TextEdit holds exactly `[B10b]`, the string `B10` turned into `"â"`. The observer shows **6 down/up pairs, one per character**, key codes 33/11/18/29/11/30 — real codes, never `virtualKey: 0` — spaced 27/24/26/27/27 ms against the 25 ms constant. `EX-26` works |
| B12 | Repeat B10: two `typeText` Steps, stealing focus to Finder in between | Both strings arrive intact; no text leaks into Finder | `SF-4` `EX-12` `EX-26` | |
| B15 | With EVKey in Vietnamese mode, type `password aa dd` — a string **Telex transforms** | Exactly `password aa dd`, if `EX-27` works | `EX-27` | **Fail** — came out `Pasword â đ`. See below |
| B13 | Press `⌥⌘S` **while B11 is typing**, then check the input source in the menu bar | Back to EVKey, not left on ABC. The same obligation `SF-1` places on the mouse button | `EX-27` | **Not reachable** — there is nothing to restore, because `EX-27` never changes anything on this machine. See below |
| B14 | A `typeText` Step with `Xin chào 123 — ăn` (unchanged from `B8`) | Still exactly that string: a non-ASCII string must still take the `EX-24` route | `EX-21` `EX-24` | |

### Findings outside the checklist — session B

Three real bugs, none of them the aim of any checklist case. All three only appeared on a real
machine.

**`EX-24` — typing produces the wrong characters, silently.** The worst of them. Sending character
by character loses the Unicode payload and macOS falls back to the `virtualKey` (0 = the `a` key),
inserting `a` in place of the real character with no report at all: `"Xin chào 123 — ăn"` came out
`"Aa chào 123 — ăn"`. Fixed by sending in chunks of 20 UTF-16 units → **8 of 8 correct** through the
app itself. **The root cause is still unknown**, and there is no trustworthy failure rate — see
[ADR-0007] for what has been ruled out and why the measuring rig is not good enough.

**`UI-16` — an error shown with a tick.** `statusIcon` returned `checkmark.circle` for every
non-running state, so the line *"Có lỗi: …"* carried the success icon, and was clipped to one line
so the cause could not be read in full. Fixed: an orange warning triangle, and wrapping allowed.

**`EX-25` — a minimised window still used as the coordinate origin.** Accessibility reports the old
position of a minimised window as though it were still on screen. `EX-10` blocked the click so
nothing was damaged, but that was luck: if the locked application had a second window over that
point, the click would have fired at the coordinates of a window that is no longer visible. Fixed:
minimised windows are skipped.

### Findings outside the checklist — the key-by-key typing session

**`EX-27` does not work, and cannot work as designed.** Switching the input source to ABC with
`TISSelectInputSource` was supposed to keep Telex from folding `aa` into `â` while typing key by key.
It changes nothing here, because **EVKey is not a Text Input Services input source at all**.
Enumerating every source on this machine returns Apple's own Vietnamese methods
(`com.apple.inputmethod.VietnameseIM.*`) and no EVKey. EVKey is the event-tap kind — exactly what
[ADR-0007] described as sitting "between the keyboard event stream and replaying keys under its own
pid" — so selecting a different TIS source does not disable it.

Measured two-sided with `B15`, typing `password aa dd`:

| Who | What the observer saw |
|---|---|
| Clickify (pid 67983) | 14 `keyDown`, one per character, `p a s s w o r d ␣ a a ␣ d d`, real key codes, ~25 ms apart — **correct** |
| EVKey (pid 61037) | replayed `á`, then `as`, then `â`, then `đ`, each as `virtualKey: 0` with a Unicode payload |

TextEdit ended up with `Pasword â đ`. So `EX-26` emits exactly the right thing and EVKey rewrites it
downstream. Note what EVKey replays **with**: the `virtualKey: 0` plus payload mechanism, the very
one `EX-24` exists because of.

`EX-27` therefore needs redesigning or dropping; it is currently a requirement the code satisfies and
the machine ignores. `B11` still passes because `[B10b]` contains no Telex trigger — which is also why
it is a weak test on its own, and why `B15` was added.

**A Template that is absent gets matched to a different shape.** Found by a case nobody had run: `I7`
was always tested by **pressing** the button that makes `LATE` appear. Run without pressing it, so the
Template genuinely is not on screen, the Step does **not** wait out its 12-second timeout — it clicks
`S4` at `(283,433)` after about 554 ms, the same timing as a successful match, at the default
threshold of `0.90`.

This is **not** caused by the two-pass matching of `RG-25`. Attributed by A/B: with the second pass
disabled entirely and everything else identical, the result is the same click on the same target at
the same timing. The false match comes from the **native** pass, and predates both.

It matters more than a wrong click on a practice page suggests: `EX-9`'s `skipStep` and `EX-8`'s
`stopScenario` both rest on "not found" being reliable. A Template that cannot be found should not be
able to fire at something else.

### A measurement trap: the Vietnamese input method holds text in a composition buffer

This machine runs EVKey. Text typed by Clickify may be **correct but not yet committed** to the
document, and while it is not, `get text of document 1` returns **empty** — indistinguishable from
lost characters. Proof: typing `"abc"` reads back `""`; pressing the right arrow to commit and
reading again gives `"abc"`.

Consequence: every keyboard measurement has to **commit before reading**, and every rate measured
before this was understood has been withdrawn. The results also changed with how the document was
cleared between runs — resetting it via AppleScript gave completely different numbers from clearing
with `⌘A` + delete.

### Notes on how session B was run

Reusing session A's observer and clicker, plus three additions:

- **A window guard** — the popover **closes every time a scenario runs**, because `ScenarioRunner`
  calls `activate` on the locked application. Not knowing that the first time, the next click landed
  in the terminal window and a menu-navigation key sequence **sent a stale command into the working
  session**. Every keystroke since goes through a check that the popover is open.
- **Anchor the interface by colour, not by coordinate** — the popover changes height with its
  content so fixed offsets drift, and that made me run the wrong scenario once. It now locates the
  accent-coloured patch instead.
- **Self-check after selecting** — read `selectedScenarioID` from UserDefaults to confirm the right
  scenario, after writing a `PENDING` sentinel so that a missed selection cannot quietly pass on the
  old value.

The keyboard is checked by reading the TextEdit document back through AppleScript, never by looking
at the screen.

## Session C — Image and text recognition (Screen Recording needed)

This is the most important session: all four structural risks the automated suite cannot touch live
here.

| # | Do | Expect | Proves | Result |
|---|---|---|---|---|
| C1 | A `click`/`template` Step → **Crop a template** → draw around a button in TextEdit | The overlay disappears **before** the capture; the template contains neither the overlay nor an Clickify window | `RG-5` `RG-20` | **Pass** — the app's template matched the reference image **pixel for pixel** (`0.00/255`). The check is not vacuous: the overlay really does darken the screen (`12.74/255`). `RG-20`: putting an Clickify window over the capture area still produced the TextEdit behind it (`0.00/255`), quite unlike what was on screen (`65.74/255`) |
| C2 | Run that Step | **Clicks the exact centre of the button**, no offset | `RG-2` `RG-11` | **Pass** — clicked `(273,321)`, a **pixel-exact** match with the centre measured through Accessibility. A 272×86 **pixel** template → a 136×43 **point** area: `RG-2` is right |
| C3 | **If the machine has a non-Retina external display**: move TextEdit there, repeat C1–C2 | Still hits the centre | `RG-2` `RG-24` | **To run** — the earlier *"only one internal Retina display"* is out of date: this machine does drive a 1920-wide external display, remembered at both `Scale 1` and `Scale 2`. Run it at **`Scale 1`**, which is the case that matters: that is where the old `?? 2` fallback would have halved every coordinate | **Invalid, not a failure** — the Template was cut with `screencapture` rather than the app's own crop, and at a 3·10⁻⁵ margin that mismatch flips the choice. It clicked `DUP-A` twice, deterministically, but the matcher picks `DUP-B` correctly when handed the same images offline. Needs re-running with a Template cropped through **Chụp lại…**. See below |
| C4 | **If there are several displays**: put the window on the secondary display, repeat C1–C2 | Still hits, and does not fire onto the main display | `RG-2` | **To run** — the external display sits at `OriginX = 1512`, so this exercises a non-zero `frame.minX`, which a single-display machine can never reach | **Pass** — with the page on the external display and a game window on the built-in, the Step searched both and clicked `(2063,332)` on the external. Nothing was emitted onto the main display |
| C5 | Move that button elsewhere (resize the window), run again | Found again at the new position | `RG-8` | **Pass** — window moved to `(450,430)`, found again, clicked exactly `(523,551)` |
| C6 | Cover the button (put another window over it), run with `wait = 5000ms`, `onTimeout = stop` | Retries for about 5 seconds then stops with a message | `EX-8` | **Pass** — 0 clicks, the following Step did **not** run; measured 8.44 s = 3 s countdown + **5.44 s of retrying** (5000 ms configured) |
| C7 | Repeat C6 with `onTimeout = skipStep` and another Step after it | That Step is skipped and the next one **still runs** | `EX-9` | **Pass** — Step 1 timed out without clicking, **Step 2 still ran** and clicked exactly `(900,700)` |
| C8 | Repeat C6 but uncover it **while** it is waiting | Found and clicked at once, without waiting out the timeout | `EX-8` | **Pass** — uncovered at t+5.0 s, click emitted at t+5.7 s: found after **714 ms**, not after the full 8 seconds |
| C9 | A `click`/`text` Step with a word visible on screen | Clicks the centre of that piece of text | `RG-13` `RG-15` | **Pass after a fix** — the first run clicked `(356,551)` = the centre of the **whole line** `"ZUKAMI QWERTY"`, not of the word being aimed at; 83 points off (see `RG-15`). Rerun on the target page, where each word is wrapped in its own `<span>` so the page **declares each word's own coordinates**: Clickify clicked `(138,716)` = exactly the centre of `word-zukami`, while the whole line's centre is `(404,716)` — 266 points apart, impossible to confuse. The page confirmed the click landed on `word-zukami` |
| C10 | Repeat C9 with the case changed | Still found | `RG-14` | **Pass** — the scenario looked for lowercase `"zukami"` while the screen showed uppercase `"ZUKAMI"`: it still clicked `word-zukami@(138,716)` |
| C11 | **Draw a search region** over the left half of the screen, put the template in the right half, run | **Not found** — the search region really does take effect | `RG-4` `RG-6` | **Pass in both directions** — target outside the region: *"Không tìm thấy Ảnh mẫu zukami.png (ngưỡng 0.90)"*; moved inside the region: clicked exactly `(873,551)` |
| C12 | A `template` Step with **no** Locked application | Still runs, not blocked | `RG-17` (ADR-0006) | **Pass** — a scenario with no Locked application ran recognition normally |
| C13 | Time it by eye: how long a `template` Step takes from start to click | Record the number. Over 1 second means `RG-18` needs another look | `RG-18` `RG-21` | **Pass** — 560–710 ms per full-screen recognition. Under the 1-second threshold |
| C14 | **Revoke** the Screen Recording permission in System Settings then run a `template` Step | An error naming **both** possibilities (never granted / needs restarting), no crash | `SF-7` | **Pass** — no manual revoke was needed: after the install with a stable certificate, the Screen Recording permission lapsed on its own even though the System Settings toggle still showed as on, so the machine was already in exactly the state to test. Running a scenario with a `template` Step: the app **did not crash**, and the popover showed exactly one orange line *"Có lỗi: Hãy cấp quyền Screen Recording cho Clickify rồi thử lại. Nếu đã cấp rồi, hãy thoát và mở lại Clickify."* — naming both possibilities as `SF-7` demands. Before that the popover already carried the warning *"Kịch bản dùng nhận dạng ảnh/chữ nên cần thêm quyền Screen Recording"* with a grant link, exactly per `SF-5`: it only asks when the selected scenario actually uses recognition.<br><br>**The "toggle it off yourself" branch does not reproduce on this machine, and is not waiting to be run.** Turning Clickify's toggle off in System Settings: the toggle **stays at `0`**, but the running process **still captures the screen normally** (still recognised `S3` and clicked its exact centre `(267,353)`) — standard macOS behaviour, a revoke only takes effect after the app quits. Quit and reopen: the toggle **returns to `1`** on its own and the app captures as before. Tried twice, the second time with no stray clicks from the rig. So the state "revoked and in force" does not reproduce here; what was measured is the state where the permission genuinely is not in force, and there `SF-7` reports correctly |
| C15 | Crop a Template on the **built-in** screen, move the window to the **1x external** display, run | Found and clicked — the second pass at the other scale is what makes this work. Without it the Step just times out | `RG-25` |**Blocked** — the external display was not connected during this session | **Pass**, two-sided. Template `124×124` cropped at 2x on the built-in; `DUP-A` on the 1x external is `62×62`. Clickify emitted `(2063,332)` and the page independently logged `DUP-A` at `screenX 2063, screenY 332`. **A/B:** with the second pass disabled and nothing else changed, the Step emitted nothing and timed out — `RG-25` is exactly what makes this work |
| C16 | The reverse: crop on the **1x external** display, move the window back to the built-in, run | Found and clicked | `RG-25` |**Blocked** — needs the external display | **Pass** — the other direction. A genuine 1x capture (`62×62`) found on the 2x built-in, where the tile is `124×124`: clicked `(687,433)`, page logged `DUP-B`, `pageX 627`. Here it did pick the tile the Template came from |
| C17 | Time `C15` by eye, then time `C2` again | `C2` is unchanged (560–710 ms): the ordinary same-display case must not have been made slower. `C15` may be about twice that | `RG-25` `RG-18` |**Blocked** — needs the external display | **Pass** — native, same scale: **567 ms**. Cross-scale 1x→2x: **639 ms**. Cross-scale 2x→1x: **829 ms**, the dearest because the native pass has to fail on both displays first. All under the 1-second bar, and the ordinary path is unchanged |
| C18 | Drag the external display's box **up or down** in System Settings so the screens are no longer top-aligned, then crop a Template on it | The highlighted frame follows the cursor instead of being drawn offset | `RG-26` |**Blocked** — needs the external display | **Inconclusive** — the two formulas draw **pixel-identically** on this arrangement. See `RG-26` |

### Findings outside the checklist — session C

**`RG-15` — clicking the middle of the line instead of the word being aimed at.** `TextFinder` took
`observation.boundingBox`, but Vision folds a **whole line** into one observation. Looking for
`"ZUKAMI"` in the line `"ZUKAMI QWERTY"` gave the centre of the line, **83 points** off. In real use
that is looking for `"Lưu"` in the line `"Lưu   ⌘S"` and firing into the gap between the two. Fixed
with `candidate.boundingBox(for: range)`, with a regression test: two words on the same line must
give **two different boxes** — revert to the old code and the boxes coincide exactly and the test
goes red.

### Two items left hanging from session A, now done

The **editor** window is a real `NSWindow`, so its AX tree reads in full, quite unlike the popover.

- **`UI-9` passes** — adding a Step, **reordering by drag** (Step 1 dragged to the end, the order
  really changed), duplicating (3→4 Steps, two template Steps side by side), deleting (4→3). Every
  change written straight into `scenario.json`, with no Save button (`UI-11`, `ST-11`).
- **`UI-10` passes** — the detail panel shows exactly **two separate pickers**, Action and Target,
  plus the fields each choice implies (Button, Click count, Hold; Match threshold, Max wait, On
  timeout, Search region).

## Practice targets for image recognition — `I1`…`I8`

Session C used TextEdit as the target, and TextEdit **cannot expose** the class of failure most
dangerous to the primary use case: in a game the area to aim at usually **has no text at all**, and
two near-identical buttons are an everyday occurrence. So a dedicated target page was built:
`tools/testing/target-page.html` + `log-server.py`.

The page declares **each target's screen coordinates** itself and logs **which click it received**.
That gives every case **two independent measurements**: the pid-tagged observer records what Auto
Click emitted, the page records what the application received. The image area deliberately contains
no text whatsoever.

| # | Target | Expect | Result |
|---|---|---|---|
| I1 | One clearly distinct shape among others | Clicks it | **Pass** — `S3@(267,353)`, exactly the centre the page declares |
| I2 | Two nearly identical tiles differing slightly in colour (`#1e88e5` / `#2b93e8`) | Clicks the one the template came from | **Pass** — `DUP-A@(611,353)`, searching the **whole screen** with `DUP-B` right beside it. The first run produced `S2@(191,353)` and exposed a broken fixture → see below |
| I3 | One different shape among a crowd of identical ones | Clicks the different one | **Pass** |
| I4 | Five identical circles | Record which one wins | **Pass** — `G1@(109,449)`, meaning **the leftmost** wins. Five identical circles score equally, so the first in scan order keeps the crown. Not random; rerunning still gives `G1` |
| I5 | A tiny 26 px target | Still found | **Pass** — `TINY@(274,558)`, hitting the exact centre despite being only 28×28 |
| I6 | A target on a noisy background | Still found | **Pass** — `NOISY-ICON@(160,591)`, 1 px off the page's declared centre `(160,592)` because the 63.5 px height rounds |
| I7 | A target that appears late | Waits then clicks | **Pass** — pressed the button that makes `LATE` appear after 4 s and only then ran; the scenario waited within its 12 s budget and clicked `LATE@(371,575)` |
| I8 | A target that has moved | Finds it at the new place | **Pass** — moved `MOVE` from `(491,575)` to `(651,605)` immediately before running; Clickify clicked exactly `(651,605)`, not the old spot |

### Findings outside the checklist — template matching

**Fixture `I2` posed the wrong question.** On the first rerun after the `Float`→`Double` fix, Auto
Click clicked `S2` rather than `DUP-A`. Not the app's fault: `S2` was declared as
`['square','#1e88e5']` and `DUP-A` is also a `#1e88e5` square of the same `62×62` size — **two
pixel-identical targets**. The question "click the tile you were given a template of" then had
**two** right answers, and a full-screen run could pass without ever having to tell `DUP-A` from
`DUP-B` — precisely what `I2` exists to measure.

`S2` was changed to `#0d47a1` so that the only near-identical pair on the board is `DUP-A`/`DUP-B`.
Rerun: `DUP-A`, correctly. No other fixture uses an `S2` template so the rest are unaffected; `I1`
and `I3` were rerun on the new board and still pass.

**Template matching picked a near-identical wrong target.** Given a template of tile **A**, Auto
Click clicked tile **B**. Measured with the app's own matcher:

| | Score |
|---|---|
| template A on tile **A** (correct) | `0.999498` |
| template A on tile **B** (wrong) | **`1.001296`** ← wins |

A score **above 1.0** is impossible for normalised correlation — the signature of catastrophic
cancellation. Recomputing with the **same formula** in `Double`: tile A = `1.000000`, tile B =
`0.999957`. So the matcher **can** tell them apart, with a true difference of `4·10⁻⁵`; but the
`Σh² − n·h̄²` form accumulated in `Float` generates noise of about `2·10⁻³` — **forty times the
real difference** — so it was choosing at random. Control: template `S3` scored `1.0017` on `S3`
and `0.5841` on `S2`, so the measurement still discriminates when two things are genuinely
different.

Switched to `Double` accumulation. Two regression tests reproduce the worst cancellation conditions
(bright area, low contrast, 124×124) and both are mutation-checked: the `Float` version scores
`1.0148` and **picks the wrong target**.

This bug is also why `RG-23` exists: with the default search region hugging the spot just cropped,
there was never a chance to grab an identical patch in another corner of the screen.

## Session D — Recording

### A dedicated target instead of Chrome, and why it had to change

`D10`…`D12` are the cases that **replay** a recording. Before running any of them you have to know
where the recording will click — and it turns out you cannot know that if the target is Chrome.

A recording keeps only the **bundle id** of the Locked application. At run time,
`ScenarioSystemBridge.processIdentifier` takes `runningApplications.first { $0.bundleIdentifier == …
}`, and then `WindowAnchor.focusedWindowFrame` takes that process's **focused window**. The machine
was running two Chrome processes — the user's everyday Chrome and the rig's isolated Chrome profile
— so `first` returned the **user's** process, and the recording would have clicked into their
logged-in window. The checklist forbids exactly that, so `D10` could not be run as it stood.

Instead the rig builds a **dedicated target**: a small AppKit application packaged twice as
`com.local.biaA` and `com.local.biaB`, each window a 3×3 grid of tiles `T1`…`T9`, logging every
mouse event it receives to a TSV file with screen coordinates. This target can be **named
unambiguously** by bundle id, allows point-exact two-sided measurement, and can be moved at will for
`D12`.

See `Findings outside the checklist — session D` below: two processes sharing a bundle id is not
only the rig's problem.

| # | Do | Expect | Proves | Result |
|---|---|---|---|---|
| D1 | Press `⌥⌘R`, click a few places in TextEdit, press `⌥⌘R` again | A new scenario appears, named after the application and the time | `RC-1` `RC-16` | **Pass** — the new scenario appeared at once, named after the target application and the time (`BiaKiemThuA 12/09 09:58`), and the editor window opened on it |
| D2 | Watch the floating panel while recording | Shows the number of Steps recorded and the `⌥⌘R` reminder | `RC-3` | **Pass** — the panel showed *"Đang ghi thao tác"*, the operation count rising with each click, and the reminder *"Kết thúc bằng ⌥⌘R"*. Read from a **screenshot**: the panel's contents do not appear in the Accessibility tree (see the findings below) |
| D3 | Press `⌥⌘R`, **click only inside Clickify's own window**, finish | No scenario is created; it reports "nothing was recorded" | `RC-2` `RC-17` | **Pass** — no scenario was created and the popover correctly reported that nothing was recorded |
| D4 | Record one double click | **One** Step with `count = 2`, not two Steps | `RC-7` | **Pass** — a single Step with `count=2` |
| D5 | Record one ~2-second long press | `holdMs ≈ 2000` | `RC-6` | **Pass** — `holdMilliseconds=2967`, matching the rig's real hold of ~2.87 s (not a round 2000: the number to compare against is what the tool actually held, not what it was asked to hold) |
| D6 | Record one text selection by dragging | One `drag` Step, not a burst of clicks | `RC-8` | **Pass** — one `drag` Step, both measurements agreeing exactly: `(800,430) → (990,510)` |
| D7 | Record a trackpad scroll burst, **scrolling to the end and letting inertia run** | **One** scroll Step, not cut in two when the inertia changes sign | `RC-9` | **Pass** — reproduced the exact event shape of a trackpad flick (touch phases `began/changed/ended`, then inertia phases `begin/continue/end`, with a **sign-flipping** tail `-1 -2 -1`, 17 events 16 ms apart) and injected it into the session event stream. The recorder produced **1 Step**, `scroll deltaY=11`. The 11 is in **lines**, not the 92 pixels emitted: the recorder reads `scrollWheelEventDeltaAxis1`, and `MouseEventEmitter.scroll` replays with `units: .line` — the same unit on both ends, so the round trip does not drift |
| D8 | Record: click, **wait 5 seconds**, click | The first Step has a delay of about 5000 ms | `RC-10` (ADR-0004) | **Pass** — the first Step's delay is `5330 ms` |
| D9 | Look at the last Step of every recording | Delay = 0 | `RC-11` | **Pass** — every recording made this session has a last-Step delay of `0 ms` |
| D10 | **Replay** the D1 recording | Repeats exactly what was recorded | `RC-13` | **Pass** — two-sided, point for point: Clickify emitted `(270,220) (720,420) (870,220)` and the target received exactly `T1 T6 T3` at exactly those three coordinates |
| D11 | Record a session touching **two** applications (TextEdit then Finder) | A warning that the recording spans 2 applications; Targets are absolute coordinates | `RC-14` | **Pass** — recorded 3 clicks across two targets. The scenario: locked application = *none*, all 3 Steps absolute `screenPoint`, the name falling back to `Bản ghi 12/09 10:00` rather than an application name. The popover showed exactly one orange line: *"Bản ghi trải trên 2 ứng dụng nên dùng toạ độ tuyệt đối; các bước sẽ trượt nếu cửa sổ dịch chuyển."* |
| D12 | After recording, move the TextEdit window and replay the single-application recording | The operations **follow the window** | `RC-13` | **Pass** — moved the target window from `(120,88)` to `(300,240)`, i.e. `+180/+152`, then replayed D10's scenario. Every click shifted by exactly that: `(450,372) (900,572) (1050,372)`, and the target still received `T1 T6 T3`. The three Steps anchor to **three different corners** (`topLeft`, `bottomRight`, `topRight`), so this also tests `WindowAnchor.offset` picking the nearest corner |
| D13 | Type on the keyboard while recording | Keystrokes **do not** enter the scenario | `RC-4` `SF-6` (ADR-0003) | **Pass** — typed the string `matkhau` outright while recording: the scenario came out with **0** keyboard Steps, and the string `matkhau` **appears nowhere** in the file on disk |
| D14 | Open System Settings → Privacy → **Input Monitoring** | Clickify is **not** in the list | `SF-6` | **Pass** — the app's only `CGEvent.tapCreate` registers a mask of **mouse and scroll only**; the two remaining uses of `keyDown` are `addLocalMonitorForEvents` (which only sees keys delivered to the app's own windows, needs no permission, and exists to catch Esc). `Info.plist` has **no** key requesting Input Monitoring; the installed binary **does not reference** `IOHIDRequestAccess`/`IOHIDCheckAccess`. **Seen with my own eyes**: after a whole test session with dozens of recordings, the Input Monitoring list is still **`No Items`** — macOS has never registered Clickify as something watching the keyboard |

### Findings outside the checklist — the two-display session

The machine used here: built-in `(0, 0, 1512, 982)` at **2x**, and an LG FULL HD
`(1512, -98, 1920, 1080)` at **1x**. Both `NSScreen` and `CGDisplayCopyDisplayMode` agreed on each
scale, so `RG-24`'s chain never had to fall past its first step — what that requirement buys here is
the removal of a guess, not a correction.

**How small the margin between two near-identical tiles really is, and what that costs.** `C3`
cropped its Template from `DUP-B` and clicked `DUP-A`, twice, deterministically. Chasing it produced a
more useful answer than the case was written for.

The tiles differ genuinely — `DUP-B` means `(147, 199, 241)`, `DUP-A` means `(140, 193, 240)`, being
`#2b93e8` against `#1e88e5`. But they are flat colour, and **normalised** cross-correlation subtracts
the mean and divides by the standard deviation, so a uniform shade difference is exactly what it
removes. What survives is only the antialiased edge, and it is worth about **3·10⁻⁵**:

| Measured | `DUP-B` | `DUP-A` |
|---|---|---|
| Luma grey, 1x | `1.000000` | `0.999970` |
| Through `GrayImage`'s own 8-bit DeviceGray conversion | `1.00000000` | `0.99996443` |
| In-app Template (SCK, 2x) against captures of both | `0.999994` | `0.999960` |

That is precisely the figure the `correlation` comment already records — *"two nearly identical
buttons really differ by about 4·10⁻⁵"* — and the reason that comment exists is that accumulating in
`Float` produced ~2·10⁻³ of noise, forty times the gap. Accumulating in `Double` is what makes the
margin visible at all.

**The matcher is not at fault.** Handed the very same two images offline, `TemplateMatcher.bestMatch`
returns `origin=(108, 21)` — `DUP-B`, the right tile — with `score = 1.0`. The failure only appears in
the running app.

The difference is where the Template came from. `C3`'s was cut with `/usr/sbin/screencapture`, while
the haystack the app matches against comes from **ScreenCaptureKit**. Everything from one path agrees;
mixing paths moves the numbers by more than 3·10⁻⁵ and the wrong tile wins. `I2`, whose Template was
cropped by the app itself, picks correctly.

So `C3` as run here **does not show a defect**: it exercised a route no user can take, because the
only way to make a Template is the app's own crop. What it does show is how little headroom there is.
Any future change that perturbs the capture path — a colour space, a downsample, a resize — can flip a
near-duplicate choice without failing anything else, and `01-scope.md` names telling near-identical
things apart as a requirement. A cheap guard would be to compare mean brightness alongside the
correlation score, since that is the one thing the tiles differ in and the one thing `RG-8`'s measure
deliberately ignores.

**A test that cost more than it proved.** `C3` had to be rebuilt as an offline unit test and measured
three separate ways before its own validity could be judged. A manual case that crops its Template by
any means other than the app's own button is not measuring the app.

**The popover's Accessibility tree is readable after all.** Session A recorded "63 elements with
empty `role`, `name` and `value`", and every session since has clicked the popover by coordinates
measured from a screenshot. It now exposes `AXPopUpButton desc="Kịch bản"`,
`AXButton desc="Bắt đầu sau 3 giây"`, `AXLink desc="Soạn kịch bản…"` and the rest, with positions.
A rig built on this would not need colour anchoring at all.

**What a drifting menu bar cost.** The status item moved from `x=906` to `x=872` mid-session, and
later to the external display entirely at `(2828, -95)`, because menu-bar extras come and go. Every
run in between clicked the wrong icon, the popover never opened, and `C15` was recorded as a failure
that had never run. Looking the item up through Accessibility on every run, and refusing to click
Start unless the popover is confirmed open, is not optional tooling — without it the session
produces confident wrong answers.

### The I-series, re-run against the two-pass matcher

`RG-25` changed how every Template is matched, so the practice targets were re-run to check the
ordinary path had not been disturbed. The page reports which target it received, independently of
what the observer saw Clickify emit.

| # | Result |
|---|---|
| I1 | **Pass** — `S3@(207,433)`, the centre the page declares |
| I2 | **Pass** — `DUP-A@(551,433)` with `DUP-B` 76 points away. The near-identical pair still resolves correctly, which is the case most at risk from a blurrier second comparison |
| I4 | **Pass** — `G2@(109,529)`. The earlier session recorded `G1`; the difference is not the tie-break changing but the **Search region** doing its job. `G1` spans `x=24…84` and the region starts at `x=60`, so the leftmost circle **inside the region** is `G2`. Incidentally re-proves `C11` |
| I5 | **Pass** — `TINY@(214,638)`, exact |
| I6 | **Pass** — `NOISY-ICON@(100,671)` against a declared `(100,672)`, the same 1 px rounding the earlier session recorded |

The window was at a **different position** from when the Templates were cropped (offset `(0,154)`
rather than `(60,74)`), so these also re-prove `RG-8`: a Template is found again after the interface
has moved.

**Timing (`C13`)**: measured from the rig's click on Start to the click Clickify emitted, minus the
3-second countdown, with all three clicks separated by source pid: **I1 554 ms, I4 562 ms, I5 469 ms,
I6 491 ms**. The band recorded before this change was 560–710 ms. Native-first did what it was
supposed to: the ordinary path never reaches the second pass and is not slower.

### Findings outside the checklist — session D

#### A recording names the **application**, never the **window**

Found while preparing `D10`.

`Scenario.applicationBundleIdentifier` is everything a recording knows about where it will click.
At run time there are two narrowing steps, and neither was narrow enough:

1. `ScenarioSystemBridge.processIdentifier` takes `runningApplications.first` matching the bundle
   id. With two processes sharing a bundle id — two Chrome profiles, two copies of a game open side
   by side — which one you get is luck of the launch order.
2. `WindowAnchor.focusedWindowFrame` takes that process's **focused** window. With one process and
   ten windows, the scenario clicks whichever window happens to be in front at run time, not the
   window that was recorded.

The consequence is not mild: a scenario recorded on a scratch window can replay onto the logged-in
window, at exactly those coordinates, on whatever happens to be sitting there. `RC-14` already warns
when a recording spans **two applications**; spanning **two windows of the same application** gets
no warning at all, because the recording cannot tell.

This is exactly why `D10` could not be run with Chrome as the target (see the top of session D).

For the primary use case — games — there is usually one process and one window, so this stays
silent. For browsers and editors it does not.

**Fixed** (`DM-23`): a recording now also remembers the **Anchor window title**, taken at the moment
of the first operation rather than at the end. At run time it prefers the process that has a window
with that title, and within that process prefers that window; a mismatch falls back to the old
behaviour. Six regression tests, three of them mutation-checked (dropping the title from the save,
dropping it from what is passed down to the anchor resolver, and removing the field from the model
entirely — all three turn tests red).

What has no automated test is the two `live` closures that genuinely ask Accessibility
(`ScenarioSystemBridge.processIdentifier` and `WindowAnchor.frame(ofProcess:preferringTitle:)`), in
the same group as every other `live` seam in the project. They were **measured by hand**, and the
measurement reproduces the exact situation that produced the bug:

Two **processes** sharing the bundle id `com.local.biaA`, with windows named `Bia kiem thu A2` at
`(20,33)` and `Bia kiem thu A` at `(560,298)`. Process `A2` launched **first** so it heads
`runningApplications` — exactly what the old logic would pick — and was brought to the front before
running. Two clicks were recorded in window **A**:

| | Window A received | Window A2 received |
|---|---|---|
| recording with `windowTitle` | `T2@(1100,500)`, `T4@(800,620)` | *(nothing)* |
| same recording, `windowTitle` **removed** from the file | *(nothing)* | `T2@(560,235)`, `T4@(260,355)` |

The bottom row is the behaviour before the fix, and also the behaviour that remains for **older
recordings**: with no title there is nothing to choose by. An old recording has to be re-recorded to
benefit from `DM-23`.

#### Neither the recording panel nor the popover's contents can be read through Accessibility

Not a bug, but the thing that slows every measurement session down. `entire contents` returns empty
for both windows — SwiftUI content inside a `MenuBarExtra` and inside an `NSPanel` does not appear
in the AX tree. Every claim in this document about text shown on those two surfaces rests on a
**screenshot**, not on the AX tree.

#### A scroll event follows the **coordinates written into the event**, not the real cursor

Measured in `D7`, confirming the comment in `MouseEventEmitter.scroll`. Put the real cursor
somewhere with no window under it and emit a scroll event carrying coordinates inside target A:
**target A** received it, while target B — the application most recently brought to the front —
received nothing. That is what lets a replayed scroll Step avoid moving the user's cursor.

## Session E — Storage and data corruption

| # | Do | Expect | Proves | Result |
|---|---|---|---|---|
| E1 | `echo "broken" > ~/Library/Application\ Support/Clickify/Scenarios/<uuid>/scenario.json` then reopen the app | The app still runs, only that scenario is missing | `ST-9` | **Pass** — before the damage: the app saw all **9 of 9** scenarios with no warning. After: it saw **8**, exactly the damaged one gone, and showed *"1 kịch bản có vấn đề"* with a tooltip naming the directory. The broken file and its `templates/` were **left intact** — the app does not clean up what it cannot read |
| E2 | Change one scenario's `"schemaVersion"` to `99`, reopen the app | The scenario appears but **read-only**, with no data lost | `ST-12` | **Pass, but it exposed a data-loss bug** — the scenario showed the right name, the edit panel was replaced entirely by a lock message (no name field, no repeat field, no Step list), and the file on disk was untouched. But the **Duplicate** button was still clickable → see below |
| E3 | Add an unknown field to `scenario.json`, reopen | Ignored, no error | `ST-10` | **Pass on all three counts** — added an unknown field at the root *and* inside a Step, removed `delayMillisecondsAfter`, set `repeat = 999,999,999` and `threshold = 7.5`. The scenario loaded normally with no warning; the app showed `×1,000,000` and `threshold 1.00` — clamped correctly. The file on disk was **not overwritten**: out-of-range values are clamped in memory only |
| E4 | Delete a scenario that uses a template, check the directory | The whole directory disappears, `templates/` included | `ST-3` | **Pass** — duplicated `I6` (which has `NOISYICON.png`) then deleted the copy: the whole directory went, `templates/` included. Building this check is what exposed the other half of `ST-3` being broken → see below |
| E5 | Change a Step's template a few times, count the files in `templates/` | Old files are cleaned up, the directory does not grow | `ST-12` | **Pass** — duplicated `I1` then re-cropped the template **three times** over three different areas. After each one `templates/` held **exactly one** file, renamed each time (`S3.png` → `50DD6B9E.png` → `2956631F.png` → `B93CF474.png`), with the old one gone immediately. `ScenarioStore.save` calls `removeUnused(keeping:)` after every write, so no orphan file is left behind.<br><br>This also confirmed `RG-23` end to end: the last region drawn was `(60,520,220,140)`, padding `min(160, max(48, 220/2)) = 110`, expanding to `(-50,410,440,360)`, clipped to the screen as **`(0,410,390,360)`** — exactly the numbers in `scenario.json`. And `ST-3` in the duplication direction is right too: the copy of `I1` came with `S3.png` in its own `templates/` |

### Findings outside the checklist — session E

Two **data-loss** bugs from the same family: a scenario that looks normal but is hollow.

**Duplicating a read-only scenario produced an empty one.** What loads for a scenario with a newer
`schemaVersion` is only its `id` and `name` — the Steps live in the part of the JSON this build
cannot decode. The Duplicate button was still clickable, and wrote straight to disk a scenario
carrying the **original's name**, the **current** `schemaVersion`, and **no Steps at all**.
Measured: one press produced `"I6 tren nen nhieu (bản sao)"` with 0 steps, and the editor said
*"Kịch bản chưa có bước nào."* The danger is that the copy looks perfectly normal: the user believes
they have rescued their data from the read-only lock, deletes the original, and loses everything.
Exactly what `ST-12` exists to prevent. Now blocked in the store and the button is disabled too;
deleting is still allowed because that is something the user does on purpose.

**Duplicating forgot the templates.** [ADR-0005] chose *"one directory per scenario, with templates
duplicated"* precisely so that deleting is deleting a directory and **duplicating is copying a
directory**. The delete half was right (`E4`); the duplicate half copied only `scenario.json`: the
copy kept the template filenames but its `templates/` was empty, so every recognition Step in it
broke immediately. Before `UI-19` the interface showed only a filename, with no way to tell the file
did not exist. Fixed; rechecked on the real app: the copy of `I6` now carries `NOISYICON.png` in its
own directory.

---

## Session F — Interface languages

These need a real `.app` with its `.lproj` directories installed, and a real screen to draw on.

Part of `F2` turned out **not** to be a judgement call after all: "is this label wider than the box"
is arithmetic, and it is now a test (`UI-24`, `UI-25`). What is left for a person is the rest of it —
whether a line wraps somewhere sensible, and whether the sentence is one a speaker of that language
would write.

| # | Do | Expect | Proves | Result |
|---|---|---|---|---|
| F1 | Open the popover, switch the language picker from *English* to *Tiếng Việt* | Every label changes **at once**, with no relaunch and no flicker of raw keys. The popover stays open | `LC-6`, `LC-7` | **Pass** — the same process (`pid 83859`) and the **same popover window** (`id 370599`, `340×412` before and after), so nothing relaunched and nothing reopened. All 16 labels read back through OCR changed together: `Start in 3 seconds` → `Bắt đầu sau 3 giây`, `Launch at login` → `Khởi động cùng MacBook`. No raw key appeared at any point |
| F2 | Switch to *Español*, then walk the popover **and** the editor window | No label is clipped, wrapped mid-word or squeezed to nothing — the popover is a hard `340` points wide and the editor's middle column about `380` (`UI-18`) | `LC-1` | **Fail, then Pass** — three defects, all fixed in this commit: the unit column cut `veces` to `ve…` **and `times` to `tim…`**, so the default language was clipped too (`UI-24`); `Capturar otra vez…` read `Capturar otra…` on the Template row (`UI-25`); and the popover did not shrink going Simple → Scenario (`UI-23`, reported separately, not a translation defect). Re-run after the fixes: both popover modes and the editor's three columns are clean in Spanish. Two things still truncate and **neither is a label** — a Scenario's own name in the `240`-point sidebar, and a Step summary carrying four coordinates. Both are data, and both truncate the same way in every language |
| F3 | Switch to *日本語*. Now find the way back to *Tiếng Việt* **without reading anything else on screen** | The picker lists `English`, `Tiếng Việt`, `中文（简体）`, `日本語`, `Español` in their own scripts, so the row is recognisable to someone who cannot read the interface around it | `LC-8` | **Pass** — with the interface in Japanese the picker reads `システムに従う`, `English`, `Tiếng Việt`, `中文（简体）`, `日本語`, `Español`. Only the *follow the system* row is translated; the five names stay in their own script (`LC-8`). Read twice over, from a screenshot and from the Accessibility tree, then clicked `Tiếng Việt` without reading anything else on screen |
| F4 | Record a scenario while the interface is English, then switch to Vietnamese and reopen the editor | The saved name still reads `Recording 2026-…`, unchanged. New scenarios created afterwards are named in Vietnamese | `LC-11`, `RC-20` | **Pass**, and the other half checked too. Recorded in English: `TextEdit 2026-09-18 17:05` and, across two applications, `Recording 2026-09-18 17:06` — `RC-16` names it after the Locked application when there is one, and only otherwise uses the translated word. Switched to Vietnamese: both names unchanged on disk and in the list, while the count beside them became `3 bước`. Created two new Scenarios there — `Kịch bản mới`, `Kịch bản mới 2` — then switched back to English: the names **stayed Vietnamese** and only `0 steps` changed. That is `LC-11` in both directions |

`F2` is the reason `es` was taken knowing it stretches the layout — see
[ADR-0010](adr/0010-strings-files-and-a-live-bundle-swap.md). `UI-18` recorded a previous occasion
where a fixed-width column squeezed a label to zero points; `F2` found the same shape of failure
twice more (`UI-24`, `UI-25`). Three times is not bad luck, which is why both are now under test.

### Findings outside the checklist — session F

- **`F2` failed on the default language, not only on Spanish.** The case was written expecting Spanish
  to stretch the layout, and it did. What it was not expecting is that the same `30`-point box had been
  clipping English `times` to `tim…` since the day the interface stopped being Vietnamese — `lần` is
  `18` points and fitted, `times` is `33` and never did. The case that was predicted to fail found a
  defect **nobody was looking for**, in the language everyone sees first.
- **The popover's empty band was not a translation defect at all.** It was reported from use, and it
  reproduces in every language: `MenuBarExtra(.window)` never gives back height (`UI-23`). Session F is
  where it was measured, because measuring popover geometry is what session F was already doing.
- **`ViewThatFits` does not do what the Template row needed.** An `HStack` containing a `Spacer()`
  reports that it fits at any proposed width, so the one-line candidate always won. Recorded in
  `UI-25`, because the next person to reach for it will reach for it for the same reason.
- **`RC-16` names a recording after the application, not after the word "Recording".** The case as
  written expected `Recording 2026-…`; a recording confined to one application is named
  `TextEdit 2026-09-18 17:05`, and only one spanning several falls back to the translated word. That
  branch is the one that proves `LC-11`, so `F4` now drives both.
- **Two things truncate that are not labels** — a Scenario's name in the `240`-point sidebar, and a
  Step summary carrying four coordinates. Both are data rather than interface text, both truncate
  identically in all five languages, and widening a column to fit an arbitrary name is not a thing a
  layout can promise.

### Notes on how session F was run

The rig from sessions A–E, plus two additions and one correction:

- **OCR as the measurement.** Accessibility hands back the string a control *holds*, which is exactly
  what a clipping test must not trust: `AXValue` still reads `Capturar otra vez…` while the screen
  says `Capturar otra…`. So every claim about clipping here comes from `VNRecognizeTextRequest` over a
  screenshot, with `usesLanguageCorrection = false` (`LC-13`) — what is **drawn**, not what is stored.
- **`screencapture -l <windowid>`** for the editor, after a picture-in-picture window from another app
  landed on top of a screen-region capture and was read as if it were the editor's content.
- **The popover-open guard needed both proofs.** `findstart` looks for the accent-blue Start bar, and
  it is not accent-coloured while Start is disabled; the window list stopped reporting the panel at all
  after one app restart. Either alone aborted a run that was fine. The guard now accepts either.

One thing went wrong and is worth writing down: a language-picker script picked *the last*
`AXPopUpButton` in the tree, which with the editor window open is a Step's **On timeout** picker, not
the language picker. It opened the wrong menu twice. Nothing was changed — the script looks for a row
by name and escapes when it is not there — but the fix is to identify the control by **what its menu
contains**, never by its position in the tree.

There is deliberately **no** case here for "the app appears in System Settings → Language & Region →
Applications". Whether an `LSUIElement` agent is listed there was never established, and `LC-6`
exists so that the answer does not matter.

---

## Recording results

When a case fails, record **the case number, what you saw, and the requirement identifier**. Those
three facts are enough to trace straight from the symptom to the relevant piece of spec and the
relevant piece of code.

---

## How session A was run

Nothing was judged by eye. Two small tools were built:

- **The observer** — a listen-only `CGEventTap` logging every mouse event to TSV with its timestamp,
  coordinates, `clickState` and **source pid**. `pid=0` is the user's real mouse, `pid=<n>` is an
  event synthesised by that process, which separates exactly what Clickify emitted.
- **The clicker** — emits `mouseMoved` + `mouseDown`/`mouseUp` at a coordinate, to drive the
  interface like a user.

Two things had to be worked around, and session A's results have to be read with both limits in
mind:

- **The SwiftUI popover's AX tree has no readable attributes** — 63 elements with empty `role`,
  `name` and `value`. So it has to be clicked by coordinates measured from a screenshot. The editor
  window is the opposite: a real `NSWindow`, where AX works well.
- **The scenarios for A4…A9 were written straight to `scenario.json`** using the app's own encoder,
  rather than built in the editor window. That also checks `ST-2` and `ST-5`…`ST-8`, but it does
  **not** check `UI-9` (drag to reorder, add/duplicate/delete a Step) or `UI-10` (the detail panel).
  Those two had only been **seen** to exist, not pressed.
