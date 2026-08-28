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
        'cardCount': 98,
        'drawPerGame': 10,
        'timerSeconds': 20,
        'readerCount': 2,
        'cards': [
          {'n': 1, 'text': '(à définir)', 'hint': 'Carte n° 1'},
        ],
      });

      expect(concept.color, const Color(0xFF7B4FCB));
      expect(concept.isTimed, isTrue);
      expect(concept.readerCount, 2);
      expect(concept.example?.body, 'Joueur 1 : « … »');
      expect(concept.cards.first.hint, 'Carte n° 1');
      expect(concept.cardCode(concept.cards.first), 'PUN · 1');
    });

    test('cardCount annonce la taille du paquet, pas ce qui est livré', () {
      final declared = Concept.fromJson({
        'id': 'x',
        'name': 'X',
        'code': 'XXX',
        'color': '#000000',
        'instruction': '…',
        'cardCount': 104,
        'drawPerGame': 12,
        'cards': [
          {'n': 1, 'text': '(à définir)'},
        ],
      });
      expect(declared.cardCount, 104);
      expect(declared.metaLabel, '104 cartes · 12 tirées par partie');

      final implicit = Concept.fromJson({
        'id': 'y',
        'name': 'Y',
        'code': 'YYY',
        'color': '#000000',
        'instruction': '…',
        'drawPerGame': 12,
        'cards': [
          {'n': 1, 'text': 'a'},
          {'n': 2, 'text': 'b'},
        ],
      });
      expect(implicit.cardCount, 2);
      expect(implicit.isTimed, isFalse);
    });

    test('rejette une couleur invalide', () {
      expect(() => Concept.parseColor('rouge'), throwsFormatException);
      expect(Concept.parseColor('#FF4D6D'), const Color(0xFFFF4D6D));
    });
  });
}
