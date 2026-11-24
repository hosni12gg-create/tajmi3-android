#!/usr/bin/env bash
# tajmi3 server installer (Debian/Ubuntu). Run from this folder:  ./install.sh
set -euo pipefail
cd "$(dirname "$0")"
DIR="$(pwd)"
RUN_USER="${SUDO_USER:-$USER}"

echo ">> installing python venv"
sudo apt-get update -y
sudo apt-get install -y python3 python3-venv python3-pip
python3 -m venv .venv
.venv/bin/pip install -q --upgrade pip
.venv/bin/pip install -q -r requirements.txt

if [ ! -f .env ]; then
  cp .env.example .env
  echo ">> created .env  (edit ALLOWED_EMAILS if you want more emails:  nano $DIR/.env)"
fi
mkdir -p data backups

echo ">> installing systemd service"
sudo tee /etc/systemd/system/tajmi3.service >/dev/null <<UNIT
[Unit]
Description=tajmi3 sync server
After=network-online.target
Wants=network-online.target

[Service]
User=$RUN_USER
WorkingDirectory=$DIR
EnvironmentFile=$DIR/.env
ExecStart=$DIR/.venv/bin/uvicorn app:app --host 127.0.0.1 --port 8787
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now tajmi3
sleep 2
curl -fsS http://127.0.0.1:8787/health && echo && echo ">> server is running on 127.0.0.1:8787"

echo ">> daily backup (03:30) to $DIR/backups"
( crontab -l 2>/dev/null | grep -v tajmi3-backup ; echo "30 3 * * * $DIR/.venv/bin/python $DIR/backup.py # tajmi3-backup" ) | crontab -

cat <<MSG

Done. Next: make it reachable from outside with Tailscale Funnel:
  curl -fsSL https://tailscale.com/install.sh | sh
  sudo tailscale up
  sudo tailscale funnel --bg 8787
It prints your public https address (https://NAME.TAILNET.ts.net). Send it to Claude.
MSG
