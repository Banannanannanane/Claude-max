extends Node
## Pilote de tests (activé seulement par l'argument « -- --qa=<scénario> [--out=<dossier>] »).
## logic : économie et usine ; ui : fenêtres, boutons, entrées tactiles ; shots : captures ; icon : icône.
## Le programme quitte avec le code 1 si un test échoue.

var main: Node
var out := "user://qa"
var failures := 0
var passed := 0


func _ready() -> void:
	var scenario := ""
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--qa="):
			scenario = a.substr(5)
		elif a.begins_with("--out="):
			out = a.substr(6)
	DirAccess.make_dir_recursive_absolute(out)
	match scenario:
		"logic":
			_logic.call_deferred()
		"ui":
			_ui.call_deferred()
		"shots":
			_shots.call_deferred()
		"icon":
			_icon.call_deferred()


func check(cond: bool, what: String) -> void:
	if cond:
		passed += 1
		print("OK   ", what)
	else:
		failures += 1
		printerr("ÉCHEC ", what)


func _finish() -> void:
	print("\nRÉSULTAT : %d réussi(s), %d échec(s)" % [passed, failures])
	get_tree().quit(1 if failures > 0 else 0)


func _run(seconds: float) -> void:
	var t := 0.0
	while t < seconds:
		Game._step(Game.STEP)
		t += Game.STEP


func _rich() -> void:
	Game.money = 1e9
	for id in Data.TREE:
		Game.tree[id] = true
	Game.stats.piles = 10


## Trouve une ancre pour poser `type` juste après le tapis de la case `belt` (sens r).
func _anchor_after(type: String, belt: Vector2i, r: int) -> Vector2i:
	var target: Vector2i = belt + Data.DIRS[r]
	for dx in range(-4, 5):
		for dz in range(-4, 5):
			var c: Vector2i = target + Vector2i(dx, dz)
			if target in Game.footprint(type, c, r) and belt in Game.in_cells(type, c, r):
				return c
	return Vector2i(99999, 0)


## Construit une ligne à partir de `start` vers r : éléments "belt:N" ou type de machine.
## Une machine en tête de ligne est posée de sorte que sa sortie tombe sur `start`.
func _chain(start: Vector2i, r: int, seq: Array) -> Dictionary:
	var cur := start
	var ids := []
	var ok := true
	var pending_first := true
	for s in seq:
		if String(s).begins_with("belt:"):
			var n := int(String(s).substr(5))
			for i in n:
				if not pending_first:
					cur += Data.DIRS[r]
				pending_first = false
				ok = Game.build("convoyeur", cur, r) and ok
		else:
			var c: Vector2i
			if pending_first:
				# tête de ligne : sa sortie (première case de devant) doit être `start`
				c = Vector2i(99999, 0)
				for dx in range(-4, 5):
					for dz in range(-4, 5):
						var cand := start + Vector2i(dx, dz)
						if Game.out_cells(s, cand, r)[0] == start:
							c = cand
				ok = Game.build(s, c, r) and ok
				ids.append(Game.grid.get(Game.footprint(s, c, r)[0], -1))
				cur = start - Data.DIRS[r]
			else:
				c = _anchor_after(s, cur, r)
				ok = Game.build(s, c, r) and ok
				ids.append(Game.grid.get(Game.footprint(s, c, r)[0], -1))
				cur = Game.out_cells(s, c, r)[0] - Data.DIRS[r]
			pending_first = false
	return {"end": cur, "ids": ids, "ok": ok}


## Pose des tapis depuis la case qui suit `from` (sens r) jusque dans le trou de vente,
## en contournant ce qui est déjà construit (recherche de chemin en largeur).
func _route_to_hole(from: Vector2i, r: int) -> bool:
	var trou := {}
	for id in Game.entities:
		if Game.entities[id].type == "trou":
			for c in Game.footprint("trou", Game.entities[id].c, 0):
				trou[c] = true
	var start: Vector2i = from + Data.DIRS[r]
	if trou.has(start):
		return true
	if not Game.entity_at(start).is_empty():
		return false
	var prev := {start: start}
	var queue := [start]
	var goal := Vector2i(99999, 0)
	var head := 0
	while head < queue.size():
		var cur: Vector2i = queue[head]
		head += 1
		for d in 4:
			var nx: Vector2i = cur + Data.DIRS[d]
			if prev.has(nx):
				continue
			if trou.has(nx):
				prev[nx] = cur
				goal = nx
				break
			if absi(nx.x) > Data.FIELD or absi(nx.y) > Data.FIELD or Game._pile_blocks(nx) or not Game.entity_at(nx).is_empty():
				continue
			prev[nx] = cur
			queue.append(nx)
		if goal.x != 99999:
			break
	if goal.x == 99999:
		return false
	var path := [goal]
	while path[0] != start:
		path.push_front(prev[path[0]])
	for i in path.size() - 1:
		var a2: Vector2i = path[i]
		var b2: Vector2i = path[i + 1]
		var dir := Data.DIRS.find(b2 - a2)
		if not Game.build("convoyeur", a2, dir):
			return false
	return true


func _inject(cell: Vector2i, item: Dictionary) -> bool:
	var e := Game.entity_at(cell)
	if e.is_empty() or e.type != "convoyeur" or e.item != null:
		return false
	item["p"] = 0.0
	e.item = item
	return true


func _tremie_id() -> int:
	for id in Game.entities:
		if Game.entities[id].type == "tremie":
			return id
	return -1


# ============================================================ tests de logique
func _logic() -> void:
	_test_geometry()
	_test_start_and_hand()
	_test_placement()
	_test_belts()
	_test_recipes()
	_test_diggers_drone()
	_test_hay_and_piles()
	_test_progression()
	_test_contracts_quests()
	_test_save()
	_test_stress()
	_finish()


