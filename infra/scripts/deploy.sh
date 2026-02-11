#!/usr/bin/env bash
# deploy.sh — Pull latest code and redeploy containers
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="$APP_DIR/infra/docker-compose.prod.yml"
DOMAIN="muhabbet.rollingcatsoftware.com"

echo "=== Pulling latest code ==="
cd "$APP_DIR"
git pull

echo "=== Rebuilding and restarting containers ==="
docker compose -f "$COMPOSE_FILE" up -d --build

echo "=== Waiting for backend to be healthy ==="
MAX_WAIT=120
ELAPSED=0
until curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do
    if [ $ELAPSED -ge $MAX_WAIT ]; then
        echo "ERROR: Backend failed to become healthy within ${MAX_WAIT}s"
        echo "Check logs: docker compose -f $COMPOSE_FILE logs backend"
        exit 1
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
    echo "  Waiting... (${ELAPSED}s)"
done

echo ""
echo "=== Deployment successful ==="
echo "Backend health: $(curl -s http://localhost:8080/actuator/health)"
echo "Public URL: https://$DOMAIN"
