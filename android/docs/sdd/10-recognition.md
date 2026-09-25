# 10 — Recognition (Android)

Slice A3. macOS states the same feature in [`07-target-recognition.md`](../../../docs/sdd/07-target-recognition.md);
this document is not a translation of it. Two of its hardest problems do not exist here, one of its
easy ones is hard here, and the differences are the reason this is written out rather than
cross-referenced.

Requirements are numbered `RC-*`. `RG-*` always means the macOS document.

## What a Template is

**TP-1** `[A3]` A **Template** is a rectangle of the display's raw pixels, cropped by the user,
stored as a PNG inside the **Scenario**'s own directory, and named by an identifier rather than by
what it shows.

It is pixels rather than a description on purpose. The use case is games ([landscape](../landscape.md)),
whose buttons are drawn rather than laid out, so there is no text to read and no node to find —
the out-of-scope note on node **Target**s in [01](./01-scope.md) is the same point from the other
side.

## Taking a frame

**TP-2** `[A3]` Every frame comes from `AccessibilityService.takeScreenshot()`. MediaProjection is
not used at all — [ADR-0016].

**TP-3** `[A3]` A frame arrives in the display's raw pixels, which is the coordinate space a
**Screen profile**, a **Marker** and a **Gesture** are already in (`SM-11`, `OV-31`). Nothing is
scaled and no factor is applied.

This is the easy half of macOS `RG-2` and `RG-24`, and it is easy for a structural reason: a Mac has
several displays with different scale factors inside one coordinate space, and Android has one
display whose pixels are the only pixels there are. If a frame ever arrives at a size other than
the **Screen profile**'s, the point found in it is mapped back by the ratio of the two rather than
trusted — the ratio is 1 on every device this has run on, and a frame that silently disagreed with
the profile would move every match.

**TP-4** `[A3]` The platform rate-limits `takeScreenshot` to roughly one call every 333 ms and
refuses the ones in between. A wait therefore polls at 400 ms and is counted in **milliseconds
elapsed**, never in polls — otherwise a device that refuses a call would shorten the wait.

**TP-5** `[A3]` A frame is never reused between two polls of one wait. The whole point of waiting is
to see an interface that **has changed**. Mirrors `RG-3`.

**TP-6** `[A3]` **Clickify's own windows are in the frame and cannot be taken out of it.**

macOS excludes its own windows from a capture (`RG-20`); `takeScreenshot` captures the display and
offers no exclusion. While a **Scenario** runs, the **Marker**s and the panel are already gone
(`OV-11`, `OV-20`), so what is left is the floating control — which can sit on top of the very
thing being looked for.

The consequence is stated rather than hidden, and it is the harmless one: a covered **Template**
**fails to match**, so the **Step** waits and then does what its timeout says. It does not match
somewhere else and press the wrong thing. The control is movable (`OV-14`) and collapsible
(`OV-33`), and that is the answer.

## Cropping a Template

**TP-7** `[A3]` Cropping takes the Overlay off the screen, waits for the window manager to actually
do it, takes **one** frame, and then puts that frame **back on the screen** for the user to drag a
rectangle on.

Cropping from a still rather than from the live screen is the Android answer to macOS `RG-5`, and
it is a better one. There is no window to hide at the moment of capture and therefore no 10 %
darkening to accidentally bake into a **Template**: what the user drags on is the frame itself.

It also answers `RG-4`'s problem — *the thing you want to aim at is usually not on screen while you
are writing the Scenario* — differently. On a phone the user walks to the screen that shows it and
crops there, exactly as recording already asks them to. Importing a **Template** from a picture in
the gallery is deferred, not refused; see *Deferred from A3*.

**TP-8** `[A3]` The rectangle is dragged in display pixels and the **Template** is what is inside
it. A rectangle smaller than 8 × 8 pixels is refused: below that there is not enough left to
correlate against, and the match would be a coin toss with a confident number attached.

**TP-9** `[A3]` The default **Search region** hugs the crop rather than covering the screen:
padding of half the **Template**'s longer side, floored at 48 dp, capped at 160 dp, then clipped to
the display. Clearing it searches the whole screen.