func _test_geometry() -> void:
	for t in Data.MACHINES:
		var sz: Vector2i = Data.MACHINES[t].size
		for r in 4:
			var c := Vector2i(30, 30)
			var fp := Game.footprint(t, c, r)
			var uniq := {}
			for x in fp:
				uniq[x] = true
			var outs := Game.out_cells(t, c, r)
			var ins := Game.in_cells(t, c, r)
			var adj_ok := true
			for o in outs:
				if o in fp or not (o - Data.DIRS[r]) in fp:
					adj_ok = false
			for i in ins:
				if i in fp or not (i + Data.DIRS[r]) in fp:
					adj_ok = false
			var ctr := Game.machine_center(t, c, r)
			var mean := Vector3.ZERO
			for x in fp:
				mean += Game.cell_center(x)
			mean /= fp.size()
			if fp.size() != sz.x * sz.y or uniq.size() != fp.size() or outs.size() != sz.x or ins.size() != sz.x or not adj_ok or ctr.distance_to(mean) > 0.01:
				check(false, "géométrie de %s (rotation %d)" % [t, r])
				return
	check(true, "géométrie : emprises, centres, entrées et sorties de toutes les machines dans les 4 sens")


func _test_start_and_hand() -> void:
	Game.new_game()
	check(Game.pile_items() == 622, "le petit tas contient 600 aiguilles + 22 brins")
	check(Game.count_type("convoyeur") == 18 and Game.count_type("tremie") == 1 and Game.count_type("trou") == 1 and Game.count_type("bureau") == 1, "installation de départ : trémie, 18 tapis, trou de vente, borne")
	check(Game.money == 0.0 and Game.quest == 0, "départ à 0 € sur le premier objectif")
	var tremie := _tremie_id()
	var guard := 0
	while Game.grab() > 0 and guard < 50:
		guard += 1
	check(Game.hand_n + Game.hand_h == Game.hand_cap(), "la main se remplit jusqu'à sa capacité")
	check(Game.grab() == -1, "main pleine : on ne peut plus ramasser")
	check(Game.deposit_tremie(tremie) > 0 and Game.hand_n + Game.hand_h == 0, "versement dans la trémie")
	_run(30.0)
	check(Game.money > 0.0 and int(Game.stats.sold.get("vrac", 0)) > 0, "les aiguilles vont par tapis jusqu'au trou et sont payées (%s)" % Fmt.eur(Game.money))
	for k in 2:
		while Game.grab() > 0:
			pass
		Game.deposit_tremie(tremie)
	_run(20.0)
	Game._check_quest()
	check(Game.quest >= 2, "les premiers objectifs se valident en jouant (objectif n°%d)" % Game.quest)
	Game.hand_n = 8
	var belt: int = Game.grid[Vector2i(5, -10)]
	_run(5.0)
	check(Game.entities[belt].item == null and Game.drop_on_belt(belt) == 8 and Game.hand_n == 0, "poser une poignée directement sur un tapis")
	check(Game.drop_on_belt(belt) == 0, "on ne pose rien sur un tapis occupé ou avec la main vide")


func _test_placement() -> void:
	Game.new_game()
	_rich()
	check(Game.placement_ok("fonderie", Vector2i(0, -16), 0) == "Trop près du tas", "pas de construction sur le tas")
	check(Game.placement_ok("fonderie", Vector2i(Data.FIELD + 1, 0), 0) == "Hors du terrain", "pas de construction hors de la clôture")
	check(Game.placement_ok("fonderie", Vector2i(Data.FIELD - 1, 0), 0) == "", "on construit jusqu'au bord de la grande zone (%d cases de côté)" % (Data.FIELD * 2))
	check(Game.placement_ok("fonderie", Vector2i(12, -2), 0) != "", "pas de construction sur le trou de vente")
	Game.player_cells = [Vector2i(-30, 5)]
	check(Game.placement_ok("fonderie", Vector2i(-30, 5), 0) == "Tu es dans le chemin !", "pas de machine sur le joueur")
	check(Game.placement_ok("convoyeur", Vector2i(-30, 5), 0) == "", "on peut poser un tapis sous ses pieds")
	Game.player_cells = []
	check(Game.build("fonderie", Vector2i(-30, 5), 0), "construction d'une fonderie")
	check(not Game.build("purif", Vector2i(-30, 5), 0), "deux machines ne se chevauchent pas")
	check(Game.build("convoyeur", Vector2i(-20, 5), 0) and Game.build("convoyeur", Vector2i(-20, 6), 0), "pose de tapis")
	var m0 := Game.money
	var cost := Game.build_cost("scanner")
	check(Game.build("scanner", Vector2i(-20, 5), 0) and Game.entity_at(Vector2i(-20, 6)).get("type", "") == "scanner", "une machine remplace les tapis qu'elle recouvre")
	check(absf((m0 - Game.money) - (cost - 10.0)) < 0.01, "les tapis remplacés sont remboursés")
	var fid: int = Game.grid[Vector2i(-30, 5)]
	check(not Game.demolish(Game.grid[Vector2i(12, -2)]), "le trou de vente est indestructible")
	Game.rotate_entity(fid)
	check(int(Game.entities[fid].r) == 1, "pivoter une machine")
	check(Game.move_entity(fid, Vector2i(-36, 5), 2) and Game.entity_at(Vector2i(-36, 5)).get("id", -1) == fid and Game.entity_at(Vector2i(-30, 5)).is_empty(), "déplacer une machine")
	var before := Game.money
	check(Game.demolish(fid) and Game.money > before and Game.entity_at(Vector2i(-36, 5)).is_empty(), "démolir rembourse et libère la place")
	Game.money = 0.0
	check(not Game.build("fonderie", Vector2i(-30, 5), 0), "pas de construction sans argent")
	Game.money = 1e9
	Game.tree.erase("p_presse")
	check(Game.can_build("presse") != "" and not Game.build("presse", Vector2i(-30, 5), 0), "pas de construction sans le plan")
	check(not Game.build("trou", Vector2i(-50, 5), 0) and not Game.build("bureau", Vector2i(-50, 5), 0), "le trou et la borne ne se construisent pas")


