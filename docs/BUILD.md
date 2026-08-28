# Builder l'application

L'app Flutter vit dans `app/`. Les commandes ci-dessous sont à lancer depuis ce
dossier.

## Prérequis

| Cible | Nécessaire |
| --- | --- |
| Android (APK / AAB) | Flutter stable, JDK 17+, Android SDK (platform 35 + build-tools) |
| iOS (IPA) | macOS, Xcode, CocoaPods, un compte Apple Developer pour signer |
| Web | Flutter stable uniquement |

```bash
flutter --version   # 3.47.x ou plus récent
flutter doctor       # doit être vert pour la cible visée
flutter pub get
```

## Vérifications avant tout build

```bash
flutter analyze
flutter test
```

## Android

### APK de test (installable directement sur un téléphone)

```bash
flutter build apk --release
# -> build/app/outputs/flutter-apk/app-release.apk
```

Sans `android/key.properties`, ce build est signé avec la clé de debug : il
s'installe sur un appareil (`adb install -r <apk>`) mais le Play Store le
refuse.

### APK découpés par architecture (fichiers plus légers)

```bash
flutter build apk --release --split-per-abi
```

### App Bundle pour le Play Store

Le Play Store n'accepte plus les APK pour les nouvelles applications : il faut
un `.aab`.

```bash
flutter build appbundle --release
# -> build/app/outputs/bundle/release/app-release.aab
```

### Signature de publication

1. Générer une clé d'upload (à conserver précieusement et hors du dépôt — la
   perdre oblige à passer par la procédure de récupération de Google) :

   ```bash
   keytool -genkey -v -keystore ~/ca-part-upload.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```

2. Créer `app/android/key.properties` (déjà couvert par le `.gitignore`) :

   ```properties
   storeFile=/chemin/absolu/vers/ca-part-upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```

3. Relancer `flutter build appbundle --release`. Le Gradle du projet bascule
   automatiquement sur cette clé dès que le fichier existe.

### Numéro de version

`version: 1.0.0+1` dans `app/pubspec.yaml` : `1.0.0` est le `versionName`
(visible par l'utilisateur), `1` le `versionCode`. **Le `versionCode` doit être
incrémenté à chaque dépôt sur le Play Store**, sinon l'envoi est rejeté.

## iOS

```bash
flutter build ios --release              # nécessite une identité de signature
flutter build ipa --release              # produit build/ios/ipa/*.ipa
open ios/Runner.xcworkspace              # pour régler l'équipe et le bundle id
```

Le bundle identifier par défaut est `fr.capart.caPart` — à remplacer par celui
créé sur le portail Apple Developer.

## Web

```bash
flutter build web --release
# -> build/web (déployable tel quel)
```

## Intégration continue

`.github/workflows/android.yml` rejoue `analyze` + `test`, puis produit l'APK et
l'AAB à chaque push, en artefacts téléchargeables depuis l'onglet Actions.

Pour que la CI signe les builds, ajouter ces secrets au dépôt :

| Secret | Contenu |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 ca-part-upload.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `ANDROID_KEY_ALIAS` | `upload` |
| `ANDROID_KEY_PASSWORD` | mot de passe de la clé |

Sans ces secrets le workflow tourne quand même, en signature debug.

`.github/workflows/ios.yml` est déclenché à la main (`workflow_dispatch`) et
vérifie que la cible iOS compile, sans signature.

## Icône et écran de démarrage

Déposer `assets/images/app_icon.png` (1024×1024),
`assets/images/app_icon_foreground.png` et `assets/images/splash.png`, puis :

```bash
dart run flutter_launcher_icons
dart run flutter_native_splash:create
```
