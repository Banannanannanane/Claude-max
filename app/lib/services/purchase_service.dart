import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:in_app_purchase/in_app_purchase.dart';

import 'settings_service.dart';

/// Déblocage des concepts payants.
///
/// Le site vend les concepts en paiement web (3,99 € l'unité, 7,99 € les
/// trois). Dans une application, Apple et Google imposent que du contenu
/// numérique passe par leur facturation : le même déblocage se fait donc ici
/// par achat in-app non consommable, et « J'ai déjà acheté » devient la
/// restauration d'achats — que les deux stores exigent de toute façon.
class PurchaseService extends ChangeNotifier {
  PurchaseService(this._settings);

  /// Identifiants à créer à l'identique dans App Store Connect et dans la
  /// Play Console. Le suffixe reprend l'identifiant du concept.
  static const productPrefix = 'fr.capart.concept.';

  /// L'offre groupée « 7,99 € les trois » du site.
  static const bundleProductId = 'fr.capart.pack.trois';

  static String productIdFor(String conceptId) => '$productPrefix$conceptId';

  static String? conceptIdFor(String productId) =>
      productId.startsWith(productPrefix)
          ? productId.substring(productPrefix.length)
          : null;

  final SettingsService _settings;

  // Résolu à la première utilisation : `InAppPurchase.instance` enregistre la
  // plateforme du store et lève sur un hôte qui n'en a pas (test, web). Une
  // app qui n'ouvre jamais d'écran d'achat ne doit pas s'en apercevoir.
  late final InAppPurchase _iap = InAppPurchase.instance;

  StreamSubscription<List<PurchaseDetails>>? _subscription;
  Map<String, ProductDetails> _products = const {};

  bool _available = false;
  bool _busy = false;
  String? _error;

  /// La facturation du store répond. Faux sur le web et sur un appareil dont
  /// le compte store n'est pas configuré : l'app reste utilisable, seuls les
  /// boutons de déblocage sont désactivés.
  bool get storeAvailable => _available;
  bool get busy => _busy;
  String? get error => _error;

  /// Prix formaté par le store (devise et format locaux), ou `null` tant que
  /// le catalogue n'est pas chargé — la fiche affiche alors le prix du site.
  String? priceFor(String conceptId) =>
      _products[productIdFor(conceptId)]?.price;

  String? get bundlePrice => _products[bundleProductId]?.price;

  Future<void> init(Iterable<String> conceptIds) async {
    try {
      _available = await _iap.isAvailable();
    } catch (e) {
      // Pas de facturation ici (web, appareil sans compte store) : l'app reste
      // jouable, les boutons de déblocage restent désactivés.
      _available = false;
    }
    if (!_available) {
      notifyListeners();
      return;
    }

    _subscription = _iap.purchaseStream.listen(
      _onPurchases,
      onError: (Object e) {
        _error = '$e';
        notifyListeners();
      },
    );

    final ids = <String>{
      bundleProductId,
      for (final id in conceptIds) productIdFor(id),
    };
    final response = await _iap.queryProductDetails(ids);
    _products = {for (final p in response.productDetails) p.id: p};
    notifyListeners();
  }

  Future<void> buy(String conceptId) => _buyProduct(productIdFor(conceptId));

  Future<void> buyBundle() => _buyProduct(bundleProductId);

  Future<void> _buyProduct(String productId) async {
    final product = _products[productId];
    if (product == null) {
      _error = 'Ce concept n\'est pas disponible à l\'achat pour le moment.';
      notifyListeners();
      return;
    }
    _busy = true;
    _error = null;
    notifyListeners();

    // Les concepts sont des achats définitifs : non consommables des deux
    // côtés, pour qu'un changement de téléphone les retrouve.
    await _iap.buyNonConsumable(
      purchaseParam: PurchaseParam(productDetails: product),
    );
  }

  /// « J'ai déjà acheté ». Les achats restaurés arrivent par le flux, traité
  /// par [_onPurchases].
  Future<void> restore() async {
    if (!_available) return;
    _busy = true;
    _error = null;
    notifyListeners();
    await _iap.restorePurchases();
    _busy = false;
    notifyListeners();
  }

  void _onPurchases(List<PurchaseDetails> purchases) {
    for (final purchase in purchases) {
      switch (purchase.status) {
        case PurchaseStatus.pending:
          _busy = true;
        case PurchaseStatus.error:
          _busy = false;
          _error = purchase.error?.message ?? 'L\'achat n\'a pas abouti.';
        case PurchaseStatus.canceled:
          _busy = false;
        case PurchaseStatus.purchased:
        case PurchaseStatus.restored:
          _busy = false;
          _grant(purchase.productID);
      }

      // Obligatoire des deux côtés : sans cet accusé de réception, le store
      // rejoue l'achat au lancement suivant et finit par le rembourser.
      if (purchase.pendingCompletePurchase) {
        _iap.completePurchase(purchase);
      }
    }
    notifyListeners();
  }

  void _grant(String productId) {
    if (productId == bundleProductId) {
      // Le pack débloque les trois concepts payants.
      for (final id in const ['rapido', 'confession', 'sauve-moi-si-tu-peux']) {
        _settings.unlock(id);
      }
      return;
    }
    final conceptId = conceptIdFor(productId);
    if (conceptId != null) _settings.unlock(conceptId);
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }
}
