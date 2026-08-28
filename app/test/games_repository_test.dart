import 'package:ca_part/data/games_repository.dart';
import 'package:ca_part/models/game.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('GamesRepository.parseGame', () {
    test('lit une définition complète', () {
      final game = GamesRepository.parseGame({
        'id': 'demo',
        'name': 'Démo',
        'tagline': 'Une ligne',
        'description': 'Un paragraphe',
        'rules': ['Règle 1', 'Règle 2'],
        'engine': 'timedRound',
        'accent': '#4DD9C0',
        'emoji': '⏱️',
        'minPlayers': 4,
        'maxPlayers': 8,
        'durationMinutes': 12,
        'roundSeconds': 45,
        'prompts': [
          {'text': 'Carte A', 'subtitle': 'Sous-titre'},
          {'text': 'Carte B', 'spicy': true},
        ],
      });

      expect(game.id, 'demo');
      expect(game.engine, GameEngine.timedRound);
      expect(game.accent, const Color(0xFF4DD9C0));
      expect(game.roundSeconds, 45);
      expect(game.rules, hasLength(2));
      expect(game.prompts.first.subtitle, 'Sous-titre');
      expect(game.prompts.last.spicy, isTrue);
      expect(game.playersLabel, '4–8 joueurs');
      expect(game.durationLabel, '12 min');
    });

    test('applique les valeurs par défaut', () {
      final game = GamesRepository.parseGame({'id': 'x', 'name': 'X'});

      expect(game.engine, GameEngine.promptDeck);
      expect(game.minPlayers, 2);
      expect(game.maxPlayers, 12);
      expect(game.prompts, isEmpty);
      expect(game.needsPlayerNames, isTrue);
    });

    test('accepte une couleur sans canal alpha', () {
      final game =
          GamesRepository.parseGame({'id': 'x', 'name': 'X', 'accent': '#FF4D6D'});
      expect(game.accent, const Color(0xFFFF4D6D));
    });

    test('rejette un moteur inconnu', () {
      expect(
        () => GamesRepository.parseGame(
            {'id': 'x', 'name': 'X', 'engine': 'roulette'}),
        throwsFormatException,
      );
    });

    test('rejette une couleur invalide', () {
      expect(
        () => GamesRepository.parseGame(
            {'id': 'x', 'name': 'X', 'accent': 'rouge'}),
        throwsFormatException,
      );
    });
  });
}