Mirrors `RG-23`, for the same reason and more strongly. What a game asks you to press almost always
stays where it was cropped, and a whole-screen scan is both slower and likelier to find an
identical patch somewhere else.

## Matching

**TP-10** `[A3]` The algorithm is pyramid normalised cross-correlation, ported from
`TemplateMatcher.swift` (`RG-8`): a coarse scan at a downscaled level, then refinement within a few
pixels of each candidate at native resolution.

**TP-11** `[A3]` The downscale level is the cheapest one at which the **Template** keeps at least
half its original contrast (`RG-21`). A fixed level wipes out text, 1-pixel borders and small
checker patterns, and the coarse peak then lands somewhere random.

**TP-12** `[A3]` The coarse scan keeps **8 separated candidates** — non-maximum suppression at half
the **Template**'s size — and refines all of them (`RG-22`). A row of identical buttons is the
ordinary case here, not the exotic one.

**TP-13** `[A3]` The level is bounded above by a ceiling of about 40 million comparisons for one
coarse scan, and when no level both keeps contrast and fits, **the ceiling wins** (`RG-18`).
Narrowing the **Search region** is how the user buys accuracy back.

**TP-14** `[A3]` Correlation accumulates in `Double`, never in `Float`. `Σh² − n·h̄²` suffers
catastrophic cancellation, and at `Float` precision two nearly identical buttons — an everyday
thing in a game — are inside the noise, so the matcher picks between them at random. This is a bug
that happened on macOS and has a test holding it shut here.

**TP-15** `[A3]` A score is a real number `0…1`. The threshold is per-**Step** and defaults to
`0.90` (`RG-9`).

**TP-16** `[A3]` The highest score above the threshold wins; on a tie the topmost, then the
leftmost, so the result is deterministic (`RG-10`).

**TP-17** `[A3]` The **Target** returned is the **centre** of the matched area (`RG-11`).

**TP-18** `[A3]` **Two-scale matching is deliberately not ported**, although [01](./01-scope.md)
listed it.

[ADR-0008] exists because a **Template** cropped on a 2× display has twice the pixels of the same
button on a 1× display, and a Mac has both at once inside one coordinate space. Android has one
display, and [ADR-0013] already refuses to run a **Scenario** whose **Screen profile** is not the
one in front of the user — so a **Template** is always matched against the screen it was cropped
on, at the size it was cropped at. Porting the second scale would double the cost of every failed
match to defend against a case `SM-15` has already blocked.

If a **Scenario** is ever allowed to travel between phones, this is one of the things that comes
back; the out-of-scope note in [01](./01-scope.md) says the same about **Marker**s.

## A Template as a Target

**TP-19** `[A3]` A **Target** is still a point (`SM-11`). What A3 adds is a **Template search**
the **Step** may attach to it: a **Template**, a threshold, an optional **Search region**, a wait,
and what to do when the wait expires.

A modifier rather than a second kind of **Target**, and that is the whole design. With no search,
the point is where the **Step** acts. With one, the point is where it **would** act — which is
where the **Template** was cropped — and the match moves it. So `SM-8` still holds without an
exception, a **Step** still has exactly one point to draw a **Marker** at (`TP-23`), `SM-15` still
has coordinates to check, and rebuilding for another screen (`SM-18`) still has something to clamp.
A sealed **Target** would have had to invent all four of those back.

**TP-20** `[A3]` A found **Template** moves the **Step rigidly**. The centre of the match becomes
the **Step**'s point, and every other point the **Action** carries — a swipe's destination, every
path of a `multiTouch` — moves by the **same delta**.

The alternative, resolving only the start and leaving the rest absolute, turns "swipe this card
away" into "swipe from the card towards a fixed corner", which is a different gesture every time
the card moves. Moving the whole thing keeps the **shape** the user drew, which is what they drew
it for.

**TP-21** `[A3]` A **Template** **Target** has a wait in milliseconds and an `onTimeout` of either
**stop the Scenario** or **skip the Step**, and nothing else. Mirrors macOS `DM-16`.

