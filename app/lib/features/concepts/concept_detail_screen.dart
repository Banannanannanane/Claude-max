import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app.dart';
import '../../design/components/cap_button.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';
import '../../models/concept.dart';

/// La fiche d'un concept : sa consigne, son exemple, ses compteurs, et les
/// deux chemins possibles — lancer une partie ou débloquer le paquet complet.
class ConceptDetailScreen extends StatelessWidget {
  const ConceptDetailScreen({super.key, required this.concept});

  final Concept concept;

  @override
  Widget build(BuildContext context) {
    return CapScaffold(
      title: concept.name,
      onBack: () => context.pop(),
      bottomBar: CapButton(
        label: 'Lancer',
        onPressed: () => _start(context),
      ),
      child: ListView(
        padding: const EdgeInsets.only(bottom: CapSpacing.lg),
        children: [
          Text(concept.name, style: CapType.display),
          const SizedBox(height: CapSpacing.lg),
          CapCard(
            accent: concept.color,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(concept.instruction, style: CapType.heading),
                if (concept.example != null) ...[
                  const SizedBox(height: CapSpacing.lg),
                  const Divider(color: CapColors.divider, height: 1),
                  const SizedBox(height: CapSpacing.lg),
                  Text.rich(
                    TextSpan(
                      children: [
                        TextSpan(text: 'Exemple', style: CapType.bodyStrong),
                        TextSpan(
                          text: ' · ${concept.example!.body}',
                          style: CapType.body
                              .copyWith(color: CapColors.textSecondary),
                        ),
                      ],
                    ),
                  ),
                ],
                const SizedBox(height: CapSpacing.md),
                const CapCardMark(),
              ],
            ),
          ),
          const SizedBox(height: CapSpacing.sm + 2),
          Text(
            concept.metaLabel,
            style: CapType.meta.copyWith(color: CapColors.textSecondary),
          ),
          const SizedBox(height: CapSpacing.xs),
          CapLink(
            label: 'Changer les prénoms',
            onPressed: () => context.pushNamed('prenoms'),
          ),
        ],
      ),
    );
  }

  void _start(BuildContext context) => context.pushNamed(
        'partie',
        extra: <String>[concept.id],
      );
}

/// « Envie de tout ? Mélange les concepts. » — même fiche, mais le paquet est
/// tiré dans tous les concepts jouables.
class MixDetailScreen extends StatelessWidget {
  const MixDetailScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final concepts = AppScope.of(context).concepts;

    return CapScaffold(
      title: 'Mélange',
      onBack: () => context.pop(),
      bottomBar: CapButton(
        label: 'Lancer',
        onPressed: concepts.isEmpty
            ? null
            : () => context.pushNamed(
                  'partie',
                  extra: concepts.map((c) => c.id).toList(),
                ),
      ),
      child: ListView(
        padding: const EdgeInsets.only(bottom: CapSpacing.lg),
        children: [
          Text('Mélange les concepts', style: CapType.display),
          const SizedBox(height: CapSpacing.lg),
          CapCard(
            accent: CapColors.red,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Tous les formats dans le même paquet. Le téléphone tourne, '
                  'la consigne change à chaque carte.',
                  style: CapType.heading,
                ),
                const SizedBox(height: CapSpacing.md),
                const CapCardMark(),
              ],
            ),
          ),
          const SizedBox(height: CapSpacing.md),
          const CapEyebrow('Dans le paquet'),
          const SizedBox(height: CapSpacing.sm),
          for (final concept in concepts)
            _MixRow(name: concept.name, color: concept.color),
        ],
      ),
    );
  }
}

class _MixRow extends StatelessWidget {
  const _MixRow({required this.name, required this.color});

  final String name;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: CapSpacing.sm),
      child: Row(
        children: [
          Container(
            width: 9,
            height: 9,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: CapSpacing.sm + 2),
          Expanded(child: Text(name, style: CapType.body)),
          Icon(Icons.check_rounded, size: 18, color: color),
        ],
      ),
    );
  }
}
