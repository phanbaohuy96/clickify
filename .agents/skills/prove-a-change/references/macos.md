# macOS rig — manual e2e sessions and screenshots

`docs/manual-e2e-tests.md` is the source: preparation, every case, and the requirement it proves.
This file is the part of running it that went wrong before.

## Before a session

- `./scripts/build-app.sh && ./scripts/install.sh` from `macos/`, then grant **Accessibility** (and
  **Screen Recording** for session C) to the installed bundle, then **quit and reopen** — a running
  process never receives a new grant (`SF-7`).
- The destination is harmless: TextEdit, or `tools/testing/target-page.html` served by
  `log-server.py`. Never Terminal, a logged-in browser, or anything with a delete button.
- `⌥⌘S` stops everything (`UI-15`). Press it once before starting.
- Check the displays that are actually attached (`system_profiler SPDisplaysDataType`). Cases like
  `C3`/`C4`/`C15` need an external display, and a note saying one is absent may be out of date.

## Driving the interface

- **Never click remembered coordinates.** Menu-bar extras come and go, and the Clickify status item
  has moved between runs of the same session — once onto another display entirely. Every run that
  followed clicked something else, the popover never opened, and a case was written up as a failure
  for a scenario that had never run.
- Look the status item up through Accessibility on **every** run (an `AXMenuBarItem`), and confirm
  the popover is open before pressing Start. If the item or the Start button cannot be found, abort —
  do not click.
- The popover is readable through Accessibility (`docs/manual-e2e-tests.md`, *The popover's
  Accessibility tree is readable after all*). Match on the label in the **current interface
  language**, since labels come from `<code>.lproj`.
- The app cannot write to `/tmp`; diagnostics written from inside it silently produce nothing.

## Templates

Crop a **Template** only through the app's own crop control. A Template cut with `screencapture`
exercises a route no user can take — `C3` failed deterministically that way while the matcher, handed
the same images offline, chose correctly. The result was recorded as *Invalid, not a failure*.

## Keyboard

An input method (Telex, for example) can hold typed text in a composition buffer; read
*A measurement trap* under session B before judging any typing case.
