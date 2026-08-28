import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app.dart';
import '../../design/components/cap_button.dart';
import '../../design/components/cap_card.dart';
import '../../design/components/cap_scaffold.dart';
import '../../design/tokens.dart';

/// « Changer les prénoms ».
///
/// Les prénoms saisis remplacent les jetons des cartes ({j1}, {j2}, {autre}) :
/// c'est ce qui fait qu'une carte s'adresse à quelqu'un autour de la table
/// plutôt qu'à « Joueur 1 ».
class PlayersScreen extends StatefulWidget {
  const PlayersScreen({super.key});

  @override
  State<PlayersScreen> createState() => _PlayersScreenState();
}

class _PlayersScreenState extends State<PlayersScreen> {
  static const _maxPlayers = 16;

  final _controller = TextEditingController();
  final _focus = FocusNode();
  List<String>? _players;

  List<String> get players => _players ??= List<String>.from(
        AppScope.of(context).settings.players,
      );

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _add() {
    final name = _controller.text.trim();
    if (name.isEmpty || players.length >= _maxPlayers) return;
    if (players.any((p) => p.toLowerCase() == name.toLowerCase())) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$name est déjà à la table.')),
      );
      return;
    }
    setState(() {
      players.add(name);
      _controller.clear();
    });
    _focus.requestFocus();
  }

  void _save() {
    AppScope.of(context).settings.players = players;
    context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final full = players.length >= _maxPlayers;

    return CapScaffold(
      title: 'Les prénoms',
      onBack: () => context.pop(),
      bottomBar: CapButton(label: 'Enregistrer', onPressed: _save),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: CapSpacing.sm),
          Text('Qui est là ?', style: CapType.display),
          const SizedBox(height: CapSpacing.sm),
          Text(
            'Les cartes appelleront les joueurs par leur prénom. Sans prénom '
            'saisi, elles disent « Joueur 1 », comme sur le site.',
            style: CapType.body.copyWith(color: CapColors.textSecondary),
          ),
          const SizedBox(height: CapSpacing.lg),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _controller,
                  focusNode: _focus,
                  enabled: !full,
                  textCapitalization: TextCapitalization.words,
                  textInputAction: TextInputAction.done,
                  onSubmitted: (_) => _add(),
                  style: CapType.body,
                  decoration: InputDecoration(
                    hintText: full ? 'Table complète' : 'Prénom',
                    hintStyle:
                        CapType.body.copyWith(color: CapColors.textMuted),
                    filled: true,
                    fillColor: CapColors.surface,
                    enabledBorder: const OutlineInputBorder(
                      borderRadius: CapRadius.buttonAll,
                      borderSide: BorderSide(color: CapColors.border),
                    ),
                    focusedBorder: const OutlineInputBorder(
                      borderRadius: CapRadius.buttonAll,
                      borderSide: BorderSide(color: CapColors.red, width: 1.5),
                    ),
                    border: const OutlineInputBorder(
                      borderRadius: CapRadius.buttonAll,
                      borderSide: BorderSide(color: CapColors.border),
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
                variant: CapButtonVariant.outline,
                expand: false,
                onPressed: full ? null : _add,
              ),
            ],
          ),
          const SizedBox(height: CapSpacing.lg),
          Expanded(
            child: players.isEmpty
                ? Center(
                    child: Text(
                      'Personne pour l\'instant.',
                      style: CapType.body.copyWith(color: CapColors.textMuted),
                    ),
                  )
                : ListView.separated(
                    itemCount: players.length,
                    separatorBuilder: (_, _) =>
                        const SizedBox(height: CapSpacing.sm),
                    itemBuilder: (context, index) => CapCard(
                      bordered: true,
                      background: CapColors.surface,
                      padding: const EdgeInsets.only(
                        left: CapSpacing.md,
                        top: CapSpacing.sm,
                        bottom: CapSpacing.sm,
                        right: CapSpacing.sm,
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: Text(players[index], style: CapType.body),
                          ),
                          IconButton(
                            onPressed: () =>
                                setState(() => players.removeAt(index)),
                            icon: const Icon(Icons.close_rounded, size: 20),
                            color: CapColors.textMuted,
                            tooltip: 'Retirer ${players[index]}',
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
