# 06 — The Overlay and Markers (Android)

macOS has a menu bar, windows and a cursor. Android has none of those while another application is
in front, so **every** surface Clickify offers during authoring or running is drawn on top of
somebody else's screen. That is the **Overlay**, and it is why this document is long.

Compose runs there by hand-wiring three owners onto a `WindowManager` view: [ADR-0015].

## The windows

**OV-1** `[A1]` There is more than one **Overlay** window, because they need different things from
the system:

| Window | Present while | Receives touches |
|---|---|---|
| Floating control | a **Scenario** is open or running | yes, on itself only |
| **Marker** layer | editing | yes, on the **Marker**s only |
| **Step** panel | one **Step** is being configured | yes, on itself only |

**OV-2** `[A1]` Every **Overlay** window is `TYPE_APPLICATION_OVERLAY` and passes through every
touch that is not on one of its own controls. The application underneath must behave exactly as it
does without Clickify installed; a tool that intercepts stray taps is worse than no tool.

**OV-3** `[A1]` **No Overlay window takes input focus**, with the single narrow exception in
`OV-20`. `FLAG_NOT_FOCUSABLE` is not an optimisation here. `setText` finds the field with
`findFocus(FOCUS_INPUT)` (`GX-17`), so an **Overlay** that takes focus makes every `setText`
**Step** write into Clickify instead of the application being automated — a failure that looks
like the other application's fault.

**OV-4** `[A1]` A window is removed from `WindowManager` when it stops being needed, not hidden.
An **Overlay** left attached keeps drawing, keeps a `ViewModelStore` alive, and is what turns
[ADR-0015]'s stated risk into a leak.

## Markers

**OV-5** `[A1]` A **Marker** is drawn for each **Step** whose **Action** uses its **Target**
(`SM-8`). A `setText` or `globalAction` **Step** draws none, and its place in the order is shown in
the **Scenario** list instead.

**OV-6** `[A1]` A **Marker** carries the **Step**'s position in the **Scenario**, counting from 1.
Reordering the **Scenario** renumbers every **Marker** immediately. Order is the one thing a
spatial layout cannot show by itself.

**OV-7** `[A1]` A **Marker** is placed by dragging and configured by tapping. Dragging writes raw
pixels into the **Step**'s **Target**, at the **Screen profile** in force (`SM-14`).

**OV-8** `[A1]` A swipe **Step** draws **two** **Marker**s joined by a line, the start carrying the
**Step** number and the end carrying an arrow. Dragging either end edits that end.

**OV-9** `[A1]` A **multiTouch** **Step** draws one numbered **Marker** per path, all carrying the
same **Step** number, so it is visible that they happen together rather than in sequence.

**OV-10** `[A1]` A **Marker** cannot be dragged outside the screen (`SM-17`). It stops at the edge
rather than being refused on save.

**OV-11** `[A1]` **Marker**s are not drawn while a **Scenario** runs. They would be tapped by the
very **Gesture**s they describe. Not dimmed — **removed**, because each one is now a window of its
own (`OV-27`) and a window that is merely faint still takes the touch.

**OV-27** `[A1]` Each drawn **Marker** is a **window of its own**, sized to the handle, and the
layer behind them carrying the connecting lines takes no touches at all.

This is not an implementation note: it is the requirement that there is **no mode**. Android has no
public way to say "this window answers touches *here* and nowhere else", so a full-screen layer
either takes every touch — which makes the phone unusable while Clickify is open — or takes none,
in which case a **Marker** can be looked at but not moved. The earlier answer was a mode the user
had to remember to leave, and `landscape.md` ranks "controls that sit on top of what you are
automating" seventh among the category's complaints. One window per handle gives both at once: the
handle answers touches inside its own few dozen pixels, and everything else reaches the application
underneath.

The cost is stated rather than hidden: one window per drawn **Marker**, which is one per tap, two
per swipe, and up to two per contact of a **multiTouch**.

**OV-31** `[A1]` **Marker** windows are laid out in **display** coordinates, not in what is left of
the display after the system bars.

A window laid out the default way has its origin below the status bar, so a **Marker** at y = 1496
is drawn at 1655 on a phone with a 159-pixel one — while the **Gesture** it describes still lands
at 1496. The user aims with the **Marker**, so this is not an offset but a lie (`SM-11`). The
floating control and the panel are the other way round: they are reached for rather than aimed
with, and belong inside the bars where nothing covers them.

## The floating control

**OV-12** `[A1]` The floating control is always reachable, in both states:

- *stopped* — a drag handle, Start, add a **Step**, open the panel, collapse; above them the
  **Scenario**'s name and how many **Step**s it has
- *running* — the **Step** currently running, Stop, and **free the touch**; nothing else

Four buttons and no more, and the count is the requirement. Width is what the control costs the
user, and everything that is not one of those four is one tap away in the panel.

Collapsed, it keeps saying whether something is running, and says which **Step** it has reached: a
control small enough to forget is a control that can be running without anyone noticing.

**OV-13** `[A1]` Stop is the largest target in the running state, and is reachable at every moment
of a run (`GX-8`). Nothing is ever drawn over it.

**OV-14** `[A1]` The control is dragged by a handle of its own and **snaps to the nearer side**,
remembering where it was left, per device, in `DataStore` (`AP-1`). It collapses to a small bubble
and expands on tap, because it spends most of its life in the way of something.

Three things this requires that are easy to get wrong, all of which were got wrong first:

- The drag is measured in **raw screen coordinates**. A window moved under the finger takes its own
  coordinate space with it, so a delta measured against the view reads zero by the second event and
  the control stops one frame into the drag.
- The handle is its **own target**, not the whole control. A drag beginning on a button would have
  to be told apart from a press of it, and a handle that looks like a handle is the only thing on
  the control saying it can be moved at all.
- The control is re-settled against its side whenever its **width** changes. A run replaces four
  buttons with one large Stop, and a control that kept its left edge would float in from the side
  the moment a run began.

**OV-15** `[A1]` **Free the touch** (`GX-11`) is in four places: on the running control, in the
panel, in the run notification, and on a **Quick Settings tile**.

The copies that matter are the last two. A latched touch is a phone that has stopped answering the
finger, so no **Overlay** window can be tapped at all — and the shade opens on a system gesture
that is handled before any application sees it. The first two are simply where someone who is
reading rather than panicking will look.

**OV-30** `[A1]` The **Overlay** offers a way back to the Activity and a way to close itself. Both
live in the panel rather than on the control, because neither is wanted in a hurry.

## Running, seen from the Overlay

**OV-16** `[A1]` The countdown (`GX-3`) is shown in the control, counting down, with a cancel that
is the same Stop.

**OV-17** `[A1]` When a run ends for any reason other than finishing, the reason is shown where the
user is already looking — in the control — and not only in a notification they have to pull down.
A **Screen profile** mismatch (`SM-15`) is shown before the countdown and names what changed.

## Lifecycle

**OV-18** `[A1]` Every **Overlay** window is created through one host in `:core` that supplies the
`LifecycleOwner`, `SavedStateRegistryOwner` and `ViewModelStoreOwner` Compose requires, and that
**clears the `ViewModelStore` when the window is removed**. It is written once and never
re-implemented per feature — [ADR-0015] calls this the place where this goes wrong if it goes
wrong.

**OV-19** `[A1]` Opening and closing an **Overlay** window many times leaks nothing. This is
checkable without a device and is therefore under test, not left to inspection.

## The Step panel

**OV-20** `[A1]` The **Step** panel is the one window permitted to take input focus, and only
while a field inside it holds the caret. A `setText` **Step**'s string has to be typed somewhere,
and a window that cannot take focus cannot open a keyboard. The two alternatives are worse for the
same reason: an Activity takes focus just the same, and editing the string only in the main
application means leaving the screen being automated.

What makes the exception safe is **when the panel exists**, not the flag itself. The panel is drawn
only while nothing is running — it is derived from the state, not closed by a call somebody
remembers to make — so the window `findFocus(FOCUS_INPUT)` would find during a `setText` **Step**
is never this one. Both halves are under test: the flag in `:core`, the state rule in the
coordinator.

**OV-21** `[A1]` The panel edits everything about a **Step** **except where it touches**. Points
are dragged on the **Marker** layer, where the user can see what they are aiming at (`OV-7`); a
pair of coordinate boxes would be a worse way to set the same value and would disagree with the
layer about it.

The **Marker**s show the **draft**, not the **Step** last written to disk, so choosing `swipe`
draws the destination immediately rather than after a save. While the panel is open a drag
therefore edits the draft too — one rule, *everything in the panel is a draft until Save* — because
the alternative loses work: a drag written straight to disk would be silently undone by the next
Save.

**OV-22** `[A1]` Save is offered only when the **Step** has no violations (`SM-17`), and every
violation is listed at once rather than one at a time. Cancel discards. Delete and the two move
buttons apply immediately: they change the **Scenario**'s shape rather than this **Step**'s fields,
which is the same immediacy dragging a **Marker** already has.

