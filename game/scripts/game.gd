extends Node
## État de la partie et économie : tas, foin caché, entrepôt, machines, progression, sauvegarde.

signal changed
signal toast(text: String, gold: bool)
signal hay_found(count: int, by_hand: bool)
signal buildings_changed
signal pile_changed
signal pile_completed

const SAVE_PATH := "user://save.json"
const MAX_OFFLINE := 8.0 * 3600.0

# --- argent et stocks
var money := 0.0
var hay_stock := 0
var hay_stock_value := 0.0
var hand_n := 0 # aiguilles en main
var hand_h := 0 # foin caché dans la main
var vrac_n := 0 # entrepôt : aiguilles non vérifiées
var vrac_h := 0 # foin caché dans le vrac
var table_n := 0 # file d'attente des tables de tri
var table_h := 0
var verified := 0
var raw := 0 # lingots bruts
var pure := 0 # lingots purs

# --- tas en cours
var pile_size := "petit"
var pile_total := 400
var pile_n := 400
var pile_h := 22
var pile_found := 0
var pile_done := false

# --- progression
var up := {}
var tree := {"licence": true}
var buildings: Array = [] # [{type, x, z, rot}]
var stats := {"needles": 0, "hay": 0, "piles": 0, "sold": 0, "earned": 0.0, "ingots": 0, "time": 0.0}
var settings := {"sound": true, "sens": 1.0, "truck_hay": false}
var last_save := 0

var offline := false
var _acc := {}
var _activity := {} # type -> horloge du dernier travail (animations)
var _clock := 0.0
var _save_acc := 0.0
var _changed_acc := 0.0
var _toast_flood := 0.0


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	if not load_game():
		new_game()


func new_game() -> void:
	money = 0.0
	hay_stock = 0
	hay_stock_value = 0.0
	hand_n = 0
	hand_h = 0
	vrac_n = 0
	vrac_h = 0
	table_n = 0
	table_h = 0
	verified = 0
	raw = 0
	pure = 0
	up = {}
	tree = {"licence": true}
	stats = {"needles": 0, "hay": 0, "piles": 0, "sold": 0, "earned": 0.0, "ingots": 0, "time": 0.0}
	buildings = [
		{"type": "table", "x": 5.0, "z": -2.0, "rot": 0},
		{"type": "entrepot", "x": -8.0, "z": -1.0, "rot": 1},
		{"type": "comptoir", "x": 5.5, "z": 6.0, "rot": 3},
		{"type": "bureau", "x": -5.0, "z": 7.0, "rot": 0},
	]
	_set_pile("petit")
	buildings_changed.emit()
	changed.emit()


# ============================================================ formules
func lvl(id: String) -> int:
	return int(up.get(id, 0))


func has_right(id: String) -> bool:
	return id == "" or tree.has(id)


func upgrade_cost(id: String) -> float:
	var u: Dictionary = Data.UPGRADES[id]
	return round(u.base * pow(u.k, lvl(id)))


func hand_cap() -> int:
	return 15 + 10 * lvl("main")


func grab_amount() -> int:
	return 3 + 2 * lvl("poignee")


func stamina_max() -> float:
	return 100.0 + 25.0 * lvl("endurance")


func stamina_regen() -> float:
	return 14.0 + 5.0 * lvl("recup")


func walk_speed() -> float:
	return 4.2 + 0.45 * lvl("vitesse")


func reach() -> float:
	return 3.6 + 0.75 * lvl("portee")


func spot_chance() -> float:
	return minf(0.9, 0.3 + 0.07 * lvl("oeil"))


func count_type(t: String) -> int:
	var n := 0
	for b in buildings:
		if b.type == t:
			n += 1
	return n


func capacity() -> int:
	return count_type("entrepot") * (300 + 150 * lvl("etageres"))


func used() -> int:
	return vrac_n + vrac_h + verified + raw + pure


func free_space() -> int:
	return maxi(0, capacity() - used())


func table_cap() -> int:
	return count_type("table") * 120


