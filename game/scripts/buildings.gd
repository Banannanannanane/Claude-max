class_name Buildings
## Modèles 3D procéduraux des machines. Convention : origine = centre de l'emprise au sol,
## sortie à l'avant (-Z local), entrée à l'arrière (+Z local). Pièces animées dans la méta "parts".

const C_ORANGE := Color(0.93, 0.47, 0.1)
const C_YELLOW := Color(0.96, 0.74, 0.12)
const C_TEAL := Color(0.16, 0.5, 0.56)
const C_BLUE := Color(0.18, 0.33, 0.6)
const C_RED := Color(0.66, 0.16, 0.16)
const C_GREEN := Color(0.22, 0.48, 0.28)
const C_CREAM := Color(0.88, 0.85, 0.76)
const C_WOOD := Color(0.55, 0.37, 0.22)

const LIGHT_ON := Color(0.25, 1.0, 0.35)
const LIGHT_IDLE := Color(1.0, 0.7, 0.15)
const LIGHT_BAD := Color(1.0, 0.2, 0.15)


static func create(type: String) -> Node3D:
	var root := Node3D.new()
	var p := {}
	var m: Dictionary = Data.MACHINES[type]
	var sz: Vector2i = m.size
	match type:
		"convoyeur":
			_belt_ghost(root, p)
		"separateur":
			_separateur(root, p)
		"tremie":
			_tremie(root, p)
		"scanner":
			_scanner(root, p)
		"bras":
			_bras(root, p)
		"pelle":
			_pelle(root, p)
		"fonderie":
			_fonderie(root, p)
		"purif":
			_purif(root, p)
		"presse":
			_presse(root, p)
		"trefileuse":
			_trefileuse(root, p)
		"aiguilleuse":
			_aiguilleuse(root, p)
		"tampon":
			_tampon(root, p)
		"drone":
			_drone_pad(root, p)
		"trou":
			_trou(root, p)
		"bureau":
			_bureau(root, p)
		"radar":
			_radar(root, p)
		"groupe":
			_groupe(root, p)
		"eolienne":
			_eolienne(root, p)
		"solaire":
			_solaire(root, p)
	if m.has("in") or type in ["tremie", "tampon", "bras", "pelle"]:
		_ports(root, type, sz)
	if not p.has("status") and type not in ["convoyeur", "separateur", "trou", "bureau"]:
		p["status"] = _status_light(root, Vector3(sz.x * 0.5 - 0.15, float(m.h) + 0.05, sz.y * 0.5 - 0.15))
	if type != "convoyeur" and type != "separateur" and type != "trou":
		var lbl := Mk.label(root, m.name, Vector3(0, float(m.h) + 0.6, 0), 40)
		lbl.visibility_range_end = 12.0
		p["label"] = lbl
	root.set_meta("parts", p)
	return root


## Corps de collision : bloque le joueur et sert à viser la machine.
static func add_body(root: Node3D, type: String, id: int) -> void:
	var body := StaticBody3D.new()
	body.collision_layer = 3
	body.set_meta("kind", "entity")
	body.set_meta("id", id)
	var cs := CollisionShape3D.new()
	var sh := BoxShape3D.new()
	var sz: Vector2i = Data.MACHINES[type].size
	var hgt: float = maxf(0.45, float(Data.MACHINES[type].h))
	sh.size = Vector3(sz.x * 0.92, hgt, sz.y * 0.92)
	cs.shape = sh
	cs.position = Vector3(0, hgt * 0.5, 0)
	body.add_child(cs)
	root.add_child(body)


# ============================================================ pièces communes
static func _arrow(parent: Node3D, pos: Vector3, color: Color, size := 0.42) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var pm := PrismMesh.new()
	pm.size = Vector3(size, size, 0.02)
	mi.mesh = pm
	mi.position = pos
	mi.rotation = Vector3(-PI / 2, 0, 0)
	mi.material_override = Mk.glow(color, 0.8)
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	parent.add_child(mi)
	return mi


## Flèches au sol : vertes à l'arrière (entrée), bleues devant (sortie).
static func _ports(r: Node3D, type: String, sz: Vector2i) -> void:
	for i in sz.x:
		var x := -sz.x * 0.5 + 0.5 + i
		_arrow(r, Vector3(x, 0.03, -sz.y * 0.5 - 0.5), Color(0.3, 0.6, 1.0))
		if Data.MACHINES[type].has("in") or type == "tampon":
			_arrow(r, Vector3(x, 0.03, sz.y * 0.5 + 0.5), Color(0.3, 1.0, 0.45))


static func _status_light(r: Node3D, pos: Vector3) -> MeshInstance3D:
	Mk.cyl(r, 0.025, 0.25, pos + Vector3(0, 0.12, 0), Mk.dark_steel(), -1.0, 6)
	Mk.cyl(r, 0.07, 0.05, pos + Vector3(0, 0.26, 0), Mk.dark_steel(), -1.0, 10)
	var lamp := Mk.sphere(r, 0.075, pos + Vector3(0, 0.33, 0), Mk.glow(LIGHT_IDLE, 3.0))
	return lamp


## Socle métallique à bord rayé jaune et noir.
static func _plinth(r: Node3D, w: float, d: float, h := 0.14) -> void:
	Mk.box(r, Vector3(w - 0.06, h, d - 0.06), Vector3(0, h * 0.5, 0), Mk.dark_steel())
	var t := 0.06
	Mk.box(r, Vector3(w - 0.04, 0.04, t), Vector3(0, h + 0.01, -(d * 0.5) + 0.05), Mk.hazard())
	Mk.box(r, Vector3(w - 0.04, 0.04, t), Vector3(0, h + 0.01, d * 0.5 - 0.05), Mk.hazard())
	Mk.box(r, Vector3(t, 0.04, d - 0.04), Vector3(-(w * 0.5) + 0.05, h + 0.01, 0), Mk.hazard())
	Mk.box(r, Vector3(t, 0.04, d - 0.04), Vector3(w * 0.5 - 0.05, h + 0.01, 0), Mk.hazard())


