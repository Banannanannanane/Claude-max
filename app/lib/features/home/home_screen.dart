import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';
import '../../models/game.dart';

/// La liste des concepts — l'écran d'accueil, équivalent de `/concepts`.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.games});

  final List<GameDefinition> games;

  @override
  Widget build(BuildContext context) {
    return CapScaffold(
      padded: false,
      child: CustomScrollView(
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(
              CapSpacing.lg,
              CapSpacing.xl,
              CapSpacing.lg,
              CapSpacing.lg,
            ),
            sliver: SliverToBoxAdapter(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Ça part !', style: CapType.display),
                        const SizedBox(height: CapSpacing.sm),
                        Text(
                          'Choisissez un concept, passez le téléphone.',
                          style: CapType.body
                              .copyWith(color: CapColors.textSecondary),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: () => context.pushNamed('settings'),
                    icon: const Icon(Icons.settings_rounded),
                    color: CapColors.textSecondary,
                    tooltip: 'Réglages',
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(
              CapSpacing.lg,
              0,
              CapSpacing.lg,
              CapSpacing.xxl,
            ),
            sliver: SliverList.separated(
              itemCount: games.length,
              separatorBuilder: (_, _) => const SizedBox(height: CapSpacing.md),
              itemBuilder: (context, index) => _GameTile(game: games[index]),
            ),
          ),
        ],
      ),
    );
  }
}

class _GameTile extends StatelessWidget {
  const _GameTile({required this.game});

  final GameDefinition game;

  @override
  Widget build(BuildContext context) {
    return CapCard(
      onTap: () => context.pushNamed('concept', pathParameters: {'id': game.id}),
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            height: 6,
            decoration: BoxDecoration(
              color: game.accent,
              borderRadius: const BorderRadius.vertical(top: CapRadius.lg),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(CapSpacing.md),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 52,
                  height: 52,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(

                    color: game.accent.withValues(alpha: 0.16),
                    borderRadius: CapRadius.mdAll,
                  ),
                  child: Text(game.emoji, style: const TextStyle(fontSize: 26)),
                ),
                const SizedBox(width: CapSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(game.name, style: CapType.heading),
                      if (game.tagline.isNotEmpty) ...[
                        const SizedBox(height: CapSpacing.xs),
                        Text(
                          game.tagline,
                          style: CapType.body
                              .copyWith(color: CapColors.textSecondary),
                        ),
                      ],
                      const SizedBox(height: CapSpacing.sm + 2),
                      Wrap(
                        spacing: CapSpacing.sm,
                        runSpacing: CapSpacing.xs,
                        children: [
                          CapTag(
                            label: game.playersLabel,
                            icon: Icons.group_rounded,
                          ),
                          CapTag(
                            label: game.durationLabel,
                            icon: Icons.schedule_rounded,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
