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
# We populate that tree with `git clone --depth 1 --branch <ref>` against github.com, which is a
# different host from codeload and is not throttled. The result is byte-identical to what the runner
# would have extracted from the tarball: the tarball is just the tree at that ref.
#
# THE TRADE-OFF, STATED PLAINLY: a seeded action is pinned. The runner will never re-download it, so
# if `v4` moves — including for a security fix — this host keeps running the old one until the cache
# is refreshed. That is why this is a script you re-run rather than a thing you did once. Run it
# after any runner reinstall, and on a schedule you are willing to defend; `--force` re-clones.
#
# Usage:
#   ./seed-runner-action-cache.sh [--force] [runner-dir ...]
# Defaults to every /opt/actions-runner* on the host.

set -euo pipefail

# Every action referenced by .github/workflows/*.yml, as <owner>/<repo>@<ref>. A sub-action such as
# `gradle/actions/setup-gradle@v4` or `github/codeql-action/init@v3` caches under its *repository*,
# not its path, so it is listed once here as `gradle/actions@v4` / `github/codeql-action@v3`.
ACTIONS=(
  "actions/checkout@v4"
  "actions/setup-java@v4"
  "actions/upload-artifact@v4"
  "appleboy/ssh-action@v1"
  "aquasecurity/trivy-action@master"
  "docker/build-push-action@v6"
  "docker/setup-buildx-action@v3"
  "github/codeql-action@v3"
  "gitleaks/gitleaks-action@v2"
  "gradle/actions@v4"
  "madrapps/jacoco-report@v1.7.1"
  "mikepenz/action-junit-report@v5"
)

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

    if git clone --quiet --depth 1 --branch "$ref" "https://github.com/$repo" "$dest" 2>/dev/null; then
      rm -rf "$dest/.git"
      # The runner writes a timestamp here. It never reads the contents — only File.Exists — but
      # matching the format keeps the directory legible to whoever looks at it next.
      date +"%-m/%-d/%Y %-I:%M:%S %p" > "$marker"
      echo "   seed  $spec"
      seeded=$((seeded + 1))
    else
      echo "   FAIL  $spec (could not clone $ref)" >&2
      rm -rf "$dest"
      failed=$((failed + 1))
    fi
  done
done

echo
echo "seeded=$seeded skipped=$skipped failed=$failed"
[ "$failed" -eq 0 ]