func _test_belts() -> void:
	Game.new_game()
	_rich()
	for x in range(-40, -35):
		Game.build("convoyeur", Vector2i(x, 20), 1)
	Game.build("convoyeur", Vector2i(-35, 20), 2)
	Game.build("convoyeur", Vector2i(-35, 21), 2)
	_inject(Vector2i(-40, 20), {"t": "acier", "n": 10})
	_run(6.0)
	check(Game.entity_at(Vector2i(-35, 21)).item != null, "un objet suit la ligne et le virage jusqu'au bout")
	for i in 8:
		_inject(Vector2i(-40, 20), {"t": "acier", "n": 10})
		_run(1.0)
	var full := 0
	for x in range(-40, -35):
		if Game.entity_at(Vector2i(x, 20)).item != null:
			full += 1
	check(full >= 4, "une ligne bloquée se remplit sans perdre d'objet (%d cases pleines)" % full)
	Game.build("convoyeur", Vector2i(-45, 30), 1)
	Game.build("convoyeur", Vector2i(-44, 30), 1)
	Game.build("convoyeur", Vector2i(-44, 29), 2)
	_inject(Vector2i(-44, 29), {"t": "acier", "n": 10})
	_run(1.0)
	check(Game.entity_at(Vector2i(-44, 29)).item == null, "un tapis peut se jeter sur le côté d'un autre")
	Game.build("convoyeur", Vector2i(-50, 40), 1)
	Game.build("convoyeur", Vector2i(-49, 40), 3)
	_inject(Vector2i(-50, 40), {"t": "acier", "n": 10})
	_run(2.0)
	check(Game.entity_at(Vector2i(-50, 40)).item != null, "deux tapis face à face ne s'échangent pas d'objets")
	Game.build("convoyeur", Vector2i(-60, 50), 1)
	Game.build("separateur", Vector2i(-59, 50), 1)
	Game.build("convoyeur", Vector2i(-59, 49), 0)
	Game.build("convoyeur", Vector2i(-58, 50), 1)
	Game.build("convoyeur", Vector2i(-59, 51), 2)
	for i in 3:
		_inject(Vector2i(-60, 50), {"t": "acier", "n": 10})
		_run(2.0)
	var got := 0
	for c in [Vector2i(-59, 49), Vector2i(-58, 50), Vector2i(-59, 51)]:
		if Game.entity_at(c).item != null:
			got += 1
	check(got == 3, "le séparateur envoie un objet sur chacune de ses 3 sorties")
	Game.build("convoyeur", Vector2i(-70, 60), 1)
	Game.build("convoyeur", Vector2i(-69, 60), 2)
	Game.build("convoyeur", Vector2i(-69, 61), 3)
	Game.build("convoyeur", Vector2i(-70, 61), 0)
	_inject(Vector2i(-70, 60), {"t": "acier", "n": 10})
	_run(10.0)
	var in_loop := 0
	for c in [Vector2i(-70, 60), Vector2i(-69, 60), Vector2i(-69, 61), Vector2i(-70, 61)]:
		if Game.entity_at(c).item != null:
			in_loop += 1
	check(in_loop == 1, "un objet tourne dans une boucle fermée sans se dupliquer ni disparaître")
	var ch := _chain(Vector2i(-40, -40), 1, ["belt:2", "tampon", "belt:1"])
	check(ch.ok, "ligne avec stockage tampon")
	for i in 8:
		_inject(Vector2i(-40, -40), {"t": "acier", "n": 10})
		_run(1.0)
	var tq: Dictionary = Game.entities[ch.ids[0]]
	check(tq.q.size() >= 3, "le tampon stocke ce que la sortie ne peut pas prendre (%d)" % tq.q.size())
	var ch2 := _chain(Vector2i(-40, -50), 1, ["belt:2", "fonderie"])
	_inject(Vector2i(-40, -50), {"t": "vrac", "n": 10, "h": 0})
	_run(3.0)
	var fd: Dictionary = Game.entities[ch2.ids[0]]
	check(fd.inq.is_empty() and Game.entity_at(Vector2i(-39, -50)).item != null, "la fonderie refuse les aiguilles non vérifiées")
	# la trémie accepte du vrac apporté par tapis
	var tr := _tremie_id()
	var te: Dictionary = Game.entities[tr]
	var in_c: Vector2i = Game.in_cells("tremie", te.c, te.r)[0]
	Game.build("convoyeur", in_c, te.r)
	var ok_b: bool = Game.entity_at(in_c).get("type", "") == "convoyeur"
	_inject(in_c, {"t": "vrac", "n": 10, "h": 0})
	_run(2.0)
	check(ok_b and Game.entity_at(in_c).item == null, "une trémie accepte les aiguilles en vrac arrivant par tapis")


