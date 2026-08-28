import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme.dart';
import '../tokens.dart';
import 'cap_card.dart';

/// Écran type de l'app.
///
/// Deux en-têtes possibles, repris du site : la barre de marque « Ça Part. »
/// sur les écrans de premier niveau, et le chevron de retour surmonté du titre
/// centré sur les fiches de concept.
class CapScaffold extends StatelessWidget {
  const CapScaffold({
    super.key,
    required this.child,
    this.title,
    this.onBack,
    this.showWordmark = false,
    this.actions = const <Widget>[],
    this.padded = true,
    this.bottomBar,
  });

  final Widget child;
  final String? title;
  final VoidCallback? onBack;
  final bool showWordmark;
  final List<Widget> actions;
  final bool padded;
  final Widget? bottomBar;

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: capSystemOverlay,
      child: Scaffold(
        backgroundColor: CapColors.background,
        body: SafeArea(
          child: Column(
            children: [
              if (showWordmark) const _Wordmark() else if (title != null || onBack != null) _TitleBar(title: title, onBack: onBack, actions: actions),
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
    );
  }
}

class _Wordmark extends StatelessWidget {
  const _Wordmark();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(
        CapSpacing.lg,
        CapSpacing.md,
        CapSpacing.lg,
        CapSpacing.md,
      ),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: CapColors.divider)),
      ),
      child: const CapWordmark(),
    );
  }
}

class _TitleBar extends StatelessWidget {
  const _TitleBar({required this.title, required this.onBack, required this.actions});

  final String? title;
  final VoidCallback? onBack;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 56,
      child: Stack(
        alignment: Alignment.center,
        children: [
          if (title != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 56),
              child: Text(
                title!,
                textAlign: TextAlign.center,
                overflow: TextOverflow.ellipsis,
                style: CapType.bodyStrong.copyWith(color: CapColors.textSecondary),
              ),
            ),
          if (onBack != null)
            Align(
              alignment: Alignment.centerLeft,
              child: IconButton(
                onPressed: onBack,
                icon: const Icon(Icons.chevron_left_rounded, size: 30),
                color: CapColors.textPrimary,
                tooltip: 'Retour',
              ),
            ),
          if (actions.isNotEmpty)
            Align(
              alignment: Alignment.centerRight,
              child: Row(mainAxisSize: MainAxisSize.min, children: actions),
            ),
        ],
      ),
    );
  }
}
