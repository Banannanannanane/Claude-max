class_name Data
## Tables de jeu : tas, objets, machines, arbre technologique, boutique, succès.

const PILE_POS := Vector3(0, 0, -36)
const HAY_PER_PILE := 22
const FIELD := 80 # demi-côté du terrain constructible (cases)
const LOT := 10 # unités par lot transporté sur les tapis
const NEEDLE_UNIT := 1000 # une unité de jeu = 1 000 aiguilles (affichage)

## Directions de la grille : 0 nord (-Z), 1 est (+X), 2 sud (+Z), 3 ouest (-X).
const DIRS := [Vector2i(0, -1), Vector2i(1, 0), Vector2i(0, 1), Vector2i(-1, 0)]

const PILE_ORDER := ["petit", "moyen", "gros", "montagne"]
## needles : en unités (× NEEDLE_UNIT aiguilles). radius : rayon au sol du tas neuf (m), hauteur ≈ 1,3 × rayon.
const PILES := {
	"petit": {"name": "Petit tas", "needles": 10000, "radius": 4.5, "hay_value": 30.0, "order": 0.0, "right": ""},
	"moyen": {"name": "Tas moyen", "needles": 25000, "radius": 6.5, "hay_value": 90.0, "order": 2500.0, "right": "c_moyen"},
	"gros": {"name": "Grand tas", "needles": 100000, "radius": 11.0, "hay_value": 320.0, "order": 40000.0, "right": "c_gros"},
	"montagne": {"name": "Montagne d'aiguilles", "needles": 300000, "radius": 24.0, "hay_value": 1500.0, "order": 400000.0, "right": "c_montagne"},
}

## Objets qui circulent sur les tapis (prix = vente dans le trou, pour un lot / une pièce).
const ITEMS := {
	"vrac": {"name": "Aiguilles en vrac", "price": 2.0, "color": Color(0.55, 0.56, 0.58), "scale": Vector3(0.5, 0.12, 0.2)},
	"acier": {"name": "Aiguilles vérifiées", "price": 4.0, "color": Color(0.78, 0.8, 0.84), "scale": Vector3(0.5, 0.12, 0.2)},
	"affutee": {"name": "Aiguilles affûtées", "price": 9.0, "color": Color(0.88, 0.9, 0.95), "scale": Vector3(0.5, 0.12, 0.2)},
	"balle": {"name": "Balle d'aiguilles compressées", "price": 28.0, "color": Color(0.6, 0.62, 0.66), "scale": Vector3(0.45, 0.3, 0.35)},
	"brut": {"name": "Lingot brut", "price": 10.0, "color": Color(0.42, 0.38, 0.35), "scale": Vector3(0.36, 0.14, 0.2)},
	"pur": {"name": "Lingot pur", "price": 22.0, "color": Color(0.9, 0.92, 0.95), "scale": Vector3(0.36, 0.14, 0.2)},
	"tole": {"name": "Tôle d'acier", "price": 55.0, "color": Color(0.55, 0.62, 0.72), "scale": Vector3(0.55, 0.04, 0.5)},
	"fil": {"name": "Bobine de fil", "price": 28.0, "color": Color(0.85, 0.55, 0.3), "scale": Vector3(0.3, 0.3, 0.3)},
	"boite": {"name": "Boîte d'aiguilles neuves", "price": 80.0, "color": Color(0.85, 0.2, 0.25), "scale": Vector3(0.36, 0.22, 0.3)},
	"epingle": {"name": "Boîte d'épingles", "price": 42.0, "color": Color(0.95, 0.75, 0.2), "scale": Vector3(0.3, 0.16, 0.24)},
	"kit": {"name": "Kit de couture", "price": 260.0, "color": Color(0.55, 0.25, 0.65), "scale": Vector3(0.42, 0.14, 0.32)},
	"carton": {"name": "Carton d'expédition", "price": 1250.0, "color": Color(0.72, 0.55, 0.33), "scale": Vector3(0.5, 0.4, 0.45)},
}
const ITEM_ORDER := ["vrac", "acier", "affutee", "balle", "brut", "pur", "tole", "fil", "epingle", "boite", "kit", "carton"]

