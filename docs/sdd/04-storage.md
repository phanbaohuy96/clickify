# 04 — Storage

The directory layout and why **Template**s are duplicated:
[ADR-0005](../adr/0005-one-directory-per-scenario.md).

## Layout

- **ST-1** `[Slice 1]` `[done]` The storage root is `~/Library/Application Support/Clickify/`.
- **ST-2** `[Slice 1]` `[done]` Every **Scenario** is a directory `Scenarios/<uuid>/` holding
  `scenario.json`, and (from Slice 4) a `templates/` subdirectory.
- **ST-3** `[Slice 1]` `[done]` Deleting a **Scenario** deletes the whole directory. There is no
  reference counting anywhere. The other side of the same choice ([ADR-0005]): **duplicating
  copies the whole directory**, `templates/` included. Copying only `scenario.json` leaves the
  copy pointing at files that do not exist — every recognition Step in it breaks immediately,
  while the interface still shows what looks like a normal Scenario.
- **ST-4** `[Slice 1]` `[done]` `UserDefaults` now holds only: the **Simple mode** configuration
  (keys inherited from 1.2.0, names unchanged) and the selected **Scenario** identifier.

## The `scenario.json` format

- **ST-5** `[Slice 1]` `[done]` The JSON carries a `schemaVersion` integer, starting at `1`.
- **ST-6** `[Slice 1]` `[done]` **Action** and **Target** are encoded with a `kind` discriminator,
  not with Swift's synthesised Codable for enums with associated values — the JSON has to be
  readable and editable by hand.
- **ST-7** `[Slice 1]` `[done]` Coordinates are encoded as separate `x`/`y`, not as an array.
  `CGPoint` encodes to `[1,2]` by default, which is unacceptable for a specified format.

```json
{
  "schemaVersion": 1,
  "id": "6A1F…",
  "name": "Bulk delete",
  "repeat": 50,
  "lockedApplication": { "bundleIdentifier": "com.google.Chrome", "name": "Google Chrome" },
  "steps": [
    {
      "id": "0C22…",
      "action": { "kind": "click", "button": "left", "count": 2, "holdMilliseconds": 0 },
      "target": { "kind": "screenPoint", "x": 820, "y": 410 },
      "repeat": 1,
      "delayMillisecondsAfter": 200
    },
    {
      "id": "91B7…",
      "action": { "kind": "scroll", "deltaX": 0, "deltaY": -3 },
      "target": { "kind": "cursor" },
      "repeat": 5,
      "delayMillisecondsAfter": 100
    }
  ]
}
```

- **ST-8** `[Slice 1]` `[done]` `"repeat"` accepts an integer, or the string `"until-stopped"` for
  a **Scenario** that runs without limit (`DM-2`). A **Step**'s repeat count is always an integer.

## Reading and writing

- **ST-9** `[Slice 1]` `[done]` Any subdirectory whose `scenario.json` cannot be read is **skipped
  and logged**, without breaking the load of the remaining **Scenario**s.
- **ST-10** `[Slice 1]` `[done]` Unrecognised fields are ignored; missing fields take their
  default; out-of-range fields are clamped (`DM-20`).
- **ST-11** `[Slice 1]` `[done]` Writing goes to a temporary file and then replaces atomically, so
  a shutdown mid-write cannot leave a truncated `scenario.json`.
- **ST-12** `[Slice 1]` `[done]` A `schemaVersion` newer than the app understands makes that
  **Scenario** load read-only and refuse to run, rather than being read wrongly. What loads has
  only a name and an id, and **no Steps at all** — the Steps live in the part this app cannot
  decode. **Duplicating is therefore blocked too**: the copy would carry the original's name, the
  current `schemaVersion` and no Steps, which is exactly what this requirement exists to prevent.
  Deleting is still allowed, because that is something the user does on purpose.
