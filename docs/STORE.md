# Publier Trouve le Foin sur les stores

## Google Play (Android)

1. **Créer ta clé de publication** (une seule fois, et à garder précieusement) :
   ```
   keytool -genkeypair -v -keystore publication.jks -alias trouvelefoin -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **L'ajouter aux secrets du dépôt GitHub** (Settings → Secrets and variables → Actions) :
   - `ANDROID_KEYSTORE_BASE64` : le résultat de `base64 -w0 publication.jks`
   - `ANDROID_KEY_ALIAS` : `trouvelefoin`
   - `ANDROID_KEYSTORE_PASSWORD` : le mot de passe choisi

   Sans ces secrets, la compilation signe avec `keystore/trouvelefoin.jks`. Cette clé de test
   est publique : elle permet d'installer et de mettre à jour l'APK sur ton téléphone, mais
   **ne l'utilise pas pour le Play Store**.
3. **Créer un compte Google Play Console** (frais d'inscription uniques de 25 $), puis
   « Créer une application ».
4. **Envoyer le fichier `TrouveLeFoin.aab`** de la dernière release, d'abord dans
   « Tests internes ».
5. **Remplir la fiche** : texte ci-dessous, captures d'écran, classification du contenu
   (jeu sans violence, tout public) et lien vers la politique de confidentialité
   (`docs/PRIVACY.md`, publiable par exemple avec GitHub Pages).
6. Avant chaque nouvelle version, augmente `version/code` (et `version/name`) dans
   `game/export_presets.cfg`.

### Texte de la fiche

**Titre** : Trouve le Foin

**Description courte** : Trouve les 22 brins de foin cachés dans une montagne d'aiguilles !

**Description** :
> Tout le monde cherche l'aiguille dans la botte de foin… Ici, c'est l'inverse !
> Fouille d'immenses tas d'aiguilles en 3D pour retrouver les 22 brins de foin cachés dans chacun.
> Ramasse à la main, vérifie, fonds les aiguilles en lingots, purifie le métal et vends ta production.
> Achète les droits de construction, des bras robots, des pelleteuses, des fonderies, des
> purificateurs et des camions de vente, puis regarde ta ferme tourner toute seule.
> Du petit tas à la montagne de 120 000 aiguilles, deviendras-tu le magnat de l'aiguille ?

## App Store (iOS)

Il faut un **Mac avec Xcode** et un **compte Apple Developer** (99 $ par an).

1. Dans `game/export_presets.cfg` (preset « iOS »), remplace `application/app_store_team_id`
   par ton Team ID Apple.
2. Lance le workflow **« iOS (projet Xcode) »** depuis l'onglet Actions, puis télécharge
   l'artefact `TrouveLeFoin-ios-xcode.zip`. Tu peux aussi exporter depuis l'éditeur Godot sur
   le Mac (Projet → Exporter → iOS).
3. Ouvre le projet dans Xcode, choisis ton équipe de signature, puis Product → Archive
   → Distribute App → App Store Connect.
4. Termine la fiche dans App Store Connect avec le même texte, des captures et la politique de
   confidentialité.
