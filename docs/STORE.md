# Publier sur le Play Store et l'App Store

## Google Play

### Compte et fiche

- Compte développeur Google Play (frais uniques ~25 $). Depuis 2023, un compte
  personnel neuf doit faire tester l'app par 12 testeurs pendant 14 jours avant
  d'accéder à la production ; un compte d'organisation (avec numéro DUNS) n'a
  pas cette contrainte.
- Créer l'application dans la console, catégorie **Jeux → Jeux de société** ou
  **Applications → Divertissement** selon le positionnement.

### Éléments à fournir

| Élément | Format |
| --- | --- |
| Icône | PNG 512×512, sans transparence |
| Bannière | PNG/JPEG 1024×500 |
| Captures téléphone | 2 à 8, min 320 px, ratio entre 16:9 et 9:16 |
| Captures tablette 7" et 10" | recommandées |
| Description courte | 80 caractères |
| Description longue | 4000 caractères |
| Politique de confidentialité | URL publique obligatoire |

### Questionnaires de la console

- **Sécurité des données** : l'app ne collecte rien et ne transmet rien. Les
  noms des joueurs et les réglages restent dans les préférences locales de
  l'appareil (`shared_preferences`). Répondre « aucune donnée collectée ».
- **Classification du contenu** (IARC) : le questionnaire porte sur le contenu
  des cartes. Si le mode « hot » contient des références sexuelles ou à
  l'alcool, il faut le déclarer — un contenu déclaré sous-évalué est un motif de
  retrait.
- **Public cible** : ne pas déclarer un public d'enfants si les paquets adultes
  existent, sinon les règles « Families » s'appliquent.

### Envoi

`flutter build appbundle --release` puis dépôt du `.aab` sur une piste de test
interne d'abord, production ensuite. Le `versionCode` doit augmenter à chaque
envoi.

## App Store

### Compte et fiche

- Compte Apple Developer Program (99 $/an).
- Créer l'app dans App Store Connect avec un bundle identifier enregistré.

### Éléments à fournir

| Élément | Format |
| --- | --- |
| Icône | PNG 1024×1024, sans canal alpha ni coins arrondis |
| Captures iPhone 6,7" | obligatoires |
| Captures iPad 12,9" | obligatoires si l'app est publiée pour iPad |
| Sous-titre | 30 caractères |
| Description | 4000 caractères |
| Mots-clés | 100 caractères |
| URL de politique de confidentialité | obligatoire |

### Points de vigilance de la revue Apple

- **Guideline 4.2 (Minimum Functionality)** : une app qui n'est qu'un site web
  emballé est rejetée. Ici l'app est un binaire Flutter natif, fonctionnant
  hors ligne, sans WebView — ce motif ne s'applique pas.
- **Guideline 1.1.x (contenu répréhensible)** : si le mode « hot » existe, régler
  la classification par âge en conséquence (17+ le cas échéant) et le mentionner
  dans les notes de revue.
- **Confidentialité** : remplir la fiche « App Privacy » en « Données non
  collectées ».
- **Notes pour le testeur** : indiquer comment activer le mode « hot »
  (Réglages), sinon le testeur ne verra pas le contenu classé.

### Envoi

```bash
flutter build ipa --release
xcrun altool --upload-app -f build/ios/ipa/*.ipa -u <apple-id> -p <mot-de-passe-app>
```

ou via Xcode → Organizer → Distribute App.

## Confidentialité

L'app n'a besoin d'aucune permission Android ni iOS et n'accède pas au réseau.
La politique de confidentialité peut donc tenir en un paragraphe : aucune
collecte, aucun partage, aucun traqueur ; les données saisies (prénoms des
joueurs, réglages) restent sur l'appareil et disparaissent à la désinstallation.
