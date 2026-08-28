import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme.dart';
import '../tokens.dart';

/// Écran type de l'app : fond dégradé de la marque, safe areas gérées, barre de
/// titre optionnelle avec bouton retour.
class CapScaffold extends StatelessWidget {
  const CapScaffold({
    super.key,
    required this.child,
    this.title,
    this.onBack,
    this.actions = const <Widget>[],
    this.padded = true,
    this.bottomBar,
  });

  final Widget child;
  final String? title;
  final VoidCallback? onBack;
  final List<Widget> actions;
  final bool padded;
  final Widget? bottomBar;

  @override
  Widget build(BuildContext context) {
    final hasHeader = title != null || onBack != null || actions.isNotEmpty;

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: capSystemOverlay,
      child: Scaffold(
        backgroundColor: CapColors.background,
        body: DecoratedBox(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [CapColors.surface, CapColors.background],
              stops: [0.0, 0.6],
            ),
          ),
          child: SafeArea(
            child: Column(
              children: [
                if (hasHeader) _Header(title: title, onBack: onBack, actions: actions),
                Expanded(
                  child: Padding(
                    padding: padded
                        ? const EdgeInsets.symmetric(horizontal: CapSpacing.lg)
                        : EdgeInsets.zero,
                    child: child,
                  ),
                ),
                if (bottomBar != null)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(
                      CapSpacing.lg,
                      CapSpacing.sm,
                      CapSpacing.lg,
                      CapSpacing.md,
                    ),
                    child: bottomBar,
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.title, required this.onBack, required this.actions});

  final String? title;
  final VoidCallback? onBack;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        CapSpacing.sm,
        CapSpacing.sm,
        CapSpacing.sm,
        CapSpacing.sm,
      ),
      child: Row(
        children: [
          if (onBack != null)
            IconButton(
              onPressed: onBack,
              icon: const Icon(Icons.arrow_back_rounded),
              color: CapColors.textPrimary,
              tooltip: 'Retour',
            )
          else
            const SizedBox(width: CapSpacing.md),
          Expanded(
            child: Text(
              title ?? '',
              style: CapType.heading.copyWith(color: CapColors.textPrimary),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          ...actions,
          const SizedBox(width: CapSpacing.sm),
        ],
      ),
    );
  }
}