## Machines. size = (largeur, profondeur) en cases ; l'entrée est à l'arrière (toute la largeur),
## la sortie à l'avant (la machine sert à tour de rôle chaque case de devant). plan = nœud de l'arbre qui donne le droit de construire.
## recipe : entrée (type -> quantité), sortie (type -> quantité), durée en secondes.
const MACHINES := {
	"convoyeur": {"name": "Convoyeur", "desc": "Tapis roulant : transporte les objets dans le sens des flèches.",
		"size": Vector2i(1, 1), "cost": 5.0, "plan": "p_convoyeur", "h": 0.3},
	"express": {"name": "Tapis express", "desc": "Un convoyeur deux fois plus rapide (chevrons bleus). Se pose en ligne comme un tapis.",
		"size": Vector2i(1, 1), "cost": 25.0, "plan": "p_express", "h": 0.3},
	"separateur": {"name": "Séparateur", "desc": "Répartit les objets à gauche, devant et à droite, chacun son tour.",
		"size": Vector2i(1, 1), "cost": 60.0, "plan": "p_separateur", "h": 0.6},
	"tremie": {"name": "Trémie", "desc": "Dépose tes aiguilles dedans : elle les envoie sur le tapis, lot par lot.",
		"size": Vector2i(2, 2), "cost": 250.0, "plan": "p_convoyeur", "h": 1.9, "cap": 600},
	"scanner": {"name": "Scanner", "desc": "Détecte le foin caché dans les aiguilles en vrac. Les aiguilles ressortent vérifiées.",
		"size": Vector2i(1, 2), "cost": 150.0, "plan": "p_scanner", "h": 1.9,
		"in": {"vrac": 1}, "out": {"acier": 1}, "time": 1.2, "speed": "u_scan_vitesse", "power": 2.0},
	"bras": {"name": "Bras robot", "desc": "Ramasse les aiguilles du tas et les pose sur le tapis de devant. À placer près du tas.",
		"size": Vector2i(1, 1), "cost": 400.0, "plan": "p_bras", "h": 1.2, "dig": 2.0, "range": "u_bras_portee", "speed": "u_bras_vitesse", "power": 3.0},
	"pelle": {"name": "Pelleteuse", "desc": "Creuse le tas par grosses pelletées. À placer près du tas.",
		"size": Vector2i(2, 2), "cost": 6000.0, "plan": "p_pelle", "h": 1.9, "dig": 0.6, "range": "u_pelle_portee", "speed": "u_pelle_vitesse", "lot_mult": 3, "power": 8.0},
	"fonderie": {"name": "Fonderie", "desc": "Fond les aiguilles vérifiées en lingots bruts (jamais de vrac : le foin brûlerait !).",
		"size": Vector2i(2, 2), "cost": 800.0, "plan": "p_fonderie", "h": 2.2,
		"in": {"acier": 1}, "out": {"brut": 1}, "time": 3.0, "speed": "u_fond_vitesse", "batch": "u_fond_lot", "quality": "u_fond_qualite", "power": 6.0},
	"purif": {"name": "Purificateur", "desc": "Purifie les lingots bruts en lingots purs.",
		"size": Vector2i(2, 2), "cost": 3000.0, "plan": "p_purif", "h": 2.3,
		"in": {"brut": 1}, "out": {"pur": 1}, "time": 3.0, "speed": "u_purif_vitesse", "quality": "u_purif_qualite", "power": 6.0},
	"presse": {"name": "Presse à tôles", "desc": "Lamine 2 lingots purs en une tôle d'acier.",
		"size": Vector2i(2, 2), "cost": 12000.0, "plan": "p_presse", "h": 2.3,
		"in": {"pur": 2}, "out": {"tole": 1}, "time": 4.0, "speed": "u_presse_vitesse", "quality": "u_presse_qualite", "power": 8.0},
	"trefileuse": {"name": "Tréfileuse", "desc": "Étire un lingot pur en deux bobines de fil.",
		"size": Vector2i(2, 2), "cost": 10000.0, "plan": "p_trefileuse", "h": 1.8,
		"in": {"pur": 1}, "out": {"fil": 2}, "time": 3.0, "speed": "u_tref_vitesse", "quality": "u_tref_qualite", "power": 6.0},
	"aiguilleuse": {"name": "Aiguilleuse", "desc": "Fabrique des boîtes d'aiguilles neuves avec le fil. Ironique, non ?",
		"size": Vector2i(2, 2), "cost": 40000.0, "plan": "p_aiguilleuse", "h": 2.0,
		"in": {"fil": 1}, "out": {"boite": 1}, "time": 5.0, "speed": "u_aig_vitesse", "quality": "u_aig_qualite", "power": 10.0},
	"tampon": {"name": "Stockage tampon", "desc": "Accumule jusqu'à 120 objets et les relâche au rythme du tapis.",
		"size": Vector2i(2, 2), "cost": 500.0, "plan": "p_tampon", "h": 2.0, "cap": 120},
	"drone": {"name": "Drone collecteur", "desc": "Fait la navette entre le tas et la trémie la plus proche.",
		"size": Vector2i(1, 1), "cost": 15000.0, "plan": "p_drone", "h": 0.3, "power": 2.0},
	"radar": {"name": "Radar à foin", "desc": "Révèle les brins de foin cachés autour de lui : une balise dorée s'allume au-dessus de chacun, et ils apparaissent sur la carte.",
		"size": Vector2i(1, 1), "cost": 900.0, "plan": "p_radar", "h": 2.4, "power": 2.0},
	"groupe": {"name": "Groupe électrogène", "desc": "Produit 15 kW pour toute l'usine, mais brûle du carburant (0,05 € par seconde).",
		"size": Vector2i(2, 2), "cost": 400.0, "plan": "p_energie", "h": 1.6, "gen": 15.0, "kind": "fuel"},
	"eolienne": {"name": "Éolienne", "desc": "Produit 4 à 12 kW selon le vent, gratuitement. Elle tourne plus fort sous les averses.",
		"size": Vector2i(1, 1), "cost": 2500.0, "plan": "p_eolienne", "h": 7.5, "gen": 8.0, "kind": "wind"},
	"solaire": {"name": "Panneaux solaires", "desc": "Jusqu'à 14 kW gratuits en plein jour ; beaucoup moins sous la pluie, rien la nuit.",
		"size": Vector2i(2, 2), "cost": 4000.0, "plan": "p_solaire", "h": 1.5, "gen": 14.0, "kind": "solar"},
	"compacteuse": {"name": "Compacteuse", "desc": "Compresse 5 lots d'aiguilles vérifiées en une balle, qui se vend 40 % plus cher.",
		"size": Vector2i(2, 2), "cost": 1500.0, "plan": "p_compacteuse", "h": 1.9, "power": 5.0,
		"in": {"acier": 5}, "out": {"balle": 1}, "time": 4.0, "speed": "u_comp_vitesse"},
	"affuteuse": {"name": "Affûteuse", "desc": "Affûte les aiguilles vérifiées : elles se vendent plus du double.",
		"size": Vector2i(2, 2), "cost": 2200.0, "plan": "p_affuteuse", "h": 1.6, "power": 4.0,
		"in": {"acier": 1}, "out": {"affutee": 1}, "time": 2.0, "speed": "u_aff_vitesse", "quality": "u_aff_qualite"},
	"haut_fourneau": {"name": "Haut fourneau", "desc": "Énorme four de 3 × 3 : fond 8 lots vérifiés en 4 lingots bruts d'un coup.",
		"size": Vector2i(3, 3), "cost": 18000.0, "plan": "p_haut_fourneau", "h": 4.5, "power": 14.0,
		"in": {"acier": 8}, "out": {"brut": 4}, "time": 6.0, "speed": "u_hf_vitesse"},
	"epingles": {"name": "Fabrique d'épingles", "desc": "Coupe et étête le fil : une bobine donne une boîte d'épingles.",
		"size": Vector2i(2, 2), "cost": 14000.0, "plan": "p_epingles", "h": 1.8, "power": 6.0,
		"in": {"fil": 1}, "out": {"epingle": 1}, "time": 3.0, "speed": "u_ep_vitesse"},
	"couture": {"name": "Machine à coudre", "desc": "Assemble une boîte d'aiguilles et deux bobines de fil en un kit de couture. Il faut amener les deux sur la même entrée.",
		"size": Vector2i(2, 2), "cost": 60000.0, "plan": "p_couture", "h": 2.0, "power": 8.0,
		"in": {"boite": 1, "fil": 2}, "out": {"kit": 1}, "time": 6.0, "speed": "u_cou_vitesse", "quality": "u_cou_qualite"},
	"emballeuse": {"name": "Emballeuse", "desc": "Met 4 kits de couture en carton d'expédition : le produit le plus cher du jeu.",
		"size": Vector2i(2, 2), "cost": 150000.0, "plan": "p_emballeuse", "h": 2.2, "power": 10.0,
		"in": {"kit": 4}, "out": {"carton": 1}, "time": 8.0, "speed": "u_emb_vitesse"},
	"entrepot": {"name": "Entrepôt", "desc": "Grand hangar de stockage : garde jusqu'à 2 000 objets et les renvoie au rythme du tapis.",
		"size": Vector2i(3, 3), "cost": 9000.0, "plan": "p_entrepot", "h": 3.2, "cap": 2000},
	"ouvrier": {"name": "Cabane d'ouvrier", "desc": "Embauche un ouvrier : il ramasse à la main au pied du tas et va vider sa brouette dans la trémie la plus proche. Il touche un salaire.",
		"size": Vector2i(1, 1), "cost": 800.0, "plan": "p_ouvrier", "h": 1.8},
	"batterie": {"name": "Batterie", "desc": "Stocke l'électricité en trop (le solaire de midi, le vent de la tempête) et la rend quand il en manque.",
		"size": Vector2i(1, 1), "cost": 3500.0, "plan": "p_batterie", "h": 1.5},
	"trieur": {"name": "Trieur", "desc": "Envoie à gauche le type d'objet choisi (touche-le pour le régler), le reste continue tout droit.",
		"size": Vector2i(1, 1), "cost": 150.0, "plan": "p_trieur", "h": 0.6},
	"atelier": {"name": "Atelier de maintenance", "desc": "Répare tout seul les machines usées ou en panne autour de lui.",
		"size": Vector2i(2, 2), "cost": 2500.0, "plan": "p_atelier", "h": 2.4, "power": 4.0},
	"trou": {"name": "Trou de vente", "desc": "Le seul endroit où l'on vend : tout ce qui y tombe est payé.",
		"size": Vector2i(4, 4), "cost": 0.0, "plan": "", "h": 0.4, "fixed": true},
	"bureau": {"name": "Borne des commandes", "desc": "Commande les tas d'aiguilles et accepte des contrats.",
		"size": Vector2i(1, 1), "cost": 0.0, "plan": "", "h": 1.6, "fixed": true},
}
const BUILD_ORDER := ["convoyeur", "express", "ouvrier", "tremie", "scanner", "groupe", "separateur", "trieur", "radar", "bras", "compacteuse",
	"fonderie", "affuteuse", "tampon", "entrepot", "batterie", "eolienne", "atelier", "purif", "pelle", "solaire", "presse", "trefileuse", "drone",
	"haut_fourneau", "epingles", "aiguilleuse", "couture", "emballeuse"]
