#!/usr/bin/env bash
# setup-vm.sh — One-time VM setup: install Docker, clone repo, configure .env
set -euo pipefail

REPO_URL="https://github.com/ahmetabdullahgultekin/Muhabbet.git"
APP_DIR="$HOME/Muhabbet"

echo "=== Installing Docker ==="
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin git
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker "$USER"

echo "=== Cloning repository ==="
if [ -d "$APP_DIR" ]; then
    echo "Directory $APP_DIR already exists, pulling latest..."
    cd "$APP_DIR" && git pull
else
    git clone "$REPO_URL" "$APP_DIR"
fi

cd "$APP_DIR"

echo "=== Generating .env ==="
if [ -f .env ]; then
    echo ".env already exists — skipping generation. Edit manually if needed."
else
    cp .env.example .env

    # Generate secrets
    DB_PASS=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
    REDIS_PASS=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
    JWT_SEC=$(openssl rand -base64 64)
    MINIO_KEY="muhabbet-minio"
    MINIO_SEC=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)

    sed -i "s|CHANGE_ME_strong_password_here|${DB_PASS}|" .env          # first match = DB
    sed -i "0,/CHANGE_ME_strong_password_here/{s|CHANGE_ME_strong_password_here|${REDIS_PASS}|}" .env
    sed -i "s|CHANGE_ME_generate_with_openssl_rand_base64_64|${JWT_SEC}|" .env
    sed -i "s|MINIO_ACCESS_KEY=CHANGE_ME|MINIO_ACCESS_KEY=${MINIO_KEY}|" .env
    sed -i "0,/CHANGE_ME_strong_password_here/{s|CHANGE_ME_strong_password_here|${MINIO_SEC}|}" .env

    echo ""
    echo "=== .env generated with random secrets ==="
    echo "Review it: cat $APP_DIR/.env"
fi

echo ""
echo "=== Setup complete ==="
echo "IMPORTANT: Log out and back in for Docker group to take effect, then run:"
echo "  cd $APP_DIR && bash infra/scripts/init-ssl.sh"
