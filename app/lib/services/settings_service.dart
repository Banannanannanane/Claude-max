import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'haptics.dart';

/// Préférences persistées de l'app. Aucun compte, aucun réseau : tout reste sur
/// l'appareil, ce qui simplifie la déclaration de confidentialité des stores.
class SettingsService extends ChangeNotifier {
  SettingsService._(this._prefs);

  static const _kSpicy = 'spicy_mode';
  static const _kHaptics = 'haptics';
  static const _kPlayers = 'last_players';

  final SharedPreferences _prefs;

  static Future<SettingsService> load() async {
    final prefs = await SharedPreferences.getInstance();
    final service = SettingsService._(prefs);
    Haptics.enabled = service.haptics;
    return service;
  }

  /// Débloque les cartes marquées `spicy` dans les paquets.
  bool get spicyMode => _prefs.getBool(_kSpicy) ?? false;
  set spicyMode(bool value) {
    _prefs.setBool(_kSpicy, value);
    notifyListeners();
  }

  bool get haptics => _prefs.getBool(_kHaptics) ?? true;
  set haptics(bool value) {
    _prefs.setBool(_kHaptics, value);
    Haptics.enabled = value;
    notifyListeners();
  }

  /// Derniers joueurs saisis, reproposés au lancement de la partie suivante.
  List<String> get lastPlayers => _prefs.getStringList(_kPlayers) ?? const [];
  set lastPlayers(List<String> value) {
    _prefs.setStringList(_kPlayers, value);
    notifyListeners();
  }
}
