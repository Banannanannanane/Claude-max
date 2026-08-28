import 'package:ca_part/app.dart';
import 'package:ca_part/data/games_repository.dart';
import 'package:ca_part/models/game.dart';
import 'package:ca_part/services/settings_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Le catalogue et les préférences sont chargés dans [setUpAll], hors du corps
/// des tests : `rootBundle.loadString` et `SharedPreferences.getInstance` sont
/// de l'asynchrone réel, qui ne progresse pas dans la zone `fakeAsync` d'un
/// `testWidgets`.
late final List<GameDefinition> games;
late final SettingsService settings;

void main() {
  setUpAll(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
    games = await GamesRepository().load();
    settings = await SettingsService.load();
  });

  testWidgets('le catalogue livré est valide et s\'affiche', (tester) async {
    // Une entrée malformée de assets/games/index.json ferait échouer setUpAll
    // avant d'atteindre un appareil.
    expect(games, isNotEmpty);

    await tester.pumpWidget(CaPartApp(settings: settings, games: games));
    await tester.pumpAndSettle();

    expect(find.text('Ça part !'), findsOneWidget);
    for (final game in games) {
      expect(find.text(game.name), findsOneWidget);
    }
  });

  testWidgets('ouvrir un concept mène à ses règles puis aux joueurs',
      (tester) async {
    await tester.pumpWidget(CaPartApp(settings: settings, games: games));
    await tester.pumpAndSettle();

    final game = games.first;
    await tester.tap(find.text(game.name));
    await tester.pumpAndSettle();

    expect(find.text('Lancer la partie'), findsOneWidget);
    if (game.rules.isNotEmpty) {
      expect(find.text('Comment on joue'), findsOneWidget);
    }

    await tester.tap(find.text('Lancer la partie'));
    await tester.pumpAndSettle();

    if (game.needsPlayerNames) {
      expect(find.text('Les joueurs'), findsOneWidget);
      // Il faut atteindre le minimum de joueurs pour que le bouton s'active.
      expect(find.text('C\'est parti'), findsOneWidget);
    }
  });
}
