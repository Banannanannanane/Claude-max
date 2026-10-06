extends Node3D
## Assemble le monde, le joueur et l'interface ; gère les effets et le rattrapage hors ligne.

var world: Node3D
var player: CharacterBody3D
var hud: CanvasLayer
var _spark_mat: StandardMaterial3D
var _hay_mat: StandardMaterial3D
var title: CanvasLayer
var _away_at_start := 0.0


func _ready() -> void:
	world = Node3D.new()
	world.set_script(load("res://scripts/world.gd"))
	add_child(world)
	player = CharacterBody3D.new()
	player.set_script(load("res://scripts/player.gd"))
	add_child(player)
	hud = CanvasLayer.new()
	hud.set_script(load("res://scripts/hud.gd"))
	hud.player = player
	hud.main = self
	player.hud = hud
	player.world = world
	add_child(hud)
	_spark_mat = Mk.mat(Color(0.6, 0.6, 0.62), 0.4, 0.4)
	_hay_mat = Mk.mat(Color(1, 0.85, 0.3), 0.0, 0.5, Color(1, 0.8, 0.2), 2.0)
	apply_quality()
	# regarde vers le tas au démarrage
	player.look_at_from_position(player.position, Vector3(Data.PILE_POS.x, player.position.y, Data.PILE_POS.z))
	# absence mesurée tout de suite : la sauvegarde automatique tourne pendant l'écran titre
	if Game.last_save > 0:
		_away_at_start = Time.get_unix_time_from_system() - float(Game.last_save)
	var qa_run := false
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--qa="):
			qa_run = true
			var qa := Node.new()
			var qa_script = load("res://scripts/qa.gd")
			if qa_script == null or not qa_script.can_instantiate():
				printerr("Pilote de tests illisible")
				get_tree().quit(2)
				return
			qa.set_script(qa_script)
			qa.set("main", self)
			add_child(qa)
	if qa_run:
		_after_title()
		return
	title = CanvasLayer.new()
	title.set_script(load("res://scripts/title.gd"))
	title.set("main", self)
	title.started.connect(_after_title)
	add_child(title)


## Après l'écran titre : message d'accueil ou bilan de l'absence.
func _after_title() -> void:
	if Game.last_save > 0:
		_catch_up.call_deferred(_away_at_start)
	else:
		_intro.call_deferred()


func title_open() -> bool:
	return title != null and is_instance_valid(title) and not title.is_closing()


## Qualité graphique : 0 = Bas (téléphones modestes), 1 = Moyen, 2 = Élevé.
func apply_quality() -> void:
	var q := clampi(int(Game.settings.get("quality", 1)), 0, 2)
	Engine.max_fps = 60
	var vp := get_viewport()
	vp.scaling_3d_scale = [0.7, 0.85, 1.0][q]
	vp.msaa_3d = Viewport.MSAA_DISABLED if q == 0 else Viewport.MSAA_2X
	RenderingServer.directional_shadow_atlas_set_size([1024, 1024, 2048][q], true)
	world.sun.shadow_enabled = q > 0
	world.sun.directional_shadow_max_distance = [30.0, 40.0, 55.0][q]
	world.env.glow_enabled = q > 0
	world.pile.rebuild()


func _intro() -> void:
	hud.message("Trouve le Foin", "\n".join([
		"Tout le monde cherche l'aiguille dans la botte de foin… Ici, c'est l'inverse : ce tas d'aiguilles cache 22 brins de foin. À toi de les trouver !",
		"",
		"1. Vise le tas et garde ACTION appuyé pour ramasser des aiguilles.",
		"2. Verse-les dans la trémie : le tapis les emmène jusqu'au trou de vente.",
		"3. Le trou est le seul endroit où l'on vend. Ce qui y tombe est payé… mais le foin non détecté retourne dans le tas !",
		"4. Achète le plan du Scanner dans l'Arbre et place-le sur le tapis pour détecter le foin.",
		"5. Ensuite : fonderie, purificateur, presses, bras robots, pelleteuses, drones… automatise tout !",
	]))


func _catch_up(away := -1.0) -> void:
	if away < 0.0:
		away = Time.get_unix_time_from_system() - float(Game.last_save)
	if away < 30.0:
		return
	var r := Game.simulate_offline(away)
	Game.save_slot(Game.slot)
	if r.seconds >= 60.0 and (r.needles > 0 or r.money > 0.5):
		hud.message("Pendant ton absence…", "\n".join([
			"Ton usine a tourné pendant %s." % Fmt.duration(r.seconds),
			"",
			"Aiguilles ramassées : %s" % Fmt.num(r.needles),
			"Brins de foin trouvés : %d" % r.hay,
			"Argent gagné : %s" % Fmt.eur(r.money),
		]))


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_GO_BACK_REQUEST:
		if title_open():
			get_tree().quit()
		else:
			hud.go_back()
		return
	if what == NOTIFICATION_APPLICATION_RESUMED or what == NOTIFICATION_APPLICATION_FOCUS_IN:
		if Game.last_save > 0 and is_inside_tree() and not title_open():
			_catch_up()


func _burst(point: Vector3, material: Material, amount: int, size: float, speed: float, life: float) -> void:
	var p := CPUParticles3D.new()
	p.one_shot = true
	p.explosiveness = 0.95
	p.amount = amount
	p.lifetime = life
	p.direction = Vector3(0, 1, 0)
	p.spread = 70.0
	p.initial_velocity_min = speed * 0.5
	p.initial_velocity_max = speed
	p.gravity = Vector3(0, -6, 0)
	p.scale_amount_min = 0.6
	p.scale_amount_max = 1.2
	var m := BoxMesh.new()
	m.size = Vector3(size * 0.15, size * 0.15, size)
	p.mesh = m
	p.material_override = material
	p.position = point
	add_child(p)
	p.emitting = true
	get_tree().create_timer(life + 0.2).timeout.connect(p.queue_free)


## Ramassage : quelques aiguilles jaillissent du tas vers le joueur.
func spark_fx(point: Vector3) -> void:
	if point == Vector3.ZERO:
		return
	var p := CPUParticles3D.new()
	p.one_shot = true
	p.explosiveness = 0.9
	p.amount = 9
	p.lifetime = 0.45
	var to_cam: Vector3 = player.cam.global_position - point
	p.direction = (to_cam.normalized() + Vector3(0, 0.6, 0)).normalized()
	p.spread = 28.0
	p.initial_velocity_min = 2.5
	p.initial_velocity_max = 4.5
	p.gravity = Vector3(0, -7, 0)
	p.particle_flag_align_y = true
	var m := BoxMesh.new()
	m.size = Vector3(0.008, 0.22, 0.008)
	p.mesh = m
	p.material_override = _spark_mat
	p.position = point
	add_child(p)
	p.emitting = true
	get_tree().create_timer(0.7).timeout.connect(p.queue_free)


func hay_fx() -> void:
	var cam: Camera3D = player.cam
	var p := cam.global_position - cam.global_transform.basis.z * 1.2
	_burst(p, _hay_mat, 28, 0.3, 2.0, 1.0)
