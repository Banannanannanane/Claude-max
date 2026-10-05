class_name Data
## Tables de jeu : tas, objets, machines, arbre technologique, boutique, succès.

const PILE_POS := Vector3(0, 0, -16)
const HAY_PER_PILE := 22
const FIELD := 46 # demi-côté du terrain constructible (cases)
const LOT := 10 # aiguilles par lot transporté sur les tapis

## Directions de la grille : 0 nord (-Z), 1 est (+X), 2 sud (+Z), 3 ouest (-X).
const DIRS := [Vector2i(0, -1), Vector2i(1, 0), Vector2i(0, 1), Vector2i(-1, 0)]

const PILE_ORDER := ["petit", "moyen", "gros", "enorme", "montagne"]
const PILES := {
	"petit": {"name": "Petit tas", "needles": 600, "radius": 2.6, "hay_value": 30.0, "order": 0.0, "right": ""},
	"moyen": {"name": "Tas moyen", "needles": 2500, "radius": 3.8, "hay_value": 80.0, "order": 200.0, "right": "c_moyen"},
	"gros": {"name": "Gros tas", "needles": 10000, "radius": 5.6, "hay_value": 220.0, "order": 2000.0, "right": "c_gros"},
	"enorme": {"name": "Tas énorme", "needles": 40000, "radius": 8.0, "hay_value": 600.0, "order": 15000.0, "right": "c_enorme"},
	"montagne": {"name": "Montagne d'aiguilles", "needles": 200000, "radius": 11.5, "hay_value": 2000.0, "order": 120000.0, "right": "c_montagne"},
}

## Objets qui circulent sur les tapis (prix = vente dans le trou, pour un lot / une pièce).
const ITEMS := {
	"vrac": {"name": "Aiguilles en vrac", "price": 2.0, "color": Color(0.55, 0.56, 0.58), "scale": Vector3(0.5, 0.12, 0.2)},
	"acier": {"name": "Aiguilles vérifiées", "price": 4.0, "color": Color(0.78, 0.8, 0.84), "scale": Vector3(0.5, 0.12, 0.2)},
	"brut": {"name": "Lingot brut", "price": 10.0, "color": Color(0.42, 0.38, 0.35), "scale": Vector3(0.36, 0.14, 0.2)},
	"pur": {"name": "Lingot pur", "price": 22.0, "color": Color(0.9, 0.92, 0.95), "scale": Vector3(0.36, 0.14, 0.2)},
	"tole": {"name": "Tôle d'acier", "price": 55.0, "color": Color(0.55, 0.62, 0.72), "scale": Vector3(0.55, 0.04, 0.5)},
	"fil": {"name": "Bobine de fil", "price": 28.0, "color": Color(0.85, 0.55, 0.3), "scale": Vector3(0.3, 0.3, 0.3)},
	"boite": {"name": "Boîte d'aiguilles neuves", "price": 80.0, "color": Color(0.85, 0.2, 0.25), "scale": Vector3(0.36, 0.22, 0.3)},
}
const ITEM_ORDER := ["vrac", "acier", "brut", "pur", "tole", "fil", "boite"]