func price_mult() -> float:
	return (1.0 + 0.05 * lvl("m_vendeur")) * (1.25 if tree.has("contrats") else 1.0)


func hay_unit_value() -> float:
	return Data.PILES[pile_size].hay_value * (1.5 if tree.has("magnat") else 1.0)


func machine_mult() -> float:
	return 1.5 if tree.has("auto") else 1.0


func pile_radius() -> float:
	var base: float = Data.PILES[pile_size].radius
	var left := float(pile_n + pile_h) / float(maxi(1, pile_total + Data.HAY_PER_PILE))
	return base * clampf(pow(left, 1.0 / 3.0), 0.0, 1.0)


func pile_items() -> int:
	return pile_n + pile_h


func building_cost(t: String) -> float:
	var base: float = Data.BUILDINGS[t].cost
	var n := count_type(t)
	if t == "table" or t == "entrepot":
		n -= 1 # le premier est offert
	return round(base * pow(1.5, maxi(0, n)))


func is_active(t: String) -> bool:
	return _clock - float(_activity.get(t, -10.0)) < 1.2


func digger_in_range(b: Dictionary) -> bool:
	var r: float = Data.BUILDINGS[b.type].get("range", 0.0)
	var d := Vector2(b.x - Data.PILE_POS.x, b.z - Data.PILE_POS.z).length()
	return pile_items() > 0 and d - pile_radius() <= r


func rate_of(t: String) -> float:
	var m := machine_mult()
	match t:
		"bras":
			return 2.5 * (1.0 + 0.25 * lvl("m_bras")) * m
		"pelle":
			return 18.0 * (1.0 + 0.25 * lvl("m_pelle")) * m
		"table":
			return (2.0 + 1.5 * lvl("tri")) * (2.0 if tree.has("tri_express") else 1.0)
		"verif":
			return 8.0 * (1.0 + 0.3 * lvl("m_verif")) * m
		"fonderie":
			return 0.4 * (1.0 + 0.25 * lvl("m_fonderie")) * m
		"purif":
			return 0.25 * (1.0 + 0.25 * lvl("m_purif")) * m
		"vendeur":
			return 3.0 * (1.0 + 0.2 * lvl("m_vendeur")) * m
	return 0.0


# ============================================================ notifications
func _toast(text: String, gold := false) -> void:
	if not offline:
		toast.emit(text, gold)


func _sfx(name: String) -> void:
	if not offline:
		Sfx.play(name)


func gain(x: float) -> void:
	money += x
	stats.earned += x


## Arrondi aléatoire : 2,3 → 2 (70 %) ou 3 (30 %).
func _sround(x: float) -> int:
	var f := floorf(x)
	return int(f) + (1 if randf() < x - f else 0)


## Tire k objets d'un mélange (n aiguilles, h foin). Renvoie Vector2i(aiguilles, foin).
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
	pile_found += count
	hay_stock += count
	hay_stock_value += count * hay_unit_value()
	stats.hay += count
	if not offline:
		hay_found.emit(count, by_hand)
		_sfx("hay")
		_toast("Brin de foin trouvé ! (%d / %d)" % [pile_found, Data.HAY_PER_PILE], true)
	if pile_found >= Data.HAY_PER_PILE and not pile_done:
		pile_done = true
		stats.piles += 1
		var bonus := Data.HAY_PER_PILE * hay_unit_value() * 0.5
		gain(bonus)
		_sfx("win")
		_toast("%s terminé : les 22 brins sont trouvés ! Prime : %s" % [Data.PILES[pile_size].name, Fmt.eur(bonus)], true)
		pile_completed.emit()


# ============================================================ actions du joueur
## Ramasse une poignée dans le tas. Renvoie le nombre d'objets pris (-1 main pleine).
func grab() -> int:
	var room := hand_cap() - hand_n - hand_h
	if room <= 0:
		return -1
	var got := _draw_mix(mini(grab_amount(), room), pile_n, pile_h)
	if got.x + got.y <= 0:
		return 0
	pile_n -= got.x
	pile_h -= got.y
	hand_n += got.x
	stats.needles += got.x
	var seen := 0
	for i in got.y:
		if randf() < spot_chance():
			seen += 1
		else:
			hand_h += 1
	_found(seen, true)
	_activity["main"] = _clock
	pile_changed.emit()
	changed.emit()
	return got.x + got.y


