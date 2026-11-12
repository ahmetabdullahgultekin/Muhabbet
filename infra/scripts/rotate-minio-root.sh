#!/usr/bin/env bash
# rotate-minio-root.sh — retire the leaked MinIO root password on the production host (#745, A7).
#
# What it does, in order, stopping at the first failure and undoing what it changed:
#   0. Preflight (changes nothing): containers, files, compose wiring, the old password in each place.
#   1. Backs up the three env files it will edit (mode 600, next to the originals).
#   2. Creates a least-privilege MinIO user per application, each limited to its own bucket:
#        muhabbet-app  -> muhabbet-media      mandalean-app -> mandalean-media
#      and proves each can read its own bucket and NOT the other one.
#   3. Points both applications at their new user, recreates their containers, checks them.
#      On failure: restores both env files, recreates the apps again, exits non-zero.
#   4. Replaces the root password with a fresh random one, recreates shared-minio, then proves:
#      the old root password is rejected, the new one works, both app users still work.
#      On failure: restores the root password and recreates shared-minio again.
#
# It never prints, logs or passes on the host command line any secret value. New secrets are random
# 64-hex strings generated here and written only into the env files they belong to.
#
# Run on the production host as root:   sudo bash rotate-minio-root.sh
# Override paths with environment variables if they differ (see "Configuration" below).
set -euo pipefail
set +x
umask 077

# ---------------------------------------------------------------- Configuration
MINIO_CONTAINER="${MINIO_CONTAINER:-shared-minio}"
MINIO_ENV="${MINIO_ENV:-/opt/projects/infra/shared-db/.env}"
MUH_ENV="${MUH_ENV:-/opt/projects/Muhabbet/.env.prod}"
MUH_CONTAINERS="${MUH_CONTAINERS:-muhabbet-backend}"
MUH_BUCKET="${MUH_BUCKET:-muhabbet-media}"
MUH_USER="${MUH_USER:-muhabbet-app}"
MAN_ENV="${MAN_ENV:-/opt/projects/mandalean/deploy/.env.prod}"
MAN_CONTAINERS="${MAN_CONTAINERS:-mandalean-api mandalean-web}"
MAN_BUCKET="${MAN_BUCKET:-mandalean-media}"
MAN_USER="${MAN_USER:-mandalean-app}"
MAN_SECRET_VAR="${MAN_SECRET_VAR:-STORAGE_SECRET_KEY}"
WAIT_SECONDS="${WAIT_SECONDS:-150}"

TS="$(date +%Y%m%d-%H%M%S)"
STAGE="preflight"

