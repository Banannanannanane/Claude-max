# 🏭 Usine Stellaire

Jeu d'automatisation / idle en français, inspiré de Factorio et Satisfactory.
**100 % hors-ligne, 100 % gratuit, aucune inscription, aucune publicité.**

## Le jeu

- ⛏️ Mine à la main tes premières ressources (fer, cuivre, charbon, pierre)
- ⚙️ Construis des foreuses, fours et assembleurs — chaque machine consomme les
  ressources du tier inférieur pour produire le tier supérieur
- ⚖️ Équilibre tes chaînes de production (les barres d'efficacité montrent les
  machines en manque d'intrants) — tu peux vendre les machines excédentaires
- 🧪 Produis de la science et débloque 21 recherches
- 📋 10 objectifs guident ta progression
- ⏳ L'usine continue de produire quand le jeu est fermé (2 h, extensible à 24 h)
- 🚀 Prestige : lance des fusées pour gagner des fragments stellaires
  (+20 % de production permanente chacun, coût doublé à chaque lancement)
- 💾 Sauvegarde automatique locale + export/import

## Installer sur ton téléphone

Le jeu est une PWA : un seul fichier `index.html` + manifest + service worker.

**Option 1 — GitHub Pages (recommandé, vraie appli installable) :**
1. Sur GitHub : *Settings → Pages → Source : Deploy from a branch*, choisis la
   branche et le dossier racine
2. Ouvre `https://<ton-user>.github.io/<repo>/usine-stellaire/` sur ton téléphone
3. Menu du navigateur → **« Ajouter à l'écran d'accueil »** — le jeu s'installe
   comme une appli et fonctionne ensuite totalement hors-ligne

**Option 2 — fichier local :** ouvre simplement `index.html` dans n'importe quel
navigateur ; la sauvegarde locale fonctionne aussi.

## Technique

- Vanilla JS, zéro dépendance, un seul fichier de ~1 200 lignes
- Simulation par ticks de 250 ms basée sur l'horloge réelle (robuste aux
  ralentissements) ; le hors-ligne réutilise la même simulation par pas de 30 s
- Sauvegarde `localStorage` versionnée et validée au chargement (résiste à la corruption)
- 58 tests automatisés Playwright (économie, chaînes, prestige, sauvegarde,
  hors-ligne, UI) + un bot joueur qui vérifie que la progression jusqu'à la
  fusée n'est jamais bloquée
