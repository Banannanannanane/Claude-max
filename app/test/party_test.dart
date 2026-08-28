import 'package:ca_part/models/concept.dart';
import 'package:ca_part/models/party.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Concept _concept({
  String id = 'c',
  String code = 'CCC',
  int drawPerGame = 3,
  int readerCount = 1,
  List<ConceptCard> cards = const [],
}) =>
    Concept(
      id: id,
      name: id,
      code: code,
      color: const Color(0xFF000000),
      instruction: '…',
      drawPerGame: drawPerGame,
      readerCount: readerCount,
      cards: cards,
    );

void main() {
  group('Party', () {
    test('tire au plus drawPerGame cartes et se termine', () {
      final party = Party(
        concepts: [
          _concept(
            drawPerGame: 2,
            cards: const [
              ConceptCard(number: 1, text: 'a'),
              ConceptCard(number: 2, text: 'b'),
              ConceptCard(number: 3, text: 'c'),
            ],
          ),
        ],
        players: const ['Léa', 'Théo'],
        seed: 1,
      );

      expect(party.total, 2);
      party.next();
      party.next();
      expect(party.isFinished, isTrue);
      expect(party.current, isNull);
    });

    test('remplace les jetons de prénom', () {
      final party = Party(
        concepts: [
          _concept(
            drawPerGame: 1,
            readerCount: 2,
            cards: const [
              ConceptCard(number: 7, text: '{j1} accuse {j2} devant {autre}.'),
            ],
          ),
        ],
        players: const ['Léa', 'Théo', 'Sam'],
        seed: 4,
      );

      final card = party.current!;
      expect(card.readers, hasLength(2));
      expect(card.text, isNot(contains('{')));
      for (final reader in card.readers) {
        expect(card.text, contains(reader));
      }
      // {autre} désigne quelqu'un qui ne joue pas ce tour.
      final other = ['Léa', 'Théo', 'Sam']
          .firstWhere((p) => !card.readers.contains(p));
      expect(card.text, contains(other));
    });

    test('retombe sur « Joueur 1 » sans prénoms saisis', () {
      final party = Party(
        concepts: [
          _concept(
            drawPerGame: 1,
            readerCount: 2,
            cards: const [ConceptCard(number: 1, text: '{j1} et {j2}')],
          ),
        ],
        players: const [],
        seed: 2,
      );

      expect(party.current!.text, 'Joueur 1 et Joueur 2');
    });

    test('mélange les concepts au lieu de les enchaîner par paquets', () {
      final party = Party(
        concepts: [
          _concept(
            id: 'un',
            drawPerGame: 4,
            cards: List.generate(
              4,
              (i) => ConceptCard(number: i, text: 'un $i'),
            ),
          ),
          _concept(
            id: 'deux',
            drawPerGame: 4,
            cards: List.generate(
              4,
              (i) => ConceptCard(number: i, text: 'deux $i'),
            ),
          ),
        ],
        players: const [],
        seed: 9,
      );

      expect(party.total, 8);
      final ids = party.deck.map((c) => c.concept.id).toList();
      // Un paquet trié donnerait 'un' x4 puis 'deux' x4.
      expect(ids.sublist(0, 4).toSet().length, greaterThan(1));
    });

    test('un paquet vide est signalé, pas planté', () {
      final party = Party(
        concepts: [_concept(cards: const [])],
        players: const [],
        seed: 1,
      );
      expect(party.isEmpty, isTrue);
      party.next();
      expect(party.current, isNull);
    });
  });
}
