import 'package:flutter/material.dart';

import '../../services/haptics.dart';
import '../tokens.dart';

enum CapButtonVariant { primary, secondary, ghost }

enum CapButtonSize { regular, large }

/// Bouton de l'app : un seul composant pour toutes les variantes, pour que la
/// DA reste pilotée par les tokens.
class CapButton extends StatefulWidget {
  const CapButton({
    super.key,
    required this.label,
    this.onPressed,
    this.variant = CapButtonVariant.primary,
    this.size = CapButtonSize.regular,
    this.icon,
    this.expand = true,
  });

  final String label;
  final VoidCallback? onPressed;
  final CapButtonVariant variant;
  final CapButtonSize size;
  final IconData? icon;
  final bool expand;

  @override
  State<CapButton> createState() => _CapButtonState();
}

class _CapButtonState extends State<CapButton> {
  bool _pressed = false;

  bool get _enabled => widget.onPressed != null;

  Color get _background {
    if (!_enabled) return CapColors.border;
    switch (widget.variant) {
      case CapButtonVariant.primary:
        return _pressed ? CapColors.primaryPressed : CapColors.primary;
      case CapButtonVariant.secondary:
        return _pressed ? CapColors.surface : CapColors.surfaceRaised;
      case CapButtonVariant.ghost:
        return _pressed ? CapColors.border : const Color(0x00000000);
    }
  }

  Color get _foreground {
    if (!_enabled) return CapColors.textMuted;
    return widget.variant == CapButtonVariant.primary
        ? CapColors.onPrimary
        : CapColors.textPrimary;
  }

  @override
  Widget build(BuildContext context) {
    final height = widget.size == CapButtonSize.large ? 60.0 : 52.0;

    final content = Row(
      mainAxisSize: widget.expand ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (widget.icon != null) ...[
          Icon(widget.icon, size: 20, color: _foreground),
          const SizedBox(width: CapSpacing.sm),
        ],
        Flexible(
          child: Text(
            widget.label,
            textAlign: TextAlign.center,
            overflow: TextOverflow.ellipsis,
            style: CapType.button.copyWith(color: _foreground),
          ),
        ),
      ],
    );

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
          height: height,
          width: widget.expand ? double.infinity : null,
          padding: const EdgeInsets.symmetric(horizontal: CapSpacing.lg),
          decoration: BoxDecoration(
            color: _background,
            borderRadius: CapRadius.pillAll,
            border: widget.variant == CapButtonVariant.ghost
                ? Border.all(color: CapColors.border)
                : null,
            boxShadow: widget.variant == CapButtonVariant.primary && _enabled
                ? CapShadows.card
                : null,
          ),
          transform: Matrix4.identity()
            ..scaleByDouble(
              _pressed ? 0.98 : 1.0,
              _pressed ? 0.98 : 1.0,
              1.0,
              1.0,
            ),
          transformAlignment: Alignment.center,
          child: Center(child: content),
        ),
      ),
    );
  }
}
