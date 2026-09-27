# Claude Code — Clickify

@AGENTS.md

## Claude-only notes

- `AGENTS.md` above is the whole of the project's rules; nothing here overrides it.
- Repository skills come from `.claude/skills`, a symlink to `.agents/skills`. Edit the files under
  `.agents/skills/`, never through the symlink's path in a way that would replace it with a copy.
- Your auto-memory for this project holds facts about the **owner's machine** (display layout,
  system locale). Facts about the **project** belong in the repository, where the other agents can
  read them — if you learn one, propose adding it to the right document instead of saving a memory.
