# 🎮 GameHost OS

Un **OS d'hébergement de serveurs de jeux**, clé en main : tu graves l'ISO, tu
démarres ta machine, et une **interface web** te permet d'installer et gérer des
serveurs **Counter-Strike 2, Rust, Minecraft, Valheim** (et tout jeu SteamCMD).

Basé sur **Ubuntu 24.04** (serveur, sans bureau) → la consommation d'un Ubuntu de
base, **sans conteneur** : les serveurs de jeux tournent en processus natifs
supervisés (overhead quasi nul, pas la couche Docker).

```
┌──────────────────────────────────────────────┐
│  Ubuntu 24.04 (headless, durci, réglé réseau) │
│  ├─ gamehost.service   → panneau web :8080    │
│  ├─ écran d'état local sur le moniteur (tty1) │
│  ├─ SteamCMD + presets CS2/Rust/Valheim/MC    │
│  └─ pare-feu (ufw) + sysctl optimisé jeux     │
└──────────────────────────────────────────────┘
```

## À quoi ça ressemble

Le panneau web (ci-dessous) : cartes de serveurs avec état en direct (RAM, PID,
relances), boutons Installer / Démarrer / Arrêter / Console, et un formulaire de
création par jeu. Écran de première configuration (mot de passe admin), puis
connexion. Voir `panel/web` pour l'interface.

## Utiliser l'ISO

1. **Grave** `GameHostOS.iso` sur une clé USB (Rufus, balenaEtcher, ou
   `dd if=GameHostOS.iso of=/dev/sdX bs=4M`).
2. **Démarre** ta machine dessus (Boot USB). L'écran affiche l'adresse à ouvrir.
3. Depuis un autre appareil du réseau, ouvre **`http://IP-de-la-machine:8080`**,
   choisis un mot de passe, et crée ton premier serveur.
4. Clique **Installer** (télécharge le jeu via SteamCMD) puis **Démarrer**.

> L'ISO démarre en mode « live ». Pour une installation permanente sur disque,
> voir la section Installation du wiki (ou lance l'installateur Ubuntu inclus).

## Construire l'ISO soi-même

Sur une machine Ubuntu/Debian avec accès Internet (idéalement KVM pour tester) :

```bash
sudo apt-get install live-build debootstrap xorriso squashfs-tools \
     grub-pc-bin grub-efi-amd64-bin mtools ubuntu-keyring golang-go
cd iso && sudo ./build.sh          # → GameHostOS.iso
```

`build.sh` : compile le panneau (Go, binaire statique), configure `live-build`
en mode Ubuntu, injecte l'overlay (services, scripts, presets), puis assemble
l'ISO hybride (BIOS + UEFI).

## Le panneau (`panel/`)

- **Go, stdlib uniquement** (aucune dépendance) → un seul binaire de ~6 Mo.
- Supervise chaque serveur comme **processus enfant** : logs en anneau + flux
  console (SSE), relance auto après crash, stats CPU/RAM via `/proc`.
- API JSON + interface web embarquée (`go:embed`). Auth par mot de passe
  (hash salé et étiré, session cookie).
- **Presets déclaratifs** (`preset.go`) : ajouter un jeu = quelques lignes.

Tests : `cd panel && go test -race ./...` — 8 suites (auth, cycle de vie
install/start/stop/restart/delete, relance auto après crash, éditeur de config,
persistance, placeholders, presets), **race detector propre**.

Lancer en local (hors ISO) pour essayer l'interface :

```bash
cd panel && go run . -listen :8080 -data /tmp/gh/data -srv /tmp/gh/srv -config /tmp/gh/cfg.json
# puis http://localhost:8080
```

## Structure

```
gamehost-os/
├─ panel/                  # panneau de contrôle (Go) + interface web + tests
│  ├─ main.go preset.go supervisor.go panel_test.go
│  └─ web/index.html
└─ iso/                    # construction de l'OS (live-build Ubuntu)
   ├─ build.sh
   ├─ overlay/             # fichiers injectés dans le système (→ includes.chroot)
   │  ├─ opt/gamehost/{panel, bin/*.sh}
   │  └─ etc/systemd/system/*.service, etc/sysctl.d, ...
   ├─ package-lists/gamehost.list.chroot
   └─ hooks/0100-gamehost.hook.chroot
```

## Sécurité (à faire côté hébergeur)

Pare-feu activé par défaut (SSH, panneau, plages de ports de jeu). Change le mot
de passe admin au premier lancement. Le panneau tourne en root (gestion des
services/SteamCMD) mais n'est exposé qu'au réseau local par défaut — mets-le
derrière un VPN/reverse-proxy si tu l'ouvres sur Internet.
