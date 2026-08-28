import 'package:ca_part/models/concept.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Concept.fromJson', () {
    test('lit une définition complète', () {
      final concept = Concept.fromJson({
        'id': 'sauve-moi-si-tu-peux',
        'name': 'Sauve-moi si tu peux',
        'code': 'PUN',
        'color': '#7B4FCB',
        'instruction': 'Joueur 1, lis ta carte. Joueur 2, sauve-le : 20 secondes.',
        'example': {'body': 'Joueur 1 : « … »'},
        'freeCardCount': 36,
        'extraCardCount': 62,
        'drawPerGame': 10,
        'timerSeconds': 20,
        'readerCount': 2,
        'cards': [
          {'n': 840, 'text': 'J\'ai rendu la mauvaise urne.', 'hint': '20 s.'},
          {'n': 841, 'text': 'Carte payante', 'free': false},
        ],
      });

      expect(concept.color, const Color(0xFF7B4FCB));
      expect(concept.isTimed, isTrue);
      expect(concept.readerCount, 2);
      expect(concept.example?.body, 'Joueur 1 : « … »');
      expect(concept.cards.first.hint, '20 s.');
      expect(concept.cards.last.free, isFalse);
      expect(concept.cardCode(concept.cards.first), 'PUN · 840');
    });

    test('compose la ligne de méta comme le site', () {
      final paid = Concept.fromJson({
        'id': 'x',
        'name': 'X',
        'code': 'XXX',
        'color': '#000000',
        'instruction': '…',
        'freeCardCount': 36,
        'extraCardCount': 62,
        'drawPerGame': 10,
      });
      expect(paid.metaLabel, '36 cartes gratuites · 62 de plus · 10 tirées par partie');

      final free = Concept.fromJson({
        'id': 'dilemme',
        'name': 'Dilemme',
        'code': 'DIL',
        'color': '#D79A22',
        'instruction': '…',
        'freeCardCount': 120,
        'drawPerGame': 12,
        'free': true,
      });
      expect(free.metaLabel, '120 cartes gratuites · 12 tirées par partie');
      expect(free.hasExtraCards, isFalse);
      expect(free.isTimed, isFalse);
    });

    test('rejette une couleur invalide', () {
      expect(() => Concept.parseColor('rouge'), throwsFormatException);
      expect(Concept.parseColor('#FF4D6D'), const Color(0xFFFF4D6D));
    });
  });
}
