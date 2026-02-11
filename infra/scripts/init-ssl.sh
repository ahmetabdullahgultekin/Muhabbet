#!/usr/bin/env bash
# init-ssl.sh — One-time SSL certificate acquisition via Let's Encrypt
set -euo pipefail

DOMAIN="muhabbet.rollingcatsoftware.com"
EMAIL="rollingcat.help@gmail.com"
APP_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="$APP_DIR/infra/docker-compose.prod.yml"
NGINX_CONF_DIR="$APP_DIR/infra/nginx/conf.d"

echo "=== Step 1: Swap to HTTP-only nginx config ==="
cp "$NGINX_CONF_DIR/default.conf" "$NGINX_CONF_DIR/default.conf.ssl-backup"
cp "$NGINX_CONF_DIR/default.conf.init" "$NGINX_CONF_DIR/default.conf"

echo "=== Step 2: Start nginx (HTTP only) ==="
docker compose -f "$COMPOSE_FILE" up -d nginx

echo "Waiting for nginx to start..."
sleep 5

echo "=== Step 3: Request SSL certificate ==="
docker compose -f "$COMPOSE_FILE" run --rm certbot \
    certonly --webroot --webroot-path=/var/www/certbot \
    -d "$DOMAIN" \
    --email "$EMAIL" \
    --agree-tos \
    --no-eff-email

echo "=== Step 4: Restore full SSL nginx config ==="
cp "$NGINX_CONF_DIR/default.conf.ssl-backup" "$NGINX_CONF_DIR/default.conf"
rm "$NGINX_CONF_DIR/default.conf.ssl-backup"

echo "=== Step 5: Start all services ==="
docker compose -f "$COMPOSE_FILE" up -d --build

echo ""
echo "=== SSL setup complete ==="
echo "Verify: curl -s https://$DOMAIN/actuator/health"