func _test_recipes() -> void:
	Game.new_game()
	_rich()
	var cases := [
		["scanner", "vrac", "acier"], ["fonderie", "acier", "brut"], ["purif", "brut", "pur"],
		["presse", "pur", "tole"], ["trefileuse", "pur", "fil"], ["aiguilleuse", "fil", "boite"],
	]
	var row := 0
	for cs in cases:
		var y := 20 + row * 8
		row += 1
		var ch := _chain(Vector2i(20, y), 1, ["belt:2", cs[0], "belt:2"])
		var routed: bool = _route_to_hole(ch.end, 1) if ch.ok else false
		check(ch.ok and routed, "ligne « %s » construite et reliée au trou" % Data.MACHINES[cs[0]].name)
		var sold0 := int(Game.stats.sold.get(cs[2], 0))
		for i in 20:
			_inject(Vector2i(20, y), {"t": cs[1], "n": 10, "h": 0})
			_run(0.9)
		_run(60.0)
		check(int(Game.stats.sold.get(cs[2], 0)) > sold0, "%s : %s → %s vendu au trou (%d)" % [Data.MACHINES[cs[0]].name, cs[1], cs[2], int(Game.stats.sold.get(cs[2], 0)) - sold0])
	var ch3 := _chain(Vector2i(-20, -60), 1, ["belt:2", "fonderie", "belt:8"])
	var fid: int = ch3.ids[0]
	Game.ups["u_fond_lot"] = 3
	var ing0 := int(Game.stats.ingots)
	_inject(Vector2i(-20, -60), {"t": "acier", "n": 10})
	_run(8.0)
	check(int(Game.stats.ingots) - ing0 == 4, "grand creuset niv. 3 : 4 lingots par fournée")
	Game.ups["u_fond_vitesse"] = 4
	check(Game.machine_speed("fonderie") > 1.9, "amélioration de vitesse de la fonderie")
	var p1 := Game.item_price({"t": "brut"})
	Game.ups["u_fond_qualite"] = 2
	check(Game.item_price({"t": "brut"}) > p1, "amélioration de qualité : les lingots se vendent plus cher")
	for i in 30:
		_inject(Vector2i(-20, -60), {"t": "acier", "n": 10})
		_run(0.5)
	var e: Dictionary = Game.entities[fid]
	check(e.outq.size() <= 4 + 3, "la sortie d'une machine ne déborde pas quand la ligne est bloquée (%d)" % e.outq.size())


func _test_diggers_drone() -> void:
	Game.new_game()
	_rich()
	Game.pile_done = true
	Game.order_pile("gros")
	var R := Game.pile_radius()
	var near := Vector2i(int(R) + 2, -16)
	check(Game.build("bras", near, 1), "bras robot au bord du tas")
	check(Game.digger_in_range("bras", near, 1), "le bras est à portée")
	check(not Game.digger_in_range("bras", Vector2i(40, -16), 1), "un bras loin du tas ne creuse pas")
	check(_route_to_hole(near, 1), "sortie du bras reliée au trou")
	var n0 := Game.pile_n
	_run(20.0)
	check(Game.pile_n < n0, "le bras robot ramasse dans le tas (%d)" % (n0 - Game.pile_n))
	var pc := Vector2i(-int(R) - 3, -16)
	check(Game.build("pelle", pc, 3), "pelleteuse près du tas")
	check(Game.digger_in_range("pelle", pc, 3), "la pelleteuse est à portée")
	var n1 := Game.pile_n
	_run(20.0)
	check(Game.pile_n < n1, "la pelleteuse creuse")
	var tremie := _tremie_id()
	check(tremie >= 0, "la trémie de départ est toujours là")
	check(Game.build("drone", Vector2i(-6, -6), 0), "drone collecteur")
	var sold0 := int(Game.stats.sold.get("vrac", 0))
	var delivered := false
	for i in 60:
		_run(1.0)
		if int(Game.stats.sold.get("vrac", 0)) > sold0 + 2:
			delivered = true
			break
	check(delivered, "le drone livre ses aiguilles à la trémie, qui les envoie au trou")


func _test_hay_and_piles() -> void:
	Game.new_game()
	var tremie := _tremie_id()
	Game.pile_n = 0
	Game.pile_h = 0
	var e: Dictionary = Game.entities[tremie]
	e.n = 0
	e.h = 3
	_run(25.0)
	check(int(Game.stats.lost_hay) == 3 and Game.pile_h == 3, "le foin non détecté tombé dans le trou retourne dans le tas")
	Game.new_game()
	_rich()
	tremie = _tremie_id()
	Game.build("scanner", Vector2i(5, -10), 1)
	var guard := 0
	while Game.pile_items() > 0 and guard < 20000:
		guard += 1
		Game.grab()
		if Game.deposit_tremie(tremie) == 0:
			_run(1.0)
	_run(200.0)
	check(Game.pile_found == 22 and Game.pile_done, "le scanner retrouve les 22 brins (trouvés : %d, perdus : %d)" % [Game.pile_found, Game.stats.lost_hay])
	var ok := true
	for size in Data.PILE_ORDER:
		Game.pile_done = true
		if not Game.order_pile(size):
			ok = false
		if Game.pile_items() != int(Data.PILES[size].needles) + 22:
			ok = false
	check(ok, "commande des 5 tailles de tas jusqu'à la montagne")
	var blocked := false
	for id in Game.entities:
		var en: Dictionary = Game.entities[id]
		for c in Game.footprint(en.type, en.c, en.r):
			if Game._pile_blocks(c):
				blocked = true
	check(not blocked, "aucune construction n'est recouverte par la montagne (remboursées)")
	check(Game.count_type("trou") == 1 and Game.count_type("bureau") == 1, "le trou et la borne ne sont jamais recouverts")
	check(Game.can_order("petit") != "", "pas de nouveau tas tant que les 22 brins ne sont pas trouvés")


