#!/bin/bash
# Enrobage SteamCMD : l'installe à la première utilisation puis relaie les
# arguments. Tourne en tant que gameuser. Les fichiers vont dans ~/.steam.
set -e
STEAMROOT=/srv/gamehost/.steamcmd
BIN="$STEAMROOT/steamcmd.sh"
mkdir -p "$STEAMROOT"
if [ ! -x "$BIN" ]; then
  echo "[GameHost] Installation de SteamCMD…"
  curl -sSL https://steamcdn-a.akamaihd.net/client/installer/steamcmd_linux.tar.gz \
    | tar -xz -C "$STEAMROOT"
fi
exec "$BIN" "$@"
