import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app.dart';
import '../../design/components/cap_button.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';
import '../../models/game.dart';
import '../../models/session.dart';
import '../../services/haptics.dart';

/// Écran de partie. Aiguille vers le déroulé correspondant au moteur du jeu.
class PlayScreen extends StatefulWidget {
  const PlayScreen({super.key, required this.game, required this.players});

  final GameDefinition game;
  final List<String> players;

  @override
  State<PlayScreen> createState() => _PlayScreenState();
}

class _PlayScreenState extends State<PlayScreen> {
  GameSession? _session;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _session ??= GameSession(
      game: widget.game,
      players: widget.players,
      includeSpicy: SettingsScope.of(context).spicyMode,
    );
  }

  @override
  void dispose() {
    _session?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final session = _session!;
    return CapScaffold(
      title: widget.game.name,
      onBack: () => context.pop(),
      child: AnimatedBuilder(
        animation: session,
        builder: (context, _) {
          if (session.isEmpty) {
            return _Message(
              title: 'Paquet vide',
              body: widget.game.prompts.isEmpty
                  ? 'Ce jeu n\'a pas encore de cartes dans assets/games/index.json.'
                  : 'Toutes les cartes de ce jeu sont réservées au mode « hot », '
                      'activable dans les réglages.',
              accent: widget.game.accent,
            );
          }
          if (session.isFinished) {
            return _FinishedView(session: session);
          }
          switch (widget.game.engine) {
            case GameEngine.timedRound:
              return _TimedRoundView(session: session);
            case GameEngine.promptDeck:
            case GameEngine.custom:
              return _PromptDeckView(session: session);
          }
        },
      ),
    );
  }
}

/// Déroulé « on pioche une carte » : le joueur du tour lit sa carte, tape pour
/// passer au suivant.
class _PromptDeckView extends StatelessWidget {
  const _PromptDeckView({required this.session});

  final GameSession session;

