class_name Buildings
## Modèles 3D procéduraux des bâtiments et leurs animations.

const C_STEEL := Color(0.55, 0.58, 0.63)
const C_DARK := Color(0.18, 0.2, 0.23)
const C_ORANGE := Color(0.95, 0.5, 0.12)
const C_YELLOW := Color(0.97, 0.75, 0.1)
const C_WOOD := Color(0.52, 0.34, 0.2)
const C_RED := Color(0.62, 0.17, 0.13)
const C_BRICK := Color(0.55, 0.27, 0.2)
const C_WHITE := Color(0.92, 0.92, 0.9)


## Crée le modèle d'un bâtiment. Les pièces animées sont rangées en métadonnées.
static func create(type: String) -> Node3D:
	var root := Node3D.new()
	var parts := {}
	match type:
		"table":
			_table(root, parts)
		"entrepot":
			_entrepot(root, parts)
		"verif":
			_verif(root, parts)
		"bras":
			_bras(root, parts)
		"pelle":
			_pelle(root, parts)
		"fonderie":
			_fonderie(root, parts)
		"purif":
			_purif(root, parts)
		"vendeur":
			_vendeur(root, parts)
		"comptoir":
			_comptoir(root, parts)
		"bureau":
			_bureau(root, parts)
	root.set_meta("parts", parts)
	var info: Dictionary = Data.BUILDINGS[type]
	var top: float = parts.get("label_y", 3.0)
	parts["label"] = Mk.label(root, info.name, Vector3(0, top, 0), 40)
	return root


## Corps de collision (bloque le joueur + visable au viseur).
static func add_body(root: Node3D, type: String, index: int) -> void:
	var body := StaticBody3D.new()
	body.collision_layer = 3
	body.set_meta("kind", "building")
	body.set_meta("index", index)
	body.set_meta("type", type)
	var cs := CollisionShape3D.new()
	var sh := BoxShape3D.new()
	var sz: Vector2 = Data.BUILDINGS[type].size
	var hgt: float = root.get_meta("parts").get("height", 2.0)
	sh.size = Vector3(sz.x, hgt, sz.y)
	cs.shape = sh
	cs.position = Vector3(0, hgt * 0.5, 0)
	body.add_child(cs)
	root.add_child(body)


static func _table(r: Node3D, p: Dictionary) -> void:
	var wood := Mk.mat(C_WOOD, 0.0, 0.85)
	Mk.box(r, Vector3(2.2, 0.08, 1.3), Vector3(0, 0.95, 0), wood)
	for x in [-1.0, 1.0]:
		for z in [-0.55, 0.55]:
			Mk.box(r, Vector3(0.08, 0.95, 0.08), Vector3(x, 0.47, z), wood)
	Mk.box(r, Vector3(1.9, 0.08, 1.0), Vector3(0, 1.02, 0), Mk.mat(Color(0.3, 0.32, 0.35), 0.4, 0.5))
	var heap := Mk.sphere(r, 0.5, Vector3(0, 1.05, 0), Mk.needle_material())
	heap.scale = Vector3(1.4, 0.25, 0.8)
	p["heap"] = heap
	var lamp := Mk.cyl(r, 0.03, 1.2, Vector3(0.9, 1.6, -0.5), Mk.mat(C_DARK))
	lamp.rotation.z = 0.2
	var light := OmniLight3D.new()
	light.position = Vector3(0.7, 2.1, -0.4)
	light.light_color = Color(1, 0.95, 0.8)
	light.light_energy = 0.6
	light.omni_range = 3.0
	r.add_child(light)
	p["height"] = 1.2
	p["label_y"] = 2.2


