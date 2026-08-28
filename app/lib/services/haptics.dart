import 'package:flutter/services.dart';

/// Retour haptique, court-circuité quand l'utilisateur l'a désactivé dans les
/// réglages. Un point d'entrée statique évite de faire descendre le réglage
/// jusque dans les composants du design system.
class Haptics {
  const Haptics._();

  static bool enabled = true;

  static void light() {
    if (enabled) HapticFeedback.lightImpact();
  }

  static void selection() {
    if (enabled) HapticFeedback.selectionClick();
  }

  static void heavy() {
    if (enabled) HapticFeedback.heavyImpact();
  }
}
