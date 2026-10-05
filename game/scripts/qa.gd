extends Node
## Pilote de tests (activé seulement par l'argument « -- --qa=<scénario> [--out=<dossier>] »).
## logic : vérifie l'économie et quitte avec un code d'erreur ; shots : prend des captures.

var main: Node
var out := "user://qa"
var failures := 0


func _ready() -> void:
	var scenario := ""
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--qa="):
			scenario = a.substr(5)
		elif a.begins_with("--out="):
			out = a.substr(6)
	DirAccess.make_dir_recursive_absolute(out)
	if scenario == "logic":
		_logic.call_deferred()
	elif scenario == "icon":
		_icon.call_deferred()
	elif scenario == "shots":
		_shots.call_deferred()


func check(cond: bool, what: String) -> void:
	if cond:
		print("OK   ", what)
	else:
		failures += 1
		printerr("ÉCHEC ", what)


func _run(seconds: float) -> void:
	var t := 0.0
	while t < seconds:
		Game._step(Game.STEP)
		t += Game.STEP


func _logic() -> void:
	Game.new_game()
	check(Game.pile_items() == 622, "le petit tas contient 600 aiguilles + 22 brins")
	check(Game.count_type("convoyeur") == 14 and Game.count_type("tremie") == 1 and Game.count_type("trou") == 1, "installation de départ : trémie, 14 tapis, trou de vente")
	var tremie := -1
	for id in Game.entities:
		if Game.entities[id].type == "tremie":
			tremie = id
	# ramasser et verser dans la trémie
	var guard := 0
	while Game.grab() > 0 and guard < 50:
		guard += 1
	check(Game.hand_n + Game.hand_h == Game.hand_cap(), "la main se remplit")
	check(Game.grab() == -1, "main pleine : on ne peut plus ramasser")
	var q := Game.deposit_tremie(tremie)
	check(q > 0 and Game.hand_n + Game.hand_h == 0, "versement dans la trémie")
	var m0 := Game.money
	_run(20.0)
	check(Game.money > m0, "les aiguilles voyagent par tapis jusqu'au trou et se vendent (+%s)" % Fmt.eur(Game.money - m0))
	check(int(Game.stats.sold.get("vrac", 0)) > 0, "le trou compte les ventes")
	# un objet posé à la main sur un tapis isolé ne se vend pas
	Game.money = 1e6
	check(Game.build("convoyeur", Vector2i(-20, 10), 1), "pose d'un convoyeur isolé")
	var lone: int = Game.grid[Vector2i(-20, 10)]
	Game.hand_n = 10
	check(Game.drop_on_belt(lone) == 10, "poser une poignée sur un tapis")
	var m1 := Game.money
	_run(5.0)
	check(Game.money == m1 and Game.entities[lone].item != null, "sans trou au bout, rien ne se vend (l'objet attend en bout de tapis)")
	# droits de construction
	check(Game.can_build("fonderie") != "", "pas de fonderie sans son plan")
	check(not Game.build("scanner", Vector2i(5, -10), 1), "pas de scanner sans son plan")
	check(Game.buy_tree("p_scanner"), "achat du plan Scanner")
	check(Game.tree_state("c_moyen") == 0, "contrat tas moyen verrouillé tant qu'aucun tas n'est terminé")
	check(Game.build("scanner", Vector2i(5, -10), 1), "le scanner remplace 3 tapis de la ligne")
	check(Game.count_type("convoyeur") == 15 - 3 + 0, "les tapis recouverts ont été retirés")
	check(Game.buy_up("u_conv_vitesse") and Game.belt_speed() > 1.6, "amélioration de plan : tapis plus rapides")
	# vider le tas à la main : tout passe par le scanner, les 22 brins sortent
	guard = 0
	while Game.pile_items() > 0 and guard < 20000:
		guard += 1
		Game.grab()
		if Game.deposit_tremie(tremie) == 0:
			_run(1.0)
	_run(240.0)
	check(Game.pile_found == 22 and Game.pile_done, "les 22 brins sont retrouvés (trouvés : %d, perdus dans le trou : %d)" % [Game.pile_found, Game.stats.lost_hay])
	check(int(Game.stats.sold.get("acier", 0)) > 0, "les aiguilles ressortent vérifiées du scanner")
	check(Game.tree_state("c_moyen") == 1 and Game.buy_tree("c_moyen") and Game.order_pile("moyen"), "commande d'un tas moyen")
	check(Game.pile_items() == 2522 and Game.pile_found == 0, "le tas moyen est livré")
	# chaîne automatique : bras -> tapis -> scanner -> fonderie -> trou
	for r in ["p_bras", "p_fonderie", "p_separateur"]:
		Game.buy_tree(r)
	Game.money = 1e6
	# nouvelle ligne au nord-est : bras au bord du tas, tapis vers l'est
	var y := -16
	var bx := 5
	check(Game.build("bras", Vector2i(bx, y), 1), "bras robot posé au bord du tas")
	check(Game.digger_in_range("bras", Vector2i(bx, y)), "le bras est à portée du tas")
	for x in range(bx + 1, bx + 3):
		Game.build("convoyeur", Vector2i(x, y), 1)
	check(Game.build("scanner", Vector2i(bx + 4, y), 1), "scanner sur la ligne du bras")
	for x in range(bx + 6, bx + 8):
		Game.build("convoyeur", Vector2i(x, y), 1)
	check(Game.build("fonderie", Vector2i(bx + 9, y), 1), "fonderie au bout de la ligne")
	# sortie de la fonderie (x = bx+11) : tapis vers le sud jusqu'au trou (x 10..14, z -4..0)
	var fx := bx + 11
	for z in range(y, -2):
		Game.build("convoyeur", Vector2i(fx, z), 2)
	Game.build("convoyeur", Vector2i(fx, -2), 3)
	Game.build("convoyeur", Vector2i(fx - 1, -2), 3)
	_run(90.0)
	check(int(Game.stats.ingots) > 0, "la fonderie coule des lingots (%d)" % Game.stats.ingots)
	check(int(Game.stats.sold.get("brut", 0)) > 0, "les lingots tombent dans le trou et se vendent (%d)" % int(Game.stats.sold.get("brut", 0)))
	var base_ingots: int = Game.stats.ingots
	Game.buy_up("u_fond_lot")
	_run(60.0)
	check(int(Game.stats.ingots) - base_ingots > 0, "grand creuset : la fonderie coule plusieurs lingots par fournée")
	# contrats
	check(Game.offers.size() == 3 and Game.accept_offer(0), "acceptation d'un contrat")
	# performance de la simulation
	var t0 := Time.get_ticks_msec()
	_run(60.0)
	check(Time.get_ticks_msec() - t0 < 4000, "simulation d'une minute d'usine rapide (%d ms)" % (Time.get_ticks_msec() - t0))
	# hors ligne
	Game._update_rates(10.0)
	Game.rates = {"income": 5.0, "dig": 3.0, "hay": 0.01}
	var r2 := Game.simulate_offline(3600.0)
	check(r2.needles > 0 and r2.money > 0, "production hors ligne (%s, %d aiguilles)" % [Fmt.eur(r2.money), r2.needles])
	# sauvegardes multiples
	var money_before := Game.money
	var n_before := Game.entities.size()
	check(Game.save_slot(2), "sauvegarde dans l'emplacement 2")
	Game.new_game_in_slot(3)
	check(Game.entities.size() == 17, "nouvelle partie dans l'emplacement 3")
	check(Game.load_slot(2), "rechargement de l'emplacement 2")
	check(absf(Game.money - money_before) < 0.01 and Game.entities.size() == n_before and Game.pile_size == "moyen", "état restauré à l'identique")
	check(Game.entity_at(Vector2i(bx + 9, y)).get("type", "") == "fonderie", "les machines sont à leur place après chargement")
	_run(10.0)
	check(true, "la simulation repart après chargement")
	check(not Game.slot_info(2).is_empty() and not Game.slot_info(3).is_empty(), "infos des emplacements")
	Game.delete_slot(3)
	check(Game.slot_info(3).is_empty(), "suppression d'un emplacement")
	print("\n%s : %d échec(s)" % ["RÉSULTAT", failures])
	get_tree().quit(1 if failures > 0 else 0)


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
	await _shot("01_intro", 20)
	main.hud.close_panel()
	_face(Vector3(-3, 0, -2), Vector3(6, 0, -9), -18)
	await _shot("02_depart", 10)
	_face(Vector3(0.5, 0, -11.3), Data.PILE_POS, -25)
	for i in 4:
		main.player._grab_cd = 0
		main.player.try_grab()
		await get_tree().create_timer(0.1).timeout
	await _shot("03_ramasser", 4)
	# usine automatisée
	Game.money = 1e7
	for id in Data.TREE:
		Game.tree[id] = true
	Game.stats.piles = 4
	Game.pile_done = true
	Game.order_pile("gros")
	var y := -18
	Game.build("bras", Vector2i(6, y), 1)
	Game.build("convoyeur", Vector2i(7, y), 1)
	Game.build("scanner", Vector2i(9, y), 1)
	for x in range(11, 13):
		Game.build("convoyeur", Vector2i(x, y), 1)
	Game.build("fonderie", Vector2i(14, y), 1)
	Game.build("convoyeur", Vector2i(16, y), 1)
	Game.build("purif", Vector2i(18, y), 1)
	for z in range(y, -2):
		Game.build("convoyeur", Vector2i(20, z), 2)
	for x in [20, 19, 18, 17, 16, 15]:
		Game.build("convoyeur", Vector2i(x, -2), 3)
	Game.build("pelle", Vector2i(-9, -22), 3)
	for x in range(-11, -14, -1):
		Game.build("convoyeur", Vector2i(x, -22), 3)
	Game.build("drone", Vector2i(-4, -6), 0)
	Game.build("tampon", Vector2i(-15, -22), 3)
	for i in 900:
		Game._step(Game.STEP)
	main.player.head.position.y = 10.0
	_face(Vector3(24, 0, 6), Vector3(4, 0, -14), -26)
	await _shot("04_usine", 40)
	main.player.head.position.y = 1.62
	_face(Vector3(10, 0, -13), Vector3(11, 0.6, -18), -16)
	await _shot("05_ligne", 20)
	_face(Vector3(12, 0, 4.5), Vector3(12, 0, -2), -30)
	await _shot("06_trou", 20)
	for p in ["arbre", "boutique", "construire", "commandes", "stock", "reglages", "sauvegardes"]:
		main.hud.open_panel(p)
		await _shot("07_" + p, 6)
	main.hud.close_panel()
	main.hud.begin_build("convoyeur")
	_face(Vector3(-6, 0, 6), Vector3(-6, 0, 0), -30)
	await _shot("08_construire_tapis", 10)
	main.player.cancel_build()
	main.hud.begin_build("fonderie")
	_face(Vector3(-18, 0, 8), Vector3(-18, 0, 0), -24)
	await _shot("09_fantome_fonderie", 10)
	main.player.cancel_build()
	Game.pile_done = true
	Game.order_pile("montagne")
	main.player.head.position.y = 4.0
	_face(Vector3(0, 0, 18), Data.PILE_POS + Vector3(0, 3, 0), -6)
	await _shot("10_montagne", 30)
	get_tree().quit()


## Rendu de l'icône : un brin de foin doré posé sur un tas d'aiguilles.
func _icon() -> void:
	main.hud.visible = false
	main.hud.close_panel()
	main.player.hand.visible = false
	main.hud.layer = -1
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
	var light := OmniLight3D.new()
	light.position = straw.position + Vector3(0.4, 0.8, 1.0)
	light.light_color = Color(1, 0.9, 0.6)
	light.light_energy = 1.6
	light.omni_range = 4
	main.add_child(light)
	await _shot("icon_raw", 20)
	var img := get_viewport().get_texture().get_image()
	img.resize(512, 512, Image.INTERPOLATE_LANCZOS)
	img.save_png(ProjectSettings.globalize_path("res://icon.png"))
	var fg := img.duplicate()
	fg.resize(432, 432, Image.INTERPOLATE_LANCZOS)
	fg.save_png(ProjectSettings.globalize_path("res://icon_fg.png"))
	var bg := Image.create(432, 432, false, Image.FORMAT_RGB8)
	bg.fill(Color(0.16, 0.3, 0.1))
	bg.save_png(ProjectSettings.globalize_path("res://icon_bg.png"))
	get_tree().quit()
