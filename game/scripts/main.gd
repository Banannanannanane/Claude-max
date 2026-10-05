extends Node3D
## Assemble le monde, le joueur et l'interface ; gère les effets et le rattrapage hors ligne.

var world: Node3D
var player: CharacterBody3D
var hud: CanvasLayer
var _spark_mat: StandardMaterial3D
var _hay_mat: StandardMaterial3D


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
	_spark_mat = Mk.mat(Color(0.9, 0.92, 0.96), 0.9, 0.2, Color(0.8, 0.85, 1.0), 0.6)
	_hay_mat = Mk.mat(Color(1, 0.85, 0.3), 0.0, 0.5, Color(1, 0.8, 0.2), 2.0)
	# regarde vers le tas au démarrage
	player.look_at_from_position(player.position, Vector3(Data.PILE_POS.x, player.position.y, Data.PILE_POS.z))
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--qa="):
			var qa := Node.new()
			qa.set_script(load("res://scripts/qa.gd"))
			qa.set("main", self)
			add_child(qa)
	if Game.last_save > 0:
		_catch_up.call_deferred()
	else:
		_intro.call_deferred()


func _intro() -> void:
	hud.message("Trouve le Foin", "\n".join([
		"Tout le monde cherche l'aiguille dans la botte de foin…",
		"Ici, c'est l'inverse : un tas d'aiguilles cache 22 brins de foin. À toi de les trouver !",
		"",
		"Vise le tas et garde ACTION appuyé pour ramasser une poignée d'aiguilles, puis dépose-la sur la table de tri : elle repère le foin caché.",
		"Vends ta production au comptoir, achète des améliorations à la boutique et des droits de construction dans l'arbre, puis automatise tout : vérificateurs, bras robots, pelleteuses, fonderies, purificateurs…",
	]))


func _catch_up() -> void:
	var away := Time.get_unix_time_from_system() - float(Game.last_save)
	if away < 10.0:
		return
	var r := Game.simulate(away)
	Game.save_game()
	if r.seconds >= 60.0 and (r.needles > 0 or r.money > 0.5 or r.ingots > 0):
		hud.message("Pendant ton absence…", "\n".join([
			"Tes machines ont tourné pendant %s." % Fmt.duration(r.seconds),
			"",
			"Aiguilles ramassées : %s" % Fmt.num(r.needles),
			"Brins de foin trouvés : %d" % r.hay,
			"Lingots coulés : %s" % Fmt.num(r.ingots),
			"Argent gagné : %s" % Fmt.eur(r.money),
		]))


func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_RESUMED or what == NOTIFICATION_APPLICATION_FOCUS_IN:
		if Game.last_save > 0 and is_inside_tree():
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


func spark_fx(point: Vector3) -> void:
	if point != Vector3.ZERO:
		_burst(point, _spark_mat, 10, 0.25, 2.5, 0.6)


func hay_fx() -> void:
	var cam: Camera3D = player.cam
	var p := cam.global_position - cam.global_transform.basis.z * 1.2
	_burst(p, _hay_mat, 28, 0.3, 2.0, 1.0)