func deposit_table() -> int:
	var room := table_cap() - table_n - table_h
	var q := mini(room, hand_n + hand_h)
	if q <= 0:
		return 0
	var mix := _draw_mix(q, hand_n, hand_h)
	hand_n -= mix.x
	hand_h -= mix.y
	table_n += mix.x
	table_h += mix.y
	changed.emit()
	return q


func deposit_storage() -> int:
	var q := mini(free_space(), hand_n + hand_h)
	if q <= 0:
		return 0
	var mix := _draw_mix(q, hand_n, hand_h)
	hand_n -= mix.x
	hand_h -= mix.y
	vrac_n += mix.x
	vrac_h += mix.y
	changed.emit()
	return q


func buy_upgrade(id: String) -> bool:
	var u: Dictionary = Data.UPGRADES[id]
	var c := upgrade_cost(id)
	if lvl(id) >= int(u.max) or money < c or not has_right(u.get("right", "")):
		return false
	money -= c
	up[id] = lvl(id) + 1
	_sfx("buy")
	changed.emit()
	return true


func tree_state(id: String) -> int:
	## 0 verrouillé, 1 achetable, 2 acquis
	if tree.has(id):
		return 2
	var n: Dictionary = Data.TREE[id]
	for r in n.req:
		if not tree.has(r):
			return 0
	if stats.piles < int(n.get("piles", 0)):
		return 0
	return 1


func buy_tree(id: String) -> bool:
	if tree_state(id) != 1:
		return false
	var c: float = Data.TREE[id].cost
	if money < c:
		return false
	money -= c
	tree[id] = true
	_sfx("buy")
	_toast("Débloqué : %s" % Data.TREE[id].name, true)
	changed.emit()
	return true


func can_build(t: String) -> bool:
	var b: Dictionary = Data.BUILDINGS[t]
	return b.buildable and has_right(b.right) and money >= building_cost(t)


func place_building(t: String, pos: Vector3, rot: int) -> bool:
	if not can_build(t):
		return false
	money -= building_cost(t)
	buildings.append({"type": t, "x": snappedf(pos.x, 0.5), "z": snappedf(pos.z, 0.5), "rot": rot})
	_sfx("buy")
	buildings_changed.emit()
	changed.emit()
	return true


## Déplacement gratuit d'un bâtiment existant.
func move_building(i: int, pos: Vector3, rot: int) -> void:
	buildings[i].x = snappedf(pos.x, 0.5)
	buildings[i].z = snappedf(pos.z, 0.5)
	buildings[i].rot = rot
	buildings_changed.emit()


func demolish(i: int) -> bool:
	var b: Dictionary = buildings[i]
	if not Data.BUILDINGS[b.type].buildable:
		return false
	if (b.type == "table" or b.type == "entrepot") and count_type(b.type) <= 1:
		_toast("Il te faut garder au moins un exemplaire de ce bâtiment.")
		return false
	buildings.remove_at(i)
	if b.type == "entrepot":
		# ce qui ne rentre plus est perdu, en commençant par le vrac
		var over := used() - capacity()
		if over > 0:
			var lost := _draw_mix(mini(over, vrac_n + vrac_h), vrac_n, vrac_h)
			vrac_n -= lost.x
			vrac_h -= lost.y
			pile_h += lost.y # le foin perdu retourne au tas pour rester trouvable
	gain(building_cost(b.type) * 0.5)
	buildings_changed.emit()
	changed.emit()
	return true


