extends Node
## État de la partie et simulation de l'usine : grille, convoyeurs, machines, trou de vente,
## arbre technologique, contrats, succès et sauvegardes (3 emplacements).

signal changed
signal toast(text: String, gold: bool)
signal hay_found(count: int, by_hand: bool)
signal entities_changed
signal pile_changed
signal sold(cell: Vector2i, item_type: String)
signal golden_found
signal recycled

const STEP := 1.0 / 30.0
const MAX_OFFLINE := 8.0 * 3600.0
const SLOTS := 3

# --- économie
var money := 0.0
var hand_n := 0
var hand_h := 0

# --- tas en cours
var pile_size := "petit"
var pile_total := 600
var pile_n := 600
var pile_h := 22
var pile_found := 0
var pile_done := false
var pile_gold := 0 # rang du brin doré dans ce tas (0 = aucun)
var field := PileField.new() # relief du tas, creusé localement
var hay_spots: Array = [] # position (monde) de chaque brin encore caché ; autant que pile_h
var _take_carry := 0.0
var _relax_acc := 0.0
var prestige := 0 # jetons de recyclage (bonus permanent)
var run_earned := 0.0 # argent gagné depuis le dernier recyclage

# --- progression
var shop := {} # niveaux de la boutique
var tree := {"p_convoyeur": true} # nœuds achetés
var ups := {} # niveaux des améliorations de plans
var achievements := {}
var stats := {}
var settings := {"sound": true, "music": true, "weather": true, "sens": 1.0, "daynight": true, "quality": 1, "fps": false, "vibrate": true}
var contract := {} # contrat en cours
var offers: Array = [] # contrats proposés
var rates := {"income": 0.0, "dig": 0.0, "hay": 0.0}
var quest := 0
var slot := 1
var last_save := 0

# --- usine
var entities := {} # id -> Dictionary
var grid := {} # Vector2i -> id
var next_id := 1
var _belt_order: Array = []
var _order_dirty := true

var offline := false
var _acc := 0.0
var _clock := 0.0
var _save_acc := 0.0
var _changed_acc := 0.0
var _sec_acc := 0.0
var _rate_acc := 0.0
var _rate_base := {}
var _lost_toast := -100.0
var _activity := {} # id -> horloge du dernier travail


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	entities_changed.connect(_update_power)
	_load_device()
	slot = _last_slot()
	if not load_slot(slot):
		new_game()


## Réglages propres à l'appareil (qualité, vibration, compteur) : communs à toutes les sauvegardes.
const DEVICE_KEYS := ["quality", "fps", "vibrate", "sound", "music", "weather"]
const DEVICE_PATH := "user://device.json"


func _load_device() -> void:
	var d = _read_json(DEVICE_PATH)
	if typeof(d) != TYPE_DICTIONARY:
		return
	for k in DEVICE_KEYS:
		if d.has(k):
			settings[k] = d[k]
	settings.quality = clampi(int(settings.quality), 0, 2)
	settings.fps = bool(settings.fps)
	for k in ["fps", "vibrate", "sound", "music", "weather"]:
		settings[k] = bool(settings[k])


func save_device() -> void:
	var d := {}
	for k in DEVICE_KEYS:
		d[k] = settings[k]
	var f := FileAccess.open(DEVICE_PATH, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(d))


# ============================================================ nouvelle partie
func _blank_stats() -> Dictionary:
	return {"needles": 0, "hay": 0, "piles": 0, "earned": 0.0, "ingots": 0, "time": 0.0, "belts": 0,
		"contracts": 0, "sold": {}, "lost_hay": 0, "poured": 0, "ordered": {}, "golden": 0, "recycles": 0}


const START_TREMIE := Vector2i(0, -28) # trémie de départ, au pied du petit tas


## keep_meta : recyclage — on garde succès, statistiques et jetons.
func new_game(keep_meta := false) -> void:
	money = 0.0
	hand_n = 0
	hand_h = 0
	shop = {}
	tree = {"p_convoyeur": true}
	ups = {}
	run_earned = 0.0
	if not keep_meta:
		achievements = {}
		stats = _blank_stats()
		prestige = 0
	contract = {}
	offers = []
	rates = {"income": 0.0, "dig": 0.0, "hay": 0.0}
	quest = 0
	entities = {}
	grid = {}
	next_id = 1
	_set_pile("petit")
	# installation de départ : une trémie près du tas, un tapis jusqu'au trou de vente
	_add("trou", Vector2i(12, -2), 0)
	_add("bureau", Vector2i(-6, 2), 2)
	_add("tremie", START_TREMIE, 1)
	for x in range(START_TREMIE.x + 1, 12):
		_add("convoyeur", Vector2i(x, START_TREMIE.y), 1)
	for z in range(START_TREMIE.y, -3):
		_add("convoyeur", Vector2i(12, z), 2)
	_roll_offers()
	_order_dirty = true
	entities_changed.emit()
	pile_changed.emit()
	changed.emit()


# ============================================================ formules
func shop_lvl(id: String) -> int:
	return int(shop.get(id, 0))


func up_lvl(id: String) -> int:
	return int(ups.get(id, 0))


func has_plan(id: String) -> bool:
	return id == "" or tree.has(id)


func shop_cost(id: String) -> float:
	var u: Dictionary = Data.SHOP[id]
	return round(u.base * pow(u.k, shop_lvl(id)))


func up_cost(id: String) -> float:
	var u: Dictionary = Data.TREE_UPS[id]
	return round(u.base * pow(u.k, up_lvl(id)))


func hand_cap() -> int:
	return 15 + 10 * shop_lvl("main")


func grab_amount() -> int:
	return 3 + 2 * shop_lvl("poignee")


## Rayon de la poignée : l'outil creuse plus large à mesure qu'il grandit.
func grab_radius() -> float:
	return 0.4 + 0.06 * shop_lvl("poignee")


## Outil de fouille selon le niveau de la boutique (comme la pelle, la fourche… de Find the Needle).
func tool_name(lvl := -1) -> String:
	if lvl < 0:
		lvl = shop_lvl("poignee")
	var names := ["Mains nues", "Pelle", "Pelle", "Fourche", "Fourche", "Fourche", "Brouette", "Brouette", "Brouette", "Brouette", "Aspirateur"]
	return names[mini(lvl, names.size() - 1)]


func stamina_max() -> float:
	return 100.0 + 25.0 * shop_lvl("endurance")


func stamina_regen() -> float:
	return 14.0 + 5.0 * shop_lvl("recup")


func walk_speed() -> float:
	return 4.2 + 0.45 * shop_lvl("vitesse")


func reach() -> float:
	return 3.6 + 0.75 * shop_lvl("portee")


func spot_chance() -> float:
	return minf(0.9, 0.3 + 0.07 * shop_lvl("oeil"))


func tremie_cap() -> int:
	return 600 + 300 * shop_lvl("tremie_cap")


func belt_speed() -> float:
	return 1.6 * (1.0 + 0.2 * up_lvl("u_conv_vitesse"))


func auto_mult() -> float:
	return (1.5 if tree.has("b_auto") else 1.0) * (1.0 + 0.1 * up_lvl("u_auto"))


func machine_speed(type: String) -> float:
	var m: Dictionary = Data.MACHINES[type]
	var s := 1.0
	if m.has("speed"):
		s += (0.3 if type == "scanner" else 0.25) * up_lvl(m.speed)
	if m.has("power"):
		s *= power_factor
	return s * auto_mult()


# ============================================================ électricité
## Le réseau est commun à toute l'usine : production (raccordement + générateurs) contre consommation.
## S'il manque du courant, toutes les machines électriques ralentissent d'autant.
var power_factor := 1.0
var power_supply := Data.GRID_POWER
var power_demand := 0.0
var weather_rain := 0.0 # intensité de l'averse en cours (fixée par le décor)
var free_power := false # tests uniquement