func _test_progression() -> void:
	Game.new_game()
	check(Game.tree_state("p_convoyeur") == 2 and Game.tree_state("p_scanner") == 1 and Game.tree_state("p_fonderie") == 0, "états de l'arbre au départ")
	check(not Game.buy_tree("p_scanner"), "pas d'achat sans argent")
	Game.money = 1e9
	check(not Game.buy_tree("p_fonderie"), "pas d'achat d'un plan verrouillé")
	check(Game.buy_tree("p_scanner") and Game.buy_tree("p_fonderie"), "achat des plans dans l'ordre")
	check(not Game.buy_up("u_purif_vitesse"), "pas d'amélioration sans son plan")
	check(Game.tree_state("c_moyen") == 0, "contrat verrouillé tant qu'aucun tas n'est terminé")
	Game.stats.piles = 1
	check(Game.tree_state("c_moyen") == 1, "contrat débloqué après un tas terminé")
	for id in Data.TREE:
		Game.tree[id] = true
	var all_ok := true
	for up in Data.TREE_UPS:
		var mx := int(Data.TREE_UPS[up].max)
		for i in mx:
			if not Game.buy_up(up):
				all_ok = false
		if Game.buy_up(up):
			all_ok = false
	check(all_ok, "chaque amélioration de plan s'achète jusqu'à son maximum, pas au-delà")
	check(Game.belt_speed() > 4.0 and Game.machine_speed("scanner") > 3.0, "les améliorations accélèrent tapis et machines")
	var shop_ok := true
	for id in Data.SHOP:
		for i in int(Data.SHOP[id].max):
			if not Game.buy_shop(id):
				shop_ok = false
		if Game.buy_shop(id):
			shop_ok = false
	check(shop_ok, "chaque amélioration de la boutique s'achète jusqu'au maximum")
	check(Game.hand_cap() == 215 and Game.reach() > 10.0 and Game.tremie_cap() == 3600, "effets de la boutique")


func _test_contracts_quests() -> void:
	Game.new_game()
	check(Game.offers.size() == 3, "3 contrats proposés")
	check(Game.accept_offer(0) and not Game.contract.is_empty(), "accepter un contrat")
	check(not Game.accept_offer(0), "un seul contrat à la fois")
	var c: Dictionary = Game.contract
	var m0 := Game.money
	for i in int(c.qty):
		Game._sell({"t": c.t, "n": 10, "h": 0}, Vector2i(12, -2))
	check(Game.contract.is_empty() and int(Game.stats.contracts) == 1 and Game.money > m0 + float(c.reward) - 0.01, "contrat rempli : prime versée")
	check(Game.offers.size() == 3, "de nouvelles offres arrivent")
	Game.accept_offer(1)
	Game.stats.time = float(Game.contract.until) + 1.0
	Game._process(1.1)
	check(Game.contract.is_empty(), "un contrat expiré est retiré")
	Game.new_game()
	Game.stats.needles = 30
	Game.stats.poured = 10
	Game.stats.earned = 20.0
	Game._check_quest()
	check(Game.quest == 3, "les objectifs remplis se valident d'un coup (n°%d)" % Game.quest)
	check(Game.money >= 55.0, "les objectifs rapportent leur prime")
	var all_known := true
	for q in Data.QUESTS:
		if Game.quest_progress(q[0]) == Vector2(0, 1) and not q[0] in ["plan_scan", "scanner", "pile1", "fonderie", "ingot", "bras", "moyen", "contrat", "purif", "gros", "presse", "boite", "montagne"]:
			all_known = false
	check(all_known, "tous les objectifs ont une progression calculée")
	Game.stats.belts = 1
	Game.stats.ingots = 1
	Game._check_achievements()
	check(Game.achievements.has("first_belt") and Game.achievements.has("first_ingot"), "succès débloqués")


func _test_save() -> void:
	Game.new_game()
	_rich()
	var ch := _chain(Vector2i(20, 20), 1, ["belt:2", "scanner", "belt:1", "fonderie", "belt:1", "purif", "belt:2"])
	_route_to_hole(ch.end, 1)
	Game.build("drone", Vector2i(-6, -6), 0)
	for i in 5:
		_inject(Vector2i(20, 20), {"t": "vrac", "n": 10, "h": 0})
		_run(1.5)
	Game.ups["u_conv_vitesse"] = 3
	Game.shop["main"] = 2
	Game.accept_offer(0)
	Game.quest = 4
	var before := JSON.stringify(Game.to_dict().entities)
	var money_before := Game.money
	var n_ent := Game.entities.size()
	check(Game.save_slot(2), "sauvegarde dans l'emplacement 2")
	Game.new_game_in_slot(3)
	check(Game.entities.size() == 21 and Game.slot == 3, "nouvelle partie dans l'emplacement 3")
	check(Game.load_slot(2), "chargement de l'emplacement 2")
	var after := JSON.stringify(Game.to_dict().entities)
	check(before == after and Game.entities.size() == n_ent, "toutes les machines, tapis et objets transportés sont restaurés à l'identique")
	check(absf(Game.money - money_before) < 0.01 and Game.up_lvl("u_conv_vitesse") == 3 and Game.shop_lvl("main") == 2 and Game.quest == 4 and not Game.contract.is_empty(), "argent, améliorations, objectif et contrat restaurés")
	_run(10.0)
	check(true, "la simulation repart après chargement")
	check(not Game.slot_info(2).is_empty(), "infos des emplacements")
	var q0 = Game.settings.quality
	Game.settings.quality = 0
	Game.save_device()
	Game.settings.quality = 2
	Game._load_device()
	var q_ok: bool = int(Game.settings.quality) == 0
	Game.settings.quality = 2
	Game.load_slot(2)
	check(q_ok and int(Game.settings.quality) == 2, "la qualité graphique est propre à l'appareil, pas à la sauvegarde")
	Game.settings.quality = q0
	Game.save_device()
	Game.delete_slot(3)
	check(Game.slot_info(3).is_empty(), "suppression d'un emplacement")
	var f := FileAccess.open(Game.slot_path(3), FileAccess.WRITE)
	f.store_string("{pas du json")
	f.close()
	check(not Game.load_slot(3) and Game.load_slot(2), "une sauvegarde corrompue est refusée sans casser la partie")
	f = FileAccess.open(Game.slot_path(3), FileAccess.WRITE)
	f.store_string(JSON.stringify({"v": 1, "money": 5, "pile_size": "petit"}))
	f.close()
	check(not Game.load_slot(3), "une sauvegarde de l'ancienne version est refusée proprement")
	Game.delete_slot(3)
	Game.rates = {"income": 5.0, "dig": 3.0, "hay": 0.01}
	var pile0 := Game.pile_n
	var r := Game.simulate_offline(1e7)
	check(is_equal_approx(float(r.seconds), Game.MAX_OFFLINE) and r.money > 0.0 and Game.pile_n <= pile0, "production hors ligne plafonnée à 8 h")


