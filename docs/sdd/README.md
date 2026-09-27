# SDD — Clickify design specification

The specification comes **before** the code. Every observable behaviour of Clickify has to
appear here as a numbered requirement, and the code has to cite that number wherever the
behaviour is not obvious from the code itself.

## Relationship to the other documents

| Document | Answers |
|---|---|
| [`../../CONTEXT.md`](../../CONTEXT.md) | What the **shared** concepts are called and what they mean |
| [`../../macos/CONTEXT.md`](../../macos/CONTEXT.md) | The terms that exist **only** on macOS |
| [`../adr/`](../adr/) | **Why** this option was chosen over that one |
| `docs/sdd/` (this document) | What the system **must do**, precise enough to check |

## Requirement identifier convention

| Prefix | Scope | File |
|---|---|---|
| `DM` | Data model | [02](./02-data-model.md) |
| `EX` | Execution semantics | [03](./03-execution-semantics.md) |
| `ST` | Storage | [04](./04-storage.md) |
| `UI` | User interface | [05](./05-user-interface.md) |
| `RC` | Recording | [06](./06-recording.md) |
| `RG` | Target recognition | [07](./07-target-recognition.md) |
| `SF` | Permissions and safety | [08](./08-permissions-and-safety.md) |
| `LC` | Localisation | [09](./09-localisation.md) |

Android has its own registry in
[`../../android/docs/sdd/README.md`](../../android/docs/sdd/README.md), using prefixes that do
not appear above, so a bare identifier never means two things.

Requirement identifiers are **never reused**. A dropped requirement is marked `~~DM-7~~
(dropped)`, never deleted and never renumbered.

## Status

Every requirement carries a status label:

- **`[Slice 1]`…`[Slice 6]`** — which slice it belongs to, see [01](./01-scope.md)
- **`[done]`** — code and matching tests exist

`[done]` means the code exists; it does **not** mean it has been tried on a real machine. Any
requirement that can only be checked by hand — because it needs a TCC permission or a real screen
— is listed in [`../manual-e2e-tests.md`](../manual-e2e-tests.md) along with how to prove each
one.
