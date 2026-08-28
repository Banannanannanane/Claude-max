import 'package:flutter/widgets.dart';

/// Une carte d'un concept.
@immutable
class ConceptCard {
  const ConceptCard({
    required this.number,
    required this.text,
    this.hint,
    this.free = true,
  });

  /// Numéro imprimé en bas de carte, à côté du code du concept (« RAP · 626 »).
  final int number;

  /// Le texte de la carte. Peut contenir des jetons de prénom : {j1}, {j2},
  /// {autre} — remplacés au tirage par les prénoms de la table.
  final String text;

  /// Ligne d'exemple ou de relance affichée sous le texte, en gris.
  final String? hint;

  /// `false` pour les cartes du paquet étendu, réservées aux acheteurs.
  final bool free;

  factory ConceptCard.fromJson(Map<String, dynamic> json) => ConceptCard(
        number: json['n'] as int,
        text: json['text'] as String,
        hint: json['hint'] as String?,
        free: json['free'] as bool? ?? true,
      );
}

/// L'exemple affiché sur la fiche d'un concept, sous la consigne.
@immutable
class ConceptExample {
  const ConceptExample({required this.body});

  /// Texte de l'exemple, préfixé par « Exemple · » à l'affichage.
  final String body;

  factory ConceptExample.fromJson(Map<String, dynamic> json) =>
      ConceptExample(body: json['body'] as String);
}

/// Un concept de Ça Part : un format de jeu, son paquet, sa consigne et son
/// modèle d'accès (gratuit ou débloquable).
@immutable
class Concept {
  const Concept({
    required this.id,
    required this.name,
    required this.code,
    required this.color,
    required this.instruction,
    required this.freeCardCount,
    required this.extraCardCount,
    required this.drawPerGame,
    required this.cards,
    this.example,
    this.free = false,
    this.priceLabel = '3,99 €',
    this.timerSeconds = 0,
    this.readerCount = 1,
  });

  /// Identifiant stable, repris dans les URLs (`/concepts/<id>`).
  final String id;

  final String name;

  /// Préfixe du code de carte : RAP, DIL, CON, PUN.
  final String code;

  /// Couleur de la barre latérale des cartes et de la pastille du concept.
  final Color color;

  /// La consigne du tour, affichée en gros sur la fiche.
  final String instruction;

  final ConceptExample? example;

  /// Nombre de cartes gratuites annoncé sur la fiche.
  final int freeCardCount;

  /// Nombre de cartes supplémentaires débloquées à l'achat. 0 si le concept
  /// est intégralement gratuit.
  final int extraCardCount;

  /// Nombre de cartes tirées pour une partie.
  final int drawPerGame;

  /// Concept accessible sans achat (Dilemme).
  final bool free;

  final String priceLabel;

  /// Durée d'un tour en secondes. 0 quand le concept n'est pas chronométré.
  final int timerSeconds;

  /// Nombre de joueurs impliqués dans un tour : 1 (Rapido, Dilemme,
  /// Confession) ou 2 (Sauve-moi si tu peux — celui qui lit et celui qui
  /// défend).
  final int readerCount;

  final List<ConceptCard> cards;

  bool get isTimed => timerSeconds > 0;
  bool get hasExtraCards => extraCardCount > 0;

  /// « 36 cartes gratuites · 62 de plus · 10 tirées par partie »
  String get metaLabel {
    final parts = <String>[
      '$freeCardCount carte${freeCardCount > 1 ? 's' : ''} gratuite'
          '${freeCardCount > 1 ? 's' : ''}',
      if (hasExtraCards) '$extraCardCount de plus',
      '$drawPerGame tirée${drawPerGame > 1 ? 's' : ''} par partie',
    ];
    return parts.join(' · ');
  }

  String cardCode(ConceptCard card) => '$code · ${card.number}';

  factory Concept.fromJson(Map<String, dynamic> json) => Concept(
        id: json['id'] as String,
        name: json['name'] as String,
        code: json['code'] as String,
        color: parseColor(json['color'] as String),
        instruction: json['instruction'] as String,
        example: json['example'] == null
            ? null
            : ConceptExample.fromJson(json['example'] as Map<String, dynamic>),
        freeCardCount: json['freeCardCount'] as int,
        extraCardCount: json['extraCardCount'] as int? ?? 0,
        drawPerGame: json['drawPerGame'] as int,
        free: json['free'] as bool? ?? false,
        priceLabel: json['priceLabel'] as String? ?? '3,99 €',
        timerSeconds: json['timerSeconds'] as int? ?? 0,
        readerCount: json['readerCount'] as int? ?? 1,
        cards: (json['cards'] as List? ?? const [])
            .cast<Map<String, dynamic>>()
            .map(ConceptCard.fromJson)
            .toList(growable: false),
      );

  /// Accepte `#RRGGBB` et `#AARRGGBB`.
  static Color parseColor(String value) {
    var hex = value.replaceFirst('#', '');
    if (hex.length == 6) hex = 'FF$hex';
    final parsed = int.tryParse(hex, radix: 16);
    if (parsed == null) {
      throw FormatException('Couleur invalide : $value');
    }
    return Color(parsed);
  }
}