static func _entrepot(r: Node3D, p: Dictionary) -> void:
	var wall := Mk.mat(Color(0.62, 0.22, 0.17), 0.0, 0.9)
	var trim := Mk.mat(C_WHITE, 0.0, 0.8)
	Mk.box(r, Vector3(4.2, 3.0, 5.2), Vector3(0, 1.5, 0), wall)
	var roof := MeshInstance3D.new()
	var pm := PrismMesh.new()
	pm.size = Vector3(4.6, 1.4, 5.6)
	roof.mesh = pm
	roof.position = Vector3(0, 3.7, 0)
	roof.material_override = Mk.mat(Color(0.25, 0.25, 0.28), 0.3, 0.6)
	r.add_child(roof)
	Mk.box(r, Vector3(2.0, 2.3, 0.05), Vector3(0, 1.15, 2.62), Mk.mat(Color(0.2, 0.12, 0.08)))
	Mk.box(r, Vector3(0.12, 2.4, 0.08), Vector3(-1.05, 1.2, 2.64), trim)
	Mk.box(r, Vector3(0.12, 2.4, 0.08), Vector3(1.05, 1.2, 2.64), trim)
	Mk.box(r, Vector3(2.2, 0.12, 0.08), Vector3(0, 2.4, 2.64), trim)
	# caisses devant la porte : leur nombre montre le remplissage
	var crates: Array = []
	var cm := Mk.mat(C_WOOD.lightened(0.15), 0.0, 0.9)
	# deux piles de trois caisses, de part et d'autre de la porte
	for i in 6:
		var x := -1.6 if i % 2 == 0 else 1.6
		crates.append(Mk.box(r, Vector3(0.7, 0.7, 0.7), Vector3(x, 0.35 + (i / 2) * 0.72, 3.15), cm))
	p["crates"] = crates
	p["height"] = 3.6
	p["label_y"] = 5.0


static func _verif(r: Node3D, p: Dictionary) -> void:
	var body := Mk.mat(Color(0.2, 0.45, 0.55), 0.3, 0.5)
	Mk.box(r, Vector3(2.8, 0.8, 1.2), Vector3(0, 0.4, 0), body)
	var belt := Mk.box(r, Vector3(2.7, 0.06, 0.9), Vector3(0, 0.83, 0), Mk.mat(Color(0.08, 0.08, 0.09), 0.0, 0.9))
	p["belt"] = belt
	var post := Mk.mat(Color(0.85, 0.85, 0.88), 0.6, 0.3)
	Mk.box(r, Vector3(0.12, 1.5, 0.12), Vector3(0, 1.4, -0.6), post)
	Mk.box(r, Vector3(0.12, 1.5, 0.12), Vector3(0, 1.4, 0.6), post)
	Mk.box(r, Vector3(0.3, 0.2, 1.4), Vector3(0, 2.15, 0), post)
	var scan := Mk.box(r, Vector3(0.05, 0.9, 1.1), Vector3(0, 1.3, 0), Mk.mat(Color(0.2, 1, 0.4, 0.45), 0.0, 0.5, Color(0.2, 1, 0.4), 2.5))
	p["scan"] = scan
	var screen := Mk.box(r, Vector3(0.6, 0.4, 0.05), Vector3(0.9, 1.4, -0.55), Mk.mat(Color(0.05, 0.2, 0.1), 0.0, 0.4, Color(0.2, 0.9, 0.4), 1.2))
	p["screen"] = screen
	var bits: Array = []
	for i in 4:
		var n := Mk.box(r, Vector3(0.45, 0.04, 0.04), Vector3(-1.2 + i * 0.7, 0.88, randf_range(-0.3, 0.3)), Mk.needle_material())
		bits.append(n)
	p["bits"] = bits
	p["height"] = 2.2
	p["label_y"] = 3.0


static func _bras(r: Node3D, p: Dictionary) -> void:
	var dark := Mk.mat(C_DARK, 0.5, 0.4)
	var orange := Mk.mat(C_ORANGE, 0.3, 0.45)
	Mk.cyl(r, 0.7, 0.35, Vector3(0, 0.17, 0), dark)
	var turret := Mk.pivot(r, Vector3(0, 0.35, 0))
	Mk.cyl(turret, 0.45, 0.5, Vector3(0, 0.25, 0), orange)
	var shoulder := Mk.pivot(turret, Vector3(0, 0.6, 0))
	Mk.sphere(shoulder, 0.25, Vector3.ZERO, dark)
	var a1 := Mk.box(shoulder, Vector3(0.24, 1.7, 0.24), Vector3(0, 0.85, 0), orange)
	a1.name = "a1"
	var elbow := Mk.pivot(shoulder, Vector3(0, 1.7, 0))
	Mk.sphere(elbow, 0.2, Vector3.ZERO, dark)
	Mk.box(elbow, Vector3(0.2, 1.5, 0.2), Vector3(0, 0.75, 0), orange)
	var wrist := Mk.pivot(elbow, Vector3(0, 1.5, 0))
	Mk.box(wrist, Vector3(0.35, 0.12, 0.35), Vector3.ZERO, dark)
	Mk.box(wrist, Vector3(0.06, 0.35, 0.18), Vector3(-0.12, 0.2, 0), dark)
	Mk.box(wrist, Vector3(0.06, 0.35, 0.18), Vector3(0.12, 0.2, 0), dark)
	var load := Mk.box(wrist, Vector3(0.18, 0.1, 0.4), Vector3(0, 0.25, 0), Mk.needle_material())
	p.merge({"turret": turret, "shoulder": shoulder, "elbow": elbow, "load": load, "height": 2.0, "label_y": 4.4})