## Ensoleillement de 0 (nuit) à 1 (plein jour), d'après le même cycle que le décor (900 s).
func daylight() -> float:
	if not settings.get("daynight", true):
		return 1.0
	var ph := fmod(float(stats.get("time", 0.0)) / 900.0, 1.0)
	return 1.0 - smoothstep(0.62, 0.72, ph) * (1.0 - smoothstep(0.9, 1.0, ph))


## Force du vent de 0,5 à 1,5 : rafales lentes, plus fort sous la pluie.
func wind() -> float:
	return clampf(0.75 + 0.25 * sin(_clock * 0.07) * sin(_clock * 0.023 + 1.0) + 0.6 * weather_rain, 0.5, 1.5)


func generator_output(type: String) -> float:
	var m: Dictionary = Data.MACHINES[type]
	var g := float(m.get("gen", 0.0)) * (1.0 + 0.15 * up_lvl("u_energie"))
	match str(m.get("kind", "")):
		"fuel":
			return g if money > 0.0 else 0.0
		"solar":
			return g * daylight() * (1.0 - 0.7 * weather_rain)
		"wind":
			return g * wind()
	return 0.0


func _update_power() -> void:
	var sup := Data.GRID_POWER
	var dem := 0.0
	for id in entities:
		var m: Dictionary = Data.MACHINES[entities[id].type]
		if m.has("gen"):
			var out := generator_output(entities[id].type)
			sup += out
			if out > 0.0:
				_activity[id] = _clock
		dem += float(m.get("power", 0.0))
	power_supply = sup
	power_demand = dem
	power_factor = 1.0 if free_power or dem <= sup else clampf(sup / dem, 0.0, 1.0)


func _burn_fuel(dt: float) -> void:
	if money <= 0.0:
		return
	for id in entities:
		if Data.MACHINES[entities[id].type].get("kind", "") == "fuel":
			money -= Data.FUEL_COST * dt


# ============================================================ radar et détecteur
func radar_range() -> float:
	return 6.0 + 3.0 * up_lvl("u_radar_portee")


func detector_range() -> float:
	return 3.0 + 1.5 * shop_lvl("detecteur")


## Brins de foin cachés repérés par au moins un radar alimenté.
func revealed_hay() -> Array:
	var out: Array = []
	if hay_spots.is_empty():
		return out
	var radars: Array = []
	for id in entities:
		if entities[id].type == "radar":
			radars.append(machine_center("radar", entities[id].c, entities[id].r))
	if radars.is_empty():
		return out
	var rr := radar_range() * clampf(power_factor * 1.2, 0.0, 1.0)
	for sp: Vector3 in hay_spots:
		for c: Vector3 in radars:
			if Vector2(sp.x - c.x, sp.z - c.z).length() <= rr:
				out.append(sp)
				break
	return out


func dig_range(type: String) -> float:
	if type == "bras":
		return 3.0 + up_lvl("u_bras_portee")
	if type == "pelle":
		return 5.0 + 1.5 * up_lvl("u_pelle_portee")
	return 0.0


func sell_mult() -> float:
	return (1.1 if tree.has("b_trou") else 1.0) * (1.0 + 0.05 * up_lvl("u_trou_prix")) * prestige_mult()


func quality_mult(item: String) -> float:
	match item:
		"brut":
			return 1.0 + 0.1 * up_lvl("u_fond_qualite")
		"pur":
			return 1.0 + 0.1 * up_lvl("u_purif_qualite")
		"tole":
			return 1.0 + 0.1 * up_lvl("u_presse_qualite")
		"fil":
			return 1.0 + 0.1 * up_lvl("u_tref_qualite")
		"boite":
			return 1.0 + 0.1 * up_lvl("u_aig_qualite")
	return 1.0


func item_price(item: Dictionary) -> float:
	var t: String = item.t
	var base: float = Data.ITEMS[t].price
	if t == "vrac" or t == "acier":
		base *= float(item.get("n", Data.LOT)) / Data.LOT
	return base * quality_mult(t) * sell_mult()


func hay_unit_value() -> float:
	return Data.PILES[pile_size].hay_value * prestige_mult()


func pile_radius() -> float:
	var base: float = Data.PILES[pile_size].radius
	var left := float(pile_n + pile_h) / float(maxi(1, pile_total + Data.HAY_PER_PILE))
	return base * clampf(pow(left, 1.0 / 3.0), 0.0, 1.0)


func pile_items() -> int:
	return pile_n + pile_h


func build_cost(type: String) -> float:
	var base: float = Data.MACHINES[type].cost
	if type == "convoyeur" or type == "separateur":
		return base
	var n := count_type(type)
	return round(base * pow(1.35, n))


func count_type(t: String) -> int:
	var n := 0
	for id in entities:
		if entities[id].type == t:
			n += 1
	return n


func is_active(id: int) -> bool:
	return _clock - float(_activity.get(id, -10.0)) < 1.0


# ============================================================ grille
static func rot_vec(v: Vector2i, r: int) -> Vector2i:
	for i in r % 4:
		v = Vector2i(-v.y, v.x)
	return v


static func cell_center(c: Vector2i) -> Vector3:
	return Vector3(c.x + 0.5, 0, c.y + 0.5)


static func world_to_cell(p: Vector3) -> Vector2i:
	return Vector2i(floori(p.x), floori(p.z))


## Bornes locales d'une emprise de n cases : [lo, hi] autour de la case d'ancrage.
static func _span(n: int) -> Vector2i:
	var lo := -((n - 1) / 2)
	return Vector2i(lo, lo + n - 1)


static func footprint(type: String, c: Vector2i, r: int) -> Array:
	var s: Vector2i = Data.MACHINES[type].size
	var sx := _span(s.x)
	var sz := _span(s.y)
	var out := []
	for lx in range(sx.x, sx.y + 1):
		for lz in range(sz.x, sz.y + 1):
			out.append(c + rot_vec(Vector2i(lx, lz), r))
	return out


## Centre visuel de la machine (au sol), utile pour les emprises paires.
static func machine_center(type: String, c: Vector2i, r: int) -> Vector3:
	var s: Vector2i = Data.MACHINES[type].size
	var sx := _span(s.x)
	var sz := _span(s.y)
	var off := Vector2((sx.x + sx.y) * 0.5, (sz.x + sz.y) * 0.5)
	for i in r % 4:
		off = Vector2(-off.y, off.x)
	return cell_center(c) + Vector3(off.x, 0, off.y)


## Cases devant la machine (où elle pose ce qu'elle produit), de gauche à droite.
static func out_cells(type: String, c: Vector2i, r: int) -> Array:
	var s: Vector2i = Data.MACHINES[type].size
	var sx := _span(s.x)
	var sz := _span(s.y)
	var out := []
	for lx in range(sx.x, sx.y + 1):
		out.append(c + rot_vec(Vector2i(lx, sz.x - 1), r))
	return out


## Cases derrière la machine (d'où arrivent les tapis d'entrée).
static func in_cells(type: String, c: Vector2i, r: int) -> Array:
	var s: Vector2i = Data.MACHINES[type].size
	var sx := _span(s.x)
	var sz := _span(s.y)
	var out := []
	for lx in range(sx.x, sx.y + 1):
		out.append(c + rot_vec(Vector2i(lx, sz.y + 1), r))
	return out


func _pile_blocks(cell: Vector2i) -> bool:
	# on ne construit pas là où il y a des aiguilles (le bord de la case compte aussi)
	var p := cell_center(cell)
	for o: Vector2 in [Vector2.ZERO, Vector2(-0.45, -0.45), Vector2(0.45, -0.45), Vector2(-0.45, 0.45), Vector2(0.45, 0.45)]:
		if field.height_at(p.x + o.x, p.z + o.y) > 0.04:
			return true
	return false


