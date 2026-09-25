# Type ASCII key by key

> The second half of this decision — forcing the input source to ABC — was **measured wrong after
> the fact**. See the last section. The key-by-key route itself stands.

[ADR-0007](./0007-type-strings-in-chunks.md) closed with *"typing strings is a low-priority feature
and basic testing is enough"*, and left `B10` and the root cause of `EX-24` open on that basis.

**The premise was wrong.** The project owner does use `typeText` inside games — chat, commands,
names — and always in ASCII. ADR-0007's own *Consequences* section had already written down what
that means:

> The destination application sees **one** key carrying 20 characters, not 20 keys. Anything that
> reacts key by key — **games**, filter-as-you-type fields — will behave differently from a real
> typist.

Nobody had connected that line to [`01-scope.md`](../sdd/01-scope.md), where games are the primary
use case. Chunked typing is correct for TextEdit and wrong for the thing this application is mainly
used for.

## The third route ADR-0007 did not list

ADR-0007 weighed exactly two ways out: keep the broken mechanism, or paste from the clipboard. There
is a third, and it falls out of the diagnosis already recorded there.

`EX-24` is the loss of the **Unicode payload** attached to an event whose `virtualKey` is 0. ASCII
does not need that payload. Sending the **real key code** for each character makes the character
arrive through the ordinary key-code path — the same path `pressKey` has always used without
trouble (`EX-22`) — which:

- **avoids the broken mechanism entirely** rather than reducing how often it is touched, and
- gives the destination **one key per character**, which is what a game expects.

`B10` should fall to this as a side effect: `"[B10b]"` is ASCII throughout.

## Decided: choose the route from the string's contents

An all-ASCII string is typed key by key. Anything containing a character outside ASCII keeps the
chunked path from ADR-0007, because there is no key code for `ằ` or `😀` and the Unicode payload is
the only way to send them.

Nothing is added to `scenario.json` and nothing is added to the editor. The alternative — a `perKey`
switch on each `typeText` **Step** — was rejected as premature: it is a second field on an already
crowded panel, to control something the string itself already determines. The cost of deciding
automatically is that two strings in the same **Step** can behave differently, so `UI-21` requires
the detail panel to **say which route a string will take** rather than leave it to be guessed.

Key codes are resolved through the **current keyboard layout** at run time (`UCKeyTranslate`), not
from a fixed table. `KeyCatalog`'s promise that typing *"does not depend on the keyboard layout"* is
what a hard-coded QWERTY table would break: key code 8 is `c` on QWERTY and something else on AZERTY
or Dvorak. Resolving through the live layout keeps the promise. A character the layout cannot
produce with at most Shift and Option falls back to the chunked path rather than being typed wrong.

## The input method is the real hazard (this section's answer turned out to be wrong)

Per-key typing means the characters pass **through** the active input method. The payload route
bypassed it; this one cannot. This machine runs EVKey, and Telex folds `aa` into `â`, `as` into `á`,
`dd` into `đ` — so `"pass"` would be typed as `"pá"`.

This is not hypothetical. ADR-0007 watched it happen: *"it falls back to `a`, and the Telex input
method folds `aa` into `â`."* That is what made `B10` produce `"â"`.

Decided: **switch the input source to ABC before typing and restore it afterwards**
(`TISSelectInputSource`). It is the same idea as `SF-4`, which already brings the **Locked
application** to the front before typing — prepare the environment, then type into it.

The restore lives on the **stop and cleanup path**, next to `SF-1`'s mouse-button release, not at
the end of `type`. A `⌥⌘S` part-way through a string must not leave the user's input source changed;
that is the same class of mistake as leaving a mouse button held down.

Rejected: detecting a non-ABC source and refusing to run. It is honest, and it never touches system
state — but it means turning EVKey off by hand before every game session, which is exactly the kind
of friction that makes a feature go unused. Rejected more firmly: doing nothing, which reproduces
the silent-wrong-characters failure ADR-0007 called *"worse than one that cannot type at all"*, only
with a different culprit.

## Pace

25 ms between key pairs, a named constant with this paragraph behind it. Games drop input that
arrives inside one frame, and dropped keys would be another silent loss of characters — the very
thing `EX-24` exists to stop. 25 ms is about 40 keys per second: faster than a person types,
comfortably slower than a frame. ADR-0007's measurements of 30 ms and 60 ms are **not** evidence
here; they were taken on the payload mechanism, which fails for a different reason.

## What is still open

The root cause of `EX-24` remains unknown, and this decision does not investigate it. It narrows
what the broken mechanism is used for — non-ASCII strings only — rather than fixing it. If typing
Vietnamese into a game ever becomes a requirement, both this route and the payload route fall short
at once, and the clipboard route in ADR-0007 is the next thing to weigh.

## Measured after the fact: the input-source switch does not cover EVKey

`B15` put the decision above to the test on the real machine, typing `password aa dd` — a string Telex
transforms — with EVKey in Vietnamese mode. It came out `Pasword â đ`.

Clickify did its half correctly: the observer recorded 14 `keyDown` events, one per character, with
real key codes about 25 ms apart. **EVKey rewrote them downstream**, replaying `á`, `as`, `â` and `đ`
under its own pid.

The reason is a false assumption in this ADR. Switching with `TISSelectInputSource` only affects input
methods registered with Text Input Services, and **EVKey is not one**: enumerating every source on this
machine returns Apple's own `com.apple.inputmethod.VietnameseIM.*` and no EVKey. It is an event-tap
input method — which ADR-0007 had already written down, in the same sentence this ADR quotes from it:
*"sits between the keyboard event stream and replays keys under its own pid."* The evidence for the
right answer was on the page; it was read as background rather than as a constraint.

So `EX-26` stands and `EX-27` does not do what this ADR claims. The options left, none yet chosen:

- **Detect rather than prevent.** A tap-based input method cannot be switched off from inside the
  process, but its replay is visible: characters arrive under a different pid. Refusing to run a
  key-by-key `typeText` while one is active is honest, and it is the "detect and warn" option this ADR
  rejected — rejected on the assumption that switching worked, which it does not.
- **The clipboard route** from ADR-0007, which bypasses the keyboard entirely and so bypasses the tap
  as well. It was rejected there for taking over the user's clipboard, and that objection still holds.
- **Accept it** and say plainly that key-by-key typing is for when no event-tap input method is
  running. Cheapest, and it leaves a silently-wrong failure in place for anyone who forgets — the
  thing ADR-0007 called *"worse than one that cannot type at all"*.

Worth noting for whichever is chosen: what EVKey replays with is `virtualKey: 0` plus a Unicode
payload, the exact mechanism behind `EX-24`.
