#!/usr/bin/env bash
#
# Seed a self-hosted runner's action cache so jobs stop downloading from codeload.
#
# Why this exists (#563): on 2026-08-17 every job on `muhabbet-cx43` died in *Set up job* with
#     Failed to download action 'https://codeload.github.com/actions/checkout/tar.gz/<sha>'.
#     Error: Response status code does not indicate success: 429 (Too Many Requests).
# It fails before any step of ours runs, so it reads as a build failure with no log to explain it —
# the exact shape that teaches people to ignore red checks. Re-running does not help while the limit
# holds, and `curl` to the same URL from the runner returns 429 on its own, so it is the host and the
# hostname, not the workflow.
#
# The runner already has the escape hatch. Before downloading, `ActionManager` checks for a watermark
# file next to the extracted action:
#     _work/_actions/<owner>/<repo>/<ref>          <- the action tree
#     _work/_actions/<owner>/<repo>/<ref>.completed <- "already have it, skip the download"
# and returns early if it exists. Runner 2.336.0 has no ACTIONS_RUNNER_ACTION_ARCHIVE_CACHE knob
# (checked: the string is in none of its assemblies), so the watermark is the mechanism available.
#
# We populate that tree from github.com, which is a different host from codeload and is not
# throttled: `git fetch --depth 1 origin <sha>` followed by a checkout of FETCH_HEAD. The result is
# byte-identical to what the runner would have extracted from the tarball: the tarball is just the
# tree at that commit.
#
# Every `uses:` is pinned to a full commit SHA (F35), and the runner caches a SHA-pinned action under
# that SHA, not under the tag in the trailing comment. So the list below is read from the workflows
# themselves rather than kept by hand: a hand-kept `@v4` list stopped matching anything the moment
# the pins landed, and the cache silently went unused.
#
# Because every ref is a SHA, a seeded copy can never go stale under its own name: when Dependabot
# bumps a pin, the workflow names a new SHA, the cache has no entry for it, and the runner downloads
# it (or this script seeds it on its next run). Re-run after any runner reinstall and after merging
# an action bump; `--force` re-fetches everything.
#
# Usage:
#   ./seed-runner-action-cache.sh [--force] [runner-dir ...]
# Defaults to every /opt/actions-runner* on the host.

set -euo pipefail

# Every action referenced by .github/workflows/*.yml, as <owner>/<repo>@<sha>. A sub-action such as
# `gradle/actions/setup-gradle@<sha>` caches under its *repository*, not its path, so the path is
# dropped and duplicates collapse. Override the directory with WORKFLOWS_DIR when running the script
# from somewhere other than a checkout of this repository.
WORKFLOWS_DIR="${WORKFLOWS_DIR:-$(cd "$(dirname "$0")/../.." && pwd)/.github/workflows}"
if [ ! -d "$WORKFLOWS_DIR" ]; then
  echo "Workflow directory not found: $WORKFLOWS_DIR (set WORKFLOWS_DIR)." >&2
  exit 1
fi
mapfile -t ACTIONS < <(
  grep -hoE 'uses:[[:space:]]*[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(/[A-Za-z0-9_./-]+)?@[0-9a-f]{40}' \
      "$WORKFLOWS_DIR"/*.yml "$WORKFLOWS_DIR"/*.yaml 2>/dev/null \
    | sed -E 's/^uses:[[:space:]]*//; s#^([^/]+/[^/@]+)[^@]*@#\1@#' \
    | sort -u
)
if [ ${#ACTIONS[@]} -eq 0 ]; then
  echo "No SHA-pinned actions found under $WORKFLOWS_DIR." >&2
  exit 1
fi

FORCE=0
RUNNER_DIRS=()
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=1 ;;
    *) RUNNER_DIRS+=("$arg") ;;
  esac
done

if [ ${#RUNNER_DIRS[@]} -eq 0 ]; then
  # `ls -d` on a glob that matches nothing would leave the literal pattern, hence the -d test.
  for d in /opt/actions-runner*; do
    [ -d "$d/_work" ] && RUNNER_DIRS+=("$d")
  done
fi

if [ ${#RUNNER_DIRS[@]} -eq 0 ]; then
  echo "No runner directory found (looked for /opt/actions-runner*/_work)." >&2
  exit 1
fi

seeded=0
skipped=0
failed=0

for runner in "${RUNNER_DIRS[@]}"; do
  cache="$runner/_work/_actions"
  echo "== $cache"
  mkdir -p "$cache"

  for spec in "${ACTIONS[@]}"; do
    repo="${spec%@*}"
    ref="${spec##*@}"
    dest="$cache/$repo/$ref"
    marker="$dest.completed"

    if [ -f "$marker" ] && [ "$FORCE" -eq 0 ]; then
      echo "   skip  $spec (already cached)"
      skipped=$((skipped + 1))
      continue
    fi

    rm -rf "$dest" "$marker"
    mkdir -p "$(dirname "$dest")"

    if git init --quiet "$dest" \
        && git -C "$dest" fetch --quiet --depth 1 "https://github.com/$repo" "$ref" 2>/dev/null \
        && git -C "$dest" -c advice.detachedHead=false checkout --quiet FETCH_HEAD; then
      rm -rf "$dest/.git"
      # The runner writes a timestamp here. It never reads the contents — only File.Exists — but
      # matching the format keeps the directory legible to whoever looks at it next.
      date +"%-m/%-d/%Y %-I:%M:%S %p" > "$marker"
      echo "   seed  $spec"
      seeded=$((seeded + 1))
    else
      echo "   FAIL  $spec (could not fetch $ref)" >&2
      rm -rf "$dest"
      failed=$((failed + 1))
    fi
  done
done

echo
echo "seeded=$seeded skipped=$skipped failed=$failed"
[ "$failed" -eq 0 ]