var player_cells: Array = [] # cases occupées par le joueur (on ne construit pas sur lui)


func placement_ok(type: String, c: Vector2i, r: int, ignore := -1) -> String:
	for cell in footprint(type, c, r):
		if cell in player_cells and type != "convoyeur":
			return "Tu es dans le chemin !"
		if absi(cell.x) > Data.FIELD or absi(cell.y) > Data.FIELD:
			return "Hors du terrain"
		if _pile_blocks(cell):
			return "Trop près du tas"
		var o: int = grid.get(cell, -1)
		if o >= 0 and o != ignore:
			# un convoyeur peut être remplacé par une machine (il est remboursé)
			if entities[o].type != "convoyeur" or type == "convoyeur":
				return "Emplacement occupé"
	return ""


func entity_at(cell: Vector2i) -> Dictionary:
	var id: int = grid.get(cell, -1)
	return entities[id] if id >= 0 else {}


func _add(type: String, c: Vector2i, r: int) -> int:
	var id := next_id
	next_id += 1
	var e := {"id": id, "type": type, "c": c, "r": r}
	_init_state(e)
	entities[id] = e
	for cell in footprint(type, c, r):
		grid[cell] = id
	_order_dirty = true
	return id


func _init_state(e: Dictionary) -> void:
	match e.type:
		"convoyeur", "separateur":
			e["item"] = null
			e["k"] = 0
		"tremie":
			e["n"] = 0
			e["h"] = 0
			e["acc"] = 0.0
			e["outq"] = []
		"tampon":
			e["q"] = []
		"bras", "pelle":
			e["acc"] = 0.0
			e["outq"] = []
		"drone":
			e["state"] = 0
			e["pos"] = cell_center(e.c) + Vector3(0, 0.6, 0)
			e["carry_n"] = 0
			e["carry_h"] = 0
		_:
			if Data.MACHINES[e.type].has("in"):
				e["inq"] = []
				e["outq"] = []
				e["prog"] = 0.0
				e["busy"] = []


func _remove(id: int) -> void:
	var e: Dictionary = entities[id]
	for cell in footprint(e.type, e.c, e.r):
		if grid.get(cell, -1) == id:
			grid.erase(cell)
	entities.erase(id)
	_order_dirty = true


func can_build(type: String) -> String:
	var m: Dictionary = Data.MACHINES[type]
	if m.get("fixed", false):
		return "Non constructible"
	if not has_plan(m.plan):
		return "Plan requis dans l'arbre"
	if money < build_cost(type):
		return "Pas assez d'argent"
	return ""


func build(type: String, c: Vector2i, r: int) -> bool:
	if can_build(type) != "" or placement_ok(type, c, r) != "":
		return false
	# remplace les convoyeurs recouverts
	for cell in footprint(type, c, r):
		var o: int = grid.get(cell, -1)
		if o >= 0:
			money += build_cost("convoyeur")
			_remove(o)
	money -= build_cost(type)
	_add(type, c, r)
	if type == "convoyeur":
		stats.belts += 1
	_sfx("buy" if type != "convoyeur" else "click")
	entities_changed.emit()
	changed.emit()
	return true


func move_entity(id: int, c: Vector2i, r: int) -> bool:
	var e: Dictionary = entities[id]
	if placement_ok(e.type, c, r, id) != "":
		return false
	for cell in footprint(e.type, e.c, e.r):
		if grid.get(cell, -1) == id:
			grid.erase(cell)
	for cell in footprint(e.type, c, r):
		var o: int = grid.get(cell, -1)
		if o >= 0 and o != id:
			_remove(o)
	e.c = c
	e.r = r
	for cell in footprint(e.type, c, r):
		grid[cell] = id
	if e.type == "drone":
		e.pos = cell_center(c) + Vector3(0, 0.6, 0)
		e.state = 0
	_order_dirty = true
	entities_changed.emit()
	return true


func rotate_entity(id: int) -> void:
	var e: Dictionary = entities[id]
	var nr: int = (e.r + 1) % 4
	if placement_ok(e.type, e.c, nr, id) == "":
		move_entity(id, e.c, nr)


func demolish(id: int) -> bool:
	var e: Dictionary = entities.get(id, {})
	if e.is_empty() or Data.MACHINES[e.type].get("fixed", false):
		return false
	var refund := build_cost(e.type) if e.type == "convoyeur" else build_cost(e.type) / 1.35 * 0.5
	# les aiguilles de la trémie retournent dans le tas, le foin reste trouvable
	if e.type == "tremie":
		var back := int(e.n) + int(e.h)
		if back > 0 and pile_items() > 0:
			field.scale_all(float(pile_items() + back) / float(pile_items()))
		pile_n += int(e.n)
		pile_h += int(e.h)
		_hay_bury(int(e.h))
	_return_hay_of(e)
	_remove(id)
	money += refund
	entities_changed.emit()
	changed.emit()
	return true


func _return_hay_of(e: Dictionary) -> void:
	var lots: Array = []
	if e.get("item") != null:
		lots.append(e.item)
	for k in ["outq", "inq", "q", "busy"]:
		if e.has(k):
			lots.append_array(e[k])
	for it in lots:
		if it.get("h", 0) > 0:
			pile_h += int(it.h)
			_hay_bury(int(it.h))


# ============================================================ actions du joueur
func _sfx(n: String) -> void:
	if not offline:
		Sfx.play(n)


func _toast(text: String, gold := false) -> void:
	if not offline:
		toast.emit(text, gold)


func _sround(x: float) -> int:
	var f := floorf(x)
	return int(f) + (1 if randf() < x - f else 0)


## Tire k objets d'un mélange (n aiguilles, h foin) : Vector2i(aiguilles, foin).
func _draw_mix(k: int, n: int, h: int) -> Vector2i:
	k = mini(k, n + h)
	if k <= 0:
		return Vector2i.ZERO
	var got_h := 0
	if k <= 60:
		var nn := n
		var hh := h
		for i in k:
			if randf() * float(nn + hh) < float(hh):
				hh -= 1
				got_h += 1
			else:
				nn -= 1
	else:
		got_h = mini(h, _sround(float(k) * float(h) / float(n + h)))
	var got_n := k - got_h
	if got_n > n:
		got_h += got_n - n
		got_n = n
	return Vector2i(got_n, got_h)


func _found(count: int, by_hand: bool) -> void:
	if count <= 0:
		return
	var before := pile_found
	pile_found += count
	stats.hay += count
	if pile_gold > before and pile_gold <= pile_found:
		# le brin doré : rare, il vaut dix brins
		var gold := hay_unit_value() * 10.0
		gain(gold)
		stats.golden += 1
		_unlock("golden")
		if not offline:
			golden_found.emit()
			_sfx("golden")
			_toast("BRIN DORÉ ! Il vaut dix brins : +%s" % Fmt.eur(gold), true)
	var reward := count * hay_unit_value()
	gain(reward)
	if not offline:
		hay_found.emit(count, by_hand)
		_sfx("hay")
		_toast("Brin de foin trouvé ! (%d / %d)  +%s" % [pile_found, Data.HAY_PER_PILE, Fmt.eur(reward)], true)
	if not by_hand:
		_unlock("first_scan")
	_unlock("first_hay")
	if pile_found >= Data.HAY_PER_PILE and not pile_done:
		pile_done = true
		stats.piles += 1
		var bonus := Data.HAY_PER_PILE * hay_unit_value() * 0.5
		gain(bonus)
		_sfx("win")
		_toast("%s terminé : les 22 brins sont trouvés ! Prime : %s" % [Data.PILES[pile_size].name, Fmt.eur(bonus)], true)
		_unlock("first_pile")
		if pile_size == "montagne":
			_unlock("mountain")