static func _pelle(r: Node3D, p: Dictionary) -> void:
	var yellow := Mk.mat(C_YELLOW, 0.2, 0.5)
	var dark := Mk.mat(Color(0.12, 0.12, 0.13), 0.2, 0.8)
	Mk.box(r, Vector3(0.6, 0.7, 3.4), Vector3(-0.95, 0.35, 0), dark)
	Mk.box(r, Vector3(0.6, 0.7, 3.4), Vector3(0.95, 0.35, 0), dark)
	var house := Mk.pivot(r, Vector3(0, 0.75, 0))
	Mk.box(house, Vector3(2.3, 0.9, 2.6), Vector3(0, 0.45, 0.3), yellow)
	Mk.box(house, Vector3(1.0, 1.2, 1.1), Vector3(-0.55, 1.5, -0.4), Mk.mat(Color(0.5, 0.75, 0.9, 0.55), 0.6, 0.1))
	Mk.box(house, Vector3(1.05, 0.08, 1.15), Vector3(-0.55, 2.12, -0.4), yellow)
	Mk.cyl(house, 0.08, 0.8, Vector3(0.7, 1.3, 1.0), dark)
	var boom := Mk.pivot(house, Vector3(0.45, 1.0, -0.9))
	Mk.box(boom, Vector3(0.35, 0.4, 3.2), Vector3(0, 0, -1.6), yellow)
	var stick := Mk.pivot(boom, Vector3(0, 0, -3.2))
	Mk.box(stick, Vector3(0.28, 2.0, 0.3), Vector3(0, -1.0, 0), yellow)
	var bucket := Mk.pivot(stick, Vector3(0, -2.0, 0))
	Mk.box(bucket, Vector3(0.9, 0.5, 0.6), Vector3(0, -0.15, -0.25), dark)
	var load := Mk.box(bucket, Vector3(0.8, 0.2, 0.5), Vector3(0, 0.08, -0.25), Mk.needle_material())
	p.merge({"house": house, "boom": boom, "stick": stick, "bucket": bucket, "load": load, "height": 2.4, "label_y": 4.6})


static func _fonderie(r: Node3D, p: Dictionary) -> void:
	var brick := Mk.mat(C_BRICK, 0.0, 0.95)
	Mk.box(r, Vector3(3.0, 2.4, 3.0), Vector3(0, 1.2, 0), brick)
	Mk.box(r, Vector3(3.2, 0.25, 3.2), Vector3(0, 2.5, 0), Mk.mat(C_DARK, 0.3, 0.6))
	Mk.cyl(r, 0.35, 2.6, Vector3(0.8, 3.8, 0.8), brick, 0.3)
	var mouth := Mk.box(r, Vector3(1.2, 0.8, 0.08), Vector3(0, 0.8, 1.51), Mk.mat(Color(1, 0.45, 0.1), 0.0, 0.5, Color(1, 0.45, 0.05), 3.0))
	p["mouth"] = mouth
	var light := OmniLight3D.new()
	light.position = Vector3(0, 1.0, 2.2)
	light.light_color = Color(1, 0.55, 0.2)
	light.light_energy = 1.5
	light.omni_range = 5.0
	r.add_child(light)
	p["light"] = light
	var smoke := CPUParticles3D.new()
	smoke.position = Vector3(0.8, 5.2, 0.8)
	smoke.amount = 24
	smoke.lifetime = 3.0
	smoke.direction = Vector3(0, 1, 0)
	smoke.spread = 12.0
	smoke.initial_velocity_min = 0.8
	smoke.initial_velocity_max = 1.4
	smoke.gravity = Vector3(0.3, 0.2, 0)
	smoke.scale_amount_min = 0.6
	smoke.scale_amount_max = 1.4
	var sm := SphereMesh.new()
	sm.radius = 0.4
	sm.height = 0.8
	sm.radial_segments = 8
	sm.rings = 4
	smoke.mesh = sm
	var smat := Mk.mat(Color(0.35, 0.35, 0.37, 0.45), 0.0, 1.0)
	smoke.material_override = smat
	r.add_child(smoke)
	p["smoke"] = smoke
	var ingot := Mk.box(r, Vector3(0.5, 0.18, 0.25), Vector3(0.9, 0.09, 1.9), Mk.mat(Color(0.6, 0.6, 0.62), 0.9, 0.35))
	p["ingot"] = ingot
	p["height"] = 2.6
	p["label_y"] = 5.6


