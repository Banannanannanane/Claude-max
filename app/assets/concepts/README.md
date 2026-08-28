# Paquets de cartes

`index.json` décrit les quatre concepts de Ça Part : leur consigne, leur
couleur, leur code de carte, leur modèle d'accès et les compteurs affichés sur
la fiche (« 36 cartes gratuites · 62 de plus · 10 tirées par partie »). Ces
métadonnées sont celles du site.

**Les textes de cartes, eux, sont des cartes d'amorce écrites pour l'app.**
Seules deux cartes proviennent du site (RAP 626 et l'exemple de Confession) ;
le reste est là pour que l'application soit jouable avant l'import. Les vrais
paquets — 37+67 pour Rapido, 120 pour Dilemme, 36+130 pour Confession,
36+62 pour Sauve-moi si tu peux — doivent remplacer le tableau `cards` de
chaque concept.

## Format d'une carte

```jsonc
{
  "n": 626,                 // numéro imprimé en bas de carte
  "text": "…",              // texte affiché en grand
  "hint": "…",              // ligne grise sous le texte (optionnelle)
  "free": true              // false = carte du paquet payant
}
```

## Jetons de prénom

Le texte accepte des jetons remplacés au tirage par les prénoms de la table
(« Changer les prénoms ») :

| Jeton | Remplacé par |
| --- | --- |
| `{j1}` | le joueur qui prend le téléphone |
| `{j2}` | le second joueur du tour (Sauve-moi si tu peux) |
| `{autre}` | un joueur au hasard qui ne joue pas ce tour |

Sans prénoms saisis, `{j1}` et `{j2}` retombent sur « Joueur 1 » et
« Joueur 2 », comme sur le site.