func gain(x: float) -> void:
	money += x
	stats.earned += x
	run_earned += x


# ============================================================ recyclage (prestige)
## Bonus permanent des jetons : +10 % sur toutes les ventes et les primes de foin, par jeton.
func prestige_mult() -> float:
	return 1.0 + 0.1 * prestige


## Jetons gagnés si l'on recycle maintenant : racine de l'argent gagné depuis le dernier recyclage.
func tokens_pending() -> int:
	return int(floor(sqrt(run_earned / RECYCLE_BASE)))


const RECYCLE_BASE := 50000.0


## Recycle toute l'usine : on repart de zéro avec les jetons en plus.
func recycle() -> bool:
	var t := tokens_pending()
	if t < 1:
		return false
	new_game(true)
	prestige += t
	stats.recycles += 1
	_unlock("recycle")
	_sfx("recycle")
	_toast("Usine recyclée ! +%d jeton%s : toutes tes ventes rapportent %d %% de plus." % [t, "s" if t > 1 else "", int(round((prestige_mult() - 1.0) * 100))], true)
	recycled.emit()
	changed.emit()
	save_slot(slot)
	return true


## Prend une poignée dans le tas, à l'endroit visé (at) : le relief s'y creuse un peu.
func grab(at := Vector3.INF) -> int:
	var room := hand_cap() - hand_n - hand_h
	if room <= 0:
		return -1
	if at == Vector3.INF:
		at = field.top_near(Data.PILE_POS.x, Data.PILE_POS.z, field.radius)
	var got := _dig(at.x, at.z, maxf(grab_radius(), field.cell * 1.3), mini(grab_amount(), room))
	if got.x + got.y <= 0:
		return 0
	hand_n += got.x
	stats.needles += got.x
	var seen := 0
	for i in got.y:
		if randf() < spot_chance():
			seen += 1
		else:
			hand_h += 1
	_found(seen, true)
	pile_changed.emit()
	changed.emit()
	return got.x + got.y


## Vide la main dans une trémie. Renvoie le nombre d'aiguilles déposées.
func deposit_tremie(id: int) -> int:
	var e: Dictionary = entities[id]
	var q := mini(tremie_cap() - int(e.n) - int(e.h), hand_n + hand_h)
	if q <= 0:
		return 0
	var mix := _draw_mix(q, hand_n, hand_h)
	hand_n -= mix.x
	hand_h -= mix.y
	e.n += mix.x
	e.h += mix.y
	stats.poured += q
	changed.emit()
	return q


## Pose un lot (10 aiguilles max) de la main sur un tapis vide.
func drop_on_belt(id: int) -> int:
	var e: Dictionary = entities[id]
	if e.item != null or hand_n + hand_h <= 0:
		return 0
	var mix := _draw_mix(mini(Data.LOT, hand_n + hand_h), hand_n, hand_h)
	hand_n -= mix.x
	hand_h -= mix.y
	e.item = {"t": "vrac", "n": mix.x, "h": mix.y, "p": 0.0}
	changed.emit()
	return mix.x + mix.y


func buy_shop(id: String) -> bool:
	var u: Dictionary = Data.SHOP[id]
	var c := shop_cost(id)
	if shop_lvl(id) >= int(u.max) or money < c:
		return false
	money -= c
	shop[id] = shop_lvl(id) + 1
	_sfx("buy")
	changed.emit()
	return true


## 0 verrouillé, 1 achetable, 2 acquis
func tree_state(id: String) -> int:
	if tree.has(id):
		return 2
	var n: Dictionary = Data.TREE[id]
	for r in n.req:
		if not tree.has(r):
			return 0
	if int(stats.piles) < int(n.get("piles", 0)):
		return 0
	return 1


func buy_tree(id: String) -> bool:
	if tree_state(id) != 1 or money < float(Data.TREE[id].cost):
		return false
	money -= float(Data.TREE[id].cost)
	tree[id] = true
	_sfx("buy")
	_toast("Débloqué : %s" % Data.TREE[id].name, true)
	changed.emit()
	return true


func up_parent(up_id: String) -> String:
	for id in Data.TREE:
		if up_id in Data.TREE[id].ups:
			return id
	return ""


func buy_up(id: String) -> bool:
	var u: Dictionary = Data.TREE_UPS[id]
	var c := up_cost(id)
	if not tree.has(up_parent(id)) or up_lvl(id) >= int(u.max) or money < c:
		return false
	money -= c
	ups[id] = up_lvl(id) + 1
	_sfx("buy")
	changed.emit()
	return true


func order_cost(size: String) -> float:
	return Data.PILES[size].order


func can_order(size: String) -> String:
	if not has_plan(Data.PILES[size].right):
		return "Contrat requis dans l'arbre"
	if not pile_done:
		return "Trouve d'abord les 22 brins du tas actuel"
	if money < order_cost(size):
		return "Pas assez d'argent"
	return ""


func order_pile(size: String) -> bool:
	if can_order(size) != "":
		return false
	money -= order_cost(size)
	stats.ordered[size] = int(stats.ordered.get(size, 0)) + 1
	_set_pile(size)
	_sfx("win")
	_toast("%s livré ! 22 brins de foin y sont cachés%s" % [Data.PILES[size].name, "… et on murmure qu'un brin DORÉ s'y trouve !" if pile_gold > 0 else "."], true)
	changed.emit()
	return true


func _set_pile(size: String) -> void:
	pile_size = size
	pile_total = int(Data.PILES[size].needles)
	pile_n = pile_total
	pile_h = Data.HAY_PER_PILE
	pile_found = 0
	pile_done = false
	# 40 % des tas cachent un brin doré parmi leurs 22 brins
	pile_gold = randi_range(1, Data.HAY_PER_PILE) if randf() < 0.4 else 0
	field.generate(float(Data.PILES[size].radius), randi())
	_take_carry = 0.0
	hay_spots.clear()
	_hay_bury(pile_h)
	hand_h = 0
	# le foin caché de l'ancien tas ne compte plus ; ce que le nouveau tas recouvre est remboursé
	var removed := 0
	for id in entities.keys():
		var e: Dictionary = entities[id]
		for k in ["outq", "inq", "q", "busy"]:
			if e.has(k):
				for it in e[k]:
					it["h"] = 0
		if e.get("item") != null:
			e.item["h"] = 0
		if e.has("h") and e.type == "tremie":
			e.h = 0
		if Data.MACHINES[e.type].get("fixed", false):
			continue
		for cell in footprint(e.type, e.c, e.r):
			if _pile_blocks(cell):
				money += build_cost(e.type) / (1.35 if e.type != "convoyeur" and e.type != "separateur" else 1.0)
				_remove(id)
				removed += 1
				break
	if removed > 0:
		_toast("%d construction(s) recouverte(s) par le nouveau tas ont été remboursées." % removed)
		entities_changed.emit()
	pile_changed.emit()


# ============================================================ contrats
func available_items() -> Array:
	var out := ["vrac"]
	if tree.has("p_scanner"):
		out.append("acier")
	if tree.has("p_fonderie"):
		out.append("brut")
	if tree.has("p_purif"):
		out.append("pur")
	if tree.has("p_presse"):
		out.append("tole")
	if tree.has("p_trefileuse"):
		out.append("fil")
	if tree.has("p_aiguilleuse"):
		out.append("boite")
	return out


