#!/bin/bash
# Écran d'état plein écran sur le moniteur branché (tty1) : montre l'IP et
# l'URL du panneau à ouvrir depuis un autre appareil. Rafraîchi en boucle.
GREEN=$'\e[92m'; CYAN=$'\e[96m'; AMBER=$'\e[93m'; DIM=$'\e[2m'; BOLD=$'\e[1m'; RST=$'\e[0m'
while true; do
  IP=$(hostname -I 2>/dev/null | awk '{print $1}')
  [ -z "$IP" ] && IP="(pas de réseau)"
  RAM=$(free -m | awk '/Mem:/{printf "%d / %d Mo", $3, $2}')
  UP=$(uptime -p 2>/dev/null | sed 's/^up //')
  # serveurs actifs (via l'API locale du panneau)
  RUN=$(curl -s --max-time 2 http://127.0.0.1:8080/api/state 2>/dev/null | grep -o '"status":"running"' | wc -l)
  clear
  printf '%s' "$CYAN$BOLD"
  cat <<'ART'
   ____                       _   _           _
  / ___| __ _ _ __ ___   ___ | | | | ___  ___| |_
 | |  _ / _` | '_ ` _ \ / _ \| |_| |/ _ \/ __| __|
 | |_| | (_| | | | | | |  __/|  _  | (_) \__ \ |_
  \____|\__,_|_| |_| |_|\___||_| |_|\___/|___/\__|
ART
  printf '%s\n' "$RST"
  printf '  %sHôte de serveurs de jeux — prêt.%s\n\n' "$DIM" "$RST"
  printf '  Ouvre le panneau depuis un autre appareil du réseau :\n\n'
  printf '      %s%s➜  http://%s:8080%s\n\n' "$GREEN" "$BOLD" "$IP" "$RST"
  printf '  %sRAM%s %s   %sServeurs actifs%s %s%s%s   %sen ligne depuis%s %s\n' \
     "$DIM" "$RST" "$RAM" "$DIM" "$RST" "$AMBER" "$RUN" "$RST" "$DIM" "$RST" "$UP"
  printf '\n  %sConsole locale : Alt+F2 pour un terminal. Identifiants par défaut affichés au 1er lancement.%s\n' "$DIM" "$RST"
  sleep 5
done
