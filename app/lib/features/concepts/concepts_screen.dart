import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';
import '../../models/concept.dart';

/// « CHOISIS TON CONCEPT » — la grille de la page /concepts.
class ConceptsScreen extends StatelessWidget {
  const ConceptsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final concepts = AppScope.of(context).concepts;

    return CapScaffold(
      showWordmark: true,
      child: ListView(
        padding: const EdgeInsets.symmetric(vertical: CapSpacing.lg),
        children: [
          const CapEyebrow('Choisis ton concept'),
          const SizedBox(height: CapSpacing.md),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: concepts.length,
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              mainAxisSpacing: CapSpacing.md,
              crossAxisSpacing: CapSpacing.md,
              childAspectRatio: 1.15,
            ),
            itemBuilder: (context, i) => _ConceptTile(concept: concepts[i]),
          ),
          const SizedBox(height: CapSpacing.md),
          CapCard(
            bordered: true,
            background: CapColors.surfaceSunken,
            padding: const EdgeInsets.symmetric(
              horizontal: CapSpacing.md,
              vertical: CapSpacing.md + 2,
            ),
            onTap: () => context.pushNamed('melange'),
            child: Row(
              children: [
                Expanded(
                  child: Text.rich(
                    TextSpan(
                      children: [
                        TextSpan(text: 'Envie de tout ? ', style: CapType.body),
                        TextSpan(
                          text: 'Mélange les concepts.',
                          style: CapType.body
                              .copyWith(color: CapColors.textSecondary),
                        ),
                      ],
                    ),
                  ),
                ),
                const Icon(Icons.chevron_right_rounded,
                    size: 22, color: CapColors.textMuted),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ConceptTile extends StatelessWidget {
  const _ConceptTile({required this.concept});

  final Concept concept;

  @override
  Widget build(BuildContext context) {
    return CapCard(
      accent: concept.color,
      padding: const EdgeInsets.all(CapSpacing.md),
      onTap: () =>
          context.pushNamed('concept', pathParameters: {'id': concept.id}),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const CapEyebrow('Concept'),
          const Spacer(),
          Text(
            concept.name,
            style: CapType.heading.copyWith(fontSize: 21),
          ),
        ],
      ),
    );
  }
}
