# Reprendre la web app dans l'app Flutter

Ce document décrit la marche à suivre pour transférer la direction artistique et
les jeux de la web app vers l'application.

## 1. Direction artistique

Tout est centralisé dans `app/lib/design/tokens.dart`. Aucun écran ne code une
couleur ou une taille en dur : remplacer les valeurs de ce fichier suffit à
changer la DA de toute l'app.

| Source côté web | Destination |
| --- | --- |
| Variables CSS `--*` de `:root` | `CapColors` |
| `font-family`, tailles et graisses | `CapType` + `assets/fonts/` |
| `border-radius` | `CapRadius` |
| `box-shadow` | `CapShadows` |
| échelle d'espacement | `CapSpacing` |
| `transition` / `animation` | `CapMotion` |

Pour la typographie :

1. Copier les fichiers `.ttf` / `.otf` dans `app/assets/fonts/`.
2. Décommenter le bloc `fonts:` de `app/pubspec.yaml` et y lister les graisses.
3. Renseigner `CapType.fontFamily` avec le nom de la famille déclarée.

Les images et illustrations vont dans `app/assets/images/` (préférer le SVG
converti en PNG @3x, ou ajouter `flutter_svg` si les sources sont vectorielles).

## 2. Jeux

Le catalogue est piloté par les données : `app/assets/games/index.json`. Chaque
jeu de la web app devient un objet de ce tableau.

```jsonc
{
  "id": "identifiant-stable",       // sert d'URL : /concepts/<id>
  "name": "Nom du jeu",
  "tagline": "Une ligne d'accroche",
  "description": "Présentation longue affichée sur la fiche.",
  "rules": ["Étape 1", "Étape 2"],
  "engine": "promptDeck",           // promptDeck | timedRound | custom
  "accent": "#FF4D6D",
  "emoji": "🃏",
  "minPlayers": 3,
  "maxPlayers": 10,
  "durationMinutes": 20,
  "roundSeconds": 60,               // utilisé par timedRound
  "needsPlayerNames": true,
  "prompts": [
    { "text": "Texte de la carte", "subtitle": "Optionnel", "spicy": false }
  ]
}
```

### Choisir le moteur

- **`promptDeck`** — on pioche les cartes une à une, un joueur après l'autre.
  Couvre les défis, les questions, les « qui est le plus susceptible de… ».
- **`timedRound`** — manches chronométrées avec score : un joueur fait deviner,
  les autres cherchent.
- **`custom`** — le jeu a ses propres écrans. Écrire le widget puis l'enregistrer
  avant le `runApp` :

  ```dart
  GamesRepository.customScreens['mon-jeu'] = (context) => const MonJeuScreen();
  ```

### Cartes réservées aux adultes

`"spicy": true` sur une carte la masque tant que le mode « hot » est désactivé
dans les réglages. C'est ce qui permet de garder une classification d'âge basse
par défaut tout en proposant le contenu adulte.

## 3. Vérifier le portage

```bash
cd app
flutter analyze
flutter test        # test/app_test.dart valide index.json et l'affichage
flutter run         # sur un appareil ou un émulateur
```

`test/app_test.dart` charge le catalogue réel : un JSON malformé ou un moteur
inconnu fait échouer la CI avant d'arriver sur un téléphone.
