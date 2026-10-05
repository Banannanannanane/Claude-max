# Trouve le Foin — le jeu 3D

Un jeu mobile en 3D inspiré de *Find the Needle* (Steam), mais **à l'envers** : au lieu de
chercher une aiguille dans une botte de foin, tu fouilles des **tas d'aiguilles** pour retrouver
les **22 brins de foin** cachés dans chacun. Au début tout se fait à la main. Ensuite tu vends ta
production, tu achètes des améliorations et des droits de construction, et tu finis par
automatiser toute la ferme.

Fait avec **Godot 4.5** (rendu 3D « Compatibility », OpenGL ES 3), pour Android et iOS.
L'objectif est 60 images/s sur un Snapdragon 7s Gen 3.

## Télécharger le jeu (Android)

Chaque push compile le jeu automatiquement (GitHub Actions → onglet **Releases**) :

- `TrouveLeFoin.apk` : ouvre-le sur le téléphone. Si Android le demande, autorise
  l'« installation d'applis inconnues ».
- `TrouveLeFoin.aab` : le paquet à envoyer sur la Google Play Console.

## Le jeu

| | |
|---|---|
| **Tas d'aiguilles** | 5 tailles : petit (600 aiguilles), moyen (2 500), gros (10 000), énorme (40 000) et montagne (200 000). Chacun cache 22 brins de foin, et chaque brin trouvé rapporte une prime. Trouver les 22 permet de commander le tas suivant au bureau. |
| **À la main** | Vise le tas et garde ACTION appuyé pour ramasser. Verse ensuite dans une trémie, ou pose ta poignée directement sur un tapis. L'endurance limite la course et le ramassage. |
| **Convoyeurs** | De vrais tapis en 3D sur une grille : les lots d'aiguilles et les lingots y circulent. Pour en poser une ligne, garde PLACER appuyé en marchant. Les séparateurs répartissent le flux, les stockages tampons l'absorbent. |
| **Un seul point de vente** | Le **trou de vente**, un grand puits dans le sol : seul ce que les tapis y font tomber est payé. Le foin non détecté qui y tombe retourne dans le tas. |
| **Scanners** | Placés sur la ligne, ils détectent le foin caché, et les aiguilles en ressortent vérifiées. |
| **Métallurgie** | Aiguilles vérifiées → **fonderie** (lingots bruts) → **purificateur** (lingots purs) → **presse à tôles** ou **tréfileuse** (bobines de fil) → **aiguilleuse** (boîtes d'aiguilles neuves). Chaque étape vaut plus cher. |
| **Automatisation** | Bras robots et pelleteuses à placer près du tas, et drones collecteurs qui font la navette jusqu'aux trémies. |
| **Arbre technologique** | 5 étapes. Les **plans** donnent le droit de construire chaque machine, et chacun a ses **améliorations à niveaux** : tapis plus rapides, fonte plus rapide, grand creuset (plus de lingots par fournée), qualité (meilleur prix), portée des bras… On y achète aussi les **contrats** pour les tas plus gros et des bonus (trou élargi, automatisation avancée). |
| **Boutique** | Capacité de la main, fourche, endurance, récupération, bottes, longs bras (ramasser et placer plus loin), œil de lynx, trémies géantes. |
| **Bureau** | Commande des tas et **contrats de livraison** à prime, avec un délai. |
| **Sauvegardes** | 3 emplacements avec sauvegarde automatique, sauvegarde manuelle et chargement. La production continue hors ligne (8 h maximum). Il y a aussi 12 **succès** à débloquer. |

Chaque machine prend ce qui arrive par l'arrière (flèche verte) et rend sa production par
l'avant (flèche bleue). La plupart des machines occupent 2×2 cases, et la zone constructible fait
160×160 cases. Un voyant sur chaque machine indique son état : vert en marche, orange en attente,
rouge si elle est bloquée (sortie pleine ou trop loin du tas).

Il y a aussi des **objectifs guidés** avec primes pour apprendre le jeu, et un **cycle jour/nuit**
avec lampadaires (désactivable). Le bouton Retour d'Android ferme les fenêtres et demande
confirmation avant de quitter.

Commandes tactiles : joystick flottant à gauche, glisser à droite pour regarder, boutons
ACTION, Courir et Saut. En mode construction : PLACER, Pivoter et Annuler. Au clavier (pour
tester sur PC) : ZQSD/WASD, souris, E, R pour pivoter, Entrée pour placer, Maj, Espace.

## Structure

```
game/                Projet Godot (ouvrir game/project.godot dans l'éditeur Godot 4.5)
  scripts/game.gd    Économie et état de la partie (autoload « Game »)
  scripts/data.gd    Tables : tas, bâtiments, arbre, améliorations
  scripts/world.gd   Décor 3D ; pile.gd = le tas ; buildings.gd = modèles des machines
  scripts/player.gd  Joueur à la première personne, ramassage, mode construction
  scripts/hud.gd     HUD et contrôles tactiles ; panels.gd = boutique, arbre, vente…
  scripts/qa.gd      Tests automatiques (exclus de l'export)
.github/workflows/   Compilation Android (APK + AAB) et projet Xcode iOS
docs/STORE.md        Publier sur Google Play et l'App Store
```

## Tests

`tools/run_tests.sh /chemin/vers/godot` lance environ 150 vérifications automatiques. Le script
échoue au moindre test raté ou à la moindre erreur du moteur. Il couvre :
- la géométrie de chaque machine dans les 4 sens ;
- les règles de construction, les tapis (virages, fusions, séparateurs, boucles, tampons) et
  chaque recette de bout en bout ;
- les bras, les pelleteuses, les drones et la détection du foin ;
- les 5 tailles de tas, l'arbre, la boutique, les contrats, les objectifs et les succès ;
- les sauvegardes (y compris les fichiers abîmés), un test de charge à 850 tapis ;
- toutes les fenêtres et leurs boutons, le bouton Retour, et des gestes tactiles simulés
  (joystick, regard, multitouche, ACTION, PLACER).

## Licence des fichiers

Tout le contenu (code, modèles 3D procéduraux, sons synthétisés) est original et a été créé
pour ce projet. Le moteur Godot est sous licence MIT.
