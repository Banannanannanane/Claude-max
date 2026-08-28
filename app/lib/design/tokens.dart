import 'package:flutter/widgets.dart';

/// Design tokens repris de la web app ca-part.fr.
///
/// La DA du site : fond crème chaud, noir profond pour le texte, un rouge de
/// marque unique pour les actions, et une couleur par concept qui n'apparaît
/// que sur la barre latérale des cartes et les pastilles.
class CapColors {
  const CapColors._();

  // Fonds
  static const background = Color(0xFFF4EDE3);
  static const surface = Color(0xFFF8F2EA);
  static const surfaceSunken = Color(0xFFEFE8DD);
  static const ink = Color(0xFF141312); // cartes « Entre vous »
  static const onInk = Color(0xFFFFFFFF);

  // Marque
  static const red = Color(0xFFCE0E2E);
  static const redPressed = Color(0xFFAE0B27);
  static const onRed = Color(0xFFFFFFFF);

  // Texte
  static const textPrimary = Color(0xFF12100E);
  static const textSecondary = Color(0xFF6E665C);
  static const textMuted = Color(0xFF9A9186);
  static const onInkMuted = Color(0xFFA79E93);

  // Traits
  static const border = Color(0xFFE2D9CB);
  static const divider = Color(0xFFE7DFD2);
}

/// Couleurs de concept, telles qu'utilisées sur les barres de carte et les
/// pastilles de la page d'accueil.
class CapConceptColors {
  const CapConceptColors._();

  static const rapido = Color(0xFF1E7F4B);
  static const dilemme = Color(0xFFD79A22);
  static const confession = Color(0xFF3D6FD1);
  static const sauveMoi = Color(0xFF7B4FCB);
}

/// Typographie : une grotesque très grasse pour les titres, la même famille en
/// régulier pour le texte courant, et des intertitres en petites capitales
/// espacées.
///
/// TODO(DA) : déposer les fichiers de la police du site dans assets/fonts/,
/// les déclarer dans pubspec.yaml, puis renseigner [fontFamily]. Tant que
/// c'est `null`, la police système prend le relais avec les mêmes graisses.
class CapType {
  const CapType._();

  static const String? fontFamily = null;

  /// Titre de page (« Sauve-moi si tu peux », « Concepts. »).
  static const display = TextStyle(
    fontFamily: fontFamily,
    fontSize: 40,
    height: 1.05,
    fontWeight: FontWeight.w800,
    letterSpacing: -1.2,
  );

  /// Titre de section du site (« Entre vous. »).
  static const title = TextStyle(
    fontFamily: fontFamily,
    fontSize: 30,
    height: 1.12,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.8,
  );

  /// Consigne d'une carte, nom d'un concept dans la grille.
  static const heading = TextStyle(
    fontFamily: fontFamily,
    fontSize: 23,
    height: 1.28,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.4,
  );

  static const body = TextStyle(
    fontFamily: fontFamily,
    fontSize: 16,
    height: 1.55,
    fontWeight: FontWeight.w400,
  );

  static const bodyStrong = TextStyle(
    fontFamily: fontFamily,
    fontSize: 16,
    height: 1.55,
    fontWeight: FontWeight.w700,
  );

  /// Ligne de méta sous une carte (« 36 cartes gratuites · … »).
  static const meta = TextStyle(
    fontFamily: fontFamily,
    fontSize: 14,
    height: 1.4,
    fontWeight: FontWeight.w400,
  );

  /// Intertitre en capitales espacées (« CHOISIS TON CONCEPT »).
  static const eyebrow = TextStyle(
    fontFamily: fontFamily,
    fontSize: 12.5,
    height: 1.3,
    fontWeight: FontWeight.w600,
    letterSpacing: 1.6,
  );

  /// Code de carte en bas à droite (« RAP · 626 »).
  static const cardCode = TextStyle(
    fontFamily: fontFamily,
    fontSize: 12,
    height: 1.2,
    fontWeight: FontWeight.w500,
    letterSpacing: 1.2,
  );

  static const button = TextStyle(
    fontFamily: fontFamily,
    fontSize: 17,
    height: 1.2,
    fontWeight: FontWeight.w700,
  );

  static const link = TextStyle(
    fontFamily: fontFamily,
    fontSize: 16,
    height: 1.3,
    fontWeight: FontWeight.w700,
  );
}

class CapSpacing {
  const CapSpacing._();

  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 16.0;
  static const lg = 24.0;
  static const xl = 32.0;
  static const xxl = 48.0;
}

class CapRadius {
  const CapRadius._();

  static const card = Radius.circular(18);
  static const button = Radius.circular(12);
  static const chip = Radius.circular(999);

  static const cardAll = BorderRadius.all(card);
  static const buttonAll = BorderRadius.all(button);
  static const chipAll = BorderRadius.all(chip);

  /// Largeur de la barre de couleur à gauche des cartes de concept.
  static const accentBar = 6.0;
}

class CapShadows {
  const CapShadows._();

  /// Ombre discrète et chaude des cartes du site.
  static const card = <BoxShadow>[
    BoxShadow(color: Color(0x14000000), blurRadius: 18, offset: Offset(0, 6)),
  ];
  static const deck = <BoxShadow>[
    BoxShadow(color: Color(0x1F000000), blurRadius: 28, offset: Offset(0, 10)),
  ];
}

class CapMotion {
  const CapMotion._();

  static const fast = Duration(milliseconds: 140);
  static const normal = Duration(milliseconds: 240);
  static const curve = Curves.easeOutCubic;
}
