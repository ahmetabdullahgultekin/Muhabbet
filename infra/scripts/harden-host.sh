#!/usr/bin/env bash
# harden-host.sh — security sweep for a host that runs (or ran) Muhabbet (#745).
#
# Default mode is READ-ONLY and prints a report:
#   1. every env file and every running container that still holds a value known to have leaked
#      in the public git history (compared by SHA-256; no value is ever printed);
#   2. the self-hosted GitHub Actions runners on this host and which repository each serves;
#   3. anonymous (unauthenticated) access rules on the shared MinIO buckets.
# With --apply it also:
#   a. stops and disables the runner service(s) serving ahmetabdullahgultekin/Muhabbet on this host
#      (a production host must not run CI jobs: any job can read the secrets and the Docker socket);
#   b. removes anonymous access from muhabbet-media (thumbnails are served by presigned URL already);
#   c. downgrades any anonymous rule on other buckets that allows LISTING or WRITING to download-only;
#   d. with --retire, if no Muhabbet container runs here, shreds /opt/projects/Muhabbet/.env.prod.
#
# Usage:  sudo bash harden-host.sh              # report only
#         sudo bash harden-host.sh --apply      # report, then fix a–c
#         sudo bash harden-host.sh --apply --retire   # on a host Muhabbet no longer runs on
set -euo pipefail
set +x
umask 077

APPLY=0; RETIRE=0
for a in "$@"; do case "$a" in --apply) APPLY=1 ;; --retire) RETIRE=1 ;; *) echo "unknown option $a" >&2; exit 1 ;; esac; done
[[ $EUID -eq 0 ]] || { echo "run as root" >&2; exit 1; }

