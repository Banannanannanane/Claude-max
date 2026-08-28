import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../../app.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  String _version = '';

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
    final settings = SettingsScope.of(context);

    return CapScaffold(
      title: 'Réglages',
      onBack: () => context.pop(),
      child: ListView(
        children: [
          CapCard(
            child: Column(
              children: [
                _SwitchRow(
                  label: 'Mode hot',
                  hint: 'Débloque les cartes réservées aux adultes.',
                  value: settings.spicyMode,
                  onChanged: (v) => setState(() => settings.spicyMode = v),
                ),
                const Divider(color: CapColors.border, height: CapSpacing.lg),
                _SwitchRow(
                  label: 'Vibrations',
                  hint: 'Retour haptique sur les boutons et la fin de manche.',
                  value: settings.haptics,
                  onChanged: (v) => setState(() => settings.haptics = v),
                ),
              ],
            ),
          ),
          const SizedBox(height: CapSpacing.md),
          CapCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('À propos', style: CapType.bodyStrong),
                const SizedBox(height: CapSpacing.sm),
                Text(
                  'Ça part ! fonctionne entièrement hors ligne. Aucune donnée '
                  'ne quitte votre téléphone.',
                  style: CapType.body.copyWith(color: CapColors.textSecondary),
                ),
                if (_version.isNotEmpty) ...[
                  const SizedBox(height: CapSpacing.md),
                  Text(
                    'Version $_version',
                    style: CapType.caption.copyWith(color: CapColors.textMuted),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SwitchRow extends StatelessWidget {
  const _SwitchRow({
    required this.label,
    required this.hint,
    required this.value,
    required this.onChanged,
  });

  final String label;
  final String hint;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: CapType.bodyStrong),
              const SizedBox(height: CapSpacing.xs),
              Text(
                hint,
                style: CapType.caption.copyWith(color: CapColors.textMuted),
              ),
            ],
          ),
        ),
        Switch(value: value, onChanged: onChanged),
      ],
    );
  }
}
