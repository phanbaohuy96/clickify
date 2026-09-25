# 01 — Scope and slices

## The problem

Clickify can currently emit only **one** kind of operation (a left click) repeated N times at
one point. The goal is to grow it into a **Scenario** runner — an ordered sequence of varied
operations — and to let scenarios be created by **recording** the user's real work.

### No application is the use case

Clickify encodes **no knowledge of any application**. There is no list of supported apps, no
per-app rule, and no scenario shipped in the box. The user supplies the points, the order, the
timing and the **Template**s; the app supplies faithful execution and gets out of the way.
Anything that could only ever be true of one application belongs inside a **Scenario**, never in
the code.

What the user automates is therefore not knowable here, and guessing at it is how a general tool
turns into a bad special one. What *is* knowable, and what this specification is answerable to, is
that the tool has to be **easy to author with** and **pleasant to reach for** — because a
scenario that takes longer to build than to do by hand will never be built.

### Three pressures, pulling against each other

The design is decided by three pressures rather than by any use case. They matter because they do
not merely differ — they **conflict**, and every serious decision in this document is a ruling
about which one wins where.

- **Repetition** — the same operation many times over, hands-free. Wants a high repeat count, a
  steady interval, and a **Stop** that always works, including mid-press.
- **Speed and exact order** — a short sequence that has to land fast and in the right order,
  because something else is racing it. Wants the smallest possible gap between **Step**s and
  **no recognition at all**: recognition costs far more time than the operation it would guard.
- **Certainty** — act only when the screen proves it is safe to act. Wants recognition **before**
  the press, and a defined behaviour when the target is not found (`DM-16`), so that a press on
  the wrong screen does not set something else in motion.

Certainty is a **brake**, and a brake is exactly what the racing sequence must not have.
Recognition is therefore never a global mode and never a default: it is chosen per **Step**, by
the user, with its cost visible at the point of choosing.

Seen this way recognition has **two** jobs, not one. It **locates** something whose position is
not known in advance — and it **withholds** an operation whose position is known perfectly well
but whose screen might be the wrong screen. The second job is what keeps a misfire from doing
damage, and it is why `skipStep` (`DM-16`) is not a minor option on a dropdown.

### What games taught, and what still holds

Most of the above was learned while automating games, and several requirements exist because of
that. They stay — but as **general** consequences, not as a bet on games:

- Things worth aiming at often carry **no text** — an icon, a drawn button, an item slot. **Text**
  recognition (`DM-15`) is therefore the secondary route; **template matching** (`DM-14`) is what
  carries the feature. That is also why the `I1`…`I8` practice targets in the
  [manual tests](../manual-e2e-tests.md) deliberately contain no text.
- Interfaces are full of **near-identical** things — five item slots in the same frame, two
  buttons differing only in shade. Matching has to tell them apart; "close enough" is not good
  enough.
- A window often **renames** itself while you work. That is why `DM-23` treats the window title as
  a **preference** rather than a hard requirement.
- What you aim at almost always stays where it was cropped, so the default search region hugs that
  spot (`RG-23`) instead of scanning the whole screen.

## Slices

Every slice leaves a runnable, installable app. The order is settled; see
[ADR-0001](../adr/0001-screencapturekit-and-min-macos-14.md) for why recognition was pushed last.

### Slice 1 — The Scenario framework  ✅

Replace the runner beneath the Start button without changing what the user can do.

- The **Scenario / Step / Action / Target** model
- A single Scenario runner; **Simple mode** builds a one-step Scenario
- **Target**: at the cursor, absolute point
- **Action**: click(button, count, hold), scroll, move
- Directory-based storage; the editor window
- Release the mouse button on stop (`SF-1`)

Done when: everything possible in version 1.2.0 is still possible, and runs through the new
runner.

### Slice 2 — Window anchoring, drag, typing  ✅

- **Target** relative to the **Anchor window** by nearest corner
- **Action**s drag and type
- Bring the **Locked application** to the front before typing (`SF-4`)

### Slice 3 — Recording  ✅

- Capture system-wide mouse events and infer **Action**s from them
- Automatically raise **Target**s to **Anchor window**-relative when a whole session stays inside
  one application

### Slice 4 — Target recognition  ✅

- Raise the minimum to macOS 14, capture the screen with ScreenCaptureKit
- **Target** by **Template** (pyramid matching) and by text (Vision OCR)
- Search region, threshold, timeout, behaviour when nothing is found

### Slice 5 — Two-scale matching and per-key typing  ✅

Not in the original plan; both came out of running the manual tests on real hardware.

- Match **Template**s at two scales so they survive an external display
  ([ADR-0008](../adr/0008-match-templates-at-two-scales.md))
- Type an all-ASCII string **key by key**, so a game sees one key per character
  ([ADR-0009](../adr/0009-type-ascii-key-by-key.md))

### Slice 6 — Interface languages

Preparation for releasing the source. The interface was written entirely in Vietnamese; the
documentation and the log messages were translated earlier, the interface was not.

- `en` as the development language, plus `vi`, `zh-Hans`, `ja`, `es` ([09](./09-localisation.md))
- Switching language inside the application, without a relaunch
- A boundary drawn between **interface text** and **names written into `scenario.json`**
- Layouts that hold five wordings rather than one (`UI-24`, `UI-25`), under test

Done when: adding a sixth language is adding one directory and no Swift.

## Out of scope

Stated explicitly so it does not get proposed again:

- **Control flow inside a Scenario.** No `if`, no branches, no conditional loops, no jumps, no
  subroutines. "Wait until the button appears" is the timeout on resolving a **Target**, not a loop.
  The boundary is one rule — **a Step may decide its own fate, never another Step's** — which is why
  `skipStep` (`DM-16`) is allowed and "skip the next three Steps" is not. `if`/`else` is written as
  two guarded **Step**s instead. This is a product position and not a postponement; the reasoning,
  the rejected alternatives and what it costs are in
  [ADR-0011](../adr/0011-a-scenario-has-no-branches.md).
- **Keyboard capture.** See [ADR-0003](../adr/0003-no-keyboard-capture-when-recording.md).
- **Capping idle time while recording.** See
  [ADR-0004](../adr/0004-recordings-keep-real-timing.md).
- **Running Scenarios on a schedule** or triggering them from another application.
- **Syncing Scenarios between machines.**