## Machines. size = (largeur, profondeur) en cases, toujours impaires ; l'entrée est à l'arrière,
## la sortie à l'avant. plan = nœud de l'arbre qui donne le droit de construire.
## recipe : entrée (type -> quantité), sortie (type -> quantité), durée en secondes.
const MACHINES := {
	"convoyeur": {"name": "Convoyeur", "desc": "Tapis roulant : transporte les objets dans le sens des flèches.",
		"size": Vector2i(1, 1), "cost": 5.0, "plan": "p_convoyeur", "h": 0.3},
	"separateur": {"name": "Séparateur", "desc": "Répartit les objets à gauche, devant et à droite, chacun son tour.",
		"size": Vector2i(1, 1), "cost": 60.0, "plan": "p_separateur", "h": 0.6},
	"tremie": {"name": "Trémie", "desc": "Dépose tes aiguilles dedans : elle les envoie sur le tapis, lot par lot.",
		"size": Vector2i(3, 3), "cost": 250.0, "plan": "p_convoyeur", "h": 1.6, "cap": 600},
	"scanner": {"name": "Scanner", "desc": "Détecte le foin caché dans les aiguilles en vrac. Les aiguilles ressortent vérifiées.",
		"size": Vector2i(1, 3), "cost": 150.0, "plan": "p_scanner", "h": 2.0,
		"in": {"vrac": 1}, "out": {"acier": 1}, "time": 1.2, "speed": "u_scan_vitesse"},
	"bras": {"name": "Bras robot", "desc": "Ramasse les aiguilles du tas et les pose sur le tapis de devant. À placer près du tas.",
		"size": Vector2i(1, 1), "cost": 400.0, "plan": "p_bras", "h": 1.0, "dig": 2.0, "range": "u_bras_portee", "speed": "u_bras_vitesse"},
	"pelle": {"name": "Pelleteuse", "desc": "Creuse le tas par grosses pelletées. À placer près du tas.",
		"size": Vector2i(3, 3), "cost": 6000.0, "plan": "p_pelle", "h": 2.4, "dig": 0.6, "range": "u_pelle_portee", "speed": "u_pelle_vitesse", "lot_mult": 3},
	"fonderie": {"name": "Fonderie", "desc": "Fond les aiguilles vérifiées en lingots bruts (jamais de vrac : le foin brûlerait !).",
		"size": Vector2i(3, 3), "cost": 800.0, "plan": "p_fonderie", "h": 2.6,
		"in": {"acier": 1}, "out": {"brut": 1}, "time": 3.0, "speed": "u_fond_vitesse", "batch": "u_fond_lot", "quality": "u_fond_qualite"},
	"purif": {"name": "Purificateur", "desc": "Purifie les lingots bruts en lingots purs.",
		"size": Vector2i(3, 3), "cost": 3000.0, "plan": "p_purif", "h": 3.0,
		"in": {"brut": 1}, "out": {"pur": 1}, "time": 3.0, "speed": "u_purif_vitesse", "quality": "u_purif_qualite"},
	"presse": {"name": "Presse à tôles", "desc": "Lamine 2 lingots purs en une tôle d'acier.",
		"size": Vector2i(3, 3), "cost": 12000.0, "plan": "p_presse", "h": 2.6,
		"in": {"pur": 2}, "out": {"tole": 1}, "time": 4.0, "speed": "u_presse_vitesse", "quality": "u_presse_qualite"},
	"trefileuse": {"name": "Tréfileuse", "desc": "Étire un lingot pur en deux bobines de fil.",
		"size": Vector2i(3, 3), "cost": 10000.0, "plan": "p_trefileuse", "h": 2.2,
		"in": {"pur": 1}, "out": {"fil": 2}, "time": 3.0, "speed": "u_tref_vitesse", "quality": "u_tref_qualite"},
	"aiguilleuse": {"name": "Aiguilleuse", "desc": "Fabrique des boîtes d'aiguilles neuves avec le fil. Ironique, non ?",
		"size": Vector2i(3, 3), "cost": 40000.0, "plan": "p_aiguilleuse", "h": 2.4,
		"in": {"fil": 1}, "out": {"boite": 1}, "time": 5.0, "speed": "u_aig_vitesse", "quality": "u_aig_qualite"},
	"tampon": {"name": "Stockage tampon", "desc": "Accumule jusqu'à 120 objets et les relâche au rythme du tapis.",
		"size": Vector2i(3, 3), "cost": 500.0, "plan": "p_tampon", "h": 2.2, "cap": 120},
	"drone": {"name": "Drone collecteur", "desc": "Fait la navette entre le tas et la trémie la plus proche.",
		"size": Vector2i(1, 1), "cost": 15000.0, "plan": "p_drone", "h": 0.3},
	"trou": {"name": "Trou de vente", "desc": "Le seul endroit où l'on vend : tout ce qui y tombe est payé.",
		"size": Vector2i(5, 5), "cost": 0.0, "plan": "", "h": 0.4, "fixed": true},
	"bureau": {"name": "Bureau des commandes", "desc": "Commande les tas d'aiguilles et accepte des contrats.",
		"size": Vector2i(3, 3), "cost": 0.0, "plan": "", "h": 2.5, "fixed": true},
}
const BUILD_ORDER := ["convoyeur", "tremie", "scanner", "separateur", "bras", "fonderie", "tampon", "purif",
	"pelle", "presse", "trefileuse", "drone", "aiguilleuse"]

