import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../app.dart';
import '../../design/components/cap_button.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';

/// « Les vraies questions » — la FAQ du site, plus ce qui n'a de sens que dans
/// une application : restaurer ses achats, couper les vibrations.
class InfosScreen extends StatefulWidget {
  const InfosScreen({super.key});

  /// TODO(contenu) : le site a deux questions de plus, « Combien de joueurs ? »
  /// et « Quel âge faut-il ? ». Leurs réponses ne figuraient pas dans les
  /// captures ; les recopier ici plutôt que d'en inventer.
  static const faq = <(String, String)>[
    (
      'Entre vous, c\'est anonyme ?',
      'L\'auteur n\'est jamais affiché. Le groupe pourra parfois essayer de le '
          'deviner. Vos questions restent sur ce téléphone pendant la partie. '
          'Ça Part ne les envoie pas à ses serveurs et les efface à la fin.',
    ),
    (
      'C\'est payant ?',
      'Dilemme et Entre vous sont gratuits, les autres concepts se testent '
          'avant d\'acheter. Ensuite c\'est un achat unique, pas '
          'd\'abonnement : 3,99 € le concept complet, 7,99 € les trois. '
          'L\'achat est lié à votre compte du magasin d\'applications et se '
          'restaure sur vos autres appareils.',
    ),
    (
      'Faut-il télécharger un truc ?',
      'Plus rien : tout est dans l\'application, cartes comprises. Elle '
          'fonctionne sans connexion — un seul téléphone, qui passe de main '
          'en main.',
    ),
  ];

  static final siteUri = Uri.parse('https://www.ca-part.fr/');

  @override
  State<InfosScreen> createState() => _InfosScreenState();
}

class _InfosScreenState extends State<InfosScreen> {
  String _version = '';
  int? _openIndex = 0;

  @override
  void initState() {
    super.initState();
    PackageInfo.fromPlatform().then((info) {
      if (!mounted) return;
      setState(() => _version = '${info.version} (${info.buildNumber})');
    });
  }

  @override
  Widget build(BuildContext context) {
    final scope = AppScope.of(context);
    final settings = scope.settings;
    final purchases = scope.purchases;

    return CapScaffold(
      title: 'Infos',
      onBack: () => context.pop(),
      child: ListView(
        padding: const EdgeInsets.only(bottom: CapSpacing.xxl),
        children: [
          const SizedBox(height: CapSpacing.sm),
          const CapEyebrow('Les vraies questions'),
          const SizedBox(height: CapSpacing.md),
          for (var i = 0; i < InfosScreen.faq.length; i++)
            _FaqRow(
              question: InfosScreen.faq[i].$1,
              answer: InfosScreen.faq[i].$2,
              open: _openIndex == i,
              onTap: () => setState(() => _openIndex = _openIndex == i ? null : i),
            ),

          const SizedBox(height: CapSpacing.xl),
          const CapEyebrow('Vos achats'),
          const SizedBox(height: CapSpacing.md),
          CapButton(
            label: 'J\'ai déjà acheté',
            variant: CapButtonVariant.outline,
            busy: purchases.busy,
            onPressed: purchases.storeAvailable ? purchases.restore : null,
          ),
          if (!purchases.storeAvailable)
            Padding(
              padding: const EdgeInsets.only(top: CapSpacing.sm),
              child: Text(
                'Le magasin n\'est pas joignable sur cet appareil.',
                style: CapType.meta.copyWith(color: CapColors.textMuted),
              ),
            ),

          const SizedBox(height: CapSpacing.xl),
          const CapEyebrow('Réglages'),
          const SizedBox(height: CapSpacing.sm),
          CapCard(
            bordered: true,
            padding: const EdgeInsets.symmetric(
              horizontal: CapSpacing.md,
              vertical: CapSpacing.sm,
            ),
            child: Row(
              children: [
                Expanded(child: Text('Vibrations', style: CapType.body)),
                Switch(
                  value: settings.haptics,
                  onChanged: (v) => setState(() => settings.haptics = v),
                ),
              ],
            ),
          ),
          const SizedBox(height: CapSpacing.sm),
          CapLink(
            label: 'Changer les prénoms',
            onPressed: () => context.pushNamed('prenoms'),
          ),

          const SizedBox(height: CapSpacing.xl),
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
          CapLink(
            label: 'CGV, mentions légales et confidentialité',
            onPressed: () => launchUrl(
              InfosScreen.siteUri,
              mode: LaunchMode.externalApplication,
            ),
          ),
          if (_version.isNotEmpty)
            Text(
              'Version $_version',
              style: CapType.meta.copyWith(color: CapColors.textMuted),
            ),
        ],
      ),
    );
  }
}

class _FaqRow extends StatelessWidget {
  const _FaqRow({
    required this.question,
    required this.answer,
    required this.open,
    required this.onTap,
  });

  final String question;
  final String answer;
  final bool open;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        GestureDetector(
          onTap: onTap,
          behavior: HitTestBehavior.opaque,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: CapSpacing.md),
            child: Row(
              children: [
                Expanded(child: Text(question, style: CapType.bodyStrong)),
                Icon(
                  open
                      ? Icons.keyboard_arrow_down_rounded
                      : Icons.chevron_right_rounded,
                  size: 24,
                  color: CapColors.textSecondary,
                ),
              ],
            ),
          ),
        ),
        AnimatedCrossFade(
          duration: CapMotion.normal,
          sizeCurve: CapMotion.curve,
          crossFadeState:
              open ? CrossFadeState.showFirst : CrossFadeState.showSecond,
          firstChild: Padding(
            padding: const EdgeInsets.only(bottom: CapSpacing.md),
            child: Text(
              answer,
              style: CapType.body.copyWith(color: CapColors.textSecondary),
            ),
          ),
          secondChild: const SizedBox(width: double.infinity),
        ),
        const Divider(color: CapColors.divider, height: 1),
      ],
    );
  }
}
