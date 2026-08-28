# Paquets de cartes

`index.json` décrit les quatre concepts de Ça Part : consigne, couleur, code de
carte, taille du paquet et nombre de cartes tirées par partie. Ces métadonnées
sont celles du site (les paquets gratuits et payants y sont réunis, cette
version n'ayant pas de paiement).

**Les 488 cartes livrées sont des emplacements vides**, tous marqués
« (à définir) ». Les positions et les codes sont en place, les textes restent à
écrire : il suffit de remplacer le champ `text` de chaque entrée.

| Concept | Emplacements |
| --- | --- |
| Rapido | 104 |
| Dilemme | 120 |
| Confession | 166 |
| Sauve-moi si tu peux | 98 |

## Format d'une carte

```jsonc
{
  "n": 626,                 // numéro imprimé en bas de carte
  "text": "…",              // texte affiché en grand
  "hint": "…"               // ligne grise sous le texte (optionnelle)
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
