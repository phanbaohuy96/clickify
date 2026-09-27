# AGENTS.md — Clickify

The one set of instructions for every coding agent in this repository. Codex, Grok and agy read this
file directly; Claude Code reaches it through `CLAUDE.md`. It is a **map**, not a second copy: every
fact it points at is defined in exactly one other place, and this file only says where, and in what
order.

## The product, in one paragraph

Clickify is an auto clicker on two platforms — a macOS menu-bar app (`macos/`, Swift) and an Android
app (`android/`, Kotlin, driven by an accessibility service). It encodes knowledge of **no**
application: the user decides the use case, and the design is decided by three pressures that pull
against each other — repetition, speed, certainty. Read `docs/sdd/01-scope.md`, sections *No
application is the use case* and *Three pressures*, before proposing anything that favours one
kind of target application.

## Read this before you touch that

| Before you… | Read |
|---|---|
| use or coin **any** domain word | `CONTEXT-MAP.md`, then `CONTEXT.md` and the platform glossary (`macos/CONTEXT.md`, `android/CONTEXT.md`) |
| change observable behaviour | the requirement registry: `docs/sdd/README.md` (macOS: `DM EX ST UI RC RG SF LC`) or `android/docs/sdd/README.md` (`PM SM FS GX OV RD DS AP PK TP IL`) |
| undo or work around a design choice | `docs/adr/` and `android/docs/adr/` — one numbering sequence across both folders |
| claim something works on a device or screen | `android/docs/testing.md` (tiers 1–3, and the traps) or `docs/manual-e2e-tests.md` |
| open a pull request | `.github/pull_request_template.md` |

A term is defined in exactly one glossary. Never restate a definition here, in a skill, or in code
comments — cite it.

## The two laws

1. **The specification comes before the code.** A behaviour change moves its numbered requirement
   first, in the same change. Identifiers are never reused; a dropped one is struck through
   (`~~DM-7~~ (dropped)`), never deleted or renumbered. Code cites the identifier wherever the
   behaviour is not obvious, and every identifier cited must exist.
2. **A claim is measured or it is not made.** Every sentence added to a document, a commit or a PR
   was checked against the tree or a run, not remembered. `[done]` means code and tests exist — not
   that anyone saw it on a real screen. Say what you did **not** check.

## Verify

```bash
cd android && make check                        # tier 1, exactly what CI runs
cd android && make tier2                        # needs an emulator; required for service, runner, gestures, Overlay, Screen profile
cd macos && swift build && swift test --no-parallel   # --no-parallel is what CI runs; see macos.yml for why
```

`swift test` cannot prove the macOS app clicks anything: TCC only grants a signed bundle. What only a
person can check lives in `docs/manual-e2e-tests.md`.

## Git and GitHub

- Work on a `type/slug` branch (`feat/`, `fix/`, `docs/`, `test/`, `ci/`, `chore/`). Never commit to `main`.
- **Ask before every commit**, and before every push, pull request or merge. Show the message first.
- Commit subjects are a conventional prefix plus one sentence saying what is now true, in lower case:
  `docs: the README must not claim what the code does not do`. Platform scope in parentheses when it
  is one platform: `fix(android): …`.
- A pull request fills in the whole template, including *What this does not claim*.

## Language

- Everything in the repository is English: docs, comments, logs, fixtures, scripts.
- No translatable user-facing string is hard-coded (the name and raw numbers are not translatable). macOS: a `StringKey` case plus `macos/Resources/<code>.lproj`.
  Android: string resources in the module that shows them. Five interface languages: en, vi, ja,
  zh-Hans, es.
- **Clickify** is a proper noun: never translated, inflected or shortened. "auto clicker" is the
  lowercase category. "Auto Click" is the retired name (ADR-0017).

## Out of scope unless the owner asks in the session

- GitHub issues **#9–#22** (milestones `v1.0-commercial-foundation`, `v1.1-pro-features`,
  `v1.2-community-and-growth`) are **parked** pending a review. They look like active work and are
  not. Several contradict `android/docs/sdd/01-scope.md`'s *Out of scope* outright (Google Play,
  wall-clock scheduling, sharing a Scenario, finding by text). When asked what is left to do, answer
  with engineering work only.
- Control flow inside a **Scenario** — `if`, jumps, loops over Steps, subroutines (ADR-0011).

## Traps that already cost real time

Each one produces a plausible result rather than a failure. Details in the `prove-a-change` skill.

- An e2e run that clicks remembered coordinates — the macOS status item moves between runs.
- `am force-stop` / `am start -S` wipe the Android accessibility grant; the app then looks un-onboarded.
- The Android **Overlay** is invisible to `uiautomator dump`; find it in the pixels.
- `adb shell input` cancels a gesture in flight; use `adb emu event mouse`.
- In zsh, `git log -- $paths` does not split a variable into several paths — use `${=paths}`.

## Skills

Repository skills live in `.agents/skills/<name>/SKILL.md` (`.claude/skills` is a symlink to it).

| Skill | Use when |
|---|---|
| `spec-first-change` | changing or adding behaviour on either platform |
| `prove-a-change` | about to say a change works — choosing the tier, running it, writing the evidence |
| `add-interface-string` | adding or changing text the user sees |
| `doc-claim-audit` | editing or reviewing a README, SDD file, ADR or testing document |

## Per-harness notes

Checked on 2026-09-27 by asking each CLI, with tools forbidden, what it had loaded.

- **Claude Code** — loads `CLAUDE.md`, which imports this file; skills through the `.claude/skills`
  symlink.
- **Codex** — loads this file and `.agents/skills/` natively.
- **Grok** — loads this file, `CLAUDE.md` (for Claude compatibility; its notes are labelled
  Claude-only) and `.agents/skills/`, but **only in a trusted folder**: without `--trust` or an
  interactive grant it starts with neither. Keep repository skills out of `.grok/skills/`, so there
  is one copy.
- **agy (Antigravity CLI)** — loads this file and `.agents/skills/` natively. Do not add a
  `GEMINI.md`; it would be a second copy of this one.
