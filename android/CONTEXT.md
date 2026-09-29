# Clickify for Android

The Android member of Clickify. This document holds **only** the terms that do not exist on
macOS; everything shared — **Scenario**, **Step**, **Action**, **Target**, **Guard**, **Template**,
**Search region**, **Recording session**, **Interface language** — is defined once in the
[root glossary](../CONTEXT.md) and is not repeated here. The terms that exist only on the other
platform are in [its own glossary](../macos/CONTEXT.md).

This document is a **glossary**, not a spec.

## Language

**Overlay**:
The window Clickify draws on top of whichever application is in front.
_Avoid_: floating window, bubble, HUD, widget, popup

**Marker**:
The on-screen handle showing where one **Step** will act, dragged into place over the application
being automated and tapped to configure that **Step**.
_Avoid_: point, dot, pin, cursor, hotspot, anchor

**Gesture**:
The synthetic touch Clickify hands to the system to perform an **Action** — one or more strokes,
each with a path and a duration.
_Avoid_: event, touch, injection, input

**Global action**:
One of the fixed system operations an **Action** can ask for — Back, Home, Recents, Notifications,
Quick Settings, Lock, Screenshot — and the only keyboard-shaped thing Android permits.
_Avoid_: key press, shortcut, system key, hardware button

**Set text**:
The **Action** writing a whole string into whichever field currently holds input focus.
_Avoid_: type, typing, type text, input text, enter text

**Screen profile**:
The screen facts a coordinate depends on — resolution, orientation, density, and whether a
navigation bar takes up an edge — recorded with a **Scenario** so a mismatch can be detected.
_Avoid_: screen size, device config, layout, resolution

**Foreground application**:
The application whose window is in front, which is therefore the one a **Gesture** will reach.
_Avoid_: current app, active app, locked application

**Scenario map**:
A read-only drawing of one **Scenario** on a frame shaped like its **Screen profile**: every **Step**
at the place it will act, joined in order, with its **Guard** and **Template** beside it. It shows
what a run would do; it never runs anything.
_Avoid_: preview (the button's word, not the concept's), diagram, simulation, flowchart

**Playback**:
The **Scenario map** walking its own path one **Step** at a time, at a pace of its own rather than
the **Scenario**'s timing, taking the path on which every **Guard** comes true and every **Template**
is found where it was cropped.
_Avoid_: dry run, simulation, test run, replay (the button's word)

## Relationships

- The **Overlay** is the only surface Clickify has while another application is in front, so
  **both** authoring and running happen there. Clickify's own screens exist to manage
  **Scenario**s, never to build one.
- A **Marker** shows exactly one **Step**. A **Step** whose **Target** is a fixed point has one
  **Marker**; a swipe has two, joined; a **Step** with no fixed point has none.
- **Marker**s are numbered by **Step** order, because order is the one thing a spatial layout
  cannot show by itself.
- A **Scenario map** draws the same numbered path the **Marker**s do, from Clickify's own screens
  rather than over another application, and so is a way to read a **Scenario**, never to build one.
  Because a **Scenario** has no branches, the map has one path; a **Guard** or search that gives up
  either ends it or steps around its own **Step**, never elsewhere. See [ADR-0011](../docs/adr/0011-a-scenario-has-no-branches.md).
- **Playback** reads a **Scenario** in time, it does not try one: it never sees a screen, so it
  cannot know whether a **Guard** holds, and it says what would happen if one did not instead of
  pretending to find out. Nothing about a **Playback** is evidence that a **Scenario** works.
- A **Scenario** belongs to exactly one **Screen profile**. A **Marker** is a raw pixel inside that
  profile, never a fraction of the screen; when the profile does not match, the **Scenario** refuses
  to run rather than acting approximately. See [ADR-0013].
- A **Recording session** has two modes, and they differ only in whether the swallowed touch is
  re-emitted as a **Gesture**: *pass-through* lets the application underneath react and so can follow
  a sequence across screens; *silent* leaves it untouched and is for marking several points on one.
- **Set text** and **Global action** carry no coordinates, so they ignore their **Step**'s **Target**
  entirely — the same shape macOS already has for its keyboard **Action**s. **Set text** goes to the
  focused field, so the **Step** before it is normally a tap that puts focus there.
- A **Gesture** carries no identity: the system delivers it to whatever is in front. The
  **Foreground application** is therefore something Clickify **observes and may refuse to act
  on**, never something it can aim at — the opposite of macOS's
  [**Locked application**](../macos/CONTEXT.md), which is addressed directly.

## Flagged ambiguities

- **"Record" migrates, but through a different door.** Android exposes no API for observing another
  application's touches. A **Recording session** is nevertheless possible, by a route macOS never
  needs: a full-screen **Overlay** swallows each touch, writes it down, and immediately re-emits it
  as a **Gesture** so the application underneath still responds. Macrorify ships exactly this — a
  green border marks the armed **Overlay**, and the documentation warns "only touch the screen when
  the border is visible", which is the tell: outside the border there is nothing to swallow.
  The consequence to keep in view: **while recording, the application underneath is being driven by
  synthetic touches, not by the user's finger.** An application that rejects synthetic input behaves
  one way when recorded and another way when used — so a recording can look broken in exactly the
  application worth recording. See `android/docs/landscape.md`.
- **"Anchor window" does not migrate either.** Android has no window the user moves, so macOS's
  [window-relative **Target**](../macos/CONTEXT.md) form has nothing to hang off and is absent.
- **"Type" is the wrong word here, deliberately.** macOS types a string **key by key** so that a game
  sees one key per character; [ADR-0009] argues that at length and it is load-bearing there. Android
  cannot send keys to another application at all, so the chosen mechanism is `ACTION_SET_TEXT`, which
  writes the whole string at once and presses nothing. The behaviour is the **opposite** of the
  promise ADR-0009 makes, so the name had to differ too: the **Action** is **Set text**, and
  **ADR-0009 does not apply on Android**. Two consequences worth stating rather than discovering:
  it needs a focused, editable field, so it does nothing in an OpenGL game; and a field that filters
  as you type will see one bulk change instead of a sequence. Shipping an input method of our own
  would restore key-by-key typing — `switchToInputMethod()` exists from API 30 — and that is the day
  this gets its own ADR, not before.
- "marker" was almost called "point" — rejected: a **Marker** is a handle you drag and configure,
  a point is a coordinate. The **Step** underneath keeps the coordinate; the **Marker** is how a
  finger reaches it.
