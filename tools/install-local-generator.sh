#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
unit_dir="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
mkdir -p "$unit_dir"

cat > "$unit_dir/psychiatryk-item-help.service" <<EOF
[Unit]
Description=Psychiatryk consultant item generator
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/python3 -m http.server 8765 --bind 127.0.0.1 --directory $repo_dir/docs
Restart=on-failure
RestartSec=2

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now psychiatryk-item-help.service
printf 'Generator running at http://127.0.0.1:8765/\n'

