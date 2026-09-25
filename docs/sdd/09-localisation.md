# 09 — Localisation

How the interface is translated, and — just as important — **what is not interface**.

The mechanism was chosen by measurement, not by preference; the measurements and the options they
ruled out are in [ADR-0010](../adr/0010-strings-files-and-a-live-bundle-swap.md).

## Languages

- **LC-1** `[Slice 6]` `[done]` The interface ships in five languages: **`en`** (the development
  language), **`vi`**, **`zh-Hans`**, **`ja`**, **`es`**. `CFBundleDevelopmentRegion` is `en`.

  `en` is the source of truth. Every other language is a translation **of it**, and anything
  missing falls back to it (`LC-5`).

- **LC-2** `[Slice 6]` `[done]` Adding a language is adding **one directory**. It must never
  require a change to Swift, to `Package.swift` or to the build script. This is the property the
  whole design is bought with, and `LC-15` is what keeps it affordable.

## Where the translations live

- **LC-3** `[Slice 6]` `[done]` Translations are `Resources/<code>.lproj/Localizable.strings`, next
  to `Resources/Info.plist`, plus `Localizable.stringsdict` where a count appears (`LC-9`).
  `scripts/build-app.sh` copies the `.lproj` directories into `Contents/Resources/`.

- **LC-4** `[Slice 6]` `[done]` The application **never** uses `Bundle.module`, and `Package.swift`
  declares no resources.

  This is not a style preference. SwiftPM's generated `Bundle.module` accessor looks for the
  resource bundle at `Bundle.main.bundleURL/Clickify_Clickify.bundle` — the **root** of the
  `.app`, not `Contents/Resources` — and when that fails it falls back to an **absolute path into
  the `.build/` directory of the machine that compiled it**. Measured: on the build machine it
  resolves through that fallback and works; with `.build/` renamed it dies with
  `Fatal error: could not load resource bundle`. A cloned repo, a copied `.app`, or a cleaned build
  directory would each crash the app at its first string lookup, and never on the maintainer's
  machine.

## Choosing the language

- **LC-5** `[Slice 6]` `[done]` The preference is stored as `String?` in `UserDefaults`; `nil`
  means **follow the system**, and is the default. `nil` resolves through
  `Bundle.main.preferredLocalizations`, which already performs Apple's own negotiation.

- **LC-6** `[Slice 6]` `[done]` Changing the language takes effect **immediately**. No relaunch, no
  restart prompt.

  This forces the lookup to go through an explicitly loaded `<code>.lproj` sub-bundle rather than
  `Bundle.main`. `Bundle.main` resolves its language **once per process and caches it**: measured,
  writing `AppleLanguages` into the application's own defaults domain mid-process leaves
  `preferredLocalizations` at `["en"]` and the strings unchanged. Only a new process picks it up.

- **LC-7** `[Slice 6]` `[done]` A key missing from the active language falls back to **`en`**. The
  raw key must never reach the screen.

  Loading a sub-bundle directly is what makes this a requirement rather than a freebie: Foundation's
  automatic fallback chain belongs to `Bundle.main`. A sub-bundle asked for a key it does not have
  returns **the key itself** — measured: `ja.lproj` missing `farewell` returns `"farewell"`, and
  passing `value: ""` returns `"farewell"` too. The miss is detected by passing a sentinel as
  `value:` and comparing identity.

- **LC-8** `[Slice 6]` `[done]` In the picker, each language is written **in its own language** —
  `English`, `Tiếng Việt`, `中文（简体）`, `日本語`, `Español` — and is **never** translated.

  Someone who picked `日本語` by accident has to find the way back while unable to read anything
  else on screen. Translating this list would show them *"ベトナム語"* where they are looking for
  *"Tiếng Việt"*. These five names are frozen constants and are deliberately **not** keys.

## Formatting

