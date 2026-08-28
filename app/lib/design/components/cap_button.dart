import 'package:flutter/material.dart';

import '../../services/haptics.dart';
import '../tokens.dart';

enum CapButtonVariant {
  /// Bouton rouge plein : « Lancer », « Lancer une partie ».
  primary,

  /// Bouton contourné : « Débloquer Rapido · 3,99 € », « Voir les concepts ».
  outline,
}

/// Bouton pleine largeur de Ça Part.
class CapButton extends StatefulWidget {
  const CapButton({
    super.key,
    required this.label,
    this.onPressed,
    this.variant = CapButtonVariant.primary,
    this.expand = true,
    this.busy = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final CapButtonVariant variant;
  final bool expand;
  final bool busy;

  @override
  State<CapButton> createState() => _CapButtonState();
}

class _CapButtonState extends State<CapButton> {
  bool _pressed = false;

  bool get _enabled => widget.onPressed != null && !widget.busy;

  @override
  Widget build(BuildContext context) {
    final primary = widget.variant == CapButtonVariant.primary;

    final background = primary
        ? (_enabled
            ? (_pressed ? CapColors.redPressed : CapColors.red)
            : CapColors.border)
        : (_pressed ? CapColors.surfaceSunken : const Color(0x00000000));

    final foreground = primary
        ? (_enabled ? CapColors.onRed : CapColors.textMuted)
        : (_enabled ? CapColors.textPrimary : CapColors.textMuted);

    return Semantics(
      button: true,
      enabled: _enabled,
      label: widget.label,
      child: GestureDetector(
        onTapDown: _enabled ? (_) => setState(() => _pressed = true) : null,
        onTapCancel: _enabled ? () => setState(() => _pressed = false) : null,
        onTapUp: _enabled ? (_) => setState(() => _pressed = false) : null,
        onTap: _enabled
            ? () {
                Haptics.light();
                widget.onPressed!();
              }
            : null,
        child: AnimatedContainer(
          duration: CapMotion.fast,
          curve: CapMotion.curve,
          height: 58,
          width: widget.expand ? double.infinity : null,
          padding: const EdgeInsets.symmetric(horizontal: CapSpacing.lg),
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: background,
            borderRadius: CapRadius.buttonAll,
            border: primary ? null : Border.all(color: CapColors.border, width: 1.5),
          ),
          child: widget.busy
              ? SizedBox(
                  height: 20,
                  width: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    valueColor: AlwaysStoppedAnimation(foreground),
                  ),
                )
              : Text(
                  widget.label,
                  textAlign: TextAlign.center,
                  overflow: TextOverflow.ellipsis,
                  style: CapType.button.copyWith(color: foreground),
                ),
        ),
      ),
    );
  }
}

/// Lien rouge souligné : « Changer les prénoms », « J'ai déjà acheté ».
class CapLink extends StatelessWidget {
  const CapLink({
    super.key,
    required this.label,
    required this.onPressed,
    this.align = TextAlign.left,
  });

  final String label;
  final VoidCallback? onPressed;
  final TextAlign align;

  @override
  Widget build(BuildContext context) {
    final color = onPressed == null ? CapColors.textMuted : CapColors.red;
    return Semantics(
      button: true,
      link: true,
      child: GestureDetector(
        onTap: onPressed == null
            ? null
            : () {
                Haptics.selection();
                onPressed!();
              },
        behavior: HitTestBehavior.opaque,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: CapSpacing.sm),
          child: Text(
            label,
            textAlign: align,
            style: CapType.link.copyWith(
              color: color,
              decoration: TextDecoration.underline,
              decorationColor: color,
              decorationThickness: 1.6,
            ),
          ),
        ),
      ),
    );
  }
}
