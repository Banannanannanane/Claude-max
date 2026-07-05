#!/bin/bash
# Télécharge le dernier serveur Minecraft Java officiel dans le dossier donné,
# et pré-accepte l'EULA. Nécessite curl (fourni) et Java (fourni).
set -e
DIR="${1:?dossier requis}"
mkdir -p "$DIR"
echo "[GameHost] Récupération du manifeste Minecraft…"
MANIFEST=$(curl -sSL https://launchermeta.mojang.com/mc/game/version_manifest.json)
LATEST=$(echo "$MANIFEST" | grep -oE '"release":"[^"]+"' | head -1 | cut -d'"' -f4)
VURL=$(echo "$MANIFEST" | tr ',' '\n' | grep -A2 "\"id\":\"$LATEST\"" | grep -oE 'https://[^"]+' | head -1)
SURL=$(curl -sSL "$VURL" | tr ',' '\n' | grep -A3 '"server"' | grep -oE 'https://[^"]+server[^"]*\.jar' | head -1)
echo "[GameHost] Minecraft $LATEST → server.jar"
curl -sSL "$SURL" -o "$DIR/server.jar"
echo "eula=true" > "$DIR/eula.txt"
echo "[GameHost] Minecraft prêt."
