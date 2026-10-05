extends CharacterBody3D
## Joueur à la première personne : marche, course, regard, visée et actions.

signal target_changed(info: Dictionary)

const GRAVITY := 18.0
const JUMP := 6.0
const SPRINT_MULT := 1.7
const SPRINT_COST := 20.0
const GRAB_COST := 5.0
const GRAB_DELAY := 0.38

var hud: Node # fourni par main.gd
var world: Node3D
var head: Node3D
var cam: Camera3D
var ray: RayCast3D
var hand: MultiMeshInstance3D
var stamina := 100.0
var target := {}
var build_type := "" # mode construction
var move_index := -1 # déplacement d'un bâtiment existant
var build_rot := 0
var ghost: Node3D
var ghost_ok := false
var ghost_pos := Vector3.ZERO
var _grab_cd := 0.0
var _bob := 0.0
var _hand_kick := 0.0
var _exhausted := false


func _ready() -> void:
	collision_layer = 1
	collision_mask = 1
	position = Vector3(0, 0.1, 9)
	var cs := CollisionShape3D.new()
	var cap := CapsuleShape3D.new()
	cap.radius = 0.35
	cap.height = 1.75
	cs.shape = cap
	cs.position = Vector3(0, 0.875, 0)
	add_child(cs)
	head = Node3D.new()
	head.position = Vector3(0, 1.62, 0)
	add_child(head)
	cam = Camera3D.new()
	cam.fov = 72.0
	cam.near = 0.05
	cam.far = 260.0
	cam.current = true
	head.add_child(cam)
	ray = RayCast3D.new()
	ray.collision_mask = 2
	ray.target_position = Vector3(0, 0, -Game.reach())
	cam.add_child(ray)
	_make_hand()
	stamina = Game.stamina_max()


func _make_hand() -> void:
	hand = MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.mesh = Mk.needle_mesh(0.42, 0.006)
	mm.instance_count = 30
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	for i in 30:
		var b := Basis(Vector3.FORWARD, PI / 2 + rng.randf_range(-0.12, 0.12)) * Basis(Vector3.RIGHT, rng.randf_range(-0.15, 0.15))
		mm.set_instance_transform(i, Transform3D(b, Vector3(rng.randf_range(-0.04, 0.04), rng.randf_range(-0.03, 0.03), rng.randf_range(-0.05, 0.05))))
	hand.multimesh = mm
	hand.material_override = Mk.needle_material()
	hand.position = Vector3(0.3, -0.27, -0.55)
	hand.rotation = Vector3(0.35, 0.55, 0.25)
	cam.add_child(hand)


func look(delta: Vector2) -> void:
	var s: float = 0.0042 * float(Game.settings.get("sens", 1.0))
	rotation.y -= delta.x * s
	head.rotation.x = clampf(head.rotation.x - delta.y * s, deg_to_rad(-85), deg_to_rad(85))


func _physics_process(delta: float) -> void:
	var input: Vector2 = hud.move_vector() if hud else Vector2.ZERO
	var kb := Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down")
	if Input.is_physical_key_pressed(KEY_A):
		kb.x -= 1
	if Input.is_physical_key_pressed(KEY_D):
		kb.x += 1
	if Input.is_physical_key_pressed(KEY_W):
		kb.y -= 1
	if Input.is_physical_key_pressed(KEY_S):
		kb.y += 1
	if kb.length() > 0.1:
		input = kb.limit_length(1.0)
	var sprinting: bool = (hud and hud.sprint_held) or Input.is_physical_key_pressed(KEY_SHIFT)
	sprinting = sprinting and input.length() > 0.2 and not _exhausted and stamina > 0.0
	var speed := Game.walk_speed() * (SPRINT_MULT if sprinting else 1.0)
	var dir := (transform.basis * Vector3(input.x, 0, input.y))
	dir.y = 0
	var horiz := dir * speed
	velocity.x = horiz.x
	velocity.z = horiz.z
	if not is_on_floor():
		velocity.y -= GRAVITY * delta
	elif (hud and hud.consume_jump()) or Input.is_physical_key_pressed(KEY_SPACE):
		velocity.y = JUMP
	move_and_slide()

	# endurance
	var smax := Game.stamina_max()
	if sprinting:
		stamina -= SPRINT_COST * delta
	else:
		stamina += Game.stamina_regen() * delta
	stamina = clampf(stamina, 0.0, smax)
	if stamina <= 0.0:
		_exhausted = true
	elif stamina > smax * 0.25:
		_exhausted = false

	# balancement de la tête et de la main
	var moving := input.length() > 0.1 and is_on_floor()
	_bob += delta * (12.0 if sprinting else 8.0) * (1.0 if moving else 0.0)
	cam.position.y = sin(_bob) * 0.035 if moving else lerpf(cam.position.y, 0.0, delta * 8.0)
	_hand_kick = maxf(0.0, _hand_kick - delta * 4.0)
	hand.position = Vector3(0.3 + cos(_bob * 0.5) * 0.01, -0.27 + sin(_bob) * 0.012 - _hand_kick * 0.08, -0.55 + _hand_kick * 0.12)
	var fill := float(Game.hand_n + Game.hand_h) / float(Game.hand_cap())
	hand.multimesh.visible_instance_count = int(ceil(fill * 30.0))

	_grab_cd = maxf(0.0, _grab_cd - delta)
	ray.target_position = Vector3(0, 0, -Game.reach())
	if build_type != "":
		_update_ghost()
	else:
		_update_target()
		if hud and hud.action_held and target.get("kind", "") == "pile":
			try_grab()