## Boîte peinte avec un liseré plus sombre en haut et en bas.
static func _cabinet(r: Node3D, size: Vector3, pos: Vector3, color: Color) -> MeshInstance3D:
	var b := Mk.box(r, size, pos + Vector3(0, size.y * 0.5, 0), Mk.paint(color))
	var trim := Mk.paint(color.darkened(0.45), 0.4, 0.5)
	Mk.box(r, Vector3(size.x + 0.03, 0.05, size.z + 0.03), pos + Vector3(0, 0.025, 0), trim)
	Mk.box(r, Vector3(size.x + 0.03, 0.05, size.z + 0.03), pos + Vector3(0, size.y - 0.025, 0), trim)
	return b


static func _bolts(r: Node3D, center: Vector3, half: Vector2, y: float) -> void:
	var m := Mk.steel()
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			Mk.cyl(r, 0.03, 0.03, center + Vector3(sx * half.x, y, sz * half.y), m, -1.0, 6)


## Goulotte d'entrée à l'arrière ou de sortie à l'avant.
static func _chute(r: Node3D, z: float, y: float, w: float, out: bool) -> void:
	var c := Mk.box(r, Vector3(w, 0.06, 0.45), Vector3(0, y, z), Mk.steel())
	c.rotation.x = 0.35 if out else -0.35
	for sx in [-0.5, 0.5]:
		var side := Mk.box(r, Vector3(0.04, 0.16, 0.45), Vector3(w * sx, y + 0.06, z), Mk.steel())
		side.rotation.x = c.rotation.x


static func _screen(r: Node3D, pos: Vector3, size: Vector2, text: String, rot_y := 0.0) -> Label3D:
	var frame := Mk.box(r, Vector3(size.x + 0.06, size.y + 0.06, 0.05), pos, Mk.dark_steel())
	frame.rotation.y = rot_y
	var scr := Mk.box(r, Vector3(size.x, size.y, 0.02), pos + Vector3(0, 0, -0.03).rotated(Vector3.UP, rot_y), Mk.glow(Color(0.1, 0.35, 0.25), 1.2))
	scr.rotation.y = rot_y
	var l := Label3D.new()
	l.text = text
	l.font_size = 28
	l.pixel_size = 0.004
	l.modulate = Color(0.6, 1, 0.7)
	l.position = pos + Vector3(0, 0, -0.05).rotated(Vector3.UP, rot_y)
	l.rotation.y = rot_y + PI
	l.outline_size = 0
	r.add_child(l)
	return l


static func _belt_ghost(r: Node3D, p: Dictionary) -> void:
	Mk.box(r, Vector3(0.98, 0.2, 1.0), Vector3(0, 0.1, 0), Mk.dark_steel())
	_arrow(r, Vector3(0, 0.24, 0), Color(0.3, 1, 0.4), 0.5)


# ============================================================ machines
static func _separateur(r: Node3D, p: Dictionary) -> void:
	Mk.box(r, Vector3(0.98, 0.22, 0.98), Vector3(0, 0.11, 0), Mk.paint(C_BLUE, 0.3, 0.5))
	Mk.box(r, Vector3(0.8, 0.02, 0.8), Vector3(0, 0.225, 0), Mk.rubber())
	for a in [0.0, PI / 2, -PI / 2]:
		var ar := _arrow(r, Vector3(0, 0.24, 0) + Vector3(0, 0, -0.28).rotated(Vector3.UP, a), Color(1, 0.85, 0.3), 0.22)
		ar.rotation.y = a
	var hub := Mk.pivot(r, Vector3(0, 0.24, 0))
	Mk.cyl(hub, 0.08, 0.1, Vector3(0, 0.05, 0), Mk.steel())
	Mk.box(hub, Vector3(0.62, 0.08, 0.04), Vector3(0, 0.06, 0), Mk.paint(C_YELLOW))
	p["spin"] = hub
	p["h"] = 0.3


static func _tremie(r: Node3D, p: Dictionary) -> void:
	var steel := Mk.steel()
	for sx in [-0.78, 0.78]:
		for sz in [-0.78, 0.78]:
			Mk.box(r, Vector3(0.1, 1.15, 0.1), Vector3(sx, 0.575, sz), Mk.paint(C_YELLOW, 0.3))
	Mk.bar(r, Vector3(-0.78, 0.45, 0.78), Vector3(0.78, 0.45, 0.78), 0.03, steel)
	Mk.bar(r, Vector3(-0.78, 0.45, -0.78), Vector3(-0.78, 0.45, 0.78), 0.03, steel)
	Mk.bar(r, Vector3(0.78, 0.45, -0.78), Vector3(0.78, 0.45, 0.78), 0.03, steel)
	var funnel := Mk.cyl(r, 1.18, 0.85, Vector3(0, 1.45, 0), Mk.paint(Color(0.55, 0.58, 0.62), 0.6, 0.4), -1.0, 4)
	(funnel.mesh as CylinderMesh).top_radius = 1.25
	(funnel.mesh as CylinderMesh).bottom_radius = 0.3
	funnel.rotation.y = PI / 4
	for i in 4:
		var rim := Mk.box(r, Vector3(1.8, 0.07, 0.07), Vector3(0, 1.88, -0.88).rotated(Vector3.UP, i * PI / 2), Mk.paint(C_YELLOW, 0.3))
		rim.rotation.y = i * PI / 2
	var heap := Mk.sphere(r, 0.8, Vector3(0, 1.72, 0), Mk.needle_material())
	heap.scale = Vector3(1.0, 0.22, 1.0)
	p["heap"] = heap
	Mk.cyl(r, 0.22, 0.3, Vector3(0, 0.9, 0), steel)
	_chute(r, -0.7, 0.42, 0.42, true)
	_cabinet(r, Vector3(0.42, 0.35, 0.32), Vector3(0.62, 0.0, -0.35), C_BLUE)
	var fan := Mk.cyl(r, 0.11, 0.03, Vector3(0.62, 0.36, -0.52), Mk.dark_steel(), -1.0, 12)
	fan.rotation.x = PI / 2
	# échelle
	for sx in [0.86, 1.02]:
		Mk.box(r, Vector3(0.03, 1.9, 0.03), Vector3(sx, 0.95, 0.45), steel)
	for i in 6:
		Mk.box(r, Vector3(0.18, 0.025, 0.025), Vector3(0.94, 0.25 + i * 0.3, 0.45), steel)
	p["h"] = 1.9


