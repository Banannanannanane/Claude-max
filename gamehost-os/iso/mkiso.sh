#!/bin/bash
# Assemble l'ISO live bootable (BIOS + UEFI) à partir du chroot déjà construit,
# avec GRUB 2 (grub-mkrescue) et casper — contourne l'étape bootloader cassée
# de l'ancien live-build. Usage : sudo ./mkiso.sh [work/chroot] [sortie.iso]
set -euo pipefail
cd "$(dirname "$0")"
CHROOT="${1:-work/chroot}"
OUT="${2:-GameHostOS.iso}"
STAGE="work/iso"
COMP="${COMP:-zstd}"   # zstd (rapide) ou xz (plus petit)

[ -d "$CHROOT" ] || { echo "chroot introuvable: $CHROOT" >&2; exit 1; }

echo "== préparation de l'arborescence ISO =="
rm -rf "$STAGE"; mkdir -p "$STAGE/casper" "$STAGE/boot/grub" "$STAGE/.disk"
KVER=$(basename "$(ls -1 "$CHROOT"/boot/vmlinuz-* | sort -V | tail -1)" | sed 's/vmlinuz-//')
cp "$CHROOT/boot/vmlinuz-$KVER" "$STAGE/casper/vmlinuz"
cp "$CHROOT/boot/initrd.img-$KVER" "$STAGE/casper/initrd"
echo "GameHost OS — hôte de serveurs de jeux" > "$STAGE/.disk/info"

echo "== compression du système de fichiers (squashfs, $COMP) =="
rm -f "$STAGE/casper/filesystem.squashfs"
mksquashfs "$CHROOT" "$STAGE/casper/filesystem.squashfs" \
  -comp "$COMP" -noappend -no-progress \
  -e boot/vmlinuz-* -e boot/initrd.img-* \
  -e proc -e sys -e dev/pts
printf '%s' "$(du -sx --block-size=1 "$CHROOT" | cut -f1)" > "$STAGE/casper/filesystem.size"

echo "== configuration GRUB =="
cat > "$STAGE/boot/grub/grub.cfg" <<'GRUB'
set default=0
set timeout=5
serial --unit=0 --speed=115200
terminal_input console serial
terminal_output console serial
insmod all_video
menuentry "GameHost OS (demarrer l hote de serveurs de jeux)" {
    linux /casper/vmlinuz boot=casper console=tty0 console=ttyS0,115200 ---
    initrd /casper/initrd
}
menuentry "GameHost OS (mode sans echec / nomodeset)" {
    linux /casper/vmlinuz boot=casper nomodeset console=tty0 console=ttyS0,115200 ---
    initrd /casper/initrd
}
GRUB

echo "== génération de l'ISO hybride (BIOS + UEFI) =="
grub-mkrescue --compress=xz -o "$OUT" "$STAGE" -- -volid GAMEHOST 2>/dev/null
echo "OK → $(pwd)/$OUT ($(du -h "$OUT" | cut -f1))"
