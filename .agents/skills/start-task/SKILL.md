---
name: start-task
description: Run one Clickify task end to end — grill the task into a written plan with grill-with-docs on a strong model, hand the plan to a cheaper implementer subagent (or do a small task in place), check its work, ask the user before committing and opening a pull request, review that pull request with a separate reviewer subagent, and report the tokens each phase spent. Use when the user says "start task", "bắt đầu task", or hands over an issue or feature to take from idea to reviewed PR.
---

# Start a task

The strong model thinks; a cheaper model types, in a clean context; a fresh model reviews. Every rule
in `AGENTS.md` still applies — above all, nothing is committed, pushed or opened without asking.

## Why this saves, and when it does not

Measured on this repository's sessions: about 99% of tokens are **cache reads** — the whole context
re-read on every turn. So what a task costs is context length × turns, far more than which model
writes the code. The implementer saves mainly because it starts clean, carrying the plan and not the
grilling transcript; the cheaper model saves again on top.

But a spawn is not free: starting a subagent and reading its role cost 50–90k tokens on Claude and
about 66k on Codex. So a **small** task is done in the main session, and every run ends with
`measure.py`, so the claim stays measured.

## Models: tiers, resolved at run time

Two tiers, `strong` and `fast`. What each harness uses for them:

| Harness | `fast` → implementer | `strong` → reviewer | How it is chosen |
|---|---|---|---|
| Claude Code | `sonnet`, effort medium | `opus`, effort high | aliases in `.claude/agents/*.md`, always the newest model |
| Codex | resolved | resolved | `resolve-model.py codex <tier>` → `<model> <effort>` |
| Grok | resolved | resolved | `resolve-model.py grok <tier>` → `<model>`; effort is inherited |

`resolve-model.py` reads the preference list in `.agents/models.json`, takes the harness's live
catalog, and returns the first candidate that answers a one-word ping (cached for the day — a ping
costs about 33k tokens on Codex). Exit code 3 means nothing answered: spawn without a model, which
inherits the main session's.

The main session's model is chosen when it starts, and this skill cannot switch it. Start it on the
`strong` tier. Claude: `claude --model opus`. Codex:

```bash
read model effort < <(.agents/skills/start-task/resolve-model.py codex strong)
codex -m "$model" -c model_reasoning_effort="$effort"
```

## Phase 1 — Plan (main session)

1. Create the branch: `git switch -c <type>/<slug>` from an up-to-date `main`. A branch is not a
   commit; no need to ask.
2. Run the **grill-with-docs** skill on the task (user level, `~/.agents/skills/`). If it is not
   available, grill inline: one question at a time, each with your recommendation, codebase
   questions answered by reading the code, terms checked against `CONTEXT.md`.
3. Write `.agents/plans/<slug>.md` (git-ignored), starting with `Started: <local ISO time>` so
   `measure.py` knows where the task began, then:
   - **Size** — `small` (at most ~3 files, no new behaviour, no new requirement) or `normal`.
   - **Goal** — one paragraph, in the glossary's words.
   - **Requirements** — each ID to add or change and its new wording (`spec-first-change`).
   - **Changes** — file by file, what and why. Name every file.
   - **Strings** — every new user-facing string in all five languages, or "none".
   - **Verify** — the exact commands from `prove-a-change`, and what passing looks like.
   - **Out of scope** — what the implementer must not touch.
   - **Open questions** — must be empty before phase 2.
4. Show the plan, with its Size, and wait for the user's go-ahead.

## Phase 2 — Implement

**Size `small`:** do it yourself in this session, following `roles/implementer.md` as if it were
your brief. A spawn would cost more than it saves.

**Size `normal`:** spawn the implementer with a prompt that holds only the plan path and the branch
name, starting "Read `.agents/skills/start-task/roles/implementer.md`; it is your role."

- Claude Code: `subagent_type: implementer`.
- Codex: `spawn_agent` with `model` and `reasoning_effort` from `resolve-model.py codex fast`.
  Timeouts must be whole numbers — `120000.0` is rejected.
- Grok: `spawn_subagent` with `model` from `resolve-model.py grok fast`.

If the implementer reports the plan wrong or blocked, go back to phase 1 with the user; do not patch
the design yourself.

## Phase 3 — Check (main session)

Do not take the report on trust (law 2): `git diff --stat` matches **Changes** and nothing else
moved; re-run the **Verify** commands yourself; run
`.agents/skills/doc-claim-audit/check-docs.sh` on any changed document. Small fixes you may make;
anything that changes the design goes back to the user.

## Phase 4 — Commit and pull request (ask first)

Show the diff summary, the verify output and the proposed commit message in the repository's voice
(`AGENTS.md` → *Git and GitHub*). Ask: **commit, push and open a pull request?** On a yes: commit
(with any attribution your harness requires), push with `-u`, `gh pr create` with
`.github/pull_request_template.md` filled in completely, and give the user the URL. On a no, stop
and say what is left uncommitted.

## Phase 5 — Review the pull request

Ask whether to start the review (default yes). Save the PR where a sandboxed subagent can read it:

```bash
gh pr view <n> --json number,title,body,headRefName,baseRefName,files > .agents/plans/<slug>.pr.json
gh pr diff <n> > .agents/plans/<slug>.pr.diff
```

Spawn the reviewer with those two paths and the plan path, starting "Read
`.agents/skills/start-task/roles/reviewer.md`; it is your role." — Claude: `subagent_type:
reviewer`; Codex: `resolve-model.py codex strong`; Grok: `resolve-model.py grok strong`. A review is
never skipped for size: it is the one phase that brings fresh eyes.

Relay its findings, most severe first, and ask what to do: fix them (small ones yourself, larger ones
through another implementer run with an amended plan), post them as PR comments, or leave them.
Posting to GitHub is outward-facing — only on a yes. On Claude Code the user can also run
`/code-review <PR number>` for a second opinion.

## Phase 6 — Report what it cost

```bash
.agents/skills/start-task/measure.py claude    # or: codex
```

Show the table. It reads `Started:` from the plan, so it covers this task only. Say plainly whether
the implementer's cache reads were well below the main session's; if not, the task was too small to
split, and the next one like it should be marked `small`. Grok is not measured yet.