## Peut-on poser une emprise (rayon r) en pos ? ignore = index à ignorer (déplacement).
func placement_ok(t: String, pos: Vector3, ignore := -1) -> String:
	var sz: Vector2 = Data.BUILDINGS[t].size
	var r := maxf(sz.x, sz.y) * 0.5
	if absf(pos.x) > Data.FIELD - r or absf(pos.z) > Data.FIELD - r:
		return "Hors du terrain"
	var dp := Vector2(pos.x - Data.PILE_POS.x, pos.z - Data.PILE_POS.z).length()
	if dp < pile_radius() + r + 0.3:
		return "Trop près du tas"
	for i in buildings.size():
		if i == ignore:
			continue
		var o: Dictionary = buildings[i]
		var os: Vector2 = Data.BUILDINGS[o.type].size
		var orr := maxf(os.x, os.y) * 0.5
		if Vector2(pos.x - o.x, pos.z - o.z).length() < (r + orr) * 0.82:
			return "Emplacement occupé"
	if Vector2(pos.x, pos.z - 12.0).length() < r + 1.0:
		return "Laisse le passage libre"
	return ""


func order_cost(size: String) -> float:
	return Data.PILES[size].order


func can_order(size: String) -> String:
	if not has_right(Data.PILES[size].right):
		return "Droit requis dans l'arbre"
	if not pile_done:
		return "Trouve d'abord les 22 brins du tas actuel"
	if money < order_cost(size):
		return "Pas assez d'argent"
	return ""


func order_pile(size: String) -> bool:
	if can_order(size) != "":
		return false
	money -= order_cost(size)
	_set_pile(size)
	_sfx("win")
	_toast("%s livré ! 22 brins de foin y sont cachés." % Data.PILES[size].name, true)
	changed.emit()
	return true


func _set_pile(size: String) -> void:
	pile_size = size
	pile_total = int(Data.PILES[size].needles)
	pile_n = pile_total
	pile_h = Data.HAY_PER_PILE
	pile_found = 0
	pile_done = false
	# Le foin encore caché ailleurs (ancien tas) ne compte plus.
	hand_h = 0
	vrac_h = 0
	table_h = 0
	# Les bâtiments recouverts par un tas plus grand sont poussés sur le côté.
	var R: float = Data.PILES[size].radius
	var moved := false
	for b in buildings:
		var sz: Vector2 = Data.BUILDINGS[b.type].size
		var r := maxf(sz.x, sz.y) * 0.5
		var d := Vector2(b.x - Data.PILE_POS.x, b.z - Data.PILE_POS.z)
		if d.length() < R + r + 0.3:
			var dir := d.normalized() if d.length() > 0.01 else Vector2(1, 0)
			var p := Vector2(Data.PILE_POS.x, Data.PILE_POS.z) + dir * (R + r + 0.8)
			b.x = snappedf(p.x, 0.5)
			b.z = snappedf(p.y, 0.5)
			moved = true
	if moved:
		buildings_changed.emit()
	pile_changed.emit()


func sell(kind: String, q: int) -> float:
	var have := stock_of(kind)
	q = mini(q, have)
	if q <= 0:
		return 0.0
	var total := q * unit_price(kind)
	match kind:
		"verified":
			verified -= q
		"raw":
			raw -= q
		"pure":
			pure -= q
		"hay":
			hay_stock_value -= total / price_mult()
			hay_stock -= q
			if hay_stock <= 0:
				hay_stock_value = 0.0
	gain(total)
	stats.sold += q
	_sfx("cash")
	changed.emit()
	return total


func stock_of(kind: String) -> int:
	match kind:
		"verified":
			return verified
		"raw":
			return raw
		"pure":
			return pure
		"hay":
			return hay_stock
	return 0


func unit_price(kind: String) -> float:
	match kind:
		"verified":
			return Data.PRICE_NEEDLE * price_mult()
		"raw":
			return Data.PRICE_RAW * price_mult()
		"pure":
			return Data.PRICE_PURE * price_mult()
		"hay":
			return (hay_stock_value / maxf(1.0, hay_stock)) * price_mult() if hay_stock > 0 else hay_unit_value() * price_mult()
	return 0.0


