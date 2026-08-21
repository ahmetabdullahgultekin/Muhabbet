#!/usr/bin/env bash
#
# Answers exactly one question, on stdout, as `true` or `false`:
#
#     did this pull request touch a file under any of these path prefixes?
#
# WHY THIS EXISTS
#
# `backend-ci.yml` and `mobile-ci.yml` used to carry `paths:` filters on their `pull_request`
# triggers. A filter there does not make a workflow finish quickly — it stops the workflow from
# starting at all, which means GitHub never creates the check run. A pull request that changes only
# documentation therefore produced ZERO checks (observed on #682 and #683), and a check that is
# never created can never be a *required* check: GitHub leaves such a pull request "Expected —
# waiting for status to be reported" forever. So the repository was stuck between two bad states —
# no required checks at all (a red pull request merges, which is how `dev` was broken), or required
# checks that permanently block every docs-only pull request.
#
# Moving the decision from the trigger into the job fixes both. The workflow now starts on every
# pull request, so the check run ALWAYS exists and can be required; the expensive steps ask this
# script whether they are relevant and skip themselves when they are not. An unrelated pull request
# spends about half a minute in checkout plus this script, and reports green.
#
# WHY NOT `dorny/paths-filter`
#
# It is the usual answer and it would work, but a new action is not free here. The self-hosted
# runner's action cache is seeded by hand (`infra/scripts/seed-runner-action-cache.sh`) precisely so
# that jobs stop downloading from codeload, which returned 429 for a whole day and killed every job
# in *Set up job* (#563). Every new action is one more entry to keep seeded and one more thing that
# breaks on the next 429 day if someone forgets. `git diff` is already installed.
#
# HOW THE DIFF IS TAKEN
#
# On a pull request the checkout is `refs/pull/N/merge` — the head merged into the base tip — so
# `git diff <base sha> HEAD` is exactly the set of files the pull request changes. The base commit
# is often missing from a shallow checkout, so it is fetched by SHA first (github.com allows this).
#
# FAILING OPEN IS DELIBERATE
#
# Every error path prints `true`. If this script cannot work out what changed, the answer is "run
# everything", never "skip everything" — a bug here must cost time, not coverage.
#
# Usage:  paths-changed.sh <path-prefix>...
#   env:  GITHUB_EVENT_NAME, BASE_SHA (github.event.pull_request.base.sha)
#
# Prefixes are matched literally against the start of each path, with no globbing: `backend/`
# matches everything in that directory, `build.gradle.kts` matches that file at the repository root.

set -uo pipefail

if [ "$#" -eq 0 ]; then
  echo "usage: paths-changed.sh <path-prefix>..." >&2
  exit 2
fi

note() { echo "paths-changed: $*" >&2; }

# Anything that is not a pull request — a push, a tag, workflow_dispatch, a schedule — has no base
# to diff against, and no reason to economise. Build all of it.
if [ "${GITHUB_EVENT_NAME:-}" != "pull_request" ]; then
  note "event is '${GITHUB_EVENT_NAME:-none}', not a pull request -> everything is relevant"
  echo "true"
  exit 0
fi

base="${BASE_SHA:-}"
if [ -z "$base" ]; then
  note "BASE_SHA is empty -> failing open"
  echo "true"
  exit 0
fi

if ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
  note "base $base not in this checkout, fetching it"
  git fetch --no-tags --quiet --depth=1 origin "$base" 2>/dev/null || true
fi

if ! changed="$(git diff --name-only "$base" HEAD 2>/dev/null)"; then
  note "could not diff against $base -> failing open"
  echo "true"
  exit 0
fi

note "$(printf '%s\n' "$changed" | grep -c '[^[:space:]]') file(s) changed against ${base:0:8}"

matched=false
while IFS= read -r file; do
  [ -n "$file" ] || continue
  for prefix in "$@"; do
    case "$file" in
      "$prefix"*)
        note "match: $file (prefix '$prefix')"
        matched=true
        break 2
        ;;
    esac
  done
done <<EOF
$changed
EOF

echo "$matched"