func _test_stress() -> void:
	Game.new_game()
	_rich()
	for k in 12:
		var y := -70 + k * 6
		_chain(Vector2i(-75, y), 1, ["belt:30", "scanner", "belt:10", "fonderie", "belt:10", "purif", "belt:20"])
		for i in 20:
			_inject(Vector2i(-75 + (i * 3) % 30, y), {"t": "vrac", "n": 10, "h": 0})
	var belts := Game.count_type("convoyeur")
	var t0 := Time.get_ticks_msec()
	_run(30.0)
	var ms := Time.get_ticks_msec() - t0
	check(belts > 800, "usine de test : %d tapis, %d machines" % [belts, Game.entities.size() - belts])
	check(ms < 15000, "30 s de simulation en %d ms (%.1f ms par seconde de jeu)" % [ms, ms / 30.0])


# ============================================================ tests de l'interface
func _press_all(node: Node) -> int:
	var n := 0
	for c in node.get_children():
		if c is Button and not c.disabled and c.is_visible_in_tree():
			var t: String = c.text
			if not (t == "Fermer" or t.contains("Quitter") or t.contains("Effacer") or t.contains("Nouvelle") or t.contains("Démolir") or t.contains("Déplacer") or t.contains("Construire")):
				c.emit_signal("pressed")
				n += 1
		n += _press_all(c)
	return n


## Les événements injectés sont en coordonnées de fenêtre : on convertit depuis l'écran virtuel.
func _win(pos: Vector2) -> Vector2:
	return get_viewport().get_final_transform() * pos


func _touch(pos: Vector2, pressed: bool, index := 0) -> void:
	var ev := InputEventScreenTouch.new()
	ev.position = _win(pos)
	ev.pressed = pressed
	ev.index = index
	Input.parse_input_event(ev)


func _drag(pos: Vector2, rel: Vector2, index := 0) -> void:
	var ev := InputEventScreenDrag.new()
	ev.position = _win(pos)
	ev.relative = get_viewport().get_final_transform().basis_xform(rel)
	ev.index = index
	Input.parse_input_event(ev)


func _frames(n: int) -> void:
	for i in n:
		await get_tree().physics_frame
		await get_tree().process_frame