# ============================================================ simulation des machines
func _work(key: String, rate: float, dt: float) -> int:
	if rate <= 0.0:
		_acc[key] = 0.0
		return 0
	var a: float = _acc.get(key, 0.0) + rate * dt
	var n := int(floor(a))
	_acc[key] = minf(a - n, maxf(1.0, rate))
	return n


func tick(dt: float) -> void:
	_clock += dt
	stats.time += dt

	# creuseurs : bras robots et pelleteuses à portée du tas
	var dig := 0.0
	var any_bras := false
	var any_pelle := false
	for b in buildings:
		if (b.type == "bras" or b.type == "pelle") and digger_in_range(b):
			dig += rate_of(b.type)
			if b.type == "bras":
				any_bras = true
			else:
				any_pelle = true
	var nd := mini(_work("dig", dig, dt), free_space())
	if nd > 0:
		var got := _draw_mix(nd, pile_n, pile_h)
		if got.x + got.y > 0:
			pile_n -= got.x
			pile_h -= got.y
			vrac_n += got.x
			vrac_h += got.y
			stats.needles += got.x
			if any_bras:
				_activity["bras"] = _clock
			if any_pelle:
				_activity["pelle"] = _clock
			if not offline:
				pile_changed.emit()

	# tables de tri
	var nt := mini(_work("table", rate_of("table") * count_type("table"), dt), free_space())
	if nt > 0 and table_n + table_h > 0:
		var mix := _draw_mix(nt, table_n, table_h)
		table_n -= mix.x
		table_h -= mix.y
		verified += mix.x
		_activity["table"] = _clock
		_found(mix.y, false)

	# vérificateurs
	var nv := _work("verif", rate_of("verif") * count_type("verif"), dt)
	if nv > 0 and vrac_n + vrac_h > 0:
		var mix2 := _draw_mix(nv, vrac_n, vrac_h)
		vrac_n -= mix2.x
		vrac_h -= mix2.y
		verified += mix2.x
		_activity["verif"] = _clock
		_found(mix2.y, false)

	# fonderies : 10 aiguilles vérifiées -> 1 lingot brut (libère de la place)
	var nf := mini(_work("fonderie", rate_of("fonderie") * count_type("fonderie"), dt), verified / Data.NEEDLES_PER_INGOT)
	if nf > 0:
		verified -= nf * Data.NEEDLES_PER_INGOT
		raw += nf
		stats.ingots += nf
		_activity["fonderie"] = _clock

	# purificateurs
	var np := mini(_work("purif", rate_of("purif") * count_type("purif"), dt), raw)
	if np > 0:
		raw -= np
		pure += np
		_activity["purif"] = _clock

	# camions de vente : lingots purs, puis bruts, puis aiguilles (et foin si demandé)
	var ns := _work("vendeur", rate_of("vendeur") * count_type("vendeur"), dt)
	if ns > 0:
		var sold := 0.0
		for kind in ["pure", "raw", "verified", "hay"]:
			if kind == "hay" and not settings.truck_hay:
				continue
			var q := mini(ns, stock_of(kind))
			if kind == "verified":
				q = mini(stock_of(kind), ns * 10) # les aiguilles partent par cartons
			if q > 0:
				var was := offline
				offline = true # pas de bruit de caisse à chaque carton
				sold += sell(kind, q)
				offline = was
				ns -= q if kind != "verified" else int(ceil(q / 10.0))
			if ns <= 0:
				break
		if sold > 0.0:
			_activity["vendeur"] = _clock

	if not offline:
		_changed_acc += dt
		if _changed_acc >= 0.25:
			_changed_acc = 0.0
			changed.emit()
		_save_acc += dt
		if _save_acc >= 15.0:
			_save_acc = 0.0
			save_game()


func _process(delta: float) -> void:
	tick(minf(delta, 0.25))