## Arbre technologique par étapes : plans (droit de construire), contrats (tailles de tas) et bonus.
## Chaque plan a ses propres améliorations à niveaux (ups).
const TREE := {
	"p_convoyeur": {"stage": 1, "name": "Plan : Convoyeur et trémie", "cost": 0.0, "req": [], "ups": ["u_conv_vitesse"]},
	"p_scanner": {"stage": 1, "name": "Plan : Scanner", "cost": 120.0, "req": [], "ups": ["u_scan_vitesse"]},
	"c_moyen": {"stage": 1, "name": "Contrat : tas moyen", "cost": 200.0, "req": ["p_scanner"], "piles": 1, "ups": []},
	"p_separateur": {"stage": 2, "name": "Plan : Séparateur", "cost": 250.0, "req": ["p_scanner"], "ups": []},
	"p_bras": {"stage": 2, "name": "Plan : Bras robot", "cost": 450.0, "req": ["p_scanner"], "ups": ["u_bras_vitesse", "u_bras_portee"]},
	"p_fonderie": {"stage": 2, "name": "Plan : Fonderie", "cost": 800.0, "req": ["p_scanner"], "ups": ["u_fond_vitesse", "u_fond_lot", "u_fond_qualite"]},
	"p_tampon": {"stage": 2, "name": "Plan : Stockage tampon", "cost": 500.0, "req": ["p_separateur"], "ups": []},
	"c_gros": {"stage": 3, "name": "Contrat : gros tas", "cost": 2500.0, "req": ["c_moyen", "p_bras"], "piles": 2, "ups": []},
	"p_purif": {"stage": 3, "name": "Plan : Purificateur", "cost": 3000.0, "req": ["p_fonderie"], "ups": ["u_purif_vitesse", "u_purif_qualite"]},
	"p_pelle": {"stage": 3, "name": "Plan : Pelleteuse", "cost": 6000.0, "req": ["p_bras"], "ups": ["u_pelle_vitesse", "u_pelle_portee"]},
	"p_presse": {"stage": 4, "name": "Plan : Presse à tôles", "cost": 12000.0, "req": ["p_purif"], "ups": ["u_presse_vitesse", "u_presse_qualite"]},
	"p_trefileuse": {"stage": 4, "name": "Plan : Tréfileuse", "cost": 10000.0, "req": ["p_purif"], "ups": ["u_tref_vitesse", "u_tref_qualite"]},
	"p_drone": {"stage": 4, "name": "Plan : Drone collecteur", "cost": 15000.0, "req": ["p_pelle"], "ups": ["u_drone_vitesse", "u_drone_charge"]},
	"c_enorme": {"stage": 4, "name": "Contrat : tas énorme", "cost": 15000.0, "req": ["c_gros", "p_pelle"], "piles": 3, "ups": []},
	"p_aiguilleuse": {"stage": 5, "name": "Plan : Aiguilleuse", "cost": 40000.0, "req": ["p_trefileuse"], "ups": ["u_aig_vitesse", "u_aig_qualite"]},
	"b_trou": {"stage": 5, "name": "Trou de vente élargi", "cost": 25000.0, "req": ["p_presse"], "ups": ["u_trou_prix"]},
	"b_auto": {"stage": 5, "name": "Automatisation avancée", "cost": 80000.0, "req": ["p_drone", "p_presse"], "ups": ["u_auto"]},
	"c_montagne": {"stage": 5, "name": "Contrat : montagne", "cost": 120000.0, "req": ["c_enorme", "p_aiguilleuse"], "piles": 4, "ups": []},
}
const STAGES := 5

