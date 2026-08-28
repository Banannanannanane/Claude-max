import 'package:ca_part/app.dart';
import 'package:ca_part/data/concepts_repository.dart';
import 'package:ca_part/models/concept.dart';
import 'package:ca_part/services/purchase_service.dart';
import 'package:ca_part/services/settings_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Le catalogue et les préférences sont chargés dans [setUpAll], hors du corps
/// des tests : `rootBundle.loadString` et `SharedPreferences.getInstance` sont
/// de l'asynchrone réel, qui ne progresse pas dans la zone `fakeAsync` d'un
/// `testWidgets`.
late final List<Concept> concepts;
late final SettingsService settings;
late final PurchaseService purchases;

void main() {
  setUpAll(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
    concepts = await ConceptsRepository().load();
    settings = await SettingsService.load();
    purchases = PurchaseService(settings);
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
    expect(dilemme.free, isTrue, reason: 'Dilemme est gratuit sur le site');
    expect(dilemme.metaLabel,
        '120 cartes gratuites · 12 tirées par partie');

    final rapido = concepts.firstWhere((c) => c.id == 'rapido');
    expect(rapido.timerSeconds, 15);
    expect(rapido.metaLabel,
        '37 cartes gratuites · 67 de plus · 12 tirées par partie');

    final sauveMoi = concepts.firstWhere((c) => c.id == 'sauve-moi-si-tu-peux');
    expect(sauveMoi.readerCount, 2,
        reason: 'un joueur lit, un autre défend');
    expect(sauveMoi.timerSeconds, 20);
  });

  testWidgets('l\'accueil mène à la grille des concepts', (tester) async {
    await tester.pumpWidget(CaPartApp(
      settings: settings,
      purchases: purchases,
      concepts: concepts,
    ));
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

  testWidgets('la fiche d\'un concept payant propose le déblocage',
      (tester) async {
    await tester.pumpWidget(CaPartApp(
      settings: settings,
      purchases: purchases,
      concepts: concepts,
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Lancer une partie'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Rapido').last);
    await tester.pumpAndSettle();

    expect(find.text('Lancer'), findsOneWidget);
    expect(find.text('Débloquer Rapido · 3,99 €'), findsOneWidget);
    expect(find.text('J\'ai déjà acheté'), findsOneWidget);
    expect(find.text('Changer les prénoms'), findsOneWidget);
    expect(
      find.text('37 cartes gratuites · 67 de plus · 12 tirées par partie'),
      findsOneWidget,
    );
  });

  testWidgets('un concept gratuit se lance sans passer par l\'achat',
      (tester) async {
    await tester.pumpWidget(CaPartApp(
      settings: settings,
      purchases: purchases,
      concepts: concepts,
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Lancer une partie'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dilemme').last);
    await tester.pumpAndSettle();

    expect(find.textContaining('Débloquer'), findsNothing);

    await tester.tap(find.text('Lancer'));
    await tester.pumpAndSettle();

    expect(find.text('Carte suivante'), findsOneWidget);
    expect(find.text('1 / 12'), findsOneWidget);
  });
}
