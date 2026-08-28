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
| Aucun paiement : tout est ouvert | en place |
| Deux apps installables côte à côte (Ça Part / Ça Part test) | en place |
| Build APK / AAB / IPA + CI GitHub Actions | en place |
| **Les textes de cartes** | **488 emplacements « (à définir) »** |
| Police, logo, icône | à fournir |

Détail et marche à suivre : [`docs/PORTAGE.md`](docs/PORTAGE.md).

## Les concepts

| Concept | Code | Chrono | Paquet | Tirées par partie |
| --- | --- | --- | --- | --- |
| Rapido | RAP | 15 s | 104 | 12 |
| Dilemme | DIL | — | 120 | 12 |
| Confession | CON | — | 166 | 12 |
| Sauve-moi si tu peux | PUN | 20 s | 98 | 10 |
| Entre vous | — | — | écrit par les joueurs | — |

Plus « Mélange les concepts », qui tire dans les quatre paquets et alterne les
formats.

## Deux applications

| Variante | Identifiant | Nom sous l'icône |
| --- | --- | --- |
| Production | `fr.capart.ca_part` | Ça Part |
| Test | `fr.capart.ca_part.test` | Ça Part test |

Les identifiants diffèrent, donc les deux s'installent côte à côte sur le même
téléphone.

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
flutter run --flavor prod --dart-define=FLAVOR=prod
flutter build apk --release --flavor prod --dart-define=FLAVOR=prod
```

Détails dans [`docs/BUILD.md`](docs/BUILD.md).

## Choix techniques

- **Flutter** plutôt qu'une WebView : Apple rejette les sites emballés
  (guideline 4.2), et l'app doit tourner sans connexion pendant une soirée.
- **Rien sur un serveur, aucun réseau** : prénoms et réglages restent sur
  l'appareil, les questions d'« Entre vous » sont effacées à la fin de la
  partie. C'est la promesse du site, et ça simplifie les déclarations de
  confidentialité des deux stores.
- **Le contenu est de la donnée** : les cartes sont un JSON, la DA un fichier
  de tokens. Changer les paquets ou la charte ne demande pas de toucher aux
  écrans.
