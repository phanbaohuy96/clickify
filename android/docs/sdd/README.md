# SDD — Clickify for Android

The specification comes **before** the code here too. The rules in
[`../../../docs/sdd/README.md`](../../../docs/sdd/README.md) apply unchanged: every observable
behaviour appears as a numbered requirement, the code cites the number where the behaviour is not
obvious, identifiers are **never reused**, and a dropped requirement is struck through rather than
deleted.

## Requirement identifier convention

Android identifiers use prefixes that **do not appear on macOS**, so a bare identifier is never
ambiguous about which platform it belongs to.

| Prefix | Scope | File |
|---|---|---|
| `PM` | Permissions and onboarding | [02](./02-permissions-and-onboarding.md) |
| `SM` | Scenario model | [03](./03-scenario-model.md) |
| `FS` | Storage | [04](./04-storage.md) |
| `GX` | Gesture execution | [05](./05-gesture-execution.md) |
| `OV` | The Overlay and Markers | [06](./06-overlay-and-markers.md) |
| `RD` | Recording | [08](./08-recording.md) |
| `DS` | The design system | [07](./07-design-system.md) |
| `AP` | Settings that are not a Scenario | [04](./04-storage.md) |
| `PK` | Picking a point to aim a Step at | [09](./09-picking.md) |
| `TP` | Templates and recognition | [10](./10-recognition.md) |
| `IL` | Interface languages | [11](./11-localisation.md) |

macOS identifiers — `DM`, `EX`, `ST`, `UI`, `RC`, `RG`, `SF`, `LC` — keep their meaning when cited
from here, and always link back to [`../../../docs/sdd/`](../../../docs/sdd/).

Two of those are why recognition is `TP` and interface languages are `IL` rather than the obvious
`RG` and `LC`: macOS already spends `RC` on **recording** and `LC` on **localisation**, and the
rule above is that a bare identifier is never ambiguous about which platform it belongs to. The
obvious name losing to the rule is the rule working.

## Status

Every requirement carries the slice it belongs to, `[A1]`…`[A4]`, see [01](./01-scope.md).
`[done]` means the code and its tests exist. It does **not** mean the behaviour has been seen on a
real phone: per [`../testing.md`](../testing.md) no physical Android device is in use on this
project, so anything that can only be proved on hardware stays listed there as unverified.
