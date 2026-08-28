import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'tokens.dart';

/// Construit le [ThemeData] Material à partir des tokens.
///
/// Les écrans n'utilisent presque jamais ce thème directement : ils passent par
/// les composants de `lib/design/components/`. Il est là pour que les widgets
/// Material embarqués (dialogs, snackbars, sélection de texte) restent cohérents
/// avec la DA.
ThemeData buildCapTheme() {
  const scheme = ColorScheme.dark(
    primary: CapColors.primary,
    onPrimary: CapColors.onPrimary,
    secondary: CapColors.secondary,
    onSecondary: CapColors.background,
    surface: CapColors.surface,
    onSurface: CapColors.textPrimary,
    error: CapColors.danger,
    onError: CapColors.onPrimary,
  );

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: CapColors.background,
    fontFamily: CapType.fontFamily,
    splashFactory: InkSparkle.splashFactory,
    textTheme: const TextTheme(
      displayLarge: CapType.display,
      titleLarge: CapType.title,
      titleMedium: CapType.heading,
      bodyLarge: CapType.body,
      bodyMedium: CapType.body,
      labelLarge: CapType.button,
      labelSmall: CapType.caption,
    ).apply(
      bodyColor: CapColors.textPrimary,
      displayColor: CapColors.textPrimary,
    ),
    dialogTheme: const DialogThemeData(
      backgroundColor: CapColors.surfaceRaised,
      shape: RoundedRectangleBorder(borderRadius: CapRadius.lgAll),
    ),
    snackBarTheme: const SnackBarThemeData(
      backgroundColor: CapColors.surfaceRaised,
      contentTextStyle: CapType.body,
      behavior: SnackBarBehavior.floating,
    ),
    sliderTheme: const SliderThemeData(
      activeTrackColor: CapColors.primary,
      thumbColor: CapColors.primary,
      inactiveTrackColor: CapColors.border,
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? CapColors.primary
            : CapColors.textMuted,
      ),
    ),
  );
}

/// Barre de statut et barre de navigation transparentes, icônes claires.
const capSystemOverlay = SystemUiOverlayStyle(
  statusBarColor: Color(0x00000000),
  statusBarIconBrightness: Brightness.light,
  statusBarBrightness: Brightness.dark,
  systemNavigationBarColor: CapColors.background,
  systemNavigationBarIconBrightness: Brightness.light,
);
