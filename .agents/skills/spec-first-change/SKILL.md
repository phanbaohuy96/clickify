---
name: spec-first-change
description: Change or add behaviour in Clickify the way this repository requires — the numbered requirement moves first, then the code that cites it, then an ADR only if the decision earns one. Use for any feature, fix or behaviour change under macos/ or android/, and whenever code needs to cite a requirement identifier.
---

# Spec-first change

The rule is in `AGENTS.md` (law 1). This is how to follow it without breaking the registry.

## 1. Find the platform and the registry

| Change lives in | Registry | Prefixes | Status labels |
|---|---|---|---|
| `macos/` | `docs/sdd/README.md` | `DM EX ST UI RC RG SF LC` | `[Slice 1]`…`[Slice 6]`, `[done]` |
| `android/` | `android/docs/sdd/README.md` | `PM SM FS GX OV RD DS AP PK TP IL` | `[A1]`…`[A4]`, `[done]` |
| both | each platform's own file | — | — |

A prefix belongs to exactly one platform. Never invent a prefix, and never put an Android rule under
a macOS prefix because the name looks better — the registry README explains why `TP` is not `RG`.

## 2. Find the requirement, or the next free number

```bash
grep -rn "GX-8\b" docs android/docs          # does it exist, and where
grep -rhoE "\bGX-[0-9]+" android/docs/sdd | sort -t- -k2 -n | tail -1   # highest used, struck-through ones included
```

The next number is one above the highest ever used, **including** struck-through ones. Numbers are
never reused.

## 3. Write the requirement before the code

- Behaviour stated so it can be checked: an observable outcome, not an implementation.
- Use the glossary's words exactly (`CONTEXT.md` and the platform glossary). A new term goes into the
  **one** glossary it belongs to — `CONTEXT-MAP.md` says which.
- Status label for the current slice; `[done]` only once code **and** tests exist.
- Dropping one: `~~OV-12~~ (dropped)` plus one line saying why. Never delete, never renumber.
- If it can only be proved by hand, add or update the case in `docs/manual-e2e-tests.md` (macOS) or
  the tier 2/3 lists in `android/docs/testing.md` in the same change.

## 4. Write the code, citing the identifier

Cite it in a comment or test name wherever the behaviour is not obvious from the code, in the
style already in the tree — Swift: ``/// Every key the interface uses (`LC-14`).``, Kotlin:
`// GX-15: every stroke start …`.
Every cited identifier must exist: `grep` it before committing.

## 5. Does it need an ADR?

Only if all three hold: hard to reverse, surprising without context, the result of a real trade-off.
If yes:

- One numbering sequence for the whole product; the folder says the context. Next number:
  `ls docs/adr android/docs/adr | grep -oE '^[0-9]{4}' | sort | tail -1`, plus one.
- Shared or macOS decisions in `docs/adr/`, Android-only in `android/docs/adr/`.
- A later ADR that overturns part of an earlier one says so at the top and links back (see how
  `0009` treats `0007`); the earlier one is not rewritten.

## 6. Before handing back

- [ ] `grep` every identifier the diff cites — each one exists.
- [ ] The requirement's status matches reality (`[done]` = code + tests, not "seen on a device").
- [ ] Anything unverifiable is written down as unverified, per `prove-a-change`.
