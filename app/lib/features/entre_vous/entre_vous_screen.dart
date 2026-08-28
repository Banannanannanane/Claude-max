import 'dart:math';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app.dart';
import '../../design/components/cap_button.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';

/// Une question écrite par un joueur.
class _Question {
  _Question({required this.text, required this.target});

  final String text;

  /// Prénom visé, ou `null` pour une question générale.
  final String? target;

  bool get targeted => target != null;
}

enum _Phase { intro, writing, handover, narrating, done }

/// « Entre vous » : ce sont les joueurs qui écrivent les cartes.
///
/// Les questions ne quittent pas l'appareil et sont effacées à la fin de la
/// partie — c'est la promesse du site, et c'est aussi ce qui permet de
/// déclarer « aucune donnée collectée » aux deux stores.
class EntreVousScreen extends StatefulWidget {
  const EntreVousScreen({super.key});

  static const questionsPerPlayer = 2;

  @override
  State<EntreVousScreen> createState() => _EntreVousScreenState();
}

class _EntreVousScreenState extends State<EntreVousScreen> {
  final _controller = TextEditingController();
  final _questions = <_Question>[];
  final _random = Random();

  _Phase _phase = _Phase.intro;
  int _playerIndex = 0;
  int _writtenByCurrent = 0;
  int _narrationIndex = 0;
  String? _target;

  List<String> get _players => AppScope.of(context).settings.players;

  @override
  void dispose() {
    _controller.dispose();
    // Les questions vivent dans cet écran et disparaissent avec lui.
    _questions.clear();
    super.dispose();
  }

  void _submitQuestion() {
    final text = _controller.text.trim();
    if (text.isEmpty) return;

    setState(() {
      _questions.add(_Question(text: text, target: _target));
      _controller.clear();
      _target = null;
      _writtenByCurrent++;

      if (_writtenByCurrent < EntreVousScreen.questionsPerPlayer) return;

      _writtenByCurrent = 0;
      _playerIndex++;
      _phase = _playerIndex >= _players.length ? _Phase.narrating : _Phase.handover;
      if (_phase == _Phase.narrating) _questions.shuffle(_random);
    });
  }

  @override
  Widget build(BuildContext context) {
    return CapScaffold(
      title: 'Entre vous',
      onBack: () => context.pop(),
      child: switch (_phase) {
        _Phase.intro => _buildIntro(),
        _Phase.writing => _buildWriting(),
        _Phase.handover => _buildHandover(),
        _Phase.narrating => _buildNarrating(),
        _Phase.done => _buildDone(),
      },
    );
  }