static func _scanner(r: Node3D, p: Dictionary) -> void:
	_cabinet(r, Vector3(0.96, 0.3, 1.94), Vector3.ZERO, C_TEAL)
	Mk.box(r, Vector3(0.7, 0.02, 1.94), Vector3(0, 0.31, 0), Mk.rubber())
	for z in [-0.85, 0.85]:
		var roll := Mk.cyl(r, 0.05, 0.74, Vector3(0, 0.32, z), Mk.steel(), -1.0, 10)
		roll.rotation.z = PI / 2
	var white := Mk.paint(Color(0.88, 0.9, 0.92), 0.3, 0.4)
	for sx in [-0.44, 0.44]:
		Mk.box(r, Vector3(0.1, 1.45, 0.22), Vector3(sx, 1.02, 0.1), white)
	Mk.box(r, Vector3(1.0, 0.3, 0.34), Vector3(0, 1.82, 0.1), white)
	Mk.box(r, Vector3(0.96, 0.06, 0.3), Vector3(0, 1.66, 0.1), Mk.dark_steel())
	var beam := Mk.box(r, Vector3(0.82, 0.02, 0.06), Vector3(0, 1.0, 0.1), Mk.glow(Color(0.2, 1, 0.4), 4.0))
	beam.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	p["beam"] = beam
	var curtain := Mk.box(r, Vector3(0.8, 1.3, 0.01), Vector3(0, 1.0, 0.1), Mk.mat(Color(0.2, 1, 0.4, 0.12), 0.0, 0.5, Color(0.2, 1, 0.4), 0.8))
	curtain.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	p["curtain"] = curtain
	p["screen"] = _screen(r, Vector3(0, 1.82, -0.08), Vector2(0.62, 0.2), "SCAN OK")
	Mk.bar(r, Vector3(0.44, 1.7, 0.3), Vector3(0.44, 0.3, 0.75), 0.025, Mk.rubber())
	p["status"] = _status_light(r, Vector3(-0.4, 1.97, 0.2))
	p["h"] = 1.9


static func _bras(r: Node3D, p: Dictionary) -> void:
	var dark := Mk.dark_steel()
	var orange := Mk.paint(C_ORANGE, 0.35, 0.4, 0.08)
	Mk.cyl(r, 0.46, 0.12, Vector3(0, 0.06, 0), dark, -1.0, 20)
	_bolts(r, Vector3.ZERO, Vector2(0.32, 0.32), 0.13)
	var turret := Mk.pivot(r, Vector3(0, 0.12, 0))
	Mk.cyl(turret, 0.32, 0.3, Vector3(0, 0.15, 0), orange, 0.28, 20)
	Mk.box(turret, Vector3(0.36, 0.3, 0.3), Vector3(0, 0.42, 0), orange)
	var shoulder := Mk.pivot(turret, Vector3(0, 0.5, 0))
	var j1 := Mk.cyl(shoulder, 0.17, 0.42, Vector3.ZERO, dark, -1.0, 16)
	j1.rotation.z = PI / 2
	Mk.box(shoulder, Vector3(0.2, 1.25, 0.22), Vector3(0, 0.62, 0), orange)
	Mk.bar(shoulder, Vector3(0.13, 0.2, 0.05), Vector3(0.13, 1.1, 0.05), 0.025, Mk.steel())
	var elbow := Mk.pivot(shoulder, Vector3(0, 1.25, 0))
	var j2 := Mk.cyl(elbow, 0.13, 0.34, Vector3.ZERO, dark, -1.0, 16)
	j2.rotation.z = PI / 2
	Mk.box(elbow, Vector3(0.15, 1.05, 0.16), Vector3(0, 0.52, 0), orange)
	var wrist := Mk.pivot(elbow, Vector3(0, 1.05, 0))
	Mk.cyl(wrist, 0.1, 0.12, Vector3(0, 0.03, 0), dark, -1.0, 12)
	Mk.box(wrist, Vector3(0.26, 0.06, 0.12), Vector3(0, 0.1, 0), dark)
	var f1 := Mk.box(wrist, Vector3(0.04, 0.22, 0.1), Vector3(-0.1, 0.22, 0), Mk.steel())
	var f2 := Mk.box(wrist, Vector3(0.04, 0.22, 0.1), Vector3(0.1, 0.22, 0), Mk.steel())
	var load := Mk.box(wrist, Vector3(0.14, 0.06, 0.34), Vector3(0, 0.24, 0), Mk.needle_material())
	Mk.bar(r, Vector3(0.3, 0.1, 0.3), Vector3(0.1, 0.4, 0.2), 0.03, Mk.rubber())
	p.merge({"turret": turret, "shoulder": shoulder, "elbow": elbow, "load": load, "fingers": [f1, f2], "h": 1.2})
	p["status"] = _status_light(r, Vector3(-0.35, 0.12, 0.35))


