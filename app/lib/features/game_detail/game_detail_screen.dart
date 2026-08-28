import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../design/components/cap_button.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';
import '../../models/game.dart';

/// La fiche d'un concept : présentation, règles, bouton pour lancer la partie.
class GameDetailScreen extends StatelessWidget {
  const GameDetailScreen({super.key, required this.game});

  final GameDefinition game;

  void _start(BuildContext context) {
    if (game.needsPlayerNames) {
      context.pushNamed('players', pathParameters: {'id': game.id});
    } else {
      context.pushNamed(
        'play',
        pathParameters: {'id': game.id},
        extra: const <String>[],
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return CapScaffold(
      title: game.name,
      onBack: () => context.pop(),
      bottomBar: CapButton(
        label: 'Lancer la partie',
        icon: Icons.play_arrow_rounded,
        size: CapButtonSize.large,
        onPressed: () => _start(context),
      ),
      child: ListView(
        padding: const EdgeInsets.only(bottom: CapSpacing.lg),
        children: [
          CapCard(

            color: game.accent.withValues(alpha: 0.14),
            child: Row(
              children: [
                Text(game.emoji, style: const TextStyle(fontSize: 40)),
                const SizedBox(width: CapSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(game.name, style: CapType.title),
                      if (game.tagline.isNotEmpty) ...[
                        const SizedBox(height: CapSpacing.xs),
                        Text(
                          game.tagline,
                          style: CapType.body
                              .copyWith(color: CapColors.textSecondary),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: CapSpacing.md),
          Row(
            children: [
              CapTag(label: game.playersLabel, icon: Icons.group_rounded),
              const SizedBox(width: CapSpacing.sm),
              CapTag(label: game.durationLabel, icon: Icons.schedule_rounded),
              if (game.engine == GameEngine.timedRound) ...[
                const SizedBox(width: CapSpacing.sm),
                CapTag(
                  label: '${game.roundSeconds} s / manche',
                  icon: Icons.timer_rounded,
                ),
              ],
            ],
          ),
          if (game.description.isNotEmpty) ...[
            const SizedBox(height: CapSpacing.lg),
            Text(
              game.description,
              style: CapType.body.copyWith(color: CapColors.textSecondary),
            ),
          ],
          if (game.rules.isNotEmpty) ...[
            const SizedBox(height: CapSpacing.lg),
            Text('Comment on joue', style: CapType.heading),
            const SizedBox(height: CapSpacing.md),
            for (var i = 0; i < game.rules.length; i++)
              Padding(
                padding: const EdgeInsets.only(bottom: CapSpacing.md),
                child: _RuleStep(index: i + 1, text: game.rules[i], accent: game.accent),
              ),
          ],
        ],
      ),
    );
  }
}

class _RuleStep extends StatelessWidget {
  const _RuleStep({required this.index, required this.text, required this.accent});

  final int index;
  final String text;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 28,
          height: 28,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
          child: Text(
            '$index',
            style: CapType.caption.copyWith(
              color: CapColors.onPrimary,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
        const SizedBox(width: CapSpacing.md),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.only(top: 3),
            child: Text(
              text,
              style: CapType.body.copyWith(color: CapColors.textSecondary),
            ),
          ),
        ),
      ],
    );
  }
}
