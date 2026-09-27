# 04 — Storage (Android)

A **Scenario** is a directory, not a row: [ADR-0014] gives the reasoning and [ADR-0005] gives the
layout it inherits from macOS. This document says what is on disk and what happens when what is on
disk is wrong.

## Layout

**FS-1** `[A1]` The storage root is `filesDir/scenarios/`. Internal storage, so no permission is
needed to reach it and nothing else on the phone can read it.

**FS-2** `[A1]` Every **Scenario** is a directory `scenarios/<uuid>/` holding `scenario.json`, and
from A3 a `templates/` subdirectory.

**FS-3** `[A1]` Deleting a **Scenario** deletes the whole directory; there is no reference counting
anywhere. The other half of that choice: **duplicating copies the whole directory**, `templates/`
included. Copying only `scenario.json` would leave the copy pointing at files that do not exist.
Mirrors macOS `ST-3`.

**FS-4** `[A1]` `DataStore` holds only what is not part of a **Scenario**: which **Scenario** is
selected, where the floating control was last left, and the interface language (`AP-1`). No **Scenario**
data is ever kept there.

## The `scenario.json` format

**FS-5** `[A1]` The JSON carries a `schemaVersion` integer, starting at `1`.

**FS-6** `[A1]` **Action** and **Target** are encoded with a `kind` discriminator, spelled out
rather than left to the serialiser's default, because the file has to be readable and editable by
hand. Mirrors macOS `ST-6`.

**FS-7** `[A1]` Coordinates are encoded as separate `x`/`y`, never as an array.

**FS-8** `[A1]` The **Screen profile** is part of the file. A `scenario.json` without one describes
points that cannot be checked, and `SM-15` has nothing to compare against.

```json
{
  "schemaVersion": 1,
  "id": "9f1c…",
  "name": "Daily quest",
  "repeat": 20,
  "countdownMilliseconds": 3000,
  "screenProfile": { "widthPixels": 1080, "heightPixels": 2400, "densityDpi": 440, "rotation": "PORTRAIT" },
  "steps": [
    {
      "id": "0c22…",
      "action": { "kind": "tap", "holdMilliseconds": 0 },
      "target": { "kind": "point", "x": 820, "y": 410 },
      "repeat": 1,
      "delayMillisecondsAfter": 200
    },
    {
      "id": "91b7…",
      "action": { "kind": "swipe", "x": 540, "y": 600, "durationMilliseconds": 250 },
      "target": { "kind": "point", "x": 540, "y": 1800 },
      "repeat": 1,
      "delayMillisecondsAfter": 100
    },
    {
      "id": "3d40…",
      "action": { "kind": "globalAction", "action": "BACK" },
      "target": { "kind": "point", "x": 0, "y": 0 },
      "repeat": 1,
      "delayMillisecondsAfter": 300
    }
  ]
}
```

**FS-9** `[A1]` `"repeat"` on a **Scenario** accepts an integer or the string `"until-stopped"`
(`SM-2`). A **Step**'s repeat count is always an integer.

**FS-10** `[A1]` A **Step** whose **Action** ignores its **Target** (`SM-8`) still writes one, so
the format has no optional field and no reader has to guess. The example above shows it as
`{"x": 0, "y": 0}`.

## Reading and writing

**FS-11** `[A1]` A directory whose `scenario.json` cannot be read is **skipped and logged**, and
the remaining **Scenario**s still load. One broken file is not a broken app.

**FS-12** `[A1]` Unrecognised fields are ignored, missing fields take their default, out-of-range
values are clamped (`SM-16`).

**FS-13** `[A1]` Writing goes to a temporary file in the same directory, is flushed to disk, and
then replaces the target atomically. A battery pull mid-write cannot leave a truncated
`scenario.json`. Mirrors macOS `ST-11`.

**FS-14** `[A1]` A `schemaVersion` newer than this build understands makes that **Scenario** load
**read-only and unrunnable** rather than being read wrongly. What loads carries its name and id and
**no Steps at all**, because the Steps are in the part this build cannot decode. Duplicating is
blocked for the same reason — the copy would carry the original's name, this build's
`schemaVersion` and no Steps. Deleting stays allowed, because that is something the user does on
purpose. Mirrors macOS `ST-12`.

**FS-15** `[A1]` Saving is not a button. A **Scenario** is written when the user stops editing it,
and the write is cheap enough that there is no reason to ask. The one thing that must never happen
is a **Scenario** that exists in the interface and not on disk.

## Settings, which are not a Scenario

**AP-1** `[A1]` The handful of things Clickify remembers about **itself** live in a Preferences
`DataStore`, apart from every **Scenario**.

Apart on purpose. A **Scenario** is the user's document and is built to travel ([ADR-0014]); where
they happen to like the floating control is a property of **this phone** and would be wrong on any
other. The same reasoning keeps it out of `scenario.json` even though it would have fitted.

Today it holds one thing: where the control was left (`OV-14`). Absent and *the top-left corner*
are different answers — the first means the user has never had a say, and the control is placed
against an edge rather than inheriting a corner nobody chose.

[ADR-0005]: ../../../docs/adr/0005-one-directory-per-scenario.md
[ADR-0014]: ../adr/0014-no-database.md