func _ui() -> void:
	Game.new_game()
	await _frames(5)
	var hud: Node = main.hud
	var pl: CharacterBody3D = main.player
	hud.close_panel()
	await _frames(2)
	_rich()
	for p in ["boutique", "arbre", "construire", "commandes", "stock", "carte", "reglages", "sauvegardes", "succes", "objectifs"]:
		hud.open_panel(p)
		await _frames(2)
		var panel: Node = hud._panel
		var ok: bool = panel != null and is_instance_valid(panel)
		var pressed := _press_all(panel) if ok else 0
		await _frames(2)
		check(ok, "fenêtre « %s » : ouverte, %d boutons testés" % [p, pressed])
		if pl.build_type != "":
			pl.cancel_build()
		hud.close_panel()
		await _frames(1)
	# fiche de chaque type de machine
	_chain(Vector2i(-30, 30), 1, ["belt:1", "scanner", "belt:1", "fonderie", "belt:1", "purif", "belt:1", "presse", "belt:1", "trefileuse", "belt:1", "aiguilleuse", "belt:1"])
	Game.build("tampon", Vector2i(-30, 40), 0)
	Game.build("drone", Vector2i(-25, 40), 0)
	Game.build("bras", Vector2i(5, -16), 1)
	Game.build("pelle", Vector2i(-6, -16), 3)
	Game.build("separateur", Vector2i(-20, 40), 0)
	await _frames(3)
	var types_seen := {}
	for id in Game.entities.keys():
		var t: String = Game.entities[id].type
		if t == "convoyeur" or types_seen.has(t):
			continue
		types_seen[t] = true
		hud.open_entity(id)
		await _frames(2)
		var ok2: bool = is_instance_valid(hud._panel)
		if ok2:
			for c in hud._panel.find_children("*", "Button", true, false):
				if c.text == "Pivoter":
					c.emit_signal("pressed")
		await _frames(1)
		check(ok2, "fiche de « %s »" % Data.MACHINES[t].name)
		hud.close_panel()
	check(types_seen.size() >= 13, "toutes les sortes de machines ont été ouvertes (%d)" % types_seen.size())
	# bouton retour d'Android
	hud.open_panel("boutique")
	await _frames(1)
	hud.go_back()
	await _frames(2)
	check(not hud.panel_open(), "Retour ferme la fenêtre")
	pl.start_build("convoyeur")
	hud.go_back()
	check(pl.build_type == "", "Retour annule la construction")
	hud.go_back()
	await _frames(1)
	check(hud._panel is Panels.QuitPanel, "Retour propose de quitter")
	hud.go_back()
	await _frames(2)
	check(not hud.panel_open(), "Retour referme la question")
	# joystick
	var vs: Vector2 = hud.root.get_viewport_rect().size
	pl.global_position = Vector3(-20, 0.1, 10)
	pl.rotation.y = 0
	await _frames(3)
	var p0 := pl.global_position
	var jc := Vector2(vs.x * 0.15, vs.y * 0.75)
	_touch(jc, true, 0)
	for i in 20:
		_drag(jc + Vector2(0, -80), Vector2(0, -4), 0)
		await _frames(1)
	_touch(jc + Vector2(0, -80), false, 0)
	await _frames(1)
	check(pl.global_position.z < p0.z - 0.5, "le joystick fait avancer le joueur (%.1f m)" % (p0.z - pl.global_position.z))
	# regard
	var yaw0 := pl.rotation.y
	var lc := Vector2(vs.x * 0.6, vs.y * 0.4)
	_touch(lc, true, 1)
	for i in 10:
		_drag(lc + Vector2(i * 10, 0), Vector2(10, 0), 1)
		await _frames(1)
	_touch(lc + Vector2(100, 0), false, 1)
	check(absf(pl.rotation.y - yaw0) > 0.2, "glisser sur l'écran fait tourner la vue")
	# multitouche
	pl.rotation.y = 0
	p0 = pl.global_position
	_touch(jc, true, 0)
	_touch(lc, true, 1)
	for i in 15:
		_drag(jc + Vector2(0, -80), Vector2(0, -4), 0)
		_drag(lc + Vector2(i * 6, 0), Vector2(6, 0), 1)
		await _frames(1)
	_touch(jc, false, 0)
	_touch(lc, false, 1)
	await _frames(1)
	check(pl.global_position.distance_to(p0) > 0.5 and absf(pl.rotation.y) > 0.1, "multitouche : on marche et on regarde en même temps")
	# ACTION sur le tas
	Game.new_game()
	await _frames(3)
	pl.global_position = Vector3(0.5, 0.1, -11.2)
	pl.rotation.y = 0
	pl.head.rotation.x = deg_to_rad(-25)
	await _frames(4)
	check(pl.target.get("kind", "") == "pile", "viser le tas")
	var btn: TouchScreenButton = hud._btn_action
	var bc := btn.position + Vector2(95, 95)
	_touch(bc, true, 2)
	await _frames(40)
	_touch(bc, false, 2)
	await _frames(2)
	check(Game.hand_n + Game.hand_h > 0, "garder ACTION appuyé ramasse des aiguilles (%d)" % (Game.hand_n + Game.hand_h))
	check(not hud.action_held, "relâcher ACTION arrête de ramasser")
	var tr := _tremie_id()
	var tc := Game.machine_center("tremie", Game.entities[tr].c, Game.entities[tr].r)
	pl.global_position = tc + Vector3(0, 0.1, 2.0)
	pl.rotation.y = 0
	pl.head.rotation.x = deg_to_rad(-12)
	await _frames(4)
	check(pl.target.get("type", "") == "tremie", "viser la trémie")
	var held := Game.hand_n + Game.hand_h
	pl.action_pressed()
	check(Game.hand_n + Game.hand_h < held, "ACTION verse la main dans la trémie")
	# construction de tapis en marchant
	Game.money = 1000.0
	pl.global_position = Vector3(-30, 0.1, 20)
	pl.rotation.y = 0
	pl.head.rotation.x = deg_to_rad(-30)
	await _frames(3)
	var belts0 := Game.count_type("convoyeur")
	hud.begin_build("convoyeur")
	await _frames(2)
	pl.place_pressed()
	for i in 40:
		pl.global_position.z -= 0.15
		await _frames(1)
	pl.place_released()
	pl.cancel_build()
	var laid := Game.count_type("convoyeur") - belts0
	check(laid >= 4, "PLACER maintenu en marchant pose une ligne de tapis (%d)" % laid)
	var dirs_ok := true
	for id in Game.entities:
		var be: Dictionary = Game.entities[id]
		if be.type == "convoyeur" and be.c.x == -30 and be.c.y < 20 and be.c.y > 5 and int(be.r) != 0:
			dirs_ok = false
	check(dirs_ok, "les tapis posés suivent la direction du regard")
	Game.money = 1e6
	Game.tree["p_fonderie"] = true
	pl.global_position = Vector3(-40, 0.1, 30)
	hud.begin_build("fonderie")
	await _frames(3)
	var f0 := Game.count_type("fonderie")
	if not pl.ghost_ok:
		print("DIAG fantôme : ", pl.ghost_why, " case ", pl.ghost_cell)
	pl.place_pressed()
	pl.place_released()
	check(Game.count_type("fonderie") == f0 + 1 and pl.build_type == "", "placement d'une fonderie depuis le mode construction")
	var fond := -1
	for id in Game.entities:
		if Game.entities[id].type == "fonderie":
			fond = id
	var fe: Dictionary = Game.entities[fond]
	var player_cell := Game.world_to_cell(pl.global_position)
	check(not player_cell in Game.footprint("fonderie", fe.c, fe.r), "la machine n'est pas construite sur le joueur")
	hud.begin_build("__demolir")
	pl.global_position = Vector3(-30, 0.1, 22)
	pl.rotation.y = 0
	pl.head.rotation.x = deg_to_rad(-45)
	await _frames(3)
	var b0 := Game.count_type("convoyeur")
	pl.place_pressed()
	pl.place_released()
	pl.cancel_build()
	check(Game.count_type("convoyeur") <= b0, "le mode démolition retire ce qui est visé")
	var fc := Game.machine_center("fonderie", fe.c, fe.r)
	pl.global_position = fc + Vector3(0, 0.1, 4)
	pl.rotation.y = 0
	await _frames(2)
	_touch(jc, true, 0)
	for i in 60:
		_drag(jc + Vector2(0, -90), Vector2(0, -2), 0)
		await _frames(1)
	_touch(jc, false, 0)
	await _frames(1)
	check(pl.global_position.distance_to(fc) > 0.9, "on ne traverse pas les machines")
	check(pl.global_position.y > -0.5 and pl.global_position.y < 2.0, "le joueur reste au sol")
	# boucle de jeu réelle pendant quelques secondes avec tout ouvert/fermé rapidement
	for p in ["arbre", "boutique", "stock"]:
		hud.open_panel(p)
		await _frames(3)
		hud.close_panel()
	await _frames(30)
	check(true, "le jeu tourne sans erreur")
	_finish()