func _roll_offers() -> void:
	offers = []
	var items := available_items()
	for i in 3:
		var t: String = items[randi() % items.size()] if i < 2 else items[items.size() - 1]
		var qty := randi_range(15, 40) * (1 + int(stats.piles))
		var price: float = Data.ITEMS[t].price
		offers.append({"t": t, "qty": qty, "reward": round(qty * price * randf_range(1.6, 2.4) + 50.0), "time": float(randi_range(5, 12) * 60)})


func accept_offer(i: int) -> bool:
	if not contract.is_empty() or i >= offers.size():
		return false
	var o: Dictionary = offers[i]
	contract = {"t": o.t, "qty": o.qty, "done": 0, "reward": o.reward, "until": stats.time + o.time}
	offers.remove_at(i)
	_sfx("click")
	changed.emit()
	return true


func abandon_contract() -> void:
	contract = {}
	_roll_offers()
	changed.emit()


# ============================================================ objectifs guidés
## (fait, but) pour l'objectif courant.
func quest_progress(id: String) -> Vector2:
	match id:
		"grab30":
			return Vector2(float(stats.needles) * Data.NEEDLE_UNIT, 30000)
		"tremie":
			return Vector2(mini(int(stats.poured), 1), 1)
		"sell10":
			return Vector2(stats.earned, 10)
		"plan_scan":
			return Vector2(1 if tree.has("p_scanner") else 0, 1)
		"scanner":
			return Vector2(mini(count_type("scanner"), 1), 1)
		"hay3":
			return Vector2(stats.hay, 3)
		"belts10":
			return Vector2(stats.belts, 10)
		"pile1":
			return Vector2(stats.piles, 1)
		"fonderie":
			return Vector2(mini(count_type("fonderie"), 1), 1)
		"energie":
			return Vector2(mini(count_type("groupe") + count_type("eolienne") + count_type("solaire"), 1), 1)
		"radar":
			return Vector2(mini(count_type("radar"), 1), 1)
		"ingot":
			return Vector2(mini(int(stats.sold.get("brut", 0)), 1), 1)
		"bras":
			return Vector2(mini(count_type("bras"), 1), 1)
		"moyen":
			return Vector2(mini(int(stats.ordered.get("moyen", 0)), 1), 1)
		"contrat":
			return Vector2(mini(int(stats.contracts), 1), 1)
		"purif":
			return Vector2(mini(count_type("purif"), 1), 1)
		"gros":
			return Vector2(mini(int(stats.ordered.get("gros", 0)), 1), 1)
		"presse":
			return Vector2(mini(int(stats.sold.get("tole", 0)), 1), 1)
		"boite":
			return Vector2(mini(int(stats.sold.get("boite", 0)), 1), 1)
		"montagne":
			return Vector2(mini(int(stats.ordered.get("montagne", 0)), 1), 1)
	return Vector2(0, 1)


func _check_quest() -> void:
	while quest < Data.QUESTS.size():
		var q: Array = Data.QUESTS[quest]
		var p := quest_progress(q[0])
		if p.x < p.y:
			return
		gain(float(q[2]))
		quest += 1
		_toast("Objectif atteint : %s (+%s)" % [q[1], Fmt.eur(q[2])], true)
		_sfx("win")


# ============================================================ succès
func _unlock(id: String) -> void:
	if achievements.has(id):
		return
	achievements[id] = true
	for a in Data.ACHIEVEMENTS:
		if a[0] == id:
			_toast("Succès débloqué : %s" % a[1], true)
			_sfx("win")


func _check_achievements() -> void:
	if int(stats.belts) >= 1:
		_unlock("first_belt")
	if int(stats.belts) >= 100:
		_unlock("belts_100")
	if int(stats.ingots) >= 1:
		_unlock("first_ingot")
	if int(stats.sold.get("pur", 0)) >= 100:
		_unlock("pure_100")
	if int(stats.contracts) >= 5:
		_unlock("contract_5")
	if int(stats.needles) >= 100000:
		_unlock("needles_100k")
	if int(stats.sold.get("boite", 0)) >= 1:
		_unlock("boite_1")
	if float(stats.earned) >= 1000000.0:
		_unlock("money_1m")


# ============================================================ simulation
## Essaie de faire entrer un objet dans la case `cell` en venant dans la direction `dir`.
func _insert(cell: Vector2i, item: Dictionary, dir: int) -> bool:
	var id: int = grid.get(cell, -1)
	if id < 0:
		return false
	var e: Dictionary = entities[id]
	match e.type:
		"convoyeur":
			if e.item == null and e.r != (dir + 2) % 4:
				item.p = 0.0
				e.item = item
				return true
		"separateur":
			if e.item == null and e.r == dir:
				item.p = 0.0
				e.item = item
				return true
		"trou":
			_sell(item, cell)
			return true
		"tampon":
			if e.r == dir and e.q.size() < int(Data.MACHINES.tampon.cap):
				e.q.append(item)
				return true
		"tremie":
			# une trémie accepte aussi les aiguilles en vrac apportées par tapis
			if item.t == "vrac" and int(e.n) + int(e.h) + int(item.n) + int(item.h) <= tremie_cap():
				e.n += int(item.n)
				e.h += int(item.h)
				return true
		_:
			var m: Dictionary = Data.MACHINES[e.type]
			if m.has("in") and e.r == dir and m.in.has(item.t):
				var have := 0
				for it in e.inq:
					if it.t == item.t:
						have += 1
				if have < int(m.in[item.t]) * 3:
					e.inq.append(item)
					return true
	return false


func _sell(item: Dictionary, cell: Vector2i) -> void:
	var t: String = item.t
	var value := item_price(item)
	gain(value)
	stats.sold[t] = int(stats.sold.get(t, 0)) + 1
	if t == "vrac" and int(item.get("h", 0)) > 0:
		# du foin non détecté tombe dans le trou : il est recraché dans le tas
		pile_h += int(item.h)
		_hay_bury(int(item.h))
		stats.lost_hay += int(item.h)
		if _clock - _lost_toast > 8.0:
			_lost_toast = _clock
			_toast("Du foin non détecté est tombé dans le trou… il retourne dans le tas ! Installe un scanner.")
			_sfx("prick")
	if not contract.is_empty() and contract.t == t:
		contract.done += 1
		if contract.done >= contract.qty:
			gain(contract.reward)
			stats.contracts += 1
			_toast("Contrat rempli ! Prime : %s" % Fmt.eur(contract.reward), true)
			_sfx("win")
			contract = {}
			_roll_offers()
	if not offline:
		sold.emit(cell, t)


func _rebuild_order() -> void:
	# ordre de mise à jour : de l'aval vers l'amont, pour que les files avancent d'un bloc
	var dist := {}
	for id in entities:
		var e: Dictionary = entities[id]
		if e.type != "convoyeur" and e.type != "separateur":
			continue
		if dist.has(id):
			continue
		var path: Array = []
		var on_path := {}
		var cur: int = id
		var base := 0
		while true:
			if dist.has(cur):
				base = dist[cur]
				break
			if on_path.has(cur):
				base = 0
				break
			path.append(cur)
			on_path[cur] = true
			var ce: Dictionary = entities[cur]
			var nid: int = grid.get(ce.c + Data.DIRS[ce.r], -1)
			if nid < 0 or (entities[nid].type != "convoyeur" and entities[nid].type != "separateur"):
				base = 0
				break
			cur = nid
		for i in range(path.size() - 1, -1, -1):
			base += 1
			dist[path[i]] = base
	_belt_order = dist.keys()
	_belt_order.sort_custom(func(a: int, b: int) -> bool: return dist[a] < dist[b])
	_order_dirty = false


