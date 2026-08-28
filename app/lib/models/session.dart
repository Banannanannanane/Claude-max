import 'dart:math';

import 'package:flutter/foundation.dart';

import 'game.dart';

/// État d'une partie en cours : la liste des joueurs et l'avancement dans le
/// paquet. Le mélange est déterministe pour une graine donnée, ce qui rend le
/// déroulé testable.
class GameSession extends ChangeNotifier {
  GameSession({
    required this.game,
    required List<String> players,
    bool includeSpicy = false,
    int? seed,
  })  : players = List.unmodifiable(players),
        _deck = _buildDeck(game, includeSpicy, seed);

  final GameDefinition game;
  final List<String> players;

  final List<GamePrompt> _deck;
  int _index = 0;
  int _turn = 0;

  static List<GamePrompt> _buildDeck(
    GameDefinition game,
    bool includeSpicy,
    int? seed,
  ) {
    final cards = game.prompts.where((p) => includeSpicy || !p.spicy).toList();
    cards.shuffle(Random(seed ?? DateTime.now().millisecondsSinceEpoch));
    return cards;
  }

  bool get isEmpty => _deck.isEmpty;
  bool get isFinished => _index >= _deck.length;
  int get remaining => (_deck.length - _index).clamp(0, _deck.length);
  int get total => _deck.length;
  double get progress => _deck.isEmpty ? 1 : _index / _deck.length;

  GamePrompt? get current => isFinished ? null : _deck[_index];

  /// Le joueur dont c'est le tour, ou `null` si le jeu ne suit pas les joueurs.
  String? get currentPlayer =>
      players.isEmpty ? null : players[_turn % players.length];

  String? get nextPlayer =>
      players.isEmpty ? null : players[(_turn + 1) % players.length];

  void next() {
    if (isFinished) return;
    _index++;
    _turn++;
    notifyListeners();
  }

  void restart() {
    _index = 0;
    _turn = 0;
    notifyListeners();
  }
}
