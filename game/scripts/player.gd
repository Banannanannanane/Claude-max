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
var build_type := "" # mode construction ("__demolir" = démolition)
var move_id := -1 # déplacement d'une machine existante
var build_rot := 0 # rotation ajoutée par le bouton Pivoter
var ghost: Node3D
var ghost_ok := false
var ghost_cell := Vector2i.ZERO
var ghost_r := 0
var ghost_why := ""
var place_held := false
var _last_placed := Vector2i(999999, 0)
var _cells: MultiMeshInstance3D
var _drop_cd := 0.0
var _grab_cd := 0.0
var _bob := 0.0
var _hand_kick := 0.0
var _exhausted := false


func _ready() -> void:
	collision_layer = 1
	collision_mask = 1
	position = Vector3(1.5, 0.1, -3.5)
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
	_cells = MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	var q := PlaneMesh.new()
	q.size = Vector2(0.94, 0.94)
	mm.mesh = q
	_cells.multimesh = mm
	var cm := StandardMaterial3D.new()
	cm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	cm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	cm.albedo_color = Color(0.3, 1, 0.4, 0.4)
	_cells.material_override = cm
	_cells.visible = false


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
	_drop_cd = maxf(0.0, _drop_cd - delta)
	ray.target_position = Vector3(0, 0, -(Game.reach() + 1.5))
	_update_target()
	if build_type != "":
		_update_ghost()
		if place_held and build_type == "convoyeur" and ghost_ok and ghost_cell != _last_placed:
			_place_now()
	elif hud and hud.action_held:
		var kind: String = target.get("kind", "")
		if kind == "pile":
			try_grab()
		elif kind == "entity" and target.type == "convoyeur" and _drop_cd <= 0.0:
			_drop_cd = 0.18
			_drop_on_belt(false)


func is_exhausted() -> bool:
	return _exhausted


## Case visée au sol (rayon du viseur jusqu'au sol), ou à défaut devant soi.
func aim_cell(max_dist: float) -> Vector2i:
	var from := cam.global_position
	var fwd := -cam.global_transform.basis.z
	if fwd.y < -0.05:
		var t := (from.y - 0.15) / -fwd.y
		if t <= max_dist:
			return Game.world_to_cell(from + fwd * t)
	var flat := Vector3(fwd.x, 0, fwd.z).normalized()
	return Game.world_to_cell(global_position + flat * minf(max_dist, 3.0))


func facing_dir() -> int:
	var f := -global_transform.basis.z
	if absf(f.x) > absf(f.z):
		return 1 if f.x > 0 else 3
	return 2 if f.z > 0 else 0


func _update_target() -> void:
	var info := {}
	if ray.is_colliding():
		var c := ray.get_collider()
		if c and c.has_meta("kind"):
			if c.get_meta("kind") == "pile":
				info = {"kind": "pile", "point": ray.get_collision_point()}
			elif c.get_meta("kind") == "entity" and Game.entities.has(c.get_meta("id")):
				var id: int = c.get_meta("id")
				info = {"kind": "entity", "id": id, "type": Game.entities[id].type}
	if info.is_empty():
		var e := Game.entity_at(aim_cell(Game.reach()))
		if not e.is_empty() and (e.type == "convoyeur" or e.type == "separateur"):
			info = {"kind": "entity", "id": e.id, "type": e.type}
	var changed_t: bool = info.get("kind", "") != target.get("kind", "") or info.get("id", -1) != target.get("id", -1)
	target = info
	if changed_t:
		target_changed.emit(target)


## Action principale (bouton) selon la cible.
func action_pressed() -> void:
	if build_type != "":
		return
	var kind: String = target.get("kind", "")
	if kind == "pile":
		try_grab()
		return
	if kind != "entity":
		if Game.hand_n + Game.hand_h > 0:
			hud.toast("Vise une trémie ou un tapis pour déposer tes aiguilles.")
		return
	var id: int = target.id
	match target.type:
		"tremie":
			var q := Game.deposit_tremie(id)
			if Game.hand_n + Game.hand_h == 0 and q == 0:
				hud.toast("Tu n'as rien en main. Va ramasser des aiguilles au tas !")
			elif q <= 0:
				hud.toast("La trémie est pleine !")
				Sfx.play("prick")
			else:
				Sfx.play("click")
				_hand_kick = 1.0
				hud.toast("%d aiguilles versées dans la trémie" % q)
		"convoyeur":
			_drop_cd = 0.25
			_drop_on_belt(true)
		"bureau":
			hud.open_panel("commandes")
		"trou":
			hud.toast("Le trou de vente : amène-y tes produits avec des convoyeurs !")
		_:
			hud.open_entity(id)


