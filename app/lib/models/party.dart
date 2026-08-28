import 'dart:math';

import 'package:flutter/foundation.dart';

import 'concept.dart';

/// Une carte prête à être montrée : texte déjà personnalisé avec les prénoms
/// de la table, et joueurs désignés pour le tour.
@immutable
class DrawnCard {
  const DrawnCard({
    required this.concept,
    required this.card,
    required this.text,
    required this.hint,
    required this.readers,
  });

  final Concept concept;
  final ConceptCard card;

  /// Texte de la carte, jetons de prénom remplacés.
  final String text;
  final String? hint;

  /// Le ou les joueurs concernés par ce tour, dans l'ordre : celui qui prend le
  /// téléphone d'abord.
  final List<String> readers;

  String get code => concept.cardCode(card);
}

/// Le déroulé d'une partie : un paquet tiré une fois pour toutes, les prénoms
/// de la table, et la position courante.
///
/// Le tirage est déterministe pour une graine donnée, ce qui rend la
/// distribution testable.
class Party extends ChangeNotifier {
  Party({
    required List<Concept> concepts,
    required List<String> players,
    required Set<String> unlockedConceptIds,
    int? seed,
  })  : players = List.unmodifiable(players),
        _random = Random(seed ?? DateTime.now().millisecondsSinceEpoch) {
    _deck = _draw(concepts, unlockedConceptIds);
  }

  final List<String> players;
  final Random _random;

  late final List<DrawnCard> _deck;
  int _index = 0;

  List<DrawnCard> get deck => List.unmodifiable(_deck);
  bool get isEmpty => _deck.isEmpty;
  bool get isFinished => _index >= _deck.length;
  int get position => _index;
  int get total => _deck.length;
  DrawnCard? get current => isFinished ? null : _deck[_index];

  void next() {
    if (isFinished) return;
    _index++;
    notifyListeners();
  }

  void restart() {
    _index = 0;
    notifyListeners();
  }

  /// Tire les cartes de chaque concept demandé, dans la limite de son
  /// `drawPerGame`, puis mélange l'ensemble : en mode « Mélange les concepts »
  /// les formats s'alternent au lieu de se suivre par paquets.
  List<DrawnCard> _draw(List<Concept> concepts, Set<String> unlocked) {
    final drawn = <DrawnCard>[];

    for (final concept in concepts) {
      final available = concept.cards
          .where((c) => c.free || concept.free || unlocked.contains(concept.id))
          .toList()
        ..shuffle(_random);

      for (final card in available.take(concept.drawPerGame)) {
        final readers = _pickReaders(concept.readerCount);
        drawn.add(
          DrawnCard(
            concept: concept,
            card: card,
            text: _personalise(card.text, readers),
            hint: card.hint == null ? null : _personalise(card.hint!, readers),
            readers: readers,
          ),
        );
      }
    }

    drawn.shuffle(_random);
    return List.unmodifiable(drawn);
  }

  /// Désigne les joueurs du tour. Sans prénoms saisis, on retombe sur les
  /// libellés génériques du site (« Joueur 1 », « Joueur 2 »).
  List<String> _pickReaders(int count) {
    if (players.isEmpty) {
      return List.generate(count, (i) => 'Joueur ${i + 1}');
    }
    final pool = List<String>.from(players)..shuffle(_random);
    if (pool.length >= count) return pool.take(count).toList();
    return List.generate(count, (i) => pool[i % pool.length]);
  }

  /// Remplace les jetons de prénom d'une carte.
  ///
  /// `{j1}` et `{j2}` sont les joueurs du tour ; `{autre}` est un joueur pris
  /// au hasard parmi ceux qui ne jouent pas ce tour.
  String _personalise(String text, List<String> readers) {
    if (!text.contains('{')) return text;

    var out = text;
    for (var i = 0; i < readers.length; i++) {
      out = out.replaceAll('{j${i + 1}}', readers[i]);
    }

    if (out.contains('{autre}')) {
      final others = players.where((p) => !readers.contains(p)).toList();
      final pick = others.isEmpty
          ? (players.isEmpty ? 'quelqu\'un' : players[_random.nextInt(players.length)])
          : others[_random.nextInt(others.length)];
      out = out.replaceAll('{autre}', pick);
    }

    // Filet de sécurité : un jeton non résolu ne doit jamais s'afficher tel
    // quel sur une carte pendant une soirée.
    out = out.replaceAll(RegExp(r'\{j\d+\}'), 'Joueur 1');
    return out;
  }
}