func is_exhausted() -> bool:
	return _exhausted


func _update_target() -> void:
	var info := {}
	if ray.is_colliding():
		var c := ray.get_collider()
		if c and c.has_meta("kind"):
			info["kind"] = c.get_meta("kind")
			info["point"] = ray.get_collision_point()
			if info.kind == "building":
				info["index"] = c.get_meta("index")
				info["type"] = c.get_meta("type")
	if info.get("kind", "") != target.get("kind", "") or info.get("index", -1) != target.get("index", -1):
		target = info
		target_changed.emit(target)
	else:
		target = info


## Action principale (bouton) selon la cible.
func action_pressed() -> void:
	if build_type != "":
		return
	var kind: String = target.get("kind", "")
	if kind == "pile":
		try_grab()
		return
	if kind != "building":
		if Game.hand_n + Game.hand_h > 0:
			hud.toast("Vise la table de tri ou l'entrepôt pour déposer tes aiguilles.")
		return
	var type: String = target.type
	match type:
		"table":
			_deposit(Game.deposit_table(), "sur la table de tri", "La table de tri est pleine !")
		"entrepot":
			_deposit(Game.deposit_storage(), "dans l'entrepôt", "L'entrepôt est plein ! Vends, fonds ou agrandis.")
		"comptoir":
			hud.open_panel("vente")
		"bureau":
			hud.open_panel("commandes")
		_:
			hud.open_building(int(target.index))


func _deposit(q: int, where: String, full_msg: String) -> void:
	if Game.hand_n + Game.hand_h == 0 and q == 0:
		hud.toast("Tu n'as rien en main. Va ramasser des aiguilles au tas !")
		return
	if q <= 0:
		hud.toast(full_msg)
		Sfx.play("prick")
		return
	Sfx.play("click")
	_hand_kick = 1.0
	hud.toast("%d aiguilles déposées %s" % [q, where])


func try_grab() -> void:
	if _grab_cd > 0.0:
		return
	if stamina < GRAB_COST:
		_grab_cd = 0.5
		hud.toast("Tu es épuisé… reprends ton souffle.")
		return
	var got := Game.grab()
	_grab_cd = GRAB_DELAY
	if got < 0:
		_grab_cd = 1.0
		hud.toast("Main pleine ! Dépose les aiguilles à la table de tri ou à l'entrepôt.")
		return
	if got == 0:
		return
	stamina -= GRAB_COST
	_hand_kick = 1.0
	Input.vibrate_handheld(12)
	Sfx.play("needle", randf_range(0.9, 1.15))
	if hud:
		hud.grab_fx(target.get("point", Vector3.ZERO))


# ============================================================ mode construction
func start_build(type: String, index := -1) -> void:
	build_type = type
	move_index = index
	build_rot = 0 if index < 0 else int(Game.buildings[index].rot)
	if ghost:
		ghost.queue_free()
	ghost = Buildings.create(type)
	world.add_child(ghost)
	_ghostify(ghost)
	if index >= 0 and index < world.building_nodes.size():
		world.building_nodes[index].visible = false


var _ghost_mat: StandardMaterial3D


func _ghostify(n: Node) -> void:
	if not _ghost_mat:
		_ghost_mat = StandardMaterial3D.new()
		_ghost_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		_ghost_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		_ghost_mat.albedo_color = Color(0.3, 1, 0.4, 0.45)
	for c in n.get_children():
		if c is MeshInstance3D:
			(c as MeshInstance3D).material_override = _ghost_mat
			(c as MeshInstance3D).cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		if c is Light3D or c is CPUParticles3D:
			c.visible = false
		_ghostify(c)


func _update_ghost() -> void:
	var sz: Vector2 = Data.BUILDINGS[build_type].size
	var fwd := -global_transform.basis.z
	var dist := Game.reach() + maxf(sz.x, sz.y) * 0.5 + 1.0
	var p := global_position + fwd * dist
	p.y = 0
	p.x = snappedf(p.x, 0.5)
	p.z = snappedf(p.z, 0.5)
	ghost_pos = p
	ghost.position = p
	ghost.rotation.y = build_rot * PI / 2.0
	var why := Game.placement_ok(build_type, p, move_index)
	ghost_ok = why == ""
	var tint := Color(0.3, 1, 0.4) if ghost_ok else Color(1, 0.3, 0.3)
	_ghost_mat.albedo_color = Color(tint, 0.45)
	var lbl: Label3D = ghost.get_meta("parts").label
	var extra := ""
	if build_type == "bras" or build_type == "pelle":
		var inr := Game.digger_in_range({"type": build_type, "x": p.x, "z": p.z})
		extra = "\nÀ portée du tas" if inr else "\nTrop loin du tas !"
	lbl.text = (Data.BUILDINGS[build_type].name if ghost_ok else why) + extra
	lbl.modulate = tint


func rotate_build() -> void:
	build_rot = (build_rot + 1) % 4
	Sfx.play("click")


func confirm_build() -> bool:
	if not ghost_ok:
		Sfx.play("prick")
		return false
	if move_index >= 0:
		Game.move_building(move_index, ghost_pos, build_rot)
	elif not Game.place_building(build_type, ghost_pos, build_rot):
		hud.toast("Pas assez d'argent.")
		return false
	cancel_build()
	return true


func cancel_build() -> void:
	if move_index >= 0 and move_index < world.building_nodes.size():
		world.building_nodes[move_index].visible = true
	build_type = ""
	move_index = -1
	if ghost:
		ghost.queue_free()
		ghost = null
	target = {}
	target_changed.emit(target)
