# Muhabbet — Natro XCloud VPS Deployment Guide

**Target:** Natro XCloud Medium (2 vCPU, 4GB RAM, 60GB SSD, 100Mbps unlimited)
**OS:** Ubuntu 24.04 LTS
**DC:** Istanbul, Turkey
**Domain:** `muhabbet.rollingcatsoftware.com`
**Architecture:** All services on a single box via Docker Compose

---

## Table of Contents

1. [Natro VPS Order](#1-natro-vps-order)
2. [Server Hardening](#2-server-hardening)
3. [Docker Setup](#3-docker-setup)
4. [Project Deployment](#4-project-deployment)
5. [DNS Configuration](#5-dns-configuration)
6. [SSL/TLS with Let's Encrypt](#6-ssltls-with-lets-encrypt)
7. [First Deployment](#7-first-deployment)
8. [Monitoring](#8-monitoring)
9. [Backup Strategy](#9-backup-strategy)
10. [Maintenance](#10-maintenance)
11. [Troubleshooting](#11-troubleshooting)
12. [Security Checklist](#12-security-checklist)
13. [Appendix](#13-appendix)

---

## 1. Natro VPS Order

### 1.1 Plan Selection

| Spec | Value |
|------|-------|
| Plan | XCloud Medium |
| vCPU | 2 |
| RAM | 4 GB |
| SSD | 60 GB |
| Bandwidth | 100 Mbps unlimited |
| DC Location | Istanbul |
| OS | Ubuntu 24.04 LTS |
| Price | ~₺436/mo |

**Why this plan:** 4GB is sufficient for the MVP with swap. Memory budget:

| Service | RAM Budget |
|---------|-----------|
| PostgreSQL | ~512 MB (shared_buffers=128MB) |
| Spring Boot JVM | ~712 MB (512 heap + 200 metaspace) |
| Redis | ~128 MB |
| MinIO | ~256 MB |
| Nginx + OS | ~500 MB |
| **Total** | ~2.1 GB active (4GB physical + 4GB swap safety) |

### 1.2 Order Steps

1. Go to [natro.com](https://www.natro.com/sunucu-kiralama/vps-cloud-server) → Bulut Sunucu → XCloud Medium
2. Select **Ubuntu 24.04 LTS** as OS
3. Select **Istanbul** data center
4. Complete payment (TL billing, no FX risk)
5. Receive email with: **IP address**, **root password**, **SSH port** (default 22)

### 1.3 First Login

```bash
ssh root@<NATRO_IP>
passwd  # Change root password immediately
```

---

## 2. Server Hardening

### 2.1 Create Deploy User

```bash
adduser deploy
usermod -aG sudo deploy
```

### 2.2 SSH Key Authentication

On your **local machine** (PowerShell):

```powershell
ssh-keygen -t ed25519 -C "muhabbet-deploy" -f $HOME\.ssh\muhabbet_deploy
type $HOME\.ssh\muhabbet_deploy.pub | ssh root@<NATRO_IP> "mkdir -p /home/deploy/.ssh && cat >> /home/deploy/.ssh/authorized_keys"
```

On the **server**:

```bash
chmod 700 /home/deploy/.ssh
chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
```

### 2.3 Disable Root & Password Login

Edit `/etc/ssh/sshd_config`:

```
PermitRootLogin no
PasswordAuthentication no
PubkeyAuthentication yes
MaxAuthTries 3
AllowUsers deploy
```

```bash
systemctl restart sshd
```

> **CRITICAL:** Test SSH login as `deploy` in a **separate terminal** before closing the root session. If it fails, you still have the root session open to fix it.

### 2.4 UFW Firewall

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'HTTP - redirect to HTTPS'
ufw allow 443/tcp comment 'HTTPS + WSS'
ufw enable
ufw status verbose
```

> **Note:** Port 8080 is intentionally NOT exposed. Nginx reverse-proxies to `backend:8080` internally via Docker network. Exposing 8080 would bypass Nginx and SSL.

### 2.5 fail2ban

```bash
apt install -y fail2ban
```

Create `/etc/fail2ban/jail.local`:

```ini
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 3
backend = systemd

[sshd]
enabled = true
port = 22
filter = sshd
maxretry = 3
bantime = 86400
```

```bash
systemctl enable fail2ban
systemctl start fail2ban
```

### 2.6 Automatic Security Updates

```bash
apt install -y unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades
```

Verify `/etc/apt/apt.conf.d/20auto-upgrades`:

```
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
```

### 2.7 Swap (Essential for 4GB RAM)

```bash
fallocate -l 4G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

Keep swappiness low (prefer RAM, use swap as safety net):

```bash
echo 'vm.swappiness=10' >> /etc/sysctl.conf
sysctl -p
```

Verify:

```bash
free -h
swapon --show
```

### 2.8 Timezone

```bash
timedatectl set-timezone Europe/Istanbul
```

---

## 3. Docker Setup

### 3.1 Install Docker Engine

```bash
# Remove old versions
apt remove -y docker docker-engine docker.io containerd runc 2>/dev/null

# Install prerequisites
apt update
apt install -y ca-certificates curl gnupg

# Add Docker GPG key
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

# Add Docker repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Add deploy user to docker group
usermod -aG docker deploy

# Enable and start
systemctl enable docker
systemctl start docker
```

Verify:

```bash
docker --version
docker compose version
```

### 3.2 Docker Log Rotation (Critical for 60GB SSD)

Create `/etc/docker/daemon.json`:

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2"
}
```

```bash
systemctl restart docker
```

Each container is limited to 30MB of logs (3 files x 10MB). Without this, Spring Boot structured JSON logging can fill 60GB within weeks.

---

## 4. Project Deployment

### 4.1 Directory Structure

```bash
su - deploy
mkdir -p ~/muhabbet
mkdir -p ~/muhabbet/backups/{postgres,minio}
mkdir -p ~/muhabbet/logs
```

### 4.2 Git Clone

**Option A — Deploy Key (recommended for private repos):**

```bash
ssh-keygen -t ed25519 -C "muhabbet-deploy-key" -f ~/.ssh/muhabbet_deploy_key -N ""
cat ~/.ssh/muhabbet_deploy_key.pub
# → Add this as a Deploy Key in GitHub repo settings (read-only)
```

Configure SSH for GitHub:

```bash
cat >> ~/.ssh/config << 'EOF'
Host github.com-muhabbet
    HostName github.com
    User git
    IdentityFile ~/.ssh/muhabbet_deploy_key
    IdentitiesOnly yes
EOF
```

```bash
cd ~
git clone git@github.com-muhabbet:<org>/Muhabbet.git muhabbet
```

**Option B — HTTPS with PAT (simpler):**

```bash
cd ~
git clone https://<GITHUB_PAT>@github.com/<org>/Muhabbet.git muhabbet
```

### 4.3 Environment File

```bash
cd ~/muhabbet/infra
cp ../.env.example .env
chmod 600 .env
```

Generate strong secrets:

```bash
# Run each and paste into .env
openssl rand -base64 32   # → DB_PASSWORD
openssl rand -base64 32   # → REDIS_PASSWORD
openssl rand -base64 64   # → JWT_SECRET (min 256 bits for HS256)
openssl rand -base64 32   # → MINIO_SECRET_KEY
```

Edit `.env` with production values:

```env
# ---- Database ----
DB_USERNAME=muhabbet
DB_PASSWORD=<paste-generated-password>

# ---- Redis ----
REDIS_PASSWORD=<paste-generated-password>

# ---- JWT ----
JWT_SECRET=<paste-generated-64-byte-base64>

# ---- MinIO ----
MINIO_ACCESS_KEY=muhabbet-minio
MINIO_SECRET_KEY=<paste-generated-password>

# ---- Netgsm SMS ----
NETGSM_USERCODE=<your-netgsm-usercode>
NETGSM_PASSWORD=<your-netgsm-password>

# ---- Sentry ----
SENTRY_DSN=<your-sentry-dsn>
```

### 4.4 Firebase Credentials

From your local machine:

```powershell
scp firebase-adminsdk.json deploy@<NATRO_IP>:~/muhabbet/firebase-adminsdk.json
```

On the server:

```bash
chmod 600 ~/muhabbet/firebase-adminsdk.json
```

---

## 5. DNS Configuration

### 5.1 A Record

At your domain registrar or Cloudflare DNS:

| Type | Name | Value | TTL |
|------|------|-------|-----|
| A | `muhabbet` | `<NATRO_IP>` | 300 |

Result: `muhabbet.rollingcatsoftware.com` → `<NATRO_IP>`

### 5.2 Verify Propagation

```bash
dig muhabbet.rollingcatsoftware.com +short
# Should return: <NATRO_IP>
```

Or check at https://dnschecker.org

### 5.3 Cloudflare Notes (If Using)

- **Initial setup:** Set DNS to **DNS-only** (gray cloud, not orange proxy) so Certbot can obtain the certificate directly
- **After cert obtained:** Optionally enable Cloudflare proxy (orange cloud)
- **If proxied:** Set SSL mode to **Full (strict)** since we have a valid Let's Encrypt cert
- WebSocket connections work through Cloudflare on all plans

---

## 6. SSL/TLS with Let's Encrypt

The Nginx config (`infra/nginx/conf.d/default.conf`) references SSL cert files that don't exist on a fresh server. We need a bootstrap process.

### 6.1 Create Temporary Nginx Config

```bash
cd ~/muhabbet/infra

# Backup the real config
cp nginx/conf.d/default.conf nginx/conf.d/default.conf.bak

# Write temporary config for ACME challenge only
cat > nginx/conf.d/default.conf << 'NGINX'
server {
    listen 80;
    server_name muhabbet.rollingcatsoftware.com;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 200 'Muhabbet SSL setup in progress';
        add_header Content-Type text/plain;
    }
}
NGINX
```

### 6.2 Start Only Nginx

```bash
docker compose -f docker-compose.prod.yml up -d nginx
```

### 6.3 Obtain Certificate

```bash
docker compose -f docker-compose.prod.yml run --rm certbot \
    certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email your-email@rollingcatsoftware.com \
    --agree-tos \
    --no-eff-email \
    -d muhabbet.rollingcatsoftware.com
```

### 6.4 Restore Real Nginx Config & Restart

```bash
cp nginx/conf.d/default.conf.bak nginx/conf.d/default.conf
docker compose -f docker-compose.prod.yml restart nginx
```

### 6.5 Auto-Renewal

The Certbot service in `docker-compose.prod.yml` already runs `certbot renew` every 12 hours. Add a cron to reload Nginx after renewal:

```bash
crontab -e
```

Add:

```cron
0 3 * * * docker exec muhabbet-nginx nginx -s reload 2>/dev/null
```

### 6.6 Verify SSL

```bash
curl -I https://muhabbet.rollingcatsoftware.com/actuator/health
```

Or test at https://www.ssllabs.com/ssltest/

---

## 7. First Deployment

### 7.1 Pre-Flight Checklist

```
[ ] DNS A record pointing to Natro IP
[ ] DNS propagated (dig returns correct IP)
[ ] .env file created with all production values
[ ] .env file permissions: 600
[ ] firebase-adminsdk.json in project root with 600 permissions
[ ] SSL certificate obtained (Section 6)
[ ] Docker log rotation configured (Section 3.2)
[ ] Swap configured and active (Section 2.7)
```

### 7.2 Build & Start All Services

```bash
cd ~/muhabbet/infra

# Pull base images first
docker compose -f docker-compose.prod.yml pull postgres redis minio nginx certbot

# Build and start everything
docker compose -f docker-compose.prod.yml up -d --build
```

### 7.3 Watch Startup Logs

```bash
# All services
docker compose -f docker-compose.prod.yml logs -f

# Backend only (most likely to fail)
docker compose -f docker-compose.prod.yml logs -f backend
```

Wait for: `Started MuhabbetBackendApplicationKt in X seconds`

### 7.4 Verify All Services

```bash
# Container status — all should be "Up" with "(healthy)"
docker compose -f docker-compose.prod.yml ps

# Health check
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# External check
curl -I https://muhabbet.rollingcatsoftware.com/actuator/health
```

Expected health response:

```json
{
    "status": "UP",
    "components": {
        "db": { "status": "UP" },
        "redis": { "status": "UP" },
        "diskSpace": { "status": "UP" }
    }
}
```

### 7.5 Initialize MinIO Bucket

The prod compose doesn't include `minio-init`. Initialize manually:

```bash
# Install mc (MinIO Client) on the host
docker exec muhabbet-minio mc alias set local http://localhost:9000 $MINIO_ACCESS_KEY $MINIO_SECRET_KEY
docker exec muhabbet-minio mc mb local/muhabbet-media --ignore-existing
docker exec muhabbet-minio mc anonymous set download local/muhabbet-media/thumbnails
```

> **Note:** MinIO console (port 9001) is intentionally NOT exposed in production. For debugging, use SSH tunneling:
> ```bash
> ssh -L 9001:localhost:9001 deploy@<NATRO_IP>
> # Then access MinIO console at http://localhost:9001
> ```

### 7.6 Test WebSocket

```bash
curl -i -N \
    -H "Connection: Upgrade" \
    -H "Upgrade: websocket" \
    -H "Sec-WebSocket-Version: 13" \
    -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
    https://muhabbet.rollingcatsoftware.com/ws?token=test
```

Expected: HTTP 101 Switching Protocols (or 401 if token invalid — both mean WebSocket routing works).

---

## 8. Monitoring

### 8.1 Spring Actuator (Built-In)

Already configured in `application.yml`:

| Endpoint | Access | Purpose |
|----------|--------|---------|
| `/actuator/health` | Public via Nginx | Uptime checks |
| `/actuator/metrics` | Internal only (Nginx blocks) | App metrics |
| `/actuator/prometheus` | Internal only | Prometheus scraping |

### 8.2 Sentry (Error Tracking)

Already configured via `sentry-spring-boot-starter-jakarta` and `SENTRY_DSN` env var. No additional setup needed.

Verify by triggering a test error:

```bash
curl -X POST https://muhabbet.rollingcatsoftware.com/api/v1/auth/otp/verify \
    -H "Content-Type: application/json" \
    -d '{"phoneNumber": "invalid"}'
```

Check your Sentry dashboard for the error event.

### 8.3 Health Check Script (Auto-Restart)

Create `~/muhabbet/scripts/health-check.sh`:

```bash
#!/bin/bash
set -euo pipefail

HEALTH_URL="http://localhost:8080/actuator/health"
LOG_FILE="/home/deploy/muhabbet/logs/health.log"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$HEALTH_URL" || echo "000")

if [ "$STATUS" != "200" ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ALERT: Health check failed (HTTP $STATUS). Restarting backend..." >> "$LOG_FILE"
    cd /home/deploy/muhabbet/infra
    docker compose -f docker-compose.prod.yml restart backend
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backend restart triggered." >> "$LOG_FILE"
fi
```

```bash
chmod +x ~/muhabbet/scripts/health-check.sh
```

Cron — every 5 minutes:

```cron
*/5 * * * * /home/deploy/muhabbet/scripts/health-check.sh
```

### 8.4 Disk Usage Monitoring

Cron — every 6 hours, alert if >80%:

```cron
0 */6 * * * USAGE=$(df / | tail -1 | awk '{print $5}' | tr -d '%%'); [ "$USAGE" -gt 80 ] && echo "[$(date '+%Y-%m-%d %H:%M:%S')] DISK WARNING: ${USAGE}%% used" >> /home/deploy/muhabbet/logs/disk.log
```

Check manually:

```bash
df -h /
du -sh /var/lib/docker/
docker system df
```

### 8.5 Prometheus + Grafana (Optional for MVP)

For MVP, Sentry + health check cron is sufficient. When you need metrics dashboards, create `infra/monitoring/docker-compose.monitoring.yml`:

```yaml
version: '3.9'
services:
  prometheus:
    image: prom/prometheus:latest
    container_name: muhabbet-prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=15d'
    restart: unless-stopped
    networks:
      - infra_muhabbet-net

  grafana:
    image: grafana/grafana:latest
    container_name: muhabbet-grafana
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-changeme}
    volumes:
      - grafana_data:/var/lib/grafana
    restart: unless-stopped
    networks:
      - infra_muhabbet-net

volumes:
  prometheus_data:
  grafana_data:

networks:
  infra_muhabbet-net:
    external: true
```

With `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'muhabbet-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['backend:8080']
```

Access via SSH tunnel:

```bash
ssh -L 3000:localhost:3000 deploy@<NATRO_IP>
# Open http://localhost:3000 (admin / changeme)
```

---

## 9. Backup Strategy

### 9.1 PostgreSQL — Daily Backup

Create `~/muhabbet/scripts/backup-postgres.sh`:

```bash
#!/bin/bash
set -euo pipefail

BACKUP_DIR="/home/deploy/muhabbet/backups/postgres"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
KEEP_DAYS=7

# Source .env for credentials
source /home/deploy/muhabbet/infra/.env

# Dump from running container
docker exec muhabbet-postgres pg_dump \
    -U "${DB_USERNAME}" \
    -d muhabbet \
    --format=custom \
    --compress=9 \
    > "${BACKUP_DIR}/muhabbet_${TIMESTAMP}.dump"

# Remove backups older than KEEP_DAYS
find "$BACKUP_DIR" -name "*.dump" -mtime +${KEEP_DAYS} -delete

echo "[$(date '+%Y-%m-%d %H:%M:%S')] PostgreSQL backup: muhabbet_${TIMESTAMP}.dump ($(du -h ${BACKUP_DIR}/muhabbet_${TIMESTAMP}.dump | cut -f1))"
```

```bash
chmod +x ~/muhabbet/scripts/backup-postgres.sh
```

Cron — daily at 03:00:

```cron
0 3 * * * /home/deploy/muhabbet/scripts/backup-postgres.sh >> /home/deploy/muhabbet/logs/backup.log 2>&1
```

### 9.2 PostgreSQL — Restore

```bash
cd ~/muhabbet/infra

# Stop backend to prevent writes
docker compose -f docker-compose.prod.yml stop backend

# Source .env
source .env

# Restore from dump
docker exec -i muhabbet-postgres pg_restore \
    -U "${DB_USERNAME}" \
    -d muhabbet \
    --clean \
    --if-exists \
    < ~/muhabbet/backups/postgres/muhabbet_YYYYMMDD_HHMMSS.dump

# Restart backend
docker compose -f docker-compose.prod.yml start backend
```

### 9.3 MinIO — Weekly Backup

Create `~/muhabbet/scripts/backup-minio.sh`:

```bash
#!/bin/bash
set -euo pipefail

BACKUP_DIR="/home/deploy/muhabbet/backups/minio"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
KEEP_WEEKS=4

# Source .env
source /home/deploy/muhabbet/infra/.env

# Mirror from MinIO to host via mc
docker exec muhabbet-minio mc alias set local http://localhost:9000 "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}" 2>/dev/null
docker exec muhabbet-minio mc mirror --quiet local/muhabbet-media /tmp/minio-backup

# Copy from container to host
docker cp muhabbet-minio:/tmp/minio-backup "${BACKUP_DIR}/media_${TIMESTAMP}"

# Cleanup container temp
docker exec muhabbet-minio rm -rf /tmp/minio-backup

# Remove old backups
find "$BACKUP_DIR" -maxdepth 1 -name "media_*" -mtime +$((KEEP_WEEKS * 7)) -exec rm -rf {} +

echo "[$(date '+%Y-%m-%d %H:%M:%S')] MinIO backup: media_${TIMESTAMP}"
```

```bash
chmod +x ~/muhabbet/scripts/backup-minio.sh
```

Cron — weekly at 04:00 Sunday:

```cron
0 4 * * 0 /home/deploy/muhabbet/scripts/backup-minio.sh >> /home/deploy/muhabbet/logs/backup.log 2>&1
```

### 9.4 Offsite Backup

**Option A — rsync to second server:**

```bash
rsync -avz --progress ~/muhabbet/backups/ deploy@backup-server:/backups/muhabbet/
```

**Option B — rclone to S3-compatible storage (Backblaze B2 / Wasabi):**

```bash
# Install rclone
curl https://rclone.org/install.sh | bash
rclone config  # Configure remote

# Sync
rclone sync ~/muhabbet/backups/ remote:muhabbet-backups/ --progress
```

Cron — weekly Sunday at 05:00:

```cron
0 5 * * 0 rclone sync /home/deploy/muhabbet/backups/ remote:muhabbet-backups/ >> /home/deploy/muhabbet/logs/offsite-backup.log 2>&1
```

### 9.5 Redis

Redis data (OTP sessions, presence, typing indicators) is **ephemeral** — no backup needed. If the container restarts, Redis RDB persistence survives via the Docker volume. If data is truly lost, users simply re-authenticate.

---

## 10. Maintenance

### 10.1 Code Update (Deploy New Version)

```bash
cd ~/muhabbet

# Pull latest code
git pull origin main

# Rebuild and restart backend only
cd infra
docker compose -f docker-compose.prod.yml up -d --build backend

# Watch for startup errors
docker compose -f docker-compose.prod.yml logs -f backend
```

### 10.2 Full Stack Update

```bash
cd ~/muhabbet/infra

# Pull latest images (PG, Redis, MinIO, Nginx, Certbot)
docker compose -f docker-compose.prod.yml pull

# Rebuild and restart everything
docker compose -f docker-compose.prod.yml up -d --build

# Verify
docker compose -f docker-compose.prod.yml ps
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

### 10.3 Rollback

```bash
cd ~/muhabbet

# View recent commits
git log --oneline -10

# Rollback to specific commit
git checkout <commit-hash>

# Rebuild
cd infra
docker compose -f docker-compose.prod.yml up -d --build backend
```

For database rollback: restore from backup (Section 9.2). Flyway migrations are forward-only — to undo, create a new migration that reverses the changes.

### 10.4 Log Rotation (App Logs)

Docker log rotation is handled by `daemon.json` (Section 3.2). For script logs:

Create `/etc/logrotate.d/muhabbet`:

```
/home/deploy/muhabbet/logs/*.log {
    daily
    missingok
    rotate 14
    compress
    delaycompress
    notifempty
    create 0640 deploy deploy
}
```

### 10.5 Docker Cleanup (Prevent Disk Fill)

Cron — weekly Sunday at 02:00:

```cron
0 2 * * 0 docker system prune -af --filter "until=168h" >> /home/deploy/muhabbet/logs/docker-cleanup.log 2>&1
```

Manual cleanup:

```bash
docker system df            # See disk usage
docker system prune -af     # Remove unused images/containers/networks
```

### 10.6 Scaling Guide

Monitor these thresholds — when exceeded, take action:

| Metric | Warning Threshold | Action |
|--------|-------------------|--------|
| RAM usage | >85% sustained | Upgrade to XCloud Large (6GB, ₺567/mo) |
| CPU usage | >80% sustained | Upgrade vCPU count |
| Disk usage | >70% (42GB of 60GB) | Offload media to external S3 or add disk |
| WebSocket connections | >5,000 concurrent | Horizontal scaling (second server) |
| DB response time | >100ms average | Separate PostgreSQL to dedicated server |
| DB size | >20GB | Move to managed PostgreSQL |

**Natro vertical scaling path:**

1. **XCloud Medium** (current): 2 vCPU, 4GB RAM, 60GB SSD — ₺436/mo
2. **XCloud Large**: 2 vCPU, 6GB RAM, 100GB SSD — ₺567/mo
3. **XCloud Pro**: 4 vCPU, 8GB RAM, 200GB SSD — ₺1,309/mo
4. **XCloud Ultra**: 8 vCPU, 32GB RAM, 600GB SSD — ₺2,618/mo

---

## 11. Troubleshooting

### 11.1 Backend Won't Start

```bash
# Check logs
docker compose -f docker-compose.prod.yml logs --tail 100 backend
```

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `Connection refused` to PostgreSQL | PG not ready / wrong creds | Check `docker compose logs postgres`, verify `.env` |
| Flyway migration error | Schema conflict | Check `docker compose logs backend \| grep flyway` |
| `java.lang.OutOfMemoryError` | 4GB RAM exhausted | Add swap, reduce JVM heap (see Appendix B) |
| Exit code 137 | OOM killed by kernel | Same as above — check `dmesg \| grep -i oom` |
| Exit code 1 | Application error | Read full log output |

### 11.2 SSL Certificate Issues

```bash
# Check cert status
docker compose -f docker-compose.prod.yml exec certbot certbot certificates

# Force renewal
docker compose -f docker-compose.prod.yml run --rm certbot certbot renew --force-renewal

# Reload Nginx after renewal
docker exec muhabbet-nginx nginx -s reload
```

### 11.3 WebSocket Fails

```bash
# Check Nginx logs for WebSocket upgrade
docker compose -f docker-compose.prod.yml logs nginx | grep -i upgrade

# Test upgrade handshake
curl -i -N \
    -H "Connection: Upgrade" \
    -H "Upgrade: websocket" \
    -H "Sec-WebSocket-Version: 13" \
    -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
    https://muhabbet.rollingcatsoftware.com/ws?token=test
# Expect: HTTP 101 or HTTP 401 (both mean routing works)
```

### 11.4 Disk Space Full

```bash
# Find what's using space
du -sh /var/lib/docker/*
docker system df -v

# Emergency cleanup
docker system prune -af

# Check for oversized log files
find /var/lib/docker/containers/ -name "*.log" -size +50M -ls
```

### 11.5 Redis Connection Issues

```bash
# Source .env for password
source ~/muhabbet/infra/.env

# Ping
docker exec muhabbet-redis redis-cli -a "${REDIS_PASSWORD}" ping
# Expected: PONG

# Memory check
docker exec muhabbet-redis redis-cli -a "${REDIS_PASSWORD}" INFO memory
```

### 11.6 PostgreSQL Connection Pool Exhausted

```bash
source ~/muhabbet/infra/.env

# Check active connections
docker exec muhabbet-postgres psql -U "${DB_USERNAME}" -d muhabbet \
    -c "SELECT count(*) FROM pg_stat_activity"

# Kill idle connections older than 10 minutes
docker exec muhabbet-postgres psql -U "${DB_USERNAME}" -d muhabbet \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state = 'idle' AND query_start < now() - interval '10 minutes'"
```

HikariCP is configured with `maximum-pool-size: 20` and `minimum-idle: 5` in `application.yml`, which is appropriate for single-box MVP.

### 11.7 Container Restart Loop

```bash
# Check exit code
docker inspect muhabbet-backend --format='{{.State.ExitCode}}'
# 137 = OOM killed, 1 = app error

# Check resource usage
docker stats --no-stream

# Check kernel OOM logs
dmesg | grep -i oom
```

---

## 12. Security Checklist

### 12.1 Pre-Launch

**Server:**

- [ ] SSH key-only authentication (password login disabled)
- [ ] Non-root `deploy` user for all operations
- [ ] UFW firewall enabled (only ports 22, 80, 443)
- [ ] fail2ban active and monitoring SSH
- [ ] Automatic security updates enabled
- [ ] Port 8080 NOT exposed externally

**Application:**

- [ ] `JWT_SECRET` randomly generated (min 256 bits) — `openssl rand -base64 64`
- [ ] `DB_PASSWORD` is strong (32+ characters)
- [ ] `REDIS_PASSWORD` is set and strong
- [ ] `MINIO_SECRET_KEY` is strong
- [ ] OTP mock mode **disabled** (`muhabbet.otp.mock-enabled: false` in prod profile)
- [ ] FCM credentials file has `600` permissions
- [ ] `.env` file has `600` permissions
- [ ] `.env` is in `.gitignore` (never committed)
- [ ] `firebase-adminsdk.json` is in `.gitignore`

**SSL/TLS:**

- [ ] TLS 1.2+ only (configured in Nginx)
- [ ] HSTS header set (`max-age=31536000; includeSubDomains`)
- [ ] `X-Frame-Options: DENY`
- [ ] `X-Content-Type-Options: nosniff`
- [ ] SSL Labs score: A or A+

**Docker:**

- [ ] Backend runs as non-root user (`muhabbet` in Dockerfile)
- [ ] Docker log rotation configured (`daemon.json`)
- [ ] No unnecessary ports exposed
- [ ] Docker socket not mounted in containers

**Network:**

- [ ] Only Nginx (80, 443) exposed to internet
- [ ] All inter-service traffic via Docker bridge network
- [ ] PostgreSQL, Redis, MinIO NOT accessible from outside
- [ ] Actuator endpoints blocked except `/actuator/health`

**Data:**

- [ ] PostgreSQL daily backup cron active
- [ ] Backup restore tested at least once
- [ ] Offsite backup configured
- [ ] Soft delete implemented (`deleted_at` column — KVKK compliance)

**Monitoring:**

- [ ] Sentry DSN configured and receiving events
- [ ] Health check cron active (every 5 min)
- [ ] Disk usage monitoring active

### 12.2 Periodic Security Tasks

| Task | Frequency |
|------|-----------|
| Review server access logs (`/var/log/auth.log`) | Weekly |
| Check fail2ban bans (`fail2ban-client status sshd`) | Weekly |
| Verify backup integrity (test restore) | Monthly |
| Update Docker images (security patches) | Monthly |
| Review and rotate `JWT_SECRET` | Every 6 months |
| Ubuntu security updates | Automatic |
| SSL certificate renewal | Automatic (Certbot) |
| Docker system prune | Weekly (automated) |
| Check disk usage | Every 6 hours (automated) |

---

## 13. Appendix

### Appendix A: Quick Reference Commands

```bash
# ── Status ──
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
docker stats --no-stream
free -h
df -h /

# ── Deploy ──
cd ~/muhabbet && git pull origin main
cd infra && docker compose -f docker-compose.prod.yml up -d --build backend

# ── Restart ──
docker compose -f docker-compose.prod.yml restart backend

# ── Rebuild All ──
docker compose -f docker-compose.prod.yml up -d --build

# ── Stop All ──
docker compose -f docker-compose.prod.yml down

# ── Database CLI ──
source .env && docker exec -it muhabbet-postgres psql -U "${DB_USERNAME}" -d muhabbet

# ── Redis CLI ──
source .env && docker exec -it muhabbet-redis redis-cli -a "${REDIS_PASSWORD}"

# ── Nginx Reload ──
docker exec muhabbet-nginx nginx -s reload

# ── Disk Check ──
df -h / && docker system df

# ── View Logs ──
docker compose -f docker-compose.prod.yml logs --tail 100 backend
docker compose -f docker-compose.prod.yml logs --tail 100 nginx
docker compose -f docker-compose.prod.yml logs --tail 100 postgres
```

### Appendix B: Recommended docker-compose.prod.yml Tuning

These changes are recommended for the 4GB server but NOT applied automatically. Apply them when ready.

**Backend — JVM Memory Limits:**

Add to `backend` service:

```yaml
environment:
  JAVA_OPTS: "-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
deploy:
  resources:
    limits:
      memory: 1024M
    reservations:
      memory: 512M
```

And update `backend/Dockerfile` ENTRYPOINT to accept JAVA_OPTS:

```dockerfile
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Redis — Memory Limit:**

Change the `command` in redis service:

```yaml
command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 128mb --maxmemory-policy allkeys-lru
```

**PostgreSQL — Tuning for 4GB Server:**

Add `command` to postgres service:

```yaml
command: >
  postgres
    -c shared_buffers=128MB
    -c effective_cache_size=256MB
    -c work_mem=4MB
    -c maintenance_work_mem=64MB
    -c max_connections=50
```

### Appendix C: All Cron Entries

Add all at once with `crontab -e`:

```cron
# Health check — every 5 minutes
*/5 * * * * /home/deploy/muhabbet/scripts/health-check.sh

# PostgreSQL backup — daily at 03:00
0 3 * * * /home/deploy/muhabbet/scripts/backup-postgres.sh >> /home/deploy/muhabbet/logs/backup.log 2>&1

# MinIO backup — weekly Sunday at 04:00
0 4 * * 0 /home/deploy/muhabbet/scripts/backup-minio.sh >> /home/deploy/muhabbet/logs/backup.log 2>&1

# Offsite backup — weekly Sunday at 05:00
0 5 * * 0 rclone sync /home/deploy/muhabbet/backups/ remote:muhabbet-backups/ >> /home/deploy/muhabbet/logs/offsite-backup.log 2>&1

# Nginx reload (for cert renewal) — daily at 03:00
0 3 * * * docker exec muhabbet-nginx nginx -s reload 2>/dev/null

# Docker cleanup — weekly Sunday at 02:00
0 2 * * 0 docker system prune -af --filter "until=168h" >> /home/deploy/muhabbet/logs/docker-cleanup.log 2>&1

# Disk usage alert — every 6 hours
0 */6 * * * USAGE=$(df / | tail -1 | awk '{print $5}' | tr -d '%%'); [ "$USAGE" -gt 80 ] && echo "[$(date)] DISK: ${USAGE}%%" >> /home/deploy/muhabbet/logs/disk.log
```

### Appendix D: Server Directory Layout (Final)

```
/home/deploy/muhabbet/
├── infra/
│   ├── docker-compose.prod.yml
│   ├── .env                          ← secrets (chmod 600, .gitignored)
│   ├── nginx/conf.d/default.conf
│   └── scripts/
│       └── .gitkeep
├── backend/
│   ├── Dockerfile
│   └── src/...
├── shared/
│   └── src/...
├── firebase-adminsdk.json            ← Firebase creds (chmod 600, .gitignored)
├── scripts/
│   ├── backup-postgres.sh
│   ├── backup-minio.sh
│   └── health-check.sh
├── backups/
│   ├── postgres/                     ← Daily dumps (7-day retention)
│   └── minio/                        ← Weekly mirrors (4-week retention)
├── logs/
│   ├── health.log
│   ├── backup.log
│   ├── disk.log
│   └── docker-cleanup.log
├── .env.example
├── CLAUDE.md
└── ...
```
