---
name: add-interface-string
description: Add, change or remove text the Clickify user sees, in all five interface languages on the right platform and module — StringKey plus five .lproj files on macOS, string resources in every values-* folder on Android. Use whenever a change adds a label, message, button, error text or notification, or touches Localizable.strings, stringsdict or strings*.xml.
---

# Add an interface string

Five interface languages on both platforms: **en** (development language and fallback), **vi**,
**ja**, **zh-Hans** (Android folder `values-zh-rCN`), **es**. A string missing from one of them does
not fail loudly — the user silently gets English.

## Rules that are not about files

- **Clickify** is never translated, inflected or shortened, in any language. Android enforces this
  in `BrandNameTest`; macOS relies on you.
- Use the glossary's words (`CONTEXT.md`, the platform glossary), translated consistently with the
  existing strings in that language — read the neighbours before writing a new one.
- **Interface language** is a display setting only. A string change never touches recognition
  languages, input sources or how characters are typed; wiring them together is a bug.
- A **Scenario**'s name is data, not interface: it is translated once, when created, and never again.

## macOS

1. Add a case to `macos/Sources/Clickify/Localization/StringKey.swift`, in the `// MARK:` group of
   the screen that shows it. The raw value is a dotted identifier (`menu.language.follow_system`).
   Removed keys are never reused (`LC-16`).
2. Add the key to **all five** `macos/Resources/<code>.lproj/Localizable.strings`. Plurals go in
   `Localizable.stringsdict` instead, again in all five.
3. `cd macos && swift test --no-parallel`. `LocalizationTests` fails if **en** or **vi** is missing a
   key or if en declares an unused one. It only **prints** coverage for ja, zh-Hans and es — read
   the `localisation coverage —` lines and check each is `N/N`.

## Android

1. Put the string in the module whose UI shows it. `:app` splits its strings by surface —
   `strings.xml`, `strings_onboarding.xml`, `strings_overlay.xml` (the floating control, `OV-12`),
   `strings_step.xml` (the Step panel), `strings_recognition.xml`. Read the file's header comment:
   it states the length budget for that surface (the **Overlay** sits on someone else's screen, so
   its labels are short). Prefix keys the way their neighbours are prefixed.
2. Add it to `values/` and to **every** sibling `values-*` folder that module has
   (`values-night` holds styles, not strings).
3. Check nothing is left behind:

   ```bash
   bash -c 'cd android/app/src/main/res   # or the module you changed
     keys() { cat "$1"/strings*.xml 2>/dev/null | grep -oE "name=\"[^\"]+\"" | grep -v "\"app_name\"" | sort; }
     for d in values-*; do ls "$d"/strings*.xml >/dev/null 2>&1 || continue
       diff <(keys values) <(keys "$d") >/dev/null && echo "$d ok" || { echo "$d differs:"; diff <(keys values) <(keys "$d"); }
     done'
   ```

   `app_name` is skipped because it exists only in `values/`, on purpose.
4. `cd android && make check`.

**Known gap:** `:core` and `:data` have only `values` and `values-vi`, so their error texts appear in
English to ja, zh-Hans and es users. Adding a string there means adding the three missing folders,
or saying explicitly in the PR that you did not.

## Before handing back

- [ ] Five languages on every platform touched, checked by a command, not by eye.
- [ ] No "Clickify" translated or declined; no hard-coded translatable literal in Swift or Kotlin.
- [ ] If a screenshot in `docs/assets/` shows this text, it is now out of date — say so or retake it
      (see `prove-a-change`).