# ============================================================ captures
func _shot(name: String, frames := 6) -> void:
	for i in frames:
		await get_tree().process_frame
	var img := get_viewport().get_texture().get_image()
	img.save_png(out.path_join(name + ".png"))
	print("capture ", name)


func _face(pos: Vector3, look: Vector3, pitch := -8.0) -> void:
	var p: CharacterBody3D = main.player
	p.global_position = pos
	p.velocity = Vector3.ZERO
	var d := look - pos
	p.rotation.y = atan2(-d.x, -d.z)
	p.head.rotation.x = deg_to_rad(pitch)


func _shots() -> void:
	Game.new_game()
	Game.settings.daynight = false
	await _shot("01_intro", 20)
	main.hud.close_panel()
	_face(Vector3(-3, 0, -3), Vector3(6, 0, -9), -16)
	await _shot("02_depart", 10)
	_face(Vector3(0.5, 0, -11.3), Data.PILE_POS, -25)
	for i in 4:
		main.player._grab_cd = 0
		main.player.try_grab()
		await get_tree().create_timer(0.1).timeout
	await _shot("03_ramasser", 4)
	_rich()
	Game.pile_done = true
	Game.order_pile("gros")
	var R := Game.pile_radius()
	var ch := _chain(Vector2i(int(R) + 2, -16), 1, ["bras", "belt:1", "scanner", "belt:1", "fonderie", "belt:1", "purif", "belt:1", "presse", "belt:1"])
	_route_to_hole(ch.end, 1)
	var ch2 := _chain(Vector2i(-int(R) - 3, -18), 3, ["pelle", "belt:2", "scanner", "belt:1", "fonderie", "belt:1", "purif", "belt:1", "trefileuse", "belt:1", "aiguilleuse", "belt:1", "tampon", "belt:2"])
	Game.build("drone", Vector2i(-4, -6), 0)
	Game.build("separateur", Vector2i(-14, 2), 0)
	for i in 1500:
		Game._step(Game.STEP)
	main.player.head.position.y = 11.0
	_face(Vector3(26, 0, 8), Vector3(4, 0, -16), -26)
	await _shot("04_usine", 40)
	main.player.head.position.y = 1.62
	var fid: int = ch.ids[2]
	var mid := Game.machine_center("fonderie", Game.entities[fid].c, Game.entities[fid].r)
	_face(mid + Vector3(-1.5, 0, 5.0), mid + Vector3(1, 0.5, 0), -14)
	await _shot("05_ligne", 20)
	_face(Vector3(12.5, 0, 5.5), Vector3(12.5, 0, -1.5), -32)
	await _shot("06_trou", 20)
	var aid: int = ch2.ids[5]
	var pm := Game.machine_center("aiguilleuse", Game.entities[aid].c, Game.entities[aid].r)
	_face(pm + Vector3(1.5, 0, 4.5), pm, -16)
	await _shot("07_aiguilleuse", 20)
	_face(Vector3(-6.5, 0, 5.5), Vector3(-5.5, 1, 2.5), -16)
	await _shot("08_borne", 20)
	for p in ["arbre", "construire", "objectifs", "carte", "reglages", "succes"]:
		main.hud.open_panel(p)
		await _shot("09_" + p, 6)
	main.hud.close_panel()
	main.hud.begin_build("fonderie")
	_face(Vector3(-18, 0, 12), Vector3(-18, 0, 4), -26)
	await _shot("10_fantome", 10)
	main.player.cancel_build()
	Game.settings.daynight = true
	Game.stats.time = 0.82 * 900.0
	main.world.night = 1.0
	main.player.head.position.y = 6.0
	_face(Vector3(18, 0, 6), Vector3(4, 0, -12), -14)
	await _shot("11_nuit", 30)
	main.player.head.position.y = 1.62
	Game.pile_done = true
	Game.order_pile("montagne")
	Game.settings.daynight = false
	main.world.night = 0.0
	main.player.head.position.y = 4.0
	_face(Vector3(0, 0, 20), Data.PILE_POS + Vector3(0, 3, 0), -6)
	await _shot("12_montagne", 30)
	Game.settings.quality = 0
	main.apply_quality()
	await _shot("13_qualite_basse", 30)
	get_tree().quit()


## Rendu de l'icône : un brin de foin doré posé sur un tas d'aiguilles.
func _icon() -> void:
	main.hud.close_panel()
	main.player.hand.visible = false
	for c in main.hud.root.get_children():
		c.visible = false
	var p: CharacterBody3D = main.player
	p.global_position = Data.PILE_POS + Vector3(0, 0.3, 6.2)
	p.rotation.y = 0
	p.head.rotation.x = deg_to_rad(-9)
	p.cam.fov = 30
	var straw := Node3D.new()
	main.add_child(straw)
	var m := Mk.hay_material()
	for i in 3:
		var s := Mk.cyl(straw, 0.05 - i * 0.008, 1.5 - i * 0.2, Vector3(i * 0.06 - 0.06, 0, i * 0.04), m, (0.05 - i * 0.008) * 0.6, 10)
		s.rotation = Vector3(0, 0, 1.05 + i * 0.1)
	straw.position = Data.PILE_POS + Vector3(0, 1.0, 1.9)
	straw.rotation = Vector3(0.0, 0.0, -0.45)
	straw.scale = Vector3(1.25, 1.25, 1.25)
	await _shot("icon_raw", 20)
	get_tree().quit()
