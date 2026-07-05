#!/bin/bash
# Provisionnement au premier démarrage : utilisateur de jeu, dépendances
# SteamCMD, pare-feu. Idempotent (marqueur .provisioned).
set -e
LOG=/var/log/gamehost-firstboot.log
exec > >(tee -a "$LOG") 2>&1
echo "=== GameHost firstboot $(date) ==="

mkdir -p /srv/gamehost /var/lib/gamehost /etc/gamehost

# utilisateur non-privilégié qui possède les fichiers de jeu
if ! id gameuser >/dev/null 2>&1; then
  useradd --system --home /srv/gamehost --shell /usr/sbin/nologin gameuser || true
fi
chown -R gameuser:gameuser /srv/gamehost || true

# accepter la licence Steam (SteamCMD) de façon non-interactive
echo steam steam/question select "I AGREE" | debconf-set-selections || true
echo steam steam/license note '' | debconf-set-selections || true

# pare-feu : SSH + panneau + plages de ports de jeu usuelles
if command -v ufw >/dev/null; then
  ufw --force reset || true
  ufw default deny incoming || true
  ufw default allow outgoing || true
  ufw allow 22/tcp || true
  ufw allow 8080/tcp comment 'GameHost panel' || true
  ufw allow 27015:27020/tcp || true
  ufw allow 27015:27020/udp || true   # CS2
  ufw allow 28015:28020/tcp || true
  ufw allow 28015:28020/udp || true   # Rust
  ufw allow 2456:2458/udp || true     # Valheim
  ufw allow 25565:25575/tcp || true   # Minecraft
  ufw --force enable || true
fi

# Claude Code : installé au premier démarrage (npm, internet de la cible).
# Permet de lancer « claude » sur la machine pour faire évoluer l'hôte.
if command -v npm >/dev/null && ! command -v claude >/dev/null; then
  echo "[GameHost] Installation de Claude Code…"
  timeout 300 npm install -g @anthropic-ai/claude-code >/dev/null 2>&1 \
    && echo "[GameHost] Claude Code installé (commande : claude)." \
    || echo "[GameHost] Claude Code non installé (réseau ?) — relance : npm i -g @anthropic-ai/claude-code"
fi

touch /var/lib/gamehost/.provisioned
echo "=== firstboot terminé ==="
