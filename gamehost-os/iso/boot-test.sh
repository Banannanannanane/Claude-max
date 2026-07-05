#!/bin/bash
# Teste l'ISO dans QEMU (sans KVM → émulation TCG, lent mais suffisant) :
# démarre la VM, redirige le port 8080 du panneau vers l'hôte, et vérifie que
# l'interface répond. Usage : ./boot-test.sh GameHostOS.iso
set -u
ISO="${1:?chemin de l ISO requis}"
LOG=/tmp/gh-boot.log
: > "$LOG"
echo "== Démarrage QEMU (TCG, 3 Go RAM, 2 vCPU) =="
qemu-system-x86_64 \
  -m 3072 -smp 2 \
  -cdrom "$ISO" \
  -boot d \
  -nographic \
  -serial "file:$LOG" \
  -netdev user,id=n0,hostfwd=tcp::8081-:8080 \
  -device e1000,netdev=n0 \
  -no-reboot &
QPID=$!
trap 'kill $QPID 2>/dev/null' EXIT

echo "== Attente du panneau (max 12 min d emulation) =="
OK=0
for i in $(seq 1 144); do
  if curl -s --max-time 3 http://127.0.0.1:8081/api/status | grep -q '"setup"'; then
    OK=1; break
  fi
  sleep 5
done

echo "== Dernières lignes de la console série =="
tail -20 "$LOG" 2>/dev/null

if [ "$OK" = 1 ]; then
  echo "RESULTAT: OK — le panneau répond dans la VM."
  curl -s http://127.0.0.1:8081/api/status; echo
  exit 0
else
  echo "RESULTAT: le panneau n a pas repondu (boot TCG trop lent ou erreur)."
  exit 1
fi