static func _pelle(r: Node3D, p: Dictionary) -> void:
	var yellow := Mk.paint(C_YELLOW, 0.25, 0.45, 0.15)
	var dark := Mk.rubber()
	var root := Mk.pivot(r, Vector3.ZERO)
	root.scale = Vector3(0.72, 0.72, 0.72)
	for sx in [-0.95, 0.95]:
		Mk.box(root, Vector3(0.55, 0.55, 2.5), Vector3(sx, 0.28, 0), dark)
		for z in [-1.0, 0.0, 1.0]:
			var w := Mk.cyl(root, 0.2, 0.57, Vector3(sx, 0.28, z), Mk.dark_steel(), -1.0, 12)
			w.rotation.z = PI / 2
	var house := Mk.pivot(root, Vector3(0, 0.6, 0))
	Mk.box(house, Vector3(2.1, 0.8, 2.1), Vector3(0, 0.4, 0.25), yellow)
	Mk.box(house, Vector3(2.12, 0.12, 0.7), Vector3(0, 0.4, 1.0), Mk.dark_steel())
	var cab := Mk.box(house, Vector3(0.9, 1.0, 0.95), Vector3(-0.55, 1.3, -0.35), Mk.glass())
	cab.name = "Cabine"
	Mk.box(house, Vector3(0.96, 0.08, 1.0), Vector3(-0.55, 1.84, -0.35), yellow)
	for sx in [-1.0, -0.1]:
		Mk.box(house, Vector3(0.06, 1.0, 0.06), Vector3(sx, 1.3, -0.82), yellow)
	Mk.cyl(house, 0.07, 0.7, Vector3(0.75, 1.15, 0.9), Mk.dark_steel())
	var boom := Mk.pivot(house, Vector3(0.45, 0.9, -0.75))
	Mk.box(boom, Vector3(0.32, 0.38, 2.7), Vector3(0, 0, -1.35), yellow)
	Mk.bar(boom, Vector3(0.0, -0.3, -0.2), Vector3(0.0, 0.1, -1.6), 0.07, Mk.steel())
	var stick := Mk.pivot(boom, Vector3(0, 0, -2.7))
	Mk.box(stick, Vector3(0.26, 1.7, 0.28), Vector3(0, -0.85, 0), yellow)
	var bucket := Mk.pivot(stick, Vector3(0, -1.7, 0))
	Mk.box(bucket, Vector3(0.85, 0.45, 0.55), Vector3(0, -0.13, -0.22), Mk.dark_steel())
	for i in 4:
		Mk.box(bucket, Vector3(0.08, 0.06, 0.15), Vector3(-0.3 + i * 0.2, -0.36, -0.48), Mk.steel())
	var load := Mk.box(bucket, Vector3(0.75, 0.18, 0.45), Vector3(0, 0.08, -0.22), Mk.needle_material())
	p.merge({"house": house, "boom": boom, "stick": stick, "bucket": bucket, "load": load, "h": 1.9})


static func _smoke(r: Node3D, pos: Vector3, color: Color, amount := 18) -> CPUParticles3D:
	var smoke := CPUParticles3D.new()
	smoke.position = pos
	smoke.amount = amount
	smoke.lifetime = 2.8
	smoke.direction = Vector3(0, 1, 0)
	smoke.spread = 12.0
	smoke.initial_velocity_min = 0.7
	smoke.initial_velocity_max = 1.2
	smoke.gravity = Vector3(0.3, 0.25, 0)
	smoke.scale_amount_min = 0.5
	smoke.scale_amount_max = 1.2
	var sm := SphereMesh.new()
	sm.radius = 0.3
	sm.height = 0.6
	sm.radial_segments = 8
	sm.rings = 4
	smoke.mesh = sm
	smoke.material_override = Mk.mat(color, 0.0, 1.0)
	r.add_child(smoke)
	return smoke


static func _sparks(r: Node3D, pos: Vector3) -> CPUParticles3D:
	var s := CPUParticles3D.new()
	s.position = pos
	s.amount = 16
	s.lifetime = 0.7
	s.direction = Vector3(0, 1, -1)
	s.spread = 40.0
	s.initial_velocity_min = 1.0
	s.initial_velocity_max = 2.5
	s.gravity = Vector3(0, -6, 0)
	s.scale_amount_min = 0.5
	s.scale_amount_max = 1.0
	var bm := BoxMesh.new()
	bm.size = Vector3(0.03, 0.03, 0.03)
	s.mesh = bm
	s.material_override = Mk.glow(Color(1, 0.6, 0.15), 4.0)
	r.add_child(s)
	return s


static func _fonderie(r: Node3D, p: Dictionary) -> void:
	_plinth(r, 2.0, 2.0)
	var brick := Mk.brick()
	Mk.box(r, Vector3(1.6, 1.3, 1.4), Vector3(0, 0.79, 0.15), brick)
	var dome := Mk.sphere(r, 0.85, Vector3(0, 1.44, 0.15), brick)
	dome.scale = Vector3(0.95, 0.45, 0.85)
	Mk.cyl(r, 0.2, 1.4, Vector3(0.45, 2.2, 0.45), brick, 0.17)
	Mk.cyl(r, 0.25, 0.08, Vector3(0.45, 2.92, 0.45), Mk.dark_steel())
	Mk.box(r, Vector3(1.66, 0.08, 1.46), Vector3(0, 0.52, 0.15), Mk.dark_steel())
	# porte rougeoyante et coulée
	Mk.box(r, Vector3(0.7, 0.55, 0.06), Vector3(0, 0.95, -0.56), Mk.dark_steel())
	var mouth := Mk.box(r, Vector3(0.55, 0.42, 0.04), Vector3(0, 0.95, -0.6), Mk.glow(Color(1, 0.45, 0.08), 3.5))
	p["mouth"] = mouth
	var trough := Mk.box(r, Vector3(0.18, 0.06, 0.5), Vector3(0, 0.62, -0.78), Mk.steel())
	trough.rotation.x = -0.25
	var mold := Mk.box(r, Vector3(0.9, 0.12, 0.3), Vector3(0, 0.22, -0.82), Mk.dark_steel())
	mold.name = "Moule"
	var hot := Mk.box(r, Vector3(0.7, 0.05, 0.18), Vector3(0, 0.3, -0.82), Mk.glow(Color(1, 0.55, 0.1), 3.0))
	p["hot"] = hot
	_chute(r, 0.95, 0.75, 0.5, false)
	var light := OmniLight3D.new()
	light.position = Vector3(0, 1.0, -1.2)
	light.light_color = Color(1, 0.55, 0.2)
	light.light_energy = 1.0
	light.omni_range = 3.5
	r.add_child(light)
	p["light"] = light
	p["smoke"] = _smoke(r, Vector3(0.45, 3.0, 0.45), Color(0.32, 0.32, 0.34, 0.45))
	p["sparks"] = _sparks(r, Vector3(0, 0.9, -0.7))
	p["h"] = 2.2


