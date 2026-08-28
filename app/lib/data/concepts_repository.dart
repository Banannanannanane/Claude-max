import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;

import '../models/concept.dart';

/// Charge les concepts depuis `assets/concepts/index.json`.
class ConceptsRepository {
  ConceptsRepository({this.assetPath = 'assets/concepts/index.json'});

  final String assetPath;

  List<Concept>? _cache;

  Future<List<Concept>> load() async {
    if (_cache != null) return _cache!;
    final raw = await rootBundle.loadString(assetPath);
    final decoded = jsonDecode(raw);
    if (decoded is! List) {
      throw FormatException('$assetPath doit contenir une liste de concepts.');
    }
    _cache = decoded
        .cast<Map<String, dynamic>>()
        .map(Concept.fromJson)
        .toList(growable: false);
    return _cache!;
  }

  @visibleForTesting
  void seed(List<Concept> concepts) => _cache = List.unmodifiable(concepts);
}