## Améliorations des plans (achetées dans l'arbre, par niveaux).
const TREE_UPS := {
	"u_conv_vitesse": {"name": "Tapis plus rapides", "base": 40.0, "k": 1.75, "max": 10, "fx": "+20 % de vitesse des convoyeurs"},
	"u_scan_vitesse": {"name": "Scanner plus rapide", "base": 90.0, "k": 1.7, "max": 10, "fx": "+30 % de lots scannés"},
	"u_bras_vitesse": {"name": "Moteurs de bras", "base": 250.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse des bras"},
	"u_bras_portee": {"name": "Bras télescopique", "base": 300.0, "k": 2.0, "max": 5, "fx": "+1 m de portée"},
	"u_fond_vitesse": {"name": "Fonte plus rapide", "base": 400.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse de fonte"},
	"u_fond_lot": {"name": "Grand creuset", "base": 900.0, "k": 2.2, "max": 4, "fx": "+1 lingot coulé par fournée"},
	"u_fond_qualite": {"name": "Lingots de qualité", "base": 700.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des lingots bruts"},
	"u_purif_vitesse": {"name": "Électrolyse rapide", "base": 2000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_purif_qualite": {"name": "Pureté 99,9 %", "base": 2500.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des lingots purs"},
	"u_pelle_vitesse": {"name": "Moteur diesel", "base": 4000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse de creusage"},
	"u_pelle_portee": {"name": "Flèche longue", "base": 5000.0, "k": 2.0, "max": 5, "fx": "+1,5 m de portée"},
	"u_presse_vitesse": {"name": "Presse hydraulique", "base": 8000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_presse_qualite": {"name": "Tôles polies", "base": 9000.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des tôles"},
	"u_tref_vitesse": {"name": "Filière diamant", "base": 7000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_tref_qualite": {"name": "Fil recuit", "base": 8000.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix du fil"},
	"u_aig_vitesse": {"name": "Aiguilleuse rapide", "base": 25000.0, "k": 1.7, "max": 10, "fx": "+25 % de vitesse"},
	"u_aig_qualite": {"name": "Aiguilles de couture fine", "base": 30000.0, "k": 1.9, "max": 5, "fx": "+10 % sur le prix des boîtes"},
	"u_drone_vitesse": {"name": "Hélices carbone", "base": 9000.0, "k": 1.7, "max": 8, "fx": "+25 % de vitesse de vol"},
	"u_drone_charge": {"name": "Soute du drone", "base": 12000.0, "k": 1.8, "max": 8, "fx": "+20 aiguilles par voyage"},
	"u_trou_prix": {"name": "Acheteurs exigeants", "base": 20000.0, "k": 1.8, "max": 10, "fx": "+5 % sur toutes les ventes"},
	"u_auto": {"name": "Usine optimisée", "base": 60000.0, "k": 2.0, "max": 5, "fx": "+10 % de vitesse pour toutes les machines"},
}

## Boutique : améliorations du joueur.
const SHOP := {
	"main": {"name": "Capacité de la main", "desc": "Porte plus d'aiguilles à la fois.", "base": 40.0, "k": 1.6, "max": 20},
	"poignee": {"name": "Fourche à aiguilles", "desc": "Ramasse plus d'aiguilles à chaque geste.", "base": 35.0, "k": 1.7, "max": 15},
	"endurance": {"name": "Endurance", "desc": "Plus d'endurance pour courir et ramasser.", "base": 50.0, "k": 1.5, "max": 15},
	"recup": {"name": "Récupération", "desc": "L'endurance revient plus vite.", "base": 60.0, "k": 1.6, "max": 10},
	"vitesse": {"name": "Bottes de course", "desc": "Tu te déplaces plus vite.", "base": 80.0, "k": 1.6, "max": 10},
	"portee": {"name": "Longs bras", "desc": "Ramasse, dépose et place les objets plus loin.", "base": 70.0, "k": 1.7, "max": 10},
	"oeil": {"name": "Œil de lynx", "desc": "Plus de chances de repérer le foin directement à la main.", "base": 120.0, "k": 1.8, "max": 8},
	"tremie_cap": {"name": "Trémies géantes", "desc": "+300 aiguilles dans chaque trémie.", "base": 150.0, "k": 1.6, "max": 10},
}
const SHOP_ORDER := ["main", "poignee", "endurance", "recup", "vitesse", "portee", "oeil", "tremie_cap"]

const ACHIEVEMENTS := [
	["first_hay", "Premier brin", "Trouve ton premier brin de foin."],
	["first_belt", "Ça roule", "Pose ton premier convoyeur."],
	["first_scan", "Bip bip", "Fais détecter du foin par un scanner."],
	["first_pile", "Botte vidée", "Trouve les 22 brins d'un tas."],
	["first_ingot", "Métallurgiste", "Coule ton premier lingot."],
	["belts_100", "Réseau routier", "Pose 100 convoyeurs."],
	["pure_100", "Pur et dur", "Vends 100 lingots purs."],
	["contract_5", "Homme d'affaires", "Termine 5 contrats."],
	["needles_100k", "Mangeur d'aiguilles", "Ramasse 100 000 aiguilles."],
	["boite_1", "La boucle est bouclée", "Vends une boîte d'aiguilles neuves."],
	["money_1m", "Millionnaire", "Gagne 1 000 000 € au total."],
	["mountain", "Au sommet", "Termine une montagne d'aiguilles."],
]
