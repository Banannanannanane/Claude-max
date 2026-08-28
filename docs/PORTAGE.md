# État du portage de ca-part.fr

## Ce qui est repris

| Élément du site | Où c'est dans l'app |
| --- | --- |
| Fond crème, noir, rouge de marque | `app/lib/design/tokens.dart` → `CapColors` |
| Les quatre couleurs de concept | `CapConceptColors`, et `color` de chaque concept |
| Logo « Ça Part. » (point rouge) | `CapWordmark` |
| Cartes à barre de couleur latérale | `CapCard(accent: …)` |
| Cartes noires « Entre vous » | `CapInkCard` |
| Intertitres en capitales espacées | `CapEyebrow` |
| Bouton rouge / bouton contourné / lien souligné | `CapButton`, `CapLink` |
| Page d'accueil (accroche, sections, Entre vous, tarifs) | `features/home` |
| « CHOISIS TON CONCEPT » + « Mélange les concepts » | `features/concepts` |
| Fiche de concept (consigne, exemple, compteurs, achat) | `features/concepts/concept_detail_screen.dart` |
| « Changer les prénoms » | `features/play/players_screen.dart` |
| Déroulé de partie, minuteurs 15 s et 20 s | `features/play/play_screen.dart` |
| Mode « Entre vous » | `features/entre_vous` |
| FAQ « Les vraies questions » | `features/infos` |

Les quatre concepts, avec leurs consignes, couleurs, codes de carte et
compteurs exacts :

| Concept | Code | Couleur | Chrono | Accès | Compteurs |
| --- | --- | --- | --- | --- | --- |
| Rapido | RAP | vert | 15 s | 3,99 € | 37 gratuites · 67 de plus · 12 par partie |
| Dilemme | DIL | ambre | — | gratuit | 120 gratuites · 12 par partie |
| Confession | CON | bleu | — | 3,99 € | 36 gratuites · 130 de plus · 12 par partie |
| Sauve-moi si tu peux | PUN | violet | 20 s | 3,99 € | 36 gratuites · 62 de plus · 10 par partie |

## Ce qui reste à fournir

1. **Les paquets de cartes.** `app/assets/concepts/index.json` contient 58
   cartes d'amorce écrites pour l'app, pas les vôtres — seules RAP 626 et
   l'exemple de Confession viennent du site. Les vrais paquets (37+67, 120,
   36+130, 36+62) remplacent le tableau `cards` de chaque concept. Format et
   jetons de prénom : `app/assets/concepts/README.md`.
2. **La police.** `CapType.fontFamily` est à `null`, donc la police système
   prend le relais. Déposer les fichiers dans `app/assets/fonts/`, décommenter
   le bloc `fonts:` de `pubspec.yaml`, renseigner `fontFamily`.
3. **Le logo et l'icône.** Le monogramme « ç. » rouge dans son cercle, l'icône
   d'application et l'écran de démarrage : voir `app/assets/images/README.md`.
4. **Deux réponses de FAQ.** « Combien de joueurs ? » et « Quel âge faut-il ? »
   n'étaient pas dépliées dans les captures ; elles ne sont donc pas dans
   `features/infos/infos_screen.dart` plutôt que d'être inventées.
5. **Les URL légales.** Le pied de page renvoie pour l'instant vers
   `https://www.ca-part.fr/`. Une fois les chemins exacts connus (CGV,
   mentions légales, confidentialité), les séparer en trois liens.

## Adaptations assumées

Trois endroits où reprendre le site à la lettre aurait produit un contresens
dans une application installée :

- **« Personne ne télécharge »** devient « Il passe de main en main ». La
  promesse d'origine — pas d'installation — ne tient pas dans une app qu'on
  vient d'installer ; ce qu'elle protège, en revanche (un seul téléphone, rien
  à rejoindre), reste vrai et reste dit.
- **Le paiement** passe par les achats in-app. Apple et Google interdisent de
  vendre du contenu numérique autrement dans une application ; voir
  `STORE.md`.
- **La FAQ et les réglages** sont regroupés dans un écran « Infos » : sur le
  site ils sont en bas de la page d'accueil, ce qui n'a pas d'équivalent dans
  une navigation d'app.

## Vérifier

```bash
cd app
flutter analyze
flutter test     # le catalogue livré est validé par test/app_test.dart
flutter run
```
