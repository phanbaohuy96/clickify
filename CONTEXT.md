# Clickify

One product on two platforms — a macOS menu-bar app and an Android app — both automating repetitive
work by emitting synthetic input. This document is the project's **glossary**, not a spec, and it
holds **only** the terms that mean the same thing on both. A term that exists on one platform is
defined beside it: [macOS](./macos/CONTEXT.md), [Android](./android/CONTEXT.md).
[`CONTEXT-MAP.md`](./CONTEXT-MAP.md) says how the three fit together.

## Language

**Clickify**:
The product's name, the same on both platforms — a proper noun, so it is never translated, never
inflected and never shortened.
_Avoid_: Auto Click, AutoClick, Clickify Auto Clicker, "the app"

**Scenario**:
An ordered, user-named sequence of operations that runs start to finish as one unit.
_Avoid_: macro, workflow, script, profile

**Step**:
One unit inside a **Scenario**, made of exactly one **Action** and exactly one **Target**.
_Avoid_: action (when you mean the pair), command, task

**Action**:
What a **Step** does — press, hold, travel, scroll, type — kept separate from where it does it. The list
of kinds is each platform's own; that the axis exists is not.
_Avoid_: event, operation, step kind

**Target**:
Where an **Action** happens, resolved to concrete coordinates only at the moment that step runs.
_Avoid_: destination, coordinates, point, anchor

**Guard**:
The condition deciding whether a **Step** runs at all, kept separate from the **Target** deciding where it acts.
_Avoid_: condition, precondition, check, filter, if

**Template**:
A patch of screen the user cropped, used to find the target again at run time when coordinates cannot be trusted.
_Avoid_: sample image, snapshot, pattern

**Search region**:
The part of the screen narrowed down to look for a **Template**; optional, and it never adds a requirement of its own to the **Scenario** carrying it.
_Avoid_: search frame, scope, bounds

**Recording session**:
The span of time Clickify watches what the user really does, in order to build a **Scenario**.
*What* it can watch is the one part neither platform shares — a mouse on one, swallowed touches on
the other — so each glossary states its own.
_Avoid_: record, capture

**Interface language**:
Which translation of Clickify's **own** menus and labels is on screen. It is a display setting and nothing else: it never decides what recognition is told to expect, and never decides how characters are typed.
_Avoid_: language (unqualified), locale, region

## Relationships

- **Clickify** is the product; an *auto clicker* is the category it belongs to. The category stays a
  lowercase common noun and never stands in for the name.
- Every automated operation, whichever surface it came from, executes through the same **Scenario** runner.
- A **Scenario** holds one or more ordered **Step**s.
- A **Step** pairs exactly one **Action** with exactly one **Target**; the two axes are independent.
- A delay is a property of a **Step**, not an **Action**. There is no "wait" **Action**.
- A **Guard** is a property of a **Step** in exactly the same way, and for the same reason. It is **not** a
  third axis: a **Step** is still **Action** × **Target**.
- A **Guard** gates only the **Step** carrying it, never the ones after it. A **Step** without one always runs.
- A **Guard** tests presence **or absence**; a **Target** can only be resolved by presence, because a thing
  that is not there has no coordinates. That asymmetry is the whole reason the two are separate.
- A **Target** is a fixed screen point or the centre of a located **Template**. Each platform adds the forms its own system makes possible, and no platform has all of them.
- A **Template** **Target** needs nothing but the screen: a **Template** can be cropped from anywhere on it, including from a screenshot open in another application.
- A **Recording session** produces **Step**s. Whether they become a new **Scenario** or are added to an existing one is the platform's own rule.
- A **Target** that has to be **found** rather than remembered may fail to resolve; the **Step** then retries until its timeout expires and either stops the **Scenario** or is skipped, as that **Step** itself specifies.
- **Interface language** is a display setting and nothing else. Every other thing a platform calls a "language" — what recognition expects, how characters are typed — is a separate setting, derived from nothing, and wiring any two of them together is a bug.

## Example dialogue

> **Dev:** "So 'click on an image' is a new **Action**?"
> **Domain expert:** "No, 'on an image' is a **Target**. The **Action** is still a click. That is what keeps 'scroll at an image' or 'double-click on an image' from being new concepts at all."
>
> **Dev:** "Then 'wait for the Save button to appear, then click it' needs a conditional loop?"
> **Domain expert:** "No. That is a **Step** clicking a **Template** **Target** with a 10-second timeout. A **Scenario** has no branches — a **Step** decides its own fate and never another **Step**'s."
>
> **Dev:** "And 'only tap here if the popup is **not** showing'? There is no popup to aim at."
> **Domain expert:** "Which is exactly why that is a **Guard** and not a **Target**. The **Target** is the fixed point you already know. The **Guard** is the popup, tested for **absence**. Ask a **Target** to be absent and you have asked it for the coordinates of nothing."

## Flagged ambiguities

- "Auto Click" was at once the product's name and a plain description of what the product does, so
  no sentence could tell the two apart — settled: the name is **Clickify**, the category is
  "auto clicker" in lowercase, and a sentence that would read the same with any other auto clicker
  in it is talking about the category, not about **Clickify**. The rule that the name is never
  translated was being obeyed before it was ever written down: it already sat untranslated inside
  Japanese and Chinese sentences.
- "wait" was once listed as an **Action** — corrected: it is a property of a **Step**.
- "long press" is settled as holding in place, and travelling is a different **Action** from holding — `drag` on macOS, `swipe` on Android.
- "wait until the button appears" is not control flow — it is the timeout on resolving a **Template** **Target**.
- **Template** quietly carried two jobs: saying *where* to act, and saying *whether* to act. They looked like
  one job because the answer was usually the same pixel. They split the moment the condition is an **absence**
  — settled: a **Target** locates, a **Guard** withholds.
- "a **Scenario** has no conditions" was never literally true: `skipStep` is a condition, and so is every
  **Template** that may or may not resolve. Settled by a boundary rather than by the word — **a Step may
  decide its own fate, never another Step's**. `skipStep` and `stopScenario` decide a Step's own fate;
  `if`, jumps, loops over a range of Steps and subroutines decide other Steps' fates and stay out. An
  `if`/`else` is written as two **Step**s each guarded by its own **Target**. See [ADR-0011].
- "name" carries two meanings too, and they are the interface/data boundary: a **Scenario**'s name is data written into `scenario.json`, so it is translated once, when created, and never again. macOS's `KeyCatalog` uses the same word for the opposite thing: its `name` is an identifier and is never translated at all — only its `title` is.