func _step(dt: float) -> void:
	_clock += dt
	stats.time += dt
	if _order_dirty:
		_rebuild_order()
	var spd := belt_speed() * dt
	for id in _belt_order:
		var e: Dictionary = entities[id]
		var it = e.item
		if it == null:
			continue
		it.p = minf(1.0, it.p + spd)
		if it.p < 1.0:
			continue
		if e.type == "convoyeur":
			if _insert(e.c + Data.DIRS[e.r], it, e.r):
				e.item = null
		else:
			for k in 3:
				var d: int = [(e.r + 3) % 4, e.r, (e.r + 1) % 4][(e.k + k) % 3]
				if _insert(e.c + Data.DIRS[d], it, d):
					e.item = null
					e.k = (e.k + k + 1) % 3
					break
	for id in entities.keys():
		if not entities.has(id):
			continue
		var e: Dictionary = entities[id]
		match e.type:
			"convoyeur", "separateur", "trou", "bureau", "groupe", "eolienne", "solaire":
				pass
			"radar":
				if power_factor > 0.2:
					_activity[e.id] = _clock
			"tremie":
				_tick_tremie(e, dt)
			"bras", "pelle":
				_tick_digger(e, dt)
			"tampon":
				_emit_front(e, e.q)
			"drone":
				_tick_drone(e, dt)
			_:
				_tick_machine(e, dt)


## Sert les cases de sortie à tour de rôle.
func _push_out(e: Dictionary) -> void:
	_emit_front(e, e.outq)


func _emit_front(e: Dictionary, q: Array) -> void:
	if q.is_empty():
		return
	var cells := out_cells(e.type, e.c, e.r)
	var k: int = int(e.get("oi", 0))
	for i in cells.size():
		var idx := (k + i) % cells.size()
		if _insert(cells[idx], q[0], e.r):
			q.pop_front()
			e["oi"] = (idx + 1) % cells.size()
			return


func _tick_tremie(e: Dictionary, dt: float) -> void:
	if e.outq.is_empty() and int(e.n) + int(e.h) > 0:
		e.acc += 3.0 * dt
		if e.acc >= 1.0:
			e.acc = 0.0
			var mix := _draw_mix(Data.LOT, int(e.n), int(e.h))
			e.n -= mix.x
			e.h -= mix.y
			e.outq.append({"t": "vrac", "n": mix.x, "h": mix.y, "p": 0.0})
			_activity[e.id] = _clock
	_push_out(e)


# ============================================================ relief du tas
## Volume d'une unité (1 000 aiguilles) : le relief et le compte restent proportionnels.
func unit_volume() -> float:
	return field.volume / float(maxi(1, pile_items()))


## Retire jusqu'à k unités du relief autour de (x, z). Renvoie le nombre d'unités obtenues.
func _take_units(x: float, z: float, rad: float, k: int) -> int:
	k = mini(k, pile_items())
	if k <= 0:
		return 0
	if field.volume <= 1e-6:
		return k # relief épuisé (arrondis) : on finit le compte n'importe où
	var uv := unit_volume()
	var got := field.take(x, z, rad, uv * k) / uv + _take_carry
	var units := mini(k, int(floor(got + 1e-4)))
	_take_carry = clampf(got - units, 0.0, 0.999)
	if pile_items() - units <= 0:
		field.clear()
	return units


## Creuse k unités autour de (x, z) : retire les aiguilles du relief et libère les brins de foin
## que la surface a atteints. Renvoie Vector2i(aiguilles, foin).
func _dig(x: float, z: float, rad: float, k: int) -> Vector2i:
	var units := _take_units(x, z, rad, mini(k, pile_n))
	pile_n -= units
	var hay := _hay_release(x, z, rad)
	if pile_n <= 0 and pile_h > 0:
		# plus une aiguille : tout le foin restant est à découvert
		hay += pile_h
		pile_h = 0
		hay_spots.clear()
	if pile_items() <= 0:
		field.clear()
	return Vector2i(units, hay)


## Libère les brins proches de (x, z) dont la surface du tas est descendue jusqu'à eux.
func _hay_release(x: float, z: float, rad: float) -> int:
	var n := 0
	var i := hay_spots.size() - 1
	while i >= 0:
		var s: Vector3 = hay_spots[i]
		if Vector2(s.x - x, s.z - z).length() <= rad + 0.35 and s.y >= field.height_at(s.x, s.z) - 0.3:
			hay_spots.remove_at(i)
			n += 1
		i -= 1
	n = mini(n, pile_h)
	pile_h -= n
	return n


## Cache n brins de foin au hasard dans le tas (plus il y a d'aiguilles à un endroit, plus c'est probable).
func _hay_bury(n: int) -> void:
	var top := maxf(0.5, field.top_near(Data.PILE_POS.x, Data.PILE_POS.z, field.radius).y)
	for i in n:
		var spot := Vector3(Data.PILE_POS.x, 0.0, Data.PILE_POS.z)
		for t in 60:
			var a := randf() * TAU
			var d := field.radius * PileField.WOBBLE * sqrt(randf())
			var x := Data.PILE_POS.x + cos(a) * d
			var z := Data.PILE_POS.z + sin(a) * d
			var hh := field.height_at(x, z)
			if hh > 0.3 and randf() < hh / top:
				spot = Vector3(x, randf_range(0.1, hh - 0.15), z)
				break
		hay_spots.append(spot)


## Brin caché le plus proche d'un point : [position, distance] (distance INF s'il n'y en a pas).
func nearest_hay(p: Vector3) -> Array:
	var best := INF
	var bs := Vector3.ZERO
	for s: Vector3 in hay_spots:
		var d := s.distance_to(p)
		if d < best:
			best = d
			bs = s
	return [bs, best]


## Point de creusage d'une machine : l'endroit le plus haut parmi quelques essais dans sa portée.
func _scoop_point(center: Vector3, reach_r: float) -> Vector3:
	var best := Vector3(center.x, -1.0, center.z)
	var to_pile := Vector2(Data.PILE_POS.x - center.x, Data.PILE_POS.z - center.z)
	var base := to_pile.angle()
	for i in 9:
		var a := base + randf_range(-1.2, 1.2)
		var d := reach_r * sqrt(randf())
		var x := center.x + cos(a) * d
		var z := center.z + sin(a) * d
		var hh := field.height_at(x, z)
		if hh > best.y:
			best = Vector3(x, hh, z)
	return best


## Y a-t-il des aiguilles dans la portée de la machine ? (quelques sondages, rapide)
func digger_in_range(type: String, c: Vector2i, r := 0) -> bool:
	if pile_items() <= 0:
		return false
	var p := machine_center(type, c, r)
	var rr := dig_range(type) + 0.6
	if Vector2(p.x - Data.PILE_POS.x, p.z - Data.PILE_POS.z).length() > field.radius * PileField.WOBBLE + rr:
		return false
	for ring: float in [0.35, 0.7, 1.0]:
		for i in 12:
			var a := TAU * (float(i) + ring) / 12.0
			if field.height_at(p.x + cos(a) * rr * ring, p.z + sin(a) * rr * ring) > 0.03:
				return true
	return false


func _tick_digger(e: Dictionary, dt: float) -> void:
	_push_out(e)
	if e.outq.size() >= 3 or float(e.get("dry", 0.0)) > _clock:
		return
	var m: Dictionary = Data.MACHINES[e.type]
	e.acc += float(m.dig) * machine_speed(e.type) * dt
	if e.acc < 1.0:
		return
	e.acc -= 1.0
	var c := machine_center(e.type, e.c, e.r)
	var scoop := 0.8 if e.type == "bras" else 1.3
	var dug := false
	for i in int(m.get("lot_mult", 1)):
		var sp := _scoop_point(c, dig_range(e.type) + 0.6)
		if sp.y <= 0.0:
			break
		var mix := _dig(sp.x, sp.z, maxf(scoop, field.cell * 1.3), Data.LOT)
		if mix.x + mix.y <= 0:
			break
		stats.needles += mix.x
		e.outq.append({"t": "vrac", "n": mix.x, "h": mix.y, "p": 0.0})
		dug = true
	if not dug:
		# plus rien à portée : on réessaie dans une seconde (le tas peut s'ébouler jusqu'ici)
		e["dry"] = _clock + 1.0
		return
	_activity[e.id] = _clock
	if not offline:
		pile_changed.emit()


