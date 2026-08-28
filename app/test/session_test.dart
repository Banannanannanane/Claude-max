import 'package:ca_part/models/game.dart';
import 'package:ca_part/models/session.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

GameDefinition _game(List<GamePrompt> prompts) => GameDefinition(
      id: 'g',
      name: 'G',
      tagline: '',
      description: '',
      rules: const [],
      engine: GameEngine.promptDeck,
      accent: const Color(0xFFFF4D6D),
      prompts: prompts,
    );

void main() {
  group('GameSession', () {
    test('parcourt tout le paquet puis se termine', () {
      final session = GameSession(
        game: _game(const [GamePrompt(text: 'a'), GamePrompt(text: 'b')]),
        players: const ['Alice', 'Bob'],
        seed: 1,
      );

      expect(session.total, 2);
      expect(session.isFinished, isFalse);
      session.next();
      session.next();
      expect(session.isFinished, isTrue);
      expect(session.current, isNull);
    });

    test('exclut les cartes hot quand le mode est désactivé', () {
      final prompts = const [
        GamePrompt(text: 'soft'),
        GamePrompt(text: 'hot', spicy: true),
      ];

      expect(
        GameSession(game: _game(prompts), players: const [], seed: 1).total,
        1,
      );
      expect(
        GameSession(
          game: _game(prompts),
          players: const [],
          includeSpicy: true,
          seed: 1,
        ).total,
        2,
      );
    });

    test('fait tourner les joueurs', () {
      final session = GameSession(
        game: _game(const [
          GamePrompt(text: 'a'),
          GamePrompt(text: 'b'),
          GamePrompt(text: 'c'),
        ]),
        players: const ['Alice', 'Bob'],
        seed: 7,
      );

      expect(session.currentPlayer, 'Alice');
      expect(session.nextPlayer, 'Bob');
      session.next();
      expect(session.currentPlayer, 'Bob');
      session.next();
      expect(session.currentPlayer, 'Alice');
    });

    test('restart remet le paquet à zéro', () {
      final session = GameSession(
        game: _game(const [GamePrompt(text: 'a')]),
        players: const ['Alice'],
        seed: 3,
      );
      session.next();
      expect(session.isFinished, isTrue);
      session.restart();
      expect(session.isFinished, isFalse);
      expect(session.currentPlayer, 'Alice');
    });

    test('un paquet vide est signalé, pas planté', () {
      final session =
          GameSession(game: _game(const []), players: const [], seed: 1);
      expect(session.isEmpty, isTrue);
      expect(session.progress, 1);
      session.next();
      expect(session.current, isNull);
    });
  });
}