static func _purif(r: Node3D, p: Dictionary) -> void:
	var steel := Mk.mat(Color(0.8, 0.82, 0.86), 0.9, 0.25)
	Mk.box(r, Vector3(2.8, 0.3, 2.8), Vector3(0, 0.15, 0), Mk.mat(C_DARK, 0.4, 0.6))
	var rings: Array = []
	for x in [-0.7, 0.7]:
		Mk.cyl(r, 0.6, 2.4, Vector3(x, 1.5, 0), steel)
		Mk.sphere(r, 0.6, Vector3(x, 2.7, 0), steel)
		for y in [0.9, 1.6, 2.3]:
			var ring := Mk.cyl(r, 0.63, 0.08, Vector3(x, y, 0), Mk.mat(Color(0.2, 0.6, 1), 0.0, 0.3, Color(0.2, 0.6, 1), 2.0), -1.0, 24)
			rings.append(ring)
	var pipe := Mk.cyl(r, 0.1, 1.4, Vector3(0, 2.2, 0), steel)
	pipe.rotation.z = PI / 2
	p["rings"] = rings
	p["height"] = 3.3
	p["label_y"] = 4.3


static func _vendeur(r: Node3D, p: Dictionary) -> void:
	var blue := Mk.mat(Color(0.15, 0.35, 0.75), 0.3, 0.4)
	var dark := Mk.mat(Color(0.08, 0.08, 0.09), 0.0, 0.8)
	Mk.box(r, Vector3(2.3, 1.2, 1.4), Vector3(0, 1.1, -1.7), blue)
	Mk.box(r, Vector3(2.1, 0.6, 0.06), Vector3(0, 1.4, -2.42), Mk.mat(Color(0.5, 0.75, 0.9, 0.6), 0.6, 0.1))
	Mk.box(r, Vector3(2.5, 2.2, 3.2), Vector3(0, 1.6, 0.8), Mk.mat(C_WHITE, 0.1, 0.6))
	Mk.box(r, Vector3(2.52, 0.4, 3.22), Vector3(0, 1.0, 0.8), Mk.mat(C_YELLOW, 0.1, 0.6))
	var wheels: Array = []
	for z in [-1.7, 0.2, 1.6]:
		for x in [-1.15, 1.15]:
			var w := Mk.cyl(r, 0.42, 0.3, Vector3(x, 0.42, z), dark)
			w.rotation.z = PI / 2
			wheels.append(w)
	p["wheels"] = wheels
	p["body"] = r
	p["height"] = 2.7
	p["label_y"] = 3.6


static func _comptoir(r: Node3D, p: Dictionary) -> void:
	var wood := Mk.mat(C_WOOD, 0.0, 0.85)
	Mk.box(r, Vector3(3.2, 1.1, 1.0), Vector3(0, 0.55, 0), wood)
	Mk.box(r, Vector3(3.4, 0.08, 1.2), Vector3(0, 1.12, 0), Mk.mat(C_WOOD.lightened(0.2), 0.0, 0.8))
	for x in [-1.5, 1.5]:
		Mk.box(r, Vector3(0.1, 2.6, 0.1), Vector3(x, 1.3, -0.45), wood)
	for i in 8:
		var col := C_RED if i % 2 == 0 else C_WHITE
		var s := Mk.box(r, Vector3(0.42, 0.06, 1.5), Vector3(-1.47 + i * 0.42, 2.62, 0.2), Mk.mat(col, 0.0, 0.8))
		s.rotation.x = -0.25
	var gold := Mk.mat(Color(0.95, 0.78, 0.25), 0.9, 0.3)
	for i in 3:
		Mk.box(r, Vector3(0.4, 0.15, 0.2), Vector3(-0.8 + i * 0.45, 1.24, 0), gold)
	p["height"] = 1.2
	p["label_y"] = 3.3


