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


func _logic() -> void:
	Game.new_game()
	check(Game.pile_items() == 422, "le petit tas contient 400 aiguilles + 22 brins")
	check(Game.capacity() == 300, "un entrepôt de départ = 300 places")
	# ramasser jusqu'à remplir la main
	var guard := 0
	while Game.grab() > 0 and guard < 50:
		guard += 1
	check(Game.hand_n + Game.hand_h == Game.hand_cap(), "la main se remplit jusqu'à sa capacité")
	check(Game.grab() == -1, "main pleine : on ne peut plus ramasser")
	var q := Game.deposit_table()
	check(q > 0 and Game.hand_n + Game.hand_h == 0, "dépôt sur la table de tri")
	for i in 30:
		Game.tick(1.0)
	check(Game.verified > 0, "la table de tri vérifie les aiguilles")
	check(Game.sell("verified", 5) > 0.0, "les aiguilles vérifiées se vendent")
	check(Game.sell("raw", 5) == 0.0, "pas de vente sans stock")
	# droits de construction obligatoires
	Game.money = 100000.0
	check(not Game.can_build("fonderie"), "impossible de construire une fonderie sans le droit")
	check(Game.buy_tree("r_verif") and Game.buy_tree("r_fonderie"), "achat des droits dans l'arbre")
	check(Game.tree_state("tas_moyen") == 0, "tas moyen verrouillé tant qu'aucun tas n'est terminé")
	check(Game.place_building("verif", Vector3(12, 0, 4), 0), "construction d'un vérificateur")
	check(Game.place_building("fonderie", Vector3(-14, 0, 8), 0), "construction d'une fonderie")
	check(Game.placement_ok("table", Data.PILE_POS) != "", "on ne construit pas dans le tas")
	check(Game.buy_upgrade("main") and Game.hand_cap() == 25, "amélioration de la main")
	# vider le tas à la main et tout vérifier : les 22 brins doivent sortir
	guard = 0
	while Game.pile_items() > 0 and guard < 5000:
		guard += 1
		Game.grab()
		if Game.deposit_storage() == 0 and Game.hand_n + Game.hand_h > 0:
			Game.tick(5.0)
	for i in 400:
		Game.tick(1.0)
	check(Game.pile_found == 22 and Game.pile_done, "les 22 brins sont retrouvés (trouvés : %d)" % Game.pile_found)
	check(Game.raw > 0, "la fonderie produit des lingots")
	check(Game.tree_state("tas_moyen") == 1, "tas moyen débloquable après un tas terminé")
	check(Game.buy_tree("tas_moyen") and Game.order_pile("moyen"), "commande d'un tas moyen")
	check(Game.pile_items() == 1522 and Game.pile_found == 0, "le tas moyen est livré")
	# automatisation complète, simulation hors ligne
	for r in ["r_bras", "r_entrepot", "r_purif", "r_vendeur"]:
		Game.buy_tree(r)
	Game.money = 100000.0
	check(Game.place_building("bras", Vector3(5, 0, -16), 0), "bras robot au bord du tas")
	check(Game.digger_in_range(Game.buildings[Game.buildings.size() - 1]), "le bras robot est à portée")
	Game.place_building("entrepot", Vector3(20, 0, 10), 0)
	Game.place_building("purif", Vector3(-20, 0, -4), 0)
	Game.place_building("vendeur", Vector3(16, 0, -4), 0)
	var m0 := Game.money
	var t0 := Time.get_ticks_msec()
	var r2 := Game.simulate(3600.0)
	check(r2.needles > 1000, "une heure d'automatisation ramasse des aiguilles (%d)" % r2.needles)
	check(Game.money > m0, "le camion vend la production")
	check(Time.get_ticks_msec() - t0 < 8000, "simulation d'une heure rapide (%d ms)" % (Time.get_ticks_msec() - t0))
	# sauvegarde
	var d := Game.to_dict()
	var json := JSON.stringify(d)
	var back = JSON.parse_string(json)
	var money_before := Game.money
	var n_before := Game.buildings.size()
	Game.new_game()
	check(Game.from_dict(back), "rechargement de la sauvegarde")
	check(absf(Game.money - money_before) < 0.01 and Game.buildings.size() == n_before and Game.pile_size == "moyen", "état restauré à l'identique")
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
	await _shot("01_intro", 20)
	main.hud.close_panel()
	await _shot("02_depart", 10)
	_face(Vector3(0, 0, -11.5), Data.PILE_POS, -25)
	for i in 4:
		main.player._grab_cd = 0
		main.player.try_grab()
		await get_tree().create_timer(0.1).timeout
	await _shot("03_ramasser", 4)
	_face(Vector3(5, 0, 0.6), Vector3(5, 0.5, -2), -30)
	await _shot("04_table", 10)
	main.player.action_pressed()
	await _shot("05_depot", 4)
	for p in ["boutique", "arbre", "construire", "vente", "commandes", "stock"]:
		main.hud.open_panel(p)
		await _shot("06_" + p, 6)
	main.hud.close_panel()
	# fin de partie : une ferme automatisée
	Game.money = 1e6
	for id in Data.TREE:
		Game.tree[id] = true
	Game.stats.piles = 4
	Game.pile_done = true
	Game.order_pile("gros")
	var spots := [["bras", Vector3(7.5, 0, -14)], ["bras", Vector3(-7.5, 0, -18)], ["pelle", Vector3(3, 0, -25)],
		["pelle", Vector3(-6, 0, -9)], ["verif", Vector3(12, 0, 1)], ["verif", Vector3(12, 0, 4)],
		["fonderie", Vector3(-14, 0, 6)], ["purif", Vector3(-15, 0, -4)], ["vendeur", Vector3(15, 0, 9)],
		["entrepot", Vector3(-20, 0, 12)]]
	for s in spots:
		Game.place_building(s[0], s[1], 0)
	Game.vrac_n = 200
	for i in 20:
		Game.tick(0.25)
	main.player.head.position.y = 9.0
	_face(Vector3(20, 0, 10), Data.PILE_POS, -24)
	await _shot("07_usine", 30)
	main.player.head.position.y = 1.62
	_face(Vector3(-10, 0, 4), Vector3(-14, 1.5, -2), -6)
	await _shot("08_fonderie", 20)
	main.hud.open_panel("construire")
	await _shot("09_construire_fin", 4)
	main.hud.begin_build("bras")
	_face(Vector3(10, 0, -4), Data.PILE_POS, -12)
	await _shot("10_fantome", 10)
	main.player.cancel_build()
	Game.pile_done = true
	Game.order_pile("montagne")
	_face(Vector3(0, 0, 14), Data.PILE_POS + Vector3(0, 3, 0), 0)
	await _shot("11_montagne", 30)
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
