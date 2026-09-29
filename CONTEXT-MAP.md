# Context Map

Clickify is one product on two platforms. The concepts are shared; almost nothing else is.

## Contexts

- [Clickify](./CONTEXT.md) — the shared language: **Scenario**, **Step**, **Action**,
  **Target**, **Guard**, **Template**, **Search region**, **Recording session**,
  **Interface language**. True on every platform.
- [Clickify for Android](./android/CONTEXT.md) — **Overlay**, **Marker**, **Gesture**,
  **Global action**, **Set text**, **Screen profile**, **Foreground application**,
  **Scenario map**, **Playback**.
- [Clickify for macOS](./macos/CONTEXT.md) — **Simple mode**, **Locked application**,
  **Anchor window**, **Recognition language**, **Input source**.

A term is defined in exactly **one** of the three, and the platform files may cite the root but
never restate it. The test that keeps them apart: a sentence naming a term from one platform's file
belongs in that file, whatever it is about. That is why the root no longer lists the four **Target**
forms — two of them only exist on macOS — and why "the layout fits" is a macOS ambiguity: Compose
reflows, so Android never had the problem.

## Relationships

- **Shared → both**: the **Scenario / Step / Action × Target** model holds unchanged on both
  platforms. A concept that is true on only one of them does not belong in the root glossary.
- **No shared data.** The two platforms share the glossary and the ADRs, **not** `scenario.json`.
  A **Scenario** written by one is never opened by the other; see the ADR on splitting the schema.
- **macOS ⟂ Android** on how an operation is aimed: macOS addresses a **Locked application**
  directly and can anchor to its window; Android hands a **Gesture** to whatever is in front and
  can only observe the **Foreground application**.

## ADR numbering

One sequence across the whole product; the **folder** says which context a decision belongs to.
`docs/adr/` holds shared and macOS-era decisions, `android/docs/adr/` the Android ones. So
ADR-0011 (no branches, product-wide) is followed by ADR-0012 (`minSdk 30`, Android) without either
number being reused.
