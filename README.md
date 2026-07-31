# Taskbar Hero — mobile

Recréation de [*TBH: Task Bar Hero*](https://store.steampowered.com/app/3678970/TBH_Task_Bar_Hero/)
(Nugem Studio) pour Android : une application, et un widget d'écran d'accueil qui
en est la fenêtre — comme le jeu d'origine tient dans une fenêtre dockée à la
barre des tâches Windows.

> Projet hommage, sans affiliation avec Nugem Studio ni Nothing Technology.

**État : squelette.** Le jeu n'est pas encore écrit. Ce qui existe, c'est la
chaîne qui produit un vrai `.apk`, et la porte qui décide si l'app a de quoi
s'afficher.

## Le jeu ne dessine rien lui-même

Aucun sprite, aucune police, aucune icône n'est produite par ce dépôt. **Tout
vient d'un pack fourni**, et il n'y a ni repli ni image bouche-trou : si un
fichier requis manque, l'app affiche la liste de ce qu'elle attend et s'arrête.
Un jeu privé de son art n'est pas un jeu, et un APK à moitié illustré ne doit pas
pouvoir sortir sans que ça se voie.

La liste fait foi dans le code, pas dans une documentation qui dériverait :
[`engine/.../assets/Assets.kt`](engine/src/main/kotlin/dev/taskbarhero/assets/Assets.kt)
est à la fois le bon de commande, le contrat du chargeur et le texte de l'écran
d'erreur.

| Fichier | Requis | Pour quoi |
| --- | --- | --- |
| `art/fighters.png` + `.txt` | oui | trois classes de héros, leur pose d'attaque, un marqueur de héros tombé, six monstres, un boss |
| `art/ui.png` + `.txt` | oui | cadres, boutons dans leurs trois états, jauge, case d'inventaire |
| `art/font.png` + `.txt` | oui | police bitmap A-Z 0-9 et `+ - . % / : x` — une image, pas un TTF |
| `art/ATTRIBUTION.txt` | oui | d'où vient l'art et sous quelle licence |
| `art/room.png` + `.txt` | non | murs, sol, torches, colonnes, coffres |
| `art/icons.png` + `.txt` | non | pièce, potion, gemme, rune |

Le dépôt est public : la licence du pack doit **autoriser la redistribution**
(CC0 idéal, CC-BY avec crédit, « free for personal use » non).

## L'APK

Il est compilé par GitHub Actions à chaque push, et téléchargeable depuis
l'onglet **Actions** → le run → *Artifacts* → `taskbar-hero-debug-apk`.
C'est un APK de debug, signé avec la clé de debug : il s'installe directement sur
un téléphone (autoriser les sources inconnues).

```bash
./gradlew :app:assembleDebug   # en local, si tu as le SDK Android
./gradlew :engine:test         # la simulation, sans appareil ni SDK
```

## Structure

```
engine/   Kotlin pur, zéro dépendance Android — les règles du jeu, et tout ce qui
          se teste sans appareil
app/      Android — l'application, le widget, et rien qui décide du jeu
```
