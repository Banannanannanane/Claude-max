# Les sprites

Le jeu est livré avec un pack de sprites, et sait dessiner sans lui. Si le pack
est absent ou incomplet, chaque sprite manquant retombe **individuellement** sur
l'art interne : un nom absent du mapping garde son dessin d'origine, une ligne
mal écrite ne coûte que son sprite.

## Le pack livré

| | |
| --- | --- |
| Pack | *16x16 DungeonTileset II*, v1.7 |
| Auteur | [0x72](https://0x72.itch.io/dungeontileset-ii) |
| Licence | CC0 (domaine public) |

Le détail est dans `app/src/main/assets/ATTRIBUTION.txt`, et la liste complète
des tuiles du pack dans `tile_list_v1.7.txt` — c'est là qu'il faut piocher pour
changer un monstre.

## Les deux fichiers

Tout se joue dans `app/src/main/assets/` :

| Fichier | Rôle |
| --- | --- |
| `sprites.png` | une seule image contenant tous les sprites (un atlas) |
| `sprites.txt` | quel rectangle de l'image est quel personnage |

Rien d'autre à toucher. Si l'un des deux manque, le jeu reste sur son art
interne et n'en parle pas.

## Le mapping

Une ligne par sprite, `#` pour les commentaires :

```
# nom = x, y, largeur, hauteur
KNIGHT       = 128, 108, 15, 20   # knight_m_idle_anim_f0
GRAVE        = 293, 439,  6,  6   # skull
BOSS         =  21, 434, 23, 30   # big_demon_idle_anim_f0
```

Les noms sont ceux du jeu (`SpriteKey`), insensibles à la casse : quatre héros,
une tombe, cinq monstres, un boss.

**Serre les rectangles sur les pixels dessinés.** La plupart des packs livrent
des cases uniformes (16x16, 16x28…) avec du vide autour du dessin. Ce vide est
compté comme du sprite : un héros flotte au-dessus du sol, un groupe s'espace
comme s'il tenait deux fois sa largeur. Les valeurs ci-dessus sont les cases du
pack rognées à leur contenu.

**Les frames n'ont besoin d'être ni carrées ni de même taille**, et c'est même le
sujet : l'échelle est **commune à tout le casting** — un entier, `boîte / plus
grande frame` — donc les tailles du mapping *sont* les proportions à l'écran.
L'ogre écrase le chevalier, le crâne d'un héros tombé reste une babiole par
terre. Chaque sprite est centré horizontalement et **posé sur le sol** ; celui
qui dépasse sa boîte déborde vers le haut plutôt que d'être écrasé dedans.

Une conséquence à connaître : **la plus grande frame fixe la grille**. Mapper une
tuile de décor de 64 px rapetisserait tout le monde d'un cran.

Le blit est en *nearest neighbour* des deux côtés (Android et previewer) :
lisser un atlas pixel, c'est en faire une photo d'un dessin.

## Vérifier sans téléphone

```bash
./gradlew :preview:run --args="docs/preview"
```

Le previewer rend exactement les mêmes layouts que le widget, **avec le pack** :
il trouve `app/src/main/assets/` tout seul (un second argument permet de pointer
ailleurs). Il annonce ce qu'il a chargé et ce qui manque :

```
art: 11 frames on a 30px grid, from 512x512
  SKELETON: no frame, keeping the built-in art
  KNGIHT: not a sprite the game asks for
```

## Changer de pack — à lire avant de committer

Le pack finit dans un dépôt public. Il faut donc une licence qui **autorise la
redistribution** :

- **CC0 / domaine public** — idéal, rien à faire. [Kenney](https://kenney.nl)
  (tout son catalogue), *0x72 DungeonTileset II*.
- **CC-BY** — possible, mais l'attribution est obligatoire : ajoute le crédit
  dans ce fichier et garde le texte de licence à côté de l'image.
- **« Free for personal use », « do not redistribute »** — **non**. Beaucoup de
  packs itch.io gratuits sont dans ce cas ; on peut s'en servir sur sa machine,
  pas les publier.

Mets le fichier de licence du pack dans `app/src/main/assets/` avec l'image, et
note d'où il vient dans `ATTRIBUTION.txt` :

```
Pack    : <nom>
Auteur  : <auteur>
Source  : <url>
Licence : <CC0 / CC-BY 4.0 / ...>
```
