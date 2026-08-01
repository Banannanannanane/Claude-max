# Mammouth pour Android (APK non officiel)

Client Android qui reprend l'expérience de l'app web **Mammouth AI** dans une APK, en
s'appuyant sur son **API compatible OpenAI** (`https://api.mammouth.ai/v1`).

Deux modes complémentaires dans la même application :

| Mode | Ce que ça fait |
| --- | --- |
| **Chat natif (API)** | Interface Jetpack Compose branchée sur votre clé API : streaming, vision, projets, images, recherche web, historique local. |
| **App web** | `mammouth.ai` dans une WebView intégrée (connexion par compte, sans clé API) — couvre tout ce que l'API n'expose pas. |

## Fonctionnalités

### Discussion
- Réponses **en streaming** (SSE) ; le bouton « Stop » coupe réellement la requête réseau.
- **Sélecteur de modèle** alimenté par `GET /v1/models` (GPT, Claude, Gemini, Grok, DeepSeek, Mistral, Kimi…), avec repli hors ligne.
- **Recherche web** : une puce bascule la discussion vers un modèle sourcé (Perplexity/`sonar-pro` par défaut, configurable).
- Actions sur chaque message : **copier, partager, régénérer, modifier & renvoyer, supprimer, lecture à voix haute** (TTS).
- **Dictée vocale** pour composer un message.
- Rendu Markdown : blocs de code avec bouton copier, titres, listes, citations, **liens cliquables**, **images affichées**.
- Compteur de **jetons consommés** sous chaque réponse.

### Pièces jointes
- **Images** de la galerie → envoyées en vision aux modèles multimodaux.
- **PDF** → chaque page est rendue en image et jointe (analyse de documents sans OCR externe).
- **Fichiers texte / code / CSV / JSON** → contenu injecté dans le prompt.

### Projets (assistants)
- Instructions permanentes, modèle dédié et **documents de référence** rattachés à un projet.
- Une discussion peut être lancée directement « avec ce projet ».

### Atelier d'images
- Génération via `POST /v1/images/generations`, avec **repli automatique** sur `chat/completions` si l'endpoint n'est pas exposé.
- Choix du modèle, du format et du nombre d'images ; **enregistrement en galerie** et partage.

### Bibliothèque de prompts
- 8 prompts fournis (résumé, correction, traduction, code, tests, email, brainstorming, vision).
- Prompts personnels : création, édition, catégories, insertion en un tap.

### Historique et confort
- **Recherche** dans les titres et le contenu, **épinglage**, **renommage**, suppression.
- **Export / partage** d'une discussion en Markdown.
- **Partager vers Mammouth** depuis n'importe quelle app Android (`SEND`) et traitement d'une sélection de texte (`PROCESS_TEXT`).
- Thème clair / sombre / système, couleurs dynamiques (Android 12+), **taille du texte réglable**.
- Réglages avancés : instruction système globale, température, top-p, jetons max, streaming, URL de base.

### Confidentialité
- **Clé API chiffrée** par l'AndroidKeyStore (AES-256/GCM) ; jamais stockée en clair.
- Aucun analytics, aucun backend tiers : le trafic va uniquement vers `api.mammouth.ai`.
- Conversations, projets et prompts stockés dans le **stockage privé de l'app** (JSON).

## Récupérer l'APK

L'APK est compilée par GitHub Actions (`.github/workflows/android-build.yml`).

1. Onglet **Actions** du dépôt → dernier run **Build APK** ;
2. Section **Artifacts** → télécharger `mammouth-apk` ;
3. Dézipper et installer `mammouth-debug.apk` (autoriser « sources inconnues »).

`mammouth-release.apk` est minifié mais **non signé** : pour une installation directe,
utilisez la version debug, ou signez la release (voir plus bas).

Publier une release GitHub avec l'APK attachée :

```bash
git tag v1.0.0 && git push origin v1.0.0
```

## Compiler en local

Prérequis : JDK 17 et le SDK Android (API 35).

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Signer une release

```bash
keytool -genkey -v -keystore mammouth.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mammouth

export RELEASE_KEYSTORE_PATH=/chemin/mammouth.jks
export RELEASE_KEYSTORE_PASSWORD=...
export RELEASE_KEY_ALIAS=mammouth
export RELEASE_KEY_PASSWORD=...

./gradlew assembleRelease
```

Sans ces variables, la release est produite non signée (le build ne casse pas).

## Premier lancement

1. Créez une clé API depuis votre compte Mammouth (section API) ;
2. Dans l'app : ⚙️ **Réglages** → collez la clé → **Tester et charger les modèles** ;
3. Revenez au chat, choisissez un modèle et discutez.

`mammouth-recommended` laisse Mammouth choisir le meilleur modèle du moment.

## Architecture

```
app/src/main/java/com/mammouthclient/app/
├── MainActivity.kt              # hôte Compose, navigation, partage entrant
├── data/
│   ├── AppContainer.kt          # instances partagées (réglages, client API)
│   ├── ChatModels.kt            # Conversation / Message / Attachment / Assistant / Prompt
│   ├── ChatStore.kt             # persistance JSON des conversations
│   ├── LibraryStore.kt          # persistance des projets et prompts
│   ├── Crypto.kt                # chiffrement AndroidKeyStore de la clé API
│   └── SettingsRepository.kt    # préférences exposées en StateFlow
├── net/
│   ├── Dto.kt                   # DTO OpenAI (dont contenu multimodal)
│   └── MammouthApi.kt           # chat SSE, vision, images, modèles
├── util/FileUtils.kt            # import images/PDF/texte, galerie, base64
└── ui/
    ├── ChatScreen.kt / ChatViewModel.kt
    ├── ImageScreen.kt / ImageViewModel.kt
    ├── AssistantsScreen.kt      # projets
    ├── PromptsScreen.kt         # bibliothèque de prompts
    ├── SettingsScreen.kt
    ├── WebAppScreen.kt          # WebView de l'app web
    ├── MarkdownText.kt          # rendu Markdown + images
    └── theme/Theme.kt
```

- `minSdk 24` (Android 7.0) · `targetSdk 35` · Kotlin 2.0 · Compose BOM 2024.12 · OkHttp 4.
- Aucune dépendance de rendu d'image tierce : décodage et cache faits maison.

## Limites connues

- La disponibilité de la **génération d'images par l'API** dépend de votre offre Mammouth ;
  si l'endpoint n'est pas exposé, l'app bascule sur le chat et signale l'absence d'image.
- Les modèles acceptant la **vision** varient : joindre une image à un modèle texte renvoie une erreur de l'API.
- La **dictée vocale** et la **lecture à voix haute** dépendent des moteurs installés sur l'appareil.

## Avertissement

Projet **non officiel**, sans lien avec l'éditeur de Mammouth AI. L'usage de l'API reste
soumis aux conditions d'utilisation et au quota de votre abonnement.
