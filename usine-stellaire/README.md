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
- 📈 Paliers : tous les 25 exemplaires d'une machine, sa production double
- ⚡ Surcadençage : boost ×2 pendant 30 min (recharge 3 h)
- 🏆 20 succès, chacun +2 % de production permanente
- 💥 Coups critiques au minage (×5), sons et vibrations (désactivables)
- 🎁 Caisse quotidienne : 30 min de production offertes chaque jour
- 🚀 Prestige : lance des fusées pour gagner des fragments stellaires
  (+20 % de production permanente chacun, coût doublé à chaque lancement)
- 💾 Sauvegarde automatique locale + export/import

## Installer sur ton téléphone

**Option 1 — APK Android (recommandé, vraie appli) :**
1. Télécharge [`UsineStellaire.apk`](./UsineStellaire.apk) sur ton téléphone
2. Ouvre le fichier ; autorise « Installer des applis inconnues » pour ton navigateur si demandé
3. C'est tout : icône sur l'écran d'accueil, 100 % hors-ligne, vibrations natives

L'APK est reconstructible depuis les sources : `android/build.sh` (voir le script —
compilation sans Gradle : javac → D8 → aapt → zipalign → apksigner). Le keystore
`android/usine.keystore` est versionné pour que les mises à jour gardent la même
signature (sinon Android exige de désinstaller, ce qui efface la sauvegarde).

**Option 2 — GitHub Pages (PWA installable) :**
1. Sur GitHub : *Settings → Pages → Source : Deploy from a branch*, choisis la
   branche et le dossier racine
2. Ouvre `https://<ton-user>.github.io/<repo>/usine-stellaire/` sur ton téléphone
3. Menu du navigateur → **« Ajouter à l'écran d'accueil »** — le jeu s'installe
   comme une appli et fonctionne ensuite totalement hors-ligne

**Option 3 — fichier local :** ouvre simplement `index.html` dans n'importe quel
navigateur ; la sauvegarde locale fonctionne aussi.

## Technique

- Vanilla JS, zéro dépendance, un seul fichier de ~1 200 lignes
- Simulation par ticks de 250 ms basée sur l'horloge réelle (robuste aux
  ralentissements) ; le hors-ligne réutilise la même simulation par pas de 30 s
- Sauvegarde `localStorage` versionnée et validée au chargement (résiste à la corruption)
- 68 tests automatisés Playwright (économie, chaînes, prestige, sauvegarde,
  hors-ligne, UI) + un bot joueur qui vérifie que la progression jusqu'à la
  fusée n'est jamais bloquée