- **LC-9** `[Slice 6]` `[done]` A string carrying a count uses `Localizable.stringsdict`, not string
  interpolation. Numbers are formatted through the **active locale**, never with a decimal point or
  a thousands separator written into the translation.

  `vi` and `es` write `0,85` where `en` writes `0.85`. A limit such as `3.600.000` baked into the
  Vietnamese sentence is worse still: changing `ScenarioLimits` then leaves five translations
  stating a number the code no longer enforces. Limits are passed in as arguments.

- **LC-10** `[Slice 6]` `[done]` Every format string with more than one argument uses **positional**
  specifiers (`%1$@`, `%2$d`). `ja` and `zh-Hans` reorder clauses.

## What is not interface

- **LC-11** `[Slice 6]` `[done]` A name written into `scenario.json` is **data**. It is translated
  at the moment it is created and then never again — the same rule Finder follows for
  *"file copy"*.

  So duplicating in English gives `X (copy)` and it stays `X (copy)` after switching to Vietnamese.
  Two exceptions, both deliberate:

  - The name used when `scenario.json` has **no** `name` field is a repair value, not a name the
    user chose. It is a frozen constant and goes through no catalog. Translating it would make one
    corrupt file produce different names on different machines, and that name is then written back
    to disk on the next save — user data rewritten by an interface setting.
  - The timestamp inside a recording's default name is frozen to `en_US_POSIX` (`RC-20`). Only the
    **word** is translated.

- **LC-12** `[Slice 6]` `[done]` `KeyCatalog.Entry.name` is an identifier and is never translated;
  only `title` is (`DM-21`). This split already existed and is the shape the rest of this section
  copies.

- **LC-13** `[Slice 6]` `[done]` The interface language **never** reaches
  `VNRecognizeTextRequest.recognitionLanguages` (`RG-13`) or the input source (`EX-27`). These are
  three unrelated things that the word *language* happens to cover; see the *Flagged ambiguities*
  entry in [`macos/CONTEXT.md`](../../macos/CONTEXT.md), where two of the three are defined.

  OCR reads text belonging to **the application being automated**, which has nothing to do with the
  language of Clickify's own menus.

- **LC-17** `[Slice 6]` `[done]` A translation is readable from **any thread**, and no lookup path may
  assert main-actor isolation.

  `KeyCatalog.entries` is a `static let`: Swift builds it lazily on whichever thread touches it
  first. With the lookup behind `MainActor.assumeIsolated` that trapped with `SIGTRAP` whenever
  that thread was not the main one — deterministically under `swift test`, and in the app only by
  luck. The lookup therefore lives in an immutable `Catalogue` held behind a lock
  (`InstalledCatalogue`), not on the `@MainActor` object that views observe.

- **LC-18** `[Slice 6]` `[done]` A translated label on a value that lives in a `static let` table is
  **computed**, never stored. `KeyCatalog.Entry.title` stored its text and so froze the language of
  whoever touched the table first (`LC-12` is the same rule seen from the other side).

## Keys and tests

- **LC-14** `[Slice 6]` `[done]` Every key used by the code is a case of `StringKey: String,
  CaseIterable`. A mistyped key is a **compile error**, and the completeness tests iterate
  `allCases`.

  Only the *keys* live in Swift. The *translations* stay in `.strings`, so `LC-2` still holds.

- **LC-15** `[Slice 6]` `[done]` The completeness test is **asymmetric**:

  | Language | A missing key |
  |---|---|
  | `en` | **fails the build** — it is the source of truth and the fallback target |
  | `vi` | **fails the build** — the only other language with a reviewer |
  | `zh-Hans`, `ja`, `es` | **does not fail** — reported as coverage, e.g. `ja: 217/220` |

  A symmetric test would mean every new label in the interface turns the build red until it has
  been machine-translated into three languages nobody can check — and it would contradict `LC-7`,
  which exists precisely so that a partial translation is safe.

- **LC-16** `[Slice 6]` `[done]` `en.lproj` must contain **no key that `StringKey` does not
  declare**. Keys are never reused after removal, for the same reason requirement identifiers are
  not.
