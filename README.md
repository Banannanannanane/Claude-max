# Mammouth pour Android (APK non officiel)

Client Android qui reprend l'expérience de l'app web **Mammouth AI** dans une APK, en
s'appuyant sur son **API compatible OpenAI** (`https://api.mammouth.ai/v1`).

Deux modes dans la même application :

| Mode | Ce que ça fait |
| --- | --- |
| **Chat natif (API)** | Discussion native (Jetpack Compose) branchée sur votre clé API Mammouth : réponses en streaming, choix du modèle, historique local. |
| **App web** | Ouvre `mammouth.ai` dans une WebView intégrée (connexion par compte, sans clé API) — pratique pour les fonctions non exposées par l'API. |

## Fonctionnalités

- Réponses **en streaming** (SSE), avec bouton « Stop » qui coupe réellement la requête réseau.
- **Sélecteur de modèle** alimenté par `GET /v1/models` (la liste suit votre abonnement : GPT, Claude, Gemini, Grok, DeepSeek…), avec repli hors ligne.
- **Historique de conversations** persistant en local (JSON dans le stockage privé de l'app).
- **Clé API chiffrée** par l'AndroidKeyStore (AES-256/GCM) ; elle n'est jamais stockée en clair et ne transite que vers l'API Mammouth.
- **Instruction système**, **température** et **URL de base** configurables.
- Rendu Markdown léger : blocs de code, titres, gras/italique, code en ligne, copie d'un message en un tap.
- Thème Material 3 clair/sombre + couleurs dynamiques (Android 12+).

## Récupérer l'APK

L'APK est compilée par GitHub Actions (workflow `.github/workflows/android-build.yml`).

1. Onglet **Actions** du dépôt → dernier run **Build APK** ;
2. Section **Artifacts** → télécharger `mammouth-apk` ;
3. Installer `mammouth-debug.apk` sur le téléphone (autoriser « sources inconnues »).

`mammouth-release.apk` est **non signé** : utilisez la version debug pour une installation
directe, ou signez la release (voir plus bas).

Pour publier une release GitHub avec l'APK attachée :

```bash
git tag v1.0.0 && git push origin v1.0.0
```

## Compiler en local

Prérequis : JDK 17 et le SDK Android (API 35). Aucun `local.properties` n'est nécessaire si
`ANDROID_HOME` est défini.

```bash
./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Signer une release

Créez un keystore puis exportez les variables avant de compiler :

```bash
keytool -genkey -v -keystore mammouth.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mammouth

export RELEASE_KEYSTORE_PATH=/chemin/mammouth.jks
export RELEASE_KEYSTORE_PASSWORD=...
export RELEASE_KEY_ALIAS=mammouth
export RELEASE_KEY_PASSWORD=...

./gradlew assembleRelease
```

Sans ces variables, la release est simplement produite non signée (le build ne casse pas).

## Premier lancement

1. Créez une clé API depuis votre compte Mammouth (section API) ;
2. Dans l'app : menu ⚙️ → **Réglages** → collez la clé → **Tester et charger les modèles** ;
3. Revenez au chat, choisissez un modèle dans la barre du haut, et discutez.

Le modèle `mammouth-recommended` laisse Mammouth choisir le meilleur modèle du moment.

## Architecture

```
app/src/main/java/com/mammouthclient/app/
├── MainActivity.kt              # hôte Compose + navigation (chat / réglages / web)
├── data/
│   ├── AppContainer.kt          # dépôt de réglages partagé
│   ├── ChatModels.kt            # Conversation / Message (kotlinx.serialization)
│   ├── ChatStore.kt             # persistance JSON des conversations
│   ├── Crypto.kt                # chiffrement AndroidKeyStore de la clé API
│   └── SettingsRepository.kt    # préférences exposées en StateFlow
├── net/
│   ├── Dto.kt                   # DTO du format OpenAI
│   └── MammouthApi.kt           # /chat/completions (SSE) + /models
└── ui/
    ├── ChatScreen.kt            # écran de discussion
    ├── ChatViewModel.kt         # état, streaming, historique
    ├── MarkdownText.kt          # rendu Markdown minimal
    ├── SettingsScreen.kt        # réglages
    ├── WebAppScreen.kt          # WebView de l'app web
    └── theme/Theme.kt
```

- `minSdk 24` (Android 7.0) · `targetSdk 35` · Kotlin 2.0 · Compose BOM 2024.12 · OkHttp 4.
- Aucune dépendance analytics ni backend tiers : le trafic va uniquement vers `api.mammouth.ai`.

## Avertissement

Projet **non officiel**, sans lien avec l'éditeur de Mammouth AI. L'usage de l'API reste
soumis aux conditions d'utilisation et au quota de votre abonnement Mammouth.
