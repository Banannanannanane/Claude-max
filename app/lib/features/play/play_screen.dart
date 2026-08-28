import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app.dart';
import '../../design/components/cap_button.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';
import '../../models/party.dart';
import '../../services/haptics.dart';

/// Le déroulé d'une partie : une carte à la fois, le téléphone passe de main
/// en main.
class PlayScreen extends StatefulWidget {
  const PlayScreen({super.key, required this.conceptIds});

  /// Un identifiant pour une partie sur un seul concept, plusieurs pour le
  /// mode « Mélange les concepts ».
  final List<String> conceptIds;

  @override
  State<PlayScreen> createState() => _PlayScreenState();
}

class _PlayScreenState extends State<PlayScreen> {
  Party? _party;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_party != null) return;
    final scope = AppScope.of(context);
    _party = Party(
      concepts: widget.conceptIds.map(scope.conceptById).toList(),
      players: scope.settings.players,
    );
  }

  @override
  void dispose() {
    _party?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final party = _party!;

    return CapScaffold(
      title: widget.conceptIds.length == 1
          ? AppScope.of(context).conceptById(widget.conceptIds.first).name
          : 'Mélange',
      onBack: () => context.pop(),
      child: ListenableBuilder(
        listenable: party,
        builder: (context, _) {
          if (party.isEmpty) {
            return const _EndPanel(
              title: 'Paquet vide',
              body: 'Aucune carte disponible pour ce concept.',
            );
          }
          if (party.isFinished) {
            return _EndPanel(
              title: 'Fin de la partie',
              body: 'Vous avez passé les ${party.total} cartes.',
              onRestart: party.restart,
            );
          }
          return _Turn(
            key: ValueKey(party.position),
            party: party,
          );
        },
      ),
    );
  }
}

/// Un tour : la carte, les joueurs concernés, et le minuteur quand le concept
/// en a un.
class _Turn extends StatefulWidget {
  const _Turn({super.key, required this.party});

  final Party party;

  @override
  State<_Turn> createState() => _TurnState();
}

class _TurnState extends State<_Turn> {
  Timer? _timer;
  int _secondsLeft = 0;
  bool _running = false;

  DrawnCard get card => widget.party.current!;

  @override
  void initState() {
    super.initState();
    _secondsLeft = card.concept.timerSeconds;
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _startTimer() {
    _timer?.cancel();
    setState(() {
      _secondsLeft = card.concept.timerSeconds;
      _running = true;
    });
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_secondsLeft <= 1) {
        timer.cancel();
        Haptics.heavy();
        setState(() {
          _secondsLeft = 0;
          _running = false;
        });
        return;
      }
      setState(() => _secondsLeft--);
    });
  }

  /// « Joueur 1, prends le téléphone » devient « Léa, prends le téléphone ».
  String get _readersLine {
    final readers = card.readers;
    if (readers.length == 1) return 'Au tour de ${readers.first}';
    return '${readers.first} lit · ${readers[1]} défend';
  }

  @override
  Widget build(BuildContext context) {
    final party = widget.party;
    final concept = card.concept;
    final timed = concept.isTimed;
    final timeUp = timed && !_running && _secondsLeft == 0;

    return Column(
      children: [
        const SizedBox(height: CapSpacing.sm),
        Row(
          children: [
            Expanded(child: CapEyebrow(concept.name, color: concept.color)),
            Text(
              '${party.position + 1} / ${party.total}',
              style: CapType.cardCode.copyWith(color: CapColors.textMuted),
            ),
          ],
        ),
        const SizedBox(height: CapSpacing.sm),
        ClipRRect(
          borderRadius: CapRadius.chipAll,
          child: LinearProgressIndicator(
            value: (party.position + 1) / party.total,
            minHeight: 4,
            backgroundColor: CapColors.border,
            valueColor: AlwaysStoppedAnimation(concept.color),
          ),
        ),
        const SizedBox(height: CapSpacing.lg),
        Text(
          _readersLine,
          style: CapType.meta.copyWith(color: CapColors.textSecondary),
        ),
        const SizedBox(height: CapSpacing.md),
        Expanded(
          child: SingleChildScrollView(
            child: CapCard(
              accent: concept.color,
              padding: const EdgeInsets.all(CapSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(card.text, style: CapType.heading.copyWith(fontSize: 25)),
                  if (card.hint != null) ...[
                    const SizedBox(height: CapSpacing.md),
                    Text(
                      card.hint!,
                      style:
                          CapType.body.copyWith(color: CapColors.textSecondary),
                    ),
                  ],
                  const SizedBox(height: CapSpacing.xl),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        card.code,
                        style: CapType.cardCode
                            .copyWith(color: CapColors.textMuted),
                      ),
                      const CapWordmark(fontSize: 14),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
        if (timed) ...[
          const SizedBox(height: CapSpacing.md),
          Text(
            timeUp ? 'Temps écoulé' : '$_secondsLeft',
            style: CapType.display.copyWith(
              fontSize: timeUp ? 26 : 56,
              color: timeUp ? CapColors.red : CapColors.textPrimary,
            ),
          ),
        ],
        const SizedBox(height: CapSpacing.md),
        if (timed && !_running && !timeUp)
          CapButton(
            label: 'Démarrer les ${concept.timerSeconds} secondes',
            onPressed: _startTimer,
          )
        else
          CapButton(
            label: party.position + 1 >= party.total
                ? 'Terminer'
                : 'Carte suivante',
            onPressed: party.next,
          ),
        const SizedBox(height: CapSpacing.md),
      ],
    );
  }
}

class _EndPanel extends StatelessWidget {
  const _EndPanel({required this.title, required this.body, this.onRestart});

  final String title;
  final String body;
  final VoidCallback? onRestart;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Expanded(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(title, style: CapType.display, textAlign: TextAlign.center),
                const SizedBox(height: CapSpacing.md),
                Text(
                  body,
                  textAlign: TextAlign.center,
                  style: CapType.body.copyWith(color: CapColors.textSecondary),
                ),
              ],
            ),
          ),
        ),
        if (onRestart != null)
          CapButton(label: 'Rejouer', onPressed: onRestart),
        const SizedBox(height: CapSpacing.sm),
        CapButton(
          label: 'Choisir un autre concept',
          variant: CapButtonVariant.outline,
          onPressed: () => context.goNamed('concepts'),
        ),
        const SizedBox(height: CapSpacing.md),
      ],
    );
  }
}
