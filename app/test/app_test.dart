import 'package:ca_part/app.dart';
import 'package:ca_part/data/concepts_repository.dart';
import 'package:ca_part/models/concept.dart';
import 'package:ca_part/services/settings_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Le catalogue et les préférences sont chargés dans [setUpAll], hors du corps
/// des tests : `rootBundle.loadString` et `SharedPreferences.getInstance` sont
/// de l'asynchrone réel, qui ne progresse pas dans la zone `fakeAsync` d'un
/// `testWidgets`.
late final List<Concept> concepts;
late final SettingsService settings;

void main() {
  setUpAll(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
    concepts = await ConceptsRepository().load();
    settings = await SettingsService.load();
  });

  testWidgets('le catalogue livré est celui du site', (tester) async {
    expect(
      concepts.map((c) => c.id),
      containsAll(<String>[
        'rapido',
        'dilemme',
        'confession',
        'sauve-moi-si-tu-peux',
      ]),
    );

    final dilemme = concepts.firstWhere((c) => c.id == 'dilemme');
    expect(dilemme.metaLabel, '120 cartes · 12 tirées par partie');

    final rapido = concepts.firstWhere((c) => c.id == 'rapido');
    expect(rapido.timerSeconds, 15);
    expect(rapido.metaLabel, '104 cartes · 12 tirées par partie');

    // Chaque concept livre autant d'emplacements que le paquet annoncé.
    for (final concept in concepts) {
      expect(concept.cards, hasLength(concept.cardCount));
    }

    final sauveMoi = concepts.firstWhere((c) => c.id == 'sauve-moi-si-tu-peux');
    expect(sauveMoi.readerCount, 2,
        reason: 'un joueur lit, un autre défend');
    expect(sauveMoi.timerSeconds, 20);
  });

  testWidgets('l\'accueil mène à la grille des concepts', (tester) async {
    await tester.pumpWidget(
      CaPartApp(settings: settings, concepts: concepts),
    );
    await tester.pumpAndSettle();

    // Le logo est un Text.rich (le point est rouge) : on vise la ligne
    // d'accroche, qui est un Text simple.
    expect(
      find.text('On fournit les jeux.\nVous fournissez les problèmes.'),
      findsOneWidget,
    );
    await tester.tap(find.text('Lancer une partie'));
    await tester.pumpAndSettle();

    expect(find.text('CHOISIS TON CONCEPT'), findsOneWidget);
    for (final concept in concepts) {
      expect(find.text(concept.name), findsWidgets);
    }
  });

  testWidgets('la fiche d\'un concept ne propose que de lancer',
      (tester) async {
    await tester.pumpWidget(
      CaPartApp(settings: settings, concepts: concepts),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Lancer une partie'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Rapido').last);
    await tester.pumpAndSettle();

    expect(find.text('Lancer'), findsOneWidget);
    expect(find.textContaining('Débloquer'), findsNothing);
    expect(find.textContaining('acheté'), findsNothing);
    expect(find.text('Changer les prénoms'), findsOneWidget);
    expect(find.text('104 cartes · 12 tirées par partie'), findsOneWidget);
  });

  testWidgets('une partie démarre sur les emplacements de cartes',
      (tester) async {
    await tester.pumpWidget(
      CaPartApp(settings: settings, concepts: concepts),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Lancer une partie'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dilemme').last);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Lancer'));
    await tester.pumpAndSettle();

    expect(find.text('Carte suivante'), findsOneWidget);
    expect(find.text('1 / 12'), findsOneWidget);
    // Les cartes livrées sont des emplacements, pas du contenu.
    expect(find.text('(à définir)'), findsOneWidget);
  });
}