func _tick_machine(e: Dictionary, dt: float) -> void:
	var m: Dictionary = Data.MACHINES[e.type]
	_push_out(e)
	if e.busy.is_empty():
		# assez d'ingrédients ? on les prend
		var counts := {}
		for it in e.inq:
			counts[it.t] = int(counts.get(it.t, 0)) + 1
		for t in m.in:
			if int(counts.get(t, 0)) < int(m.in[t]):
				return
		for t in m.in:
			var need: int = m.in[t]
			var i := 0
			while need > 0 and i < e.inq.size():
				if e.inq[i].t == t:
					e.busy.append(e.inq[i])
					e.inq.remove_at(i)
					need -= 1
				else:
					i += 1
		e.prog = 0.0
	if e.outq.size() >= 4:
		return
	e.prog += dt * machine_speed(e.type) / float(m.time)
	_activity[e.id] = _clock
	if e.prog < 1.0:
		return
	var hay := 0
	for it in e.busy:
		hay += int(it.get("h", 0))
	e.busy = []
	e.prog = 0.0
	var batch := 1
	if m.has("batch"):
		batch += up_lvl(m.batch)
	for t in m.out:
		for i in int(m.out[t]) * batch:
			var lot := {"t": t, "p": 0.0}
			if t == "acier":
				lot["n"] = Data.LOT
			e.outq.append(lot)
	if e.type == "fonderie":
		stats.ingots += batch
	_found(hay, false)


func _tick_drone(e: Dictionary, dt: float) -> void:
	var speed := 5.0 * (1.0 + 0.25 * up_lvl("u_drone_vitesse")) * auto_mult() * maxf(power_factor, 0.1)
	var carry := 30 + 20 * up_lvl("u_drone_charge")
	var target: Vector3
	match int(e.state):
		0: # vers le tas
			if pile_items() <= 0:
				target = cell_center(e.c) + Vector3(0, 0.6, 0)
			else:
				# le sommet du tas, recalculé de temps en temps
				if not e.has("goal") or float(e.get("goal_t", 0.0)) < _clock:
					var top := field.top_near(Data.PILE_POS.x, Data.PILE_POS.z, field.radius)
					e["goal"] = top + Vector3(0, 1.2, 0)
					e["goal_t"] = _clock + 4.0
				target = e.goal
				if e.pos.distance_to(target) < 0.6:
					var mix := _dig(target.x, target.z, maxf(1.2, field.cell * 2.0), carry)
					e["goal_t"] = 0.0
					stats.needles += mix.x
					e.carry_n = mix.x
					e.carry_h = mix.y
					e.state = 1
					_activity[e.id] = _clock
					if not offline:
						pile_changed.emit()
		1: # vers la trémie la plus proche qui a de la place
			var best := -1
			var bd := 1e9
			for id in entities:
				var t: Dictionary = entities[id]
				if t.type == "tremie" and int(t.n) + int(t.h) + int(e.carry_n) + int(e.carry_h) <= tremie_cap():
					var dd: float = e.pos.distance_to(machine_center("tremie", t.c, t.r))
					if dd < bd:
						bd = dd
						best = id
			if best < 0:
				target = e.pos
			else:
				var tr: Dictionary = entities[best]
				target = machine_center("tremie", tr.c, tr.r) + Vector3(0, 2.6, 0)
				if e.pos.distance_to(target) < 0.6:
					tr.n += int(e.carry_n)
					tr.h += int(e.carry_h)
					e.carry_n = 0
					e.carry_h = 0
					e.state = 0
	e.pos = e.pos.move_toward(target, speed * dt)


func _process(delta: float) -> void:
	_acc += minf(delta, 0.25)
	var n := 0
	while _acc >= STEP and n < 8:
		_step(STEP)
		_acc -= STEP
		n += 1
	_relax_acc += delta
	if _relax_acc >= 0.2:
		_relax_acc = 0.0
		if field.relax(2):
			pile_changed.emit()
	_burn_fuel(delta)
	_sec_acc += delta
	if _sec_acc >= 1.0:
		_sec_acc = 0.0
		_update_power()
		_check_achievements()
		_check_quest()
		if not contract.is_empty() and stats.time > float(contract.until):
			_toast("Contrat expiré…")
			contract = {}
			_roll_offers()
	_rate_acc += delta
	if _rate_acc >= 10.0:
		_update_rates(_rate_acc)
		_rate_acc = 0.0
	_changed_acc += delta
	if _changed_acc >= 0.25:
		_changed_acc = 0.0
		changed.emit()
	_save_acc += delta
	if _save_acc >= 20.0:
		_save_acc = 0.0
		save_slot(slot)


func _update_rates(window: float) -> void:
	var now := {"income": float(stats.earned), "dig": float(stats.needles), "hay": float(stats.hay)}
	if not _rate_base.is_empty():
		for k in now:
			var inst := maxf(0.0, (now[k] - float(_rate_base[k])) / window)
			rates[k] = rates[k] * 0.7 + inst * 0.3
	_rate_base = now


## Fait tourner la ferme pendant l'absence, d'après la production récente.
func simulate_offline(seconds: float) -> Dictionary:
	seconds = minf(seconds, MAX_OFFLINE)
	var dig := mini(pile_n, int(float(rates.dig) * seconds))
	var hay := 0
	if float(rates.hay) > 0.0 and dig > 0 and pile_n + pile_h > 0:
		hay = mini(pile_h, int(round(float(dig) * float(pile_h) / float(pile_n + pile_h))))
	var money_gain: float = float(rates.income) * seconds * 0.8
	if pile_items() > 0:
		field.shrink(float(pile_items() - dig) / float(pile_items()))
	pile_n -= dig
	stats.needles += dig
	offline = true
	gain(money_gain)
	pile_h -= hay
	for i in mini(hay, hay_spots.size()):
		hay_spots.remove_at(randi() % hay_spots.size())
	_found(hay, false)
	offline = false
	stats.time += seconds
	pile_changed.emit()
	changed.emit()
	return {"seconds": seconds, "money": money_gain, "needles": dig, "hay": hay}


# ============================================================ sauvegarde
func slot_path(s: int) -> String:
	return "user://save_%d.json" % s


func _ser(v):
	# Vector2i et Vector3 ne passent pas en JSON : on les convertit
	if v is Vector2i:
		return {"__v2": [v.x, v.y]}
	if v is Vector3:
		return {"__v3": [v.x, v.y, v.z]}
	if v is Dictionary:
		var d := {}
		for k in v:
			d[k] = _ser(v[k])
		return d
	if v is Array:
		var a := []
		for x in v:
			a.append(_ser(x))
		return a
	return v


func _deser(v):
	if v is Dictionary:
		if v.has("__v2"):
			return Vector2i(int(v.__v2[0]), int(v.__v2[1]))
		if v.has("__v3"):
			return Vector3(v.__v3[0], v.__v3[1], v.__v3[2])
		var d := {}
		for k in v:
			d[k] = _deser(v[k])
		return d
	if v is Array:
		var a := []
		for x in v:
			a.append(_deser(x))
		return a
	return v