## Usure : secondes de travail avant la panne (les machines sans valeur ne s'usent pas).
const LIFE := {"scanner": 1500.0, "bras": 1200.0, "pelle": 1500.0, "fonderie": 1500.0, "purif": 1800.0, "presse": 1500.0,
	"trefileuse": 1500.0, "aiguilleuse": 1800.0, "drone": 1500.0, "radar": 3000.0, "groupe": 1200.0, "eolienne": 3600.0,
	"solaire": 4800.0, "compacteuse": 1500.0, "affuteuse": 1200.0, "haut_fourneau": 1800.0, "epingles": 1500.0,
	"couture": 1500.0, "emballeuse": 1800.0}
const MAX_LEVEL := 60
## Niveau de fermier requis par étape de l'arbre (sauf si le nœud en précise un).
const STAGE_LEVEL := [1, 1, 4, 9, 15, 22]
const GRID_POWER := 10.0 # kW fournis gratuitement par le raccordement au réseau (la borne)
const FUEL_COST := 0.05 # € par seconde et par groupe électrogène
const SALARY := 0.04 # € par seconde et par ouvrier
const BATTERY_CAP := 12000.0 # kW·s stockés par batterie (200 kW pendant une minute)
const BATTERY_RATE := 12.0 # kW échangés au plus par batterie

