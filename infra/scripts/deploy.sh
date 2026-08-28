#!/usr/bin/env bash
# deploy.sh — manual redeploy of the Muhabbet backend on the production host.
#
# This is the script form of the manual deploy documented in CLAUDE.md. CI does NOT call it:
# .github/workflows/deploy-hetzner.yml runs `/opt/projects/infra/deploy.sh`, a different,
# multi-project script that lives on the host and takes `build|restart <project>`.
#
# ---------------------------------------------------------------------------------------------
# WHY THIS FILE WAS REWRITTEN (#470)
#
# It used to run:
#
#     COMPOSE_FILE="$APP_DIR/infra/docker-compose.prod.yml"
#     docker compose -f "$COMPOSE_FILE" up -d --build
#
# Every load-bearing detail of that was wrong, and it is the same wrong that #470 found in the
# deploy workflow's rollback path:
#
#   * Wrong stack. infra/docker-compose.prod.yml is the LEGACY nginx stack — its own postgres,
#     redis and minio, no Traefik labels, container names muhabbet-postgres/-redis/-minio.
#     Nothing deploys it. Running this would not have redeployed production; it would have stood
#     up a SECOND, parallel set of containers next to the working one, on a host already at ~92%
#     disk. The stack that actually runs is the repo-root docker-compose.prod.yml.
#   * No --env-file. There is no .env in that directory, only .env.prod, and Compose reads .env
#     by default. Without the flag JWT_SECRET, POSTGRES_PASSWORD, REDIS_PASSWORD and
#     MINIO_ROOT_PASSWORD all resolve to empty and the JWT boot guard crash-loops the container
#     with a "secret too short" error that blames the secret rather than the missing flag.
#   * Unreachable health check. It polled http://localhost:8080/actuator/health, but the root
#     stack publishes no host port — it is routed by Traefik on the `proxy` network. That check
#     could never have passed even against the right stack.
#
# Everything below targets the stack that actually runs, with the invocation CLAUDE.md documents.
# ---------------------------------------------------------------------------------------------
set -euo pipefail

APP_DIR="${MUHABBET_APP_DIR:-/opt/projects/Muhabbet}"
COMPOSE_FILE="docker-compose.prod.yml"   # repo-root, NOT infra/
ENV_FILE=".env.prod"
SERVICE="muhabbet-backend"               # the root stack's service name; there is no "backend"
HEALTH_URL="https://muhabbet-api.rollingcatsoftware.com/actuator/health"

# 24 x 10s. The old 120s window was already marginal and the workflow's own 75s window failed
# every single deploy while the service came up healthy moments later (#470). This has to cover a
# Spring Boot 4 boot plus Flyway plus Traefik noticing the new container.
HEALTH_ATTEMPTS=24
HEALTH_INTERVAL=10

cd "$APP_DIR"

if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: $APP_DIR/$ENV_FILE is missing. Without it every secret resolves to empty and the"
    echo "       backend crash-loops on a misleading 'JWT secret too short'. Refusing to deploy."
    exit 1
fi

compose() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

echo "=== Pulling latest code ==="
git pull

# Capture the running image's immutable ID and the repo:tag compose refers to it by, so a rollback
# is possible after `build` reassigns :latest. Same mechanism the deploy workflow uses.
PREV_IMAGE_ID="$(docker inspect --format '{{.Image}}' "$SERVICE" 2>/dev/null || true)"
PREV_IMAGE_REF="$(docker inspect --format '{{.Config.Image}}' "$SERVICE" 2>/dev/null || true)"
echo "Previous backend image: ref=${PREV_IMAGE_REF:-<none>} id=${PREV_IMAGE_ID:-<none>}"

echo "=== Building and restarting $SERVICE ==="
compose build "$SERVICE"
compose up -d "$SERVICE"

echo "=== Waiting for the backend to answer on $HEALTH_URL ==="
HEALTHY=0
for i in $(seq 1 "$HEALTH_ATTEMPTS"); do
    sleep "$HEALTH_INTERVAL"
    if curl -sf "$HEALTH_URL" > /dev/null 2>&1; then
        echo "API healthy after $((i * HEALTH_INTERVAL))s"
        HEALTHY=1
        break
    fi
    STATE="$(docker inspect --format '{{.State.Health.Status}}' "$SERVICE" 2>/dev/null || echo unknown)"
    echo "  Attempt $i/$HEALTH_ATTEMPTS: not ready yet (container health: $STATE)"
done

if [ "$HEALTHY" = "1" ]; then
    echo ""
    echo "=== Deployment successful ==="
    exit 0
fi

# Diagnostics before anything else. Diagnosing the 2026-08-16 failure meant SSHing in by hand
# because the script printed nothing but "FAILED" (#470).
echo "ERROR: health check failed after $((HEALTH_ATTEMPTS * HEALTH_INTERVAL))s."
echo "--- container state ---"
docker ps --filter "name=$SERVICE" --format '{{.Names}} {{.Status}}' || true
echo "--- last 50 log lines ---"
docker logs --tail 50 "$SERVICE" 2>&1 || true

if [ -z "$PREV_IMAGE_ID" ] || [ -z "$PREV_IMAGE_REF" ]; then
    echo "No previous image was captured — cannot auto-roll back. Manual intervention required."
    exit 1
fi

echo "=== Rolling back to the previous image ==="
# `|| true` on the tag: under `set -e` a failure here would abort the script before the operator
# is told a rollback was even attempted, which is the difference between a bad deploy and a bad
# deploy nobody can diagnose.
docker tag "$PREV_IMAGE_ID" "$PREV_IMAGE_REF" || true
compose up -d --no-build "$SERVICE" || true

for i in 1 2 3 4 5 6; do
    sleep "$HEALTH_INTERVAL"
    if curl -sf "$HEALTH_URL" > /dev/null 2>&1; then
        echo "Rollback is healthy. Production is on the PREVIOUS image — the new build did not ship."
        exit 1
    fi
done

echo "ROLLBACK ALSO FAILED. Production is down. Manual intervention required now."
exit 1