static func _purif(r: Node3D, p: Dictionary) -> void:
	_plinth(r, 2.0, 2.0)
	var steel := Mk.steel()
	var liquids: Array = []
	for x in [-0.45, 0.45]:
		Mk.cyl(r, 0.4, 0.1, Vector3(x, 0.19, 0.2), steel)
		Mk.cyl(r, 0.37, 1.4, Vector3(x, 0.94, 0.2), Mk.glass(Color(0.7, 0.85, 1.0, 0.3)), -1.0, 20)
		var liq := Mk.cyl(r, 0.33, 1.0, Vector3(x, 0.74, 0.2), Mk.mat(Color(0.2, 0.55, 1.0, 0.7), 0.0, 0.2, Color(0.15, 0.5, 1.0), 1.8), -1.0, 20)
		liquids.append(liq)
		Mk.cyl(r, 0.4, 0.12, Vector3(x, 1.68, 0.2), steel)
		var cap := Mk.sphere(r, 0.38, Vector3(x, 1.74, 0.2), steel)
		cap.scale = Vector3(1, 0.35, 1)
		for k in 3:
			Mk.cyl(r, 0.02, 1.0, Vector3(x - 0.15 + k * 0.15, 1.2, 0.2), Mk.mat(Color(0.8, 0.5, 0.25), 0.9, 0.3), -1.0, 6)
	Mk.bar(r, Vector3(-0.45, 1.85, 0.2), Vector3(0.45, 1.85, 0.2), 0.05, steel)
	Mk.bar(r, Vector3(0.45, 1.85, 0.2), Vector3(0.8, 1.5, -0.4), 0.04, steel)
	_cabinet(r, Vector3(0.7, 0.6, 0.35), Vector3(-0.45, 0.14, -0.6), C_BLUE)
	p["screen"] = _screen(r, Vector3(-0.45, 0.6, -0.79), Vector2(0.45, 0.25), "99,9 %")
	_chute(r, -0.85, 0.35, 0.4, true)
	_chute(r, 0.95, 0.55, 0.5, false)
	p["liquids"] = liquids
	p["h"] = 2.3


static func _presse(r: Node3D, p: Dictionary) -> void:
	_plinth(r, 2.0, 2.0)
	var frame := Mk.paint(C_BLUE, 0.35, 0.45)
	Mk.box(r, Vector3(1.5, 0.5, 1.2), Vector3(0, 0.39, 0.1), frame)
	for sx in [-0.62, 0.62]:
		Mk.box(r, Vector3(0.24, 1.6, 0.4), Vector3(sx, 1.2, 0.1), frame)
	Mk.box(r, Vector3(1.55, 0.36, 0.55), Vector3(0, 2.02, 0.1), frame)
	Mk.cyl(r, 0.18, 0.4, Vector3(0, 2.36, 0.1), Mk.steel())
	var ram := Mk.pivot(r, Vector3(0, 1.6, 0.1))
	Mk.bar(ram, Vector3(0, 0, 0), Vector3(0, 0.5, 0), 0.09, Mk.steel())
	Mk.box(ram, Vector3(1.0, 0.18, 0.7), Vector3(0, -0.05, 0), Mk.dark_steel())
	p["ram"] = ram
	Mk.box(r, Vector3(1.0, 0.06, 0.7), Vector3(0, 0.67, 0.1), Mk.steel())
	var plates := Mk.box(r, Vector3(0.6, 0.12, 0.5), Vector3(0, 0.2, -0.75), Mk.mat(Color(0.55, 0.62, 0.72), 0.85, 0.3))
	p["plates"] = plates
	for sx in [-0.9, 0.9]:
		Mk.box(r, Vector3(0.05, 0.8, 0.05), Vector3(sx, 0.55, -0.9), Mk.hazard())
	_chute(r, 0.95, 0.55, 0.5, false)
	p["h"] = 2.3


static func _trefileuse(r: Node3D, p: Dictionary) -> void:
	_plinth(r, 2.0, 2.0)
	_cabinet(r, Vector3(1.7, 0.6, 1.4), Vector3(0, 0.14, 0.15), C_GREEN)
	var copper := Mk.mat(Color(0.85, 0.52, 0.28), 0.85, 0.35)
	var spools: Array = []
	for i in 3:
		var sp := Mk.pivot(r, Vector3(-0.55 + i * 0.55, 1.05, 0.25))
		var drum := Mk.cyl(sp, 0.24, 0.22, Vector3.ZERO, copper, -1.0, 20)
		drum.rotation.x = PI / 2
		for zf in [-0.12, 0.12]:
			var fl := Mk.cyl(sp, 0.3, 0.025, Vector3(0, 0, zf), Mk.steel(), -1.0, 20)
			fl.rotation.x = PI / 2
		Mk.box(sp, Vector3(0.06, 0.4, 0.03), Vector3(0, 0, 0.14), Mk.dark_steel())
		spools.append(sp)
	Mk.box(r, Vector3(1.6, 0.025, 0.025), Vector3(0, 1.3, 0.25), copper)
	for i in 4:
		Mk.box(r, Vector3(0.12, 0.2, 0.18), Vector3(-0.82 + i * 0.55, 1.3, 0.25), Mk.dark_steel())
	var coil := Mk.torus(r, 0.08, 0.2, Vector3(0, 0.25, -0.75), copper)
	coil.name = "Bobine"
	_chute(r, 0.95, 0.55, 0.5, false)
	p["spools"] = spools
	p["h"] = 1.8


static func _aiguilleuse(r: Node3D, p: Dictionary) -> void:
	_plinth(r, 2.0, 2.0)
	_cabinet(r, Vector3(1.7, 0.9, 1.5), Vector3(0, 0.14, 0.1), C_RED)
	Mk.box(r, Vector3(1.5, 0.75, 1.2), Vector3(0, 1.43, 0.15), Mk.glass(Color(0.8, 0.9, 1.0, 0.25)))
	Mk.box(r, Vector3(1.56, 0.05, 1.26), Vector3(0, 1.82, 0.15), Mk.paint(C_RED.darkened(0.3)))
	var head := Mk.pivot(r, Vector3(0, 1.55, 0.15))
	Mk.box(head, Vector3(0.22, 0.3, 0.22), Vector3.ZERO, Mk.steel())
	Mk.cyl(head, 0.02, 0.25, Vector3(0, -0.25, 0), Mk.steel(), 0.005, 6)
	p["head"] = head
	Mk.box(r, Vector3(1.5, 0.04, 0.04), Vector3(0, 1.72, 0.15), Mk.dark_steel())
	var needles := MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.mesh = Mk.needle_mesh(0.3, 0.006)
	mm.instance_count = 14
	for i in 14:
		mm.set_instance_transform(i, Transform3D(Basis(Vector3.FORWARD, PI / 2), Vector3(-0.6 + i * 0.09, 1.1, 0.3)))
	needles.multimesh = mm
	needles.material_override = Mk.needle_material()
	r.add_child(needles)
	var boxes := Mk.pivot(r, Vector3(0, 0.14, -0.8))
	for i in 3:
		var bx := Mk.box(boxes, Vector3(0.3, 0.16, 0.24), Vector3(-0.35 + i * 0.35, 0.08, 0), Mk.paint(Color(0.85, 0.2, 0.25), 0.1, 0.5, 0.0))
		Mk.box(bx, Vector3(0.31, 0.03, 0.25), Vector3(0, 0.05, 0), Mk.paint(C_CREAM, 0.0, 0.6, 0.0))
	p["screen"] = _screen(r, Vector3(0.6, 0.75, -0.66), Vector2(0.35, 0.22), "x 100")
	_chute(r, 0.95, 0.55, 0.5, false)
	p["h"] = 2.0


