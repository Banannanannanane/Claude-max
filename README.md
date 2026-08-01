# Mammouth Chat — client Android (non officiel)

Application Android qui parle à l'**API Mammouth** (`https://api.mammouth.ai/v1`),
compatible OpenAI. Projet non officiel, sans lien avec Mammouth AI.

## Fonctionnalités

- Chat avec **réponses en streaming** (SSE) et bouton *Arrêter* qui conserve le texte déjà reçu
- **Sélecteur de modèle** alimenté par le catalogue de l'API (`/v1/models`, sinon
  `https://api.mammouth.ai/public/models`), avec recherche et repli hors ligne
- **Plusieurs discussions** persistées sur l'appareil (renommage, suppression, régénération)
- **Instructions système**, température réglable, streaming activable
- Clé API **chiffrée par le Keystore Android** (AES-GCM), jamais écrite en clair
- Rendu Markdown des réponses : blocs de code copiables, titres, listes, gras/italique
- Interface Jetpack Compose Material 3, thème clair/sombre et couleurs dynamiques (Android 12+)

## Récupérer l'APK

Chaque build de la branche publie l'APK :

- **Release [`dev-apk`](https://github.com/Banannanannanane/Claude-max/releases/tag/dev-apk)**
  → téléchargement direct du `.apk`, le plus simple depuis un téléphone
- **Artefact `mammouth-chat-apk`** → onglet *Actions*, dans le run « Build APK »

L'APK est un build **debug**, signé avec la clé de debug : installable directement,
mais il faut autoriser l'installation depuis une source inconnue.

## Premier lancement

1. Ouvrir les **Paramètres** (icône en haut à droite)
2. Coller la clé API générée depuis les paramètres API de votre compte Mammouth
3. *Tester la connexion* pour vérifier la clé et le modèle
4. Choisir un modèle, puis revenir au chat

Les conversations restent dans le stockage privé de l'application ; rien n'est envoyé
ailleurs qu'à l'API Mammouth.

## Compiler soi-même

Prérequis : JDK 17 et le SDK Android (API 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # tests unitaires
```

Pour une version signée, déclarez votre propre `signingConfig` dans
`app/build.gradle.kts`, puis `./gradlew assembleRelease`.

## Structure

| Chemin | Rôle |
| --- | --- |
| `app/src/main/java/.../net/MammouthApi.kt` | Appels HTTP, streaming SSE, erreurs API |
| `app/src/main/java/.../net/ModelCatalog.kt` | Lecture tolérante du catalogue de modèles |
| `app/src/main/java/.../data/` | Réglages, stockage chiffré, historique des discussions |
| `app/src/main/java/.../ui/` | Écrans Compose et `ChatViewModel` |
| `.github/workflows/android.yml` | Build de l'APK et publication |

## Configuration

| Réglage | Défaut |
| --- | --- |
| URL de base | `https://api.mammouth.ai/v1` |
| Modèle | `gpt-4.1` (modifiable dans le sélecteur) |
| Température | `0.7` |
| minSdk / targetSdk | 24 / 35 |
