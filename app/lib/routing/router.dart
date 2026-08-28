import 'package:go_router/go_router.dart';

import '../app.dart';
import '../features/concepts/concept_detail_screen.dart';
import '../features/concepts/concepts_screen.dart';
import '../features/entre_vous/entre_vous_screen.dart';
import '../features/home/home_screen.dart';
import '../features/infos/infos_screen.dart';
import '../features/play/play_screen.dart';
import '../features/play/players_screen.dart';

/// Les routes reprennent les URLs du site, pour que le build web de l'app
/// reste partageable par lien.
GoRouter buildRouter() {
  return GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(
        path: '/',
        name: 'accueil',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: '/concepts',
        name: 'concepts',
        builder: (context, state) => const ConceptsScreen(),
        routes: [
          GoRoute(
            path: ':id',
            name: 'concept',
            builder: (context, state) => ConceptDetailScreen(
              concept: AppScope.of(context).conceptById(state.pathParameters['id']!),
            ),
          ),
        ],
      ),
      GoRoute(
        path: '/melange',
        name: 'melange',
        builder: (context, state) => const MixDetailScreen(),
      ),
      GoRoute(
        path: '/prenoms',
        name: 'prenoms',
        builder: (context, state) => const PlayersScreen(),
      ),
      GoRoute(
        path: '/partie',
        name: 'partie',
        builder: (context, state) {
          final ids = (state.extra as List<String>?) ?? const <String>[];
          return PlayScreen(conceptIds: ids);
        },
      ),
      GoRoute(
        path: '/entre-vous',
        name: 'entre-vous',
        builder: (context, state) => const EntreVousScreen(),
      ),
      GoRoute(
        path: '/infos',
        name: 'infos',
        builder: (context, state) => const InfosScreen(),
      ),
    ],
  );
}
