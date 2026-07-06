# Mini Jeux 🎮

Une collection de 6 mini-jeux mobiles dans le style des pubs TikTok, mais **sans
tout ce qui énerve** :

- ❌ Pas de pub
- ❌ Pas de micro-paiements
- ❌ Pas de compte, pas de connexion internet
- ✅ Juste des petits jeux pour s'amuser quand on s'ennuie

## Les jeux

| Jeu | Principe |
|---|---|
| 🧪 **Tri d'eau** | Verse les liquides pour trier les couleurs (niveaux infinis, annulation illimitée) |
| 🏗️ **Stack** | Tape au bon moment pour empiler les blocs le plus haut possible |
| 🐤 **Petit Oiseau** | Tape pour voler, évite les tuyaux |
| 🔪 **Couteaux** | Plante tous les couteaux dans la cible qui tourne |
| 🔢 **2048** | Glisse pour fusionner les tuiles |
| 🐍 **Serpent** | Mange les pommes, les murs se traversent |

Les records sont sauvegardés en local sur le téléphone. Sons générés en direct
(activables/désactivables depuis le menu), petites vibrations, tout en français.

## Installer l'APK

L'APK prêt à installer est dans [`dist/MiniJeux.apk`](dist/MiniJeux.apk) (~64 Ko).

1. Copie `dist/MiniJeux.apk` sur ton téléphone Android (5.0 minimum)
2. Ouvre le fichier et accepte l'installation depuis une source inconnue
3. C'est tout — l'app s'appelle « Mini Jeux »

## Structure

```
app/
├── AndroidManifest.xml          # manifeste Android (minSdk 21, targetSdk 34)
├── src/.../MainActivity.java    # une WebView plein écran, rien d'autre
├── res/mipmap-*/ic_launcher.png # icônes (générées par tools/make_icon.py)
└── assets/www/                  # les jeux (HTML/CSS/JS, zéro dépendance)
    ├── index.html
    ├── style.css
    ├── app.js                   # hub, sons WebAudio, scores, canvas, swipe
    └── games/*.js               # un fichier par jeu
```

Les jeux sont du web pur : tu peux aussi ouvrir `app/assets/www/index.html`
dans un navigateur pour y jouer ou les modifier.

## Recompiler l'APK

Le build n'utilise **pas** le SDK Android officiel, seulement des outils
disponibles dans les dépôts Ubuntu/Debian :

```bash
sudo apt install openjdk-21-jdk-headless aapt zipalign apksigner dalvik-exchange
pip install pillow
./build.sh    # télécharge android.jar depuis Maven Central si absent, produit dist/MiniJeux.apk
```

La clé de signature (`release.keystore`, mot de passe `minijeux`) est incluse
pour que les recompilations puissent mettre à jour l'app déjà installée. Ne
l'utilise pas pour publier sur un store — génère ta propre clé dans ce cas.
