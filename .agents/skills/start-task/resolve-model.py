#!/usr/bin/env python3
"""Pick a model for a start-task tier from what a harness offers right now.

Usage: resolve-model.py <codex|grok> <strong|fast> [--no-ping] [--refresh]

Prints `<model> <effort>` and exits 0, or prints nothing and exits 3 when no candidate is available
(the caller then spawns without a model, which inherits the main session's). Candidates come from
.agents/models.json; the catalog from the harness itself; availability from a one-word ping, cached
for the day in ~/.cache/clickify/models/ so a task pays for it at most once. A failed ping is cached
too; --refresh pings again, for a model that has since recovered or been given credit.
"""
import datetime, fnmatch, json, os, re, subprocess, sys, tempfile

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True).stdout.strip() or "."
CACHE = os.path.expanduser("~/.cache/clickify/models")
PING = "Reply with the single word OK."


def catalog(harness):
    try:
        return listed(harness)
    except (OSError, ValueError, subprocess.SubprocessError):
        return []  # the harness is not installed, or did not answer: nothing is offered


def listed(harness):
    if harness == "codex":
        # The provider's own list, refreshed each time Codex starts; the bundled list is the fallback.
        path = os.path.expanduser("~/.codex/models_cache.json")
        try:
            models = json.load(open(path))["models"]
        except (OSError, ValueError, KeyError):
            out = subprocess.run(["codex", "debug", "models"], capture_output=True, text=True).stdout
            models = json.loads(out or "{}").get("models", [])
        return [m["slug"] for m in models if m.get("visibility", "list") == "list"]
    out = subprocess.run(["grok", "models"], capture_output=True, text=True, timeout=60).stdout
    return re.findall(r"^\s*[*-]\s+(\S+)", out, re.M)


def version_key(name):
    return [int(p) if p.isdigit() else p for p in re.split(r"(\d+)", name)]


def answers(harness, model):
    today = datetime.date.today().isoformat()
    mark = os.path.join(CACHE, harness, model)
    try:
        day, verdict = open(mark).read().split()
        if day == today and "--refresh" not in sys.argv:
            return verdict == "ok"
    except (OSError, ValueError):
        pass
    # Ping from an empty directory, so no AGENTS.md or skill list is loaded and paid for.
    with tempfile.TemporaryDirectory() as empty:
        if harness == "codex":
            cmd = ["codex", "exec", "--skip-git-repo-check", "-C", empty, "-m", model,
                   "-c", "model_reasoning_effort=low", PING]
        else:
            cmd = ["grok", "-p", PING, "-m", model]
        try:
            out = subprocess.run(cmd, capture_output=True, text=True, timeout=120, cwd=empty,
                                 stdin=subprocess.DEVNULL).stdout
        except (OSError, subprocess.SubprocessError):
            out = ""
    ok = any(line.strip().strip("*.") == "OK" for line in out.splitlines())
    os.makedirs(os.path.dirname(mark), exist_ok=True)
    open(mark, "w").write(f"{today} {'ok' if ok else 'fail'}\n")
    return ok


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 2 or args[0] not in ("codex", "grok") or args[1] not in ("strong", "fast"):
        sys.exit(__doc__)
    harness, tier = args
    prefs = json.load(open(os.path.join(ROOT, ".agents", "models.json")))
    effort = prefs["tiers"][tier]["effort"]
    offered = catalog(harness)
    tried = set()
    for pattern in prefs.get(harness, {}).get(tier, []):
        for model in sorted(fnmatch.filter(offered, pattern), key=version_key, reverse=True):
            if model in tried:
                continue
            tried.add(model)
            if "--no-ping" in sys.argv or answers(harness, model):
                print(model, effort)
                return
            print(f"{model}: listed but did not answer", file=sys.stderr)
    print(f"no {tier} model available for {harness}; spawn without a model to inherit the session's", file=sys.stderr)
    sys.exit(3)


if __name__ == "__main__":
    main()
