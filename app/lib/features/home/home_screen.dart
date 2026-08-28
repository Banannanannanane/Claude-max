import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app.dart';
import '../../design/components/cap_button.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';
import '../../models/concept.dart';

/// L'accueil, repris de la page d'accueil du site.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _previewIndex = 0;

  @override
  Widget build(BuildContext context) {
    final concepts = AppScope.of(context).concepts;
    final preview = concepts[_previewIndex.clamp(0, concepts.length - 1)];
    final previewCard = preview.cards.isEmpty ? null : preview.cards.first;

    return CapScaffold(
      showWordmark: true,
      padded: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(
          CapSpacing.lg,
          CapSpacing.xl,
          CapSpacing.lg,
          CapSpacing.xxl,
        ),
        children: [
          Text(
            'On fournit les jeux.\nVous fournissez les problèmes.',
            textAlign: TextAlign.center,
            style: CapType.display,
          ),
          const SizedBox(height: CapSpacing.lg),
          CapButton(
            label: 'Lancer une partie',
            onPressed: () => context.pushNamed('concepts'),
          ),
          const SizedBox(height: CapSpacing.xxl),

          _SectionCard(
            eyebrow: 'Concepts',
            title: 'Des formats prêts\nà jouer.',
            action: 'Tu choisis, on lance.',
            onTap: () => context.pushNamed('concepts'),
          ),
          const SizedBox(height: CapSpacing.md),
          _SectionCard(
            eyebrow: 'Entre vous',
            title: 'Vous écrivez\nles cartes.',
            action: 'Le narrateur les révèle.',
            onTap: () => context.pushNamed('entre-vous'),
          ),

          const SizedBox(height: CapSpacing.xxl),
          Text('Concepts.', style: CapType.title),
          const SizedBox(height: CapSpacing.md),
          Wrap(
            spacing: CapSpacing.sm,
            runSpacing: CapSpacing.sm,
            children: [
              for (var i = 0; i < concepts.length; i++)
                _ConceptChip(
                  concept: concepts[i],
                  selected: i == _previewIndex,
                  onTap: () => setState(() => _previewIndex = i),
                ),
            ],
          ),
          const SizedBox(height: CapSpacing.md),
          if (previewCard != null)
            CapCard(
              accent: preview.color,
              onTap: () => context.pushNamed(
                'concept',
                pathParameters: {'id': preview.id},
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(child: CapEyebrow(preview.name)),
                      Text(
                        '1/${preview.cards.length}',
                        style: CapType.cardCode
                            .copyWith(color: CapColors.textMuted),
                      ),
                    ],
                  ),
                  const SizedBox(height: CapSpacing.md),
                  Text(previewCard.text, style: CapType.heading),
                  if (previewCard.hint != null) ...[
                    const SizedBox(height: CapSpacing.sm),
                    Text(
                      previewCard.hint!,
                      style: CapType.body
                          .copyWith(color: CapColors.textSecondary),
                    ),
                  ],
                ],
              ),
            ),
          const SizedBox(height: CapSpacing.md),
          CapButton(
            label: 'Voir les concepts',
            variant: CapButtonVariant.outline,
            expand: false,
            onPressed: () => context.pushNamed('concepts'),
          ),

          const SizedBox(height: CapSpacing.xxl),
          _EntreVousSection(onTap: () => context.pushNamed('entre-vous')),

          const SizedBox(height: CapSpacing.xxl),
          const CapEyebrow('Un seul téléphone'),
          const SizedBox(height: CapSpacing.sm),
          Text('Un seul téléphone.\nIl passe de main en main.',
              style: CapType.title),
          const SizedBox(height: CapSpacing.md),
          Text(
            'Pas de room à rejoindre : le téléphone passe de main en main. '
            'Ce qui se dit reste dans la pièce.',
            style: CapType.body.copyWith(color: CapColors.textSecondary),
          ),

          const SizedBox(height: CapSpacing.xl),
          const Divider(color: CapColors.divider),
          const SizedBox(height: CapSpacing.xl),
          const CapEyebrow('Gratuit pour commencer'),
          const SizedBox(height: CapSpacing.sm),
          Text(
            'Dilemme et Entre vous sont gratuits, les autres concepts se '
            'testent avant d\'acheter. Ensuite c\'est un achat unique, pas '
            'd\'abonnement.',
            style: CapType.body.copyWith(color: CapColors.textSecondary),
          ),
          const SizedBox(height: CapSpacing.md),
          CapLink(
            label: 'Les vraies questions',
            onPressed: () => context.pushNamed('infos'),
          ),

          const SizedBox(height: CapSpacing.xxl),
          const Divider(color: CapColors.divider),
          const SizedBox(height: CapSpacing.lg),
          const CapWordmark(fontSize: 20),
          const SizedBox(height: CapSpacing.sm),
          Text(
            'La carte qui fait partir la soirée.',
            style: CapType.body.copyWith(color: CapColors.textSecondary),
          ),
          const SizedBox(height: CapSpacing.sm),
          Text(
            '© 2026 Ça Part · fait sur la Côte d\'Azur · tous droits réservés',
            style: CapType.meta.copyWith(color: CapColors.textMuted),
          ),
        ],
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.eyebrow,
    required this.title,
    required this.action,
    required this.onTap,
  });

  final String eyebrow;
  final String title;
  final String action;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return CapCard(
      onTap: onTap,
      bordered: true,
      background: CapColors.surfaceSunken,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CapEyebrow(eyebrow),
          const SizedBox(height: CapSpacing.md),
          Text(title, style: CapType.title.copyWith(fontSize: 27)),
          const SizedBox(height: CapSpacing.lg),
          Row(
            children: [
              Text(
                action,
                style: CapType.body.copyWith(color: CapColors.textSecondary),
              ),
              const SizedBox(width: CapSpacing.xs),
              const Icon(Icons.chevron_right_rounded,
                  size: 20, color: CapColors.textMuted),
            ],
          ),
        ],
      ),
    );
  }
}

