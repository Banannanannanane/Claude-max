# Ça part !

Application mobile de jeux de soirée, reprise de la web app
[ca-part.fr](https://www.ca-part.fr/concepts) : même direction artistique, mêmes
jeux, dans un binaire natif publiable sur le Play Store et l'App Store.

## État

| Brique | État |
| --- | --- |
| Application Flutter (Android, iOS, Web) | en place |
| Design system piloté par tokens | en place, valeurs à remplacer par la DA du site |
| Moteurs de jeu (paquet de cartes, manche chronométrée, écran sur mesure) | en place |
| Catalogue de jeux piloté par JSON | en place, contenu d'exemple |
| Build APK / AAB / IPA + CI GitHub Actions | en place |
| **Contenu réel de ca-part.fr (DA, textes, jeux, assets)** | **en attente des sources** |

Le domaine `ca-part.fr` est bloqué par la politique de sortie réseau de
l'environnement de développement : le contenu n'a pas pu être récupéré
automatiquement. La marche à suivre pour l'importer est décrite dans
[`docs/PORTAGE.md`](docs/PORTAGE.md) — c'est de la donnée, pas du code : un
fichier de tokens et un fichier JSON.

## Structure

```
app/                          application Flutter
  lib/
    design/                   tokens + composants (toute la DA vit ici)
    models/                   définition d'un jeu, état d'une partie
    data/                     chargement du catalogue
    routing/                  routes, calquées sur les URLs du site
    features/                 écrans : accueil, fiche, joueurs, partie, réglages
  assets/games/index.json     le catalogue de jeux
  test/                       tests unitaires et de widgets
docs/
  BUILD.md                    produire l'APK, l'AAB, l'IPA
  STORE.md                    checklist Play Store et App Store
  PORTAGE.md                  importer la DA et les jeux de la web app
.github/workflows/            CI Android (APK + AAB) et iOS
```

## Démarrage rapide

```bash
cd app
flutter pub get
flutter run          # sur un appareil ou un émulateur
flutter build apk --release
```

Détails dans [`docs/BUILD.md`](docs/BUILD.md).

## Choix techniques

- **Flutter** plutôt qu'une WebView : Apple rejette les sites emballés
  (guideline 4.2), et l'app fonctionne hors ligne.
- **Aucun réseau, aucun compte** : tout est embarqué, ce qui simplifie les
  déclarations de confidentialité des deux stores.
- **Contenu séparé du code** : les jeux sont des données JSON, la DA un fichier
  de tokens. Faire évoluer le contenu ne demande pas de toucher aux écrans.