static func _tampon(r: Node3D, p: Dictionary) -> void:
	_plinth(r, 2.0, 2.0, 0.08)
	var frame := Mk.paint(C_BLUE, 0.4, 0.45)
	for sx in [-0.9, 0.9]:
		for sz in [-0.9, 0.9]:
			Mk.box(r, Vector3(0.08, 2.0, 0.08), Vector3(sx, 1.0, sz), frame)
	for y in [0.15, 0.82, 1.5]:
		Mk.box(r, Vector3(1.9, 0.06, 1.9), Vector3(0, y, 0), Mk.paint(C_ORANGE, 0.4, 0.45))
	var crates: Array = []
	var cm := Mk.paint(C_WOOD, 0.0, 0.85, 0.2)
	for lvl in 3:
		for i in 4:
			var c := Mk.box(r, Vector3(0.75, 0.55, 0.75), Vector3(-0.42 + (i % 2) * 0.84, 0.46 + lvl * 0.67, -0.42 + (i / 2) * 0.84), cm)
			crates.append(c)
	p["crates"] = crates
	p["h"] = 2.0


static func _drone_pad(r: Node3D, p: Dictionary) -> void:
	Mk.cyl(r, 0.47, 0.08, Vector3(0, 0.04, 0), Mk.dark_steel(), -1.0, 24)
	Mk.torus(r, 0.3, 0.36, Vector3(0, 0.085, 0), Mk.paint(C_YELLOW))
	Mk.box(r, Vector3(0.36, 0.01, 0.08), Vector3(0, 0.085, 0), Mk.paint(C_YELLOW))
	for a in 4:
		Mk.sphere(r, 0.03, Vector3(0.4, 0.09, 0).rotated(Vector3.UP, a * PI / 2 + PI / 4), Mk.glow(Color(0.3, 0.7, 1.0), 3.0))
	p["h"] = 0.3


## Radar à foin : mât, parabole qui tourne, voyant doré au sommet.
static func _radar(r: Node3D, p: Dictionary) -> void:
	Mk.cyl(r, 0.45, 0.12, Vector3(0, 0.06, 0), Mk.dark_steel(), -1.0, 20)
	Mk.box(r, Vector3(0.5, 0.5, 0.4), Vector3(0, 0.37, 0), Mk.paint(C_TEAL, 0.3, 0.45))
	Mk.cyl(r, 0.06, 1.5, Vector3(0, 1.3, 0), Mk.steel(), 0.05, 10)
	var head := Mk.pivot(r, Vector3(0, 2.05, 0))
	var dish := Mk.cyl(head, 0.42, 0.06, Vector3(0, 0.1, -0.05), Mk.paint(C_CREAM, 0.3, 0.35), 0.18, 20)
	dish.rotation.x = deg_to_rad(65)
	Mk.bar(head, Vector3(0, 0.1, -0.05), Vector3(0, 0.25, -0.32), 0.015, Mk.steel(), 6)
	Mk.sphere(head, 0.05, Vector3(0, 0.25, -0.33), Mk.glow(Color(1, 0.8, 0.25), 3.0))
	p["head"] = head
	p["h"] = 2.4


## Groupe électrogène : moteur sur châssis, radiateur, pot d'échappement qui fume.
static func _groupe(r: Node3D, p: Dictionary) -> void:
	_plinth(r, 2.0, 2.0, 0.1)
	var body := Mk.paint(Color(0.85, 0.62, 0.12), 0.25, 0.5)
	var eng := Mk.pivot(r, Vector3.ZERO)
	Mk.box(eng, Vector3(1.6, 0.9, 1.1), Vector3(0, 0.62, 0), body)
	Mk.box(eng, Vector3(1.62, 0.12, 1.12), Vector3(0, 1.1, 0), Mk.dark_steel())
	var fin_mat := Mk.dark_steel()
	for i in 6:
		Mk.box(eng, Vector3(0.03, 0.6, 0.06), Vector3(-0.81, 0.62, -0.4 + i * 0.16), fin_mat)
	Mk.box(eng, Vector3(0.5, 0.25, 0.02), Vector3(0.35, 0.75, 0.56), Mk.paint(Color(0.15, 0.15, 0.17)))
	Mk.label(eng, "15 kW", Vector3(0.35, 0.75, 0.58), 24, Color(0.4, 1, 0.5))
	Mk.cyl(eng, 0.07, 0.8, Vector3(0.55, 1.5, -0.35), Mk.dark_steel(), -1.0, 10)
	p["smoke"] = _smoke(r, Vector3(0.55, 2.0, -0.35), Color(0.35, 0.35, 0.37), 10)
	p["engine"] = eng
	p["h"] = 1.6


