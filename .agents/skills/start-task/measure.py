#!/usr/bin/env python3
"""Tokens a start-task run spent, per phase and model — the evidence that the split pays off.

Usage: measure.py <claude|codex> [--since 2026-09-27T14:00] [--session <claude session id>]

claude: the newest session transcript of this repository (the one running this), its main thread
        split by model, and every subagent it spawned, labelled by agent type.
codex:  every root thread started in this repository since --since, and the threads each spawned.
--since defaults to the `Started:` line of the newest plan in .agents/plans/.
"""
import collections, datetime, glob, json, os, re, sqlite3, subprocess, sys

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True).stdout.strip()


def local(text):
    """An ISO time as naive local time, whether or not it carries an offset."""
    t = datetime.datetime.fromisoformat(text.replace("Z", "+00:00"))
    return t.astimezone().replace(tzinfo=None) if t.tzinfo else t


def since_arg():
    if "--since" in sys.argv:
        return local(sys.argv[sys.argv.index("--since") + 1])
    plans = sorted(glob.glob(os.path.join(ROOT, ".agents", "plans", "*.md")), key=os.path.getmtime)
    for plan in reversed(plans):
        m = re.search(r"^Started:\s*(\S+)", open(plan).read(), re.M)
        if m:
            return local(m.group(1))
    return None


def stamp(entry):
    ts = entry.get("timestamp")
    return local(ts) if ts else None


def usage(path, since):
    # Claude Code writes one line per content block of a reply, each repeating the reply's usage;
    # count every reply once, by its message id.
    replies = {}
    for n, line in enumerate(open(path, errors="ignore")):
        try:
            e = json.loads(line)
        except ValueError:
            continue
        m = e.get("message")
        if not isinstance(m, dict) or not m.get("usage"):
            continue
        if since and (stamp(e) or since) < since:
            continue
        replies[m.get("id") or n] = m
    rows = collections.defaultdict(collections.Counter)
    for m in replies.values():
        u, c = m["usage"], rows[m.get("model", "?")]
        c["turns"] += 1
        c["cache read"] += u.get("cache_read_input_tokens") or 0
        c["cache write"] += u.get("cache_creation_input_tokens") or 0
        c["uncached in"] += u.get("input_tokens") or 0
        c["output"] += u.get("output_tokens") or 0
    return rows


def claude(since):
    slug = re.sub(r"[^A-Za-z0-9]", "-", ROOT)
    sessions = sorted(glob.glob(os.path.expanduser(f"~/.claude/projects/{slug}/*.jsonl")), key=os.path.getmtime)
    if not sessions:
        sys.exit("no Claude transcript for this repository")
    main = sessions[-1]
    if "--session" in sys.argv:
        main = next(s for s in sessions if os.path.basename(s).startswith(sys.argv[sys.argv.index("--session") + 1]))
    table = [("main session", model, c) for model, c in usage(main, since).items()]
    for sub in sorted(glob.glob(main[:-6] + "/subagents/*.jsonl")):
        try:
            kind = json.load(open(sub[:-6] + ".meta.json")).get("agentType", "subagent")
        except (OSError, ValueError):
            kind = "subagent"
        table += [(kind, model, c) for model, c in usage(sub, since).items()]
    cols = ["turns", "cache read", "cache write", "uncached in", "output"]
    print(f"{'phase':14} {'model':22} " + " ".join(f"{c:>12}" for c in cols) + f" {'total':>12}")
    for phase, model, c in table:
        if c["turns"]:
            total = sum(c[k] for k in cols[1:])
            print(f"{phase:14} {model:22} " + " ".join(f"{c[k]:>12,}" for k in cols) + f" {total:>12,}")
    print("\nCache reads are the whole context re-read each turn; they dominate. A subagent pays off when"
          "\nits cache reads are far below what the main session would have re-read over the same turns.")


def codex(since):
    db = sqlite3.connect(os.path.expanduser("~/.codex/state_5.sqlite"))
    children = collections.defaultdict(list)
    for parent, child in db.execute("select parent_thread_id, child_thread_id from thread_spawn_edges"):
        children[parent].append(child)
    spawned = {c for cs in children.values() for c in cs}
    info = {r[0]: r[1:] for r in db.execute("select id, created_at, model, reasoning_effort, tokens_used, title from threads")}
    cutoff = since.timestamp() if since else 0
    roots = [t for t, (created, _, _, _, _) in info.items()
             if t not in spawned and created >= cutoff
             and db.execute("select cwd from threads where id=?", (t,)).fetchone()[0] == ROOT]

    def walk(thread, depth):
        created, model, effort, tokens, title = info[thread]
        label = "main session" if depth == 0 else "subagent"
        print(f"{'  ' * depth}{label:14} {model or '?':22} {effort or '':7} {tokens:>12,}  {title[:50]}")
        return tokens + sum(walk(c, depth + 1) for c in children.get(thread, []) if c in info)

    print(f"{'phase':14} {'model':22} {'effort':7} {'tokens':>12}  title")
    grand = sum(walk(r, 0) for r in sorted(roots, key=lambda t: info[t][0]))
    print(f"{'':45}{grand:>12,}  total (Codex records one number per thread, no cache split)")


if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in ("claude", "codex"):
        sys.exit(__doc__)
    (claude if sys.argv[1] == "claude" else codex)(since_arg())
