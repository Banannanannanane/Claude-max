# Ça Part.

L'application mobile de [ca-part.fr](https://www.ca-part.fr) : les mêmes
concepts, la même direction artistique, dans un binaire natif publiable sur le
Play Store et l'App Store.

## État

| Brique | État |
| --- | --- |
| DA du site (crème, noir, rouge, une couleur par concept) | reprise |
| Les 4 concepts, leurs consignes, chronos et compteurs | repris |
| Accueil, grille des concepts, fiche, mélange | repris |
| Partie : cartes, minuteurs 15 s / 20 s, prénoms | en place |
| Mode « Entre vous » (les joueurs écrivent les cartes) | en place |
| Déblocage payant via achats in-app + restauration | en place |
| Build APK / AAB / IPA + CI GitHub Actions | en place |
| **Les paquets de cartes** | **58 cartes d'amorce, à remplacer par les vôtres** |
| Police, logo, icône | à fournir |

Détail et marche à suivre : [`docs/PORTAGE.md`](docs/PORTAGE.md).

## Les concepts

| Concept | Code | Chrono | Accès |
| --- | --- | --- | --- |
| Rapido | RAP | 15 s | 3,99 € |
| Dilemme | DIL | — | gratuit |
| Confession | CON | — | 3,99 € |
| Sauve-moi si tu peux | PUN | 20 s | 3,99 € |
| Entre vous | — | — | gratuit |

## Structure

```
app/
  lib/
    design/          tokens + composants — toute la DA vit ici
    models/          concept, carte, déroulé d'une partie
    data/            chargement du catalogue
    routing/         routes calquées sur les URLs du site
    features/        accueil, concepts, partie, entre vous, infos
    services/        préférences locales, achats in-app, haptique
  assets/concepts/   le catalogue et les paquets de cartes
  test/              tests unitaires et de widgets
docs/
  BUILD.md           produire l'APK, l'AAB, l'IPA
  STORE.md           checklists Play Store / App Store, achats in-app
  PORTAGE.md         état du portage et ce qui reste à fournir
```

## Démarrage rapide

```bash
cd app
flutter pub get
flutter run
flutter build apk --release
```

Détails dans [`docs/BUILD.md`](docs/BUILD.md).

## Choix techniques

- **Flutter** plutôt qu'une WebView : Apple rejette les sites emballés
  (guideline 4.2), et l'app doit tourner sans connexion pendant une soirée.
- **Rien sur un serveur** : prénoms, réglages et achats restent sur
  l'appareil ; les questions d'« Entre vous » sont effacées à la fin de la
  partie. C'est la promesse du site, et ça simplifie les déclarations de
  confidentialité des deux stores.
- **Le contenu est de la donnée** : les cartes sont un JSON, la DA un fichier
  de tokens. Changer les paquets ou la charte ne demande pas de toucher aux
  écrans.
