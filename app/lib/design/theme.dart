import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'tokens.dart';

/// Thème Material dérivé des tokens.
///
/// Les écrans passent par les composants de `lib/design/components/` ; ce
/// thème n'est là que pour les widgets Material embarqués (dialogues,
/// snackbars, curseur de saisie) restent dans la DA.
ThemeData buildCapTheme() {
  const scheme = ColorScheme.light(
    primary: CapColors.red,
    onPrimary: CapColors.onRed,
    secondary: CapColors.textPrimary,
    onSecondary: CapColors.background,
    surface: CapColors.surface,
    onSurface: CapColors.textPrimary,
    error: CapColors.red,
    onError: CapColors.onRed,
  );

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: CapColors.background,
    fontFamily: CapType.fontFamily,
    textSelectionTheme: const TextSelectionThemeData(
      cursorColor: CapColors.red,
      selectionHandleColor: CapColors.red,
    ),
    textTheme: const TextTheme(
      displayLarge: CapType.display,
      titleLarge: CapType.title,
      titleMedium: CapType.heading,
      bodyLarge: CapType.body,
      bodyMedium: CapType.body,
      labelLarge: CapType.button,
      labelSmall: CapType.eyebrow,
    ).apply(
      bodyColor: CapColors.textPrimary,
      displayColor: CapColors.textPrimary,
    ),
    dialogTheme: const DialogThemeData(
      backgroundColor: CapColors.surface,
      shape: RoundedRectangleBorder(borderRadius: CapRadius.cardAll),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: CapColors.ink,
      contentTextStyle: CapType.body.copyWith(color: CapColors.onInk),
      behavior: SnackBarBehavior.floating,
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? CapColors.red
            : CapColors.textMuted,
      ),
    ),
  );
}

/// Le fond crème monte jusque sous la barre de statut : icônes sombres.
const capSystemOverlay = SystemUiOverlayStyle(
  statusBarColor: Color(0x00000000),
  statusBarIconBrightness: Brightness.dark,
  statusBarBrightness: Brightness.light,
  systemNavigationBarColor: CapColors.background,
  systemNavigationBarIconBrightness: Brightness.dark,
);