func _drop_on_belt(tell: bool) -> void:
	if Game.hand_n + Game.hand_h <= 0:
		if tell:
			hud.toast("Tu n'as rien en main.")
		return
	if Game.drop_on_belt(int(target.id)) > 0:
		_hand_kick = 1.0
		Sfx.play("click")
	elif tell:
		hud.toast("Ce tapis est occupé.")


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
		hud.toast("Main pleine ! Verse-la dans une trémie ou pose-la sur un tapis.")
		return
	if got == 0:
		return
	stamina -= GRAB_COST
	_hand_kick = 1.0
	Input.vibrate_handheld(12)
	Sfx.play("needle", randf_range(0.9, 1.15))
	if hud:
		hud.grab_fx(target.get("point", Vector3.ZERO))


# ============================================================ construction / démolition
func start_build(type: String, id := -1) -> void:
	cancel_build()
	build_type = type
	move_id = id
	build_rot = 0
	if type != "__demolir":
		ghost = Buildings.create(type)
		world.add_child(ghost)
		_ghostify(ghost)
		if id >= 0 and world.nodes.has(id):
			world.nodes[id].visible = false
	if not _cells.is_inside_tree():
		world.add_child(_cells)
	_cells.visible = true


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
	var dist := Game.reach() + 3.0
	ghost_cell = aim_cell(dist)
	var cells: Array = []
	var tint := Color(0.3, 1, 0.4)
	if build_type == "__demolir":
		var e := Game.entity_at(ghost_cell)
		ghost_ok = not e.is_empty() and not Data.MACHINES[e.type].get("fixed", false)
		ghost_why = "" if ghost_ok else ("Rien à démolir ici" if e.is_empty() else "Indestructible")
		cells = Game.footprint(e.type, e.c, e.r) if not e.is_empty() else [ghost_cell]
		tint = Color(1, 0.3, 0.25)
	else:
		ghost_r = (facing_dir() + build_rot) % 4
		ghost_why = Game.placement_ok(build_type, ghost_cell, ghost_r, move_id)
		if ghost_why == "" and move_id < 0:
			ghost_why = Game.can_build(build_type)
		ghost_ok = ghost_why == ""
		cells = Game.footprint(build_type, ghost_cell, ghost_r)
		ghost.position = Game.cell_center(ghost_cell)
		ghost.basis = world.basis_for(ghost_r)
		if not ghost_ok:
			tint = Color(1, 0.3, 0.3)
		_ghost_mat.albedo_color = Color(tint, 0.45)
		var lbl = ghost.get_meta("parts").get("label")
		if lbl:
			var extra := ""
			if build_type == "bras" or build_type == "pelle":
				extra = "\nÀ portée du tas" if Game.digger_in_range(build_type, ghost_cell) else "\nTrop loin du tas !"
			lbl.text = (Data.MACHINES[build_type].name if ghost_ok else ghost_why) + extra
			lbl.modulate = tint
	var mm := _cells.multimesh
	mm.instance_count = cells.size()
	for i in cells.size():
		mm.set_instance_transform(i, Transform3D(Basis(), Game.cell_center(cells[i]) + Vector3(0, 0.03, 0)))
	(_cells.material_override as StandardMaterial3D).albedo_color = Color(tint, 0.35 if ghost_ok else 0.25)


func rotate_build() -> void:
	build_rot = (build_rot + 1) % 4
	Sfx.play("click")


func place_pressed() -> void:
	place_held = true
	_last_placed = Vector2i(999999, 0)
	_place_now()


func place_released() -> void:
	place_held = false


func _place_now() -> bool:
	if not ghost_ok:
		if not place_held or build_type != "convoyeur":
			hud.toast(ghost_why)
			Sfx.play("prick")
		return false
	if build_type == "__demolir":
		var e := Game.entity_at(ghost_cell)
		if not e.is_empty() and Game.demolish(e.id):
			Sfx.play("cast")
		return true
	if move_id >= 0:
		if Game.move_entity(move_id, ghost_cell, ghost_r):
			Sfx.play("buy")
			cancel_build()
			return true
		return false
	if Game.build(build_type, ghost_cell, ghost_r):
		_last_placed = ghost_cell
		if build_type != "convoyeur":
			hud.toast("%s construit !" % Data.MACHINES[build_type].name, true)
		return true
	return false


func cancel_build() -> void:
	if move_id >= 0 and world.nodes.has(move_id):
		world.nodes[move_id].visible = true
	build_type = ""
	move_id = -1
	place_held = false
	if ghost:
		ghost.queue_free()
		ghost = null
	if _cells:
		_cells.visible = false
	target = {}
	target_changed.emit(target)
