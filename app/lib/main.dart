import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app.dart';
import 'data/concepts_repository.dart';
import 'design/tokens.dart';
import 'services/purchase_service.dart';
import 'services/settings_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Un seul téléphone qui passe de main en main : on le tient en portrait.
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  final settings = await SettingsService.load();

  try {
    final concepts = await ConceptsRepository().load();
    final purchases = PurchaseService(settings);
    // Le catalogue du magasin arrive après le premier écran : l'app est
    // jouable pendant ce temps, seuls les prix affichés sont ceux du site.
    unawaited(purchases.init(concepts.map((c) => c.id)));
    runApp(CaPartApp(
      settings: settings,
      purchases: purchases,
      concepts: concepts,
    ));
  } catch (error, stack) {
    debugPrint('Catalogue de concepts illisible : $error\n$stack');
    runApp(_CatalogError(error: error));
  }
}

void unawaited(Future<void> future) {
  future.catchError((Object e) => debugPrint('Initialisation des achats : $e'));
}

/// Écran de repli si `assets/concepts/index.json` est absent ou malformé —
/// sans lui l'app démarrerait sur un écran blanc, indiagnosticable sur un
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
              'Le catalogue de concepts n\'a pas pu être chargé.\n\n$error',
              textAlign: TextAlign.center,
              style: CapType.body.copyWith(color: CapColors.textSecondary),
            ),
          ),
        ),
      ),
    );
  }
}
