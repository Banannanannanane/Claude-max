import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter/widgets.dart';

import '../models/game.dart';

/// Charge le catalogue de jeux depuis `assets/games/index.json`.
///
/// Le contenu est piloté par les données : porter un jeu de la web app revient à
/// ajouter une entrée dans ce JSON. Un jeu qui a besoin d'un écran spécifique
/// déclare `"engine": "custom"` et son builder est enregistré dans
/// [customScreens].
class GamesRepository {
  GamesRepository({this.assetPath = 'assets/games/index.json'});

  final String assetPath;

  /// Écrans dédiés pour les jeux `custom`, indexés par identifiant de jeu.
  static final Map<String, WidgetBuilder> customScreens = <String, WidgetBuilder>{};

  List<GameDefinition>? _cache;

  Future<List<GameDefinition>> load() async {
    if (_cache != null) return _cache!;
    final raw = await rootBundle.loadString(assetPath);
    final decoded = jsonDecode(raw);
    if (decoded is! List) {
      throw FormatException('$assetPath doit contenir une liste de jeux.');
    }
    _cache = decoded
        .cast<Map<String, dynamic>>()
        .map(parseGame)
        .toList(growable: false);
    return _cache!;
  }

  @visibleForTesting
  void seed(List<GameDefinition> games) => _cache = List.unmodifiable(games);

  static GameDefinition parseGame(Map<String, dynamic> json) {
    final id = json['id'] as String;
    final engine = _parseEngine(json['engine'] as String?);
    return GameDefinition(
      id: id,
      name: json['name'] as String,
      tagline: json['tagline'] as String? ?? '',
      description: json['description'] as String? ?? '',
      rules: (json['rules'] as List?)?.cast<String>() ?? const <String>[],
      engine: engine,
      accent: _parseColor(json['accent'] as String?),
      emoji: json['emoji'] as String? ?? '🎲',
      minPlayers: json['minPlayers'] as int? ?? 2,
      maxPlayers: json['maxPlayers'] as int? ?? 12,
      durationMinutes: json['durationMinutes'] as int? ?? 15,
      needsPlayerNames: json['needsPlayerNames'] as bool? ?? true,
      roundSeconds: json['roundSeconds'] as int? ?? 60,
      prompts: (json['prompts'] as List? ?? const [])
          .cast<Map<String, dynamic>>()
          .map(GamePrompt.fromJson)
          .toList(growable: false),
      customBuilder: engine == GameEngine.custom ? customScreens[id] : null,
    );
  }

  static GameEngine _parseEngine(String? value) {
    switch (value) {
      case 'timedRound':
        return GameEngine.timedRound;
      case 'custom':
        return GameEngine.custom;
      case 'promptDeck':
      case null:
        return GameEngine.promptDeck;
      default:
        throw FormatException('Moteur de jeu inconnu : $value');
    }
  }

  /// Accepte `#RRGGBB` et `#AARRGGBB`.
  static Color _parseColor(String? value) {
    if (value == null || value.isEmpty) return const Color(0xFFFF4D6D);
    var hex = value.replaceFirst('#', '');
    if (hex.length == 6) hex = 'FF$hex';
    final parsed = int.tryParse(hex, radix: 16);
    if (parsed == null) {
      throw FormatException('Couleur invalide : $value');
    }
    return Color(parsed);
  }
}
