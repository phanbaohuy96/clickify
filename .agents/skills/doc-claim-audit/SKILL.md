---
name: doc-claim-audit
description: Check a Clickify document against the tree before it is committed or trusted — every command runs, every path and link exists, every requirement identifier is defined, every number has a measured source, every feature claim matches the code, and the five READMEs agree. Use when writing or reviewing a README, CONTEXT, SDD, ADR, testing or e2e document, or AGENTS.md and the skills themselves.
---

# Doc-claim audit

Law 2 in `AGENTS.md`, applied to prose. This repository's history is full of documents that were
confidently wrong — every `android/Makefile` target named tasks that had never existed; a README
claimed behaviour the code did not have; `testing.md` said the emulator could not rotate. Each was
a sentence written from memory.

## 1. The mechanical half — run the script

```bash
.agents/skills/doc-claim-audit/check-docs.sh              # every tracked .md
.agents/skills/doc-claim-audit/check-docs.sh AGENTS.md    # or just the files you touched
```

It reports relative links to files that do not exist and requirement identifiers no SDD defines.
It prints how many identifiers it found first; if that number is 0, the check is broken, not clean.
New files are not tracked yet — pass them by name.

## 2. The half a script cannot do — check each kind of claim

| Claim in the document | How to check it |
|---|---|
| a command (`make check`, `swift test …`, a script) | run it, or find it verbatim in CI (`.github/workflows/`) |
| a file, class, function or flag | `grep`/`ls` it in the current tree |
| "the app does X" | find the code **and** the requirement; if only one exists, the claim is not made |
| a number (a latency, a count, a pixel) | a run output, test print or measurement recorded with its device; otherwise remove it or mark it unmeasured |
| "not possible", "never", "always" | the strongest claims and the most often wrong — find the evidence or soften to what was observed |
| a term in bold | it means what its **one** glossary says; if not, the document or the glossary is wrong |
| "**Clickify**", "auto clicker" | used as `CONTEXT.md` defines them |

Where a claim cannot be checked, write it as unverified rather than deleting the doubt.

## 3. The five READMEs

`README.md` is the source; `README.vi.md`, `README.zh-Hans.md`, `README.ja.md` and `README.es.md`
translate it, with a language switcher at the top of each. A change to what the product does, a
command or a link goes into **all five** in the same change. A wording-only change to the English
may stay English, but the PR says the translations were not updated.
