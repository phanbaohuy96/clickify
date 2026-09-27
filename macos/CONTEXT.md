# Clickify for macOS

The macOS member of Clickify: a menu-bar app that emits synthetic mouse and keyboard events. This
document holds **only** the terms that do not exist on Android; everything shared — **Scenario**,
**Step**, **Action**, **Target**, **Guard**, **Template**, **Search region**, **Recording session**,
**Interface language** — is defined once in the [root glossary](../CONTEXT.md) and is not repeated
here.

This document is a **glossary**, not a spec.

## Language

**Simple mode**:
The cut-down surface for quickly configuring one repeated operation, which underneath still builds a one-step **Scenario**.
_Avoid_: old mode, basic mode, legacy mode

**Locked application**:
The only application allowed to receive events when the user turns the restriction on; entirely distinct from **Target**.
_Avoid_: destination app, target app

**Anchor window**:
The window of the **Locked application** that relative **Target**s are measured from. It is the **recorded window** — recognised by its title — not whichever window happens to be in front at run time; only when that fails does it fall back to the frontmost one.
_Avoid_: main window, focused window

**Recognition language**:
The language Vision is told to expect when looking for a text **Target** — the language of the **application being automated**, not of Clickify.
_Avoid_: OCR language setting, text language

**Input source**:
The keyboard layout or input method in effect while a `typeText` **Step** runs. What Telex folds `aa` into `â` is this, not a language.
_Avoid_: keyboard language, typing language

## Relationships

- **Simple mode** produces exactly one one-step **Scenario**; it is not a separate way of running.
- A **Locked application** constrains the whole **Scenario**, not individual **Step**s.
- A **Target** comes in four forms here: at the cursor, an absolute screen point, an offset from a corner of the **Anchor window**, and the centre of a located **Template**.
- A window-relative **Target** can only be resolved when a **Locked application** is set; without one the **Scenario** is invalid.
- A **Template** **Target** is the opposite: it needs no **Locked application**, and a **Template** can be cropped from anywhere on screen, including from a screenshot open in another application.
- One **Recording session** produces exactly one **Scenario**; it watches the mouse only, never the keyboard.
- **Interface language**, **Recognition language** and **Input source** are three independent settings that share a word. None of them is derived from another, and wiring any two together is a bug.
- A **Locked application** is something Clickify **addresses**, which is the sharpest difference from Android: there a **Gesture** goes to whatever happens to be in front, so the application can only be observed and refused, never aimed at.

## Example dialogue

> **Dev:** "If we keep the Simple tab, do we have to write a second click loop?"
> **Domain expert:** "No. **Simple mode** is just an input funnel; it builds a one-step **Scenario** and hands it to the same runner."

## Flagged ambiguities

- "two modes side by side" originally implied two independent runners — settled: two **interface** surfaces, one runner.
- "target" in the existing code carried two meanings: `targetMode` (where to click) and `targetProcessIdentifier` (which app may receive the click) — split into **Target** and **Locked application**.
- "record" once implied capturing everything the user does — narrowed: a **Recording session** captures the mouse only; typing steps are added by hand.
- "the search region is always anchored to the window" was reversed: window anchoring is now a preference used when available, because requiring it would block cropping a **Template** from a screenshot.
- "fixed point" once meant only absolute coordinates; it is now two distinct **Target** forms — absolute, and relative to the **Anchor window**.
- "language" is the most overloaded word in the project: it means the **Interface language**, the **Recognition language**, or the **Input source**, depending on who is speaking. They look connected and are not — a Vietnamese menu says nothing about whether the game on screen is in Vietnamese, and neither says anything about what EVKey will do to a keystroke. Settled by naming all three; see `LC-13`.
- "the layout fits" was a claim about **one** wording. Five wordings later it is three separate claims — a `30`-point box fits `ms` and `lần` and not `times`; a row that fits `Capture again…` does not fit `Capturar otra vez…`. Settled by measuring the strings against the container in a test (`UI-24`, `UI-25`) rather than by eye. Android inherits none of this: Compose reflows, so the same five wordings cost nothing there.