## Éolienne : grand mât effilé, nacelle et trois pales qui tournent selon le vent.
static func _eolienne(r: Node3D, p: Dictionary) -> void:
	Mk.cyl(r, 0.45, 0.2, Vector3(0, 0.1, 0), Mk.paint(Color(0.6, 0.6, 0.62)), -1.0, 20)
	var white := Mk.paint(Color(0.94, 0.95, 0.96), 0.15, 0.4, 0.05)
	Mk.cyl(r, 0.2, 7.0, Vector3(0, 3.6, 0), white, 0.11, 14)
	Mk.box(r, Vector3(0.36, 0.36, 0.9), Vector3(0, 7.2, 0.1), white)
	var rotor := Mk.pivot(r, Vector3(0, 7.2, -0.42))
	Mk.sphere(rotor, 0.2, Vector3.ZERO, white)
	for k in 3:
		var arm := Mk.pivot(rotor, Vector3.ZERO)
		arm.rotation.z = k * TAU / 3.0
		Mk.box(arm, Vector3(0.22, 2.6, 0.05), Vector3(0, 1.4, 0), white)
	p["rotor"] = rotor
	p["h"] = 7.5


## Panneaux solaires : deux rangées de cellules inclinées sur un châssis.
static func _solaire(r: Node3D, p: Dictionary) -> void:
	_plinth(r, 2.0, 2.0, 0.08)
	var frame := Mk.steel()
	var cells := Mk.mat(Color(0.08, 0.14, 0.32), 0.6, 0.2)
	var line := Mk.mat(Color(0.75, 0.78, 0.82), 0.6, 0.3)
	for row in 2:
		var z := -0.5 + row * 1.0
		var tilt := Mk.pivot(r, Vector3(0, 0.75, z))
		tilt.rotation.x = deg_to_rad(-28)
		Mk.box(tilt, Vector3(1.9, 0.04, 0.85), Vector3.ZERO, cells)
		for i in 5:
			Mk.box(tilt, Vector3(0.015, 0.045, 0.85), Vector3(-0.76 + i * 0.38, 0.002, 0), line)
		Mk.box(tilt, Vector3(1.9, 0.045, 0.015), Vector3(0, 0.002, 0), line)
		for sx in [-0.85, 0.85]:
			Mk.box(r, Vector3(0.05, 0.75, 0.05), Vector3(sx, 0.4, z + 0.25), frame)
			Mk.box(r, Vector3(0.05, 0.45, 0.05), Vector3(sx, 0.25, z - 0.25), frame)
	p["h"] = 1.5


## Le drone lui-même (il vole, ajouté à part).
static func create_drone() -> Node3D:
	var d := Node3D.new()
	var body := Mk.paint(Color(0.92, 0.92, 0.94), 0.3, 0.35, 0.0)
	Mk.box(d, Vector3(0.36, 0.14, 0.42), Vector3.ZERO, body)
	Mk.sphere(d, 0.09, Vector3(0, -0.02, -0.22), Mk.glass(Color(0.1, 0.1, 0.12, 0.8)))
	var rotors: Array = []
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			Mk.bar(d, Vector3.ZERO, Vector3(sx * 0.38, 0.04, sz * 0.38), 0.025, Mk.dark_steel(), 6)
			Mk.cyl(d, 0.05, 0.08, Vector3(sx * 0.38, 0.06, sz * 0.38), Mk.dark_steel(), -1.0, 8)
			var disc := Mk.cyl(d, 0.22, 0.01, Vector3(sx * 0.38, 0.11, sz * 0.38), Mk.mat(Color(0.2, 0.2, 0.22, 0.35), 0.0, 0.5), -1.0, 16)
			var blade := Mk.box(d, Vector3(0.42, 0.012, 0.04), Vector3(sx * 0.38, 0.115, sz * 0.38), Mk.dark_steel())
			disc.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
			rotors.append(blade)
			Mk.sphere(d, 0.025, Vector3(sx * 0.38, 0.0, sz * 0.38), Mk.glow(Color(1, 0.2, 0.2) if sz < 0 else Color(0.2, 1, 0.3), 3.0))
	var load := Mk.box(d, Vector3(0.3, 0.12, 0.26), Vector3(0, -0.15, 0), Mk.needle_material())
	d.set_meta("parts", {"rotors": rotors, "load": load})
	return d


static func _trou(r: Node3D, p: Dictionary) -> void:
	# grand puits : fond noir, parois inclinées, margelle jaune et noire
	var floor_m := Mk.mat(Color(0.01, 0.01, 0.012), 0.0, 1.0)
	Mk.box(r, Vector3(3.7, 0.02, 3.7), Vector3(0, 0.006, 0), floor_m)
	var wall := Mk.paint(Color(0.16, 0.15, 0.14), 0.2, 0.9, 0.3)
	for i in 4:
		var piv := Mk.pivot(r, Vector3.ZERO)
		piv.rotation.y = i * PI / 2
		var w := Mk.box(piv, Vector3(3.7, 0.04, 1.2), Vector3(0, -0.12, 1.3), wall)
		w.rotation.x = 0.6
		Mk.box(piv, Vector3(4.0, 0.14, 0.18), Vector3(0, 0.07, 1.91), Mk.hazard())
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			var post := Mk.pivot(r, Vector3(sx * 1.92, 0, sz * 1.92))
			Mk.cyl(post, 0.06, 1.1, Vector3(0, 0.55, 0), Mk.paint(C_YELLOW, 0.3))
			Mk.sphere(post, 0.08, Vector3(0, 1.12, 0), Mk.glow(Color(1, 0.75, 0.3), 3.0))
	var sign_root := Mk.pivot(r, Vector3(2.4, 0, 2.4))
	Mk.cyl(sign_root, 0.06, 2.6, Vector3(0, 1.3, 0), Mk.dark_steel())
	Mk.sphere(sign_root, 0.12, Vector3(0, 2.65, 0), Mk.glow(Color(1, 0.8, 0.3), 3.0))
	var lbl := Label3D.new()
	lbl.text = "VENTE"
	lbl.font_size = 96
	lbl.pixel_size = 0.008
	lbl.position = Vector3(0, 3.2, 0)
	lbl.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	lbl.modulate = Color(1, 0.88, 0.35)
	lbl.outline_size = 18
	lbl.outline_modulate = Color(0.1, 0.05, 0.0)
	sign_root.add_child(lbl)
	var cash := Mk.label(sign_root, "", Vector3(0, 2.3, 0), 44, Color(0.6, 1, 0.6))
	p["cash"] = cash
	p["h"] = 0.4


