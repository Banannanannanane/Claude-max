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

### Deux applications

Le projet produit deux applications à partir du même code :

| Variante | `--flavor` | Identifiant | Nom sous l'icône |
| --- | --- | --- | --- |
| Production | `prod` | `fr.capart.ca_part` | Ça Part |
| Test | `dev` | `fr.capart.ca_part.test` | Ça Part test |

Les identifiants diffèrent, donc les deux s'installent côte à côte : on garde
celle qui tourne pendant qu'on essaie l'autre. La variante test affiche en plus
une pastille « test » à côté du logo.

Le nom de flavor est `dev` et non `test` parce que Gradle réserve « test » aux
source sets de test ; seul le nom affiché dit « Ça Part test ».

### APK installable directement sur un téléphone

```bash
flutter build apk --release --flavor prod --dart-define=FLAVOR=prod
# -> build/app/outputs/flutter-apk/app-prod-release.apk

flutter build apk --release --flavor dev --dart-define=FLAVOR=dev
# -> build/app/outputs/flutter-apk/app-dev-release.apk
```

`--flavor` choisit l'identifiant et le nom Android ; `--dart-define` dit à
l'app elle-même quelle variante elle est. **Les deux vont ensemble** : sans le
`--dart-define`, l'APK test s'appellerait « Ça Part » à l'intérieur.

Sans `android/key.properties`, ces builds sont signés avec la clé de debug :
ils s'installent sur un appareil (`adb install -r <apk>`) mais le Play Store
les refuse.

### APK découpés par architecture (fichiers plus légers)

```bash
flutter build apk --release --flavor prod --dart-define=FLAVOR=prod --split-per-abi
```

### App Bundle pour le Play Store

Le Play Store n'accepte plus les APK pour les nouvelles applications : il faut
un `.aab`.

```bash
flutter build appbundle --release --flavor prod --dart-define=FLAVOR=prod
# -> build/app/outputs/bundle/prodRelease/app-prod-release.aab
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
flutter build ios --release --dart-define=FLAVOR=prod   # identité de signature requise
flutter build ipa --release --dart-define=FLAVOR=prod   # produit build/ios/ipa/*.ipa
open ios/Runner.xcworkspace                             # équipe et bundle id
```

Les variantes prod/test ne sont déclarées que côté Android. Les dédoubler sur
iOS demande deux schémas Xcode et deux bundle identifiers, à faire le jour où
l'app y va.

Le bundle identifier par défaut est `fr.capart.caPart` — à remplacer par celui
créé sur le portail Apple Developer.

## Web

```bash
flutter build web --release
# -> build/web (déployable tel quel)
```

## Intégration continue

`.github/workflows/android.yml` rejoue `analyze` + `test`, puis produit à
chaque push trois artefacts téléchargeables depuis l'onglet Actions :
`ca-part-apk`, `ca-part-test-apk` et `ca-part-aab`.

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
