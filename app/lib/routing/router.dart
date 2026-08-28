import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';

import '../data/games_repository.dart';
import '../features/game_detail/game_detail_screen.dart';
import '../features/home/home_screen.dart';
import '../features/play/play_screen.dart';
import '../features/play/players_screen.dart';
import '../features/settings/settings_screen.dart';
import '../models/game.dart';

/// Les routes reprennent les URLs de la web app (`/concepts`, `/concepts/:id`)
/// pour que la version web du build reste partageable par lien.
GoRouter buildRouter({required List<GameDefinition> games}) {
  GameDefinition gameById(String id) => games.firstWhere(
        (g) => g.id == id,
        orElse: () => throw GoException('Jeu inconnu : $id'),
      );

  return GoRouter(
    initialLocation: '/concepts',
    routes: [
      GoRoute(
        path: '/concepts',
        name: 'concepts',
        builder: (context, state) => HomeScreen(games: games),
        routes: [
          GoRoute(
            path: ':id',
            name: 'concept',
            builder: (context, state) =>
                GameDetailScreen(game: gameById(state.pathParameters['id']!)),
            routes: [
              GoRoute(
                path: 'joueurs',
                name: 'players',
                builder: (context, state) =>
                    PlayersScreen(game: gameById(state.pathParameters['id']!)),
              ),
              GoRoute(
                path: 'partie',
                name: 'play',
                builder: (context, state) {
                  final game = gameById(state.pathParameters['id']!);
                  final players =
                      (state.extra as List<String>?) ?? const <String>[];
                  if (game.engine == GameEngine.custom &&
                      game.customBuilder != null) {
                    return Builder(builder: game.customBuilder!);
                  }
                  return PlayScreen(game: game, players: players);
                },
              ),
            ],
          ),
        ],
      ),
      GoRoute(
        path: '/reglages',
        name: 'settings',
        builder: (context, state) => const SettingsScreen(),
      ),
    ],
  );
}

/// Exposé pour que les écrans accèdent au dépôt sans injection de dépendances
/// lourde.
final gamesRepository = GamesRepository();
