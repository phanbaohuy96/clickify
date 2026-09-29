# 12 — The Scenario map (Android)

Slice A5. The **Overlay** is where a **Scenario** is built, and it can only show what is on the screen
in front of it: the **Marker**s over one application, one screen at a time. Nothing shows a whole
**Scenario** at once — where every **Step** acts, in what order, what each one looks for and what
happens when it does not find it. The **Scenario map** is that: a read-only, fullscreen phone preview
made from Clickify's own screens, which plays the **Scenario** through by itself (**Playback**).

Requirements are numbered `MP-*`. It has no macOS counterpart; the term is defined in
[`../../CONTEXT.md`](../../CONTEXT.md), together with **Playback**. The map draws one path, because a **Scenario** has one
([ADR-0011](../../../docs/adr/0011-a-scenario-has-no-branches.md)).

## Opening it

**MP-1** `[A5]` The **Scenario** row's ⋮ menu offers **Preview**, and a read-only row (`FS-14`) does
not, because it has no **Step**s. Tapping the row itself still opens the **Overlay** (`OV-26`),
unchanged. The map is an Activity screen and not an **Overlay** window. It reads a **Scenario** and
never builds one (`android/CONTEXT.md`, *Relationships*).

Preview is a menu item rather than the row's tap because the row's tap already means "go and use
this", and that is the far more frequent thing. Looking at a **Scenario** is the rarer question and
can afford a second tap.

## The screen

**MP-2** `[A5]` The screen is immersive: the system bars are hidden, a swipe from the edge shows them
transiently, and they come back on leaving the screen. The whole background is dark in both themes.
The top of the screen, over the dark background, has Back, the **Scenario**'s name, a caption
`W×H · portrait|landscape` (the orientation rule of `SM-18`) with `↻ ×n` or `↻ until stopped`, and a
compact **Open in Overlay** action at the top right. When the **Scenario** has no profile (no **Step**
has a point, `SM-14`) the frame uses the current display and the caption says the **Scenario** follows
the phone, in the words `SM-18` already uses.

The screen stands for a phone's screen and not for a page of this app, and what is drawn on it has to
be legible over one colour and not two.

**MP-3** `[A5]` The phone frame has a bezel and rounded corners, and is sized to fit between the top
bar and the controls with the **Screen profile**'s aspect ratio. A landscape profile is drawn as a
landscape frame fitted into whatever orientation the phone is in: the screen never forces a rotation.
The frame itself never moves. Zoom and pan act only on what is inside it. There is no screenshot,
because none is stored.

## What is drawn

**MP-4** `[A5]` Every point comes from `Scenario.markers()` (`OV-5` to `OV-9`), so the map and the
**Overlay** can never disagree about where a **Step** acts.
- A tap is one numbered dot. A swipe is a numbered start and an arrowhead end joined by a line. A
  multiTouch draws every path with the same number.
- An arrow runs from each **Step**'s anchor to the next **Step**'s anchor, in **Step** order. A
  zero-length arrow is not drawn.
- There is no arrow back to **Step** 1: a run count is a counter and not a jump (ADR-0011).
- A **Step** without a point (`globalAction`, `setText`, `SM-8`) is a chip sitting on the arrow between
  the neighbouring **Step**s that have a point. It is never given coordinates.
- **Step**s whose anchors are the same pixel share one dot, labelled with every number (`2·5·7`). Two
  taps on the same pixel are the ordinary shape of a double tap, and two dots on top of each other
  would hide one of them.

**MP-5** `[A5]` Each effective search's PNG (`TP-19`, `TP-22`) is drawn at true scale, centred where it
was cropped (`TP-23`), **behind** the dots, so zooming in reveals it. A missing PNG (`TP-29`) is an
outlined box and does not crash. The picture is also shown, at a readable fixed size, in the tooltip
and in the card (`MP-9`, `MP-10`), because the true-scale drawing alone was measured on an emulator to
hide behind its own dot at 1×.

**MP-6** `[A5]` Nothing is clipped. Every chip, badge, tooltip and card is laid out inside the frame,
on screen. It flips side (right to left, below to above) and then clamps, so no text is cut at the
frame's edge. A chip that would overlap a dot is pushed off it by a fixed on-screen distance and not a
raw-pixel one.