  @override
  Widget build(BuildContext context) {
    final prompt = session.current!;
    final player = session.currentPlayer;

    return Column(
      children: [
        _ProgressBar(session: session),
        const SizedBox(height: CapSpacing.lg),
        if (player != null)
          Text(
            'Au tour de $player',
            style: CapType.caption.copyWith(color: CapColors.textMuted),
          ),
        Expanded(
          child: GestureDetector(
            onTap: session.next,
            behavior: HitTestBehavior.opaque,
            child: Center(
              child: AnimatedSwitcher(
                duration: CapMotion.normal,
                switchInCurve: CapMotion.curve,
                child: Column(
                  key: ValueKey(prompt.text),
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      prompt.text,
                      textAlign: TextAlign.center,
                      style: CapType.title.copyWith(fontSize: 30, height: 1.25),
                    ),
                    if (prompt.subtitle != null) ...[
                      const SizedBox(height: CapSpacing.md),
                      Text(
                        prompt.subtitle!,
                        textAlign: TextAlign.center,
                        style: CapType.body
                            .copyWith(color: CapColors.textSecondary),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.only(bottom: CapSpacing.md),
          child: CapButton(
            label: session.remaining <= 1 ? 'Terminer' : 'Carte suivante',
            size: CapButtonSize.large,
            onPressed: session.next,
          ),
        ),
      ],
    );
  }
}

/// Déroulé chronométré : le joueur du tour fait deviner le plus de cartes
/// possible avant la fin du temps.
class _TimedRoundView extends StatefulWidget {
  const _TimedRoundView({required this.session});

  final GameSession session;

  @override
  State<_TimedRoundView> createState() => _TimedRoundViewState();
}

class _TimedRoundViewState extends State<_TimedRoundView> {
  Timer? _timer;
  late int _secondsLeft = widget.session.game.roundSeconds;
  int _score = 0;
  bool _running = false;

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _startRound() {
    _timer?.cancel();
    setState(() {
      _secondsLeft = widget.session.game.roundSeconds;
      _score = 0;
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

  void _found() {
    setState(() => _score++);
    widget.session.next();
  }

  @override
  Widget build(BuildContext context) {
    final session = widget.session;
    final player = session.currentPlayer;

    if (!_running) {
      final roundOver = _secondsLeft == 0;
      return _Message(
        title: roundOver ? 'Manche terminée' : 'Prêt ?',
        body: roundOver
            ? '$_score carte${_score > 1 ? 's' : ''} trouvée${_score > 1 ? 's' : ''}.'
                '${session.nextPlayer != null ? '\n\nPassez le téléphone à ${session.nextPlayer}.' : ''}'
            : player != null
                ? '$player fait deviner pendant ${session.game.roundSeconds} secondes.'
                : 'Manche de ${session.game.roundSeconds} secondes.',
        accent: session.game.accent,
        action: CapButton(
          label: roundOver ? 'Manche suivante' : 'Démarrer',
          size: CapButtonSize.large,
          onPressed: _startRound,
        ),
      );
    }

    final prompt = session.current!;
    final fraction = _secondsLeft / session.game.roundSeconds;

    return Column(
      children: [
        const SizedBox(height: CapSpacing.md),
        Text(
          '$_secondsLeft',
          style: CapType.display.copyWith(
            fontSize: 64,
            color: fraction < 0.2 ? CapColors.danger : CapColors.textPrimary,
          ),
        ),
        const SizedBox(height: CapSpacing.sm),
        ClipRRect(
          borderRadius: CapRadius.pillAll,
          child: LinearProgressIndicator(
            value: fraction,
            minHeight: 6,
            backgroundColor: CapColors.border,
            valueColor: AlwaysStoppedAnimation(session.game.accent),
          ),
        ),
        Expanded(
          child: Center(
            child: AnimatedSwitcher(
              duration: CapMotion.fast,
              child: Text(
                prompt.text,
                key: ValueKey(prompt.text),
                textAlign: TextAlign.center,
                style: CapType.display.copyWith(fontSize: 34),
              ),
            ),
          ),
        ),
        Text(
          'Score : $_score',
          style: CapType.caption.copyWith(color: CapColors.textMuted),
        ),
        const SizedBox(height: CapSpacing.sm),
        Row(
          children: [
            Expanded(
              child: CapButton(
                label: 'Passer',
                variant: CapButtonVariant.secondary,
                onPressed: session.next,
              ),
            ),
            const SizedBox(width: CapSpacing.sm),
            Expanded(
              child: CapButton(
                label: 'Trouvé',
                icon: Icons.check_rounded,
                onPressed: _found,
              ),
            ),
          ],
        ),
        const SizedBox(height: CapSpacing.md),
      ],
    );
  }
}

class _ProgressBar extends StatelessWidget {
  const _ProgressBar({required this.session});

  final GameSession session;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ClipRRect(
          borderRadius: CapRadius.pillAll,
          child: LinearProgressIndicator(
            value: session.progress,
            minHeight: 6,
            backgroundColor: CapColors.border,
            valueColor: AlwaysStoppedAnimation(session.game.accent),
          ),
        ),
        const SizedBox(height: CapSpacing.sm),
        Text(
          '${session.total - session.remaining + 1} / ${session.total}',
          textAlign: TextAlign.center,
          style: CapType.caption.copyWith(color: CapColors.textMuted),
        ),
      ],
    );
  }
}

class _FinishedView extends StatelessWidget {
  const _FinishedView({required this.session});

  final GameSession session;

  @override
  Widget build(BuildContext context) {
    return _Message(
      title: 'Fin de la partie',
      body: 'Vous avez passé les ${session.total} cartes de ce jeu.',
      accent: session.game.accent,
      action: Column(
        children: [
          CapButton(
            label: 'Rejouer',
            icon: Icons.refresh_rounded,
            size: CapButtonSize.large,
            onPressed: session.restart,
          ),
          const SizedBox(height: CapSpacing.sm),
          CapButton(
            label: 'Choisir un autre jeu',
            variant: CapButtonVariant.ghost,
            onPressed: () => context.goNamed('concepts'),
          ),
        ],
      ),
    );
  }
}

class _Message extends StatelessWidget {
  const _Message({
    required this.title,
    required this.body,
    required this.accent,
    this.action,
  });

  final String title;
  final String body;
  final Color accent;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Expanded(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  width: 72,
                  height: 72,
                  decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
                  child: const Icon(
                    Icons.celebration_rounded,
                    color: CapColors.onPrimary,
                    size: 34,
                  ),
                ),
                const SizedBox(height: CapSpacing.lg),
                Text(title, style: CapType.title, textAlign: TextAlign.center),
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
        if (action != null)
          Padding(
            padding: const EdgeInsets.only(bottom: CapSpacing.md),
            child: action!,
          ),
      ],
    );
  }
}
