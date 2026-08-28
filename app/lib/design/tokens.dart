import 'package:flutter/widgets.dart';

/// Design tokens — la DA de l'application vit ici et nulle part ailleurs.
///
/// Reprendre la direction artistique de la web app revient à remplacer les
/// valeurs de ce fichier (couleurs, typo, rayons, ombres, espacements) : aucun
/// écran ne code de couleur ou de taille en dur.
///
/// TODO(DA): remplacer par les valeurs extraites du CSS de la web app
/// (variables `--*` de :root, `font-family`, `border-radius`, `box-shadow`).
class CapColors {
  const CapColors._();

  // Fond
  static const background = Color(0xFF0E0B1A);
  static const surface = Color(0xFF1A1430);
  static const surfaceRaised = Color(0xFF241C42);
  static const overlay = Color(0xCC0E0B1A);

  // Marque
  static const primary = Color(0xFFFF4D6D);
  static const primaryPressed = Color(0xFFE03A57);
  static const onPrimary = Color(0xFFFFFFFF);
  static const secondary = Color(0xFF4DD9C0);
  static const accent = Color(0xFFFFC857);

  // Texte
  static const textPrimary = Color(0xFFFFFFFF);
  static const textSecondary = Color(0xB3FFFFFF);
  static const textMuted = Color(0x80FFFFFF);

  // États
  static const border = Color(0x1FFFFFFF);
  static const danger = Color(0xFFFF5A5A);
  static const success = Color(0xFF4ADE80);
}

/// Famille typographique. Tant que la police de la web app n'est pas embarquée
/// dans `assets/fonts/`, `null` laisse Flutter utiliser la police système.
class CapType {
  const CapType._();

  /// TODO(DA): déclarer la police dans pubspec.yaml puis mettre son nom ici.
  static const String? fontFamily = null;

  static const display = TextStyle(
    fontFamily: fontFamily,
    fontSize: 40,
    height: 1.1,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.5,
  );
  static const title = TextStyle(
    fontFamily: fontFamily,
    fontSize: 26,
    height: 1.2,
    fontWeight: FontWeight.w700,
  );
  static const heading = TextStyle(
    fontFamily: fontFamily,
    fontSize: 20,
    height: 1.25,
    fontWeight: FontWeight.w700,
  );
  static const body = TextStyle(
    fontFamily: fontFamily,
    fontSize: 16,
    height: 1.45,
    fontWeight: FontWeight.w400,
  );
  static const bodyStrong = TextStyle(
    fontFamily: fontFamily,
    fontSize: 16,
    height: 1.45,
    fontWeight: FontWeight.w600,
  );
  static const caption = TextStyle(
    fontFamily: fontFamily,
    fontSize: 13,
    height: 1.3,
    fontWeight: FontWeight.w500,
    letterSpacing: 0.2,
  );
  static const button = TextStyle(
    fontFamily: fontFamily,
    fontSize: 16,
    height: 1.2,
    fontWeight: FontWeight.w700,
    letterSpacing: 0.3,
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

  static const sm = Radius.circular(8);
  static const md = Radius.circular(16);
  static const lg = Radius.circular(24);
  static const pill = Radius.circular(999);

  static const smAll = BorderRadius.all(sm);
  static const mdAll = BorderRadius.all(md);
  static const lgAll = BorderRadius.all(lg);
  static const pillAll = BorderRadius.all(pill);
}

class CapShadows {
  const CapShadows._();

  static const card = <BoxShadow>[
    BoxShadow(color: Color(0x40000000), blurRadius: 24, offset: Offset(0, 8)),
  ];
  static const raised = <BoxShadow>[
    BoxShadow(color: Color(0x59000000), blurRadius: 32, offset: Offset(0, 12)),
  ];
}

class CapMotion {
  const CapMotion._();

  static const fast = Duration(milliseconds: 150);
  static const normal = Duration(milliseconds: 250);
  static const slow = Duration(milliseconds: 400);
  static const curve = Curves.easeOutCubic;
}
