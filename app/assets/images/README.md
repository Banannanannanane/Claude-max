# Images de l'application

À déposer ici lors du portage de la DA :

| Fichier | Usage | Format |
| --- | --- | --- |
| `app_icon.png` | icône de lancement | PNG 1024×1024, sans transparence |
| `app_icon_foreground.png` | calque avant de l'icône adaptative Android | PNG 1024×1024, sujet centré dans les 66 % centraux |
| `splash.png` | écran de démarrage natif | PNG, logo centré sur fond transparent |

Puis régénérer :

```bash
dart run flutter_launcher_icons
dart run flutter_native_splash:create
```

Ce dossier est déclaré dans `pubspec.yaml` : il doit exister même vide, d'où ce
fichier.
