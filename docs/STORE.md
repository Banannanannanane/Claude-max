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
  des cartes. Les paquets de Ça Part touchent à l'humour noir, à l'alcool et
  aux relations : il faut le déclarer — un contenu sous-évalué est un motif de
  retrait.
- **Public cible** : ne pas déclarer un public d'enfants. La question « Quel
  âge faut-il ? » de la FAQ du site doit correspondre à la classification
  déposée.

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
- **Guideline 1.1.x (contenu répréhensible)** : régler la classification par
  âge en fonction des paquets réellement embarqués (17+ le cas échéant) et le
  mentionner dans les notes de revue.
- **Guideline 3.1.1** : aucun lien vers un paiement hors app, y compris depuis
  la FAQ ou le pied de page.
- **Confidentialité** : remplir la fiche « App Privacy » en « Données non
  collectées ».
- **Notes pour le testeur** : indiquer qu'il faut saisir des prénoms
  (« Changer les prénoms ») pour jouer à Entre vous, et fournir un compte
  sandbox pour tester le déblocage d'un concept.

### Envoi

```bash
flutter build ipa --release
xcrun altool --upload-app -f build/ios/ipa/*.ipa -u <apple-id> -p <mot-de-passe-app>
```

ou via Xcode → Organizer → Distribute App.

## Achats in-app

Le site vend les concepts en paiement web (3,99 € l'unité, 7,99 € les trois).
**Ce circuit ne peut pas être réutilisé tel quel dans l'application** : Apple
(guideline 3.1.1) et Google (règlement sur les paiements) imposent leur
facturation pour tout contenu numérique débloqué dans une app, et rejettent un
lien de paiement externe. Le déblocage passe donc par des achats in-app non
consommables, gérés par `app/lib/services/purchase_service.dart`.

### Produits à créer

Les mêmes identifiants des deux côtés (App Store Connect et Play Console) :

| Identifiant | Contenu | Prix |
| --- | --- | --- |
| `fr.capart.concept.rapido` | Rapido complet | 3,99 € |
| `fr.capart.concept.confession` | Confession complet | 3,99 € |
| `fr.capart.concept.sauve-moi-si-tu-peux` | Sauve-moi si tu peux complet | 3,99 € |
| `fr.capart.pack.trois` | Les trois concepts | 7,99 € |

Type : **non consommable** (iOS) / **produit géré** (Android). Dilemme et
Entre vous restent gratuits et n'ont pas de produit.

À prévoir avant l'envoi :

- Les prix affichés dans l'app viennent du store (devise et format locaux) ;
  le prix du site n'est qu'un repli tant que le catalogue n'est pas chargé.
- « J'ai déjà acheté » appelle `restorePurchases()`. Apple exige ce bouton
  pour tout achat non consommable.
- Google Play impose de configurer la licence de test et de publier une
  première version sur une piste fermée avant que les produits ne répondent.
- La commission des stores est de 15 à 30 % : à 3,99 €, elle change le calcul
  des « 67 centimes par pote » affiché sur le site.

## Confidentialité

L'app n'a besoin d'aucune permission Android ni iOS. Le seul réseau utilisé
est celui du magasin d'applications, pour les achats. La politique de
confidentialité peut donc tenir en un paragraphe : aucune collecte, aucun
partage, aucun traqueur ; les prénoms et les réglages restent sur l'appareil,
les questions d'« Entre vous » sont effacées à la fin de la partie, et tout
disparaît à la désinstallation.
