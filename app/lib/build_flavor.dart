/// Variante de build.
///
/// Deux applications sont produites à partir du même code : « Ça Part » et
/// « Ça Part test ». Elles ont des identifiants Android distincts, donc elles
/// s'installent côte à côte sur le même téléphone — c'est tout l'intérêt : on
/// garde la version qui tourne pendant qu'on essaie l'autre.
///
/// La valeur vient du build : `--dart-define=FLAVOR=dev`, posé
/// automatiquement par les workflows.
class BuildFlavor {
  const BuildFlavor._();

  static const name = String.fromEnvironment('FLAVOR', defaultValue: 'prod');

  static bool get isTest => name != 'prod';

  /// Nom affiché dans l'app. Le nom sous l'icône vient, lui, du `resValue`
  /// Android correspondant.
  static String get appName => isTest ? 'Ça Part test' : 'Ça Part';
}
