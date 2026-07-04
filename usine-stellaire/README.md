# 🏭 Usine Stellaire : Colonie

Jeu de **construction d'usine en 3D** (vue isométrique), en français, inspiré de
Factorio et Satisfactory. **100 % hors-ligne, 100 % gratuit, sans pub, sans compte.**

## Le jeu

- 🗺️ Une **carte 3D procédurale** (nouvelle à chaque partie) : gisements de fer,
  cuivre, charbon et pierre, forêts, rochers, montagnes
- 🤖 Un **petit robot** que tu déplaces en touchant le sol (pathfinding), caméra
  libre (glisser pour bouger, pincer pour zoomer)
- ⛏️ Mine à la main près des gisements, puis **pose de vraies machines sur la
  carte** : foreuses (sur les gisements), fours, ateliers, assembleurs, labos…
- ➡️ **Convoyeurs** : les objets circulent physiquement dessus, d'une machine à
  l'autre ; les machines adjacentes s'alimentent aussi directement
- 📦 Coffres de stockage, panneau par machine (déposer/récupérer les objets)
- 🧪 18 recherches (vitesses, capacités, déblocages)
- 📋 16 objectifs façon tutoriel qui t'apprennent le jeu pas à pas
- 🏆 11 succès (+2 % de vitesse permanente chacun)
- ⏳ L'usine tourne quand le jeu est fermé (2 h → 24 h par recherche)
- 🚀 **Prestige** : construis le silo, alimente la fusée, décolle vers une
  **nouvelle planète** (carte inédite) avec des fragments ✨ permanents (+20 %
  chacun, coût de fusée ×2 à chaque lancement)
- 💾 Sauvegarde auto locale + export/import ; sons et vibrations désactivables

Le mode « idle » précédent reste disponible : [`classique.html`](./classique.html).

## Installer sur ton téléphone

**Option 1 — APK Android (recommandé) :**
1. Télécharge [`UsineStellaire.apk`](./UsineStellaire.apk) sur ton téléphone
2. Ouvre le fichier ; autorise « Installer des applis inconnues » si demandé
3. Icône sur l'écran d'accueil, 100 % hors-ligne, vibrations natives

**Option 2 — GitHub Pages (PWA)** : Settings → Pages → Deploy from a branch,
puis ouvre `usine-stellaire/` et « Ajouter à l'écran d'accueil ».

**Option 3 — local** : ouvre `index.html` via un petit serveur web
(`python3 -m http.server`) — le module three.js est un fichier séparé.

## Technique

- three.js (bundlé hors-ligne, 653 Ko) + vanilla JS, aucun autre framework
- Rendu : caméra orthographique isométrique, low-poly, meshes instanciés
  (sol, arbres, rochers, cristaux, objets sur convoyeurs)
- Simulation à pas fixe (100 ms) : recettes, tampons d'entrée/sortie,
  déversement par adjacence, files d'objets sur convoyeurs ; le hors-ligne
  rejoue la même simulation à pas de 250-500 ms
- Carte 56×56 déterministe par graine (mulberry32), A* pour le robot
- Sauvegarde `localStorage` versionnée et validée (résiste à la corruption)
- **51 tests Playwright** (monde, A*, placement, convoyeurs, recettes, silo,
  sauvegarde, hors-ligne, quêtes, stress 1 h) + **8 tests tactiles E2E**
  (tap-déplacement, minage, glisser-caméra, panneaux)
- APK : WebView + pont de vibration natif, compilée sans Gradle
  (javac → D8 → aapt → zipalign → apksigner), signée v1+v2+v3,
  minSdk 23 / targetSdk 28, ~250 Ko