say()  { printf '[%s] %s\n' "$STAGE" "$*"; }
die()  { printf '[%s] FAILED: %s\n' "$STAGE" "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run as root (sudo bash $0)"
for c in docker awk grep sed od; do command -v "$c" >/dev/null || die "$c not found"; done
docker compose version >/dev/null 2>&1 || die "docker compose v2 not available"

rand_hex() { od -An -N32 -tx1 /dev/urandom | tr -d ' \n'; }

# ---------------------------------------------------------------- env file helpers
# Value of KEY in FILE (first match, quotes stripped). Never printed by callers.
env_get() {
  awk -v k="$2" '
    { line=$0; sub(/\r$/, "", line); sub(/^[[:space:]]*export[[:space:]]+/, "", line) }
    index(line, k "=") == 1 { v=substr(line, length(k) + 2)
      if (v ~ /^".*"$/ || v ~ /^\x27.*\x27$/) v=substr(v, 2, length(v) - 2)
      print v; exit }' "$1"
}
env_count() { awk -v k="$2" '{ l=$0; sub(/^[[:space:]]*export[[:space:]]+/, "", l) } index(l, k "=") == 1 { n++ } END { print n+0 }' "$1"; }
# Set KEY=VALUE in FILE in place (owner, mode and inode preserved). VALUE must not contain newlines.
env_set() {
  local file="$1" key="$2" val="$3" tmp
  tmp="$(mktemp)"
  KEY="$key" VAL="$val" awk '
    BEGIN { k=ENVIRON["KEY"]; v=ENVIRON["VAL"]; done=0 }
    { l=$0; sub(/^[[:space:]]*export[[:space:]]+/, "", l) }
    index(l, k "=") == 1 { if (!done) { print k "=" v; done=1 }; next }
    { print }
    END { if (!done) print k "=" v }' "$file" > "$tmp"
  cat "$tmp" > "$file"
  rm -f "$tmp"
}
# Name of the one variable in FILE whose value is exactly WANTED (quotes stripped); fails unless unique.
env_key_with_value() {
  local file="$1" wanted="$2" out
  out="$(WANT="$wanted" awk '
    { l=$0; sub(/\r$/, "", l); sub(/^[[:space:]]*export[[:space:]]+/, "", l) }
    l ~ /^[A-Za-z_][A-Za-z0-9_]*=/ { k=l; sub(/=.*/, "", k); v=substr(l, length(k) + 2)
      if (v ~ /^".*"$/ || v ~ /^\x27.*\x27$/) v=substr(v, 2, length(v) - 2)
      if (v == ENVIRON["WANT"]) print k }' "$file")"
  [[ -n "$out" && "$(wc -l <<<"$out")" -eq 1 ]] || return 1
  printf '%s' "$out"
}

# ---------------------------------------------------------------- compose helpers (from container labels)
label() { docker inspect "$1" --format "{{index .Config.Labels \"$2\"}}"; }
# Recreate the given containers exactly the way compose last started them.
recreate() {   # [--default-env FILE] container...
  local default_env=""
  if [[ "$1" == --default-env ]]; then default_env="$2"; shift 2; fi
  local first="$1"; shift
  local wd files project envfiles services=() args=()
  wd="$(label "$first" com.docker.compose.project.working_dir)"
  files="$(label "$first" com.docker.compose.project.config_files)"
  project="$(label "$first" com.docker.compose.project)"
  envfiles="$(label "$first" com.docker.compose.project.environment_file)"
  [[ -n "$wd" && -n "$files" && -n "$project" ]] || die "$first was not started by docker compose"
  local f; IFS=',' read -ra _f <<<"$files"; for f in "${_f[@]}"; do args+=(-f "$f"); done
  if [[ -n "$envfiles" ]]; then IFS=',' read -ra _e <<<"$envfiles"; for f in "${_e[@]}"; do args+=(--env-file "$f"); done
  elif [[ -n "$default_env" ]]; then args+=(--env-file "$default_env"); fi
  local c; for c in "$first" "$@"; do services+=("$(label "$c" com.docker.compose.service)"); done
  docker compose -p "$project" --project-directory "$wd" "${args[@]}" up -d --no-deps --force-recreate "${services[@]}" >/dev/null
}
# Wait until every container is running (and healthy, if it has a healthcheck) and stays so.
wait_up() {
  local deadline=$(( SECONDS + WAIT_SECONDS )) c st ok
  while (( SECONDS < deadline )); do
    ok=1
    for c in "$@"; do
      st="$(docker inspect "$c" --format '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' 2>/dev/null || echo missing)"
      case "$st" in running/healthy|running/none) ;; *) ok=0 ;; esac
    done
    if (( ok )); then sleep 15
      for c in "$@"; do st="$(docker inspect "$c" --format '{{.State.Status}}/{{.RestartCount}}')"; [[ "$st" == running/* ]] || ok=0; done
      (( ok )) && return 0
    fi
    sleep 5
  done
  return 1
}
# Fail if a container logged an S3 credential error since a given time.
no_s3_auth_errors() {
  local since="$1"; shift; local c
  for c in "$@"; do
    if docker logs --since "$since" "$c" 2>&1 | grep -qiE 'InvalidAccessKeyId|SignatureDoesNotMatch|AccessDenied|invalid.*credential'; then
      say "$c logged an S3 credential error"; return 1
    fi
  done
}

# ---------------------------------------------------------------- MinIO helpers (secrets via stdin only)
# mc_as USER <<<SECRET -- <mc args...>: runs mc inside the MinIO container under a throwaway alias.
mc_as() {
  local user="$1"; shift
  docker exec -i -e A_USER="$user" "$MINIO_CONTAINER" sh -c '
    IFS= read -r S || exit 90
    mc alias set t http://localhost:9000 "$A_USER" "$S" >/dev/null 2>&1 || { echo "alias failed" >&2; exit 91; }
    shift; mc "$@"; rc=$?; mc alias rm t >/dev/null 2>&1; exit $rc' sh _ "$@"
}
minio_ready() {
  local deadline=$(( SECONDS + WAIT_SECONDS ))
  until docker exec "$MINIO_CONTAINER" sh -c 'mc alias set z http://localhost:9000 >/dev/null 2>&1 && mc ready z >/dev/null 2>&1' 2>/dev/null; do
    (( SECONDS < deadline )) || return 1; sleep 3
  done
}

# ================================================================ 0. Preflight
for f in "$MINIO_ENV" "$MUH_ENV" "$MAN_ENV"; do [[ -f "$f" && -w "$f" ]] || die "missing or not writable: $f"; done
for c in "$MINIO_CONTAINER" $MUH_CONTAINERS $MAN_CONTAINERS; do
  [[ "$(docker inspect "$c" --format '{{.State.Status}}' 2>/dev/null)" == running ]] || die "container not running: $c"
done
docker exec "$MINIO_CONTAINER" sh -c 'command -v mc >/dev/null' || die "mc not found inside $MINIO_CONTAINER"

ROOT_USER="$(docker exec "$MINIO_CONTAINER" printenv MINIO_ROOT_USER)"
OLD="$(docker exec "$MINIO_CONTAINER" printenv MINIO_ROOT_PASSWORD)"
[[ -n "$ROOT_USER" && -n "$OLD" ]] || die "could not read the running root credentials"

[[ "$(env_count "$MINIO_ENV" MINIO_ROOT_PASSWORD)" -eq 1 ]] || die "$MINIO_ENV must have exactly one MINIO_ROOT_PASSWORD line"
[[ "$(env_get "$MINIO_ENV" MINIO_ROOT_PASSWORD)" == "$OLD" ]] || die "$MINIO_ENV MINIO_ROOT_PASSWORD is not the running root password"
[[ "$(label "$MINIO_CONTAINER" com.docker.compose.project.working_dir)" == "$(dirname "$MINIO_ENV")" ]] \
  || die "$MINIO_CONTAINER is not started from $(dirname "$MINIO_ENV")"

[[ "$(env_get "$MUH_ENV" MINIO_ROOT_PASSWORD)" == "$OLD" ]] || die "$MUH_ENV MINIO_ROOT_PASSWORD is not the root password (already rotated?)"
MUH_COMPOSE="$(label muhabbet-backend com.docker.compose.project.config_files)"
grep -qE 'MINIO_SECRET_KEY:[[:space:]]*\$\{MINIO_ROOT_PASSWORD\}' "${MUH_COMPOSE%%,*}" \
  || die "Muhabbet compose no longer maps MINIO_SECRET_KEY to MINIO_ROOT_PASSWORD; adjust this script"
grep -qE 'MINIO_ACCESS_KEY:[[:space:]]*\$\{MINIO_ACCESS_KEY' "${MUH_COMPOSE%%,*}" \
  || die "Muhabbet compose does not read MINIO_ACCESS_KEY from the env file; adjust this script"

[[ "$(env_get "$MAN_ENV" "$MAN_SECRET_VAR")" == "$OLD" ]] || die "$MAN_ENV $MAN_SECRET_VAR is not the root password"
MAN_ACCESS_VAR="$(env_key_with_value "$MAN_ENV" "$ROOT_USER")" \
  || die "could not find exactly one variable in $MAN_ENV holding the root user name"

for b in "$MUH_BUCKET" "$MAN_BUCKET"; do
  mc_as "$ROOT_USER" ls "t/$b" <<<"$OLD" >/dev/null || die "bucket $b not readable with the root credentials"
done
say "ok: root user '$ROOT_USER'; Mandalean access-key variable is $MAN_ACCESS_VAR; buckets present"

# ================================================================ 1. Backups
STAGE="backup"
for f in "$MINIO_ENV" "$MUH_ENV" "$MAN_ENV"; do
  cp -p "$f" "$f.bak-rotate-$TS"; chmod 600 "$f.bak-rotate-$TS"
done
say "backups: <file>.bak-rotate-$TS (contain the OLD password; delete after you have checked the apps)"

# ================================================================ 2. Per-application users
STAGE="users"
MUH_SECRET="$(rand_hex)"; MAN_SECRET="$(rand_hex)"; NEW="$(rand_hex)"
[[ ${#MUH_SECRET} -eq 64 && ${#MAN_SECRET} -eq 64 && ${#NEW} -eq 64 ]] || die "random generation failed"

make_user() {   # name bucket secret
  local name="$1" bucket="$2" secret="$3" pol="$1-rw"
  local policy
  policy=$(printf '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:*"],"Resource":["arn:aws:s3:::%s","arn:aws:s3:::%s/*"]},{"Effect":"Allow","Action":["s3:ListAllMyBuckets","s3:GetBucketLocation"],"Resource":["arn:aws:s3:::*"]}]}' "$bucket" "$bucket")
  docker exec -i "$MINIO_CONTAINER" sh -c 'cat > /tmp/policy.json' <<<"$policy"
  mc_as "$ROOT_USER" admin policy create t "$pol" /tmp/policy.json <<<"$OLD" >/dev/null
  docker exec "$MINIO_CONTAINER" rm -f /tmp/policy.json
  # user add takes the secret as an argument inside the container only (never on the host command line)
  docker exec -i -e A_USER="$ROOT_USER" -e N="$name" "$MINIO_CONTAINER" sh -c '
    IFS= read -r R; IFS= read -r S
    mc alias set t http://localhost:9000 "$A_USER" "$R" >/dev/null 2>&1 || exit 91
    mc admin user add t "$N" "$S" >/dev/null && rc=0 || rc=$?
    mc alias rm t >/dev/null 2>&1; exit $rc' <<<"$OLD"$'\n'"$secret"
  mc_as "$ROOT_USER" admin policy attach t "$pol" --user "$name" <<<"$OLD" >/dev/null 2>&1 \
    || mc_as "$ROOT_USER" admin policy entities t --user "$name" <<<"$OLD" | grep -q "$pol" \
    || die "could not attach policy $pol to $name"
}
make_user "$MUH_USER" "$MUH_BUCKET" "$MUH_SECRET"
make_user "$MAN_USER" "$MAN_BUCKET" "$MAN_SECRET"

mc_as "$MUH_USER" ls "t/$MUH_BUCKET" <<<"$MUH_SECRET" >/dev/null || die "$MUH_USER cannot read $MUH_BUCKET"
mc_as "$MAN_USER" ls "t/$MAN_BUCKET" <<<"$MAN_SECRET" >/dev/null || die "$MAN_USER cannot read $MAN_BUCKET"
if mc_as "$MUH_USER" ls "t/$MAN_BUCKET" <<<"$MUH_SECRET" >/dev/null 2>&1; then die "$MUH_USER can read $MAN_BUCKET (policy too wide)"; fi
if mc_as "$MAN_USER" ls "t/$MUH_BUCKET" <<<"$MAN_SECRET" >/dev/null 2>&1; then die "$MAN_USER can read $MUH_BUCKET (policy too wide)"; fi
say "ok: $MUH_USER -> $MUH_BUCKET only, $MAN_USER -> $MAN_BUCKET only"

# ================================================================ 3. Switch the applications
STAGE="apps"
rollback_apps() {
  say "rolling the applications back to the previous env files"
  cat "$MUH_ENV.bak-rotate-$TS" > "$MUH_ENV"; cat "$MAN_ENV.bak-rotate-$TS" > "$MAN_ENV"
  recreate --default-env "$MUH_ENV" $MUH_CONTAINERS || true; recreate $MAN_CONTAINERS || true
  wait_up $MUH_CONTAINERS $MAN_CONTAINERS || say "WARNING: apps not healthy after rollback — check them now"
}
env_set "$MUH_ENV" MINIO_ACCESS_KEY "$MUH_USER"
env_set "$MUH_ENV" MINIO_ROOT_PASSWORD "$MUH_SECRET"
env_set "$MAN_ENV" "$MAN_ACCESS_VAR" "$MAN_USER"
env_set "$MAN_ENV" "$MAN_SECRET_VAR" "$MAN_SECRET"

SINCE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
if ! { recreate --default-env "$MUH_ENV" $MUH_CONTAINERS && recreate $MAN_CONTAINERS && wait_up $MUH_CONTAINERS $MAN_CONTAINERS \
       && sleep 20 && no_s3_auth_errors "$SINCE" $MUH_CONTAINERS $MAN_CONTAINERS; }; then
  rollback_apps; die "applications did not come up cleanly on their new users (rolled back; root unchanged)"
fi
say "ok: both applications running on their own users"

# ================================================================ 4. Rotate root
STAGE="root"
rollback_root() {
  say "restoring the previous root password"
  cat "$MINIO_ENV.bak-rotate-$TS" > "$MINIO_ENV"; recreate "$MINIO_CONTAINER" || true
  minio_ready || say "WARNING: MinIO not ready after rollback — check it now"
}
env_set "$MINIO_ENV" MINIO_ROOT_PASSWORD "$NEW"
recreate "$MINIO_CONTAINER" || { rollback_root; die "could not recreate $MINIO_CONTAINER"; }
minio_ready || { rollback_root; die "MinIO did not become ready"; }

if mc_as "$ROOT_USER" admin info t <<<"$OLD" >/dev/null 2>&1; then rollback_root; die "old root password still accepted"; fi
mc_as "$ROOT_USER" admin info t <<<"$NEW" >/dev/null 2>&1 || { rollback_root; die "new root password rejected"; }
mc_as "$MUH_USER" ls "t/$MUH_BUCKET" <<<"$MUH_SECRET" >/dev/null || { rollback_root; die "$MUH_USER lost access after the restart"; }
mc_as "$MAN_USER" ls "t/$MAN_BUCKET" <<<"$MAN_SECRET" >/dev/null || { rollback_root; die "$MAN_USER lost access after the restart"; }
SINCE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"; sleep 20
no_s3_auth_errors "$SINCE" $MUH_CONTAINERS $MAN_CONTAINERS || say "WARNING: an app logged an S3 error after the MinIO restart — check it"
unset OLD NEW MUH_SECRET MAN_SECRET

STAGE="done"
say "root password rotated; the leaked value is now rejected."
say "next: check an upload/download in both apps, then delete the backups:"
say "  shred -u $MINIO_ENV.bak-rotate-$TS $MUH_ENV.bak-rotate-$TS $MAN_ENV.bak-rotate-$TS"
say "other files that held the old password are now harmless but should be removed (see the incident record)."
