import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'haptics.dart';

/// Préférences locales : les prénoms de la table et les concepts débloqués.
///
/// Rien ne part sur un serveur — c'est la promesse du site (« Vos questions
/// restent dans le navigateur de ce téléphone »), et ça simplifie la
/// déclaration de confidentialité des deux stores.
class SettingsService extends ChangeNotifier {
  SettingsService._(this._prefs);

  static const _kPlayers = 'players';
  static const _kUnlocked = 'unlocked_concepts';
  static const _kHaptics = 'haptics';

  final SharedPreferences _prefs;

  static Future<SettingsService> load() async {
    final prefs = await SharedPreferences.getInstance();
    final service = SettingsService._(prefs);
    Haptics.enabled = service.haptics;
    return service;
  }

  /// Les prénoms de la table, réutilisés d'une partie à l'autre : autour d'une
  /// table, c'est presque toujours le même groupe.
  List<String> get players => _prefs.getStringList(_kPlayers) ?? const [];
  set players(List<String> value) {
    _prefs.setStringList(_kPlayers, value);
    notifyListeners();
  }

  Set<String> get unlockedConcepts =>
      (_prefs.getStringList(_kUnlocked) ?? const []).toSet();

  bool isUnlocked(String conceptId) => unlockedConcepts.contains(conceptId);

  void unlock(String conceptId) {
    final next = unlockedConcepts..add(conceptId);
    _prefs.setStringList(_kUnlocked, next.toList());
    notifyListeners();
  }

  /// Remplace la liste des concepts débloqués — utilisé après une restauration
  /// d'achats, qui fait autorité sur ce que le téléphone croyait savoir.
  void replaceUnlocked(Set<String> conceptIds) {
    _prefs.setStringList(_kUnlocked, conceptIds.toList());
    notifyListeners();
  }

  bool get haptics => _prefs.getBool(_kHaptics) ?? true;
  set haptics(bool value) {
    _prefs.setBool(_kHaptics, value);
    Haptics.enabled = value;
    notifyListeners();
  }
}
