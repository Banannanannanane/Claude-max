#!/bin/bash
# Finalise le chroot avant compression : overlay, services, Node, sources
# Claude Code, et allègement (optimisation de la taille de l'ISO).
# Usage : sudo ./finalize-chroot.sh work/chroot
set -euo pipefail
cd "$(dirname "$0")"
HERE=$(pwd)
CHROOT="${1:-work/chroot}"
[ -d "$CHROOT" ] || { echo "chroot introuvable: $CHROOT" >&2; exit 1; }

echo "== overlay (panneau, services, scripts) =="
cp -a "$HERE/overlay/." "$CHROOT/"
chmod +x "$CHROOT"/opt/gamehost/bin/*.sh "$CHROOT"/opt/gamehost/panel

echo "== sources sur la machine (pour Claude Code) =="
mkdir -p "$CHROOT/opt/gamehost/src"
cp -a "$HERE/../panel" "$CHROOT/opt/gamehost/src/" 2>/dev/null || true
cp -a "$HERE" "$CHROOT/opt/gamehost/src/iso" 2>/dev/null || true
rm -rf "$CHROOT/opt/gamehost/src/iso/work" "$CHROOT/opt/gamehost/src/panel/gamehost-panel" 2>/dev/null || true

# monte le minimum pour apt/systemctl dans le chroot
mount_pseudo() {
  mount --bind /dev "$CHROOT/dev" 2>/dev/null || true
  mount -t proc proc "$CHROOT/proc" 2>/dev/null || true
  mount -t sysfs sys "$CHROOT/sys" 2>/dev/null || true
  cp /etc/resolv.conf "$CHROOT/etc/resolv.conf" 2>/dev/null || true
}
umount_pseudo() {
  umount -l "$CHROOT/dev" "$CHROOT/proc" "$CHROOT/sys" 2>/dev/null || true
}
trap umount_pseudo EXIT
mount_pseudo

echo "== Node.js (runtime de Claude Code) si absent =="
if ! chroot "$CHROOT" sh -c 'command -v node' >/dev/null 2>&1; then
  chroot "$CHROOT" sh -c 'unset http_proxy HTTP_PROXY; DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends nodejs npm' \
    || echo "!! Node non installé (réseau) — Claude Code s'installera au 1er boot si npm arrive plus tard."
fi

echo "== activation des services + réglages =="
chroot "$CHROOT" sh -e <<'INCH'
echo gamehost > /etc/hostname
grep -q gamehost /etc/hosts || echo "127.0.1.1 gamehost" >> /etc/hosts
systemctl mask getty@tty1.service || true
systemctl enable gamehost-firstboot.service gamehost.service gamehost-console.service || true
systemctl enable ssh.service 2>/dev/null || true
cat > /etc/motd <<'EOF'

  GameHost OS — hôte de serveurs de jeux
  Panneau web : http://<ip>:8080   ·   fichiers : /srv/gamehost
  Claude Code : « claude » dans /opt/gamehost/src   ·   logs : journalctl -u gamehost

EOF
INCH

echo "== optimisation (allègement) =="
chroot "$CHROOT" sh -c 'apt-get clean; rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*' || true
rm -rf "$CHROOT"/usr/share/doc/* "$CHROOT"/usr/share/man/* \
       "$CHROOT"/usr/share/info/* "$CHROOT"/var/cache/apt/archives/*.deb 2>/dev/null || true
# ne garde que les locales fr + en
find "$CHROOT/usr/share/locale" -mindepth 1 -maxdepth 1 -type d \
     ! -name 'fr*' ! -name 'en*' ! -name 'C*' -exec rm -rf {} + 2>/dev/null || true

echo "== finalisation terminée =="
