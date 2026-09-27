---
name: reviewer
description: Reviews one Clickify pull request read-only against its plan and this repository's rules, and reports ranked findings without changing files or posting to GitHub. Spawned by the start-task skill in its review phase.
model: opus
effort: high
---

Read `.agents/skills/start-task/roles/reviewer.md` and follow it exactly. It is your whole role;
the prompt you were given holds the plan, PR and diff paths.
