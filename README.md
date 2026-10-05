# 🌾 Trouve le Foin

Un jeu mobile inspiré de *Find the Needle*, mais **à l'envers** : on cherche des brins de **foin**
dans une botte d'**aiguilles** ! Les aiguilles récupérées passent ensuite dans une fonderie
pour être fondues en lingots et revendues. Le but : tout automatiser.

## Télécharger l'APK

Chaque push compile l'APK automatiquement (GitHub Actions). Pour l'installer :

1. Sur le téléphone, ouvrez l'onglet **Releases** du dépôt et prenez la plus récente.
2. Téléchargez `TrouveLeFoin.apk` et ouvrez-le.
3. Autorisez « installer des applis inconnues » si Android le demande.

Android 8.0 minimum. Testé pour tourner fluide sur un Snapdragon 7s Gen 3 (rendu Canvas
accéléré par le GPU, ~300 objets à l'écran).

## Le jeu

| Étape | Contenu |
|---|---|
| 🪡 **Botte** | Touchez ou glissez pour retirer les aiguilles du dessus et dégager les brins de foin enfouis. Attention aux piqûres ! Chaque botte terminée en ouvre une plus grande, avec des métaux plus rares (cuivre, fer, argent, or, titane, tungstène). |
| 🔥 **Fonderie** | Chargez la trémie, chauffez au-dessus du point de fusion (foin ≤ 1150 °C, charbon ≤ 1850 °C, électricité ≤ 3800 °C), puis coulez les lingots. Fours : à foin → haut fourneau → four à arc → four à plasma. Forge d'alliages (acier, électrum, carbure de tungstène). |
| 📈 **Marché** | Cours fluctuants, prix qui baisse quand on vend en masse, « ruées » temporaires, achat de charbon, vente de ferraille. |
| 🛠️ **Atelier** | Aimant à main, gants, lunettes à foin, électro-aimant, chèvre renifleuse, convoyeur, chargeur de combustible, coulée continue, sélecteur intelligent, courtier automatique, contrat de charbon, forge automatique… |
| 📜 **Plus** | Objectifs guidés, statistiques, prestige (« vendre la ferme » pour des étoiles de foin), réglages. |

La partie est sauvegardée automatiquement et les machines continuent de tourner hors-ligne
(jusqu'à 8 h).

## Structure technique

- `app/src/main/assets/www/` : le jeu (HTML/CSS/JS, sans dépendance, jouable aussi dans un navigateur).
- `app/src/main/java/.../MainActivity.java` : app Android native (WebView plein écran, sauvegarde
  dans les SharedPreferences, vibrations, bouton retour).
- `keystore/trouvelefoin.jks` : clé de signature du projet (volontairement publique, mot de passe
  `foinfoin`) pour que chaque nouvel APK s'installe par-dessus l'ancien sans perdre la partie.

Compiler en local (SDK Android requis) : `./gradlew assembleRelease`
→ `app/build/outputs/apk/release/app-release.apk`.