## Fait tourner les machines pendant l'absence du joueur. Renvoie un résumé.
func simulate(seconds: float) -> Dictionary:
	seconds = minf(seconds, MAX_OFFLINE)
	var m0 := money
	var n0: int = stats.needles
	var h0: int = stats.hay
	var i0: int = stats.ingots
	offline = true
	var t := seconds
	while t > 0.0:
		var d := minf(1.0, t)
		tick(d)
		t -= d
	offline = false
	pile_changed.emit()
	changed.emit()
	return {"seconds": seconds, "money": money - m0, "needles": stats.needles - n0, "hay": stats.hay - h0, "ingots": stats.ingots - i0}


# ============================================================ sauvegarde
func to_dict() -> Dictionary:
	return {
		"v": 1, "money": money, "hay_stock": hay_stock, "hay_stock_value": hay_stock_value,
		"hand_n": hand_n, "hand_h": hand_h, "vrac_n": vrac_n, "vrac_h": vrac_h, "table_n": table_n, "table_h": table_h,
		"verified": verified, "raw": raw, "pure": pure,
		"pile_size": pile_size, "pile_total": pile_total, "pile_n": pile_n, "pile_h": pile_h,
		"pile_found": pile_found, "pile_done": pile_done,
		"up": up, "tree": tree, "buildings": buildings, "stats": stats, "settings": settings,
		"last_save": Time.get_unix_time_from_system(),
	}


func save_game() -> void:
	var f := FileAccess.open(SAVE_PATH, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(to_dict()))
		last_save = int(Time.get_unix_time_from_system())


func load_game() -> bool:
	if not FileAccess.file_exists(SAVE_PATH):
		return false
	var txt := FileAccess.get_file_as_string(SAVE_PATH)
	var d = JSON.parse_string(txt)
	if typeof(d) != TYPE_DICTIONARY:
		return false
	return from_dict(d)


func from_dict(d: Dictionary) -> bool:
	if not d.has("pile_size") or not Data.PILES.has(d.pile_size):
		return false
	money = float(d.get("money", 0.0))
	hay_stock = int(d.get("hay_stock", 0))
	hay_stock_value = float(d.get("hay_stock_value", 0.0))
	hand_n = int(d.get("hand_n", 0))
	hand_h = int(d.get("hand_h", 0))
	vrac_n = int(d.get("vrac_n", 0))
	vrac_h = int(d.get("vrac_h", 0))
	table_n = int(d.get("table_n", 0))
	table_h = int(d.get("table_h", 0))
	verified = int(d.get("verified", 0))
	raw = int(d.get("raw", 0))
	pure = int(d.get("pure", 0))
	pile_size = d.pile_size
	pile_total = int(d.get("pile_total", Data.PILES[pile_size].needles))
	pile_n = int(d.get("pile_n", pile_total))
	pile_h = int(d.get("pile_h", 0))
	pile_found = int(d.get("pile_found", 0))
	pile_done = bool(d.get("pile_done", false))
	up = {}
	for k in d.get("up", {}):
		if Data.UPGRADES.has(k):
			up[k] = int(d.up[k])
	tree = {"licence": true}
	for k in d.get("tree", {}):
		if Data.TREE.has(k):
			tree[k] = true
	buildings = []
	for b in d.get("buildings", []):
		if typeof(b) == TYPE_DICTIONARY and Data.BUILDINGS.has(b.get("type", "")):
			buildings.append({"type": b.type, "x": float(b.x), "z": float(b.z), "rot": int(b.get("rot", 0))})
	var st: Dictionary = d.get("stats", {})
	for k in stats:
		if st.has(k):
			stats[k] = st[k] if typeof(stats[k]) == TYPE_FLOAT else int(st[k])
	var se: Dictionary = d.get("settings", {})
	for k in settings:
		if se.has(k):
			settings[k] = se[k]
	last_save = int(d.get("last_save", Time.get_unix_time_from_system()))
	buildings_changed.emit()
	pile_changed.emit()
	changed.emit()
	return true


func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_PAUSED or what == NOTIFICATION_WM_CLOSE_REQUEST or what == NOTIFICATION_APPLICATION_FOCUS_OUT:
		save_game()