# SHA-256 of every value that has been public in the repository's history, with where it leaked.
LEAKED=(
  "cf376fc5d0c5738d7aaa7a6d275a2e22a089c7e2a56e7a082b55f939d0d0e586 29874bcb/.env.prod:POSTGRES_PASSWORD,docs/PROD_READINESS:POSTGRES_PASSWORD"
  "05c770585d09d84389959c5e0c8e6472a6daf660d40ef08c6e236f571eba6d55 29874bcb/.env.prod:REDIS_PASSWORD,docs/PROD_READINESS:REDIS_PASSWORD"
  "1d63aae854006e6ec5036b051bbe8d0f65d99b61e51e843888ac25fa9a96b123 29874bcb/.env.prod:JWT_SECRET"
  "5486b2f74cfff10f465f21d6887a189a21c7c748470e4b8ae39eddd101e7fc83 29874bcb/.env.prod:MINIO_ROOT_PASSWORD"
  "8d661cc193dfbdfd11f3acfe2aac33551ab758c958bb23f6d5857905c4ab1e72 e7bbb827/test_bot.py:JWT_SECRET"
  "9dce89c743fb41ef6fb310104f7f4b4509cfea27e05dffa9982368c0cd5e5eeb docs/PROD_READINESS:JWT_SECRET"
)
declare -A LEAK; for e in "${LEAKED[@]}"; do LEAK["${e%% *}"]="${e#* }"; done
sha() { printf '%s' "$1" | sha256sum | cut -d' ' -f1; }
strip() { local v="${1%$'\r'}"; if [[ "$v" =~ ^\"(.*)\"$ || "$v" =~ ^\'(.*)\'$ ]]; then v="${BASH_REMATCH[1]}"; fi; printf '%s' "$v"; }

FOUND=0
check_line() {   # source, line
  local line="${2#export }" k v h
  [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]] || return 0
  k="${line%%=*}"; v="$(strip "${line#*=}")"; [[ ${#v} -ge 8 ]] || return 0
  h="$(sha "$v")"
  if [[ -n "${LEAK[$h]+x}" ]]; then echo "  LEAKED VALUE IN USE: $1 : $k  (leaked as ${LEAK[$h]})"; FOUND=1; fi
}

echo "== 1. Leaked values still present (env files under /opt/projects /home /root /etc)"
while IFS= read -r -d '' f; do
  while IFS= read -r line || [[ -n "$line" ]]; do check_line "$f" "$line"; done < "$f"
done < <(find /opt/projects /home /root /etc -xdev \( -name .git -o -name node_modules -o -name build -o -name target \) -prune -o           -type f \( -name '.env' -o -name '.env.*' -o -name '*.env' \) -size -1M -print0 2>/dev/null)
echo "== 1b. Leaked values in running containers' environment"
for c in $(docker ps --format '{{.Names}}' 2>/dev/null); do
  while IFS= read -r line; do [[ -n "$line" ]] && check_line "container $c" "$line"; done     < <(docker inspect "$c" --format '{{range .Config.Env}}{{println .}}{{end}}')
done
(( FOUND )) && echo "  -> rotate every value above (see docs/process/security-incident-2026-11-env-prod.md)" || echo "  none found"

echo "== 2. Self-hosted GitHub Actions runners on this host"
RUNNER_SVCS=()
while IFS= read -r -d '' rf; do
  dir="$(dirname "$rf")"
  url="$(tr -d '\0\357\273\277' < "$rf" | grep -o '"gitHubUrl"[^,}]*' | sed 's/.*: *"//;s/"$//')"
  name="$(tr -d '\0\357\273\277' < "$rf" | grep -o '"agentName"[^,}]*' | sed 's/.*: *"//;s/"$//')"
  svc=""; [[ -f "$dir/.service" ]] && svc="$(cat "$dir/.service")"
  state="$( [[ -n "$svc" ]] && systemctl is-active "$svc" 2>/dev/null || echo unknown)"
  echo "  $dir  name=$name  repo=$url  service=${svc:-?}  state=$state"
  if [[ "$url" == *"/ahmetabdullahgultekin/Muhabbet"* && -n "$svc" ]]; then RUNNER_SVCS+=("$svc"); fi
done < <(find / -xdev -maxdepth 4 -name .runner -type f -print0 2>/dev/null)

echo "== 3. Anonymous access on MinIO buckets"
MINIO=""
if docker inspect shared-minio >/dev/null 2>&1; then MINIO=shared-minio; fi
mc_root() { docker exec "$MINIO" sh -c 'mc alias set r http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1 || exit 91; mc "$@"; rc=$?; mc alias rm r >/dev/null 2>&1; exit $rc' sh "$@"; }
if [[ -n "$MINIO" ]]; then
  for b in $(mc_root ls r --json | sed -n 's/.*"key":"\([^"/]*\)\/".*/\1/p'); do
    rules="$(mc_root anonymous list "r/$b" 2>/dev/null || true)"
    echo "  bucket $b: ${rules:-<no anonymous access>}" | sed 's/^/ /'
  done
else
  echo "  no shared-minio container on this host"
fi

(( APPLY )) || { echo; echo "Report only. Re-run with --apply to fix sections 2 and 3."; exit 0; }

echo; echo "== APPLY a. stop Muhabbet runner(s) on this host"
if ((${#RUNNER_SVCS[@]})); then
  for s in "${RUNNER_SVCS[@]}"; do systemctl stop "$s"; systemctl disable "$s" >/dev/null 2>&1 || true
    echo "  stopped and disabled $s ( $(systemctl is-active "$s" 2>/dev/null || true) )"; done
  echo "  NOTE: also remove it in GitHub: repo Settings -> Actions -> Runners -> the runner -> Remove."
else echo "  no Muhabbet runner service found"; fi

if [[ -n "$MINIO" ]]; then
  echo "== APPLY b. muhabbet-media: no anonymous access"
  mc_root anonymous set none r/muhabbet-media >/dev/null
  mc_root anonymous set none r/muhabbet-media/thumbnails >/dev/null
  echo "  now: $(mc_root anonymous list r/muhabbet-media 2>/dev/null || true)"
  echo "== APPLY c. other buckets: listing/writing -> download only"
  for b in $(mc_root ls r --json | sed -n 's/.*"key":"\([^"/]*\)\/".*/\1/p'); do
    [[ "$b" == muhabbet-media ]] && continue
    base="$(mc_root anonymous get "r/$b" 2>/dev/null | sed -n "s/.*is \x60\(.*\)\x60.*/\1/p")"
    case "$base" in public|upload|custom) mc_root anonymous set download "r/$b" >/dev/null; echo "  $b: $base -> download";; *) echo "  $b: ${base:-none} (unchanged)";; esac
  done
fi

if (( RETIRE )); then
  echo "== APPLY d. retire stale Muhabbet secrets on this host"
  if docker ps --format '{{.Names}}' | grep -q '^muhabbet-backend$'; then echo "  muhabbet-backend runs here — NOT retiring"; 
  elif [[ -f /opt/projects/Muhabbet/.env.prod ]]; then shred -u /opt/projects/Muhabbet/.env.prod && echo "  shredded /opt/projects/Muhabbet/.env.prod"; 
  else echo "  no /opt/projects/Muhabbet/.env.prod"; fi
fi
echo "done."