func to_dict() -> Dictionary:
	var ents := []
	for id in entities:
		ents.append(_ser(entities[id]))
	return {
		"v": 3, "money": money, "field": field.to_save(), "hand_n": hand_n, "hand_h": hand_h,
		"pile_size": pile_size, "pile_total": pile_total, "pile_n": pile_n, "pile_h": pile_h,
		"pile_found": pile_found, "pile_done": pile_done, "pile_gold": pile_gold, "hay_spots": _ser(hay_spots),
		"prestige": prestige, "run_earned": run_earned,
		"shop": shop, "tree": tree, "ups": ups, "achievements": achievements, "stats": stats,
		"settings": settings, "contract": contract, "offers": offers, "rates": rates, "quest": quest,
		"entities": ents, "next_id": next_id, "saved_at": Time.get_unix_time_from_system(),
	}


func save_slot(s: int) -> bool:
	var f := FileAccess.open(slot_path(s), FileAccess.WRITE)
	if not f:
		return false
	f.store_string(JSON.stringify(to_dict()))
	f.close()
	last_save = int(Time.get_unix_time_from_system())
	var m := FileAccess.open("user://last_slot.txt", FileAccess.WRITE)
	if m:
		m.store_string(str(s))
	return true


func _last_slot() -> int:
	if FileAccess.file_exists("user://last_slot.txt"):
		var s := int(FileAccess.get_file_as_string("user://last_slot.txt"))
		if s >= 1 and s <= SLOTS:
			return s
	return 1


## Lit un fichier JSON sans message d'erreur dans la console s'il est abîmé.
func _read_json(path: String):
	if not FileAccess.file_exists(path):
		return null
	var j := JSON.new()
	if j.parse(FileAccess.get_file_as_string(path)) != OK:
		return null
	return j.data


func slot_info(s: int) -> Dictionary:
	if not FileAccess.file_exists(slot_path(s)):
		return {}
	var d = _read_json(slot_path(s))
	if typeof(d) != TYPE_DICTIONARY:
		return {}
	return {"money": float(d.get("money", 0)), "pile": Data.PILES.get(d.get("pile_size", "petit"), Data.PILES.petit).name,
		"found": int(d.get("pile_found", 0)), "time": float(d.get("stats", {}).get("time", 0.0)),
		"saved_at": int(d.get("saved_at", 0))}


func delete_slot(s: int) -> void:
	if FileAccess.file_exists(slot_path(s)):
		DirAccess.remove_absolute(slot_path(s))


func load_slot(s: int) -> bool:
	if not FileAccess.file_exists(slot_path(s)):
		return false
	var d = _read_json(slot_path(s))
	if typeof(d) != TYPE_DICTIONARY or not from_dict(d):
		return false
	slot = s
	return true


func from_dict(d: Dictionary) -> bool:
	var v := int(d.get("v", 0))
	var ps: String = str(d.get("pile_size", ""))
	if v == 2 and ps == "enorme":
		ps = "gros" # le tas énorme n'existe plus
	if (v != 2 and v != 3) or not Data.PILES.has(ps):
		return false
	money = float(d.money)
	hand_n = int(d.get("hand_n", 0))
	hand_h = int(d.get("hand_h", 0))
	pile_size = ps
	pile_total = int(d.pile_total)
	pile_n = int(d.pile_n)
	pile_h = int(d.pile_h)
	if v == 2:
		# anciennes tailles de tas : on garde la proportion restante dans le nouveau tas
		var frac := float(pile_n + pile_h) / float(maxi(1, pile_total + Data.HAY_PER_PILE))
		pile_total = int(Data.PILES[ps].needles)
		pile_n = int(round(frac * pile_total))
		if pile_n + pile_h <= 0:
			pile_h = 0
	var full := float(pile_total + Data.HAY_PER_PILE)
	var radius_now := float(Data.PILES[ps].radius)
	if pile_n + pile_h <= 0:
		field.generate(radius_now, 1, 0.0)
		field.clear()
	elif typeof(d.get("field")) != TYPE_DICTIONARY or not field.from_save(d.field, radius_now):
		field.generate(radius_now, hash(ps), float(pile_n + pile_h) / full)
	_take_carry = 0.0
	hay_spots.clear()
	for sp in d.get("hay_spots", []):
		var v3 = _deser(sp)
		if v3 is Vector3:
			hay_spots.append(v3)
	if hay_spots.size() != pile_h:
		hay_spots.clear()
		_hay_bury(pile_h)
	pile_found = int(d.pile_found)
	pile_done = bool(d.pile_done)
	pile_gold = clampi(int(d.get("pile_gold", 0)), 0, Data.HAY_PER_PILE)
	prestige = maxi(0, int(d.get("prestige", 0)))
	shop = {}
	for k in d.get("shop", {}):
		if Data.SHOP.has(k):
			shop[k] = int(d.shop[k])
	tree = {"p_convoyeur": true}
	for k in d.get("tree", {}):
		if Data.TREE.has(k):
			tree[k] = true
	ups = {}
	for k in d.get("ups", {}):
		if Data.TREE_UPS.has(k):
			ups[k] = int(d.ups[k])
	achievements = d.get("achievements", {})
	stats = _blank_stats()
	var st: Dictionary = d.get("stats", {})
	for k in stats:
		if st.has(k):
			if typeof(stats[k]) == TYPE_FLOAT:
				stats[k] = float(st[k])
			elif typeof(stats[k]) == TYPE_DICTIONARY:
				stats[k] = st[k]
			else:
				stats[k] = int(st[k])
	var se: Dictionary = d.get("settings", {})
	for k in settings:
		if se.has(k) and k not in DEVICE_KEYS:
			settings[k] = se[k]
	run_earned = maxf(0.0, float(d.get("run_earned", stats.earned)))
	quest = clampi(int(d.get("quest", 0)), 0, Data.QUESTS.size())
	contract = d.get("contract", {})
	offers = d.get("offers", [])
	var ra: Dictionary = d.get("rates", {})
	for k in rates:
		rates[k] = float(ra.get(k, 0.0))
	entities = {}
	grid = {}
	for raw in d.get("entities", []):
		var e: Dictionary = _deser(raw)
		if not Data.MACHINES.has(e.get("type", "")) or not (e.get("c") is Vector2i):
			continue
		e.id = int(e.id)
		e.r = int(e.r)
		_normalize(e)
		entities[e.id] = e
		for cell in footprint(e.type, e.c, e.r):
			grid[cell] = e.id
	next_id = int(d.get("next_id", 1))
	for id in entities:
		next_id = maxi(next_id, id + 1)
	last_save = int(d.get("saved_at", Time.get_unix_time_from_system()))
	if offers.is_empty() and contract.is_empty():
		_roll_offers()
	_order_dirty = true
	entities_changed.emit()
	pile_changed.emit()
	changed.emit()
	return true


## Après un passage par JSON, les entiers redeviennent des entiers.
const _INT_KEYS := ["n", "h", "k", "oi", "state", "carry_n", "carry_h"]


func _normalize(e: Dictionary) -> void:
	for k in _INT_KEYS:
		if e.has(k):
			e[k] = int(e[k])
	for k in ["acc", "prog"]:
		if e.has(k):
			e[k] = float(e[k])
	if e.get("item") != null:
		_normalize(e.item)
	for k in ["outq", "inq", "q", "busy"]:
		if e.has(k):
			for it in e[k]:
				_normalize(it)


func new_game_in_slot(s: int) -> void:
	slot = s
	new_game()
	save_slot(s)


func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_PAUSED or what == NOTIFICATION_WM_CLOSE_REQUEST or what == NOTIFICATION_APPLICATION_FOCUS_OUT:
		save_slot(slot)


## Vibration courte, si le joueur ne l'a pas coupée.
func vibrate(ms: int) -> void:
	if settings.get("vibrate", true):
		Input.vibrate_handheld(ms)
