# 🏭 Usine Stellaire : Colonie

Jeu de **construction d'usine en 3D** (vue isométrique), en français, inspiré de
Factorio et Satisfactory. **100 % hors-ligne, 100 % gratuit, sans pub, sans compte.**

## Le jeu

- 🎬 **Cinématique d'intro** + création de ton **robot-colon** (4 têtes, 6 couleurs,
  modifiable ensuite dans le Menu)
- 🗺️ **Grande carte 3D procédurale** (~8× la version d'origine, 160×160) avec du
  **relief** (collines, vallées, rempart montagneux) et **4 biomes** : prairie,
  savane, neige, badlands — chacun avec ses arbres et ses teintes
- 🪨 Décor riche : rochers, forêts par biome, **troncs couchés au sol**, texture de
  sol, et des **gisements texturés façon Satisfactory** (fer gris métal, cuivre
  brun avec oxydation verte et soufre verdâtre, charbon, pierre)
- 🤖 Déplace ton robot en touchant le sol (pathfinding A*), **caméra corrigée** :
  glisser = le sol reste sous le doigt (même en diagonale), pincer pour zoomer
- ⛏️ Mine à la main près des gisements, puis **pose de vraies machines sur la
  carte** : foreuses (sur gisements), fours, ateliers, assembleurs, labos…
- ➡️ **Convoyeurs** : les objets circulent physiquement dessus ; les machines
  adjacentes s'alimentent aussi directement. Coffres de stockage.
- 🗺️ **Minimap** (coin bas-gauche) : biomes, gisements, tes bâtiments et ta
  position ; touche-la pour recadrer la caméra — impossible de se perdre
- 🎒 **Gestion du sac anti-blocage** : jette la moitié / tout un objet, ou
  **déverse tout ton sac dans un coffre** d'un seul geste (sac plein signalé)
- ⚠️ **Alertes machines** : un panneau ⚠️ flotte au-dessus des machines
  bloquées (sans intrant ou sortie pleine) — tu vois d'un coup d'œil où ça coince
- ❓ **Guide** intégré (bouton en haut) + 16 objectifs façon tutoriel
- 🧪 18 recherches · 🏆 11 succès (+2 % de vitesse chacun)
- ⏳ L'usine tourne quand le jeu est fermé (2 h → 24 h par recherche)
- 🚀 **Prestige** : construis le silo, lance la fusée, décolle vers une **nouvelle
  planète** (biomes inédits) avec des fragments ✨ permanents (+20 % chacun)
- 📱↔️ **Mode paysage** (téléphone tourné) + portrait ; sons & vibrations
- 💾 Sauvegarde auto locale + export/import

Le mode « idle » d'origine reste disponible : [`classique.html`](./classique.html).

## Installer sur ton téléphone

**Option 1 — APK Android (recommandé) :**
1. Télécharge [`UsineStellaire.apk`](./UsineStellaire.apk) sur ton téléphone
2. Ouvre le fichier ; autorise « Installer des applis inconnues » si demandé
3. Icône sur l'écran d'accueil, 100 % hors-ligne, vibrations natives

**Option 2 — GitHub Pages (PWA)** : Settings → Pages → Deploy from a branch,
puis ouvre `usine-stellaire/` et « Ajouter à l'écran d'accueil ».

**Option 3 — local** : sers le dossier via un petit serveur
(`python3 -m http.server`) — three.js est un fichier séparé.

## Technique

- three.js (bundlé hors-ligne) + vanilla JS, aucun autre framework
- Rendu : caméra ortho isométrique, relief par déplacement de vertices, meshes
  instanciés (sol, arbres/biome, rochers, troncs, cristaux), **textures de
  minerai générées au canvas** (fer/cuivre-oxydé/charbon/pierre) + texture de sol
- Sélection & déplacement par **raycast sur la géométrie réelle** (précis sur le
  relief) ; caméra pan par raycast (le point saisi reste sous le doigt)
- Carte 160×160 déterministe par graine (biomes voronoï bruités, hauteur value-noise
  3 octaves, zone de départ aplatie) ; A* sur tas binaire
- Simulation à pas fixe (100 ms) : recettes, tampons, déversement par adjacence,
  files d'objets sur convoyeurs ; hors-ligne rejoué à pas de 250-500 ms
- Sauvegarde `localStorage` versionnée et validée (résiste à la corruption)
- **104 tests Playwright** répartis en 5 suites : monde/biomes/relief/A*/
  placement/convoyeurs/recettes/silo-prestige/sauvegarde/hors-ligne (51),
  biomes/relief/robot/intro/guide/HUD (21), tactile E2E dont glisser diagonal
  et sélection sur relief (9), bug-hunt/fuzz d'interface (10), et anti-blocage :
  vider le sac, déverser dans un coffre, minimap, alertes machines (13)
- APK : WebView + pont de vibration natif, compilée sans Gradle
  (javac → D8 → aapt → zipalign → apksigner), signée v1+v2+v3, orientation libre,
  minSdk 23 / targetSdk 28
