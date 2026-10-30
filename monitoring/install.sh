#!/bin/bash
# Project configuration around the vendors' official Docker images.
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then echo "Run with sudo bash monitoring/install.sh"; exit 1; fi
SOURCE=$(cd -- "$(dirname -- "$0")" && pwd)
DEST=/opt/devopsflow-monitoring
command -v aws >/dev/null
command -v python3 >/dev/null
command -v openssl >/dev/null
docker compose version
free -h
df -h /
install -d -m 0755 "$DEST"
cp -a "$SOURCE/." "$DEST/"
install -d -o jenkins -g jenkins -m 0755 /var/lib/devopsflow-monitoring/targets
if [ ! -e /var/lib/devopsflow-monitoring/targets/fargate.json ]; then
    echo '[]' > /var/lib/devopsflow-monitoring/targets/fargate.json
    chown jenkins:jenkins /var/lib/devopsflow-monitoring/targets/fargate.json
fi
if [ ! -e "$DEST/.env" ]; then
    (umask 077; printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 24)" > "$DEST/.env")
fi
cat > /etc/systemd/system/devopsflow-discovery.service <<'EOF'
[Unit]
Description=Discover DevOps Flow Fargate metrics targets
Wants=network-online.target
After=network-online.target
[Service]
Type=oneshot
User=jenkins
Group=jenkins
WorkingDirectory=/var/lib/jenkins
Environment=HOME=/var/lib/jenkins
Environment=PATH=/usr/local/bin:/usr/bin:/bin
ExecStart=/usr/bin/python3 /opt/devopsflow-monitoring/discover.py
TimeoutStartSec=120
EOF
cat > /etc/systemd/system/devopsflow-discovery.timer <<'EOF'
[Unit]
Description=Refresh DevOps Flow Fargate targets every 30 seconds
[Timer]
OnBootSec=30
OnUnitActiveSec=30
Unit=devopsflow-discovery.service
[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl start devopsflow-discovery.service
systemctl enable --now devopsflow-discovery.timer
cd "$DEST"
docker compose pull
docker compose run --rm --no-deps --entrypoint /bin/promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose up -d
docker compose ps
echo "Grafana user: admin. Retrieve its generated password locally from /opt/devopsflow-monitoring/.env; do not share it."
echo "Live scrape and dashboard checks are still required."