**OV-23** `[A1]` A **Step** added from the floating control lands in the middle of the screen and
opens the panel on itself. The middle because it is the one place certain to be visible and not
under the control — it is a starting position to be dragged from, not a guess at what was meant.

**OV-24** `[A1]` Every **Step** is reachable from the panel, including the ones that draw no
**Marker**. `globalAction` and `setText` have nothing to tap (`SM-8`), so without a way to walk the
**Scenario** they would be writable once and never openable again. Walking away is refused while
there are unsaved edits, rather than silently discarding or silently saving them: both are guesses,
and Save and Cancel are already on screen to be asked.

**OV-25** `[A1]` Stop asked for while nothing is running does **nothing**, and is not an error.

Stop is reachable long before a run and long after one, so pressing it at an idle moment is
ordinary use. *Stopping* is the one state the **Overlay** cannot leave by itself — it ends when the
runner reports back — so entering it with no runner behind it waits for a report that never comes.
The cost is not cosmetic: "no **Marker**s" and "no panel" are both derived from "something is
running", so a stranded *Stopping* takes the whole editor with it and the app can only be recovered
by restarting the service. The transition is refused in the state, and the run notification
separately stops offering Stop when there is nothing to stop.

The other half of the same rule: when a run ends for **any** reason, including a cancelled
coroutine that sends no final event, the **Overlay** returns to *stopped*.

## The Scenario panel

**OV-28** `[A1]` The panel's other face is the **Scenario** as a whole: its name, how often it runs
(`SM-2`), its countdown (`SM-3`), and every **Step** in order.

One window, two faces, rather than two windows. They want the same place on the screen, they want
the same single exception to `OV-3`, and both open at once has no meaning.

The **Step** list is the general answer to `SM-8` that `OV-24` only half solves: a `setText` or
`globalAction` **Step** has nothing on screen to tap, and walking to it from a neighbour is a poor
substitute for seeing all of them. Reordering and deleting happen there and apply at once (`FS-15`),
as does every field — there is no Save for a **Scenario**.

Before this, a **Scenario** could only ever be called "Untitled" and run exactly once, because
`name`, `runCount` and `countdownMilliseconds` had nowhere to be edited.

**OV-26** `[A1]` Opening a **Scenario** sends the Activity to the back.

The application the user wants to automate is somewhere else — that is the whole premise of the
**Overlay**. Staying in front would put the floating control on top of the one application nobody
wants to automate, and make the first thing the user does after every Start be pressing Home.

**OV-29** `[A1]` The run notification says what is happening and offers only what is possible at
that moment, and is re-posted only when what it would say has changed.

Three states, not two: idle names the **Scenario** and offers *free the touch* and *close*;
counting down says so and has no **Step** number to give; running names the **Step** and offers
*Stop*. A countdown ticking every hundred milliseconds must not rewrite the shade thirty times on
the way to the first **Step**.

**OV-32** `[A2]` The panel is laid out against the **display** and keeps its own content clear of
the navigation bar.

A bottom-anchored overlay window is laid out inside the system bars, so it stops short of the
screen by the height of the navigation bar. On the test device that was seventy-two pixels of
somebody else's application showing through beneath a sheet whose whole job is to sit on the edge
of the phone. `FLAG_LAYOUT_IN_SCREEN` is not enough by itself — it does not move the parent frame's
bottom edge, so `Gravity.BOTTOM` still resolved to the top of the bar. It takes a negative `y` of
the inset's height as well, which `FLAG_LAYOUT_NO_LIMITS` is what permits.

The control is placed in the other space, inside the bars (`OV-14`), so what it is told to keep
clear of the panel is the panel's height **less that inset**. Otherwise it is held a navigation
bar too high and a strip of the application shows between the two.

**OV-33** `[A2]` The control is one row, and one of its buttons means *I am finished*.

It was two rows — a caption above the buttons — and measured 840×228 pixels on the test device
while a **Scenario** was being built. One row is 792×144: a third of the height, with a button
gained rather than lost. The **Scenario**'s name moves to the panel, where there is room to change
it as well as read it.

*Done* writes nothing, because every edit reached the disk when it was made (`FS-15`). What was
missing was a way to put the editor away in one press instead of closing the panel, collapsing the
control, and leaving the **Marker**s behind. It closes the panel, collapses the control and hides
the **Marker**s — and collapsing alone now hides them too, because "get out of my way" cannot mean
leaving a dozen handles scattered over the screen.