  Widget _buildIntro() {
    final players = _players;
    const steps = [
      'Chacun écrit deux questions.',
      'Le téléphone circule.',
      'Le narrateur lit. On assume.',
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: ListView(
            children: [
              const SizedBox(height: CapSpacing.sm),
              Text('Entre vous', style: CapType.display),
              const SizedBox(height: CapSpacing.lg),
              for (var i = 0; i < steps.length; i++)
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
                          child: Text(steps[i], style: CapType.body),
                        ),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: CapSpacing.md),
              Text(
                'L\'auteur n\'est jamais affiché. Les questions restent sur ce '
                'téléphone pendant la partie et sont effacées à la fin.',
                style: CapType.body.copyWith(color: CapColors.textSecondary),
              ),
              if (players.isEmpty) ...[
                const SizedBox(height: CapSpacing.lg),
                Text(
                  'Il faut d\'abord les prénoms de la table.',
                  style: CapType.bodyStrong.copyWith(color: CapColors.red),
                ),
                CapLink(
                  label: 'Ajouter les prénoms',
                  onPressed: () => context.pushNamed('prenoms'),
                ),
              ],
            ],
          ),
        ),
        CapButton(
          label: 'Commencer',
          onPressed: players.isEmpty
              ? null
              : () => setState(() {
                    _phase = _Phase.writing;
                    _playerIndex = 0;
                    _writtenByCurrent = 0;
                    _questions.clear();
                  }),
        ),
        const SizedBox(height: CapSpacing.md),
      ],
    );
  }

  Widget _buildHandover() {
    final next = _players[_playerIndex];
    return Column(
      children: [
        Expanded(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const CapEyebrow('Passe le téléphone'),
                const SizedBox(height: CapSpacing.md),
                Text(next, style: CapType.display, textAlign: TextAlign.center),
                const SizedBox(height: CapSpacing.md),
                Text(
                  'Personne d\'autre ne regarde.',
                  style: CapType.body.copyWith(color: CapColors.textSecondary),
                ),
              ],
            ),
          ),
        ),
        CapButton(
          label: 'C\'est moi',
          onPressed: () => setState(() => _phase = _Phase.writing),
        ),
        const SizedBox(height: CapSpacing.md),
      ],
    );
  }

  Widget _buildWriting() {
    final author = _players[_playerIndex];
    final others = _players.where((p) => p != author).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: ListView(
            children: [
              const SizedBox(height: CapSpacing.sm),
              CapEyebrow(
                '$author · question ${_writtenByCurrent + 1} '
                'sur ${EntreVousScreen.questionsPerPlayer}',
              ),
              const SizedBox(height: CapSpacing.md),
              TextField(
                controller: _controller,
                maxLines: 3,
                maxLength: 160,
                textCapitalization: TextCapitalization.sentences,
                style: CapType.heading.copyWith(fontSize: 20),
                decoration: InputDecoration(
                  hintText: 'Écris ta question…',
                  hintStyle:
                      CapType.heading.copyWith(color: CapColors.textMuted),
                  counterStyle:
                      CapType.meta.copyWith(color: CapColors.textMuted),
                  filled: true,
                  fillColor: CapColors.surface,
                  enabledBorder: const OutlineInputBorder(
                    borderRadius: CapRadius.cardAll,
                    borderSide: BorderSide(color: CapColors.border),
                  ),
                  focusedBorder: const OutlineInputBorder(
                    borderRadius: CapRadius.cardAll,
                    borderSide: BorderSide(color: CapColors.red, width: 1.5),
                  ),
                  border: const OutlineInputBorder(
                    borderRadius: CapRadius.cardAll,
                    borderSide: BorderSide(color: CapColors.border),
                  ),
                  contentPadding: const EdgeInsets.all(CapSpacing.md),
                ),
              ),
              const SizedBox(height: CapSpacing.md),
              const CapEyebrow('Elle vise qui ?'),
              const SizedBox(height: CapSpacing.sm),
              Wrap(
                spacing: CapSpacing.sm,
                runSpacing: CapSpacing.sm,
                children: [
                  _TargetChip(
                    label: 'Générale',
                    selected: _target == null,
                    onTap: () => setState(() => _target = null),
                  ),
                  for (final other in others)
                    _TargetChip(
                      label: other,
                      selected: _target == other,
                      onTap: () => setState(() => _target = other),
                    ),
                ],
              ),
            ],
          ),
        ),
        CapButton(label: 'Valider', onPressed: _submitQuestion),
        const SizedBox(height: CapSpacing.md),
      ],
    );
  }

  Widget _buildNarrating() {
    final question = _questions[_narrationIndex];
    final last = _narrationIndex + 1 >= _questions.length;

    return Column(
      children: [
        const SizedBox(height: CapSpacing.sm),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const CapEyebrow('Le narrateur lit'),
            Text(
              '${_narrationIndex + 1} / ${_questions.length}',
              style: CapType.cardCode.copyWith(color: CapColors.textMuted),
            ),
          ],
        ),
        const SizedBox(height: CapSpacing.lg),
        Expanded(
          child: SingleChildScrollView(
            child: CapInkCard(
              eyebrow: question.targeted ? 'Ciblée' : 'Générale',
              text: question.targeted
                  ? '${question.target}, ${question.text}'
                  : question.text,
            ),
          ),
        ),
        CapButton(
          label: last ? 'Terminer' : 'Question suivante',
          onPressed: () => setState(() {
            if (last) {
              _phase = _Phase.done;
              _questions.clear();
            } else {
              _narrationIndex++;
            }
          }),
        ),
        const SizedBox(height: CapSpacing.md),
      ],
    );
  }

  Widget _buildDone() {
    return Column(
      children: [
        Expanded(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text('C\'est tout', style: CapType.display),
                const SizedBox(height: CapSpacing.md),
                Text(
                  'Les questions ont été effacées.',
                  textAlign: TextAlign.center,
                  style: CapType.body.copyWith(color: CapColors.textSecondary),
                ),
              ],
            ),
          ),
        ),
        CapButton(
          label: 'Rejouer',
          onPressed: () => setState(() {
            _phase = _Phase.intro;
            _narrationIndex = 0;
          }),
        ),
        const SizedBox(height: CapSpacing.sm),
        CapButton(
          label: 'Voir les concepts',
          variant: CapButtonVariant.outline,
          onPressed: () => context.goNamed('concepts'),
        ),
        const SizedBox(height: CapSpacing.md),
      ],
    );
  }
}

class _TargetChip extends StatelessWidget {
  const _TargetChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
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
          color: selected ? CapColors.ink : CapColors.surfaceSunken,
          borderRadius: CapRadius.chipAll,
          border: Border.all(
            color: selected ? CapColors.ink : CapColors.border,
          ),
        ),
        child: Text(
          label,
          style: CapType.bodyStrong.copyWith(
            fontSize: 15,
            color: selected ? CapColors.onInk : CapColors.textPrimary,
          ),
        ),
      ),
    );
  }
}
