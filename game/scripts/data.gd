class_name Data
## Tables de jeu : tailles de tas, bâtiments, arbre de progression, améliorations.

const PILE_POS := Vector3(0, 0, -16)
const HAY_PER_PILE := 22
const FIELD := 46.0 # demi-côté du terrain constructible
const NEEDLES_PER_INGOT := 10

const PRICE_NEEDLE := 0.4 # aiguille vérifiée
const PRICE_RAW := 6.0 # lingot brut
const PRICE_PURE := 16.0 # lingot pur

const PILE_ORDER := ["petit", "moyen", "gros", "enorme", "montagne"]
const PILES := {
	"petit": {"name": "Petit tas", "needles": 400, "radius": 2.2, "hay_value": 25.0, "order": 0.0, "right": ""},
	"moyen": {"name": "Tas moyen", "needles": 1500, "radius": 3.6, "hay_value": 60.0, "order": 150.0, "right": "tas_moyen"},
	"gros": {"name": "Gros tas", "needles": 6000, "radius": 5.4, "hay_value": 160.0, "order": 1200.0, "right": "tas_gros"},
	"enorme": {"name": "Tas énorme", "needles": 25000, "radius": 8.0, "hay_value": 450.0, "order": 9000.0, "right": "tas_enorme"},
	"montagne": {"name": "Montagne d'aiguilles", "needles": 120000, "radius": 11.5, "hay_value": 1400.0, "order": 70000.0, "right": "montagne"},
}

## size = empreinte au sol (x, z) en mètres.
const BUILDINGS := {
	"table": {"name": "Table de tri", "desc": "Dépose tes aiguilles ici : elles sont vérifiées une par une. Le foin caché y est repéré.",
		"cost": 120.0, "right": "", "size": Vector2(2.2, 1.3), "buildable": true},
	"entrepot": {"name": "Entrepôt", "desc": "Stocke les aiguilles et les lingots. Chaque entrepôt ajoute de la place.",
		"cost": 600.0, "right": "r_entrepot", "size": Vector2(4.2, 5.2), "buildable": true},
	"verif": {"name": "Vérificateur", "desc": "Scanne les aiguilles en vrac de l'entrepôt et détecte le foin caché.",
		"cost": 250.0, "right": "r_verif", "size": Vector2(2.8, 1.5), "buildable": true},
	"bras": {"name": "Bras robot", "desc": "Ramasse les aiguilles du tas tout seul. À placer près du tas.",
		"cost": 400.0, "right": "r_bras", "size": Vector2(1.6, 1.6), "buildable": true, "range": 6.0},
	"pelle": {"name": "Pelleteuse", "desc": "Creuse le tas par grosses pelletées. À placer près du tas.",
		"cost": 7000.0, "right": "r_pelle", "size": Vector2(2.6, 3.6), "buildable": true, "range": 9.0},
	"fonderie": {"name": "Fonderie", "desc": "Fait fondre les aiguilles vérifiées : 10 aiguilles = 1 lingot brut.",
		"cost": 1200.0, "right": "r_fonderie", "size": Vector2(3.0, 3.0), "buildable": true},
	"purif": {"name": "Purificateur de métal", "desc": "Purifie les lingots bruts en lingots purs, bien plus chers.",
		"cost": 5000.0, "right": "r_purif", "size": Vector2(2.8, 2.8), "buildable": true},
	"vendeur": {"name": "Camion de vente", "desc": "Vend automatiquement lingots et aiguilles vérifiées.",
		"cost": 3000.0, "right": "r_vendeur", "size": Vector2(2.6, 5.0), "buildable": true},
	"comptoir": {"name": "Comptoir de vente", "desc": "Vends ici ta production.", "cost": 0.0, "right": "",
		"size": Vector2(3.2, 1.6), "buildable": false},
	"bureau": {"name": "Bureau des commandes", "desc": "Commande un nouveau tas d'aiguilles.", "cost": 0.0, "right": "",
		"size": Vector2(2.6, 2.6), "buildable": false},
}
const BUILD_ORDER := ["table", "verif", "entrepot", "bras", "fonderie", "vendeur", "purif", "pelle"]

