#!/bin/bash
# Construit GameHost OS : une ISO live bootable basée sur Ubuntu 24.04,
# préconfigurée pour héberger des serveurs de jeux (panneau web + presets).
#
#   sudo ./build.sh
#
# Prérequis (Ubuntu/Debian) :
#   sudo apt-get install live-build debootstrap xorriso squashfs-tools \
#        grub-pc-bin grub-efi-amd64-bin mtools ubuntu-keyring golang-go
set -euo pipefail
cd "$(dirname "$0")"
HERE=$(pwd)
WORK="${WORK:-$HERE/work}"
MIRROR="${MIRROR:-http://archive.ubuntu.com/ubuntu/}"
DIST="${DIST:-noble}"

echo "== 1/5 : compilation du panneau (binaire statique) =="
GO=$(command -v go || echo /usr/local/go/bin/go)
if [ -x "$GO" ]; then
  ( cd ../panel && CGO_ENABLED=0 "$GO" build -trimpath -ldflags "-s -w" -o "$HERE/overlay/opt/gamehost/panel" . )
elif [ -x "$HERE/overlay/opt/gamehost/panel" ]; then
  echo "go absent — utilisation du binaire déjà présent dans l'overlay."
else
  echo "!! Go introuvable et aucun binaire pré-compilé." >&2; exit 1
fi
ls -la overlay/opt/gamehost/panel

echo "== 2/5 : préparation de la config live-build =="
# Le proxy de ce réseau ne tunnelise que le HTTPS ; le HTTP direct fonctionne.
# On retire donc tout proxy HTTP, sinon apt/debootstrap échouent (405).
unset http_proxy HTTP_PROXY || true
if [ -d "$WORK/cache" ]; then
  cd "$WORK"; lb clean >/dev/null 2>&1 || true   # garde le cache de paquets → reconstruction rapide
else
  rm -rf "$WORK"; mkdir -p "$WORK"; cd "$WORK"
fi
# GRUB pour BIOS + UEFI (syslinux tire des thèmes Ubuntu obsolètes qui n'existent plus).
lb config \
  --mode ubuntu \
  --distribution "$DIST" \
  --archive-areas "main restricted universe multiverse" \
  --mirror-bootstrap "$MIRROR" \
  --mirror-chroot "$MIRROR" \
  --mirror-binary "$MIRROR" \
  --linux-flavours generic \
  --binary-images iso-hybrid \
  --apt-recommends false \
  --apt-indices false \
  --bootappend-live "boot=casper quiet splash ---" \
  --iso-application "GameHost OS" \
  --iso-publisher "GameHost" \
  --iso-volume "GameHost"

echo "== 3/5 : injection de l'overlay, des paquets et des hooks =="
mkdir -p config/includes.chroot config/package-lists config/hooks/normal
cp -a "$HERE/overlay/." config/includes.chroot/
cp "$HERE/package-lists/gamehost.list.chroot" config/package-lists/
cp "$HERE/hooks/0100-gamehost.hook.chroot" config/hooks/normal/

echo "== 4/5 : bootstrap + chroot (l'ancien live-build ne sait pas amorcer un
        bootloader moderne : on saute son étape binaire et on assemble l'ISO
        nous-mêmes avec GRUB 2) =="
lb bootstrap
lb chroot

echo "== 5/5 : finalisation + assemblage de l'ISO (GRUB 2, casper) =="
"$HERE/finalize-chroot.sh" "$WORK/chroot"
COMP="${COMP:-zstd}" "$HERE/mkiso.sh" "$WORK/chroot" "$HERE/GameHostOS.iso"