static func _bureau(r: Node3D, p: Dictionary) -> void:
	# petite borne de commande
	Mk.box(r, Vector3(0.7, 0.06, 0.6), Vector3(0, 0.03, 0), Mk.dark_steel())
	_cabinet(r, Vector3(0.5, 1.0, 0.36), Vector3(0, 0.06, 0.05), C_GREEN)
	var head := Mk.box(r, Vector3(0.6, 0.45, 0.2), Vector3(0, 1.3, -0.02), Mk.paint(C_GREEN.darkened(0.2)))
	head.rotation.x = -0.35
	var scr := Mk.box(r, Vector3(0.5, 0.36, 0.02), Vector3(0, 1.31, -0.13), Mk.glow(Color(0.25, 0.6, 0.95), 1.6))
	scr.rotation.x = -0.35
	var l := Label3D.new()
	l.text = "COMMANDES"
	l.font_size = 30
	l.pixel_size = 0.004
	l.position = Vector3(0, 1.32, -0.15)
	l.rotation.x = -0.35
	l.rotation.y = PI
	l.modulate = Color(1, 1, 1)
	r.add_child(l)
	Mk.box(r, Vector3(0.3, 0.04, 0.1), Vector3(0, 0.95, -0.2), Mk.steel())
	Mk.cyl(r, 0.025, 1.6, Vector3(0.32, 0.8, 0.22), Mk.dark_steel())
	var roof := Mk.box(r, Vector3(0.9, 0.05, 0.75), Vector3(0.1, 1.62, 0.0), Mk.paint(C_YELLOW, 0.3))
	roof.rotation.z = 0.05
	p["h"] = 1.6


# ============================================================ animations
## Anime une machine. state : 0 en attente, 1 en marche, 2 bloquée.
static func animate(root: Node3D, type: String, t: float, state: int, info: Dictionary) -> void:
	var p: Dictionary = root.get_meta("parts")
	var ph: float = root.get_meta("phase", 0.0)
	var active := state == 1
	if p.has("status"):
		var want := LIGHT_ON if state == 1 else (LIGHT_BAD if state == 2 else LIGHT_IDLE)
		var lamp: MeshInstance3D = p.status
		if lamp.get_meta("c", Color.BLACK) != want:
			lamp.set_meta("c", want)
			lamp.material_override = Mk.glow(want, 3.0)
	match type:
		"bras":
			var aim: float = info.get("aim", 0.0)
			var cyc := fmod(t * 0.7 + ph, 1.0) if active else 0.25
			var toward := 1.0 - smoothstep(0.35, 0.65, cyc) + smoothstep(0.9, 1.0, cyc)
			p.turret.rotation.y = lerp_angle(0.0, aim, clampf(toward, 0.0, 1.0))
			var dip := sin(clampf(cyc / 0.4, 0.0, 1.0) * PI) if cyc < 0.4 else 0.0
			p.shoulder.rotation.x = -0.6 - dip * 0.4
			p.elbow.rotation.x = -1.0 - dip * 0.45
			var grip := 0.04 if (cyc > 0.3 and cyc < 0.85 and active) else 0.1
			p.fingers[0].position.x = -grip
			p.fingers[1].position.x = grip
			p.load.visible = active and cyc > 0.3 and cyc < 0.85
		"pelle":
			var aim2: float = info.get("aim", 0.0)
			var c2 := fmod(t * 0.35 + ph, 1.0) if active else 0.3
			var toward2 := 1.0 - smoothstep(0.4, 0.6, c2) + smoothstep(0.9, 1.0, c2)
			p.house.rotation.y = lerp_angle(0.0, aim2, clampf(toward2, 0.0, 1.0))
			var dig := sin(clampf(c2 / 0.4, 0.0, 1.0) * PI) if c2 < 0.4 else 0.0
			p.boom.rotation.x = 0.25 - dig * 0.45
			p.stick.rotation.x = -0.2 + dig * 0.5
			p.bucket.rotation.x = 0.4 - dig * 0.9
			p.load.visible = active and c2 > 0.3 and c2 < 0.8
		"scanner":
			p.beam.position.y = 0.95 + sin(t * 4.0 + ph) * 0.55
			p.beam.visible = active
			p.curtain.visible = active
			var alert: bool = info.get("alert", false)
			p.screen.text = "FOIN !" if alert else ("SCAN..." if active else "SCAN OK")
			p.screen.modulate = Color(1, 0.85, 0.3) if alert else Color(0.6, 1, 0.7)
		"fonderie":
			p.light.light_energy = (1.1 + 0.5 * sin(t * 13.0 + ph) + 0.3 * sin(t * 7.3)) if active else 0.15
			p.smoke.emitting = active
			p.sparks.emitting = active
			p.hot.visible = active
		"purif":
			for i in p.liquids.size():
				var liq: MeshInstance3D = p.liquids[i]
				liq.scale.y = 0.75 + 0.2 * sin(t * (2.0 if active else 0.3) + i * 1.7 + ph)
		"presse":
			p.ram.position.y = 1.6 - (pow(absf(sin(t * 2.5 + ph)), 3.0) * 0.75 if active else 0.0)
		"trefileuse":
			for s: Node3D in p.spools:
				s.rotation.z = t * 5.0 if active else s.rotation.z
		"aiguilleuse":
			p.head.position.x = sin(t * 4.0 + ph) * 0.6 if active else 0.0
			p.head.position.y = 1.55 - (absf(sin(t * 12.0)) * 0.08 if active else 0.0)
		"separateur":
			p.spin.rotation.y = t * 5.0
		"tremie":
			var f: float = info.get("fill", 0.0)
			p.heap.visible = f > 0.0
			p.heap.scale = Vector3(1.0, 0.06 + 0.32 * f, 1.0)
		"tampon":
			var n := int(ceil(float(info.get("fill", 0.0)) * p.crates.size()))
			for i in p.crates.size():
				p.crates[i].visible = i < n
		"trou":
			p.cash.text = info.get("cash", "")
		"radar":
			if active:
				p.head.rotation.y = t * 1.6 + ph
		"groupe":
			p.smoke.emitting = active
			p.engine.position.y = sin(t * 60.0) * 0.006 if active else 0.0
		"eolienne":
			p.rotor.rotation.z = t * 1.8 * float(info.get("wind", 1.0)) + ph