## Arbre de progression : droits de construction et grands déblocages.
## pos = (colonne, ligne) dans l'affichage.
const TREE := {
	"licence": {"name": "Licence agricole", "desc": "Le droit de fouiller des tas d'aiguilles.", "cost": 0.0, "req": [], "pos": Vector2(0, 1)},
	"r_verif": {"name": "Droit : Vérificateur", "desc": "Autorise la construction de vérificateurs.", "cost": 150.0, "req": ["licence"], "pos": Vector2(1, 0)},
	"tas_moyen": {"name": "Tas moyen", "desc": "Autorise la commande de tas moyens (1 500 aiguilles). Termine d'abord un tas.", "cost": 200.0, "req": ["licence"], "pos": Vector2(1, 1), "piles": 1},
	"r_entrepot": {"name": "Droit : Entrepôt", "desc": "Autorise la construction d'entrepôts supplémentaires.", "cost": 300.0, "req": ["licence"], "pos": Vector2(1, 2)},
	"r_bras": {"name": "Droit : Bras robot", "desc": "Autorise les bras robots qui ramassent à ta place.", "cost": 450.0, "req": ["r_verif"], "pos": Vector2(2, 0)},
	"r_fonderie": {"name": "Droit : Fonderie", "desc": "Autorise la fonte des aiguilles en lingots.", "cost": 900.0, "req": ["r_verif"], "pos": Vector2(2, 1)},
	"tri_express": {"name": "Tri express", "desc": "Les tables de tri vont deux fois plus vite.", "cost": 400.0, "req": ["r_entrepot"], "pos": Vector2(2, 2)},
	"r_pelle": {"name": "Droit : Pelleteuse", "desc": "Autorise les pelleteuses, de vraies machines à creuser.", "cost": 6000.0, "req": ["r_bras"], "pos": Vector2(3, 0)},
	"tas_gros": {"name": "Gros tas", "desc": "Autorise la commande de gros tas (6 000 aiguilles).", "cost": 2500.0, "req": ["tas_moyen", "r_bras"], "pos": Vector2(3, 1), "piles": 2},
	"r_purif": {"name": "Droit : Purificateur", "desc": "Autorise la purification des lingots.", "cost": 4000.0, "req": ["r_fonderie"], "pos": Vector2(3, 2)},
	"tas_enorme": {"name": "Tas énorme", "desc": "Autorise la commande de tas énormes (25 000 aiguilles).", "cost": 20000.0, "req": ["tas_gros", "r_pelle"], "pos": Vector2(4, 0), "piles": 3},
	"r_vendeur": {"name": "Droit : Camion de vente", "desc": "Autorise la vente automatique par camion.", "cost": 2500.0, "req": ["r_fonderie"], "pos": Vector2(4, 1)},
	"contrats": {"name": "Contrats premium", "desc": "+25 % sur tous les prix de vente.", "cost": 8000.0, "req": ["r_purif"], "pos": Vector2(4, 2)},
	"auto": {"name": "Automatisation avancée", "desc": "Toutes les machines vont 50 % plus vite.", "cost": 30000.0, "req": ["r_pelle", "r_vendeur"], "pos": Vector2(5, 0)},
	"montagne": {"name": "Montagne d'aiguilles", "desc": "Autorise la commande d'une montagne (120 000 aiguilles).", "cost": 150000.0, "req": ["tas_enorme", "r_purif"], "pos": Vector2(5, 1), "piles": 4},
	"magnat": {"name": "Magnat de l'aiguille", "desc": "+50 % sur la valeur du foin.", "cost": 60000.0, "req": ["contrats"], "pos": Vector2(5, 2)},
}

## Améliorations du joueur et des machines (boutique). base * k^niveau.
const UPGRADES := {
	"main": {"name": "Capacité de la main", "desc": "Porte plus d'aiguilles à la fois.", "base": 40.0, "k": 1.6, "max": 20, "cat": "joueur"},
	"poignee": {"name": "Grosses poignées", "desc": "Ramasse plus d'aiguilles à chaque geste.", "base": 35.0, "k": 1.7, "max": 15, "cat": "joueur"},
	"endurance": {"name": "Endurance", "desc": "Plus d'endurance pour courir et ramasser.", "base": 50.0, "k": 1.5, "max": 15, "cat": "joueur"},
	"recup": {"name": "Récupération", "desc": "L'endurance revient plus vite.", "base": 60.0, "k": 1.6, "max": 10, "cat": "joueur"},
	"vitesse": {"name": "Vitesse de marche", "desc": "Tu te déplaces plus vite.", "base": 80.0, "k": 1.6, "max": 10, "cat": "joueur"},
	"portee": {"name": "Longs bras", "desc": "Ramasse, dépose et place les objets plus loin.", "base": 70.0, "k": 1.7, "max": 10, "cat": "joueur"},
	"oeil": {"name": "Œil de lynx", "desc": "Plus de chances de repérer le foin directement à la main.", "base": 120.0, "k": 1.8, "max": 8, "cat": "joueur"},
	"etageres": {"name": "Étagères d'entrepôt", "desc": "+150 places dans chaque entrepôt.", "base": 100.0, "k": 1.5, "max": 30, "cat": "joueur"},
	"tri": {"name": "Tri rapide", "desc": "Les tables de tri vérifient plus vite.", "base": 60.0, "k": 1.6, "max": 15, "cat": "joueur"},
	"m_bras": {"name": "Moteurs de bras robot", "desc": "+25 % de vitesse pour les bras robots.", "base": 300.0, "k": 1.6, "max": 15, "cat": "machines", "right": "r_bras"},
	"m_pelle": {"name": "Godet géant", "desc": "+25 % pour les pelleteuses.", "base": 5000.0, "k": 1.6, "max": 15, "cat": "machines", "right": "r_pelle"},
	"m_verif": {"name": "Scanner à rayons X", "desc": "+30 % de vitesse pour les vérificateurs.", "base": 200.0, "k": 1.6, "max": 15, "cat": "machines", "right": "r_verif"},
	"m_fonderie": {"name": "Brûleurs haute température", "desc": "+25 % pour les fonderies.", "base": 800.0, "k": 1.6, "max": 15, "cat": "machines", "right": "r_fonderie"},
	"m_purif": {"name": "Filtres à électrolyse", "desc": "+25 % pour les purificateurs.", "base": 3000.0, "k": 1.6, "max": 15, "cat": "machines", "right": "r_purif"},
	"m_vendeur": {"name": "Négociateur", "desc": "+5 % sur les prix de vente, camion plus rapide.", "base": 1500.0, "k": 1.7, "max": 15, "cat": "machines", "right": "r_vendeur"},
}
const UPGRADE_ORDER := ["main", "poignee", "endurance", "recup", "vitesse", "portee", "oeil", "etageres", "tri",
	"m_bras", "m_pelle", "m_verif", "m_fonderie", "m_purif", "m_vendeur"]
