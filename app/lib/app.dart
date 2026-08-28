import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'build_flavor.dart';
import 'design/theme.dart';
import 'models/concept.dart';
import 'routing/router.dart';
import 'services/settings_service.dart';

/// Rend les réglages et le catalogue accessibles depuis n'importe quel écran.
class AppScope extends InheritedWidget {
  const AppScope({
    super.key,
    required this.settings,
    required this.concepts,
    required super.child,
  });

  final SettingsService settings;
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

  @override
  bool updateShouldNotify(AppScope oldWidget) =>
      settings != oldWidget.settings || concepts != oldWidget.concepts;
}

class CaPartApp extends StatefulWidget {
  const CaPartApp({super.key, required this.settings, required this.concepts});

  final SettingsService settings;
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
    return ListenableBuilder(
      listenable: widget.settings,
      builder: (context, _) => AppScope(
        settings: widget.settings,
        concepts: widget.concepts,
        child: MaterialApp.router(
          title: BuildFlavor.appName,
          debugShowCheckedModeBanner: false,
          theme: buildCapTheme(),
          routerConfig: _router,
        ),
      ),
    );
  }
}
