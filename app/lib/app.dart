import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'design/theme.dart';
import 'models/game.dart';
import 'routing/router.dart';
import 'services/settings_service.dart';

/// Rend [SettingsService] accessible depuis n'importe quel écran.
class SettingsScope extends InheritedNotifier<SettingsService> {
  const SettingsScope({
    super.key,
    required SettingsService settings,
    required super.child,
  }) : super(notifier: settings);

  static SettingsService of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<SettingsScope>();
    assert(scope != null, 'SettingsScope absent au-dessus de ce widget.');
    return scope!.notifier!;
  }
}

class CaPartApp extends StatefulWidget {
  const CaPartApp({super.key, required this.settings, required this.games});

  final SettingsService settings;
  final List<GameDefinition> games;

  @override
  State<CaPartApp> createState() => _CaPartAppState();
}

class _CaPartAppState extends State<CaPartApp> {
  late final GoRouter _router = buildRouter(games: widget.games);

  @override
  void dispose() {
    // GoRouter s'abonne au routage de la plateforme : sans ce dispose, un
    // routeur abandonné continue d'écouter (visible en test, où deux
    // pumpWidget successifs laissent le premier routeur actif).
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SettingsScope(
      settings: widget.settings,
      child: MaterialApp.router(
        title: 'Ça part !',
        debugShowCheckedModeBanner: false,
        theme: buildCapTheme(),
        routerConfig: _router,
      ),
    );
  }
}