Round 1 of this screen was measured on an emulator with a trailing chip covering dot `1·5` and a
**Guard** badge cut at the right edge.

**MP-7** `[A5]` **Playback** takes the path where every **Guard** (`TP-24`) holds and every
**Template** is found where it was cropped. It never pauses to ask, never offers a "not found" toggle,
and never claims a **Step** would succeed. For the current **Step** only, a search or **Guard** that
gives up is drawn without a branch (ADR-0011, `TP-25`):
- for `onTimeout = SKIP_STEP`, a **dashed** line goes around the **Step** and rejoins its outgoing
  arrow, and never reaches any other **Step**;
- for `STOP_SCENARIO`, a short stub ends in a ■ stop mark.

The tooltip says the same in words. The dashed line goes *around* the dot and rejoins the same path: a
line to the **Step** after the next would be "skip the next 3 Steps if not found", which ADR-0011
names as the tempting one and forbids.

## Playing it

**MP-8** `[A5]` **Playback** is a sequence of beats.
- A **Step** beat lasts 1500 ms at 1×. Its dot or chip lights up and its tooltip shows.
- A travel beat lasts 800 ms at 1×. The arrow to the next **Step** draws progressively, with the
  **Step**'s real delay as a label at its midpoint (`wait 200 ms`), which fades when the beat ends.
- Real delays are only shown and never waited.
- A **Step** with `repeat > 1` pulses up to 3 times and shows `×n`. It is never repeated n times.
- A swipe or multiTouch **Step** moves a finger mark along its path or paths during its **Step** beat.
- A chip **Step** has its own **Step** beat, and the travel beats around it follow the arrow it sits on.
- **Playback** stops at the last **Step**. It never loops back to **Step** 1, whatever the run count.

**MP-9** `[A5]` The tooltip is at most three short lines, beside the current **Step** and inside the
frame (`MP-6`):
1. the number and the **Action** summary;
2. only with a search: a 40dp picture, the wait, and the timeout outcome;
3. only with a **Guard**: present or absent, a 40dp picture, and the outcome.

It is kept as short as it can be. Everything else is in the card.

**MP-10** `[A5]` Tapping a dot or chip pauses, makes that **Step** current, and opens a card floating
on the map next to it, inside the frame (`MP-6`). The card shows the **Action** and every parameter
(hold, swipe destination and duration, each multiTouch path, the **Global action** name, the **Set
text** string), repeat and delay, and for the search and the **Guard** the picture, present or absent,
threshold, **Search region** or "whole screen", wait, and the outcome in words. A merged dot shows
every **Step** it holds. The card closes with × or a tap on empty frame. **Playback** stays paused
until ▶ is pressed. Every value is read-only.

**MP-11** `[A5]` Controls sit at the bottom, outside the frame.
- `⏮` previous **Step** and `⏭` next **Step** jump to that **Step**'s beat and do not change
  play/pause.
- `▶/⏸` plays and pauses.
- `↺` replays: back to **Step** 1, then play.
- A speed control cycles 0.5× / 1× / 2× / 3× / 5× and scales every beat.
- The text `Step 3/6` says where it is.
- At the end, ▶ is replaced by ↺.

**MP-12** `[A5]` Opening the screen plays from **Step** 1. When the system animator scale is 0
(*Remove animations*), it opens paused on **Step** 1 instead, and plays only when asked.

**MP-13** `[A5]` Pinch-zoom works from 1× to 5× and pan works while zoomed. Dots, labels, tooltips and
the card keep their on-screen size, and a tap hits the right dot at any zoom. While playing and zoomed,
the camera follows the current **Step** by centring it. A manual pan or zoom stops the following until
▶ is pressed again.

## Leaving

**MP-14** `[A5]` **Open in Overlay** does what tapping the row does (`OV-26`). Back returns to the
list and restores the system bars.

**MP-15** `[A5]` **Playback** is not evidence. Nothing on the screen says or implies that the
**Scenario** was tested or works (`android/CONTEXT.md`, *Playback*).
