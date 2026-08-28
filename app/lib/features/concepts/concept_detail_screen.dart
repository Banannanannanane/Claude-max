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
    final scope = AppScope.of(context);
    final purchases = scope.purchases;
    final unlocked = concept.free || scope.settings.isUnlocked(concept.id);
    final price = purchases.priceFor(concept.id) ?? concept.priceLabel;

    return CapScaffold(
      title: concept.name,
      onBack: () => context.pop(),
      bottomBar: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CapButton(
            label: 'Lancer',
            onPressed: () => _start(context),
          ),
          if (!unlocked) ...[
            const SizedBox(height: CapSpacing.sm + 2),
            CapButton(
              label: 'Débloquer ${concept.name} · $price',
              variant: CapButtonVariant.outline,
              busy: purchases.busy,
              onPressed: purchases.storeAvailable
                  ? () => purchases.buy(concept.id)
                  : null,
            ),
            CapLink(
              label: 'J\'ai déjà acheté',
              align: TextAlign.center,
              onPressed: purchases.storeAvailable ? purchases.restore : null,
            ),
            if (!purchases.storeAvailable)
              Padding(
                padding: const EdgeInsets.only(bottom: CapSpacing.sm),
                child: Text(
                  'Le magasin n\'est pas joignable sur cet appareil.',
                  textAlign: TextAlign.center,
                  style: CapType.meta.copyWith(color: CapColors.textMuted),
                ),
              ),
          ],
        ],
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
          if (purchases.error != null) ...[
            const SizedBox(height: CapSpacing.md),
            Text(
              purchases.error!,
              style: CapType.meta.copyWith(color: CapColors.red),
            ),
          ],
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
    final scope = AppScope.of(context);
    final playable = scope.playableConcepts;
    final locked = scope.concepts.where((c) => !playable.contains(c)).toList();

    return CapScaffold(
      title: 'Mélange',
      onBack: () => context.pop(),
      bottomBar: CapButton(
        label: 'Lancer',
        onPressed: playable.isEmpty
            ? null
            : () => context.pushNamed(
                  'partie',
                  extra: playable.map((c) => c.id).toList(),
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
          for (final concept in playable)
            _MixRow(name: concept.name, color: concept.color, included: true),
          for (final concept in locked)
            _MixRow(name: concept.name, color: concept.color, included: false),
          if (locked.isNotEmpty) ...[
            const SizedBox(height: CapSpacing.md),
            Text(
              'Les concepts non débloqués restent en dehors du mélange.',
              style: CapType.meta.copyWith(color: CapColors.textMuted),
            ),
          ],
        ],
      ),
    );
  }
}

class _MixRow extends StatelessWidget {
  const _MixRow({
    required this.name,
    required this.color,
    required this.included,
  });

  final String name;
  final Color color;
  final bool included;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: CapSpacing.sm),
      child: Row(
        children: [
          Container(
            width: 9,
            height: 9,
            decoration: BoxDecoration(
              color: included ? color : CapColors.border,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: CapSpacing.sm + 2),
          Expanded(
            child: Text(
              name,
              style: CapType.body.copyWith(
                color: included ? CapColors.textPrimary : CapColors.textMuted,
              ),
            ),
          ),
          Icon(
            included ? Icons.check_rounded : Icons.lock_outline_rounded,
            size: 18,
            color: included ? color : CapColors.textMuted,
          ),
        ],
      ),
    );
  }
}
