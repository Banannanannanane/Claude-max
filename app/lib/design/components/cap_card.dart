import 'package:flutter/material.dart';

import '../../services/haptics.dart';
import '../tokens.dart';

/// Carte cliquable — brique de base des listes de jeux.
class CapCard extends StatefulWidget {
  const CapCard({
    super.key,
    required this.child,
    this.onTap,
    this.color,
    this.padding = const EdgeInsets.all(CapSpacing.md),
  });

  final Widget child;
  final VoidCallback? onTap;
  final Color? color;
  final EdgeInsets padding;

  @override
  State<CapCard> createState() => _CapCardState();
}

class _CapCardState extends State<CapCard> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: widget.onTap == null ? null : (_) => setState(() => _pressed = true),
      onTapCancel: widget.onTap == null ? null : () => setState(() => _pressed = false),
      onTapUp: widget.onTap == null ? null : (_) => setState(() => _pressed = false),
      onTap: widget.onTap == null
          ? null
          : () {
              Haptics.selection();
              widget.onTap!();
            },
      child: AnimatedScale(
        scale: _pressed ? 0.985 : 1.0,
        duration: CapMotion.fast,
        curve: CapMotion.curve,
        child: Container(
          padding: widget.padding,
          decoration: BoxDecoration(
            color: widget.color ?? CapColors.surfaceRaised,
            borderRadius: CapRadius.lgAll,
            border: Border.all(color: CapColors.border),
            boxShadow: CapShadows.card,
          ),
          child: widget.child,
        ),
      ),
    );
  }
}

/// Petite étiquette (durée, nombre de joueurs, catégorie…).
class CapTag extends StatelessWidget {
  const CapTag({super.key, required this.label, this.icon, this.color});

  final String label;
  final IconData? icon;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final tint = color ?? CapColors.textSecondary;
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: CapSpacing.sm + 2,
        vertical: CapSpacing.xs + 1,
      ),
      decoration: BoxDecoration(
        color: CapColors.border,
        borderRadius: CapRadius.pillAll,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 13, color: tint),
            const SizedBox(width: CapSpacing.xs),
          ],
          Text(label, style: CapType.caption.copyWith(color: tint)),
        ],
      ),
    );
  }
}
