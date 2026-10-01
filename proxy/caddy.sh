#!/bin/bash
set -euo pipefail

# Cloudsmith still signs the stable apt repo with expired key
# 531A6B20FA058A70, and that makes every later apt update fail.
# Install the release .deb directly and drop any leftover source.
rm -f /etc/apt/sources.list.d/caddy-stable.list
rm -f /usr/share/keyrings/caddy-stable-archive-keyring.gpg

apt install -y curl ca-certificates
deb_url="$(
  curl -fsSL -H 'Accept: application/vnd.github+json' \
    https://api.github.com/repos/caddyserver/caddy/releases/latest \
    | python3 -c 'import json,sys; rel=json.load(sys.stdin); print(next(a["browser_download_url"] for a in rel["assets"] if a["name"].endswith("_linux_amd64.deb")))'
)"
curl -fsSL "$deb_url" -o /tmp/caddy.deb
apt install -y /tmp/caddy.deb
rm -f /tmp/caddy.deb
