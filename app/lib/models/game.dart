import 'package:flutter/widgets.dart';

/// Moteur de jeu utilisé pour dérouler une partie.
///
/// La plupart des jeux de soirée se ramènent à l'un de ces déroulés ; les jeux
/// de la web app qui n'y rentrent pas déclarent [GameEngine.custom] et
/// fournissent leur propre écran via [GameDefinition.customBuilder].
enum GameEngine {
  /// On pioche des cartes une à une (défis, questions, « qui est le plus… »).
  promptDeck,

  /// Manches chronométrées : un joueur fait deviner, l'équipe marque des points.
  timedRound,

  /// Écran spécifique au jeu.
  custom,
}

/// Une carte du paquet d'un jeu.
@immutable
class GamePrompt {
  const GamePrompt({
    required this.text,
    this.subtitle,
    this.spicy = false,
  });

  /// Le texte principal affiché en grand.
  final String text;

  /// Précision, règle du tour, ou consigne secondaire.
  final String? subtitle;

  /// Marque une carte réservée au mode « hot » / adulte.
  final bool spicy;

  factory GamePrompt.fromJson(Map<String, dynamic> json) => GamePrompt(
        text: json['text'] as String,
        subtitle: json['subtitle'] as String?,
        spicy: json['spicy'] as bool? ?? false,
      );

  Map<String, dynamic> toJson() => {
        'text': text,
        if (subtitle != null) 'subtitle': subtitle,
        if (spicy) 'spicy': true,
      };
}

/// Définition complète d'un jeu, telle qu'affichée dans la liste des concepts
/// et telle que jouée.
@immutable
class GameDefinition {
  const GameDefinition({
    required this.id,
    required this.name,
    required this.tagline,
    required this.description,
    required this.rules,
    required this.engine,
    required this.accent,
    this.emoji = '🎲',
    this.minPlayers = 2,
    this.maxPlayers = 12,
    this.durationMinutes = 15,
    this.needsPlayerNames = true,
    this.roundSeconds = 60,
    this.prompts = const <GamePrompt>[],
    this.customBuilder,
  });

  /// Identifiant stable, utilisé dans les URLs et la persistance.
  final String id;

  final String name;

  /// Une ligne d'accroche, affichée sous le nom dans la liste.
  final String tagline;

  /// Présentation longue, affichée sur la fiche du jeu.
  final String description;

  /// Les règles, une étape par entrée.
  final List<String> rules;

  final GameEngine engine;

  /// Couleur d'accent de la fiche et de l'écran de jeu.
  final Color accent;

  final String emoji;
  final int minPlayers;
  final int maxPlayers;
  final int durationMinutes;

  /// Si faux, on saute l'écran de saisie des joueurs.
  final bool needsPlayerNames;

  /// Durée d'une manche pour [GameEngine.timedRound].
  final int roundSeconds;

  /// Le paquet de cartes du jeu.
  final List<GamePrompt> prompts;

  /// Écran dédié pour [GameEngine.custom].
  final WidgetBuilder? customBuilder;

  String get playersLabel => minPlayers == maxPlayers
      ? '$minPlayers joueurs'
      : '$minPlayers–$maxPlayers joueurs';

  String get durationLabel => '$durationMinutes min';
}
