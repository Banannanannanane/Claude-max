# Taskbar Hero — widget Nothing Phone

Un **idle-RPG en barre**, inspiré de [*TBH: Task Bar Hero*](https://store.steampowered.com/app/3678970/TBH_Task_Bar_Hero/)
(Nugem Studio) — le jeu qui tient dans une fenêtre minuscule dockée à la barre des
tâches Windows. Ici cette fenêtre devient une **bande 4×1 sur l'écran d'accueil
d'un Nothing Phone** : une salle de donjon en pixel art où un héros enchaîne les
vagues tout seul, même écran éteint.

> Projet hommage, sans affiliation avec Nugem Studio ni Nothing Technology.

![Les états de la barre](docs/preview/sheet.png)

## Ce que ça fait

La barre tient tout le jeu en trois zones :

| Zone | Contenu |
| --- | --- |
| Gauche | acte-vague (`7-06`), niveau, et l'or avec sa pièce |
| Centre | la salle : mur de briques, torche, le héros face au monstre, une jauge de vie sous chacun |
| Droite | le seul bouton : **LV UP**, cerclé d'or dès que le prix est payable |

- **Un appui sur la bande** ouvre le donjon plein écran (mêmes sprites, ×4).
- **Un appui sur le bouton** achète un niveau de héros.
- **Redimensionnée en 4×2**, la barre gagne un bandeau : acte, record, barre de
  runes, et le fil d'événements. Élargie en 5×2, les combattants passent à ×2.
- Toutes les 10 vagues, un boss ; le tuer fait vibrer le téléphone et lâche un
  butin dont **la couleur dit le grade** (les 10 grades de TBH, de Common à
  Cosmic). Si le héros tombe, il repart au début de l'acte — l'or et les niveaux
  restent acquis.
- L'option **AUTO** (dans le donjon) laisse le moteur dépenser l'or tout seul :
  le mode « je le pose et je l'oublie ».

<p align="center">
  <img src="docs/preview/bar-4x1-boss.png" width="49%" alt="Barre 4x1, combat de boss">
  <img src="docs/preview/bar-4x1-down.png" width="49%" alt="Barre 4x1, héros à terre">
</p>

## Direction artistique

Celle du jeu d'origine, pas celle du téléphone : pierre chaude, lettres parchemin,
chiffres dorés, codes ARPG que personne n'a besoin qu'on lui explique.

- **Tout est un carré plein.** Aucune courbe anti-aliasée, aucun dégradé, aucun
  coin arrondi. Les panneaux sont biseautés à l'ancienne — arête claire en haut à
  gauche, sombre en bas à droite.
- **Police bitmap 5×7**, chaque pixel posé sur une ombre d'un pixel : c'est ce qui
  rend le parchemin lisible sur la maçonnerie.
- **Sprites indexés par palette** (`'.'` transparent, `'1'..'9'` = couleurs
  propres au sprite), écrits en art ASCII dans le code : diffable, testable, et
  chaque combattant porte son contour en index 1.
- **Échelles entières uniquement.** Réduire un sprite de 16 px à 11 px supprime
  des pixels source et donne cette bouillie caractéristique ; tous les
  combattants tiennent donc sur la même grille 12×12 et ne grossissent que par
  multiples entiers. C'est aussi pourquoi une barre plus large montre de plus
  grands héros, alors qu'une barre plus haute montre une plus grande salle.
- **La teinte porte le sens** : rouge = vie, bleu = progression, or = monnaie, et
  les couleurs de grade sont réservées au butin. Un prix inaccessible ne brille
  pas — la pièce disparaît, elle ne se contente pas de pâlir.

<p align="center">
  <img src="docs/preview/dungeon.png" width="52%" alt="Le donjon plein écran">
</p>

## Le vrai problème technique

Un widget Android n'est pas une fenêtre : il n'a pas de boucle de rendu, son
`updatePeriodMillis` a un plancher de 30 minutes, et il est redessiné à des
moments qu'on ne choisit pas. Un jeu qui « tourne » en barre ne peut donc pas
compter les images.

La simulation est donc une **fonction pure du temps écoulé** :
`IdleEngine.advance(state, now)` rejoue le combat par pas de 250 ms depuis le
dernier tick enregistré. D'où trois propriétés utiles :

- avancer d'un bloc ou en cent morceaux donne **exactement le même état**
  (le tick ne bouge que par multiples entiers) — c'est testé ;
- la progression hors-ligne est gratuite, plafonnée à 12 h de rattrapage ;
- un rafraîchissement raté ne coûte qu'une image périmée, jamais une partie
  fausse.

Le rafraîchissement lui-même passe par une alarme **inexacte** de 30 s qui se
reprogramme (`WidgetTicker`) : jamais de réveil forcé du téléphone pour animer
une jauge de vie.

## Architecture

Trois modules, découpés par ce qui est testable :

```
engine/    Kotlin pur, zéro dépendance Android
  engine/  simulation (IdleEngine, Balance, GameState), police 5x7, sprites, butin
  paint/   Surface (2 méthodes) + PixelPainter + BarLayout / DungeonLayout
app/       Android : AppWidgetProvider, RemoteViews, SharedPreferences, activité
preview/   Rend les MÊMES layouts en PNG sur JVM (AWT) — revue de design sans émulateur
```

Le point d'articulation est `paint.Surface` : une interface de dessin à deux
méthodes (`rect`, `circle`). La mise en page — chaque pixel, chaque jauge — vit
dans le module pur et tourne donc à l'identique dans trois contextes :

| Implémentation | Où | Pour quoi |
| --- | --- | --- |
| `AndroidSurface` (Canvas) | `app/` | le téléphone |
| `AwtSurface` (Graphics2D) | `preview/` | les PNG de ce README |
| `RecordingSurface` | tests | vérifier que rien ne dépasse de la barre |

Les images de ce README **sortent du code de production**, pas d'une maquette. Et
c'est ce qui a payé : le previewer a révélé des défauts que la relecture ne
donnait pas — jauges recouvrant la tête des combattants, épée en deux morceaux
visible seulement à ×4, légende `DEFEATED` tronquée en `DEFEATE`.

Une note sur la couleur et la persistance : le widget ne garde qu'une ligne
d'événement en `SharedPreferences`, et une diffusion plus tard l'objet
`GameEvent` n'existe plus. La tonalité et le grade de butin voyagent donc avec le
texte (`Ticker`), sinon la couleur serait perdue au prochain rafraîchissement.

## Tests

```bash
./gradlew :engine:test                          # 35 tests
./gradlew :preview:run --args="docs/preview"    # régénère les PNG
```

Ce qui est couvert, au-delà du « ça ne crashe pas » :

- **Déterminisme** : découper l'avancée en morceaux irréguliers donne le même
  état qu'un seul appel.
- **Bornes de mise en page** : une surface d'enregistrement capture chaque
  primitive et vérifie qu'aucune ne sort du cadre, pour 6 tailles de widget × 7
  états de jeu (dont un run à `LV 240` / `1.4T` d'or, le texte le plus large que
  la barre puisse afficher). C'est le bug qu'un widget ne montre pas : le
  launcher se contente de rogner.
- **Intégrité des sprites** : chaque art est carré, non vide, contouré, et tout
  index de couleur résout — sinon le sprite lèverait une exception au dessin.
- **Lisibilité** : chaque caractère produit par le HUD a bien un glyphe dans la
  police 5×7 ; une jauge garde un pixel allumé à 0,1 % de vie et un pixel éteint
  à 99,9 % ; le bouton ne s'allume que si le niveau est payable.
- **Équilibrage** : une heure d'idle en AUTO franchit l'acte 1 et tue un boss ;
  l'achat automatique ne passe jamais l'or en négatif ; un héros niveau 1 perd
  bien contre un boss d'acte 6 et recule sans perdre son acte.

## Compiler l'APK

```bash
./gradlew :app:assembleDebug
```

Il faut un SDK Android (`compileSdk 35`, `minSdk 31` — le Nothing Phone (1) est
sorti sous Android 12) et un accès à `dl.google.com` pour le plugin Gradle
Android.

**État de vérification, en toute transparence :** le module `engine` et le
previewer sont compilés et testés (35/35 verts), et les captures ci-dessus en
sortent. Le module `app` n'a **pas** pu être compilé dans l'environnement utilisé
ici : ni SDK Android, ni accès à `dl.google.com`. Ses sources ont en revanche été
type-checkées hors Android contre des stubs minimaux des API utilisées, donc les
références et les signatures tiennent ; attendez-vous quand même à un détail à
corriger au premier `assembleDebug`.

## Glyph

Les bandes lumineuses du téléphone passent par le *Glyph Developer Kit* de
Nothing (`com.nothing.ketchum`), qui n'est ni sur Maven Central ni utilisable
sans app-id signé. Le point d'accroche est unique et documenté : `fx/Fx.kt`,
`onEvent()`, là où les boss et les montées de niveau déclenchent déjà l'haptique.
Sur tout autre matériel, le comportement se dégrade proprement en vibration.

## Réglages

Toute la courbe de jeu est dans `engine/.../engine/Balance.kt` — un `data class`
sans état, ce qui permet aux tests de rétrécir les courbes au lieu de simuler des
heures. Les couleurs sont dans `paint/Surface.kt`, les sprites dans
`engine/Sprites.kt`. Pas de constante magique ailleurs.
