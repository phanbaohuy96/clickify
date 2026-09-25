# Clickify is the name; "auto clicker" is the category

The product is **Clickify** on both platforms. The name it replaces, "Auto Click", was at once a
proper noun and a plain description of what the thing does, so nothing in the repository could tell
the two apart: a translated string, a type name and a bundle identifier all said "AutoClick" for
three different reasons.

The split we hold to is that the name is carried only by the things that **are** the product — the
bundle identifier `com.pbh.clickify` on both platforms, the Swift module and executable `Clickify`,
the Kotlin package `com.pbh.clickify`, the `@main` `ClickifyApp`, and `ClickifyAccessibilityService`,
which the user reads in Android's Settings. Everything else takes its word from the glossary
instead: the form state of **Simple mode** is `SimpleModeModel`, not `Clickifier`. A brand name
sitting inside a type that merely belongs to a domain concept is noise, and it ages badly — it has
to be renamed again at the next rebrand, for no reason.

The name is never translated. It already sat untranslated inside Japanese and Chinese sentences
before the rule was written down, so writing it down only makes the existing practice enforceable:
a test scans the string files of both platforms and fails on any legacy spelling. The test reads
string files only, never prose — this file and `CONTEXT.md` have to be able to say the old name in
order to explain it.

## Consequences

- `com.pbh.clickify` is frozen from the first release onward; changing it afterwards means a new
  application to every store and every user. It is chosen now precisely because nothing is released
  yet — no permission grant, no saved **Scenario**, no store identity is worth preserving today.
- macOS settings live in `UserDefaults.standard`, keyed by the bundle identifier, so the rename
  resets every preference on a machine that ran the old build. The **Scenario** directory moves
  from `Application Support/AutoClick` to `Application Support/Clickify` and is deliberately **not**
  migrated in code: migration code written for a pre-release rename is dead the day it ships.
- The local signing identity is renamed with everything else, so `build-app.sh` finds no certificate
  until `create-local-signing-identity.sh` is run once more. Until it is, the script falls back to
  ad-hoc signing, which invalidates every granted permission on every single build — the exact
  failure that script exists to prevent.