static func _bureau(r: Node3D, p: Dictionary) -> void:
	var wall := Mk.mat(Color(0.86, 0.82, 0.7), 0.0, 0.9)
	Mk.box(r, Vector3(2.6, 2.4, 2.6), Vector3(0, 1.2, 0), wall)
	Mk.box(r, Vector3(2.9, 0.2, 2.9), Vector3(0, 2.5, 0), Mk.mat(Color(0.3, 0.45, 0.3), 0.0, 0.8))
	Mk.box(r, Vector3(1.2, 0.8, 0.05), Vector3(0, 1.4, 1.31), Mk.mat(Color(0.5, 0.75, 0.9, 0.7), 0.6, 0.1))
	var board := Mk.box(r, Vector3(1.6, 0.9, 0.08), Vector3(0, 3.1, 0), Mk.mat(Color(0.12, 0.25, 0.15), 0.0, 0.8))
	board.rotation.y = 0
	Mk.box(r, Vector3(0.08, 0.9, 0.08), Vector3(0, 2.6, 0), Mk.mat(C_DARK))
	p["height"] = 2.5
	p["label_y"] = 4.0


## Anime un bâtiment. active = la machine a travaillé récemment.
static func animate(root: Node3D, type: String, t: float, active: bool, info: Dictionary) -> void:
	var p: Dictionary = root.get_meta("parts")
	var ph: float = root.get_meta("phase", 0.0)
	match type:
		"bras":
			var aim: float = info.get("aim", 0.0)
			var cyc := fmod(t * 0.6 + ph, 1.0) if active else 0.25
			# 0-0.4 : plonge vers le tas, 0.4-0.6 : remonte, 0.6-1 : vide sur le côté
			var toward := 1.0 - smoothstep(0.35, 0.65, cyc) + smoothstep(0.9, 1.0, cyc)
			p.turret.rotation.y = lerp_angle(aim + 1.6, aim, clampf(toward, 0.0, 1.0))
			var dip := sin(clampf(cyc / 0.4, 0.0, 1.0) * PI) if cyc < 0.4 else 0.0
			p.shoulder.rotation.x = -0.55 - dip * 0.45
			p.elbow.rotation.x = -0.9 - dip * 0.5
			p.load.visible = active and cyc > 0.3 and cyc < 0.85
		"pelle":
			var aim2: float = info.get("aim", 0.0)
			var c2 := fmod(t * 0.35 + ph, 1.0) if active else 0.3
			var toward2 := 1.0 - smoothstep(0.4, 0.6, c2) + smoothstep(0.9, 1.0, c2)
			p.house.rotation.y = lerp_angle(aim2 + PI * 0.7, aim2, clampf(toward2, 0.0, 1.0))
			var dig := sin(clampf(c2 / 0.4, 0.0, 1.0) * PI) if c2 < 0.4 else 0.0
			p.boom.rotation.x = 0.25 - dig * 0.45
			p.stick.rotation.x = -0.2 + dig * 0.5
			p.bucket.rotation.x = 0.4 - dig * 0.9
			p.load.visible = active and c2 > 0.3 and c2 < 0.8
		"verif":
			p.scan.visible = active
			p.scan.position.x = sin(t * 3.0 + ph) * 1.1
			for i in p.bits.size():
				var b: MeshInstance3D = p.bits[i]
				b.visible = active
				b.position.x = fposmod(-1.3 + i * 0.7 + t * 0.8, 2.6) - 1.3
		"fonderie":
			p.light.light_energy = (1.4 + 0.5 * sin(t * 13.0 + ph) + 0.3 * sin(t * 7.3)) if active else 0.25
			p.smoke.emitting = active
			p.ingot.visible = active
		"purif":
			for i in p.rings.size():
				var ring: MeshInstance3D = p.rings[i]
				ring.visible = (not active) or fmod(t * 2.0 + i * 0.33 + ph, 1.0) < 0.6
		"vendeur":
			for w: MeshInstance3D in p.wheels:
				w.rotation.x = t * 3.0 if active else 0.0
		"table":
			var q: float = info.get("fill", 0.0)
			p.heap.visible = q > 0.0
			p.heap.scale = Vector3(1.4, 0.05 + 0.3 * q, 0.8)
		"entrepot":
			var f: float = info.get("fill", 0.0)
			var n := int(ceil(f * p.crates.size()))
			for i in p.crates.size():
				p.crates[i].visible = i < n
