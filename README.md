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
| **Tas d'aiguilles** | 4 tailles : petit (10 millions d'aiguilles), moyen (25 millions), grand (100 millions) et montagne (300 millions, environ 30 m de haut). Chacun cache 22 brins de foin, et chaque brin trouvé rapporte une prime. Trouver les 22 permet de commander le tas suivant au bureau. |
| **Relief creusable** | Le tas est un vrai relief : chaque poignée y fait un petit creux à l'endroit visé, et un bras robot ou une pelleteuse ne creuse que dans son rayon d'action. Quand une paroi devient trop raide, les aiguilles s'éboulent. Une fois sa zone vidée, une machine s'arrête (voyant rouge) : il faut la rapprocher. Les drones prennent au sommet. Le relief creusé est sauvegardé. |
| **Foin enfoui et détecteur** | Comme le détecteur de métaux de Find the Needle, mais à l'envers : chaque brin de foin est caché à un endroit précis du tas. Le **détecteur de foin** bipe de plus en plus vite près de l'endroit visé, et un cercle doré se resserre sur le viseur. Un brin n'est libéré que lorsqu'on creuse jusqu'à lui : il faut fouiller tout le tas. Les brins mis à nu par un éboulement restent visibles sur la surface. |
| **Radar à foin** | Une machine qui révèle les brins enfouis autour d'elle : une balise dorée s'allume au-dessus de chacun, et ils apparaissent sur la carte. |
| **Électricité** | Le réseau fournit 10 kW. Au-delà, il faut un groupe électrogène (15 kW, brûle du carburant), une éolienne (selon le vent, plus fort sous la pluie) ou des panneaux solaires (le jour seulement). S'il manque du courant, toutes les machines ralentissent. |
| **Outils** | La boutique fait évoluer l'outil de fouille : mains nues, pelle, fourche, brouette puis aspirateur. Chaque outil ramasse plus et creuse plus large. |
| **À la main** | Vise le tas et garde ACTION appuyé pour ramasser. Verse ensuite dans une trémie, ou pose ta poignée directement sur un tapis. L'endurance limite la course et le ramassage. |
| **Convoyeurs** | De vrais tapis en 3D sur une grille : les lots d'aiguilles et les lingots y circulent. Pour en poser une ligne, garde PLACER appuyé en marchant. Les séparateurs répartissent le flux, les stockages tampons l'absorbent. |
| **Un seul point de vente** | Le **trou de vente**, un grand puits dans le sol : seul ce que les tapis y font tomber est payé. Le foin non détecté qui y tombe retourne dans le tas. |
| **Scanners** | Placés sur la ligne, ils détectent le foin caché, et les aiguilles en ressortent vérifiées. |
| **Métallurgie** | Aiguilles vérifiées → **fonderie** (lingots bruts) → **purificateur** (lingots purs) → **presse à tôles** ou **tréfileuse** (bobines de fil) → **aiguilleuse** (boîtes d'aiguilles neuves). Chaque étape vaut plus cher. |
| **Automatisation** | Bras robots et pelleteuses à placer près du tas, et drones collecteurs qui font la navette jusqu'aux trémies. |
| **Arbre technologique** | 5 étapes. Les **plans** donnent le droit de construire chaque machine, et chacun a ses **améliorations à niveaux** : tapis plus rapides, fonte plus rapide, grand creuset (plus de lingots par fournée), qualité (meilleur prix), portée des bras… On y achète aussi les **contrats** pour les tas plus gros et des bonus (trou élargi, automatisation avancée). |
| **Boutique** | Capacité de la main, fourche, endurance, récupération, bottes, longs bras (ramasser et placer plus loin), œil de lynx, trémies géantes. |
| **Bureau** | Commande des tas et **contrats de livraison** à prime, avec un délai. |
| **Sauvegardes** | 3 emplacements avec sauvegarde automatique, sauvegarde manuelle et chargement. La production continue hors ligne (8 h maximum). Il y a aussi 14 **succès** à débloquer et des **statistiques** de partie. |
| **Brin doré** | 4 tas sur 10 cachent un brin de foin doré qui vaut dix brins. Le bureau annonce sa présence quand le tas est livré. |
| **Recyclage** | Au bureau, recycle toute l'usine pour gagner des **jetons** (+10 % sur les ventes et les primes par jeton, pour toujours). On repart de zéro en gardant succès et statistiques. Le premier jeton s'obtient après 50 000 € gagnés, le n-ième après n² × 50 000 €. |
| **Ambiance** | Écran titre animé, musique d'accompagnement, vent et oiseaux, bruits de pas, averses de pluie avec ciel gris et brouillard, champ de vision qui s'élargit en courant. Musique et météo se coupent dans le Menu. |
| **Carte** | Vue de dessus de l'usine : machines par couleur, sens des tapis, tas, trou de vente et ta position. |
| **Graphismes** | Qualité Basse, Moyenne ou Élevée (ombres, résolution, détail du tas), compteur d'images par seconde et vibrations désactivables. Ces réglages sont propres au téléphone. |

Chaque machine prend ce qui arrive par l'arrière (flèche verte) et rend sa production par
l'avant (flèche bleue). La plupart des machines occupent 2×2 cases, et la zone constructible fait
160×160 cases. Un voyant sur chaque machine indique son état : vert en marche, orange en attente,
rouge si elle est bloquée (sortie pleine ou trop loin du tas).

Le tas est formé comme un vrai déversement : un cône principal et des épaulements, des ravines,
un pied qui s'étale en épandage d'aiguilles sur l'herbe. Sa surface en acier est faite de fines
aiguilles qui scintillent selon l'angle. Il se creuse là où l'on travaille, et quand une paroi
devient trop raide, on voit les aiguilles dévaler la pente.

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
  scripts/world.gd   Décor 3D ; pile.gd = rendu du tas ; buildings.gd = modèles des machines
  scripts/pile_field.gd  Relief du tas : grille de hauteurs, creusage local, éboulements
  scripts/player.gd  Joueur à la première personne, ramassage, mode construction
  scripts/hud.gd     HUD et contrôles tactiles ; panels.gd = boutique, arbre, vente…
  scripts/title.gd   Écran titre ; sfx.gd = sons, musique, ambiance et pluie
  scripts/qa.gd      Tests automatiques (exclus de l'export)
tools/gen_audio.py   Génère la musique et les ambiances (Python pur)
tools/run_tests.sh   Lance tous les tests
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
