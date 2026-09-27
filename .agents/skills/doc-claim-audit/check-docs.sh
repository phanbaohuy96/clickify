#!/usr/bin/env bash
# Mechanical half of a doc-claim audit: relative links that point at nothing, and requirement
# identifiers that no SDD file defines. Run from the repository root.
#
# Usage: .agents/skills/doc-claim-audit/check-docs.sh [file.md ...]   (default: every tracked .md)
# Prints one line per problem and exits 1 if there were any. Silence means both checks ran and
# found nothing — the identifier set is printed first so an empty set cannot pass vacuously.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

defined=$(grep -rhoE '\*\*[A-Z]{2}-[0-9]+\*\*|^\| *`?[A-Z]{2}-[0-9]+|^#+ *[A-Z]{2}-[0-9]+|~~[A-Z]{2}-[0-9]+~~' \
  docs/sdd android/docs/sdd | grep -oE '[A-Z]{2}-[0-9]+' | sort -u)
count=$(printf '%s\n' "$defined" | grep -c .)
echo "requirement identifiers defined: $count"
[ "$count" -gt 0 ] || { echo "no identifiers found — the pattern no longer matches the SDD"; exit 2; }
# Every prefix the SDD defines, so a new registry is checked without editing this script.
prefixes=$(printf '%s\n' "$defined" | cut -d- -f1 | sort -u | paste -sd'|' -)
echo "prefixes: $prefixes"

files=("$@")
if [ $# -eq 0 ]; then while IFS= read -r f; do files+=("$f"); done < <(git ls-files "*.md"); fi
problems=0
for f in "${files[@]}"; do
  dir=$(dirname "$f")
  while read -r target; do
    [ -z "$target" ] && continue
    [ -e "$dir/$target" ] || { echo "$f: link to $target — missing"; problems=$((problems + 1)); }
  done < <(grep -oE '\]\([^)#: ]+(#[^)]*)?\)' "$f" | sed -E 's/^\]\(//; s/\)$//; s/#.*//')
  while read -r id; do
    printf '%s\n' "$defined" | grep -qx "$id" || { echo "$f: cites $id — not defined in any SDD"; problems=$((problems + 1)); }
  done < <(grep -oE "\\b($prefixes)-[0-9]+\\b" "$f" | sort -u)
done
[ "$problems" -eq 0 ]
