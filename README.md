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
| **Tas d'aiguilles** | 5 tailles : petit (400 aiguilles), moyen (1 500), gros (6 000), énorme (25 000) et montagne (120 000). Chacun cache 22 brins de foin. Les trouver tous débloque le tas suivant au bureau des commandes. |
| **À la main** | Vise le tas et garde ACTION appuyé pour ramasser une poignée. Une partie du foin se repère tout de suite, le reste reste caché dans tes aiguilles. L'endurance limite la course et le ramassage. |
| **Vérification** | Les aiguilles non vérifiées sont **invendables** (il pourrait y avoir du foin dedans). Pour les vérifier : la table de tri au début, puis des vérificateurs automatiques. |
| **Métallurgie** | Fonderie : 10 aiguilles donnent 1 lingot brut. Purificateur : lingot brut → lingot pur, plus cher. |
| **Automatisation** | Bras robots et pelleteuses, à placer près du tas. Camion de vente automatique. Entrepôts supplémentaires. |
| **Arbre de progression** | Il faut **acheter le droit** de construire chaque machine, et celui de commander les tas plus gros. On y trouve aussi des bonus : tri express, contrats premium, automatisation avancée, magnat de l'aiguille. |
| **Boutique** | Capacité de la main, grosses poignées, endurance, récupération, vitesse, longs bras (ramasser et placer plus loin), œil de lynx, étagères d'entrepôt, tri rapide, plus les moteurs des machines. |

Commandes tactiles : joystick flottant à gauche, glisser à droite pour regarder, boutons
ACTION, Courir et Saut. Au clavier (pour tester sur PC) : ZQSD/WASD, souris, E, Maj, Espace.
La partie est sauvegardée automatiquement, et les machines continuent de tourner jusqu'à 8 h
quand le jeu est fermé.

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

Tests en local : `godot --headless --path game -- --qa=logic`

## Licence des fichiers

Tout le contenu (code, modèles 3D procéduraux, sons synthétisés) est original et a été créé
pour ce projet. Le moteur Godot est sous licence MIT.
