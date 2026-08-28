import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app.dart';
import 'design/tokens.dart';
import 'routing/router.dart';
import 'services/settings_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Jeu de soirée : on tient le téléphone en portrait, on le passe de main en
  // main.
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  final settings = await SettingsService.load();

  try {
    final games = await gamesRepository.load();
    runApp(CaPartApp(settings: settings, games: games));
  } catch (error, stack) {
    debugPrint('Catalogue de jeux illisible : $error\n$stack');
    runApp(_CatalogError(error: error));
  }
}

/// Écran de repli si `assets/games/index.json` est absent ou malformé — sans
/// lui l'app démarrerait sur un écran blanc, impossible à diagnostiquer sur un
/// appareil.
class _CatalogError extends StatelessWidget {
  const _CatalogError({required this.error});

  final Object error;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        backgroundColor: CapColors.background,
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(CapSpacing.lg),
            child: Text(
              'Le catalogue de jeux n\'a pas pu être chargé.\n\n$error',
              textAlign: TextAlign.center,
              style: CapType.body.copyWith(color: CapColors.textSecondary),
            ),
          ),
        ),
      ),
    );
  }
}