**OV-34** `[A2]` The panel is a bottom sheet that is dragged open, then scrolled.

Two phases, in this order, because it is what a sheet on this platform does. A swipe up grows the
sheet towards its full extent and the body does not move while there is sheet left to gain; once
there is none, the same unbroken swipe scrolls the body. Coming back down, the body scrolls to its
top first and only then does the sheet shrink. The handoff is a nested-scroll connection rather
than two gestures, so one finger movement crosses between them with nothing to re-grab.

**The sheet is sized by the window, not by an offset inside it.** A window covering the display
with the sheet placed inside would take every touch on the screen, and `OV-21` needs the opposite:
**Marker**s stay draggable while the panel is open, which is the only way to place a swipe's
destination while looking at the swipe's settings. So the window stays `WRAP_CONTENT` and it is the
content that changes height — and the content is bounded by a maximum rather than given a height,
so a **Step** with two fields is a short sheet with no empty space to expand into.

Dragging down does not dismiss. A **Step** being edited holds unsaved changes (`OV-24`), and a
gesture that discarded them by being slightly too long could not be used with confidence.

**OV-35** `[A2]` The **Action** chips carry a heading and a sentence.

Five unlabelled chips at the top of a sheet read as filters, which is what chips usually are;
nothing said that choosing one changes what the **Step** *does*. The heading says what the row is
and the line beneath it describes the chosen **Action** in the **Step**'s own terms, so the answer
is on screen before the question is asked.

**OV-36** `[A2]` The **Step** panel has a way **back**, and both ways out ask before discarding.

The back arrow goes up to the **Scenario**, which is the list of every **Step**. Before it the only
way out of a **Step** was the close button, which took the panel with it — so editing a second
**Step** meant closing the editor, finding its **Marker** again and tapping it, and a **Step** with
no **Marker** (`SM-8`) could not be reached at all except by walking to it.

Back and close both discard the draft, so both ask — and neither asks when the draft matches what
is on disk, because a question with only one sensible answer teaches the user to dismiss it without
reading. The question is drawn **inside the panel's own window** rather than as a dialogue: every
window here is an Overlay with its own token and its own place in the stacking order (`OV-13`), and
a fourth one that can appear at any moment would join that queue.

This is the half `OV-24` was missing. The arrows between **Step**s still refuse to move while there
are unsaved edits, because walking sideways looks like staying put; leaving is visibly leaving, so
it is allowed once the user has said so.

**OV-37** `[A2]` The **Overlay** survives a rotation, and in landscape the panel is a side sheet.

Nothing recreates an Overlay window when the screen turns — it is attached to the window manager,
not to an Activity — so every one of them keeps the size and the position it was given for a screen
that is no longer there. The control ends up off the bottom of a shorter screen, the **Marker**
layer keeps a portrait phone's bounds, and the panel keeps its width. The service takes the
configuration change and puts them all back.

It is done **twice**, a moment apart: `maximumWindowMetrics` is the display's, and on the test
device it still reported the old bounds when the callback arrived. The second pass is the one that
lands; the first is what makes the ordinary case immediate.

In landscape the panel moves to the end edge, full height and four hundred density-independent
pixels wide. A bottom sheet on a screen a third as tall has a peek height a fifth of what it has in
portrait, and every field in it stretched across the whole width — a text box the width of the
phone holding two digits. Against the end edge the sheet has the height to be useful and a width a
form can be read at, and the part of the screen being automated stays visible beside it. There is
no drag-to-expand there, because there is nothing to expand into: the same sheet state is built
with its two heights equal, so every pixel of a swipe goes to the body.

**OV-38** `[A2]` The panel is laid out from one gutter, in named sections.

Every row — header, body, footer — is measured from the same sixteen density-independent pixels.
Before this the header's first glyph sat at 63 physical pixels, the body's text at 48 and the
footer at a third number, which is what "not aligned" meant when it was reported. An icon button is
larger than its icon, so the rows that hold them are inset by the difference; that arithmetic is
written down once rather than guessed at each row.

The **Scenario** panel is sections rather than a column of controls: the name, how it runs, and
then the **Step**s under a heading that counts them and carries the one button that adds to them.
Each **Step** is two lines — what it does, and what it costs in time — because a **Scenario** is
mostly timing, and on one line the numbers ran into the coordinates until neither could be scanned.
The reorder arrows are one icon family now; they were a chevron and a filled triangle, which sit at
different heights inside their own boxes and read as two unrelated controls.

[ADR-0015]: ../adr/0015-compose-in-the-overlay.md
