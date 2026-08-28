import 'package:flutter/material.dart';

import '../../services/haptics.dart';
import '../tokens.dart';

/// Intertitre en capitales espacées (« CHOISIS TON CONCEPT », « CONCEPT »).
class CapEyebrow extends StatelessWidget {
  const CapEyebrow(this.label, {super.key, this.color});

  final String label;
  final Color? color;

  @override
  Widget build(BuildContext context) => Text(
        label.toUpperCase(),
        style: CapType.eyebrow.copyWith(color: color ?? CapColors.textSecondary),
      );
}

/// La carte de Ça Part : fond crème, coins arrondis, ombre discrète, et une
/// barre de couleur pleine hauteur à gauche quand elle porte un concept.
class CapCard extends StatefulWidget {
  const CapCard({
    super.key,
    required this.child,
    this.accent,
    this.onTap,
    this.padding = const EdgeInsets.all(CapSpacing.lg),
    this.background,
    this.bordered = false,
  });

  final Widget child;

  /// Couleur de la barre latérale. `null` = pas de barre.
  final Color? accent;

  final VoidCallback? onTap;
  final EdgeInsets padding;
  final Color? background;

  /// Trait fin au lieu de l'ombre (lignes secondaires, comme « Envie de
  /// tout ? Mélange les concepts. »).
  final bool bordered;

  @override
  State<CapCard> createState() => _CapCardState();
}

class _CapCardState extends State<CapCard> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final card = AnimatedScale(
      scale: _pressed ? 0.99 : 1.0,
      duration: CapMotion.fast,
      curve: CapMotion.curve,
      child: Container(
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          color: widget.background ?? CapColors.surface,
          borderRadius: CapRadius.cardAll,
          border: widget.bordered ? Border.all(color: CapColors.border) : null,
          boxShadow: widget.bordered ? null : CapShadows.card,
        ),
        child: IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (widget.accent != null)
                Container(width: CapRadius.accentBar, color: widget.accent),
              Expanded(
                child: Padding(padding: widget.padding, child: widget.child),
              ),
            ],
          ),
        ),
      ),
    );

    if (widget.onTap == null) return card;

    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) => setState(() => _pressed = false),
      onTap: () {
        Haptics.selection();
        widget.onTap!();
      },
      child: card,
    );
  }
}

/// Le monogramme « ç. » en bas à droite des cartes de concept.
class CapCardMark extends StatelessWidget {
  const CapCardMark({super.key, this.label = 'ç.', this.color});

  final String label;
  final Color? color;

  @override
  Widget build(BuildContext context) => Align(
        alignment: Alignment.centerRight,
        child: Text(
          label,
          style: CapType.bodyStrong.copyWith(color: color ?? CapColors.red),
        ),
      );
}

/// Le logo « Ça Part. » — le point est rouge.
class CapWordmark extends StatelessWidget {
  const CapWordmark({super.key, this.fontSize = 22, this.onInk = false});

  final double fontSize;
  final bool onInk;

  @override
  Widget build(BuildContext context) {
    final base = CapType.title.copyWith(
      fontSize: fontSize,
      letterSpacing: -0.5,
      color: onInk ? CapColors.onInk : CapColors.textPrimary,
    );
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(text: 'Ça Part', style: base),
          TextSpan(text: '.', style: base.copyWith(color: CapColors.red)),
        ],
      ),
    );
  }
}

/// Carte noire du mode « Entre vous » : ce sont les joueurs qui les écrivent.
class CapInkCard extends StatelessWidget {
  const CapInkCard({
    super.key,
    required this.eyebrow,
    required this.text,
    this.footer = 'Ça Part.',
  });

  final String eyebrow;
  final String text;
  final String footer;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(CapSpacing.lg),
      decoration: const BoxDecoration(
        color: CapColors.ink,
        borderRadius: CapRadius.cardAll,
        boxShadow: CapShadows.card,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CapEyebrow(eyebrow, color: CapColors.onInkMuted),
          const SizedBox(height: CapSpacing.md),
          Text(
            text,
            style: CapType.heading.copyWith(color: CapColors.onInk),
          ),
          const SizedBox(height: CapSpacing.md),
          Align(
            alignment: Alignment.centerRight,
            child: CapWordmark(fontSize: 14, onInk: true),
          ),
        ],
      ),
    );
  }
}