## Arbre technologique par étapes : plans (droit de construire), contrats (tailles de tas) et bonus.
## Chaque plan a ses propres améliorations à niveaux (ups).
const TREE := {
	"p_convoyeur": {"stage": 1, "name": "Plan : Convoyeur et trémie", "cost": 0.0, "req": [], "ups": ["u_conv_vitesse"]},
	"p_scanner": {"stage": 1, "name": "Plan : Scanner", "cost": 120.0, "req": [], "ups": ["u_scan_vitesse"]},
	"c_moyen": {"stage": 1, "name": "Contrat : tas moyen", "cost": 200.0, "req": ["p_scanner"], "piles": 1, "lvl": 3, "ups": []},
	"p_separateur": {"stage": 2, "name": "Plan : Séparateur", "cost": 500.0, "req": ["p_scanner"], "ups": []},
	"p_bras": {"stage": 2, "name": "Plan : Bras robot", "cost": 900.0, "req": ["p_scanner"], "ups": ["u_bras_vitesse", "u_bras_portee"]},
	"p_fonderie": {"stage": 2, "name": "Plan : Fonderie", "cost": 1600.0, "req": ["p_scanner"], "ups": ["u_fond_vitesse", "u_fond_lot", "u_fond_qualite"]},
	"p_tampon": {"stage": 2, "name": "Plan : Stockage tampon", "cost": 1000.0, "req": ["p_separateur"], "ups": []},
	"p_ouvrier": {"stage": 2, "name": "Plan : Ouvriers", "cost": 900.0, "req": ["p_scanner"], "ups": ["u_ouv_vitesse", "u_ouv_charge"]},
	"p_entrepot": {"stage": 3, "name": "Plan : Entrepôt", "cost": 9000.0, "req": ["p_tampon"], "ups": []},
	"p_express": {"stage": 3, "name": "Plan : Tapis express", "cost": 7000.0, "req": ["p_separateur"], "ups": []},
	"p_batterie": {"stage": 3, "name": "Plan : Batteries", "cost": 8000.0, "req": ["p_eolienne"], "ups": ["u_batt_cap"]},
	"p_trieur": {"stage": 2, "name": "Plan : Trieur", "cost": 1400.0, "req": ["p_separateur"], "ups": []},
	"p_compacteuse": {"stage": 2, "name": "Plan : Compacteuse", "cost": 3200.0, "req": ["p_scanner"], "lvl": 6, "ups": ["u_comp_vitesse"]},
	"p_atelier": {"stage": 3, "name": "Plan : Atelier de maintenance", "cost": 10000.0, "req": ["p_bras"], "ups": ["u_atelier_portee", "u_fiabilite"]},
	"p_affuteuse": {"stage": 3, "name": "Plan : Affûteuse", "cost": 5000.0, "req": ["p_compacteuse"], "ups": ["u_aff_vitesse", "u_aff_qualite"]},
	"p_haut_fourneau": {"stage": 4, "name": "Plan : Haut fourneau", "cost": 40000.0, "req": ["p_fonderie", "p_atelier"], "ups": ["u_hf_vitesse"]},
	"p_epingles": {"stage": 4, "name": "Plan : Fabrique d'épingles", "cost": 30000.0, "req": ["p_trefileuse"], "ups": ["u_ep_vitesse"]},
	"p_couture": {"stage": 5, "name": "Plan : Machine à coudre", "cost": 120000.0, "req": ["p_aiguilleuse", "p_epingles"], "lvl": 30, "ups": ["u_cou_vitesse", "u_cou_qualite"]},
	"p_emballeuse": {"stage": 5, "name": "Plan : Emballeuse", "cost": 300000.0, "req": ["p_couture"], "lvl": 36, "ups": ["u_emb_vitesse"]},
	"b_marche": {"stage": 3, "name": "Courtier en aiguilles", "cost": 8000.0, "req": ["p_compacteuse"], "ups": ["u_marche"]},
	"p_energie": {"stage": 1, "name": "Plan : Groupe électrogène", "cost": 150.0, "req": ["p_scanner"], "ups": ["u_energie"]},
	"p_radar": {"stage": 2, "name": "Plan : Radar à foin", "cost": 1800.0, "req": ["p_scanner"], "ups": ["u_radar_portee"]},
	"p_eolienne": {"stage": 2, "name": "Plan : Éolienne", "cost": 3600.0, "req": ["p_energie"], "ups": []},
	"p_solaire": {"stage": 3, "name": "Plan : Panneaux solaires", "cost": 9000.0, "req": ["p_eolienne"], "ups": []},
	"c_gros": {"stage": 3, "lvl": 10, "name": "Contrat : grand tas", "cost": 5000.0, "req": ["c_moyen", "p_bras"], "piles": 2, "ups": []},
	"p_purif": {"stage": 3, "name": "Plan : Purificateur", "cost": 6000.0, "req": ["p_fonderie"], "ups": ["u_purif_vitesse", "u_purif_qualite"]},
	"p_pelle": {"stage": 3, "name": "Plan : Pelleteuse", "cost": 12000.0, "req": ["p_bras"], "ups": ["u_pelle_vitesse", "u_pelle_portee"]},
	"p_presse": {"stage": 4, "name": "Plan : Presse à tôles", "cost": 24000.0, "req": ["p_purif"], "ups": ["u_presse_vitesse", "u_presse_qualite"]},
	"p_trefileuse": {"stage": 4, "name": "Plan : Tréfileuse", "cost": 20000.0, "req": ["p_purif"], "ups": ["u_tref_vitesse", "u_tref_qualite"]},
	"p_drone": {"stage": 4, "name": "Plan : Drone collecteur", "cost": 30000.0, "req": ["p_pelle"], "ups": ["u_drone_vitesse", "u_drone_charge"]},
	"p_aiguilleuse": {"stage": 5, "name": "Plan : Aiguilleuse", "cost": 80000.0, "req": ["p_trefileuse"], "ups": ["u_aig_vitesse", "u_aig_qualite"]},
	"b_trou": {"stage": 5, "name": "Trou de vente élargi", "cost": 50000.0, "req": ["p_presse"], "ups": ["u_trou_prix"]},
	"b_auto": {"stage": 5, "name": "Automatisation avancée", "cost": 160000.0, "req": ["p_drone", "p_presse"], "ups": ["u_auto"]},
	"c_montagne": {"stage": 5, "lvl": 26, "name": "Contrat : montagne", "cost": 240000.0, "req": ["c_gros", "p_pelle", "p_aiguilleuse"], "piles": 3, "ups": []},
}
const STAGES := 5