class _ConceptChip extends StatelessWidget {
  const _ConceptChip({
    required this.concept,
    required this.selected,
    required this.onTap,
  });

  final Concept concept;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: CapMotion.fast,
        padding: const EdgeInsets.symmetric(
          horizontal: CapSpacing.md,
          vertical: CapSpacing.sm + 2,
        ),
        decoration: BoxDecoration(
          color: selected ? CapColors.surface : CapColors.surfaceSunken,
          borderRadius: CapRadius.chipAll,
          border: Border.all(
            color: selected ? concept.color : CapColors.border,
            width: selected ? 1.5 : 1,
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 9,
              height: 9,
              decoration:
                  BoxDecoration(color: concept.color, shape: BoxShape.circle),
            ),
            const SizedBox(width: CapSpacing.sm),
            Text(concept.name, style: CapType.bodyStrong.copyWith(fontSize: 15)),
          ],
        ),
      ),
    );
  }
}

class _EntreVousSection extends StatelessWidget {
  const _EntreVousSection({required this.onTap});

  final VoidCallback onTap;

  static const _steps = [
    'Chacun écrit deux questions.',
    'Le téléphone circule.',
    'Le narrateur lit. On assume.',
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text.rich(
          TextSpan(
            children: [
              TextSpan(text: 'Entre vous', style: CapType.title),
              TextSpan(
                text: '.',
                style: CapType.title.copyWith(color: CapColors.red),
              ),
            ],
          ),
        ),
        const SizedBox(height: CapSpacing.lg),
        for (var i = 0; i < _steps.length; i++)
          Padding(
            padding: const EdgeInsets.only(bottom: CapSpacing.md),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 32,
                  height: 32,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: CapColors.surfaceSunken,
                    borderRadius: CapRadius.buttonAll,
                    border: Border.all(color: CapColors.border),
                  ),
                  child: Text('${i + 1}', style: CapType.bodyStrong),
                ),
                const SizedBox(width: CapSpacing.md),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(_steps[i], style: CapType.body),
                  ),
                ),
              ],
            ),
          ),
        const SizedBox(height: CapSpacing.sm),
        const CapInkCard(
          eyebrow: 'Ciblée',
          text: 'Théo, tu pourrais te voir sortir avec Léa ?',
        ),
        const SizedBox(height: CapSpacing.md),
        const CapInkCard(
          eyebrow: 'Générale',
          text: 'Qui est le plus radin dans la pièce ?',
        ),
        const SizedBox(height: CapSpacing.md),
        CapButton(
          label: 'Jouer à Entre vous',
          variant: CapButtonVariant.outline,
          onPressed: onTap,
        ),
      ],
    );
  }
}
