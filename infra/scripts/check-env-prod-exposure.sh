#!/usr/bin/env bash
# check-env-prod-exposure.sh — which production secrets leaked in public git history are still live?
#
# Context (CICD-06, docs/process/security-incident-2026-11-env-prod.md): `.env.prod` was committed in
# 29874bcb (2026-03-16) and untracked in 3925f708 (2026-03-28). The repository is public, so every
# value in that file is readable by anyone. This script answers one question per key — is the value
# that leaked the value production uses today? — WITHOUT ever printing, logging or writing a value.
#
# Run it ON THE PRODUCTION HOST, from the deploy checkout:
#   cd /opt/projects/Muhabbet && bash infra/scripts/check-env-prod-exposure.sh            # live file: .env.prod
#   bash infra/scripts/check-env-prod-exposure.sh /path/to/.env.prod                      # explicit live file
#
# Output, one line per key that existed in the leaked file:
#   JWT_SECRET            SAME       <- still the leaked value: ROTATE NOW
#   POSTGRES_PASSWORD     DIFFERENT  <- changed since the leak
#   SOME_KEY              MISSING    <- no longer in the live file
# Exit status: 0 = nothing still live, 2 = at least one SAME, 1 = could not run.
#
# How values are compared: both sides are reduced to SHA-256 digests in memory (bash variables only;
# no temp files, no `set -x`), and only the digests are compared. Surrounding quotes and a leading
# `export ` are stripped on both sides so formatting differences do not hide a reused value.
set -euo pipefail
set +x

readonly LEAK_COMMIT="29874bcb427090177c9bf738fe1dc2ec9eca3d2e"
readonly REPO_SLUG="ahmetabdullahgultekin/Muhabbet"
LIVE_FILE="${1:-.env.prod}"

die() { echo "error: $*" >&2; exit 1; }

[[ -r "$LIVE_FILE" ]] || die "live env file '$LIVE_FILE' not readable (run from /opt/projects/Muhabbet or pass the path)"
command -v sha256sum >/dev/null || die "sha256sum not found"

# 1. Obtain the leaked file. Prefer local git history (no network); fall back to the public raw URL.
leaked=""
if git cat-file -e "${LEAK_COMMIT}^{commit}" 2>/dev/null; then
  leaked="$(git show "${LEAK_COMMIT}:.env.prod")" || die "git show failed"
else
  command -v curl >/dev/null || die "commit not in local history and curl is missing (try: git fetch --unshallow)"
  leaked="$(curl -fsS "https://raw.githubusercontent.com/${REPO_SLUG}/${LEAK_COMMIT}/.env.prod")" \
    || die "could not fetch the leaked file from GitHub"
fi
[[ -n "$leaked" ]] || die "leaked file is empty — nothing to compare"

# 2. Parse KEY=VALUE lines into an associative array of digests. Values are never echoed.
normalise() {          # $1 = raw value -> stdout: value with surrounding quotes removed
  local v="$1"
  v="${v%$'\r'}"
  if [[ "$v" =~ ^\"(.*)\"$ || "$v" =~ ^\'(.*)\'$ ]]; then v="${BASH_REMATCH[1]}"; fi
  printf '%s' "$v"
}
digest() { printf '%s' "$1" | sha256sum | cut -d' ' -f1; }

declare -A LEAKED LIVE
parse_into() {         # $1 = name of assoc array, stdin = env file content
  local -n target="$1"
  local line key val
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#export }"
    [[ -z "${line//[[:space:]]/}" || "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" == *=* ]] || continue
    key="${line%%=*}"; key="${key//[[:space:]]/}"
    val="$(normalise "${line#*=}")"
    target["$key"]="$(digest "$val")"
  done
}
parse_into LEAKED <<<"$leaked"
parse_into LIVE < "$LIVE_FILE"
unset leaked

# 3. Report.
status=0
printf '%-32s %s\n' "KEY" "STATUS (leaked value vs live value)"
for key in $(printf '%s\n' "${!LEAKED[@]}" | sort); do
  if [[ -z "${LIVE[$key]+x}" ]]; then
    printf '%-32s %s\n' "$key" "MISSING"
  elif [[ "${LIVE[$key]}" == "${LEAKED[$key]}" ]]; then
    printf '%-32s %s\n' "$key" "SAME       <- still the leaked value: ROTATE"
    status=2
  else
    printf '%-32s %s\n' "$key" "DIFFERENT"
  fi
done
if [[ $status -eq 2 ]]; then
  echo
  echo "At least one leaked value is still live. Follow docs/process/security-incident-2026-11-env-prod.md."
fi
exit $status