A wait of zero is legitimate and is the whole of "close it if it is there": one frame, one search,
skip if it is not found.

**TP-22** `[A3]` `globalAction` and `setText` ignore their **Target** (`SM-8`), and therefore
ignore any search attached to it. The editor does not offer one, and the runner discards one it
finds — the same sentence `SM-8` already says about the point.

**TP-23** `[A3]` A **Step** with a **Template** **Target** still draws a **Marker**, and the
**Marker** is drawn **where the Template was cropped**. That is where the **Step** will act if the
match comes back where it was made, and it is the only honest place to put it. The **Marker** says
it is a search rather than a fixed point.

## The Guard

**TP-24** `[A3]` A **Step** may carry one **Guard**: a **Template** that must be **present**, or
one that must be **absent**, before the **Step** runs. It has the same threshold, **Search region**,
wait and `onTimeout` as `TP-21`.

**TP-25** `[A3]` A **Guard** is **not a branch** — [ADR-0011] still holds, and this is the
requirement that keeps it honest. A **Guard** that does not come true has exactly two outcomes, and
both of them were already in the language: the run stops, or this one **Step** is skipped and the
next one runs. There is no jump, no else, and no block.

**TP-26** `[A3]` A **Guard** is evaluated **once per repetition** of the **Step**, before the
**Action**, and before a **Template** **Target** is searched for. Waiting for a condition and then
searching for a **Target** are two waits, and both are the user's numbers.

## Storage

**TP-27** `[A3]` A **Template**'s pixels live at `templates/<template id>.png` inside the
**Scenario**'s directory. `FS-3` copies the whole directory, so duplicating a **Scenario** carries
its **Template**s with it and nothing had to be added for that to be true.

**TP-28** `[A3]` `schemaVersion` 2 is written **only by a Scenario that actually uses recognition**.

`FS-5` raises the version when an older build could not read the file, and a **Scenario** of plain
taps is still exactly a version 1 file. Writing 2 unconditionally would make every **Scenario** on
the device unreadable to the previous build in exchange for nothing.

**TP-29** `[A3]` A **Template** whose PNG has gone missing is a **violation in the editor**
(`SM-17`), so the **Step** cannot be saved and says why. At run time it is treated as a search that
found nothing, which means the `onTimeout` the user chose — not a crash, and not a silent success.

## Limits

**TP-30** `[A3]` Added to the table in `SM-16`, and clamped on the way in from disk by the same
rule.

| Field | Range | Why this bound |
|---|---|---|
| match threshold | 0.50…1.00 | below a half, correlation is noise with a number on it |
| wait for a Template | 0…600,000 ms | ten minutes; past that it is a schedule, which is out of scope |
| Template size | 8 × 8 … the display | `TP-8` at the bottom, the frame at the top |

## Deferred from A3

Stated so the absences are not read as oversights.

- **Finding by text.** macOS has it (`RG-13` to `RG-16`) because Vision is in the SDK. The Android
  equivalent is ML Kit, which is a Play Services dependency in an app that is
  [deliberately not distributed through Play](./01-scope.md), or a bundled model that costs several
  megabytes. It is also the weaker half of the feature for this app's use case: a game's buttons
  are pictures. Reconsider when a real use case has text in it.
- **Importing a Template from the gallery** (`TP-7`). Worth having — it is how you crop something
  that is hard to navigate back to — and it needs a photo picker, which needs an Activity.
- **A Template that matches at several scales** — `TP-18`, and it comes back only if a
  **Scenario** is ever allowed to travel.
- **More than one Guard per Step.** One covers "only if the advert is gone"; two is a conjunction,
  and a conjunction is most of the way to a branch.

[ADR-0008]: ../../../docs/adr/0008-match-templates-at-two-scales.md
[ADR-0011]: ../../../docs/adr/0011-a-scenario-has-no-branches.md
[ADR-0013]: ../adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md
[ADR-0016]: ../adr/0016-screenshots-come-from-the-accessibility-service.md
