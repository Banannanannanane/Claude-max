import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app.dart';
import '../../design/components/cap_button.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';
import '../../models/game.dart';

/// Saisie des joueurs avant le lancement. Les noms de la dernière partie sont
/// reproposés : sur un jeu de soirée, c'est presque toujours la même tablée.
class PlayersScreen extends StatefulWidget {
  const PlayersScreen({super.key, required this.game});

  final GameDefinition game;

  @override
  State<PlayersScreen> createState() => _PlayersScreenState();
}

class _PlayersScreenState extends State<PlayersScreen> {
  final _controller = TextEditingController();
  final _focus = FocusNode();
  late List<String> _players;
  bool _restored = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_restored) return;
    _restored = true;
    _players = SettingsScope.of(context)
        .lastPlayers
        .take(widget.game.maxPlayers)
        .toList();
  }

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  bool get _canAdd => _players.length < widget.game.maxPlayers;
  bool get _canStart => _players.length >= widget.game.minPlayers;

  void _add() {
    final name = _controller.text.trim();
    if (name.isEmpty || !_canAdd) return;
    if (_players.any((p) => p.toLowerCase() == name.toLowerCase())) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$name est déjà dans la partie.')),
      );
      return;
    }
    setState(() {
      _players.add(name);
      _controller.clear();
    });
    _focus.requestFocus();
  }

  void _start() {
    SettingsScope.of(context).lastPlayers = _players;
    context.pushNamed(
      'play',
      pathParameters: {'id': widget.game.id},
      extra: List<String>.from(_players),
    );
  }

  @override
  Widget build(BuildContext context) {
    return CapScaffold(
      title: 'Les joueurs',
      onBack: () => context.pop(),
      bottomBar: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (!_canStart)
            Padding(
              padding: const EdgeInsets.only(bottom: CapSpacing.sm),
              child: Text(
                'Il faut au moins ${widget.game.minPlayers} joueurs.',
                style: CapType.caption.copyWith(color: CapColors.textMuted),
              ),
            ),
          CapButton(
            label: 'C\'est parti',
            icon: Icons.play_arrow_rounded,
            size: CapButtonSize.large,
            onPressed: _canStart ? _start : null,
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _controller,
                  focusNode: _focus,
                  enabled: _canAdd,
                  textCapitalization: TextCapitalization.words,
                  textInputAction: TextInputAction.done,
                  onSubmitted: (_) => _add(),
                  style: CapType.body,
                  decoration: InputDecoration(
                    hintText: _canAdd
                        ? 'Prénom du joueur'
                        : 'Table complète (${widget.game.maxPlayers})',
                    hintStyle:
                        CapType.body.copyWith(color: CapColors.textMuted),
                    filled: true,
                    fillColor: CapColors.surfaceRaised,
                    border: const OutlineInputBorder(
                      borderRadius: CapRadius.mdAll,
                      borderSide: BorderSide.none,
                    ),
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: CapSpacing.md,
                      vertical: CapSpacing.md,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: CapSpacing.sm),
              CapButton(
                label: 'Ajouter',
                variant: CapButtonVariant.secondary,
                expand: false,
                onPressed: _canAdd ? _add : null,
              ),
            ],
          ),
          const SizedBox(height: CapSpacing.lg),
          Expanded(
            child: _players.isEmpty
                ? Center(
                    child: Text(
                      'Ajoutez les joueurs autour de la table.',
                      textAlign: TextAlign.center,
                      style: CapType.body.copyWith(color: CapColors.textMuted),
                    ),
                  )
                : ListView.separated(
                    itemCount: _players.length,
                    separatorBuilder: (_, _) =>
                        const SizedBox(height: CapSpacing.sm),
                    itemBuilder: (context, index) => CapCard(
                      padding: const EdgeInsets.symmetric(
                        horizontal: CapSpacing.md,
                        vertical: CapSpacing.sm + 2,
                      ),
                      child: Row(
                        children: [
                          Text(
                            '${index + 1}',
                            style: CapType.bodyStrong
                                .copyWith(color: widget.game.accent),
                          ),
                          const SizedBox(width: CapSpacing.md),
                          Expanded(
                            child: Text(_players[index], style: CapType.body),
                          ),
                          IconButton(
                            onPressed: () =>
                                setState(() => _players.removeAt(index)),
                            icon: const Icon(Icons.close_rounded),
                            color: CapColors.textMuted,
                            tooltip: 'Retirer ${_players[index]}',
                          ),
                        ],
                      ),
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}
