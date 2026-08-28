import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'design/theme.dart';
import 'models/concept.dart';
import 'routing/router.dart';
import 'services/purchase_service.dart';
import 'services/settings_service.dart';

/// Rend les services et le catalogue accessibles depuis n'importe quel écran.
class AppScope extends InheritedWidget {
  const AppScope({
    super.key,
    required this.settings,
    required this.purchases,
    required this.concepts,
    required super.child,
  });

  final SettingsService settings;
  final PurchaseService purchases;
  final List<Concept> concepts;

  static AppScope of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<AppScope>();
    assert(scope != null, 'AppScope absent au-dessus de ce widget.');
    return scope!;
  }

  Concept conceptById(String id) => concepts.firstWhere(
        (c) => c.id == id,
        orElse: () => throw StateError('Concept inconnu : $id'),
      );

  /// Les concepts jouables en l'état : les gratuits, plus ceux qui ont été
  /// débloqués. Utilisé par « Mélange les concepts ».
  List<Concept> get playableConcepts => concepts
      .where((c) => c.free || settings.isUnlocked(c.id))
      .toList(growable: false);

  @override
  bool updateShouldNotify(AppScope oldWidget) =>
      settings != oldWidget.settings ||
      purchases != oldWidget.purchases ||
      concepts != oldWidget.concepts;
}

class CaPartApp extends StatefulWidget {
  const CaPartApp({
    super.key,
    required this.settings,
    required this.purchases,
    required this.concepts,
  });

  final SettingsService settings;
  final PurchaseService purchases;
  final List<Concept> concepts;

  @override
  State<CaPartApp> createState() => _CaPartAppState();
}

class _CaPartAppState extends State<CaPartApp> {
  late final GoRouter _router = buildRouter();

  @override
  void dispose() {
    // GoRouter s'abonne au routage de la plateforme : sans ce dispose, un
    // routeur abandonné continue d'écouter.
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Les deux services notifient : un déblocage acheté doit rafraîchir la
    // fiche du concept et la grille sans avoir à en sortir.
    return ListenableBuilder(
      listenable: Listenable.merge([widget.settings, widget.purchases]),
      builder: (context, _) => AppScope(
        settings: widget.settings,
        purchases: widget.purchases,
        concepts: widget.concepts,
        child: MaterialApp.router(
          title: 'Ça Part',
          debugShowCheckedModeBanner: false,
          theme: buildCapTheme(),
          routerConfig: _router,
        ),
      ),
    );
  }
}