## Améliorations des plans (achetées dans l'arbre, par niveaux).
const TREE_UPS := {
	"u_conv_vitesse": {"name": "Tapis plus rapides", "base": 60.0, "k": 1.75, "max": 10, "fx": "+20 % de vitesse des convoyeurs"},
	"u_scan_vitesse": {"name": "Scanner plus rapide", "base": 135.0, "k": 1.7, "max": 10, "fx": "+30 % de lots scannés"},
	"u_bras_vitesse": {"name": "Moteurs de bras", "base": 375.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse des bras"},
	"u_bras_portee": {"name": "Bras télescopique", "base": 450.0, "k": 2.0, "max": 5, "fx": "+1 m de portée"},
	"u_fond_vitesse": {"name": "Fonte plus rapide", "base": 600.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse de fonte"},
	"u_fond_lot": {"name": "Grand creuset", "base": 1350.0, "k": 2.2, "max": 4, "fx": "+1 lingot coulé par fournée"},
	"u_fond_qualite": {"name": "Lingots de qualité", "base": 1050.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des lingots bruts"},
	"u_purif_vitesse": {"name": "Électrolyse rapide", "base": 3000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_purif_qualite": {"name": "Pureté 99,9 %", "base": 3750.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des lingots purs"},
	"u_pelle_vitesse": {"name": "Moteur diesel", "base": 6000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse de creusage"},
	"u_pelle_portee": {"name": "Flèche longue", "base": 7500.0, "k": 2.0, "max": 5, "fx": "+1,5 m de portée"},
	"u_presse_vitesse": {"name": "Presse hydraulique", "base": 12000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_presse_qualite": {"name": "Tôles polies", "base": 13500.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des tôles"},
	"u_tref_vitesse": {"name": "Filière diamant", "base": 10500.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_tref_qualite": {"name": "Fil recuit", "base": 12000.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix du fil"},
	"u_aig_vitesse": {"name": "Aiguilleuse rapide", "base": 37500.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_aig_qualite": {"name": "Aiguilles de couture fine", "base": 45000.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des boîtes"},
	"u_drone_vitesse": {"name": "Hélices carbone", "base": 13500.0, "k": 1.7, "max": 8, "fx": "+25 % de vitesse de vol"},
	"u_drone_charge": {"name": "Soute du drone", "base": 18000.0, "k": 1.8, "max": 8, "fx": "+20 000 aiguilles par voyage"},
	"u_trou_prix": {"name": "Acheteurs exigeants", "base": 30000.0, "k": 1.8, "max": 10, "fx": "+5 % sur toutes les ventes"},
	"u_energie": {"name": "Rendement électrique", "base": 900.0, "k": 1.8, "max": 6, "fx": "+15 % d'électricité produite"},
	"u_radar_portee": {"name": "Antenne longue portée", "base": 1800.0, "k": 1.9, "max": 5, "fx": "+3 m de portée du radar"},
	"u_comp_vitesse": {"name": "Compactage rapide", "base": 3750.0, "k": 1.7, "max": 10, "fx": "+25 % de balles par minute"},
	"u_atelier_portee": {"name": "Atelier mobile", "base": 9000.0, "k": 1.8, "max": 5, "fx": "+3 m de rayon de réparation"},
	"u_fiabilite": {"name": "Pièces renforcées", "base": 12000.0, "k": 1.9, "max": 6, "fx": "+30 % de durée de vie des machines"},
	"u_aff_vitesse": {"name": "Meules diamantées", "base": 5000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse d'affûtage"},
	"u_aff_qualite": {"name": "Pointe parfaite", "base": 6000.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des aiguilles affûtées"},
	"u_hf_vitesse": {"name": "Soufflerie", "base": 30000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse du haut fourneau"},
	"u_ep_vitesse": {"name": "Étêteuse rapide", "base": 25000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_cou_vitesse": {"name": "Moteur de couture", "base": 90000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_cou_qualite": {"name": "Kits de luxe", "base": 100000.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des kits"},
	"u_emb_vitesse": {"name": "Plieuse automatique", "base": 200000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse d'emballage"},
	"u_marche": {"name": "Carnet d'adresses", "base": 10000.0, "k": 2.0, "max": 5, "fx": "le marché sature 15 % moins vite"},
	"u_ouv_vitesse": {"name": "Bottes de chantier", "base": 1500.0, "k": 1.7, "max": 8, "fx": "+20 % de vitesse des ouvriers"},
	"u_ouv_charge": {"name": "Grandes brouettes", "base": 2000.0, "k": 1.8, "max": 8, "fx": "+10 000 aiguilles par voyage d'ouvrier"},
	"u_batt_cap": {"name": "Cellules lithium", "base": 9000.0, "k": 1.8, "max": 6, "fx": "+50 % de capacité des batteries"},
	"u_auto": {"name": "Usine optimisée", "base": 90000.0, "k": 2.0, "max": 5, "fx": "+10 % de vitesse pour toutes les machines"},
}

## Boutique : améliorations du joueur.
const SHOP := {
	"main": {"name": "Contenant", "desc": "Seau, brouette, chariot puis benne : porte plus d'aiguilles à la fois.", "base": 40.0, "k": 1.6, "max": 20},
	"poignee": {"name": "Outils de fouille", "desc": "Pelle, fourche, brouette puis aspirateur : plus d'aiguilles et un creux plus large à chaque geste.", "base": 35.0, "k": 1.7, "max": 15},
	"detecteur": {"name": "Détecteur de foin", "desc": "Il bipe quand un brin est caché près de l'endroit visé, de plus en plus vite en s'approchant.", "base": 60.0, "k": 1.7, "max": 8},
	"endurance": {"name": "Endurance", "desc": "Plus d'endurance pour courir et ramasser.", "base": 50.0, "k": 1.5, "max": 15},
	"recup": {"name": "Récupération", "desc": "L'endurance revient plus vite.", "base": 60.0, "k": 1.6, "max": 10},
	"vitesse": {"name": "Bottes de course", "desc": "Tu te déplaces plus vite.", "base": 80.0, "k": 1.6, "max": 10},
	"portee": {"name": "Longs bras", "desc": "Ramasse, dépose et place les objets plus loin.", "base": 70.0, "k": 1.7, "max": 10},
	"oeil": {"name": "Œil de lynx", "desc": "Plus de chances de repérer le foin directement à la main.", "base": 120.0, "k": 1.8, "max": 8},
	"crampons": {"name": "Crampons", "desc": "Grimpe sur les flancs du tas pour aller creuser là où le détecteur bipe.", "base": 400.0, "k": 3.0, "max": 3},
	"tremie_cap": {"name": "Trémies géantes", "desc": "+300 aiguilles dans chaque trémie.", "base": 150.0, "k": 1.6, "max": 10},
}
const SHOP_ORDER := ["main", "poignee", "detecteur", "endurance", "recup", "vitesse", "portee", "oeil", "crampons", "tremie_cap"]

const ACHIEVEMENTS := [
	["first_hay", "Premier brin", "Trouve ton premier brin de foin."],
	["first_belt", "Ça roule", "Pose ton premier convoyeur."],
	["first_scan", "Bip bip", "Fais détecter du foin par un scanner."],
	["first_pile", "Botte vidée", "Trouve les 22 brins d'un tas."],
	["first_ingot", "Métallurgiste", "Coule ton premier lingot."],
	["belts_100", "Réseau routier", "Pose 100 convoyeurs."],
	["pure_100", "Pur et dur", "Vends 100 lingots purs."],
	["contract_5", "Homme d'affaires", "Termine 5 contrats."],
	["needles_100k", "Mangeur d'aiguilles", "Ramasse 100 millions d'aiguilles."],
	["boite_1", "La boucle est bouclée", "Vends une boîte d'aiguilles neuves."],
	["money_1m", "Millionnaire", "Gagne 1 000 000 € au total."],
	["mountain", "Au sommet", "Termine une montagne d'aiguilles."],
	["golden", "De l'or dans les aiguilles", "Trouve un brin de foin doré."],
	["recycle", "Nouveau départ", "Recycle ton usine une première fois."],
	["level_10", "Fermier confirmé", "Atteins le niveau 10."],
	["level_30", "Maître des aiguilles", "Atteins le niveau 30."],
	["repair_10", "Mécano", "Répare 10 machines en panne."],
	["carton_1", "Expéditeur", "Vends un carton d'expédition."],
	["ouvriers_5", "Chef de chantier", "Emploie 5 ouvriers en même temps."],
	["battery_full", "Plein d'énergie", "Remplis entièrement une batterie."],
	["events_10", "Imprévus maîtrisés", "Vis 10 événements."],
]

## Objectifs guidés : [identifiant, texte, récompense]. La progression est calculée par Game.quest_progress().
const QUESTS := [
	["grab30", "Ramasse 30 000 aiguilles dans le tas", 15.0],
	["tremie", "Verse tes aiguilles dans la trémie", 15.0],
	["sell10", "Gagne 10 € grâce au trou de vente", 25.0],
	["plan_scan", "Achète le plan du Scanner (Arbre)", 30.0],
	["scanner", "Pose un scanner sur la ligne de tapis", 40.0],
	["hay3", "Trouve 3 brins de foin", 60.0],
	["belts10", "Pose 10 convoyeurs", 60.0],
	["ouvrier", "Embauche un ouvrier (cabane d'ouvrier)", 120.0],
	["pile1", "Termine ton premier tas (22 brins)", 150.0],
	["fonderie", "Construis une fonderie", 150.0],
	["energie", "Construis un générateur (groupe, éolienne ou solaire)", 150.0],
	["ingot", "Vends un lingot brut", 200.0],
	["bras", "Construis un bras robot près du tas", 300.0],
	["radar", "Construis un radar à foin", 400.0],
	["moyen", "Commande un tas moyen", 400.0],
	["contrat", "Remplis un contrat de livraison", 600.0],
	["balle", "Vends une balle d'aiguilles compressées", 800.0],
	["purif", "Construis un purificateur", 1000.0],
	["atelier", "Construis un atelier de maintenance", 1500.0],
	["gros", "Commande un grand tas", 2500.0],
	["presse", "Vends une tôle d'acier", 5000.0],
	["boite", "Vends une boîte d'aiguilles neuves", 20000.0],
	["kit", "Vends un kit de couture", 60000.0],
	["montagne", "Commande la montagne d'aiguilles", 50000.0],
]
